package io.github.eightbrows.appwatch.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 【開発版だけ】ウィジェットの6つの状態を、データを変えずに切り替えて確かめる。正式版には入らない。
 *
 * 使い方（adb から。DUMP の権限が要るので、端末上の他のアプリからは呼べない）：
 *   adb shell am broadcast -n io.github.eightbrows.appwatch/.widget.DebugWidgetReceiver --es status CHECK_STOPPED
 *   status は WidgetStatus の名前（CAUTION_APPS など）。NONE で上書きをやめて、本当の状態に戻る。
 *   --es lastCheck 2026-10-12T10:30 を付けると「最終チェック」もその時刻にする（見本の画像を撮るとき）。
 * 上書きはアプリのプロセスが終わると消える。
 */
class DebugWidgetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("status")
        val status = WidgetStatus.entries.firstOrNull { it.name == name }
        // 「最終チェック」の時刻（端末の時間帯での 2026-10-12T10:30 の形）。なければ本当の時刻
        val lastCheckAt = intent.getStringExtra("lastCheck")?.let { text ->
            runCatching { LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
        }
        WidgetDebugOverride.set(status?.let { WidgetOverride(it, lastCheckAt) })
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
