package io.github.eightbrows.appwatch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/** 画面の種類。画面を増やすときはここに足す。 */
enum class Screen { TOP, SETTINGS_MENU, SETUP, IMPORTED_DATA }

/**
 * 画面の切り替え（Navigation Compose は使わない簡単な仕組み）。
 * 開いた画面を積み重ねて持ち、戻るボタンで1つ前に戻る。
 * 画面の回転や言語の切り替えで Activity が作り直されても、開いていた画面を保つ。
 */
@Stable
class Navigator internal constructor(initial: List<Screen>) {

    private val backStack = mutableStateListOf<Screen>().apply { addAll(initial) }

    val current: Screen get() = backStack.last()

    /** 積み重ねのいちばん下（最初の画面）。 */
    val root: Screen get() = backStack.first()

    val canGoBack: Boolean get() = backStack.size > 1

    fun navigate(screen: Screen) {
        if (current != screen) backStack.add(screen)
    }

    /** 積み重ねを捨てて、[screen] を最初の画面にする（初回設定が終わったときなど）。 */
    fun resetTo(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }

    /** 1つ前の画面に戻る。最初の画面なら何もせず false を返す。 */
    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    internal fun snapshot(): List<Screen> = backStack.toList()

    companion object {
        val Saver = listSaver<Navigator, String>(
            save = { navigator -> navigator.snapshot().map { it.name } },
            restore = { names ->
                val screens = names.mapNotNull { name -> Screen.entries.firstOrNull { it.name == name } }
                Navigator(screens.ifEmpty { listOf(Screen.TOP) })
            },
        )
    }
}

@Composable
fun rememberNavigator(start: Screen = Screen.TOP): Navigator =
    rememberSaveable(saver = Navigator.Saver) { Navigator(listOf(start)) }
