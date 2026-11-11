package io.github.eightbrows.appwatch.data.status

import io.github.eightbrows.appwatch.data.record.PolicyState
import io.github.eightbrows.appwatch.data.record.TrackedApp
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStatusRulesTest {

    private fun entry(
        pkg: String,
        name: String = pkg,
        status: ReviewStatus = ReviewStatus.UNCONFIRMED,
        kind: AppKind? = AppKind.USER_INSTALLED,
        source: InstallSource? = InstallSource.GOOGLE_PLAY,
        warnings: Set<AppWarning> = emptySet(),
        present: Boolean = true,
        enabled: Boolean = true,
        preRegistered: Boolean = false,
    ) = AppStatusEntry(
        packageName = pkg,
        name = name,
        versionName = "1.0",
        kind = kind,
        source = source,
        installerPackage = null,
        initiatingPackage = null,
        warnings = warnings,
        status = status,
        statusReason = null,
        adminMemo = null,
        isPresent = present,
        isPreRegistered = preRegistered,
        isEnabled = enabled,
        isSelf = false,
        firstInstallTime = 0,
        removedAt = null,
        reinstalledAt = null,
        signatureChangedAt = null,
    )

    private val all = FilterItem.entries.toSet()

    // ---- 絞り込み ----

    @Test
    fun 初期値はシステムだけオフ() {
        val enabled = StatusFilter.enabledFrom(StatusFilter.DEFAULT_OFF)
        assertEquals(all - FilterItem.KIND_SYSTEM, enabled)
        assertFalse(AppStatusRules.matchesFilter(entry("sys", kind = AppKind.SYSTEM), enabled))
        assertTrue(AppStatusRules.matchesFilter(entry("user"), enabled))
        assertTrue(AppStatusRules.matchesFilter(entry("pre", kind = AppKind.PREINSTALLED), enabled))
    }

    @Test
    fun 同じ行の中はどれかに当たればよい() {
        val enabled = all - FilterItem.STATUS_OK
        assertTrue(AppStatusRules.matchesFilter(entry("a", status = ReviewStatus.UNCONFIRMED), enabled))
        assertTrue(AppStatusRules.matchesFilter(entry("b", status = ReviewStatus.CAUTION), enabled))
        assertFalse(AppStatusRules.matchesFilter(entry("c", status = ReviewStatus.OK), enabled))
    }

    @Test
    fun 行どうしはすべてに当たる必要がある() {
        // 確認：要注意だけ、入手：Play外だけ
        val enabled = all - FilterItem.STATUS_UNCONFIRMED - FilterItem.STATUS_OK -
            FilterItem.SOURCE_PLAY - FilterItem.SOURCE_UNKNOWN
        assertTrue(AppStatusRules.matchesFilter(entry("a", status = ReviewStatus.CAUTION, source = InstallSource.OTHER), enabled))
        assertFalse(AppStatusRules.matchesFilter(entry("b", status = ReviewStatus.CAUTION, source = InstallSource.GOOGLE_PLAY), enabled))
        assertFalse(AppStatusRules.matchesFilter(entry("c", status = ReviewStatus.UNCONFIRMED, source = InstallSource.OTHER), enabled))
    }

    @Test
    fun 一つの行がすべてオフなら何も出ない() {
        val enabled = all - FilterItem.DEVICE_PRESENT - FilterItem.DEVICE_ABSENT
        assertFalse(AppStatusRules.matchesFilter(entry("a"), enabled))
        assertFalse(AppStatusRules.matchesFilter(entry("b", present = false), enabled))
        // 種類の組がすべてオフなら端末にあるアプリは出ない。端末にないアプリ（事前登録だけ）には種類は効かない
        val noKind = all - FilterItem.KIND_USER - FilterItem.KIND_PREINSTALLED - FilterItem.KIND_SYSTEM
        assertFalse(AppStatusRules.matchesFilter(entry("c"), noKind))
        assertTrue(AppStatusRules.matchesFilter(entry("d", kind = null, source = null, present = false, preRegistered = true), noKind))
    }

    @Test
    fun 警告の行は署名と再導入の警告で見る() {
        val onlyNone = all - FilterItem.WARNING_DEBUG - FilterItem.WARNING_SIGNATURE - FilterItem.WARNING_REINSTALL
        val onlyReinstall = all - FilterItem.WARNING_NONE - FilterItem.WARNING_DEBUG - FilterItem.WARNING_SIGNATURE
        val reinstalled = entry("r", warnings = setOf(AppWarning.REINSTALLED, AppWarning.NOT_FROM_PLAY))
        val notFromPlay = entry("n", warnings = setOf(AppWarning.NOT_FROM_PLAY))
        assertFalse(AppStatusRules.matchesFilter(reinstalled, onlyNone))
        assertTrue(AppStatusRules.matchesFilter(reinstalled, onlyReinstall))
        // 「Google Play 以外」は警告の行では「警告なし」扱い（入手：の行で絞る）
        assertTrue(AppStatusRules.matchesFilter(notFromPlay, onlyNone))
        assertFalse(AppStatusRules.matchesFilter(notFromPlay, onlyReinstall))
    }

    @Test
    fun 状態は端末にあるアプリだけに効く() {
        // 状態：無効だけ（有効をオフ）
        val onlyDisabled = all - FilterItem.ENABLED
        assertTrue(AppStatusRules.matchesFilter(entry("d", enabled = false), onlyDisabled))
        assertFalse(AppStatusRules.matchesFilter(entry("e"), onlyDisabled))
        // 端末にないアプリには状態は効かない
        assertTrue(AppStatusRules.matchesFilter(entry("e2", present = false), onlyDisabled))
        // 端末にないだけ
        val onlyAbsent = all - FilterItem.DEVICE_PRESENT
        assertTrue(AppStatusRules.matchesFilter(entry("f", present = false), onlyAbsent))
        assertFalse(AppStatusRules.matchesFilter(entry("g"), onlyAbsent))
        // 「無効」をオフにすると無効のアプリが消える（今までは「端末：あり」に当たって消えなかった）
        assertFalse(AppStatusRules.matchesFilter(entry("h", enabled = false), all - FilterItem.DISABLED))
    }

    // ---- 検索 ----

    @Test
    fun 検索はアプリ名とパッケージ名の部分一致で大文字小文字を区別しない() {
        val qr = entry("com.example.qrreader", name = "QRリーダー")
        assertTrue(AppStatusRules.matchesQuery(qr, "qr"))
        assertTrue(AppStatusRules.matchesQuery(qr, "EXAMPLE"))
        assertTrue(AppStatusRules.matchesQuery(qr, "リーダー"))
        assertTrue(AppStatusRules.matchesQuery(qr, "  "))
        assertFalse(AppStatusRules.matchesQuery(qr, "weather"))
    }

    @Test
    fun 検索と絞り込みは両方に当たるものだけ出す() {
        val entries = listOf(
            entry("com.example.qrreader", name = "QRリーダー", status = ReviewStatus.CAUTION),
            entry("com.example.qrscan", name = "QRスキャン", status = ReviewStatus.OK),
            entry("com.example.weather", name = "天気ナビ", status = ReviewStatus.CAUTION),
        )
        val noOk = all - FilterItem.STATUS_OK
        assertEquals(listOf("com.example.qrreader"), AppStatusRules.visible(entries, noOk, "qr").map { it.packageName })
    }

    // ---- 並び ----

    @Test
    fun 並びは要注意_未確認_問題なしで同じ中は名前の順() {
        val entries = listOf(
            entry("1", name = "b", status = ReviewStatus.OK),
            entry("2", name = "B", status = ReviewStatus.UNCONFIRMED),
            entry("3", name = "a", status = ReviewStatus.UNCONFIRMED),
            entry("4", name = "z", status = ReviewStatus.CAUTION),
            entry("5", name = "c", status = ReviewStatus.CAUTION),
        )
        assertEquals(listOf("5", "4", "3", "2", "1"), AppStatusRules.visible(entries, all, "").map { it.packageName })
    }

    // ---- まとめて「問題なし」 ----

    @Test
    fun まとめて問題なしは警告のあるアプリを除く() {
        val plan = AppStatusRules.bulkOkPlan(
            listOf(
                entry("a"),
                entry("b", status = ReviewStatus.CAUTION),
                entry("c", warnings = setOf(AppWarning.REINSTALLED)),
                entry("d", warnings = setOf(AppWarning.NOT_FROM_PLAY)),
                entry("e", status = ReviewStatus.OK), // すでに問題なし
            ),
        )
        assertEquals(listOf("a", "b"), plan.targets)
        assertEquals(2, plan.skippedWithWarnings)
    }

    @Test
    fun すべて警告ありなら対象はない() {
        val plan = AppStatusRules.bulkOkPlan(listOf(entry("a", warnings = setOf(AppWarning.DEBUG_SIGNATURE))))
        assertTrue(plan.targets.isEmpty())
        assertEquals(1, plan.skippedWithWarnings)
    }

    // ---- 一覧の行 ----

    @Test
    fun 端末にないアプリは設定が残っているものだけ出す() {
        fun tracked(pkg: String, present: Boolean) = TrackedApp(
            packageName = pkg, name = pkg, versionName = "1", versionCode = 1, kind = AppKind.USER_INSTALLED,
            source = InstallSource.GOOGLE_PLAY, installerPackage = null, initiatingPackage = null, isEnabled = true,
            staticWarnings = emptySet(), signerSha256 = emptyList(), firstInstallTime = 1, lastUpdateTime = 1,
            isPresent = present, firstSeenAt = 1, addedAt = null, removedAt = if (present) null else 5,
            reinstalledAt = null, signatureChangedAt = null, updatedAt = 1,
        )
        fun policy(pkg: String, status: ReviewStatus = ReviewStatus.UNCONFIRMED, memo: String? = null, pre: Boolean = false) =
            PolicyState(pkg, status, memo, pre, provisionalName = if (pre) "仮の名前" else null, statusReason = null, updatedAt = 1)

        val entries = AppStatusRules.buildEntries(
            tracked = listOf(
                tracked("present", present = true),
                tracked("gone_plain", present = false),
                tracked("gone_caution", present = false),
                tracked("gone_memo", present = false),
                tracked("gone_unconfirmed_policy", present = false),
            ),
            policies = listOf(
                policy("gone_caution", ReviewStatus.CAUTION),
                policy("gone_memo", memo = "メモ"),
                policy("gone_unconfirmed_policy"),
                policy("pre_only", ReviewStatus.CAUTION, pre = true),
            ).associateBy { it.packageName },
            selfPackageName = "self",
        )
        assertEquals(
            setOf("present", "gone_caution", "gone_memo", "pre_only"),
            entries.map { it.packageName }.toSet(),
        )
        val preOnly = entries.first { it.packageName == "pre_only" }
        assertEquals("仮の名前", preOnly.name)
        assertFalse(preOnly.isPresent)
        assertTrue(preOnly.isPreRegistered)
    }
}
