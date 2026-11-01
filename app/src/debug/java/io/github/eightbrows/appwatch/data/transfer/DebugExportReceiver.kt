package io.github.eightbrows.appwatch.data.transfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.eightbrows.appwatch.AppWatchApplication
import io.github.eightbrows.appwatch.data.db.AppPolicyEntity
import io.github.eightbrows.appwatch.model.ReviewStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * 【開発版だけ】この端末のデータを「別の端末のファイル」として書き出す。正式版には入らない。
 * 1台のエミュレータで、別の端末から受け取ったファイルの取り込み（閲覧のみ・同じ端末の置き換え・前回以降）を確かめるために使う。
 *
 * 使い方（adb から。DUMP の権限が要るので、端末上の他のアプリからは呼べない）：
 *   adb shell am broadcast -n io.github.eightbrows.appwatch/.data.transfer.DebugExportReceiver \
 *     --es device_id device-a --es model "Pixel 8" --es os "Android 15" --ei days_ago 7 \
 *     --es caution com.example.app --es file debug_a_old.appwatch
 * 書き出し先：/sdcard/Android/data/io.github.eightbrows.appwatch/files/（adb で /sdcard/Download などへ写して使う）
 *
 * - device_id・model・os：ファイルの端末の情報（省略するとこの端末のまま）
 * - days_ago：書き出し日時を n 日前にする（記録の中身はそのまま）
 * - caution：そのアプリを「要注意」にして書き出す（端末にあるアプリで試すと、要注意の件数に出る）
 */
class DebugExportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AppWatchApplication
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis() - intent.getIntExtra("days_ago", 0) * 86_400_000L
                val settings = TransferSettings(
                    checkIntervalMinutes = app.settingsRepository.checkIntervalMinutes.first(),
                    themeMode = app.settingsRepository.themeMode.first(),
                )
                val base = app.transferRepository.buildExport(settings, now)
                val device = base.device.copy(
                    id = intent.getStringExtra("device_id") ?: base.device.id,
                    model = intent.getStringExtra("model") ?: base.device.model,
                    osVersion = intent.getStringExtra("os") ?: base.device.osVersion,
                )
                val caution = intent.getStringExtra("caution")
                val content = if (caution == null) {
                    base.content
                } else {
                    val others = base.content.policies.filter { it.packageName != caution }
                    val policy = AppPolicyEntity(
                        datasetId = "",
                        packageName = caution,
                        reviewStatus = ReviewStatus.CAUTION,
                        adminMemo = intent.getStringExtra("memo"),
                        isPreRegistered = false,
                        provisionalName = null,
                        statusReason = null,
                        updatedAt = now,
                    )
                    base.content.copy(policies = others + policy)
                }
                val name = intent.getStringExtra("file") ?: AppWatchFile.fileName(java.time.LocalDateTime.now())
                val out = File(context.getExternalFilesDir(null), name)
                out.outputStream().use { AppWatchFileCodec.write(base.copy(device = device, content = content), it) }
                Log.i(TAG, "書き出しました: ${out.absolutePath} (${out.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "書き出せませんでした", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AppWatch"
    }
}
