package io.github.eightbrows.appwatch

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.eightbrows.appwatch.data.settings.SetupState
import io.github.eightbrows.appwatch.widget.AppWatchWidgetReceiver
import io.github.eightbrows.appwatch.widget.WidgetPlacement
import io.github.eightbrows.appwatch.work.CheckScheduler
import io.github.eightbrows.appwatch.work.MonitoringEffects
import io.github.eightbrows.appwatch.work.MonitoringPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 初回設定の状態・チェック間隔の変更で、定期チェックの登録が切り替わること、ウィジェットの受け取りが常に有効になることを、
 * 端末の WorkManager と PackageManager で確かめる。
 * 定期チェックはテスト用の名前で登録し（本物の登録に触れない）、ウィジェットの受け取りは最後に既定（有効）に戻す。
 */
@RunWith(AndroidJUnit4::class)
class MonitoringEffectsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testName = "periodic_check_effects_test"

    private val completed = SetupState(completed = true)
    private val beforeSetup = SetupState(completed = false)

    private fun activeWork(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(testName).get(10, TimeUnit.SECONDS)
            .filter { !it.state.isFinished }

    private fun apply(setup: SetupState, interval: Int) = runBlocking {
        MonitoringEffects.apply(context, MonitoringPolicy.decide(setup, interval), uniqueName = testName)
    }

    @After
    fun tearDown() {
        CheckScheduler.cancel(context, testName)
        WorkManager.getInstance(context).pruneWork()
        WidgetPlacement.restoreDefaultEnabled(context)
    }

    @Test
    fun changingInterval_reschedulesWithNewInterval() {
        apply(completed, 30)
        assertEquals(TimeUnit.MINUTES.toMillis(30), activeWork().single().periodicityInfo?.repeatIntervalMillis)
        apply(completed, 720)
        assertEquals(1, activeWork().size)
        assertEquals(TimeUnit.HOURS.toMillis(12), activeWork().single().periodicityInfo?.repeatIntervalMillis)
        apply(completed, 1440)
        assertEquals(1, activeWork().size)
        assertEquals(TimeUnit.HOURS.toMillis(24), activeWork().single().periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun beforeSetup_cancelsCheck_butKeepsWidget() {
        apply(completed, 720)
        assertEquals(1, activeWork().size)

        apply(beforeSetup, 720)
        assertTrue(activeWork().isEmpty())
        assertTrue(WidgetPlacement.isEnabled(context))
    }

    @Test
    fun legacyViewOnlyDevice_widgetReceiverIsEnabledAgain() {
        // 前の版で閲覧専用にした端末と同じ状態（ウィジェットの受け取りが無効）を作る
        val component = ComponentName(context, AppWatchWidgetReceiver::class.java)
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        assertFalse(WidgetPlacement.isEnabled(context))

        // 更新後は初回設定の前の扱い：定期チェックは登録せず、ウィジェットの受け取りは有効に戻す
        val legacy = SetupState.fromStored(completed = true, legacyUsageMode = SetupState.LEGACY_VIEW_ONLY)
        apply(legacy, 720)
        assertTrue(activeWork().isEmpty())
        assertTrue(WidgetPlacement.isEnabled(context))
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            context.packageManager.getComponentEnabledSetting(component),
        )
    }
}
