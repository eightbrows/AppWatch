package io.github.eightbrows.appwatch.ui.setup

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SetupSelectionTest {

    private fun app(
        pkg: String,
        kind: AppKind = AppKind.USER_INSTALLED,
        source: InstallSource = InstallSource.GOOGLE_PLAY,
        warnings: Set<AppWarning> = emptySet(),
        status: ReviewStatus = ReviewStatus.UNCONFIRMED,
        isSelf: Boolean = false,
    ) = AppSummary(
        packageName = pkg,
        name = pkg,
        versionName = "1",
        kind = kind,
        source = source,
        warnings = warnings,
        reviewStatus = status,
        adminMemo = null,
        installedAt = LocalDateTime.of(2026, 10, 1, 0, 0),
        isNew = false,
        isSelf = isSelf,
    )

    private val self = app(
        "io.github.eightbrows.appwatch",
        source = InstallSource.OTHER,
        warnings = setOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE),
        isSelf = true,
    )

    private val apps = listOf(
        app("sys1", kind = AppKind.SYSTEM, source = InstallSource.UNKNOWN),
        app("sys2", kind = AppKind.SYSTEM, source = InstallSource.GOOGLE_PLAY),
        app("pre", kind = AppKind.PREINSTALLED, source = InstallSource.UNKNOWN),
        app("play"),
        app("play_debug", warnings = setOf(AppWarning.DEBUG_SIGNATURE)),
        app("other", source = InstallSource.OTHER, warnings = setOf(AppWarning.NOT_FROM_PLAY)),
        app("unknown", source = InstallSource.UNKNOWN, warnings = setOf(AppWarning.UNKNOWN_SOURCE)),
        self,
    )

    // ---- 項目と件数 ----

    @Test
    fun categoryOf_followsKindThenSource_andSelfFirst() {
        assertEquals(SetupCategory.SYSTEM, SetupSelection.categoryOf(apps[0]))
        assertEquals(SetupCategory.SYSTEM, SetupSelection.categoryOf(apps[1])) // システムは入手元に関係なくシステム
        assertEquals(SetupCategory.PREINSTALLED, SetupSelection.categoryOf(apps[2]))
        assertEquals(SetupCategory.GOOGLE_PLAY, SetupSelection.categoryOf(apps[3]))
        assertEquals(SetupCategory.OTHER_SOURCE, SetupSelection.categoryOf(apps[5]))
        assertEquals(SetupCategory.UNKNOWN_SOURCE, SetupSelection.categoryOf(apps[6]))
        assertEquals(SetupCategory.SELF, SetupSelection.categoryOf(self))
    }

    @Test
    fun counts_includeEveryApp_andSelfOnlyInItsOwnRow() {
        val counts = SetupSelection.countByCategory(apps)
        assertEquals(2, counts[SetupCategory.SYSTEM])
        assertEquals(1, counts[SetupCategory.PREINSTALLED])
        assertEquals(2, counts[SetupCategory.GOOGLE_PLAY]) // 警告のあるアプリも件数には入る
        assertEquals(1, counts[SetupCategory.OTHER_SOURCE]) // 本アプリ自身は「Google Play 以外」に入れない
        assertEquals(1, counts[SetupCategory.UNKNOWN_SOURCE])
        assertEquals(1, counts[SetupCategory.SELF])
        assertEquals(apps.size, counts.values.sum())
    }

    @Test
    fun counts_haveZeroForEmptyRows() {
        val counts = SetupSelection.countByCategory(listOf(app("play")))
        assertEquals(SetupCategory.entries.toSet(), counts.keys)
        assertEquals(0, counts[SetupCategory.SYSTEM])
    }

    // ---- まとめて「問題なし」にするアプリ ----

    @Test
    fun defaultChecked_matchesSpecTable() {
        assertEquals(
            setOf(SetupCategory.SYSTEM, SetupCategory.PREINSTALLED, SetupCategory.GOOGLE_PLAY, SetupCategory.SELF),
            SetupSelection.DEFAULT_CHECKED,
        )
    }

    @Test
    fun targets_withDefaults_excludeWarningApps_butIncludeSelf() {
        val targets = SetupSelection.targets(apps, SetupSelection.DEFAULT_CHECKED)
        assertEquals(listOf("sys1", "sys2", "pre", "play", "io.github.eightbrows.appwatch"), targets)
    }

    @Test
    fun targets_neverIncludeWarningApps_evenWhenTheirRowIsChecked() {
        val targets = SetupSelection.targets(apps, SetupCategory.entries.toSet())
        assertTrue("other" !in targets)
        assertTrue("unknown" !in targets)
        assertTrue("play_debug" !in targets)
    }

    @Test
    fun targets_onlyChangeUnconfirmedApps() {
        val mixed = listOf(
            app("caution", status = ReviewStatus.CAUTION),
            app("ok", status = ReviewStatus.OK),
            app("unconfirmed"),
            self.copy(reviewStatus = ReviewStatus.CAUTION), // 本アプリ自身も「要注意」なら変えない
        )
        assertEquals(listOf("unconfirmed"), SetupSelection.targets(mixed, SetupSelection.DEFAULT_CHECKED))
    }

    @Test
    fun targets_respectUncheckedRows() {
        val targets = SetupSelection.targets(apps, setOf(SetupCategory.PREINSTALLED))
        assertEquals(listOf("pre"), targets)
        assertTrue(SetupSelection.targets(apps, emptySet()).isEmpty())
        // 「このアプリ」のチェックを外すと、本アプリ自身は変えない
        val withoutSelf = SetupSelection.targets(apps, SetupSelection.DEFAULT_CHECKED - SetupCategory.SELF)
        assertTrue(self.packageName !in withoutSelf)
    }
}
