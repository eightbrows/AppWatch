package io.github.eightbrows.appwatch

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.eightbrows.appwatch.data.settings.SetupState
import io.github.eightbrows.appwatch.model.UsageMode
import io.github.eightbrows.appwatch.widget.WidgetPlacement
import io.github.eightbrows.appwatch.work.CheckScheduler
import io.github.eightbrows.appwatch.work.MonitoringEffects
import io.github.eightbrows.appwatch.work.MonitoringPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 使い方の切り替え・チェック間隔の変更で、定期チェックの登録とウィジェットの受け取りの有効・無効が切り替わることを、
 * 端末の WorkManager と PackageManager で確かめる。
 * 定期チェックはテスト用の名前で登録し（本物の登録に触れない）、ウィジェットの受け取りの状態は最後に元に戻す。
 */
@RunWith(AndroidJUnit4::class)
class MonitoringEffectsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testName = "periodic_check_effects_test"
    private var widgetWasEnabled = true

    private val monitor = SetupState(completed = true, usageMode = UsageMode.MONITOR)
    private val viewOnly = SetupState(completed = true, usageMode = UsageMode.VIEW_ONLY)

    private fun activeWork(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(testName).get(10, TimeUnit.SECONDS)
            .filter { !it.state.isFinished }

    private fun apply(setup: SetupState, interval: Int) = runBlocking {
        MonitoringEffects.apply(context, MonitoringPolicy.decide(setup, interval), uniqueName = testName)
    }

    @Before
    fun setUp() {
        widgetWasEnabled = WidgetPlacement.isEnabled(context)
    }

    @After
    fun tearDown() {
        CheckScheduler.cancel(context, testName)
        WorkManager.getInstance(context).pruneWork()
        WidgetPlacement.setEnabled(context, widgetWasEnabled)
    }

    @Test
    fun changingInterval_reschedulesWithNewInterval() {
        apply(monitor, 30)
        assertEquals(TimeUnit.MINUTES.toMillis(30), activeWork().single().periodicityInfo?.repeatIntervalMillis)
        apply(monitor, 180)
        assertEquals(1, activeWork().size)
        assertEquals(TimeUnit.MINUTES.toMillis(180), activeWork().single().periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun switchingToViewOnly_cancelsCheck_andDisablesWidget_andBack() {
        apply(monitor, 30)
        assertEquals(1, activeWork().size)
        assertTrue(WidgetPlacement.isEnabled(context))

        apply(viewOnly, 30)
        assertTrue(activeWork().isEmpty())
        assertFalse(WidgetPlacement.isEnabled(context))

        // 見守る端末に戻す（初回設定が完了した後）
        apply(monitor, 60)
        assertEquals(1, activeWork().size)
        assertTrue(WidgetPlacement.isEnabled(context))
    }
}
