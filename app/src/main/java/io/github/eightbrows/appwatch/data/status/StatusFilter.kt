package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus

// アプリの確認状態の画面の絞り込み（仕様書 6章）。親子の関係があるツリーで、Android を使わない純粋な関数だけを置く。
//
//   確認状態：未確認／要注意／問題なし（すべてのアプリに効く。ツリーの外）
//   導入済
//     状態：有効／無効
//     警告：なし／開発用の署名／署名が変わった／再インストール（1つのアプリに複数付くことがある）
//     種類：
//       後から導入
//         入手：Google Play／Google Play 以外／入手元が分からない
//       プリインストール
//       システム
//   未導入
//     削除したアプリ
//     事前登録だけ

/**
 * 絞り込みの組。同じ組の中は「どれかがオンなら当たる」、組どうしは「道のりの組がすべて当たる」。
 * [parent] はその組の上の項目（その項目がオフなら、この組は効かず灰色）。
 */
enum class FilterGroup {
    STATUS, DEVICE, STATE, KIND, SOURCE, WARNING, ABSENT;

    val parent: FilterItem?
        get() = when (this) {
            STATUS, DEVICE -> null
            STATE, WARNING, KIND -> FilterItem.DEVICE_PRESENT
            SOURCE -> FilterItem.KIND_USER
            ABSENT -> FilterItem.DEVICE_ABSENT
        }
}

/**
 * 絞り込みの項目（ツリーの上からの順）。保存は名前で行うので、名前を変えるときは [StatusFilter.LEGACY_NAMES] に読み替えを足す。
 * 名前の DEVICE_PRESENT／DEVICE_ABSENT は、画面の「導入済／未導入」。
 */
enum class FilterItem(val group: FilterGroup) {
    STATUS_UNCONFIRMED(FilterGroup.STATUS),
    STATUS_CAUTION(FilterGroup.STATUS),
    STATUS_OK(FilterGroup.STATUS),
    DEVICE_PRESENT(FilterGroup.DEVICE),
    ENABLED(FilterGroup.STATE),
    DISABLED(FilterGroup.STATE),
    WARNING_NONE(FilterGroup.WARNING),
    WARNING_DEBUG(FilterGroup.WARNING),
    WARNING_SIGNATURE(FilterGroup.WARNING),
    WARNING_REINSTALL(FilterGroup.WARNING),
    KIND_USER(FilterGroup.KIND),
    SOURCE_PLAY(FilterGroup.SOURCE),
    SOURCE_OTHER(FilterGroup.SOURCE),
    SOURCE_UNKNOWN(FilterGroup.SOURCE),
    KIND_PREINSTALLED(FilterGroup.KIND),
    KIND_SYSTEM(FilterGroup.KIND),
    DEVICE_ABSENT(FilterGroup.DEVICE),
    ABSENT_REMOVED(FilterGroup.ABSENT),
    ABSENT_PRE_REGISTERED(FilterGroup.ABSENT),
    ;

    /** すぐ上の項目（確認状態・導入済・未導入は null）。 */
    val parent: FilterItem? get() = group.parent
}

/**
 * 初期値と違う条件。「絞り込み」ボタンの数字（[StatusFilter.changedCount]）を数えるのに使う。
 * - [EXCLUDE]：外した項目
 * - [ONLY]：同じ組（3つ以上）で1つだけ残っている（組で1つと数える）
 * - [INCLUDE]：初期値でオフの「システム」をオンにした
 * [isDefault] は初期値のままの条件（「システム」がオフ）。数には入れない。
 */
data class FilterCondition(val type: Type, val item: FilterItem, val isDefault: Boolean = false) {
    enum class Type { EXCLUDE, ONLY, INCLUDE }
}

object StatusFilter {

    val ALL: Set<FilterItem> = FilterItem.entries.toSet()

    /** 初期値でオフの項目：「システム」だけ（仕様書 6章）。 */
    val DEFAULT_OFF: Set<FilterItem> = setOf(FilterItem.KIND_SYSTEM)

    val DEFAULT_ENABLED: Set<FilterItem> = ALL - DEFAULT_OFF

    /** 「すべてオン」：すべての項目をオン（システムも出る）。 */
    val ALL_ON: Set<FilterItem> = ALL

