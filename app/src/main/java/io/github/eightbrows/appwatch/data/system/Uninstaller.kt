package io.github.eightbrows.appwatch.data.system

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.app.PendingIntentCompat
import androidx.core.content.IntentCompat
import io.github.eightbrows.appwatch.AppWatchApplication
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** 削除の結果。 */
enum class UninstallResult {
    /** 削除された */
    DELETED,

    /** 削除されなかった（Android の確認で「キャンセル」を押した、削除できなかったなど） */
    NOT_DELETED,
}

/** Android から届いた削除の状態（PackageInstaller の状態）を、本アプリでの扱いに分ける。Android の画面を使わない。 */
object UninstallStatus {

    sealed interface Step {
        /** Android の削除の確認を出す必要がある（利用者が「OK」を押すと削除される） */
        data object NeedsConfirmation : Step

        /** 終わった */
        data class Finished(val result: UninstallResult) : Step
    }

    fun stepOf(status: Int): Step = when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> Step.NeedsConfirmation
        PackageInstaller.STATUS_SUCCESS -> Step.Finished(UninstallResult.DELETED)
        else -> Step.Finished(UninstallResult.NOT_DELETED)
    }
}

/** 削除の流れの中で、画面（Activity）に頼むこと。 */
sealed interface UninstallEvent {
    /** Android の削除の確認の画面を開く */
    data class Confirm(val intent: Intent) : UninstallEvent

    /** 結果を表示する */
    data class Finished(val appName: String, val result: UninstallResult) : UninstallEvent
}

/**
 * アプリの削除（仕様書 6章）。本アプリの確認のダイアログの後に呼ぶ。
 * PackageInstaller.uninstall で削除を頼み、結果を [UninstallResultReceiver] で受け取る。
 * 本アプリは REQUEST_DELETE_PACKAGES しか持たないので、Android が毎回確認を出す（省けない）。
 * Android 8〜14 で同じ動きになる（確認が要ると [UninstallEvent.Confirm] が届く）。
 */
object Uninstaller {

    private val events = Channel<UninstallEvent>(Channel.BUFFERED)

    /** 画面が受け取る出来事。画面が作り直されている間に届いたものは、次に受け取るまで残る。 */
    val eventFlow: Flow<UninstallEvent> = events.receiveAsFlow()

    internal const val EXTRA_APP_NAME = "io.github.eightbrows.appwatch.extra.APP_NAME"

    /**
     * 削除を頼む。
     * @return 頼めたら true。頼めなかったら false（呼び出し元は Android の削除画面を直接開く）
     */
    fun start(context: Context, packageName: String, appName: String): Boolean {
        val result = Intent(context, UninstallResultReceiver::class.java).putExtra(EXTRA_APP_NAME, appName)
        // Android が状態を書き足すので、書き換えられる PendingIntent にする（届け先は本アプリの受け取りに固定）
        val pending = PendingIntentCompat.getBroadcast(
            context,
            packageName.hashCode(),
            result,
            PendingIntent.FLAG_UPDATE_CURRENT,
            true,
        ) ?: return false
        return try {
            context.packageManager.packageInstaller.uninstall(packageName, pending.intentSender)
            true
        } catch (e: Exception) {
            false
        }
    }

    internal fun post(event: UninstallEvent) {
        events.trySend(event)
    }
}

/** 削除の状態を受け取る（本アプリの中だけ。exported="false"）。 */
class UninstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val appName = intent.getStringExtra(Uninstaller.EXTRA_APP_NAME).orEmpty()
        when (val step = UninstallStatus.stepOf(status)) {
            UninstallStatus.Step.NeedsConfirmation -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                Uninstaller.post(
                    if (confirm != null) {
                        UninstallEvent.Confirm(confirm)
                    } else {
                        UninstallEvent.Finished(appName, UninstallResult.NOT_DELETED)
                    },
                )
            }

            is UninstallStatus.Step.Finished -> {
                Uninstaller.post(UninstallEvent.Finished(appName, step.result))
                // 削除の後は今までどおりチェックする（直前1分以内でも省略しない）
                val app = context.applicationContext as AppWatchApplication
                app.checkRunner.forceNextRequest()
                app.checkRunner.request()
            }
        }
    }
}
