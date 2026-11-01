package io.github.eightbrows.appwatch

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.eightbrows.appwatch.data.db.AppWatchDatabase
import io.github.eightbrows.appwatch.data.db.DatasetKind
import io.github.eightbrows.appwatch.data.db.ScanDailyEntity
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.DeviceEnvironment
import io.github.eightbrows.appwatch.data.record.RecordRepository
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.data.transfer.AppWatchFile
import io.github.eightbrows.appwatch.data.transfer.AppWatchFileCodec
import io.github.eightbrows.appwatch.data.transfer.ReadResult
import io.github.eightbrows.appwatch.data.transfer.TransferRepository
import io.github.eightbrows.appwatch.data.transfer.TransferSettings
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.ReviewStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset

/**
 * 書き出し・取り込み（閲覧のみ）・取り込んだデータの削除・復元を、メモリ上のデータベースで確かめる。
 * 「別の端末」は、別のデータベース（もう1つの RecordRepository）で記録を作って書き出す。
 */
@RunWith(AndroidJUnit4::class)
class TransferRepositoryInstrumentedTest {

    private lateinit var db: AppWatchDatabase
    private lateinit var otherDb: AppWatchDatabase
    private var now = DAY1
    private var ids = 0

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun records(database: AppWatchDatabase, model: String) = RecordRepository(
        db = database,
        environment = DeviceEnvironment(model, "Android 14", 34, "20261101-D05"),
        selfPackageName = "io.github.eightbrows.appwatch",
        clock = { now },
        zone = { ZoneOffset.UTC },
        newId = { "id-${++ids}" },
    )

    private fun transfer(database: AppWatchDatabase, records: RecordRepository, model: String) = TransferRepository(
        db = database,
        records = records,
        environment = DeviceEnvironment(model, "Android 14", 34, "20261101-D05"),
        clock = { now },
        zone = { ZoneOffset.UTC },
        newId = { "ds-${++ids}" },
    )

    private fun app(pkg: String, kind: AppKind = AppKind.USER_INSTALLED, version: Long = 1) = InstalledApp(
        packageName = pkg, name = pkg.uppercase(), versionName = "v$version", versionCode = version, kind = kind,
        source = InstallSource.GOOGLE_PLAY, installerPackage = "com.android.vending", initiatingPackage = null,
        warnings = emptySet(), signerSha256 = listOf("aa"), firstInstallTime = 100, lastUpdateTime = 100,
        isEnabled = true, isSelf = false,
    )

