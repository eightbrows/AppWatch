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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.zip.GZIPOutputStream

/** 書き出しファイルの読み書き（往復・新しすぎる版・壊れたファイル・AppWatch でないファイル）。 */
class AppWatchFileCodecTest {

    private fun baseline(pkg: String, kind: AppKind = AppKind.USER_INSTALLED) = BaselineAppEntity(
        datasetId = "",
        packageName = pkg,
        capturedAt = 1_000,
        name = "アプリ「$pkg」\"引用\"",
        versionName = if (pkg.endsWith("x")) null else "1.0",
        versionCode = 10,
        kind = kind,
        source = InstallSource.OTHER,
        installerPackage = "com.example.store",
        initiatingPackage = null,
        isEnabled = true,
        warnings = setOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE),
        signerSha256 = listOf("aa", "bb"),
        firstInstallTime = 100,
        lastUpdateTime = 200,
    )

    private fun current(pkg: String, present: Boolean = true) = AppCurrentEntity(
        datasetId = "",
        packageName = pkg,
        name = "名前 $pkg",
        versionName = "2.0",
        versionCode = 20,
        kind = AppKind.USER_INSTALLED,
        source = InstallSource.GOOGLE_PLAY,
        installerPackage = null,
        initiatingPackage = "com.android.vending",
        isEnabled = false,
        warnings = emptySet(),
        signerSha256 = emptyList(),
        firstInstallTime = 100,
        lastUpdateTime = 300,
        isPresent = present,
        firstSeenAt = 1_000,
        addedAt = 2_000,
        removedAt = if (present) null else 3_000,
        reinstalledAt = null,
        signatureChangedAt = 4_000,
        updatedAt = 5_000,
    )

    private val events = listOf(
        AppEventEntity(
            datasetId = "", occurredAt = 2_000, type = ChangeType.ADDED, packageName = "a.b", name = "A",
            versionName = "1.0", versionCode = 10, oldVersionName = null, oldVersionCode = null, source = InstallSource.OTHER,
            isReinstall = true, oldStatus = null, newStatus = null, reason = null, oldAdminMemo = null, newAdminMemo = null,
            oldReinstalledAt = null, oldSignatureChangedAt = null, oldPreRegistered = null, oldProvisionalName = null,
            newProvisionalName = null, operationId = null,
        ),
        AppEventEntity(
            datasetId = "", occurredAt = 2_500, type = ChangeType.STATUS_CHANGED, packageName = "a.b", name = "A",
            versionName = null, versionCode = null, oldVersionName = "0.9", oldVersionCode = 9, source = null,
            isReinstall = false, oldStatus = ReviewStatus.OK, newStatus = ReviewStatus.CAUTION, reason = StatusChangeReason.UNDO,
            oldAdminMemo = "前\nのメモ", newAdminMemo = "新しいメモ", oldReinstalledAt = 11, oldSignatureChangedAt = 12,
            oldPreRegistered = true, oldProvisionalName = "仮", newProvisionalName = "仮2", operationId = "op-1",
        ),
    )

    private val sample = AppWatchFile(
        formatVersion = 1,
        exportedAt = 1_793_532_965_000,
        device = DeviceInfo(id = "device-1", model = "Pixel 8", osVersion = "Android 15", sdkInt = 35, appVersion = "20261101-D05"),
        settings = TransferSettings(checkIntervalMinutes = 60, themeMode = ThemeMode.DARK),
        content = DatasetContent(
            baseline = listOf(baseline("a.b"), baseline("c.x", AppKind.SYSTEM)),
            current = listOf(current("a.b"), current("gone.app", present = false)),
            events = events,
            policies = listOf(
                AppPolicyEntity("", "a.b", ReviewStatus.CAUTION, "メモ", isPreRegistered = false, provisionalName = null, statusReason = StatusChangeReason.ADMIN, updatedAt = 7),
                AppPolicyEntity("", "gone.app", ReviewStatus.CAUTION, null, isPreRegistered = true, provisionalName = "消えたアプリ", statusReason = null, updatedAt = 8),
            ),
            scanDays = listOf(ScanDailyEntity("", "2026-10-31", 3, 9_000), ScanDailyEntity("", "2026-11-01", 1, 9_500)),
        ),
    )

    private fun bytesOf(file: AppWatchFile): ByteArray = ByteArrayOutputStream().also { AppWatchFileCodec.write(file, it) }.toByteArray()

    private fun read(bytes: ByteArray): ReadResult = AppWatchFileCodec.read(ByteArrayInputStream(bytes))

    private fun gzip(text: String): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(text.toByteArray()) }
    }.toByteArray()

    @Test
    fun 書き出して読むと同じ内容になる() {
        val result = read(bytesOf(sample))
        assertEquals(ReadResult.Success(sample), result)
    }

    @Test
    fun 空のデータも往復できる() {
        val empty = sample.copy(content = DatasetContent.EMPTY)
        assertEquals(ReadResult.Success(empty), read(bytesOf(empty)))
    }

    @Test
    fun 中身はgzipで圧縮したJSONで_先頭に形式の名前と版() {
        val bytes = bytesOf(sample)
        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
        val json = java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().toString(Charsets.UTF_8)
        assertTrue(json, json.startsWith("""{"format":"appwatch","version":1,"exported_at":1793532965000,"device":{"id":"device-1""""))
    }

    @Test
    fun 件数() {
        assertEquals(1, sample.content.presentAppCount)
        // 要注意は2件あるが、端末にあるアプリは1件だけ
        assertEquals(1, sample.content.cautionCount)
    }

    @Test
    fun 新しすぎる版は取り込まない() {
        val newer = """{"format":"appwatch","version":2,"exported_at":1,"something_new":{"x":[1,2]}}"""
        assertEquals(ReadResult.TooNew(2), read(gzip(newer)))
    }

    @Test
    fun AppWatchのファイルでないもの() {
        assertEquals(ReadResult.NotAppWatch, read("こんにちは".toByteArray())) // gzip でない
        assertEquals(ReadResult.NotAppWatch, read(ByteArray(0)))
        assertEquals(ReadResult.NotAppWatch, read(gzip("ただの文")))
        assertEquals(ReadResult.NotAppWatch, read(gzip("""{"format":"other","version":1}""")))
        assertEquals(ReadResult.NotAppWatch, read(gzip("""["appwatch"]""")))
        assertEquals(ReadResult.NotAppWatch, read(gzip("""{"version":1,"format":"appwatch"}"""))) // 形式の名前が先頭にない
    }

    @Test
    fun 壊れたファイル() {
        val bytes = bytesOf(sample)
        assertEquals(ReadResult.NotAppWatch, read(bytes.copyOf(bytes.size / 2))) // 途中で切れている
        val json = java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().toString(Charsets.UTF_8)
        // 知らない種類の値
        assertEquals(ReadResult.NotAppWatch, read(gzip(json.replace("\"STATUS_CHANGED\"", "\"FLYING\""))))
        // 必要な項目がない
        assertEquals(ReadResult.NotAppWatch, read(gzip(json.replace("\"is_present\":true,", ""))))
        assertEquals(ReadResult.NotAppWatch, read(gzip(json.replace(",\"scan_daily\":[", ",\"scan_x\":["))))
        // 値の形が違う
        assertEquals(ReadResult.NotAppWatch, read(gzip(json.replace("\"version_code\":10", "\"version_code\":\"10\""))))
        // 同じ行が2つ
        assertEquals(ReadResult.NotAppWatch, read(gzip(json.replace("\"2026-11-01\"", "\"2026-10-31\""))))
        // 日付の形が違う
        assertEquals(ReadResult.NotAppWatch, read(gzip(json.replace("\"2026-11-01\"", "\"11/01\""))))
        // チェック間隔が知らない値
        assertEquals(ReadResult.NotAppWatch, read(gzip(json.replace("\"check_interval_minutes\":60", "\"check_interval_minutes\":7"))))
    }

    @Test
    fun 知らない項目は読み飛ばす() {
        val json = java.util.zip.GZIPInputStream(ByteArrayInputStream(bytesOf(sample))).readBytes().toString(Charsets.UTF_8)
        val extended = json
            .replace("\"exported_at\":", "\"note\":{\"a\":[1,\"b\",null]},\"exported_at\":")
            .replace("\"date\":\"2026-10-31\"", "\"date\":\"2026-10-31\",\"extra\":{\"z\":1}")
        assertEquals(ReadResult.Success(sample), read(gzip(extended)))
    }

    @Test
    fun ファイル名() {
        assertEquals("appwatch_20261028_2100.appwatch", AppWatchFile.fileName(LocalDateTime.of(2026, 10, 28, 21, 0, 59)))
    }

    @Test
    fun 入れる先のデータの番号に書き換える() {
        val content = sample.content.copy(events = sample.content.events.map { it.copy(id = 99) }).withDataset("ds")
        assertTrue(content.baseline.all { it.datasetId == "ds" })
        assertTrue(content.current.all { it.datasetId == "ds" })
        assertTrue(content.events.all { it.datasetId == "ds" && it.id == 0L })
        assertTrue(content.policies.all { it.datasetId == "ds" })
        assertTrue(content.scanDays.all { it.datasetId == "ds" })
    }
}
