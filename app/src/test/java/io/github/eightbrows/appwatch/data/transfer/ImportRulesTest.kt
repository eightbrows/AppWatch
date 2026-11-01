package io.github.eightbrows.appwatch.data.transfer

import io.github.eightbrows.appwatch.data.db.AppEventEntity
import io.github.eightbrows.appwatch.data.db.AppPolicyEntity
import io.github.eightbrows.appwatch.data.db.ScanDailyEntity
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.settings.RecentExport
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.model.UsageMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ImportRulesTest {

    private val zone = ZoneId.of("Asia/Tokyo")
    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun file(deviceId: String = "device-a", model: String = "Pixel 8", content: DatasetContent = DatasetContent.EMPTY) = AppWatchFile(
        formatVersion = 1,
        exportedAt = at("2026-10-28T21:00"),
        device = DeviceInfo(deviceId, model, "Android 15", 35, "20261101-D05"),
        settings = TransferSettings(30, ThemeMode.SYSTEM),
        content = content,
    )

    private fun imported(id: String, deviceId: String, name: String) = ImportedDataset(
        id = id, deviceId = deviceId, name = name, deviceModel = "Pixel 8", osVersion = "Android 15",
        exportedAt = 1, previousExportedAt = null, importedAt = 2, cautionCount = 0,
    )

    private fun installed(pkg: String, kind: AppKind) = InstalledApp(
        packageName = pkg, name = pkg, versionName = "1", versionCode = 1, kind = kind, source = InstallSource.GOOGLE_PLAY,
        installerPackage = null, initiatingPackage = null, warnings = emptySet(), signerSha256 = listOf("aa"),
        firstInstallTime = 1, lastUpdateTime = 1, isEnabled = true, isSelf = false,
    )

    private fun policy(pkg: String, status: ReviewStatus) =
        AppPolicyEntity("", pkg, status, adminMemo = null, isPreRegistered = false, provisionalName = null, statusReason = null, updatedAt = 1)

    // ---- 同じ端末・端末の名前 ----

    @Test
    fun 同じ端末は識別番号で判断する() {
        val list = listOf(imported("ds1", "device-b", "端末B"), imported("ds2", "device-a", "端末A"))
        assertEquals("ds2", ImportRules.sameDevice(file(deviceId = "device-a"), list)?.id)
        assertNull(ImportRules.sameDevice(file(deviceId = "device-c"), list))
    }

    @Test
    fun 端末の名前の初期値() {
        val list = listOf(imported("ds2", "device-a", "おばあちゃんのスマホ"))
        assertEquals("おばあちゃんのスマホ", ImportRules.defaultName(file(deviceId = "device-a"), list))
        assertEquals("AQUOS sense8", ImportRules.defaultName(file(deviceId = "device-b", model = "AQUOS sense8"), list))
    }

    @Test
    fun この端末自身のファイルは閲覧のみにできない() {
        assertTrue(ImportRules.isOwnFile(file(deviceId = "local"), "local"))
        assertFalse(ImportRules.canViewOnly(file(deviceId = "local"), "local"))
        assertTrue(ImportRules.canViewOnly(file(deviceId = "device-a"), "local"))
    }

    @Test
    fun 閲覧専用の端末では復元できない() {
        assertFalse(ImportRules.canRestore(UsageMode.VIEW_ONLY))
        assertTrue(ImportRules.canRestore(UsageMode.MONITOR))
        assertTrue(ImportRules.canRestore(null))
    }

    // ---- 復元の「はい」 ----

    @Test
    fun 復元のはい_今のアプリを新しい基準にし_初期化を1件記録する() {
        val oldEvent = AppEventEntity(
            datasetId = "", occurredAt = at("2026-10-20T10:00"), type = ChangeType.ADDED, packageName = "old.app", name = "旧",
            versionName = null, versionCode = null, oldVersionName = null, oldVersionCode = null, source = null, isReinstall = false,
            oldStatus = null, newStatus = null, reason = null, oldAdminMemo = null, newAdminMemo = null, oldReinstalledAt = null,
            oldSignatureChangedAt = null, oldPreRegistered = null, oldProvisionalName = null, newProvisionalName = null, operationId = null,
        )
        val content = DatasetContent(
            baseline = emptyList(),
            current = emptyList(),
            events = listOf(oldEvent),
            policies = listOf(policy("sys.known", ReviewStatus.OK), policy("user.bad", ReviewStatus.CAUTION)),
            scanDays = listOf(ScanDailyEntity("", "2026-10-20", 5, 1), ScanDailyEntity("", "2026-11-01", 2, 2)),
        )
        val scanned = listOf(
            installed("sys.known", AppKind.SYSTEM), // ファイルに確認状態がある
            installed("sys.new", AppKind.SYSTEM),
            installed("pre.new", AppKind.PREINSTALLED),
            installed("user.new", AppKind.USER_INSTALLED), // 後から導入は尋ねない
            installed("user.bad", AppKind.USER_INSTALLED),
        )
        val now = at("2026-11-01T12:00")
        val plan = ImportRules.resetContent(file(content = content), scanned, now, zone)

        assertEquals(scanned.map { it.packageName }.toSet(), plan.content.baseline.map { it.packageName }.toSet())
        assertTrue(plan.content.baseline.all { it.capturedAt == now })
        assertTrue(plan.content.current.all { it.isPresent && it.addedAt == null })
        // 経緯は引き継ぎ、最後に初期化が1件
        assertEquals(listOf(ChangeType.ADDED, ChangeType.RESET), plan.content.events.map { it.type })
        assertEquals(now, plan.content.events.last().occurredAt)
        // 確認状態はファイルのまま
        assertEquals(content.policies, plan.content.policies)
        // 取り直しも1回のチェックとして数える（今日の回数に足す）
        assertEquals(listOf("2026-10-20" to 5, "2026-11-01" to 3), plan.content.scanDays.map { it.date to it.count })
        // 新しく見つかったプリインストール・システムで、ファイルに確認状態がないもの
        assertEquals(listOf("pre.new", "sys.new"), plan.newlyFound)
    }

    @Test
    fun 復元のはい_今日のチェックがなければ1回() {
        val plan = ImportRules.resetContent(file(), emptyList(), at("2026-11-01T12:00"), zone)
        assertEquals(listOf("2026-11-01" to 1), plan.content.scanDays.map { it.date to it.count })
        assertTrue(plan.newlyFound.isEmpty())
    }

    // ---- データの切り替え ----

    private val list = listOf(imported("ds1", "device-a", "端末A"), imported("ds2", "device-b", "端末B"))

    @Test
    fun 選んだデータがあればそれ() {
        val selection = DatasetSelector.resolve("ds2", "local", "", list, viewOnly = false)
        assertEquals("ds2", selection.selected?.id)
        assertTrue(selection.isViewingImported)
        assertEquals(listOf("local", "ds1", "ds2"), selection.options.map { it.id })
        assertTrue(selection.showsSwitcher)
    }

    @Test
    fun 選んだデータが削除されたら_この端末に戻る() {
        val selection = DatasetSelector.resolve("deleted", "local", "", list, viewOnly = false)
        assertEquals("local", selection.selected?.id)
        assertFalse(selection.isViewingImported)
    }

    @Test
    fun 取り込んだデータがなければ切り替えを出さない() {
        val selection = DatasetSelector.resolve(null, "local", "", emptyList(), viewOnly = false)
        assertFalse(selection.showsSwitcher)
        assertEquals("local", selection.selected?.id)
    }

    @Test
    fun 閲覧専用ではこの端末を出さず_一覧の最初にする() {
        val selection = DatasetSelector.resolve(null, "local", "", list, viewOnly = true)
        assertEquals(listOf("ds1", "ds2"), selection.options.map { it.id })
        assertEquals("ds1", selection.selected?.id)
        // 「この端末」を保存していても使わない
        assertEquals("ds1", DatasetSelector.resolve("local", "local", "", list, viewOnly = true).selected?.id)
        // 取り込んだデータがなければ、選択なし（取り込んだデータの画面に戻る）
        assertNull(DatasetSelector.resolve(null, "local", "", emptyList(), viewOnly = true).selected)
    }

    // ---- 最近の書き出し ----

    @Test
    fun 最近の書き出しを保存して読む() {
        val list = listOf(RecentExport(2, "appwatch_20261101_1200.appwatch", 4321), RecentExport(1, "名前\tタブ.appwatch", 10))
        val decoded = RecentExport.decode(RecentExport.encode(list))
        assertEquals(listOf(list[0], RecentExport(1, "名前 タブ.appwatch", 10)), decoded)
        assertEquals(emptyList<RecentExport>(), RecentExport.decode(""))
        assertEquals(emptyList<RecentExport>(), RecentExport.decode("壊れた行"))
    }
}
