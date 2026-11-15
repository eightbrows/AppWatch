package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.data.status.FilterCondition.Type.EXCLUDE
import io.github.eightbrows.appwatch.data.status.FilterCondition.Type.INCLUDE
import io.github.eightbrows.appwatch.data.status.FilterCondition.Type.ONLY
import org.junit.Assert.assertEquals
import org.junit.Test

/** 一覧の上の条件の1行（「〜を除く」「〜だけ」）の作り方と、札の✕・ボタンの数字（ステップ23）。 */
class FilterConditionTest {

    private val all = StatusFilter.ALL
    private val default = StatusFilter.DEFAULT_ENABLED
    private val systemDefault = FilterCondition(EXCLUDE, FilterItem.KIND_SYSTEM, isDefault = true)

    @Test
    fun default_showsOnlyExcludeSystem_andCountsZero() {
        assertEquals(listOf(systemDefault), StatusFilter.conditions(default))
        assertEquals(0, StatusFilter.changedCount(default))
    }

    @Test
    fun excludedItems_inTreeOrder_afterTheDefaultOne() {
        // 「有効」と「未導入」を外した
        val enabled = default - FilterItem.ENABLED - FilterItem.DEVICE_ABSENT
        assertEquals(
            listOf(systemDefault, FilterCondition(EXCLUDE, FilterItem.ENABLED), FilterCondition(EXCLUDE, FilterItem.DEVICE_ABSENT)),
            StatusFilter.conditions(enabled),
        )
        assertEquals(2, StatusFilter.changedCount(enabled))
    }

    @Test
    fun oneLeftInGroupOfThreeOrMore_isOnly() {
        // 確認状態：要注意だけ
        val enabled = default - FilterItem.STATUS_UNCONFIRMED - FilterItem.STATUS_OK
        assertEquals(listOf(systemDefault, FilterCondition(ONLY, FilterItem.STATUS_CAUTION)), StatusFilter.conditions(enabled))
        // ✕でその組を初期値に戻す
        assertEquals(default, StatusFilter.clear(enabled, FilterCondition(ONLY, FilterItem.STATUS_CAUTION)))
        // 種類：後から導入だけ（プリインストールを外した。システムは初期値でオフ）
        val userOnly = default - FilterItem.KIND_PREINSTALLED
        assertEquals(listOf(FilterCondition(ONLY, FilterItem.KIND_USER)), StatusFilter.conditions(userOnly))
        // 2つの組（有効／無効）は「だけ」にしない
        assertEquals(
            listOf(systemDefault, FilterCondition(EXCLUDE, FilterItem.DISABLED)),
            StatusFilter.conditions(default - FilterItem.DISABLED),
        )
    }

    @Test
    fun twoOffInGroupOfFour_isTwoExcludes() {
        val enabled = default - FilterItem.WARNING_DEBUG - FilterItem.WARNING_SIGNATURE
        assertEquals(
            listOf(systemDefault, FilterCondition(EXCLUDE, FilterItem.WARNING_DEBUG), FilterCondition(EXCLUDE, FilterItem.WARNING_SIGNATURE)),
            StatusFilter.conditions(enabled),
        )
    }

    @Test
    fun clearingExcludeSystem_showsSystem_andIncludeSystemCanBeCleared() {
        val withSystem = StatusFilter.clear(default, systemDefault)
        assertEquals(all, withSystem)
        assertEquals(listOf(FilterCondition(INCLUDE, FilterItem.KIND_SYSTEM)), StatusFilter.conditions(withSystem))
        assertEquals(1, StatusFilter.changedCount(withSystem))
        assertEquals(default, StatusFilter.clear(withSystem, FilterCondition(INCLUDE, FilterItem.KIND_SYSTEM)))
    }

    @Test
    fun clearingExclude_turnsOnlyThatItemOn() {
        val enabled = default - FilterItem.ENABLED - FilterItem.SOURCE_UNKNOWN
        assertEquals(default - FilterItem.SOURCE_UNKNOWN, StatusFilter.clear(enabled, FilterCondition(EXCLUDE, FilterItem.ENABLED)))
    }

    @Test
    fun itemsUnderAnOffParent_areNotShown() {
        // 「後から導入」を外すと、その下の入手の条件は出さない（覚えてはいる）。
        // システムは初期値でオフなので、種類は「プリインストールだけ」になる
        val enabled = default - FilterItem.SOURCE_OTHER - FilterItem.KIND_USER
        assertEquals(listOf(FilterCondition(ONLY, FilterItem.KIND_PREINSTALLED)), StatusFilter.conditions(enabled))
        // ✕で種類を初期値に戻すと、覚えていた入手の条件が出る
        val back = StatusFilter.clear(enabled, FilterCondition(ONLY, FilterItem.KIND_PREINSTALLED))
        assertEquals(listOf(systemDefault, FilterCondition(EXCLUDE, FilterItem.SOURCE_OTHER)), StatusFilter.conditions(back))
        // 「導入済」を外すと、その下（状態・警告・種類・入手）は出さない。「システムを除く」も出さない
        val noPresent = default - FilterItem.DEVICE_PRESENT - FilterItem.ENABLED
        assertEquals(listOf(FilterCondition(EXCLUDE, FilterItem.DEVICE_PRESENT)), StatusFilter.conditions(noPresent))
        assertEquals(1, StatusFilter.changedCount(noPresent))
    }

    @Test
    fun allOn_showsIncludeSystem_only() {
        assertEquals(listOf(FilterCondition(INCLUDE, FilterItem.KIND_SYSTEM)), StatusFilter.conditions(StatusFilter.ALL_ON))
        assertEquals(1, StatusFilter.changedCount(StatusFilter.ALL_ON))
    }

    @Test
    fun allOff_showsTheTopGroupsOnly() {
        // すべてオフ：確認状態の3つと、導入済・未導入。その下の条件は効かないので出さない
        assertEquals(
            listOf(
                FilterCondition(EXCLUDE, FilterItem.STATUS_UNCONFIRMED),
                FilterCondition(EXCLUDE, FilterItem.STATUS_CAUTION),
                FilterCondition(EXCLUDE, FilterItem.STATUS_OK),
                FilterCondition(EXCLUDE, FilterItem.DEVICE_PRESENT),
                FilterCondition(EXCLUDE, FilterItem.DEVICE_ABSENT),
            ),
            StatusFilter.conditions(StatusFilter.ALL_OFF),
        )
        assertEquals(5, StatusFilter.changedCount(StatusFilter.ALL_OFF))
    }

    @Test
    fun warningConditions_followInstalled_notUserInstalled() {
        // 警告は「導入済」の下。「後から導入」を外しても、警告の条件は出る
        val enabled = default - FilterItem.KIND_USER - FilterItem.WARNING_DEBUG
        assertEquals(
            listOf(FilterCondition(EXCLUDE, FilterItem.WARNING_DEBUG), FilterCondition(ONLY, FilterItem.KIND_PREINSTALLED)),
            StatusFilter.conditions(enabled),
        )
        // 並びはツリーの上から：状態 → 警告 → 種類 → 入手
        val order = default - FilterItem.SOURCE_UNKNOWN - FilterItem.WARNING_NONE - FilterItem.DISABLED
        assertEquals(
            listOf(
                systemDefault,
                FilterCondition(EXCLUDE, FilterItem.DISABLED),
                FilterCondition(EXCLUDE, FilterItem.WARNING_NONE),
                FilterCondition(EXCLUDE, FilterItem.SOURCE_UNKNOWN),
            ),
            StatusFilter.conditions(order),
        )
    }
}
