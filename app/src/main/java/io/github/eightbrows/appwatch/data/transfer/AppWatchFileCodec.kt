package io.github.eightbrows.appwatch.data.transfer

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 書き出しファイルの読み書き（仕様書 7章）。JSON を gzip で圧縮する。
 * JSON は Android 標準の android.util.JsonReader／JsonWriter で順に流して読み書きする（全体を一度に文字にしない）。
 * 行の変換・先頭の項目の判断・中身の確かめは [FileRows]（単体テストできる部分）。往復は端末上のテストで確かめる。
 *
 * JSON の形（項目の名前は表の列の名前と同じ。値が null の項目は書かない）：
 * ```
 * {"format":"appwatch","version":1,"exported_at":…,
 *  "device":{"id":…,"model":…,"os_version":…,"sdk_int":…,"app_version":…},
 *  "settings":{"check_interval_minutes":30,"theme_mode":"SYSTEM"},
 *  "baseline_app":[…],"app_current":[…],"app_event":[…],"app_policy":[…],"scan_daily":[…]}
 * ```
 */
object AppWatchFileCodec {

    // ---- 書き出し ----

    /** [file] を [out] に書く。[out] は閉じる。 */
    fun write(file: AppWatchFile, out: OutputStream) {
        JsonWriter(BufferedWriter(OutputStreamWriter(GZIPOutputStream(out), Charsets.UTF_8))).use { json ->
            json.beginObject()
            // 先頭：形式の名前と版、書き出し日時、端末の情報（読むときに、ここだけで判断できるように）
            json.name("format").value(AppWatchFile.FORMAT_NAME)
            json.name("version").value(file.formatVersion.toLong())
            json.name("exported_at").value(file.exportedAt)
            json.name("device")
            writeRow(json, FileRows.device(file.device))
            json.name("settings")
            writeRow(json, FileRows.settings(file.settings))
            val content = file.content
            writeArray(json, "baseline_app", content.baseline.map(FileRows::baseline))
            writeArray(json, "app_current", content.current.map(FileRows::current))
            writeArray(json, "app_event", content.events.map(FileRows::event))
            writeArray(json, "app_policy", content.policies.map(FileRows::policy))
            writeArray(json, "scan_daily", content.scanDays.map(FileRows::scanDay))
            json.endObject()
        }
    }

    private fun writeArray(json: JsonWriter, name: String, rows: List<FileRows.Row>) {
        json.name(name).beginArray()
        rows.forEach { writeRow(json, it) }
        json.endArray()
    }

    private fun writeRow(json: JsonWriter, row: FileRows.Row) {
        json.beginObject()
        for ((name, value) in row) {
            json.name(name)
            when (value) {
                is String -> json.value(value)
                is Long -> json.value(value)
                is Boolean -> json.value(value)
                is List<*> -> {
                    json.beginArray()
                    value.forEach { json.value(it as String) }
                    json.endArray()
                }
                else -> error("書けない値: $name")
            }
        }
        json.endObject()
    }

    // ---- 読み込み ----

    /**
     * [input] を読む。[input] は閉じない。
     * gzip でない・JSON でない・形式の名前が違う・必要な項目がない・知らない値がある → [ReadResult.NotAppWatch]。
     * 形式の版がこのアプリより新しい → [ReadResult.TooNew]（記録の部分は読まない）。
     */
    fun read(input: InputStream): ReadResult {
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        if (b1 != 0x1f || b2 != 0x8b) return ReadResult.NotAppWatch // gzip の印がない
        buffered.reset()
        return try {
            val json = JsonReader(BufferedReader(InputStreamReader(GZIPInputStream(buffered), Charsets.UTF_8)))
            readJson(json)
        } catch (e: IOException) {
            ReadResult.NotAppWatch // 壊れた gzip・JSON の形の誤り（MalformedJsonException を含む）
        } catch (e: InvalidFileException) {
            ReadResult.NotAppWatch
        } catch (e: IllegalStateException) {
            ReadResult.NotAppWatch // 思った種類の値でない（物の代わりに配列など）
        } catch (e: IllegalArgumentException) {
            ReadResult.NotAppWatch // 数の形の誤り（NumberFormatException）など
        }
    }