    private val settings = TransferSettings(checkIntervalMinutes = 60, themeMode = ThemeMode.DARK)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppWatchDatabase::class.java).build()
        otherDb = Room.inMemoryDatabaseBuilder(context, AppWatchDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
        otherDb.close()
    }

    /** 別の端末（端末A）で記録を作り、書き出して読み直したファイル。 */
    private suspend fun otherDeviceFile(extraApp: String? = null): AppWatchFile {
        val otherRecords = records(otherDb, "Pixel 8")
        val otherTransfer = transfer(otherDb, otherRecords, "Pixel 8")
        if (otherDb.appRecordDao().baselineCount(otherRecords.localDatasetId()) == 0) {
            otherRecords.record(listOf(app("a.one"), app("a.two")), now)
            otherRecords.setReviewStatus(listOf("a.two"), ReviewStatus.CAUTION)
        }
        now += HOUR
        otherRecords.record(listOfNotNull(app("a.one", version = 2), app("a.two"), extraApp?.let { app(it) }), now)
        val bytes = ByteArrayOutputStream().also { AppWatchFileCodec.write(otherTransfer.buildExport(settings, now), it) }.toByteArray()
        return (AppWatchFileCodec.read(ByteArrayInputStream(bytes)) as ReadResult.Success).file
    }

    @Test
    fun importForViewing_addsDataset_andCanBeRead() = runBlocking {
        val localRecords = records(db, "Emulator")
        localRecords.record(listOf(app("local.app")), now)
        val repo = transfer(db, localRecords, "Emulator")
        val file = otherDeviceFile()

        val outcome = repo.importForViewing(file, "端末A")
        val imported = repo.getImported().single()
        assertEquals(outcome.datasetId, imported.id)
        assertEquals("端末A", imported.name)
        assertEquals(file.device.id, imported.deviceId)
        assertEquals(file.exportedAt, imported.exportedAt)
        assertNull(imported.previousExportedAt)
        assertEquals(1, imported.cautionCount)
        // 取り込んだデータの確認状態の画面・履歴と比較が読める
        val entries = localRecords.observeStatusEntries(imported.id).first()
        assertEquals(setOf("a.one", "a.two"), entries.map { it.packageName }.toSet())
        assertEquals(ReviewStatus.CAUTION, entries.single { it.packageName == "a.two" }.status)
        assertEquals(listOf(ChangeType.UPDATED), localRecords.observeHistory(imported.id).first().changes.map { it.type })
        // この端末のデータはそのまま
        assertEquals(listOf("local.app"), localRecords.observeStatusEntries().first().map { it.packageName })
    }

    @Test
    fun importSameDeviceAgain_replacesContent_andKeepsPreviousExportTime() = runBlocking {
        val localRecords = records(db, "Emulator")
        localRecords.record(listOf(app("local.app")), now)
        val repo = transfer(db, localRecords, "Emulator")
        val first = otherDeviceFile()
        val firstId = repo.importForViewing(first, "端末A").datasetId
        now += 24 * HOUR
        val second = otherDeviceFile(extraApp = "a.three")

        val outcome = repo.importForViewing(second, "おばあちゃんのスマホ")
        assertTrue(outcome.replaced)
        assertEquals(firstId, outcome.datasetId)
        val imported = repo.getImported().single()
        assertEquals("おばあちゃんのスマホ", imported.name)
        assertEquals(second.exportedAt, imported.exportedAt)
        assertEquals(first.exportedAt, imported.previousExportedAt)
        assertEquals(setOf("a.one", "a.two", "a.three"), localRecords.observeStatusEntries(firstId).first().map { it.packageName }.toSet())
        // 同じファイルをもう一度取り込んでも、前回の書き出し日時は変わらない
        repo.importForViewing(second, "おばあちゃんのスマホ")
        assertEquals(first.exportedAt, repo.getImported().single().previousExportedAt)
    }

    @Test
    fun deleteImported_removesOnlyThatDataset() = runBlocking {
        val localRecords = records(db, "Emulator")
        localRecords.record(listOf(app("local.app")), now)
        val repo = transfer(db, localRecords, "Emulator")
        val id = repo.importForViewing(otherDeviceFile(), "端末A").datasetId

        repo.deleteImported(id)
        assertTrue(repo.getImported().isEmpty())
        assertTrue(db.transferDao().getCurrent(id).isEmpty())
        assertTrue(db.transferDao().getEvents(id).isEmpty())
        assertTrue(db.transferDao().getPolicies(id).isEmpty())
        assertTrue(db.transferDao().getScanDays(id).isEmpty())
        // この端末のデータは消さない（この端末のデータの番号を渡しても消さない）
        repo.deleteImported(localRecords.localDatasetId())
        assertEquals(listOf("local.app"), localRecords.observeStatusEntries().first().map { it.packageName })
    }

    @Test
    fun restoreNo_replacesLocalData_withFileAsIs_andKeepsImported() = runBlocking {
        val localRecords = records(db, "Emulator")
        localRecords.record(listOf(app("local.app")), now)
        val repo = transfer(db, localRecords, "Emulator")
        val importedId = repo.importForViewing(otherDeviceFile(), "端末B").datasetId
        val file = otherDeviceFile()

        val outcome = repo.restore(file, scanned = null)
        assertEquals(file.device.id, outcome.localDatasetId)
        assertEquals(file.device.id, localRecords.localDatasetId()) // 識別番号を引き継ぐ
        val local = db.datasetDao().getLocal()!!
        assertEquals(DatasetKind.LOCAL, local.kind)
        assertEquals("Emulator", local.deviceModel)
        assertEquals(file.content.current.map { it.packageName }.toSet(), localRecords.observeStatusEntries().first().map { it.packageName }.toSet())
        assertEquals(file.content.events.map { it.type }, db.transferDao().getEvents(file.device.id).map { it.type })
        assertEquals(file.content.baseline.size, db.transferDao().getBaseline(file.device.id).size)
        // 前の「この端末」のデータは残っていない。取り込んだデータは残す
        assertEquals(1, db.transferDao().getImported().size)
        assertEquals(importedId, repo.getImported().single().id)
        // 次のチェックで差が記録される（この端末のアプリは local.app だけ）
        now += HOUR
        val recorded = localRecords.record(listOf(app("local.app")), now)
        assertTrue(recorded.changes.any { it.type == ChangeType.ADDED && it.packageName == "local.app" })
        assertTrue(recorded.changes.any { it.type == ChangeType.REMOVED && it.packageName == "a.one" })
    }

    @Test
    fun restoreYes_rebuildsBaseline_recordsReset_andListsNewSystemApps() = runBlocking {
        val localRecords = records(db, "Emulator")
        localRecords.record(listOf(app("local.app")), now)
        val repo = transfer(db, localRecords, "Emulator")
        val file = otherDeviceFile()
        now += HOUR
        val scanned = listOf(app("a.two"), app("new.user"), app("new.system", AppKind.SYSTEM), app("new.pre", AppKind.PREINSTALLED))

        val outcome = repo.restore(file, scanned)
        val id = outcome.localDatasetId
        assertEquals(scanned.map { it.packageName }.toSet(), db.transferDao().getBaseline(id).map { it.packageName }.toSet())
        assertEquals(ChangeType.RESET, db.transferDao().getEvents(id).last().type)
        assertEquals(1, db.transferDao().getEvents(id).count { it.type == ChangeType.RESET })
        assertEquals(listOf("new.pre", "new.system"), outcome.newlyFound)
        // 確認状態は引き継ぐ
        assertEquals(ReviewStatus.CAUTION, localRecords.observeStatusEntries().first().single { it.packageName == "a.two" }.status)
        // まとめて「問題なし」にできる（1つの操作）
        assertEquals(2, localRecords.setReviewStatus(outcome.newlyFound, ReviewStatus.OK))
        // 次のチェックでは差が出ない（今のアプリが基準になっている）
        now += HOUR
        assertTrue(localRecords.record(scanned, now).changes.isEmpty())
    }

    @Test
    fun restoreFailure_keepsOriginalData() = runBlocking {
        val localRecords = records(db, "Emulator")
        localRecords.record(listOf(app("local.app")), now)
        val repo = transfer(db, localRecords, "Emulator")
        val originalId = localRecords.localDatasetId()
        val good = otherDeviceFile()
        // 同じ日付のチェックの記録が2つある（データベースに入れるときに失敗する）
        val broken = good.copy(
            content = good.content.copy(scanDays = good.content.scanDays + ScanDailyEntity("", good.content.scanDays.first().date, 1, 1)),
        )
        try {
            repo.restore(broken, scanned = null)
            fail("失敗するはず")
        } catch (e: Exception) {
            // 期待どおり
        }
        assertEquals(originalId, db.datasetDao().getLocal()!!.id)
        assertEquals(originalId, localRecords.localDatasetId())
        assertEquals(listOf("local.app"), localRecords.observeStatusEntries().first().map { it.packageName })
        assertNull(db.transferDao().getDataset(good.device.id))
    }

    @Test
    fun ownExport_roundTrip_restoresSameRows() = runBlocking {
        val localRecords = records(db, "Emulator")
        localRecords.record(listOf(app("x.one"), app("x.two")), now)
        localRecords.preRegister("bad.app", provisionalName = "悪いアプリ", memo = "入れない")
        now += HOUR
        localRecords.record(listOf(app("x.one", version = 3)), now)
        val repo = transfer(db, localRecords, "Emulator")
        val exported = repo.buildExport(settings, now)
        val bytes = ByteArrayOutputStream().also { AppWatchFileCodec.write(exported, it) }.toByteArray()
        val file = (AppWatchFileCodec.read(ByteArrayInputStream(bytes)) as ReadResult.Success).file
        assertEquals(exported.copy(content = exported.content.withDataset("")), file)

        repo.restore(file, scanned = null)
        val id = localRecords.localDatasetId()
        assertEquals(exported.device.id, id) // 自分のファイルなので番号は同じ
        assertEquals(exported.content.policies, db.transferDao().getPolicies(id))
        assertEquals(exported.content.current, db.transferDao().getCurrent(id))
        assertEquals(exported.content.events.map { it.copy(id = 0) }, db.transferDao().getEvents(id).map { it.copy(id = 0) })
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY1 = 1_793_404_800_000L // 2026-10-31 00:00 UTC
    }
}
