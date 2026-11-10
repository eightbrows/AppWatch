package io.github.eightbrows.appwatch.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.eightbrows.appwatch.R

/** Android の画面（アンインストール、アプリ情報）を開く。 */
object AppActions {

    /**
     * Android のアンインストール画面を開く（REQUEST_DELETE_PACKAGES が必要）。
     * 削除するかどうかは、Android の確認画面で利用者が決める。
     */
    fun uninstall(context: Context, packageName: String) {
        start(context, Intent(Intent.ACTION_DELETE, packageUri(packageName)))
    }

    /** Android の削除の確認の画面を開く（PackageInstaller から届いた画面）。 */
    fun startConfirm(context: Context, intent: Intent) {
        start(context, intent)
    }

    /** Android のアプリ情報の画面を開く（無効化・強制停止などはここから行う）。 */
    fun openAppInfo(context: Context, packageName: String) {
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName)))
    }

    private fun packageUri(packageName: String): Uri = Uri.fromParts("package", packageName, null)

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_cannot_open_screen, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, R.string.error_cannot_open_screen, Toast.LENGTH_LONG).show()
        }
    }
}
