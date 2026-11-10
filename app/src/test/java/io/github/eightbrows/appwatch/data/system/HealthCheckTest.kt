package io.github.eightbrows.appwatch.data.system

import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.data.settings.CheckInterval
import io.github.eightbrows.appwatch.data.settings.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthCheckTest {

    private val now = 100_000_000L
    private val minute = 60_000L

    private val healthy = HealthInput(
        batteryOptimizationExcluded = true,
        unusedAppRestrictionsExcluded = true,
        periodicCheckRegistered = true,
        lastCheckAt = now - 10 * minute,
        now = now,
        checkIntervalMinutes = 30,
    )

    // ---- 「すべて正常」の判定 ----

    @Test
    fun everythingSet_isAllOk() {
        val result = HealthCheck.evaluate(healthy)
        assertTrue(result.allOk)
        assertFalse(result.stopped)
    }

    @Test
    fun anyMissingSetting_needsAttention() {
        assertFalse(HealthCheck.evaluate(healthy.copy(batteryOptimizationExcluded = false)).allOk)
        assertFalse(HealthCheck.evaluate(healthy.copy(unusedAppRestrictionsExcluded = false)).allOk)
        assertFalse(HealthCheck.evaluate(healthy.copy(periodicCheckRegistered = false)).allOk)
    }

    @Test
    fun stopped_usesSameRuleAsWidget() {
        // 30分間隔なら 90分より前で停止（ウィジェットの「停止」と同じ）
        assertTrue(HealthCheck.evaluate(healthy.copy(lastCheckAt = now - 90 * minute)).allOk)
        val stopped = HealthCheck.evaluate(healthy.copy(lastCheckAt = now - 91 * minute))
        assertTrue(stopped.stopped)
        assertFalse(stopped.allOk)
        // 間隔を6時間にすると、同じ最終チェックでも停止ではない
        assertTrue(HealthCheck.evaluate(healthy.copy(lastCheckAt = now - 91 * minute, checkIntervalMinutes = 360)).allOk)
    }

    @Test
    fun neverChecked_isNotStopped() {
        assertTrue(HealthCheck.evaluate(healthy.copy(lastCheckAt = null)).allOk)
    }

    // ---- 直近7日のチェック回数 ----

    @Test
    fun checksInLastDays_countsTodayAndSixDaysBefore() {
        val today = LocalDate.of(2026, 11, 1)
        val days = listOf(
            ScanDay("2026-10-25", 5, 0), // 7日前：数えない
            ScanDay("2026-10-26", 3, 0), // 6日前：数える
            ScanDay("2026-10-31", 40, 0),
            ScanDay("2026-11-01", 2, 0), // 今日
            ScanDay("2026-11-02", 9, 0), // 未来（時計のずれ）：数えない
            ScanDay("broken", 100, 0), // 読めない日付：数えない
        )
        assertEquals(45, HealthCheck.checksInLastDays(days, today))
        assertEquals(42, HealthCheck.checksInLastDays(days, today, days = 2))
        assertEquals(0, HealthCheck.checksInLastDays(emptyList(), today))
    }

    // ---- 未使用アプリの一時停止の案内（Android のバージョンで違う） ----

    @Test
    fun unusedAppHint_dependsOnAndroidVersion() {
        assertEquals(R.string.unused_hint_android12, DeviceSettings.unusedAppRestrictionsHint(sdkInt = 31))
        assertEquals(R.string.unused_hint_android12, DeviceSettings.unusedAppRestrictionsHint(sdkInt = 34))
        assertEquals(R.string.unused_hint_android11, DeviceSettings.unusedAppRestrictionsHint(sdkInt = 30))
    }

    // ---- チェック間隔 ----

    @Test
    fun checkInterval_choicesAndMinutes() {
        assertEquals(listOf(30, 60, 180, 360, 720, 1440), CheckInterval.entries.map { it.minutes })
        assertEquals(CheckInterval.HOURS_12, CheckInterval.DEFAULT)
        assertEquals(720, SettingsRepository.DEFAULT_CHECK_INTERVAL)
        assertEquals(CheckInterval.HOURS_24, CheckInterval.fromMinutes(1440))
        assertEquals(R.string.check_interval_12h, CheckInterval.HOURS_12.labelRes)
        assertEquals(R.string.check_interval_24h, CheckInterval.HOURS_24.labelRes)
        assertEquals(CheckInterval.HOURS_3, CheckInterval.fromMinutes(180))
        assertEquals(CheckInterval.DEFAULT, CheckInterval.fromMinutes(45)) // 知らない値は初期値
        assertEquals(R.string.check_interval_1h, CheckInterval.HOURS_1.labelRes)
    }
}
