package io.github.eightbrows.appwatch.data.record

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.InstalledApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckRunnerTest {

    private val app = InstalledApp(
        packageName = "a",
        name = "A",
        versionName = "1",
        versionCode = 1,
        kind = AppKind.USER_INSTALLED,
        source = InstallSource.GOOGLE_PLAY,
        installerPackage = null,
        initiatingPackage = null,
        warnings = emptySet(),
        signerSha256 = listOf("x"),
        firstInstallTime = 1,
        lastUpdateTime = 1,
        isEnabled = true,
        isSelf = false,
    )

    /** 呼ばれた回数を数える偽のデータベース。[gate] を渡すと、その完了まで記録を止められる。 */
    private class FakeRecorder(var last: Long? = null, val gate: CompletableDeferred<Unit>? = null) : CheckRecorder {
        var recordCount = 0
        var failWith: Exception? = null
        override suspend fun lastCheckAt(): Long? = last
        override suspend fun record(scanned: List<InstalledApp>, now: Long): RecordOutcome {
            gate?.await()
            failWith?.let { throw it }
            recordCount++
            last = now
            return RecordOutcome(isBaseline = recordCount == 1, appCount = scanned.size, changes = emptyList())
        }
    }

    private fun TestScope.runner(
        recorder: FakeRecorder,
        now: () -> Long = { 10_000_000L },
        scanner: () -> List<InstalledApp> = { listOf(app) },
    ) = CheckRunner(
        scope = backgroundScope,
        scanner = scanner,
        recorder = recorder,
        clock = now,
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun firstCheck_recordsAndReportsBaseline() = runTest {
        val recorder = FakeRecorder()
        val outcome = runner(recorder).check()
        assertTrue(outcome is CheckOutcome.Recorded)
        assertTrue((outcome as CheckOutcome.Recorded).outcome.isBaseline)
        assertEquals(1, recorder.recordCount)
    }

    @Test
    fun checkWithinOneMinute_isSkipped() = runTest {
        val recorder = FakeRecorder(last = 10_000_000L - 59_000)
        assertEquals(CheckOutcome.SkippedRecent, runner(recorder).check())
        assertEquals(0, recorder.recordCount)
    }

    @Test
    fun checkAfterOneMinute_runs() = runTest {
        val recorder = FakeRecorder(last = 10_000_000L - 60_000)
        assertTrue(runner(recorder).check() is CheckOutcome.Recorded)
    }

    @Test
    fun forcedCheck_runsEvenWithinOneMinute() = runTest {
        val recorder = FakeRecorder(last = 10_000_000L - 1_000)
        assertTrue(runner(recorder).check(force = true) is CheckOutcome.Recorded)
    }

    @Test
    fun clockMovedBack_isNotTreatedAsRecent() = runTest {
        val recorder = FakeRecorder(last = 10_000_000L + 5_000) // 最後のチェックが「未来」
        assertTrue(runner(recorder).check() is CheckOutcome.Recorded)
    }

    @Test
    fun secondCheckWhileRunning_isNotStarted() = runTest {
        val gate = CompletableDeferred<Unit>()
        val recorder = FakeRecorder(gate = gate)
        val runner = runner(recorder)

        val first = async { runner.check() }
        runCurrent() // 1回目が記録の途中で止まっている
        assertTrue(runner.state.value.isChecking)
        assertEquals(CheckOutcome.AlreadyRunning, runner.check(force = true))

        gate.complete(Unit)
        assertTrue(first.await() is CheckOutcome.Recorded)
        assertEquals(1, recorder.recordCount)
        assertFalse(runner.state.value.isChecking)
    }

    @Test
    fun failure_isReported_andNextCheckCanRun() = runTest {
        val recorder = FakeRecorder().apply { failWith = IllegalStateException("db") }
        val runner = runner(recorder)

        assertTrue(runner.check() is CheckOutcome.Failed)
        assertTrue(runner.state.value.failed)
        assertFalse(runner.state.value.isChecking)

        recorder.failWith = null
        assertTrue(runner.check() is CheckOutcome.Recorded)
        assertFalse(runner.state.value.failed)
    }

    @Test
    fun scannerFailure_isReported() = runTest {
        val outcome = runner(FakeRecorder(), scanner = { throw SecurityException("no permission") }).check()
        assertTrue(outcome is CheckOutcome.Failed)
    }

    @Test
    fun request_runsInBackground_andForceIsUsedOnlyOnce() = runTest {
        val recorder = FakeRecorder(last = 10_000_000L - 1_000)
        val runner = runner(recorder)

        runner.request().join()
        assertEquals(0, recorder.recordCount) // 1分以内なので省略

        runner.forceNextRequest()
        runner.request().join()
        assertEquals(1, recorder.recordCount) // 強制なので記録

        recorder.last = 10_000_000L - 1_000
        runner.request().join()
        assertEquals(1, recorder.recordCount) // 強制は1回だけ
    }

    @Test
    fun onRecorded_isCalledWithOutcome() = runBlocking {
        var received: RecordOutcome? = null
        val runner = CheckRunner(
            scope = this,
            scanner = { listOf(app) },
            recorder = FakeRecorder(),
            onRecorded = { received = it },
        )
        runner.check()
        assertEquals(1, received?.appCount)
    }
}