    /** 「すべてオフ」：すべての項目をオフ（表示は 0件）。 */
    val ALL_OFF: Set<FilterItem> = emptySet()

    /** オフの項目から、オンの項目を求める（保存はオフの方で行う。新しい項目が増えても初期値はオン）。 */
    fun enabledFrom(off: Set<FilterItem>): Set<FilterItem> = ALL - off

    /** 前の版で保存した名前の読み替え（ステップ22の「登録：」の組）。 */
    val LEGACY_NAMES: Map<String, FilterItem> = mapOf(
        "REGISTERED" to FilterItem.ABSENT_PRE_REGISTERED,
        "NOT_REGISTERED" to FilterItem.ABSENT_REMOVED,
    )

    /** 保存したオフの名前を項目にする。今の形に合わない名前が1つでもあれば、初期値に戻す。 */
    fun offFromSaved(saved: Set<String>): Set<FilterItem> {
        val items = saved.map { name -> FilterItem.entries.firstOrNull { it.name == name } ?: LEGACY_NAMES[name] }
        return if (items.any { it == null }) DEFAULT_OFF else items.filterNotNullTo(HashSet())
    }

    /** 保存する名前（オフの項目）。 */
    fun savedNames(enabled: Set<FilterItem>): Set<String> = (ALL - enabled).mapTo(HashSet()) { it.name }

    // ---- アプリの位置 ----

    /** 未導入のアプリのうち、事前登録だけで一度も入っていないもの（削除したアプリを事前登録したものは「削除したアプリ」）。 */
    fun isPreRegisteredOnly(entry: AppStatusEntry): Boolean =
        !entry.isPresent && entry.isPreRegistered && entry.kind == null && entry.removedAt == null

    /**
     * アプリがツリーで通る組と、その組で当たる項目。null は「その組のどれかがオンなら当たる」（種類・入手元が分からない時）。
     * 確認状態はすべてのアプリに効く。
     */
    internal fun pathOf(entry: AppStatusEntry): List<Pair<FilterGroup, Set<FilterItem>?>> = buildList {
        add(FilterGroup.STATUS to setOf(statusItem(entry.status)))
        if (!entry.isPresent) {
            add(FilterGroup.DEVICE to setOf(FilterItem.DEVICE_ABSENT))
            add(
                FilterGroup.ABSENT to setOf(
                    if (isPreRegisteredOnly(entry)) FilterItem.ABSENT_PRE_REGISTERED else FilterItem.ABSENT_REMOVED,
                ),
            )
            return@buildList
        }
        add(FilterGroup.DEVICE to setOf(FilterItem.DEVICE_PRESENT))
        add(FilterGroup.STATE to setOf(if (entry.isEnabled) FilterItem.ENABLED else FilterItem.DISABLED))
        // 警告は導入済のアプリ全部（後から導入・プリインストール・システム）に効く
        add(FilterGroup.WARNING to warningItems(entry.warnings))
        add(FilterGroup.KIND to entry.kind?.let { setOf(kindItem(it)) })
        // 入手は後から導入のアプリだけ
        if (entry.kind == AppKind.USER_INSTALLED) {
            add(FilterGroup.SOURCE to entry.source?.let { setOf(sourceItem(it)) })
        }
    }

    /** 絞り込みに当たるか：道のりの組ごとに、自分の項目のどれかがオン。 */
    fun matches(entry: AppStatusEntry, enabled: Set<FilterItem>): Boolean =
        pathOf(entry).all { (group, items) ->
            if (items == null) enabled.any { it.group == group } else items.any { it in enabled }
        }

    private fun statusItem(status: ReviewStatus) = when (status) {
        ReviewStatus.UNCONFIRMED -> FilterItem.STATUS_UNCONFIRMED
        ReviewStatus.CAUTION -> FilterItem.STATUS_CAUTION
        ReviewStatus.OK -> FilterItem.STATUS_OK
    }

    private fun kindItem(kind: AppKind) = when (kind) {
        AppKind.USER_INSTALLED -> FilterItem.KIND_USER
        AppKind.PREINSTALLED -> FilterItem.KIND_PREINSTALLED
        AppKind.SYSTEM -> FilterItem.KIND_SYSTEM
    }

