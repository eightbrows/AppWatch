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
    val removedAt: Long?,
    val reinstalledAt: Long?,
    val signatureChangedAt: Long?,
)

/**
 * 絞り込みの行・組（仕様書 6章の表）。同じ行（組）の中は「どれかに当たる」、行（組）どうしは「すべてに当たる」。
 * 最後の行は「端末」「登録」「状態」の3つの組に分け、それぞれを別の組として効かせる。
 */
enum class FilterRow { STATUS, KIND, SOURCE, WARNING, DEVICE, REGISTRATION, STATE }

/** 絞り込みの選択肢（チップ）。 */
enum class FilterChip(val row: FilterRow) {
    STATUS_UNCONFIRMED(FilterRow.STATUS),
    STATUS_CAUTION(FilterRow.STATUS),
    STATUS_OK(FilterRow.STATUS),
    KIND_USER(FilterRow.KIND),
    KIND_PREINSTALLED(FilterRow.KIND),
    KIND_SYSTEM(FilterRow.KIND),
    SOURCE_PLAY(FilterRow.SOURCE),
    SOURCE_OTHER(FilterRow.SOURCE),
    SOURCE_UNKNOWN(FilterRow.SOURCE),
    WARNING_NONE(FilterRow.WARNING),
    WARNING_DEBUG(FilterRow.WARNING),
    WARNING_SIGNATURE(FilterRow.WARNING),
    WARNING_REINSTALL(FilterRow.WARNING),

    // 5行目は「端末：あり／なし」「登録：事前登録／なし」「状態：有効／無効」の3つの組（どの組も全部の場合を選べる）
    DEVICE_PRESENT(FilterRow.DEVICE),
    DEVICE_ABSENT(FilterRow.DEVICE),
    REGISTERED(FilterRow.REGISTRATION),
    NOT_REGISTERED(FilterRow.REGISTRATION),
    ENABLED(FilterRow.STATE),
    DISABLED(FilterRow.STATE),
}

object AppStatusRules {

    /** 絞り込みの初期値：「システム」だけオフ（仕様書 6章）。 */
    val DEFAULT_OFF: Set<FilterChip> = setOf(FilterChip.KIND_SYSTEM)

    /** オフの選択肢から、オンの選択肢を求める（保存はオフの方で行う。新しい選択肢が増えても初期値はオン）。 */
    fun enabledFrom(off: Set<FilterChip>): Set<FilterChip> = FilterChip.entries.toSet() - off

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
        removedAt = null,
        reinstalledAt = null,
        signatureChangedAt = null,
    )

    private fun PolicyState?.hasSomething(): Boolean =
        this != null && (status != ReviewStatus.UNCONFIRMED || !adminMemo.isNullOrBlank() || isPreRegistered)

    // ---- 絞り込み・検索・並び ----

    /** その行で、アプリが当たる選択肢。種類・入手元が分からないアプリ（事前登録だけ）は、その行では常に当たる（null）。 */
    internal fun chipsOf(entry: AppStatusEntry, row: FilterRow): Set<FilterChip>? = when (row) {
        FilterRow.STATUS -> setOf(
            when (entry.status) {
                ReviewStatus.UNCONFIRMED -> FilterChip.STATUS_UNCONFIRMED
                ReviewStatus.CAUTION -> FilterChip.STATUS_CAUTION
                ReviewStatus.OK -> FilterChip.STATUS_OK
            },
        )
        FilterRow.KIND -> entry.kind?.let {
            setOf(
                when (it) {
                    AppKind.USER_INSTALLED -> FilterChip.KIND_USER
                    AppKind.PREINSTALLED -> FilterChip.KIND_PREINSTALLED
                    AppKind.SYSTEM -> FilterChip.KIND_SYSTEM
                },
            )
        }
        // 入手：は後から導入のアプリだけに効かせる（プリインストール・システムは種類で見るので、この行では外さない）
        FilterRow.SOURCE -> if (entry.kind != AppKind.USER_INSTALLED) ALWAYS else entry.source?.let {
            setOf(
                when (it) {
                    InstallSource.GOOGLE_PLAY -> FilterChip.SOURCE_PLAY
                    InstallSource.OTHER -> FilterChip.SOURCE_OTHER
                    InstallSource.UNKNOWN -> FilterChip.SOURCE_UNKNOWN
                },
            )
        }
        // 警告の行は、署名・再導入に関わる警告で見る（Google Play 以外・入手元が分からない は「入手：」の行で絞れる）
        FilterRow.WARNING -> buildSet {
            if (AppWarning.DEBUG_SIGNATURE in entry.warnings) add(FilterChip.WARNING_DEBUG)
            if (AppWarning.SIGNATURE_CHANGED in entry.warnings) add(FilterChip.WARNING_SIGNATURE)
            if (AppWarning.REINSTALLED in entry.warnings) add(FilterChip.WARNING_REINSTALL)
            if (isEmpty()) add(FilterChip.WARNING_NONE)
        }
        FilterRow.DEVICE -> setOf(if (entry.isPresent) FilterChip.DEVICE_PRESENT else FilterChip.DEVICE_ABSENT)
        FilterRow.REGISTRATION -> setOf(if (entry.isPreRegistered) FilterChip.REGISTERED else FilterChip.NOT_REGISTERED)
        // 状態：（有効・無効）は端末にあるアプリで見る。端末にないアプリには有効・無効がないので、
        // 種類が分からないアプリと同じく「この組のどれかがオンなら当たる」
        FilterRow.STATE -> if (!entry.isPresent) null else setOf(if (entry.isEnabled) FilterChip.ENABLED else FilterChip.DISABLED)
    }

    /** その行では外さない（行のチップのオン・オフに関係なく当たる）印。 */
    private val ALWAYS: Set<FilterChip> = emptySet()

    /**
     * 絞り込みに当たるか。行（組）ごとに、オンの選択肢のどれかに当たること。1つの行がすべてオフなら何も当たらない。
     * - 種類が分からない（事前登録だけで一度も入っていない）アプリ、状態：の端末にないアプリは、その行のどれかがオンなら当たる
     * - 入手：の行はプリインストール・システム（と種類が分からないアプリ）には効かず、この行では外さない
     */
    fun matchesFilter(entry: AppStatusEntry, enabled: Set<FilterChip>): Boolean =
        FilterRow.entries.all { row ->
            val chips = chipsOf(entry, row) ?: return@all enabled.any { it.row == row }
            chips === ALWAYS || chips.any { it in enabled }
        }

    /** 検索：アプリ名・パッケージ名の部分一致（大文字・小文字を区別しない）。空なら全部。 */
    fun matchesQuery(entry: AppStatusEntry, query: String): Boolean =
        AppSearch.matches(entry.name, entry.packageName, query)

    /** 並び：要注意 → 未確認 → 問題なし、同じ中はアプリ名の順。 */
    val ORDER: Comparator<AppStatusEntry> = compareBy<AppStatusEntry> {
        when (it.status) {
            ReviewStatus.CAUTION -> 0
            ReviewStatus.UNCONFIRMED -> 1
            ReviewStatus.OK -> 2
        }
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.packageName }

    /** 絞り込み・検索をして並べた、画面に出す行。 */
    fun visible(entries: List<AppStatusEntry>, enabled: Set<FilterChip>, query: String): List<AppStatusEntry> =
        entries.filter { matchesFilter(it, enabled) && matchesQuery(it, query) }.sortedWith(ORDER)

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
