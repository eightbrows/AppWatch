package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 確認状態の画面の絞り込みの組み合わせ（ステップ22で作り、ステップ23のツリーの決まりに直した）。
 * 種類・入手・確認状態・有効／無効・端末・事前登録を組み合わせた行を用意し、各項目をオン・オフした時に出る行を表で確かめる。
 */
class FilterCombinationTest {

    private fun entry(
        pkg: String,
        kind: AppKind?,
        source: InstallSource?,
        status: ReviewStatus,
        present: Boolean = true,
        enabled: Boolean = true,
        preRegistered: Boolean = false,
        warnings: Set<AppWarning> = emptySet(),
        removedAt: Long? = null,
    ) = AppStatusEntry(
        packageName = pkg, name = pkg, versionName = "1.0", kind = kind, source = source,
        installerPackage = null, initiatingPackage = null, warnings = warnings, status = status, statusReason = null,
        adminMemo = null, isPresent = present, isPreRegistered = preRegistered, isEnabled = enabled, isSelf = false,
        firstInstallTime = 0, removedAt = removedAt, reinstalledAt = null, signatureChangedAt = null,
    )

    // 用意したアプリ（レポートの「組み合わせの表」の行と同じ）
    private val rows = listOf(
        entry("sys", AppKind.SYSTEM, InstallSource.UNKNOWN, ReviewStatus.OK),
        entry("pre_play", AppKind.PREINSTALLED, InstallSource.GOOGLE_PLAY, ReviewStatus.OK),
        entry("pre_unknown", AppKind.PREINSTALLED, InstallSource.UNKNOWN, ReviewStatus.UNCONFIRMED),
        entry("pre_disabled", AppKind.PREINSTALLED, InstallSource.UNKNOWN, ReviewStatus.OK, enabled = false),
        entry("user_play", AppKind.USER_INSTALLED, InstallSource.GOOGLE_PLAY, ReviewStatus.UNCONFIRMED),
        entry(
            "user_other", AppKind.USER_INSTALLED, InstallSource.OTHER, ReviewStatus.CAUTION,
            warnings = setOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE),
        ),
        entry("user_unknown", AppKind.USER_INSTALLED, InstallSource.UNKNOWN, ReviewStatus.OK, warnings = setOf(AppWarning.UNKNOWN_SOURCE)),
        entry("user_disabled", AppKind.USER_INSTALLED, InstallSource.GOOGLE_PLAY, ReviewStatus.OK, enabled = false),
        entry(
            "removed", AppKind.USER_INSTALLED, InstallSource.OTHER, ReviewStatus.CAUTION,
            present = false, warnings = setOf(AppWarning.NOT_FROM_PLAY), removedAt = 1L,
        ),
        entry("prereg_absent", null, null, ReviewStatus.CAUTION, present = false, preRegistered = true),
        entry("prereg_installed", AppKind.USER_INSTALLED, InstallSource.GOOGLE_PLAY, ReviewStatus.CAUTION, preRegistered = true),
    )
    private val allIds = rows.map { it.packageName }.toSet()
    private val all = StatusFilter.ALL

    private fun visible(enabled: Set<FilterItem>): Set<String> =
        rows.filter { StatusFilter.matches(it, enabled) }.map { it.packageName }.toSet()

    /** 1つの項目だけをオフにした時に、出なくなる行（ほかの項目はすべてオン）。 */
    private val hiddenWhenOff: Map<FilterItem, Set<String>> = mapOf(
        FilterItem.STATUS_UNCONFIRMED to setOf("pre_unknown", "user_play"),
        FilterItem.STATUS_CAUTION to setOf("user_other", "removed", "prereg_absent", "prereg_installed"),
        FilterItem.STATUS_OK to setOf("sys", "pre_play", "pre_disabled", "user_unknown", "user_disabled"),
        // 端末にある：端末にあるアプリすべて（その下の状態・種類・入手・警告ごと）
        FilterItem.DEVICE_PRESENT to allIds - "removed" - "prereg_absent",
        FilterItem.ENABLED to setOf("sys", "pre_play", "pre_unknown", "user_play", "user_other", "user_unknown", "prereg_installed"),
        // 「無効」をオフにすると、無効にしたアプリが消える
        FilterItem.DISABLED to setOf("pre_disabled", "user_disabled"),
        // 後から導入：端末にある後から導入のアプリだけ（削除したアプリは「端末にない」の下）
        FilterItem.KIND_USER to setOf("user_play", "user_other", "user_unknown", "user_disabled", "prereg_installed"),
        // 入手・警告は端末にある後から導入のアプリだけに効く
        FilterItem.SOURCE_PLAY to setOf("user_play", "user_disabled", "prereg_installed"),
        FilterItem.SOURCE_OTHER to setOf("user_other"),
        FilterItem.SOURCE_UNKNOWN to setOf("user_unknown"),
        FilterItem.WARNING_NONE to setOf("user_play", "user_unknown", "user_disabled", "prereg_installed"),
        FilterItem.WARNING_DEBUG to setOf("user_other"),
        FilterItem.WARNING_SIGNATURE to emptySet(),
        FilterItem.WARNING_REINSTALL to emptySet(),
        FilterItem.KIND_PREINSTALLED to setOf("pre_play", "pre_unknown", "pre_disabled"),
        FilterItem.KIND_SYSTEM to setOf("sys"),
        FilterItem.DEVICE_ABSENT to setOf("removed", "prereg_absent"),
        FilterItem.ABSENT_REMOVED to setOf("removed"),
        FilterItem.ABSENT_PRE_REGISTERED to setOf("prereg_absent"),
    )

    @Test
    fun allItemsOn_showsEverything() {
        assertEquals(allIds, visible(all))
    }

    @Test
    fun defaultHidesOnlySystem() {
        assertEquals(allIds - "sys", visible(StatusFilter.DEFAULT_ENABLED))
    }

    @Test
    fun eachItemOff_hidesExactlyTheExpectedRows() {
        assertEquals("すべての項目を表に入れる", all, hiddenWhenOff.keys)
        hiddenWhenOff.forEach { (item, hidden) ->
            assertEquals("$item をオフ", allIds - hidden, visible(all - item))
        }
    }

    @Test
    fun mainCombinations() {
        // 「無効」だけ（有効と端末にないを外す）：無効のアプリだけ
        assertEquals(setOf("pre_disabled", "user_disabled"), visible(all - FilterItem.ENABLED - FilterItem.DEVICE_ABSENT))
        // 有効だけを外す：端末にないアプリは「端末にない」の下なので残る
        assertEquals(
            setOf("pre_disabled", "user_disabled", "removed", "prereg_absent"),
            visible(all - FilterItem.ENABLED),
        )
        // 端末にないだけ
        assertEquals(setOf("removed", "prereg_absent"), visible(all - FilterItem.DEVICE_PRESENT))
        // 「事前登録だけ」以外を外す
        assertEquals(setOf("prereg_absent"), visible(all - FilterItem.DEVICE_PRESENT - FilterItem.ABSENT_REMOVED))
        // 入手：Google Play 以外だけ：後から導入の Play 外と、入手が効かないもの（プリインストール・システム・端末にない）
        assertEquals(
            setOf("sys", "pre_play", "pre_unknown", "pre_disabled", "user_other", "removed", "prereg_absent"),
            visible(all - FilterItem.SOURCE_PLAY - FilterItem.SOURCE_UNKNOWN),
        )
        // 種類：プリインストールだけ（端末にないアプリには種類は効かない）
        assertEquals(
            setOf("pre_play", "pre_unknown", "pre_disabled", "removed", "prereg_absent"),
            visible(all - FilterItem.KIND_USER - FilterItem.KIND_SYSTEM),
        )
        // 種類：プリインストールだけ ＋ 端末にないを外す
        assertEquals(
            setOf("pre_play", "pre_unknown", "pre_disabled"),
            visible(all - FilterItem.KIND_USER - FilterItem.KIND_SYSTEM - FilterItem.DEVICE_ABSENT),
        )
        // 入手：入手元が分からないをオフ
        assertEquals(allIds - "user_unknown", visible(all - FilterItem.SOURCE_UNKNOWN))
        // 確認状態：要注意だけ
        assertEquals(
            setOf("user_other", "removed", "prereg_absent", "prereg_installed"),
            visible(all - FilterItem.STATUS_UNCONFIRMED - FilterItem.STATUS_OK),
        )
    }

    @Test
    fun parentOffAndBack_keepsChildren() {
        // 「Google Play 以外」をオフにしてから「後から導入」を外し、戻す
        val childOff = all - FilterItem.SOURCE_OTHER
        val parentOff = StatusFilter.toggle(childOff, FilterItem.KIND_USER)
        assertEquals(allIds - setOf("user_play", "user_other", "user_unknown", "user_disabled", "prereg_installed"), visible(parentOff))
        assertEquals(false, StatusFilter.isActive(FilterItem.SOURCE_PLAY, parentOff))
        assertEquals(CheckState.OFF, StatusFilter.checkState(FilterItem.KIND_USER, parentOff))
        val back = StatusFilter.toggle(parentOff, FilterItem.KIND_USER)
        assertEquals(childOff, back)
        assertEquals(allIds - "user_other", visible(back))
        assertEquals(CheckState.PARTIAL, StatusFilter.checkState(FilterItem.KIND_USER, back))
        assertEquals(CheckState.PARTIAL, StatusFilter.checkState(FilterItem.DEVICE_PRESENT, back))
        // 端末にあるを外して戻しても同じ
        val topBack = StatusFilter.toggle(StatusFilter.toggle(back, FilterItem.DEVICE_PRESENT), FilterItem.DEVICE_PRESENT)
        assertEquals(back, topBack)
    }

    @Test
    fun groupAllOff_showsNothingUnderIt() {
        // 確認状態をすべてオフ：何も出ない
        assertEquals(emptySet<String>(), visible(all - FilterItem.STATUS_UNCONFIRMED - FilterItem.STATUS_CAUTION - FilterItem.STATUS_OK))
        // 状態をすべてオフ：端末にあるアプリは出ない、端末にないアプリは出る
        assertEquals(setOf("removed", "prereg_absent"), visible(all - FilterItem.ENABLED - FilterItem.DISABLED))
        // 入手をすべてオフ：後から導入のアプリだけが消える
        val noSource = all - FilterItem.SOURCE_PLAY - FilterItem.SOURCE_OTHER - FilterItem.SOURCE_UNKNOWN
        assertEquals(setOf("sys", "pre_play", "pre_unknown", "pre_disabled", "removed", "prereg_absent"), visible(noSource))
    }

    @Test
    fun savedValues_areReadInTheNewForm() {
        // 今までの名前はそのまま読む（オフはオフのまま）
        val same = StatusFilter.offFromSaved(setOf("KIND_SYSTEM", "DISABLED", "DEVICE_ABSENT", "SOURCE_UNKNOWN"))
        assertEquals(setOf(FilterItem.KIND_SYSTEM, FilterItem.DISABLED, FilterItem.DEVICE_ABSENT, FilterItem.SOURCE_UNKNOWN), same)
        // 「登録：事前登録」オフ →「事前登録だけ」オフ、「登録：なし」オフ →「削除したアプリ」オフ
        assertEquals(setOf(FilterItem.ABSENT_PRE_REGISTERED), StatusFilter.offFromSaved(setOf("REGISTERED")))
        assertEquals(setOf(FilterItem.ABSENT_REMOVED), StatusFilter.offFromSaved(setOf("NOT_REGISTERED")))
        // 「登録：なし」オフを読み替えても、端末にあるアプリは消えない
        val enabled = StatusFilter.enabledFrom(StatusFilter.offFromSaved(setOf("NOT_REGISTERED")))
        assertEquals(allIds - "removed", visible(enabled))
        // 知らない名前は読み飛ばす
        assertEquals(emptySet<FilterItem>(), StatusFilter.offFromSaved(setOf("SOMETHING_OLD")))
        // 保存するのはオフの名前（新しい名前）
        assertEquals(setOf("KIND_SYSTEM", "ABSENT_REMOVED"), StatusFilter.savedNames(all - FilterItem.KIND_SYSTEM - FilterItem.ABSENT_REMOVED))
    }

    @Test
    fun counts_areByAttributeOnly() {
        val counts = StatusFilter.counts(rows)
        assertEquals(9, counts[FilterItem.DEVICE_PRESENT])
        assertEquals(2, counts[FilterItem.DEVICE_ABSENT])
        assertEquals(2, counts[FilterItem.DISABLED])
        assertEquals(5, counts[FilterItem.KIND_USER])
        assertEquals(3, counts[FilterItem.SOURCE_PLAY])
        assertEquals(1, counts[FilterItem.SOURCE_OTHER])
        assertEquals(4, counts[FilterItem.WARNING_NONE])
        assertEquals(1, counts[FilterItem.ABSENT_REMOVED])
        assertEquals(1, counts[FilterItem.ABSENT_PRE_REGISTERED])
        assertEquals(4, counts[FilterItem.STATUS_CAUTION])
    }
}