    private fun sourceItem(source: InstallSource) = when (source) {
        InstallSource.GOOGLE_PLAY -> FilterItem.SOURCE_PLAY
        InstallSource.OTHER -> FilterItem.SOURCE_OTHER
        InstallSource.UNKNOWN -> FilterItem.SOURCE_UNKNOWN
    }

    /** 警告の組は、署名・再インストールの警告で見る（Google Play 以外・入手元が分からない は「入手」で絞る）。 */
    private fun warningItems(warnings: Set<AppWarning>): Set<FilterItem> = buildSet {
        if (AppWarning.DEBUG_SIGNATURE in warnings) add(FilterItem.WARNING_DEBUG)
        if (AppWarning.SIGNATURE_CHANGED in warnings) add(FilterItem.WARNING_SIGNATURE)
        if (AppWarning.REINSTALLED in warnings) add(FilterItem.WARNING_REINSTALL)
        if (isEmpty()) add(FilterItem.WARNING_NONE)
    }

    /**
     * 各項目の件数：その項目に当てはまるアプリの数（ほかの条件・検索には関係しない）。
     * 状態・警告は導入済のアプリ全部で、入手は導入済の後から導入のアプリで数える。警告が2つあるアプリは両方に数える。
     */
    fun counts(entries: List<AppStatusEntry>): Map<FilterItem, Int> {
        val counts = HashMap<FilterItem, Int>()
        entries.forEach { entry ->
            pathOf(entry).forEach { (_, items) -> items?.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        }
        return FilterItem.entries.associateWith { counts[it] ?: 0 }
    }

    // ---- ツリーの見た目 ----

    /** その項目が効いているか：上の項目がすべてオン（自分のオン・オフは見ない）。オフの親の下は灰色で、効かない。 */
    fun isActive(item: FilterItem, enabled: Set<FilterItem>): Boolean {
        var parent = item.parent
        while (parent != null) {
            if (parent !in enabled) return false
            parent = parent.parent
        }
        return true
    }

    /** 1つの項目のオン・オフを切り替える（下の項目のオン・オフは覚えたまま）。 */
    fun toggle(enabled: Set<FilterItem>, item: FilterItem): Set<FilterItem> =
        if (item in enabled) enabled - item else enabled + item

    // ---- 初期値と違う条件（「絞り込み」ボタンの数字） ----

    /** 初期値と違う条件。効いていない組（オフの親の下）は数えない。 */
    fun conditions(enabled: Set<FilterItem>): List<FilterCondition> {
        val list = FilterGroup.entries.flatMap { group ->
            val parent = group.parent
            if (parent != null && (parent !in enabled || !isActive(parent, enabled))) emptyList()
            else groupConditions(group, enabled)
        }
        // 初期値の札（「システムを除く」）を先に、あとはツリーの上からの順
        return list.sortedWith(compareBy({ !it.isDefault }, { it.item.ordinal }))
    }

    private fun groupConditions(group: FilterGroup, enabled: Set<FilterItem>): List<FilterCondition> {
        val items = FilterItem.entries.filter { it.group == group }
        val on = items.filter { it in enabled }
        val defaultOn = items.filter { it !in DEFAULT_OFF }
        if (on == defaultOn) {
            // 初期値のまま。「システムを除く」は分かりやすさのため出す（数には入れない）
            return items.filter { it in DEFAULT_OFF }.map { FilterCondition(FilterCondition.Type.EXCLUDE, it, isDefault = true) }
        }
        // 3つ以上の組で1つだけ残っている時は「〜だけ」
        if (items.size >= 3 && on.size == 1) return listOf(FilterCondition(FilterCondition.Type.ONLY, on.single()))
        return items.mapNotNull { item ->
            val isOn = item in enabled
            when {
                item in DEFAULT_OFF && isOn -> FilterCondition(FilterCondition.Type.INCLUDE, item)
                item in DEFAULT_OFF -> FilterCondition(FilterCondition.Type.EXCLUDE, item, isDefault = true)
                !isOn -> FilterCondition(FilterCondition.Type.EXCLUDE, item)
                else -> null
            }
        }
    }

    /** 初期値と違う条件の数（「絞り込み」ボタンの数字）。 */
    fun changedCount(enabled: Set<FilterItem>): Int = conditions(enabled).count { !it.isDefault }
}
