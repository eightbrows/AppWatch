package io.github.eightbrows.appwatch.ui.components

import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.ReviewReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteTargetTest {

    private fun target(warnings: Set<AppWarning>, isSelf: Boolean = false) =
        DeleteTarget(packageName = "jp.example.app", name = "例のアプリ", versionName = "1.0", warnings = warnings, isSelf = isSelf)

    @Test
    fun 警告のないアプリは札を添えない() {
        assertTrue(target(emptySet()).shownReasons.isEmpty())
    }

    @Test
    fun 警告はトップと同じ札で優先の順に出す() {
        val all = setOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE, AppWarning.REINSTALLED)
        assertEquals(
            listOf(ReviewReason.REINSTALLED, ReviewReason.DEBUG_SIGNATURE, ReviewReason.NOT_FROM_PLAY),
            target(all).shownReasons,
        )
        assertEquals(listOf(ReviewReason.UNKNOWN_SOURCE), target(setOf(AppWarning.UNKNOWN_SOURCE)).shownReasons)
    }

    @Test
    fun 本アプリ自身は札の代わりに見守りが止まる説明を出す() {
        // 開発版は「開発用の署名」などの警告が付くが、本アプリの削除のダイアログには札を出さない
        assertTrue(target(setOf(AppWarning.DEBUG_SIGNATURE, AppWarning.UNKNOWN_SOURCE), isSelf = true).shownReasons.isEmpty())
    }
}
