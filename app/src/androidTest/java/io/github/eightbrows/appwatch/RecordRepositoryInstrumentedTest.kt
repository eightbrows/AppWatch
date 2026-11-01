package io.github.eightbrows.appwatch

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.eightbrows.appwatch.data.db.AppWatchDatabase
import io.github.eightbrows.appwatch.data.db.DatasetKind
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.DeviceEnvironment
import io.github.eightbrows.appwatch.data.record.RecordRepository
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.InstalledApp
import io.github.eightbrows.appwatch.model.ReviewStatus
import io.github.eightbrows.appwatch.ui.setup.SetupCategory
import io.github.eightbrows.appwatch.ui.setup.SetupSelection
import io.github.eightbrows.appwatch.ui.top.TopLists
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/**
 * Room のデータベースに、チェックの結果と管理者の設定が正しく保存されるかを、端末上で確かめる。
 * データベースはメモリ上に作るので、端末の本物のデータには触れない。
 */
@RunWith(AndroidJUnit4::class)
class RecordRepositoryInstrumentedTest {

    private lateinit var db: AppWatchDatabase
    private var now = DAY1
    private var ids = 0

    private fun repository() = RecordRepository(
        db = db,
        environment = DeviceEnvironment("TestPhone", "Android 14", 34, "20261031-D03"),
        selfPackageName = "io.github.eightbrows.appwatch",
        clock = { now },
        zone = { ZoneOffset.UTC },
        newId = { "id-${++ids}" },
    )

