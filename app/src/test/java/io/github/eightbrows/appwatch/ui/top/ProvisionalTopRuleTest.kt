package io.github.eightbrows.appwatch.ui.top

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ProvisionalTopRuleTest {

    private fun app(pkg: String, kind: AppKind, warnings: Set<AppWarning> = emptySet(), day: Int = 1) = AppSummary(
        packageName = pkg,
        name = pkg,
        versionName = "1",
        kind = kind,
        source = InstallSource.GOOGLE_PLAY,
        warnings = warnings,
        reviewStatus = ReviewStatus.UNCONFIRMED,
        adminMemo = null,
        installedAt = LocalDateTime.of(2026, 10, day, 0, 0),
        isNew = false,
    )

    private val apps = listOf(
        app("user_warn", AppKind.USER_INSTALLED, setOf(AppWarning.NOT_FROM_PLAY)),
        app("user_old", AppKind.USER_INSTALLED, day = 1),
        app("user_new", AppKind.USER_INSTALLED, day = 20),
        app("pre_warn", AppKind.PREINSTALLED, setOf(AppWarning.DEBUG_SIGNATURE)),
        app("pre", AppKind.PREINSTALLED),
        app("sys", AppKind.SYSTEM),
    )

    @Test
    fun cards_areUserInstalledAppsWithWarnings() {
        assertEquals(listOf("user_warn"), ProvisionalTopRule.lists(apps).cards.map { it.packageName })
    }

    @Test
    fun unconfirmed_areOtherUserInstalledApps_newestFirst() {
        assertEquals(listOf("user_new", "user_old"), ProvisionalTopRule.lists(apps).unconfirmed.map { it.packageName })
    }

    @Test
    fun preinstalledAndSystem_areOnlyInAll() {
        val lists = ProvisionalTopRule.lists(apps)
        val shown = (lists.cards + lists.unconfirmed).map { it.kind }.toSet()
        assertEquals(setOf(AppKind.USER_INSTALLED), shown)
    }

    @Test
    fun all_containsEveryApp_userFirstThenPreinstalledThenSystem() {
        assertEquals(
            listOf("user_new", "user_old", "user_warn", "pre", "pre_warn", "sys"),
            ProvisionalTopRule.lists(apps).all.map { it.packageName },
        )
    }
}
