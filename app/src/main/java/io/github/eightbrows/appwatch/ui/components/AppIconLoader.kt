package io.github.eightbrows.appwatch.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * アプリのアイコンを裏で読み込み、使い回す。
 * 一覧には数百件並ぶことがあるので、全部を先に読まず、表示される行の分だけ読む。
 */
internal object AppIconLoader {

    /** アプリが使えるメモリの 1/8 まで覚えておく（Android の推奨の目安）。 */
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun cached(key: String): Bitmap? = cache.get(key)

    suspend fun load(context: Context, packageName: String, key: String, sizePx: Int): Bitmap? {
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx)
            }.getOrNull()
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }
}

/**
 * アプリのアイコンを返す。読み込み中や、端末にないアプリ（ダミーデータなど）は null。
 * [versionKey] が変わると読み直す（アップデートでアイコンが変わることがあるため）。
 */
@Composable
internal fun rememberAppIcon(packageName: String, versionKey: String, sizePx: Int): State<Bitmap?> {
    val context = LocalContext.current.applicationContext
    val key = "$packageName@$versionKey@$sizePx"
    return produceState(initialValue = AppIconLoader.cached(key), key) {
        if (value == null) value = AppIconLoader.load(context, packageName, key, sizePx)
    }
}
