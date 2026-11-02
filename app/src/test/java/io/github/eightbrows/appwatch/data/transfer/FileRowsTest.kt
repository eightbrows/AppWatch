package io.github.eightbrows.appwatch.data.transfer

import io.github.eightbrows.appwatch.data.db.AppCurrentEntity
import io.github.eightbrows.appwatch.data.db.AppEventEntity
import io.github.eightbrows.appwatch.data.db.AppPolicyEntity
import io.github.eightbrows.appwatch.data.db.BaselineAppEntity
import io.github.eightbrows.appwatch.data.db.ScanDailyEntity
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * 書き出しファイルの JSON を使わない部分（行の変換・先頭の項目の判断・中身の確かめ・件数・ファイル名）。
 * JSON を通した往復と壊れたファイルは、端末上のテスト（AppWatchFileCodecInstrumentedTest）で確かめる。
 */
class FileRowsTest {

    private val baseline = BaselineAppEntity(
        datasetId = "", packageName = "a.b", capturedAt = 1_000, name = "アプリ「A」\"引用\"", versionName = null,
        versionCode = 10, kind = AppKind.SYSTEM, source = InstallSource.OTHER, installerPackage = "com.example.store",
        initiatingPackage = null, isEnabled = true, warnings = setOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE),
        signerSha256 = listOf("aa", "bb"), firstInstallTime = 100, lastUpdateTime = 200,
    )

    private val current = AppCurrentEntity(
        datasetId = "", packageName = "a.b", name = "名前", versionName = "2.0", versionCode = 20, kind = AppKind.USER_INSTALLED,
        source = InstallSource.GOOGLE_PLAY, installerPackage = null, initiatingPackage = "com.android.vending", isEnabled = false,
        warnings = emptySet(), signerSha256 = emptyList(), firstInstallTime = 100, lastUpdateTime = 300, isPresent = false,
        firstSeenAt = 1_000, addedAt = 2_000, removedAt = 3_000, reinstalledAt = null, signatureChangedAt = 4_000, updatedAt = 5_000,
    )

    private val event = AppEventEntity(
        datasetId = "", occurredAt = 2_500, type = ChangeType.STATUS_CHANGED, packageName = "a.b", name = "A",
        versionName = null, versionCode = null, oldVersionName = "0.9", oldVersionCode = 9, source = null,
        isReinstall = false, oldStatus = ReviewStatus.OK, newStatus = ReviewStatus.CAUTION, reason = StatusChangeReason.UNDO,
        oldAdminMemo = "前\nのメモ", newAdminMemo = "新しいメモ", oldReinstalledAt = 11, oldSignatureChangedAt = 12,
        oldPreRegistered = true, oldProvisionalName = "仮", newProvisionalName = "仮2", operationId = "op-1",
    )

    private val policy = AppPolicyEntity("", "a.b", ReviewStatus.CAUTION, "メモ", isPreRegistered = true, provisionalName = "仮", statusReason = StatusChangeReason.ADMIN, updatedAt = 7)

    private val scanDay = ScanDailyEntity("", "2026-10-31", 3, 9_000)

    /** 1行を JSON から読んだときと同じ形（名前 → 値）にする。 */
    private fun map(row: FileRows.Row): Map<String, Any?> = row.toMap()

    @Test
    fun 行の変換を往復すると同じ内容になる() {
        assertEquals(baseline, FileRows.baselineOf(map(FileRows.baseline(baseline))))
        assertEquals(current, FileRows.currentOf(map(FileRows.current(current))))
        assertEquals(event, FileRows.eventOf(map(FileRows.event(event))))
        assertEquals(policy, FileRows.policyOf(map(FileRows.policy(policy))))
        assertEquals(scanDay, FileRows.scanDayOf(map(FileRows.scanDay(scanDay))))
        val device = DeviceInfo("device-1", "Pixel 8", "Android 15", 35, "20261101-D06")
        assertEquals(device, FileRows.deviceOf(map(FileRows.device(device))))
        val settings = TransferSettings(60, ThemeMode.DARK)
        assertEquals(settings, FileRows.settingsOf(map(FileRows.settings(settings))))
    }

    @Test
    fun nullの項目は書かない() {
        val names = FileRows.baseline(baseline).map { it.first }
        assertFalse("version_name" in names)
        assertFalse("initiating_package" in names)
        assertTrue("installer_package" in names)
        // 警告は名前の順にそろえる
        assertEquals(listOf("DEBUG_SIGNATURE", "NOT_FROM_PLAY"), FileRows.baseline(baseline).toMap()["warnings"])
    }

    @Test
    fun 必要な項目がない_形が違う_知らない値なら例外() {
        val row = map(FileRows.current(current))
        assertThrows(InvalidFileException::class.java) { FileRows.currentOf(row - "is_present") }
        assertThrows(InvalidFileException::class.java) { FileRows.currentOf(row + ("version_code" to "20")) }
        assertThrows(InvalidFileException::class.java) { FileRows.currentOf(row + ("kind" to "TABLET")) }
        assertThrows(InvalidFileException::class.java) { FileRows.currentOf(row + ("warnings" to listOf("FLYING"))) }
        assertThrows(InvalidFileException::class.java) { FileRows.currentOf(row + ("is_enabled" to 1L)) }
        assertThrows(InvalidFileException::class.java) { FileRows.settingsOf(mapOf("check_interval_minutes" to 7L, "theme_mode" to "SYSTEM")) }
        assertThrows(InvalidFileException::class.java) { FileRows.scanDayOf(mapOf("date" to "2026-10-31", "count" to (1L shl 40), "last_at" to 1L)) }
    }

    @Test
    fun 知らない項目は無視する() {
        val row = map(FileRows.policy(policy)) + ("new_field" to "x") + ("nested" to null)
        assertEquals(policy, FileRows.policyOf(row))
    }

    @Test
    fun 先頭の項目の判断() {
        assertEquals(FileRows.Header.Ok, FileRows.checkHeader("appwatch", 1))
        assertEquals(FileRows.Header.TooNew(2), FileRows.checkHeader("appwatch", 2))
        assertEquals(FileRows.Header.NotAppWatch, FileRows.checkHeader("other", 1))
        assertEquals(FileRows.Header.NotAppWatch, FileRows.checkHeader(null, 1))
        assertEquals(FileRows.Header.NotAppWatch, FileRows.checkHeader("appwatch", 0))
    }

    private fun file(content: DatasetContent, deviceId: String = "device-1") = AppWatchFile(
        formatVersion = 1,
        exportedAt = 1,
        device = DeviceInfo(deviceId, "m", "Android 14", 34, "v"),
        settings = TransferSettings(30, ThemeMode.SYSTEM),
        content = content,
    )

    @Test
    fun 中身の確かめ() {
        val good = DatasetContent(listOf(baseline), listOf(current), listOf(event), listOf(policy), listOf(scanDay))
        FileRows.validate(file(good)) // 例外が出ない
        assertThrows(InvalidFileException::class.java) { FileRows.validate(file(good, deviceId = " ")) }
        assertThrows(InvalidFileException::class.java) { FileRows.validate(file(good.copy(scanDays = listOf(scanDay, scanDay.copy(count = 1))))) }
        assertThrows(InvalidFileException::class.java) { FileRows.validate(file(good.copy(current = listOf(current, current)))) }
        assertThrows(InvalidFileException::class.java) { FileRows.validate(file(good.copy(policies = listOf(policy.copy(packageName = ""))))) }
        assertThrows(InvalidFileException::class.java) { FileRows.validate(file(good.copy(scanDays = listOf(scanDay.copy(date = "11/01"))))) }
        assertThrows(InvalidFileException::class.java) { FileRows.validate(file(good.copy(scanDays = listOf(scanDay.copy(count = -1))))) }
    }

    @Test
    fun 件数は端末にあるアプリで数える() {
        val present = current.copy(packageName = "p.present", isPresent = true)
        val content = DatasetContent(
            baseline = emptyList(),
            current = listOf(present, current), // current は端末にない
            events = emptyList(),
            policies = listOf(policy.copy(packageName = "p.present"), policy), // 要注意が2件、端末にあるのは1件
            scanDays = emptyList(),
        )
        assertEquals(1, content.presentAppCount)
        assertEquals(1, content.cautionCount)
    }

    @Test
    fun ファイル名() {
        assertEquals("appwatch_20261028_2100.appwatch", AppWatchFile.fileName(LocalDateTime.of(2026, 10, 28, 21, 0, 59)))
    }

    @Test
    fun 入れる先のデータの番号に書き換える() {
        val content = DatasetContent(listOf(baseline), listOf(current), listOf(event.copy(id = 99)), listOf(policy), listOf(scanDay)).withDataset("ds")
        assertTrue(content.baseline.all { it.datasetId == "ds" })
        assertTrue(content.current.all { it.datasetId == "ds" })
        assertTrue(content.events.all { it.datasetId == "ds" && it.id == 0L })
        assertTrue(content.policies.all { it.datasetId == "ds" })
        assertTrue(content.scanDays.all { it.datasetId == "ds" })
    }
}