    private fun readJson(json: JsonReader): ReadResult {
        if (json.peek() != JsonToken.BEGIN_OBJECT) return ReadResult.NotAppWatch
        json.beginObject()
        // 先頭は形式の名前、次が版（ここで AppWatch のファイルか、新しすぎないかを判断する）
        if (!json.hasNext() || json.nextName() != "format" || json.peek() != JsonToken.STRING) return ReadResult.NotAppWatch
        val format = json.nextString()
        if (!json.hasNext() || json.nextName() != "version" || json.peek() != JsonToken.NUMBER) return ReadResult.NotAppWatch
        val version = json.nextInt()
        when (val header = FileRows.checkHeader(format, version)) {
            FileRows.Header.NotAppWatch -> return ReadResult.NotAppWatch
            is FileRows.Header.TooNew -> return ReadResult.TooNew(header.version)
            FileRows.Header.Ok -> Unit
        }
        var exportedAt: Long? = null
        var device: DeviceInfo? = null
        var settings: TransferSettings? = null
        var content = DatasetContent.EMPTY
        val seen = HashSet<String>()
        while (json.hasNext()) {
            when (val name = json.nextName()) {
                "exported_at" -> exportedAt = readValue(json) as? Long ?: throw InvalidFileException("exported_at が数ではありません")
                "device" -> device = FileRows.deviceOf(readRow(json))
                "settings" -> settings = FileRows.settingsOf(readRow(json))
                "baseline_app" -> content = content.copy(baseline = readArray(json, FileRows::baselineOf)).also { seen += name }
                "app_current" -> content = content.copy(current = readArray(json, FileRows::currentOf)).also { seen += name }
                "app_event" -> content = content.copy(events = readArray(json, FileRows::eventOf)).also { seen += name }
                "app_policy" -> content = content.copy(policies = readArray(json, FileRows::policyOf)).also { seen += name }
                "scan_daily" -> content = content.copy(scanDays = readArray(json, FileRows::scanDayOf)).also { seen += name }
                else -> json.skipValue() // 知らない項目は読み飛ばす
            }
        }
        json.endObject()
        if (json.peek() != JsonToken.END_DOCUMENT) return ReadResult.NotAppWatch
        val missing = TABLES - seen
        if (missing.isNotEmpty()) throw InvalidFileException("${missing.first()} がありません")
        val file = AppWatchFile(
            formatVersion = version,
            exportedAt = exportedAt ?: throw InvalidFileException("exported_at がありません"),
            device = device ?: throw InvalidFileException("device がありません"),
            settings = settings ?: throw InvalidFileException("settings がありません"),
            content = content,
        )
        FileRows.validate(file)
        return ReadResult.Success(FileMigrations.toCurrent(file))
    }

    private fun <T> readArray(json: JsonReader, build: (Map<String, Any?>) -> T): List<T> {
        val list = ArrayList<T>()
        json.beginArray()
        while (json.hasNext()) list += build(readRow(json))
        json.endArray()
        return list
    }

    /** 1つの行（入れ子のない JSON の物）を読む。入れ子の物など知らない形の項目は読み飛ばす。 */
    private fun readRow(json: JsonReader): Map<String, Any?> {
        val map = HashMap<String, Any?>()
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            map[name] = readValue(json)
        }
        json.endObject()
        return map
    }

    /** 文字・数（整数）・true/false・null・文字の配列。それ以外は読み飛ばして null。 */
    private fun readValue(json: JsonReader): Any? = when (json.peek()) {
        JsonToken.STRING -> json.nextString()
        JsonToken.NUMBER -> json.nextLong()
        JsonToken.BOOLEAN -> json.nextBoolean()
        JsonToken.NULL -> json.nextNull().let { null }
        JsonToken.BEGIN_ARRAY -> {
            val list = ArrayList<Any?>()
            json.beginArray()
            while (json.hasNext()) {
                list += if (json.peek() == JsonToken.STRING) json.nextString() else throw InvalidFileException("配列に文字でない値があります")
            }
            json.endArray()
            list
        }
        else -> {
            json.skipValue()
            null
        }
    }

    private val TABLES = setOf("baseline_app", "app_current", "app_event", "app_policy", "scan_daily")
}
