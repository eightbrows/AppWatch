package io.github.eightbrows.appwatch

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.eightbrows.appwatch.work.CheckScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * 定期チェックの登録を、端末の WorkManager で確かめる（work-testing は使わない）。
 * アプリ本体の登録（初回設定の状態で登録・解除される）に触れないように、テスト用の名前で登録し、最後に外す。
 */
@RunWith(AndroidJUnit4::class)
class CheckSchedulerInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workManager = WorkManager.getInstance(context)
    private val testName = "periodic_check_test"

    private fun infos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(testName).get(10, TimeUnit.SECONDS)
            .filter { !it.state.isFinished }

    @After
    fun tearDown() {
        CheckScheduler.cancel(context, testName)
        workManager.pruneWork()
    }

    @Test
    fun schedule_registersOnePeriodicWork_evenIfCalledTwice() {
        CheckScheduler.schedule(context, 30, testName)
        CheckScheduler.schedule(context, 30, testName)

        val active = infos()
        assertEquals(1, active.size)
        val info = active.single()
        assertTrue(info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING)
        assertEquals(TimeUnit.MINUTES.toMillis(30), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun schedule_withNewInterval_updatesTheSameWork() {
        CheckScheduler.schedule(context, 30, testName)
        CheckScheduler.schedule(context, 60, testName)
        assertEquals(TimeUnit.MINUTES.toMillis(60), infos().single().periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun cancel_removesTheWork() {
        CheckScheduler.schedule(context, 30, testName)
        CheckScheduler.cancel(context, testName)
        assertTrue(infos().isEmpty())
    }
}
