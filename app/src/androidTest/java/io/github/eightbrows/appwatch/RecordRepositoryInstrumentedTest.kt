package io.github.eightbrows.appwatch

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.eightbrows.appwatch.data.db.AppWatchDatabase
import io.github.eightbrows.appwatch.data.db.DatasetKind
import io.github.eightbrows.appwatch.data.history.DateRange
import io.github.eightbrows.appwatch.data.history.HistoryKind
import io.github.eightbrows.appwatch.data.history.HistoryRules
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.DeviceEnvironment
import io.github.eightbrows.appwatch.data.record.PolicyEdit
import io.github.eightbrows.appwatch.data.record.RecordRepository
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.data.status.AppStatusRules
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
import java.time.LocalDate
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

    // ---- アプリの確認状態・アプリの詳細の「元に戻す」（ステップ7） ----

    /** 要注意・管理者メモあり・再インストールの警告ありの qr と、警告のない a・b を用意する。 */
    private suspend fun prepareForUndo(repo: RecordRepository) {
        repo.record(listOf(app("qr"), app("a"), app("b")), now)
        repo.applyOperation(listOf(PolicyEdit("qr", status = ReviewStatus.CAUTION, memo = "広告が多い", setsMemo = true)))
        now += HOUR
        repo.record(listOf(app("a"), app("b")), now) // qr を削除
        now += HOUR
        repo.record(listOf(app("qr", firstInstall = 900), app("a"), app("b")), now) // qr を再インストール
        now += HOUR
    }

    private fun clean(pkg: String) = app(pkg).copy(warnings = emptySet())

    @Test
    fun bulkOk_skipsWarnedApps_andUndoRestoresEverything() = runBlocking {
        val repo = repository()
        repo.record(listOf(app("qr"), clean("a"), clean("b")), now)
        repo.applyOperation(listOf(PolicyEdit("a", memo = "メモ", setsMemo = true)))
        now += HOUR

        // 確認状態の画面と同じ流れ：選んだ3件をまとめて「問題なし」。警告（Google Play 以外）のある qr は変えない
        val selected = repo.observeStatusEntries().first()
        val plan = AppStatusRules.bulkOkPlan(selected)
        assertEquals(listOf("a", "b"), plan.targets.sorted())
        assertEquals(1, plan.skippedWithWarnings)
        val result = repo.applyOperation(plan.targets.map { PolicyEdit(it, status = ReviewStatus.OK) })
        assertEquals(2, result.changedApps)
        val okNow = repo.observeStatusEntries().first().associateBy { it.packageName }
        assertEquals(ReviewStatus.OK, okNow.getValue("a").status)
        assertEquals(ReviewStatus.UNCONFIRMED, okNow.getValue("qr").status)

        // 元に戻す：1回で2件とも戻る。管理者メモはそのまま
        now += HOUR
        assertEquals(2, repo.undoOperation(result.operationId))
        val undone = repo.observeStatusEntries().first().associateBy { it.packageName }
        assertEquals(ReviewStatus.UNCONFIRMED, undone.getValue("a").status)
        assertEquals(ReviewStatus.UNCONFIRMED, undone.getValue("b").status)
        assertEquals("メモ", undone.getValue("a").adminMemo)

        // 取り消しも変化として記録される（新しい操作の番号、理由は「元に戻す」）
        val undoEvents = db.appRecordDao().getEvents(repo.localDatasetId()).filter { it.reason == StatusChangeReason.UNDO }
        assertEquals(setOf("a", "b"), undoEvents.map { it.packageName }.toSet())
        assertTrue(undoEvents.all { it.type == ChangeType.STATUS_CHANGED && it.oldStatus == ReviewStatus.OK && it.newStatus == ReviewStatus.UNCONFIRMED })
        assertEquals(1, undoEvents.map { it.operationId }.distinct().size)
        assertFalse(undoEvents.any { it.operationId == result.operationId })
    }

    @Test
    fun bulkCaution_undo_restoresEachPreviousStatus() = runBlocking {
        val repo = repository()
        repo.record(listOf(clean("a"), clean("b")), now)
        repo.applyOperation(listOf(PolicyEdit("b", status = ReviewStatus.OK)))
        val result = repo.applyOperation(listOf("a", "b").map { PolicyEdit(it, status = ReviewStatus.CAUTION) })
        assertEquals(2, result.changedApps)
        repo.undoOperation(result.operationId)
        val after = repo.observeStatusEntries().first().associateBy { it.packageName }
        assertEquals(ReviewStatus.UNCONFIRMED, after.getValue("a").status)
        assertEquals(ReviewStatus.OK, after.getValue("b").status) // それぞれ変更前の値に戻る
    }

    @Test
    fun detailSave_thenUndo_restoresStatusMemoAndClearedWarnings() = runBlocking {
        val repo = repository()
        prepareForUndo(repo)
        val before = repo.observeAppDetail("qr").first().entry!!
        assertEquals(ReviewStatus.CAUTION, before.status)
        assertTrue(AppWarning.REINSTALLED in before.warnings)
        val reinstalledAt = before.reinstalledAt

        // 詳細の画面で「問題なし」とメモを保存（警告があっても詳細では問題なしにできる）
        val result = repo.applyOperation(
            listOf(PolicyEdit("qr", status = ReviewStatus.OK, memo = "確認しました", setsMemo = true)),
        )
        assertEquals(1, result.changedApps)
        val saved = repo.observeAppDetail("qr").first()
        assertEquals(ReviewStatus.OK, saved.entry!!.status)
        assertEquals("確認しました", saved.entry!!.adminMemo)
        assertFalse(AppWarning.REINSTALLED in saved.entry!!.warnings) // 「問題なし」で警告を消す
        val savedEvents = saved.events.filter { it.operationId == result.operationId }
        assertEquals(listOf(ChangeType.STATUS_CHANGED, ChangeType.MEMO_CHANGED), savedEvents.map { it.type })
        assertEquals(reinstalledAt, savedEvents.first().oldReinstalledAt) // 消した警告の時刻を持つ

        // 元に戻す
        now += HOUR
        assertEquals(1, repo.undoOperation(result.operationId))
        val undone = repo.observeAppDetail("qr").first()
        assertEquals(ReviewStatus.CAUTION, undone.entry!!.status)
        assertEquals("広告が多い", undone.entry!!.adminMemo)
        assertTrue(AppWarning.REINSTALLED in undone.entry!!.warnings) // 消した警告も戻る
        assertEquals(reinstalledAt, undone.entry!!.reinstalledAt)

        // これまでの経緯に、取り消しが記録される（古い順の最後）
        val last = undone.events.takeLast(2)
        assertTrue(last.all { it.reason == StatusChangeReason.UNDO && it.occurredAt == now })
        val statusUndo = last.single { it.type == ChangeType.STATUS_CHANGED }
        assertEquals(ReviewStatus.OK, statusUndo.oldStatus)
        assertEquals(ReviewStatus.CAUTION, statusUndo.newStatus)
        val memoUndo = last.single { it.type == ChangeType.MEMO_CHANGED }
        assertEquals("確認しました", memoUndo.oldAdminMemo)
        assertEquals("広告が多い", memoUndo.newAdminMemo)
    }

    @Test
    fun removedAppWithPolicy_staysInStatusList_asAbsent() = runBlocking {
        val repo = repository()
        repo.record(listOf(clean("keep"), clean("gone_caution"), clean("gone_plain")), now)
        repo.applyOperation(listOf(PolicyEdit("gone_caution", status = ReviewStatus.CAUTION)))
        now += HOUR
        repo.record(listOf(clean("keep")), now)

        val entries = repo.observeStatusEntries().first().associateBy { it.packageName }
        assertEquals(setOf("keep", "gone_caution"), entries.keys)
        assertFalse(entries.getValue("gone_caution").isPresent)
        assertEquals(now, entries.getValue("gone_caution").removedAt)
    }

    // ---- 注意アプリの事前登録（ステップ8） ----

    @Test
    fun preRegister_thenUndo_removesRegistration_andRecordsUndo() = runBlocking {
        val repo = repository()
        repo.record(listOf(clean("a")), now)
        val result = repo.preRegister("com.bad.app", provisionalName = "悪いアプリ", memo = "入れないで")
        assertEquals(1, result.changedApps)

        val registered = repo.observeStatusEntries().first().single { it.packageName == "com.bad.app" }
        assertEquals(ReviewStatus.CAUTION, registered.status)
        assertEquals("悪いアプリ", registered.name)
        assertEquals("入れないで", registered.adminMemo)
        assertTrue(registered.isPreRegistered)
        assertFalse(registered.isPresent)
        val event = repo.observeAppDetail("com.bad.app").first().events.single()
        assertEquals(ChangeType.PRE_REGISTERED, event.type)
        assertEquals(result.operationId, event.operationId)

        now += HOUR
        assertEquals(1, repo.undoOperation(result.operationId))
        assertFalse(repo.observeStatusEntries().first().any { it.packageName == "com.bad.app" }) // 一覧から消える
        val policy = db.policyDao().get(repo.localDatasetId(), "com.bad.app")!!
        assertEquals(ReviewStatus.UNCONFIRMED, policy.reviewStatus)
        assertFalse(policy.isPreRegistered)
        assertEquals(null, policy.adminMemo)
        assertEquals(null, policy.provisionalName)
        val undo = repo.observeAppDetail("com.bad.app").first().events.last()
        assertEquals(ChangeType.PRE_REGISTERED, undo.type)
        assertEquals(StatusChangeReason.UNDO, undo.reason)
    }

    @Test
    fun preRegisterAgain_keepsOldMemoAndName_whenEmpty() = runBlocking {
        val repo = repository()
        repo.preRegister("com.bad.app", provisionalName = "悪いアプリ", memo = "入れないで")
        repo.preRegister("com.bad.app", provisionalName = " ", memo = "")
        val entry = repo.observeAppDetail("com.bad.app").first().entry!!
        assertEquals("悪いアプリ", entry.name)
        assertEquals("入れないで", entry.adminMemo)
    }

    @Test
    fun unregister_thenUndo_restoresRegistration() = runBlocking {
        val repo = repository()
        repo.preRegister("com.bad.app", provisionalName = "悪いアプリ", memo = "入れないで")
        now += HOUR
        val result = repo.unregister("com.bad.app")
        assertEquals(1, result.changedApps)
        assertFalse(repo.observeStatusEntries().first().any { it.packageName == "com.bad.app" })
        assertEquals(ChangeType.UNREGISTERED, repo.observeAppDetail("com.bad.app").first().events.last().type)

        now += HOUR
        repo.undoOperation(result.operationId)
        val restored = repo.observeStatusEntries().first().single { it.packageName == "com.bad.app" }
        assertEquals(ReviewStatus.CAUTION, restored.status)
        assertEquals("悪いアプリ", restored.name)
        assertEquals("入れないで", restored.adminMemo)
        assertTrue(restored.isPreRegistered)
        val undo = repo.observeAppDetail("com.bad.app").first().events.last()
        assertEquals(ChangeType.UNREGISTERED, undo.type)
        assertEquals(StatusChangeReason.UNDO, undo.reason)
        // 登録していないアプリの解除は何もしない
        assertEquals(0, repo.unregister("com.not.registered").changedApps)
    }

    @Test
    fun registeredAppInstalled_overwritesName_andStaysCaution() = runBlocking {
        val repo = repository()
        repo.record(listOf(clean("a")), now)
        repo.preRegister("com.bad.app", provisionalName = "悪いアプリ", memo = "入れないで")
        now += HOUR
        repo.record(listOf(clean("a"), clean("com.bad.app")), now)

        val policy = db.policyDao().get(repo.localDatasetId(), "com.bad.app")!!
        assertEquals("COM.BAD.APP", policy.provisionalName) // 端末上の名前で上書き
        assertEquals(ReviewStatus.CAUTION, policy.reviewStatus)
        assertEquals("入れないで", policy.adminMemo)
        val detail = repo.observeAppDetail("com.bad.app").first()
        assertEquals("COM.BAD.APP", detail.entry!!.name)
        assertTrue(detail.entry!!.isPresent)
        assertEquals(listOf(ChangeType.PRE_REGISTERED, ChangeType.ADDED), detail.events.map { it.type })
        val card = TopLists.from(repo.observePresentApps().first()).cards.single()
        assertEquals("com.bad.app", card.packageName)
        assertEquals("入れないで", card.adminMemo)
    }

    @Test
    fun history_listsDeviceChangesInRange_andCountsMatch() = runBlocking {
        val repo = repository()
        // 1日目 0:00 最初のチェック（基準）
        repo.record(listOf(clean("keep"), clean("upd"), clean("dis"), clean("gone"), clean("sig")), now)
        // 1日目 1:00 入れる・更新する・無効にする・削除する。管理者が「問題なし」にする（一覧には出さない）
        now += HOUR
        repo.record(listOf(clean("keep"), clean("upd").copy(versionCode = 2, versionName = "v2"), clean("dis").copy(isEnabled = false), clean("sig"), clean("new")), now)
        repo.setReviewStatus(listOf("keep", "sig"), ReviewStatus.OK)
        // 2日目 1:00 有効に戻す・署名が変わる
        now = DAY2 + HOUR
        repo.record(listOf(clean("keep"), clean("upd").copy(versionCode = 2, versionName = "v2"), clean("dis"), app("sig", signer = "bbbb").copy(warnings = emptySet()), clean("new")), now)

        val records = repo.observeHistory(repo.localDatasetId()).first()
        val zone = ZoneOffset.UTC
        val day1 = LocalDate.of(2026, 10, 31)
        val day2 = LocalDate.of(2026, 11, 1)

        val first = HistoryRules.build(records.changes, records.scanDays, DateRange(day1, day1), emptySet(), zone)
        assertEquals(
            mapOf(HistoryKind.ADDED to 1, HistoryKind.REMOVED to 1, HistoryKind.UPDATED to 1, HistoryKind.DISABLED to 1, HistoryKind.SIGNATURE to 0),
            first.counts,
        )
        assertEquals(
            listOf(ChangeType.REMOVED to "gone", ChangeType.ADDED to "new", ChangeType.UPDATED to "upd", ChangeType.DISABLED to "dis"),
            first.rows.map { it.type to it.packageName },
        )
        assertEquals("v1", first.rows.single { it.type == ChangeType.UPDATED }.oldVersionName)
        assertEquals(2, first.checkCount)

        val both = HistoryRules.build(records.changes, records.scanDays, DateRange(day1, day2), emptySet(), zone)
        assertEquals(2, both.counts[HistoryKind.DISABLED]) // 有効化も「無効化」に数える
        assertEquals(1, both.counts[HistoryKind.SIGNATURE])
        assertEquals(3, both.checkCount)
        assertTrue(both.rows.none { it.type == ChangeType.STATUS_CHANGED })
        // 署名が変わったときに「問題なし」を「未確認」に戻した記録も、一覧には出さない
        assertEquals(6, both.rows.size)

        val secondHidden = HistoryRules.build(records.changes, records.scanDays, DateRange(day2, day2), setOf(HistoryKind.DISABLED), zone)
        assertEquals(listOf(ChangeType.SIGNATURE_CHANGED), secondHidden.rows.map { it.type })
        assertEquals(1, secondHidden.counts[HistoryKind.DISABLED])
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY1 = 1_793_404_800_000L // 2026-10-31 00:00 UTC
        const val DAY2 = DAY1 + 24 * HOUR // 2026-11-01 00:00 UTC
    }
}
