package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 確認状態の画面の絞り込みの組み合わせ（ステップ22）。
 * 種類・入手・確認状態・有効／無効・端末・登録を組み合わせた行を用意し、各チップをオン・オフした時に出る行を表で確かめる。
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
    ) = AppStatusEntry(
        packageName = pkg, name = pkg, versionName = "1.0", kind = kind, source = source,
        installerPackage = null, initiatingPackage = null, warnings = warnings, status = status, statusReason = null,
        adminMemo = null, isPresent = present, isPreRegistered = preRegistered, isEnabled = enabled, isSelf = false,
        firstInstallTime = 0, removedAt = null, reinstalledAt = null, signatureChangedAt = null,
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
        entry("removed", AppKind.USER_INSTALLED, InstallSource.OTHER, ReviewStatus.CAUTION, present = false, warnings = setOf(AppWarning.NOT_FROM_PLAY)),
        entry("prereg_absent", null, null, ReviewStatus.CAUTION, present = false, preRegistered = true),
        entry("prereg_installed", AppKind.USER_INSTALLED, InstallSource.GOOGLE_PLAY, ReviewStatus.CAUTION, preRegistered = true),
    )
    private val allIds = rows.map { it.packageName }.toSet()
    private val all = FilterChip.entries.toSet()

    private fun visible(enabled: Set<FilterChip>): Set<String> =
        rows.filter { AppStatusRules.matchesFilter(it, enabled) }.map { it.packageName }.toSet()

    /** 1つのチップだけをオフにした時に、出なくなる行（ほかのチップはすべてオン）。 */
    private val hiddenWhenOff: Map<FilterChip, Set<String>> = mapOf(
        FilterChip.STATUS_UNCONFIRMED to setOf("pre_unknown", "user_play"),
        FilterChip.STATUS_CAUTION to setOf("user_other", "removed", "prereg_absent", "prereg_installed"),
        FilterChip.STATUS_OK to setOf("sys", "pre_play", "pre_disabled", "user_unknown", "user_disabled"),
        FilterChip.KIND_USER to setOf("user_play", "user_other", "user_unknown", "user_disabled", "removed", "prereg_installed"),
        FilterChip.KIND_PREINSTALLED to setOf("pre_play", "pre_unknown", "pre_disabled"),
        FilterChip.KIND_SYSTEM to setOf("sys"),
        // 入手：は後から導入のアプリだけに効く（プリインストールの pre_play・システムの sys は外れない）
        FilterChip.SOURCE_PLAY to setOf("user_play", "user_disabled", "prereg_installed"),
        FilterChip.SOURCE_OTHER to setOf("user_other", "removed"),
        FilterChip.SOURCE_UNKNOWN to setOf("user_unknown"),
        FilterChip.WARNING_NONE to allIds - "user_other",
        FilterChip.WARNING_DEBUG to setOf("user_other"),
        FilterChip.WARNING_SIGNATURE to emptySet(),
        FilterChip.WARNING_REINSTALL to emptySet(),
        FilterChip.DEVICE_PRESENT to allIds - "removed" - "prereg_absent",
        FilterChip.DEVICE_ABSENT to setOf("removed", "prereg_absent"),
        FilterChip.REGISTERED to setOf("prereg_absent", "prereg_installed"),
        FilterChip.NOT_REGISTERED to allIds - "prereg_absent" - "prereg_installed",
        FilterChip.ENABLED to setOf("sys", "pre_play", "pre_unknown", "user_play", "user_other", "user_unknown", "prereg_installed"),
        // 「無効」をオフにすると、無効にしたアプリが消える（今までは「端末：あり」に当たって消えなかった）
        FilterChip.DISABLED to setOf("pre_disabled", "user_disabled"),
    )

    @Test
    fun allChipsOn_showsEverything() {
        assertEquals(allIds, visible(all))
    }

    @Test
    fun eachChipOff_hidesExactlyTheExpectedRows() {
        assertEquals("すべてのチップを表に入れる", all, hiddenWhenOff.keys)
        hiddenWhenOff.forEach { (chip, hidden) ->
            assertEquals("$chip をオフ", allIds - hidden, visible(all - chip))
        }
    }

    @Test
    fun mainCombinations() {
        // 状態：無効だけ（有効をオフ）：無効のアプリと、端末にないアプリ（有効・無効がない）
        assertEquals(
            setOf("pre_disabled", "user_disabled", "removed", "prereg_absent"),
            visible(all - FilterChip.ENABLED),
        )
        // 無効だけ ＋ 端末：ありだけ：無効のアプリだけ
        assertEquals(setOf("pre_disabled", "user_disabled"), visible(all - FilterChip.ENABLED - FilterChip.DEVICE_ABSENT))
        // 端末：なしだけ
        assertEquals(setOf("removed", "prereg_absent"), visible(all - FilterChip.DEVICE_PRESENT))
        // 登録：事前登録だけ
        assertEquals(setOf("prereg_absent", "prereg_installed"), visible(all - FilterChip.NOT_REGISTERED))
        // 入手：Play外だけ：後から導入の Play 外と、入手元で外さないプリインストール・システム・種類が分からないアプリ
        assertEquals(
            setOf("user_other", "removed", "sys", "pre_play", "pre_unknown", "pre_disabled", "prereg_absent"),
            visible(all - FilterChip.SOURCE_PLAY - FilterChip.SOURCE_UNKNOWN),
        )
        // 入手：Play外だけ ＋ 種類：後から導入だけ：後から導入の Play 外と、種類・入手元が分からない事前登録
        assertEquals(
            setOf("user_other", "removed", "prereg_absent"),
            visible(all - FilterChip.SOURCE_PLAY - FilterChip.SOURCE_UNKNOWN - FilterChip.KIND_PREINSTALLED - FilterChip.KIND_SYSTEM),
        )
        // 種類：プリインストールだけ（種類が分からない事前登録は種類の行で外さない）
        assertEquals(
            setOf("pre_play", "pre_unknown", "pre_disabled", "prereg_absent"),
            visible(all - FilterChip.KIND_USER - FilterChip.KIND_SYSTEM),
        )
        // 入手：不明をオフ：後から導入の入手元が分からないアプリだけが消え、プリインストール・システムは残る
        assertEquals(allIds - "user_unknown", visible(all - FilterChip.SOURCE_UNKNOWN))
    }

    @Test
    fun groupAllOff_showsNothing() {
        listOf(
            setOf(FilterChip.DEVICE_PRESENT, FilterChip.DEVICE_ABSENT),
            setOf(FilterChip.REGISTERED, FilterChip.NOT_REGISTERED),
            setOf(FilterChip.ENABLED, FilterChip.DISABLED),
        ).forEach { group -> assertEquals("$group をすべてオフ", emptySet<String>(), visible(all - group)) }
        // 入手：をすべてオフにしても、プリインストール・システム（と種類が分からないもの）は残る
        val noSource = all - FilterChip.SOURCE_PLAY - FilterChip.SOURCE_OTHER - FilterChip.SOURCE_UNKNOWN
        assertEquals(setOf("sys", "pre_play", "pre_unknown", "pre_disabled", "prereg_absent"), visible(noSource))
    }

    @Test
    fun savedOldOffValues_areReadAsTheSameChips() {
        // 前の版で保存した名前（オフにしていたもの）は、同じ名前のチップのオフとして読む。新しいチップはオン
        val oldOff = setOf(FilterChip.KIND_SYSTEM, FilterChip.DISABLED)
        val enabled = AppStatusRules.enabledFrom(oldOff)
        assertEquals(all - FilterChip.KIND_SYSTEM - FilterChip.DISABLED, enabled)
        assert(FilterChip.NOT_REGISTERED in enabled && FilterChip.ENABLED in enabled)
    }
}
