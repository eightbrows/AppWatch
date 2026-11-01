package io.github.eightbrows.appwatch.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 【開発版だけ】ウィジェットの6つの状態を、データを変えずに切り替えて確かめる。正式版には入らない。
 *
 * 使い方（adb から。DUMP の権限が要るので、端末上の他のアプリからは呼べない）：
 *   adb shell am broadcast -n io.github.eightbrows.appwatch/.widget.DebugWidgetReceiver --es status CHECK_STOPPED
 *   status は WidgetStatus の名前（CAUTION_APPS など）。NONE で上書きをやめて、本当の状態に戻る。
 * 上書きはアプリのプロセスが終わると消える。
 */
class DebugWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("status")
        WidgetDebugOverride.set(WidgetStatus.entries.firstOrNull { it.name == name })
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                AppWatchWidget.updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
