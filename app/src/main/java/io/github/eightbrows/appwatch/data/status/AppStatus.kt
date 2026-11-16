package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.data.record.PolicyState
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.data.record.TrackedApp
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSearch
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus

// アプリの確認状態の画面（仕様書 6章）で使う形と処理。Android と Room を使わない純粋な関数だけを置く。

/**
 * 確認状態の画面の1行。端末にあるアプリと、端末にないが確認状態・管理者メモ・事前登録が残っているアプリ。
 * 事前登録だけで一度も入っていないアプリは、種類・入手元が分からない（null）。
 */
data class AppStatusEntry(
    val packageName: String,
    val name: String,
    val versionName: String?,
    val kind: AppKind?,
    val source: InstallSource?,
    val installerPackage: String?,
    val initiatingPackage: String?,
    val warnings: Set<AppWarning>,
    val status: ReviewStatus,
    val statusReason: StatusChangeReason?,
    val adminMemo: String?,
    val isPresent: Boolean,
    val isPreRegistered: Boolean,
    val isEnabled: Boolean,
    val isSelf: Boolean,
    val firstInstallTime: Long?,
    /** Android の最終更新日時。事前登録だけのアプリは null */
    val lastUpdateTime: Long?,
    val removedAt: Long?,
    val reinstalledAt: Long?,
    val signatureChangedAt: Long?,
)

object AppStatusRules {

    // ---- 一覧の行を作る ----

    /**
     * 記録（app_current の全行）と管理者の設定から、確認状態の画面の行を作る。
     * - 端末にあるアプリはすべて
     * - 端末にないアプリは、確認状態（未確認以外）・管理者メモ・事前登録のどれかが残っているものだけ
     * - 事前登録だけで一度も入っていないアプリも出す（名前は仮のアプリ名、なければパッケージ名）
     */
    fun buildEntries(
        tracked: List<TrackedApp>,
        policies: Map<String, PolicyState>,
        selfPackageName: String,
    ): List<AppStatusEntry> {
        val fromTracked = tracked.mapNotNull { app ->
            val policy = policies[app.packageName]
            if (!app.isPresent && !policy.hasSomething()) return@mapNotNull null
            entryOf(app, policy, selfPackageName)
        }
        val trackedPackages = tracked.mapTo(HashSet()) { it.packageName }
        val policyOnly = policies.values
            .filter { it.packageName !in trackedPackages && it.hasSomething() }
            .map { entryOf(it, selfPackageName) }
        return fromTracked + policyOnly
    }

    /** 記録のあるアプリの1行。 */
    fun entryOf(app: TrackedApp, policy: PolicyState?, selfPackageName: String) = AppStatusEntry(
        packageName = app.packageName,
        name = app.name,
        versionName = app.versionName,
        kind = app.kind,
        source = app.source,
        installerPackage = app.installerPackage,
        initiatingPackage = app.initiatingPackage,
        warnings = app.warnings,
        status = policy?.status ?: ReviewStatus.UNCONFIRMED,
        statusReason = policy?.statusReason,
        adminMemo = policy?.adminMemo,
        isPresent = app.isPresent,
        isPreRegistered = policy?.isPreRegistered == true,
        isEnabled = app.isEnabled,
        isSelf = app.packageName == selfPackageName,
        firstInstallTime = app.firstInstallTime,
        lastUpdateTime = app.lastUpdateTime,
        removedAt = app.removedAt,
        reinstalledAt = app.reinstalledAt,
        signatureChangedAt = app.signatureChangedAt,
    )

    /** 管理者の設定だけがあるアプリ（事前登録だけで一度も入っていない）の1行。名前は仮のアプリ名、なければパッケージ名。 */
    fun entryOf(policy: PolicyState, selfPackageName: String) = AppStatusEntry(
        packageName = policy.packageName,
        name = policy.provisionalName?.takeIf { it.isNotBlank() } ?: policy.packageName,
        versionName = null,
        kind = null,
        source = null,
        installerPackage = null,
        initiatingPackage = null,
        warnings = emptySet(),
        status = policy.status,
        statusReason = policy.statusReason,
        adminMemo = policy.adminMemo,
        isPresent = false,
        isPreRegistered = policy.isPreRegistered,
        isEnabled = true,
        isSelf = policy.packageName == selfPackageName,
        firstInstallTime = null,
        lastUpdateTime = null,
        removedAt = null,
        reinstalledAt = null,
        signatureChangedAt = null,
    )

    private fun PolicyState?.hasSomething(): Boolean =
        this != null && (status != ReviewStatus.UNCONFIRMED || !adminMemo.isNullOrBlank() || isPreRegistered)

    // ---- 絞り込み・検索・並び ----

    /** 絞り込みに当たるか（ツリーの決まりは [StatusFilter]）。 */
    fun matchesFilter(entry: AppStatusEntry, enabled: Set<FilterItem>): Boolean = StatusFilter.matches(entry, enabled)

    /**
     * 検索：アプリ名・パッケージ名の部分一致（大文字・小文字を区別しない）。空なら全部。
     * 本アプリは、記録した名前に加えて、今の言語の名前（[selfName]）でも当たる。
     */
    fun matchesQuery(entry: AppStatusEntry, query: String, selfName: String? = null): Boolean =
        AppSearch.matches(entry.name, entry.packageName, query, extraName = selfName.takeIf { entry.isSelf })

    /** 並び：要注意 → 未確認 → 問題なし、同じ中はアプリ名の順。 */
    val ORDER: Comparator<AppStatusEntry> = compareBy<AppStatusEntry> {
        when (it.status) {
            ReviewStatus.CAUTION -> 0
            ReviewStatus.UNCONFIRMED -> 1
            ReviewStatus.OK -> 2
        }
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.packageName }

    /**
     * 一覧の見出しの組：要注意 → 未確認 → 問題なし。組の中は [visible] の順のまま。0件の組は入れない。
     * [visible] は [ORDER] で並べたもの（確認状態の順になっている）。
     */
    fun sections(visible: List<AppStatusEntry>): List<Pair<ReviewStatus, List<AppStatusEntry>>> =
        listOf(ReviewStatus.CAUTION, ReviewStatus.UNCONFIRMED, ReviewStatus.OK)
            .map { status -> status to visible.filter { it.status == status } }
            .filter { (_, entries) -> entries.isNotEmpty() }

    /** 絞り込み・検索をして並べた、画面に出す行。 */
    fun visible(
        entries: List<AppStatusEntry>,
        enabled: Set<FilterItem>,
        query: String,
        selfName: String? = null,
    ): List<AppStatusEntry> =
        entries.filter { matchesFilter(it, enabled) && matchesQuery(it, query, selfName) }.sortedWith(ORDER)

    // ---- まとめて「問題なし」 ----

    /** まとめて「問題なし」にする対象と、警告があるので変えなかった件数。 */
    data class BulkOkPlan(val targets: List<String>, val skippedWithWarnings: Int)

    /**
     * まとめて「問題なし」にする対象を選ぶ（仕様書 4章：警告のあるアプリは一括操作で「問題なし」にしない）。
     * すでに「問題なし」のアプリは対象にも、変えなかった件数にも入れない。
     */
    fun bulkOkPlan(selected: List<AppStatusEntry>): BulkOkPlan {
        val notOk = selected.filter { it.status != ReviewStatus.OK }
        val (withWarnings, withoutWarnings) = notOk.partition { it.warnings.isNotEmpty() }
        return BulkOkPlan(targets = withoutWarnings.map { it.packageName }, skippedWithWarnings = withWarnings.size)
    }
}
