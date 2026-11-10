package io.github.eightbrows.appwatch.ui.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.eightbrows.appwatch.AppWatchApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 【開発版だけ】初回設定をやり直す。正式版には入らない。
 *
 * 使い方（adb から。DUMP の権限が要るので、端末上の他のアプリからは呼べない）：
 *   adb shell am broadcast -n io.github.eightbrows.appwatch/.ui.setup.DebugSetupReceiver
 *
 * 初回設定の完了状態だけを消す。記録（データベース）・テーマ・チェック間隔は残る。
 * 消すと、アプリ側で定期チェックの登録が外れ、
 * ウィジェットは「管理者に初期設定を依頼して下さい」になる。次にアプリを開くと初回設定から始まる。
 */
class DebugSetupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AppWatchApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                app.settingsRepository.clearSetupState()
            } finally {
                pending.finish()
            }
        }
    }
}
