package io.github.eightbrows.appwatch.data.transfer

import io.github.eightbrows.appwatch.data.db.AppCurrentEntity
import io.github.eightbrows.appwatch.data.db.AppEventEntity
import io.github.eightbrows.appwatch.data.db.AppPolicyEntity
import io.github.eightbrows.appwatch.data.db.BaselineAppEntity
import io.github.eightbrows.appwatch.data.db.ScanDailyEntity
import io.github.eightbrows.appwatch.data.record.ChangeType
import io.github.eightbrows.appwatch.data.record.StatusChangeReason
import io.github.eightbrows.appwatch.data.settings.CheckInterval
import io.github.eightbrows.appwatch.data.settings.ThemeMode
import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 書き出しファイルの読み書き（仕様書 7章）。JSON を gzip で圧縮する。Android を使わないので単体テストできる。
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
        val writer = BufferedWriter(OutputStreamWriter(GZIPOutputStream(out), Charsets.UTF_8))
        writer.use {
            val json = JsonOut(it)
            json.beginObject()
            // 先頭：形式の名前と版、書き出し日時、端末の情報（読むときに、ここだけで判断できるように）
            json.name("format").value(AppWatchFile.FORMAT_NAME)
            json.name("version").value(file.formatVersion)
            json.name("exported_at").value(file.exportedAt)
            json.name("device").beginObject()
                .name("id").value(file.device.id)
                .name("model").value(file.device.model)
                .name("os_version").value(file.device.osVersion)
                .name("sdk_int").value(file.device.sdkInt)
                .name("app_version").value(file.device.appVersion)
                .endObject()
            json.name("settings").beginObject()
                .name("check_interval_minutes").value(file.settings.checkIntervalMinutes)
                .name("theme_mode").value(file.settings.themeMode.name)
                .endObject()
            val content = file.content
            json.name("baseline_app").beginArray()
            content.baseline.forEach { writeBaseline(json, it) }
            json.endArray()
            json.name("app_current").beginArray()
            content.current.forEach { writeCurrent(json, it) }
            json.endArray()
            json.name("app_event").beginArray()
            content.events.forEach { writeEvent(json, it) }
            json.endArray()
            json.name("app_policy").beginArray()
            content.policies.forEach { writePolicy(json, it) }
            json.endArray()
            json.name("scan_daily").beginArray()
            content.scanDays.forEach { writeScanDay(json, it) }
            json.endArray()
            json.endObject()
        }
    }

    private fun JsonOut.opt(name: String, value: String?) = apply { if (value != null) name(name).value(value) }
    private fun JsonOut.opt(name: String, value: Long?) = apply { if (value != null) name(name).value(value) }
    private fun JsonOut.opt(name: String, value: Boolean?) = apply { if (value != null) name(name).value(value) }

    private fun JsonOut.strings(name: String, values: Collection<String>) = apply {
        name(name).beginArray()
        values.forEach { value(it) }
        endArray()
    }

    private fun writeBaseline(json: JsonOut, e: BaselineAppEntity) {
        json.beginObject()
            .name("package_name").value(e.packageName)
            .name("captured_at").value(e.capturedAt)
            .name("name").value(e.name)
            .opt("version_name", e.versionName)
            .name("version_code").value(e.versionCode)
            .name("kind").value(e.kind.name)
            .name("source").value(e.source.name)
            .opt("installer_package", e.installerPackage)
            .opt("initiating_package", e.initiatingPackage)
            .name("is_enabled").value(e.isEnabled)
            .strings("warnings", e.warnings.map { it.name }.sorted())
            .strings("signer_sha256", e.signerSha256)
            .name("first_install_time").value(e.firstInstallTime)
            .name("last_update_time").value(e.lastUpdateTime)
            .endObject()
    }

    private fun writeCurrent(json: JsonOut, e: AppCurrentEntity) {
        json.beginObject()
            .name("package_name").value(e.packageName)
            .name("name").value(e.name)
            .opt("version_name", e.versionName)
            .name("version_code").value(e.versionCode)
            .name("kind").value(e.kind.name)
            .name("source").value(e.source.name)
            .opt("installer_package", e.installerPackage)
            .opt("initiating_package", e.initiatingPackage)
            .name("is_enabled").value(e.isEnabled)
            .strings("warnings", e.warnings.map { it.name }.sorted())
            .strings("signer_sha256", e.signerSha256)
            .name("first_install_time").value(e.firstInstallTime)
            .name("last_update_time").value(e.lastUpdateTime)
            .name("is_present").value(e.isPresent)
            .name("first_seen_at").value(e.firstSeenAt)
            .opt("added_at", e.addedAt)
            .opt("removed_at", e.removedAt)
            .opt("reinstalled_at", e.reinstalledAt)
            .opt("signature_changed_at", e.signatureChangedAt)
            .name("updated_at").value(e.updatedAt)
            .endObject()
    }

    private fun writeEvent(json: JsonOut, e: AppEventEntity) {
        json.beginObject()
            .name("occurred_at").value(e.occurredAt)
            .name("type").value(e.type.name)
            .name("package_name").value(e.packageName)
            .name("name").value(e.name)
            .opt("version_name", e.versionName)
            .opt("version_code", e.versionCode)
            .opt("old_version_name", e.oldVersionName)
            .opt("old_version_code", e.oldVersionCode)
            .opt("source", e.source?.name)
            .name("is_reinstall").value(e.isReinstall)
            .opt("old_status", e.oldStatus?.name)
            .opt("new_status", e.newStatus?.name)
            .opt("reason", e.reason?.name)
            .opt("old_admin_memo", e.oldAdminMemo)
            .opt("new_admin_memo", e.newAdminMemo)
            .opt("old_reinstalled_at", e.oldReinstalledAt)
            .opt("old_signature_changed_at", e.oldSignatureChangedAt)
            .opt("old_pre_registered", e.oldPreRegistered)
            .opt("old_provisional_name", e.oldProvisionalName)
            .opt("new_provisional_name", e.newProvisionalName)
            .opt("operation_id", e.operationId)
            .endObject()
    }

    private fun writePolicy(json: JsonOut, e: AppPolicyEntity) {
        json.beginObject()
            .name("package_name").value(e.packageName)
            .name("review_status").value(e.reviewStatus.name)
            .opt("admin_memo", e.adminMemo)
            .name("is_pre_registered").value(e.isPreRegistered)
            .opt("provisional_name", e.provisionalName)
            .opt("status_reason", e.statusReason?.name)
            .name("updated_at").value(e.updatedAt)
            .endObject()
    }

    private fun writeScanDay(json: JsonOut, e: ScanDailyEntity) {
        json.beginObject()
            .name("date").value(e.date)
            .name("count").value(e.count)
            .name("last_at").value(e.lastAt)
            .endObject()
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
            val reader = BufferedReader(InputStreamReader(GZIPInputStream(buffered), Charsets.UTF_8))
            readJson(JsonIn(reader))
        } catch (e: IOException) {
            ReadResult.NotAppWatch
        } catch (e: InvalidFileException) {
            ReadResult.NotAppWatch
        } catch (e: IllegalArgumentException) {
            ReadResult.NotAppWatch
        }
    }

    private fun readJson(json: JsonIn): ReadResult {
        if (json.peek() != JsonIn.Token.BEGIN_OBJECT) return ReadResult.NotAppWatch
        json.beginObject()
        var format: String? = null
        var version: Int? = null
        var exportedAt: Long? = null
        var device: DeviceInfo? = null
        var settings: TransferSettings? = null
        var baseline: List<BaselineAppEntity>? = null
        var current: List<AppCurrentEntity>? = null
        var events: List<AppEventEntity>? = null
        var policies: List<AppPolicyEntity>? = null
        var scanDays: List<ScanDailyEntity>? = null
        while (json.hasNext()) {
            when (json.nextName()) {
                "format" -> {
                    format = json.nextStringOrNull()
                    if (format != AppWatchFile.FORMAT_NAME) return ReadResult.NotAppWatch
                }
                "version" -> {
                    val v = json.nextInt()
                    if (format != AppWatchFile.FORMAT_NAME || v < 1) return ReadResult.NotAppWatch
                    // 新しすぎる版は、記録の形が分からないので、ここで読むのをやめる
                    if (v > AppWatchFile.CURRENT_VERSION) return ReadResult.TooNew(v)
                    version = v
                }
                "exported_at" -> exportedAt = json.nextLong()
                "device" -> device = readDevice(json)
                "settings" -> settings = readSettings(json)
                "baseline_app" -> baseline = readArray(json) { baselineOf(it) }
                "app_current" -> current = readArray(json) { currentOf(it) }
                "app_event" -> events = readArray(json) { eventOf(it) }
                "app_policy" -> policies = readArray(json) { policyOf(it) }
                "scan_daily" -> scanDays = readArray(json) { scanDayOf(it) }
                else -> json.skipValue() // 知らない項目は読み飛ばす
            }
        }
        json.endObject()
        if (json.peek() != JsonIn.Token.END_DOCUMENT) return ReadResult.NotAppWatch
        if (format != AppWatchFile.FORMAT_NAME || version == null) return ReadResult.NotAppWatch
        val file = AppWatchFile(
            formatVersion = version,
            exportedAt = exportedAt ?: throw InvalidFileException("exported_at がありません"),
            device = device ?: throw InvalidFileException("device がありません"),
            settings = settings ?: throw InvalidFileException("settings がありません"),
            content = DatasetContent(
                baseline = baseline ?: throw InvalidFileException("baseline_app がありません"),
                current = current ?: throw InvalidFileException("app_current がありません"),
                events = events ?: throw InvalidFileException("app_event がありません"),
                policies = policies ?: throw InvalidFileException("app_policy がありません"),
                scanDays = scanDays ?: throw InvalidFileException("scan_daily がありません"),
            ),
        )
        validate(file)
        return ReadResult.Success(FileMigrations.toCurrent(file))
    }

    /** データベースに入れられる形か（同じ行が2つない、日付の形、など）を確かめる。 */
    private fun validate(file: AppWatchFile) {
        if (file.device.id.isBlank()) throw InvalidFileException("端末の識別番号が空です")
        val c = file.content
        fun requireUnique(keys: List<String>, what: String) {
            if (keys.size != keys.toSet().size) throw InvalidFileException("$what に同じ行があります")
        }
        requireUnique(c.baseline.map { it.packageName }, "baseline_app")
        requireUnique(c.current.map { it.packageName }, "app_current")
        requireUnique(c.policies.map { it.packageName }, "app_policy")
        requireUnique(c.scanDays.map { it.date }, "scan_daily")
        (c.baseline.map { it.packageName } + c.current.map { it.packageName } + c.policies.map { it.packageName })
            .forEach { if (it.isBlank()) throw InvalidFileException("パッケージ名が空です") }
        c.scanDays.forEach {
            try {
                LocalDate.parse(it.date)
            } catch (e: DateTimeParseException) {
                throw InvalidFileException("日付の形が違います: ${it.date}")
            }
            if (it.count < 0) throw InvalidFileException("回数が負です")
        }
    }

    private fun readDevice(json: JsonIn): DeviceInfo {
        val f = readFields(json)
        return DeviceInfo(
            id = f.str("id"),
            model = f.str("model"),
            osVersion = f.str("os_version"),
            sdkInt = f.int("sdk_int"),
            appVersion = f.str("app_version"),
        )
    }

    private fun readSettings(json: JsonIn): TransferSettings {
        val f = readFields(json)
        val interval = f.int("check_interval_minutes")
        if (CheckInterval.entries.none { it.minutes == interval }) throw InvalidFileException("チェック間隔の値が違います: $interval")
        return TransferSettings(checkIntervalMinutes = interval, themeMode = f.enum<ThemeMode>("theme_mode"))
    }

    private fun <T> readArray(json: JsonIn, build: (Fields) -> T): List<T> {
        val list = ArrayList<T>()
        json.beginArray()
        while (json.hasNext()) list += build(readFields(json))
        json.endArray()
        return list
    }

    private fun baselineOf(f: Fields) = BaselineAppEntity(
        datasetId = "",
        packageName = f.str("package_name"),
        capturedAt = f.long("captured_at"),
        name = f.str("name"),
        versionName = f.strOrNull("version_name"),
        versionCode = f.long("version_code"),
        kind = f.enum<AppKind>("kind"),
        source = f.enum<InstallSource>("source"),
        installerPackage = f.strOrNull("installer_package"),
        initiatingPackage = f.strOrNull("initiating_package"),
        isEnabled = f.bool("is_enabled"),
        warnings = f.enums<AppWarning>("warnings"),
        signerSha256 = f.strings("signer_sha256"),
        firstInstallTime = f.long("first_install_time"),
        lastUpdateTime = f.long("last_update_time"),
    )

    private fun currentOf(f: Fields) = AppCurrentEntity(
        datasetId = "",
        packageName = f.str("package_name"),
        name = f.str("name"),
        versionName = f.strOrNull("version_name"),
        versionCode = f.long("version_code"),
        kind = f.enum<AppKind>("kind"),
        source = f.enum<InstallSource>("source"),
        installerPackage = f.strOrNull("installer_package"),
        initiatingPackage = f.strOrNull("initiating_package"),
        isEnabled = f.bool("is_enabled"),
        warnings = f.enums<AppWarning>("warnings"),
        signerSha256 = f.strings("signer_sha256"),
        firstInstallTime = f.long("first_install_time"),
        lastUpdateTime = f.long("last_update_time"),
        isPresent = f.bool("is_present"),
        firstSeenAt = f.long("first_seen_at"),
        addedAt = f.longOrNull("added_at"),
        removedAt = f.longOrNull("removed_at"),
        reinstalledAt = f.longOrNull("reinstalled_at"),
        signatureChangedAt = f.longOrNull("signature_changed_at"),
        updatedAt = f.long("updated_at"),
    )

    private fun eventOf(f: Fields) = AppEventEntity(
        id = 0,
        datasetId = "",
        occurredAt = f.long("occurred_at"),
        type = f.enum<ChangeType>("type"),
        packageName = f.str("package_name"),
        name = f.str("name"),
        versionName = f.strOrNull("version_name"),
        versionCode = f.longOrNull("version_code"),
        oldVersionName = f.strOrNull("old_version_name"),
        oldVersionCode = f.longOrNull("old_version_code"),
        source = f.enumOrNull<InstallSource>("source"),
        isReinstall = f.bool("is_reinstall"),
        oldStatus = f.enumOrNull<ReviewStatus>("old_status"),
        newStatus = f.enumOrNull<ReviewStatus>("new_status"),
        reason = f.enumOrNull<StatusChangeReason>("reason"),
        oldAdminMemo = f.strOrNull("old_admin_memo"),
        newAdminMemo = f.strOrNull("new_admin_memo"),
        oldReinstalledAt = f.longOrNull("old_reinstalled_at"),
        oldSignatureChangedAt = f.longOrNull("old_signature_changed_at"),
        oldPreRegistered = f.boolOrNull("old_pre_registered"),
        oldProvisionalName = f.strOrNull("old_provisional_name"),
        newProvisionalName = f.strOrNull("new_provisional_name"),
        operationId = f.strOrNull("operation_id"),
    )

    private fun policyOf(f: Fields) = AppPolicyEntity(
        datasetId = "",
        packageName = f.str("package_name"),
        reviewStatus = f.enum<ReviewStatus>("review_status"),
        adminMemo = f.strOrNull("admin_memo"),
        isPreRegistered = f.bool("is_pre_registered"),
        provisionalName = f.strOrNull("provisional_name"),
        statusReason = f.enumOrNull<StatusChangeReason>("status_reason"),
        updatedAt = f.long("updated_at"),
    )

    private fun scanDayOf(f: Fields) = ScanDailyEntity(
        datasetId = "",
        date = f.str("date"),
        count = f.int("count"),
        lastAt = f.long("last_at"),
    )

    /** 1つの行（入れ子のない JSON の物）を読む。値は文字・数・true/false・null・文字の配列。 */
    private fun readFields(json: JsonIn): Fields {
        val map = HashMap<String, Any?>()
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            map[name] = when (json.peek()) {
                JsonIn.Token.STRING -> json.nextString()
                JsonIn.Token.NUMBER -> json.nextLong()
                JsonIn.Token.BOOLEAN -> json.nextBoolean()
                JsonIn.Token.NULL -> json.nextNull().let { null }
                JsonIn.Token.BEGIN_ARRAY -> {
                    val list = ArrayList<String>()
                    json.beginArray()
                    while (json.hasNext()) list += json.nextString()
                    json.endArray()
                    list
                }
                else -> {
                    json.skipValue() // 知らない形の項目は読み飛ばす
                    null
                }
            }
        }
        json.endObject()
        return Fields(map)
    }

    /** 読んだ1行の値。必要な項目がない・形が違うときは [InvalidFileException]。 */
    private class Fields(private val map: Map<String, Any?>) {
        fun str(name: String): String = strOrNull(name) ?: throw InvalidFileException("$name がありません")
        fun strOrNull(name: String): String? = when (val v = map[name]) {
            null -> null
            is String -> v
            else -> throw InvalidFileException("$name が文字ではありません")
        }
        fun long(name: String): Long = longOrNull(name) ?: throw InvalidFileException("$name がありません")
        fun longOrNull(name: String): Long? = when (val v = map[name]) {
            null -> null
            is Long -> v
            else -> throw InvalidFileException("$name が数ではありません")
        }
        fun int(name: String): Int {
            val v = long(name)
            if (v !in Int.MIN_VALUE..Int.MAX_VALUE) throw InvalidFileException("$name が大きすぎます")
            return v.toInt()
        }
        fun bool(name: String): Boolean = boolOrNull(name) ?: throw InvalidFileException("$name がありません")
        fun boolOrNull(name: String): Boolean? = when (val v = map[name]) {
            null -> null
            is Boolean -> v
            else -> throw InvalidFileException("$name が true／false ではありません")
        }
        fun strings(name: String): List<String> = when (val v = map[name]) {
            null -> emptyList()
            is List<*> -> v.map { it as String }
            else -> throw InvalidFileException("$name が配列ではありません")
        }
        inline fun <reified T : Enum<T>> enum(name: String): T = enumOrNull<T>(name) ?: throw InvalidFileException("$name がありません")
        inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = strOrNull(name)?.let { value ->
            enumValues<T>().firstOrNull { it.name == value } ?: throw InvalidFileException("$name の値を知りません: $value")
        }
        inline fun <reified T : Enum<T>> enums(name: String): Set<T> = strings(name).mapTo(LinkedHashSet()) { value ->
            enumValues<T>().firstOrNull { it.name == value } ?: throw InvalidFileException("$name の値を知りません: $value")
        }
    }
}
