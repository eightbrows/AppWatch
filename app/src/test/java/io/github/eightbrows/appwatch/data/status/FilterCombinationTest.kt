package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 確認状態の画面の絞り込みの組み合わせ（ステップ22で作り、ステップ23のツリーの決まり、ステップ25の「警告は導入済の全種類に効く」に直した）。
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
        // プリインストールにも警告が付く（署名が変わった）
        entry(
            "pre_signature", AppKind.PREINSTALLED, InstallSource.GOOGLE_PLAY, ReviewStatus.UNCONFIRMED,
            warnings = setOf(AppWarning.SIGNATURE_CHANGED),
        ),
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
        FilterItem.STATUS_UNCONFIRMED to setOf("pre_unknown", "pre_signature", "user_play"),
        FilterItem.STATUS_CAUTION to setOf("user_other", "removed", "prereg_absent", "prereg_installed"),
        FilterItem.STATUS_OK to setOf("sys", "pre_play", "pre_disabled", "user_unknown", "user_disabled"),
        // 導入済：導入済のアプリすべて（その下の状態・警告・種類・入手ごと）
        FilterItem.DEVICE_PRESENT to allIds - "removed" - "prereg_absent",
        FilterItem.ENABLED to setOf("sys", "pre_play", "pre_unknown", "pre_signature", "user_play", "user_other", "user_unknown", "prereg_installed"),
        // 「無効」をオフにすると、無効にしたアプリが消える
        FilterItem.DISABLED to setOf("pre_disabled", "user_disabled"),
        // 警告は導入済のアプリ全部（後から導入・プリインストール・システム）に効く。警告のないアプリは「なし」
        FilterItem.WARNING_NONE to setOf("sys", "pre_play", "pre_unknown", "pre_disabled", "user_play", "user_unknown", "user_disabled", "prereg_installed"),
        FilterItem.WARNING_DEBUG to setOf("user_other"),
        FilterItem.WARNING_SIGNATURE to setOf("pre_signature"),
        FilterItem.WARNING_REINSTALL to emptySet(),
        // 後から導入：導入済の後から導入のアプリだけ（削除したアプリは「未導入」の下）
        FilterItem.KIND_USER to setOf("user_play", "user_other", "user_unknown", "user_disabled", "prereg_installed"),
        // 入手は導入済の後から導入のアプリだけに効く
        FilterItem.SOURCE_PLAY to setOf("user_play", "user_disabled", "prereg_installed"),
        FilterItem.SOURCE_OTHER to setOf("user_other"),
        FilterItem.SOURCE_UNKNOWN to setOf("user_unknown"),
        FilterItem.KIND_PREINSTALLED to setOf("pre_play", "pre_unknown", "pre_disabled", "pre_signature"),
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
        // 「無効」だけ（有効と未導入を外す）：無効のアプリだけ
        assertEquals(setOf("pre_disabled", "user_disabled"), visible(all - FilterItem.ENABLED - FilterItem.DEVICE_ABSENT))
        // 有効だけを外す：未導入のアプリは「未導入」の下なので残る
        assertEquals(
            setOf("pre_disabled", "user_disabled", "removed", "prereg_absent"),
            visible(all - FilterItem.ENABLED),
        )
        // 未導入だけ
        assertEquals(setOf("removed", "prereg_absent"), visible(all - FilterItem.DEVICE_PRESENT))
        // 「事前登録だけ」以外を外す
        assertEquals(setOf("prereg_absent"), visible(all - FilterItem.DEVICE_PRESENT - FilterItem.ABSENT_REMOVED))
        // 入手：Google Play 以外だけ：後から導入の Play 外と、入手が効かないもの（プリインストール・システム・未導入）
        assertEquals(
            setOf("sys", "pre_play", "pre_unknown", "pre_disabled", "pre_signature", "user_other", "removed", "prereg_absent"),
            visible(all - FilterItem.SOURCE_PLAY - FilterItem.SOURCE_UNKNOWN),
        )
        // 種類：プリインストールだけ（未導入のアプリには種類は効かない）
        assertEquals(
            setOf("pre_play", "pre_unknown", "pre_disabled", "pre_signature", "removed", "prereg_absent"),
            visible(all - FilterItem.KIND_USER - FilterItem.KIND_SYSTEM),
        )
        // 種類：プリインストールだけ ＋ 未導入を外す
        assertEquals(
            setOf("pre_play", "pre_unknown", "pre_disabled", "pre_signature"),
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
    fun warnings_applyToEveryInstalledKind() {
        // 警告：「なし」を外す：警告のあるアプリ（後から導入でもプリインストールでも）と、警告が効かない未導入
        assertEquals(
            setOf("pre_signature", "user_other", "removed", "prereg_absent"),
            visible(all - FilterItem.WARNING_NONE),
        )
        // 警告：署名変更だけ
        assertEquals(
            setOf("pre_signature", "removed", "prereg_absent"),
            visible(all - FilterItem.WARNING_NONE - FilterItem.WARNING_DEBUG - FilterItem.WARNING_REINSTALL),
        )
        // 「後から導入」を外しても、警告はプリインストール・システムに効いたまま
        assertEquals(
            setOf("pre_signature", "removed", "prereg_absent"),
            visible(all - FilterItem.KIND_USER - FilterItem.WARNING_NONE),
        )
        assertEquals(true, StatusFilter.isActive(FilterItem.WARNING_NONE, all - FilterItem.KIND_USER))
        assertEquals(false, StatusFilter.isActive(FilterItem.WARNING_NONE, all - FilterItem.DEVICE_PRESENT))
        // 警告が複数あるアプリは、どれか1つがオンなら表示
        val both = entry(
            "sys_both", AppKind.SYSTEM, InstallSource.UNKNOWN, ReviewStatus.OK,
            warnings = setOf(AppWarning.DEBUG_SIGNATURE, AppWarning.REINSTALLED),
        )
        assertEquals(true, StatusFilter.matches(both, all - FilterItem.WARNING_DEBUG))
        assertEquals(true, StatusFilter.matches(both, all - FilterItem.WARNING_REINSTALL))
        assertEquals(false, StatusFilter.matches(both, all - FilterItem.WARNING_DEBUG - FilterItem.WARNING_REINSTALL))
        // 入手元の警告だけのアプリは「なし」
        val sourceOnly = entry(
            "user_src", AppKind.USER_INSTALLED, InstallSource.OTHER, ReviewStatus.OK, warnings = setOf(AppWarning.NOT_FROM_PLAY),
        )
        assertEquals(false, StatusFilter.matches(sourceOnly, all - FilterItem.WARNING_NONE))
    }

    @Test
    fun allOnAndAllOff() {
        assertEquals(allIds, visible(StatusFilter.ALL_ON))
        assertEquals(emptySet<String>(), visible(StatusFilter.ALL_OFF))
        assertEquals(StatusFilter.ALL, StatusFilter.ALL_ON)
        // すべてオフから「初期値に戻す」と同じ値にすれば、システム以外が出る
        assertEquals(allIds - "sys", visible(StatusFilter.DEFAULT_ENABLED))
        // すべてオフの後に親だけをオンにしても、下がオフなら出ない
        assertEquals(emptySet<String>(), visible(StatusFilter.ALL_OFF + FilterItem.DEVICE_PRESENT + FilterItem.DEVICE_ABSENT))
    }

    @Test
    fun parentOffAndBack_keepsChildren() {
        // 「Google Play 以外」をオフにしてから「後から導入」を外し、戻す
        val childOff = all - FilterItem.SOURCE_OTHER
        val parentOff = StatusFilter.toggle(childOff, FilterItem.KIND_USER)
        assertEquals(allIds - setOf("user_play", "user_other", "user_unknown", "user_disabled", "prereg_installed"), visible(parentOff))
        assertEquals(false, StatusFilter.isActive(FilterItem.SOURCE_PLAY, parentOff))
        val back = StatusFilter.toggle(parentOff, FilterItem.KIND_USER)
        assertEquals(childOff, back)
        assertEquals(allIds - "user_other", visible(back))
        assertEquals(true, StatusFilter.isActive(FilterItem.SOURCE_PLAY, back))
        // 導入済を外して戻しても同じ
        val topBack = StatusFilter.toggle(StatusFilter.toggle(back, FilterItem.DEVICE_PRESENT), FilterItem.DEVICE_PRESENT)
        assertEquals(back, topBack)
    }

    @Test
    fun groupAllOff_showsNothingUnderIt() {
        // 確認状態をすべてオフ：何も出ない
        assertEquals(emptySet<String>(), visible(all - FilterItem.STATUS_UNCONFIRMED - FilterItem.STATUS_CAUTION - FilterItem.STATUS_OK))
        // 状態をすべてオフ：導入済のアプリは出ない、未導入のアプリは出る
        assertEquals(setOf("removed", "prereg_absent"), visible(all - FilterItem.ENABLED - FilterItem.DISABLED))
        // 警告をすべてオフ：同じく導入済のアプリは出ない
        assertEquals(
            setOf("removed", "prereg_absent"),
            visible(all - FilterItem.WARNING_NONE - FilterItem.WARNING_DEBUG - FilterItem.WARNING_SIGNATURE - FilterItem.WARNING_REINSTALL),
        )
        // 入手をすべてオフ：後から導入のアプリだけが消える
        val noSource = all - FilterItem.SOURCE_PLAY - FilterItem.SOURCE_OTHER - FilterItem.SOURCE_UNKNOWN
        assertEquals(setOf("sys", "pre_play", "pre_unknown", "pre_disabled", "pre_signature", "removed", "prereg_absent"), visible(noSource))
    }

    @Test
    fun savedValues_areReadInTheNewForm() {
        // 今までの名前はそのまま読む（オフはオフのまま）
        val same = StatusFilter.offFromSaved(setOf("KIND_SYSTEM", "DISABLED", "DEVICE_ABSENT", "SOURCE_UNKNOWN"))
        assertEquals(setOf(FilterItem.KIND_SYSTEM, FilterItem.DISABLED, FilterItem.DEVICE_ABSENT, FilterItem.SOURCE_UNKNOWN), same)
        // 「登録：事前登録」オフ →「事前登録だけ」オフ、「登録：なし」オフ →「削除したアプリ」オフ
        assertEquals(setOf(FilterItem.ABSENT_PRE_REGISTERED), StatusFilter.offFromSaved(setOf("REGISTERED")))
        assertEquals(setOf(FilterItem.ABSENT_REMOVED), StatusFilter.offFromSaved(setOf("NOT_REGISTERED")))
        // 「登録：なし」オフを読み替えても、導入済のアプリは消えない
        val enabled = StatusFilter.enabledFrom(StatusFilter.offFromSaved(setOf("NOT_REGISTERED")))
        assertEquals(allIds - "removed", visible(enabled))
        // 今の形に合わない名前があれば、初期値に戻す（システムだけオフ）
        assertEquals(StatusFilter.DEFAULT_OFF, StatusFilter.offFromSaved(setOf("SOMETHING_OLD")))
        assertEquals(StatusFilter.DEFAULT_OFF, StatusFilter.offFromSaved(setOf("DISABLED", "SOMETHING_OLD")))
        // すべてオフ・すべてオンも保存して読める
        assertEquals(StatusFilter.ALL_OFF, StatusFilter.enabledFrom(StatusFilter.offFromSaved(StatusFilter.savedNames(StatusFilter.ALL_OFF))))
        assertEquals(StatusFilter.ALL_ON, StatusFilter.enabledFrom(StatusFilter.offFromSaved(StatusFilter.savedNames(StatusFilter.ALL_ON))))
        // 保存するのはオフの名前（新しい名前）
        assertEquals(setOf("KIND_SYSTEM", "ABSENT_REMOVED"), StatusFilter.savedNames(all - FilterItem.KIND_SYSTEM - FilterItem.ABSENT_REMOVED))
    }

    @Test
    fun counts_areByAttributeOnly() {
        val counts = StatusFilter.counts(rows)
        assertEquals(10, counts[FilterItem.DEVICE_PRESENT])
        assertEquals(2, counts[FilterItem.DEVICE_ABSENT])
        assertEquals(2, counts[FilterItem.DISABLED])
        assertEquals(5, counts[FilterItem.KIND_USER])
        assertEquals(3, counts[FilterItem.SOURCE_PLAY])
        assertEquals(1, counts[FilterItem.SOURCE_OTHER])
        // 警告は導入済の全種類で数える（10件のうち、警告があるのは user_other と pre_signature）
        assertEquals(8, counts[FilterItem.WARNING_NONE])
        assertEquals(1, counts[FilterItem.WARNING_DEBUG])
        assertEquals(1, counts[FilterItem.WARNING_SIGNATURE])
        assertEquals(0, counts[FilterItem.WARNING_REINSTALL])
        assertEquals(4, counts[FilterItem.KIND_PREINSTALLED])
        assertEquals(1, counts[FilterItem.ABSENT_REMOVED])
        assertEquals(1, counts[FilterItem.ABSENT_PRE_REGISTERED])
        assertEquals(4, counts[FilterItem.STATUS_CAUTION])
    }
}
