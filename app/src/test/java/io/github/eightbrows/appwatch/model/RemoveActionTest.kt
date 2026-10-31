package io.github.eightbrows.appwatch.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class RemoveActionTest {

    private fun app(kind: AppKind) = AppSummary(
        packageName = "p",
        name = "n",
        versionName = "1",
        kind = kind,
        source = InstallSource.UNKNOWN,
        warnings = emptySet(),
        reviewStatus = ReviewStatus.UNCONFIRMED,
        adminMemo = null,
        installedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        isNew = false,
    )

    @Test
    fun userInstalled_canBeUninstalled() {
        assertEquals(RemoveAction.UNINSTALL, app(AppKind.USER_INSTALLED).removeAction)
    }

    @Test
    fun preinstalled_opensAppInfo() {
        assertEquals(RemoveAction.APP_INFO, app(AppKind.PREINSTALLED).removeAction)
    }

    @Test
    fun system_hasNoButton() {
        assertEquals(RemoveAction.NONE, app(AppKind.SYSTEM).removeAction)
    }
}