    private fun app(pkg: String, versionCode: Long = 1, signer: String = "aaaa", firstInstall: Long = 100) = InstalledApp(
        packageName = pkg,
        name = pkg.uppercase(),
        versionName = "v$versionCode",
        versionCode = versionCode,
        kind = AppKind.USER_INSTALLED,
        source = InstallSource.OTHER,
        installerPackage = "com.example.store",
        initiatingPackage = null,
        warnings = setOf(AppWarning.NOT_FROM_PLAY),
        signerSha256 = listOf(signer),
        firstInstallTime = firstInstall,
        lastUpdateTime = firstInstall,
        isEnabled = true,
        isSelf = false,
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppWatchDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun localDataset_isCreatedOnce_andKeepsItsId() = runBlocking {
        val first = repository().localDatasetId()
        val second = repository().localDatasetId() // 作り直しても同じ番号
        assertEquals(first, second)
        val dataset = db.datasetDao().getLocal()
        assertNotNull(dataset)
        assertEquals(DatasetKind.LOCAL, dataset!!.kind)
        assertEquals("TestPhone", dataset.deviceModel)
    }

    @Test
    fun firstCheck_savesBaselineAndCurrent_withoutEvents() = runBlocking {
        val repo = repository()
        val outcome = repo.record(listOf(app("a"), app("b")), now)
        val ds = repo.localDatasetId()

        assertTrue(outcome.isBaseline)
        assertEquals(2, db.appRecordDao().getBaseline(ds).size)
        assertEquals(listOf("aaaa"), db.appRecordDao().getBaseline(ds).first().signerSha256)
        assertEquals(2, db.appRecordDao().getCurrent(ds).size)
        assertTrue(db.appRecordDao().getEvents(ds).isEmpty())
        assertEquals(now, repo.lastCheckAt())
        // 基準のアプリは「新しく入ったアプリ」ではない
        assertTrue(TopLists.from(repo.observePresentApps().first()).cards.isEmpty())
    }

    @Test
    fun laterChecks_recordChanges_andKeepRemovedApps() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("keep"), app("gone"), app("upd", versionCode = 1)), now)
        now += HOUR
        val outcome = repo.record(listOf(app("keep"), app("upd", versionCode = 2), app("new")), now)
        val ds = repo.localDatasetId()

        assertEquals(
            listOf(ChangeType.REMOVED, ChangeType.ADDED, ChangeType.UPDATED),
            db.appRecordDao().getEvents(ds).map { it.type },
        )
        assertEquals(outcome.changes.size, 3)
        val gone = db.appRecordDao().getCurrent(ds).single { it.packageName == "gone" }
        assertFalse(gone.isPresent) // 削除したアプリは「端末にない」として残す
        assertEquals(now, gone.removedAt)
        // 画面には端末にあるアプリだけが出て、新しく入ったアプリはカードになる
        val present = repo.observePresentApps().first()
        assertEquals(setOf("keep", "upd", "new"), present.map { it.packageName }.toSet())
        assertEquals(listOf("new"), TopLists.from(present).cards.map { it.packageName })
    }

    @Test
    fun reinstalledApp_showsPreviousStatus_andReinstallWarning() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("qr")), now)
        repo.setReviewStatus(listOf("qr"), ReviewStatus.CAUTION)
        repo.setAdminMemo("qr", "広告が多い")
        now += HOUR
        repo.record(emptyList(), now) // 削除
        now += HOUR
        repo.record(listOf(app("qr", firstInstall = 900)), now) // 再インストール

        val ds = repo.localDatasetId()
        val added = db.appRecordDao().getEvents(ds).last { it.type == ChangeType.ADDED }
        assertTrue(added.isReinstall)

        val qr = repo.observePresentApps().first().single()
        assertEquals(ReviewStatus.CAUTION, qr.reviewStatus) // 以前の確認状態
        assertEquals("広告が多い", qr.adminMemo)
        assertTrue(AppWarning.REINSTALLED in qr.warnings)
        assertEquals(listOf("qr"), TopLists.from(listOf(qr)).cards.map { it.packageName })
    }

    @Test
    fun signatureChange_resetsOkToUnconfirmed_withReason() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("bank")), now)
        repo.setReviewStatus(listOf("bank"), ReviewStatus.OK)
        now += HOUR
        repo.record(listOf(app("bank", signer = "bbbb")), now)

        val ds = repo.localDatasetId()
        val policy = db.policyDao().get(ds, "bank")!!
        assertEquals(ReviewStatus.UNCONFIRMED, policy.reviewStatus)
        assertEquals(StatusChangeReason.SIGNATURE_CHANGED, policy.statusReason)
        val types = db.appRecordDao().getEvents(ds).map { it.type }
        assertEquals(listOf(ChangeType.STATUS_CHANGED, ChangeType.SIGNATURE_CHANGED, ChangeType.STATUS_CHANGED), types)
        assertEquals(listOf("bbbb"), db.appRecordDao().getCurrent(ds).single().signerSha256)
    }

    @Test
    fun settingOk_clearsReinstallAndSignatureWarnings_butKeepsHistory() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("qr")), now)
        now += HOUR
        repo.record(emptyList(), now) // 削除
        now += HOUR
        repo.record(listOf(app("qr", signer = "bbbb", firstInstall = 900)), now) // 別の署名で再インストール

        val before = repo.observePresentApps().first().single()
        assertTrue(AppWarning.REINSTALLED in before.warnings)
        assertTrue(AppWarning.SIGNATURE_CHANGED in before.warnings)
        assertEquals(listOf("qr"), TopLists.from(listOf(before)).cards.map { it.packageName })

        repo.setReviewStatus(listOf("qr"), ReviewStatus.OK)

        val after = repo.observePresentApps().first().single()
        assertFalse(AppWarning.REINSTALLED in after.warnings) // 仕様書 4章：「問題なし」で消す
        assertFalse(AppWarning.SIGNATURE_CHANGED in after.warnings)
        assertTrue(AppWarning.NOT_FROM_PLAY in after.warnings) // 端末の情報の警告は残る
        assertTrue(TopLists.from(listOf(after)).cards.isEmpty())
        val types = db.appRecordDao().getEvents(repo.localDatasetId()).map { it.type }
        assertTrue(ChangeType.SIGNATURE_CHANGED in types) // 変化の記録は残る
    }

    @Test
    fun signatureChangedApp_isCardWhileUnconfirmed() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("bank")), now)
        repo.setReviewStatus(listOf("bank"), ReviewStatus.OK)
        now += HOUR
        repo.record(listOf(app("bank", signer = "bbbb")), now) // 「問題なし」→「未確認」に戻る

        val bank = repo.observePresentApps().first().single()
        assertEquals(ReviewStatus.UNCONFIRMED, bank.reviewStatus)
        assertFalse(bank.isNew) // 新しく入ったアプリではないが
        assertEquals(listOf("bank"), TopLists.from(listOf(bank)).cards.map { it.packageName }) // カードに出る
    }

    @Test
    fun setupBulkOk_isSavedAndRecordedAsOneOperation() = runBlocking {
        // 初回設定の3「最初のチェック」と同じ流れ：選んだアプリだけを、まとめて「問題なし」にする
        val repo = repository()
        val self = app("io.github.eightbrows.appwatch") // 警告（Google Play 以外）があっても、本アプリは含める
        repo.record(listOf(app("a"), app("b"), self), now)
        repo.setReviewStatus(listOf("b"), ReviewStatus.CAUTION) // 「要注意」は変えない
        val present = repo.observePresentApps().first()

        val targets = SetupSelection.targets(present, SetupSelection.DEFAULT_CHECKED + SetupCategory.OTHER_SOURCE)
        assertEquals(listOf("io.github.eightbrows.appwatch"), targets) // a は警告ありなので除く、b は要注意
        assertEquals(1, repo.setReviewStatus(targets, ReviewStatus.OK, operationId = "setup"))

        val after = repo.observePresentApps().first().associateBy { it.packageName }
        assertEquals(ReviewStatus.OK, after.getValue("io.github.eightbrows.appwatch").reviewStatus)
        assertEquals(ReviewStatus.UNCONFIRMED, after.getValue("a").reviewStatus)
        assertEquals(ReviewStatus.CAUTION, after.getValue("b").reviewStatus)
        val event = db.appRecordDao().getEvents(repo.localDatasetId()).single { it.operationId == "setup" }
        assertEquals(ChangeType.STATUS_CHANGED, event.type)
        assertEquals(ReviewStatus.UNCONFIRMED, event.oldStatus) // 変更前の値を持つ（元に戻す用）
        assertEquals(ReviewStatus.OK, event.newStatus)
    }

    @Test
    fun scanDaily_countsChecksPerDay() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("a")), now)
        now += HOUR
        repo.record(listOf(app("a")), now)
        now = DAY2
        repo.record(listOf(app("a")), now)

        val days = db.scanDailyDao().getAll(repo.localDatasetId())
        assertEquals(listOf("2026-10-31" to 2, "2026-11-01" to 1), days.map { it.date to it.count })
        assertEquals(DAY2, days.last().lastAt)
        assertEquals(DAY2, repo.observeLastCheckAt().first())
    }

    @Test
    fun setReviewStatus_recordsOldValue_andGroupsByOperation() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("a"), app("b")), now)
        assertEquals(2, repo.setReviewStatus(listOf("a", "b"), ReviewStatus.OK, operationId = "op1"))
        assertEquals(0, repo.setReviewStatus(listOf("a"), ReviewStatus.OK)) // 同じ値なら何もしない

        val events = db.appRecordDao().getEvents(repo.localDatasetId())
        assertEquals(2, events.size)
        assertTrue(events.all { it.type == ChangeType.STATUS_CHANGED && it.operationId == "op1" })
        assertTrue(events.all { it.oldStatus == ReviewStatus.UNCONFIRMED && it.newStatus == ReviewStatus.OK })
    }

    @Test
    fun preRegisteredApp_becomesCautionCard_whenInstalled() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("a")), now)
        repo.preRegister("com.bad.app", provisionalName = "悪いアプリ", memo = "入れないで")
        now += HOUR
        repo.record(listOf(app("a"), app("com.bad.app")), now)

        val bad = repo.observePresentApps().first().single { it.packageName == "com.bad.app" }
        assertEquals(ReviewStatus.CAUTION, bad.reviewStatus)
        assertEquals("入れないで", bad.adminMemo)
        assertEquals("COM.BAD.APP", bad.name) // 実際に入ったら端末上の名前
        assertEquals(listOf("com.bad.app"), TopLists.from(listOf(bad)).cards.map { it.packageName })
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY1 = 1_793_404_800_000L // 2026-10-31 00:00 UTC
        const val DAY2 = DAY1 + 24 * HOUR // 2026-11-01 00:00 UTC
    }
}
