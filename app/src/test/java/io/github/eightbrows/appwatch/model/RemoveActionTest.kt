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
    fun system_opensAppInfo_likePreinstalled() {
        // システムのアプリも、プリインストールと同じく「アプリ情報」（種類で区別しない）
        assertEquals(RemoveAction.APP_INFO, app(AppKind.SYSTEM).removeAction)
    }

    @Test
    fun topButton_isDeleteOnlyForEnabledUserInstalledApps() {
        // トップのボタン：後から導入で有効なら「削除」、それ以外（プリインストール・システム・無効）は「アプリ情報」。ボタンなしはない
        for (kind in AppKind.entries) {
            for (enabled in listOf(true, false)) {
                val expected = if (kind == AppKind.USER_INSTALLED && enabled) RemoveAction.UNINSTALL else RemoveAction.APP_INFO
                assertEquals("$kind enabled=$enabled", expected, app(kind).copy(isEnabled = enabled).topAction)
            }
        }
    }
}
