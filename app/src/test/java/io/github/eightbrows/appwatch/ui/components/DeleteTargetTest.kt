package io.github.eightbrows.appwatch.ui.components

import io.github.eightbrows.appwatch.model.AppWarning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteTargetTest {

    private fun target(warnings: Set<AppWarning>, isSelf: Boolean = false) =
        DeleteTarget(packageName = "jp.example.app", name = "例のアプリ", versionName = "1.0", warnings = warnings, isSelf = isSelf)

    @Test
    fun 警告のないアプリは警告を添えない() {
        assertTrue(target(emptySet()).shownWarnings.isEmpty())
    }

    @Test
    fun 警告は仕様書の順に最大2行() {
        val all = setOf(AppWarning.REINSTALLED, AppWarning.DEBUG_SIGNATURE, AppWarning.NOT_FROM_PLAY)
        assertEquals(listOf(AppWarning.NOT_FROM_PLAY, AppWarning.DEBUG_SIGNATURE), target(all).shownWarnings)
        assertEquals(listOf(AppWarning.UNKNOWN_SOURCE), target(setOf(AppWarning.UNKNOWN_SOURCE)).shownWarnings)
    }

    @Test
    fun 本アプリ自身は警告の代わりに見守りが止まる説明を出す() {
        // 開発版は「開発用の署名」などの警告が付くが、本アプリの削除のダイアログには出さない
        assertTrue(target(setOf(AppWarning.DEBUG_SIGNATURE, AppWarning.UNKNOWN_SOURCE), isSelf = true).shownWarnings.isEmpty())
    }
}
