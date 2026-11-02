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
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 書き出しファイルの1行（JSON の物）と、表の行の変換、先頭の項目の判断、中身の確かめ（仕様書 7章）。
 * JSON の読み書き（android.util.JsonReader／JsonWriter）を使わないので、単体テストできる。
 *
 * 1行は「項目の名前 → 値」で、値は文字・数（Long）・true/false・文字の配列のどれか。値が null の項目は書かない。
 * 項目の名前は表の列の名前と同じ。データの番号と変化の連番は書かない。
 */
object FileRows {

    /** 1行の項目（書く順）。 */
    typealias Row = List<Pair<String, Any>>

    // ---- 表の行 → 1行 ----

    fun device(d: DeviceInfo): Row = row(
        "id" to d.id,
        "model" to d.model,
        "os_version" to d.osVersion,
        "sdk_int" to d.sdkInt.toLong(),
        "app_version" to d.appVersion,
    )

    fun settings(s: TransferSettings): Row = row(
        "check_interval_minutes" to s.checkIntervalMinutes.toLong(),
        "theme_mode" to s.themeMode.name,
    )

    fun baseline(e: BaselineAppEntity): Row = row(
        "package_name" to e.packageName,
        "captured_at" to e.capturedAt,
        "name" to e.name,
        "version_name" to e.versionName,
        "version_code" to e.versionCode,
        "kind" to e.kind.name,
        "source" to e.source.name,
        "installer_package" to e.installerPackage,
        "initiating_package" to e.initiatingPackage,
        "is_enabled" to e.isEnabled,
        "warnings" to e.warnings.map { it.name }.sorted(),
        "signer_sha256" to e.signerSha256,
        "first_install_time" to e.firstInstallTime,
        "last_update_time" to e.lastUpdateTime,
    )

    fun current(e: AppCurrentEntity): Row = row(
        "package_name" to e.packageName,
        "name" to e.name,
        "version_name" to e.versionName,
        "version_code" to e.versionCode,
        "kind" to e.kind.name,
        "source" to e.source.name,
        "installer_package" to e.installerPackage,
        "initiating_package" to e.initiatingPackage,
        "is_enabled" to e.isEnabled,
        "warnings" to e.warnings.map { it.name }.sorted(),
        "signer_sha256" to e.signerSha256,
        "first_install_time" to e.firstInstallTime,
        "last_update_time" to e.lastUpdateTime,
        "is_present" to e.isPresent,
        "first_seen_at" to e.firstSeenAt,
        "added_at" to e.addedAt,
        "removed_at" to e.removedAt,
        "reinstalled_at" to e.reinstalledAt,
        "signature_changed_at" to e.signatureChangedAt,
        "updated_at" to e.updatedAt,
    )

    fun event(e: AppEventEntity): Row = row(
        "occurred_at" to e.occurredAt,
        "type" to e.type.name,
        "package_name" to e.packageName,
        "name" to e.name,
        "version_name" to e.versionName,
        "version_code" to e.versionCode,
        "old_version_name" to e.oldVersionName,
        "old_version_code" to e.oldVersionCode,
        "source" to e.source?.name,
        "is_reinstall" to e.isReinstall,
        "old_status" to e.oldStatus?.name,
        "new_status" to e.newStatus?.name,
        "reason" to e.reason?.name,
        "old_admin_memo" to e.oldAdminMemo,
        "new_admin_memo" to e.newAdminMemo,
        "old_reinstalled_at" to e.oldReinstalledAt,
        "old_signature_changed_at" to e.oldSignatureChangedAt,
        "old_pre_registered" to e.oldPreRegistered,
        "old_provisional_name" to e.oldProvisionalName,
        "new_provisional_name" to e.newProvisionalName,
        "operation_id" to e.operationId,
    )

    fun policy(e: AppPolicyEntity): Row = row(
        "package_name" to e.packageName,
        "review_status" to e.reviewStatus.name,
        "admin_memo" to e.adminMemo,
        "is_pre_registered" to e.isPreRegistered,
        "provisional_name" to e.provisionalName,
        "status_reason" to e.statusReason?.name,
        "updated_at" to e.updatedAt,
    )

    fun scanDay(e: ScanDailyEntity): Row = row(
        "date" to e.date,
        "count" to e.count.toLong(),
        "last_at" to e.lastAt,
    )

    /** null の項目を除く。 */
    private fun row(vararg pairs: Pair<String, Any?>): Row = pairs.mapNotNull { (name, value) -> value?.let { name to it } }

    // ---- 1行 → 表の行（必要な項目がない・形が違う・知らない値なら InvalidFileException） ----

    fun deviceOf(map: Map<String, Any?>): DeviceInfo {
        val f = Fields(map)
        return DeviceInfo(id = f.str("id"), model = f.str("model"), osVersion = f.str("os_version"), sdkInt = f.int("sdk_int"), appVersion = f.str("app_version"))
    }

    fun settingsOf(map: Map<String, Any?>): TransferSettings {
        val f = Fields(map)
        val interval = f.int("check_interval_minutes")
        if (CheckInterval.entries.none { it.minutes == interval }) throw InvalidFileException("チェック間隔の値が違います: $interval")
        return TransferSettings(checkIntervalMinutes = interval, themeMode = f.enum<ThemeMode>("theme_mode"))
    }

    fun baselineOf(map: Map<String, Any?>): BaselineAppEntity {
        val f = Fields(map)
        return BaselineAppEntity(
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
    }

    fun currentOf(map: Map<String, Any?>): AppCurrentEntity {
        val f = Fields(map)
        return AppCurrentEntity(
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
    }

    fun eventOf(map: Map<String, Any?>): AppEventEntity {
        val f = Fields(map)
        return AppEventEntity(
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
    }

    fun policyOf(map: Map<String, Any?>): AppPolicyEntity {
        val f = Fields(map)
        return AppPolicyEntity(
            datasetId = "",
            packageName = f.str("package_name"),
            reviewStatus = f.enum<ReviewStatus>("review_status"),
            adminMemo = f.strOrNull("admin_memo"),
            isPreRegistered = f.bool("is_pre_registered"),
            provisionalName = f.strOrNull("provisional_name"),
            statusReason = f.enumOrNull<StatusChangeReason>("status_reason"),
            updatedAt = f.long("updated_at"),
        )
    }

    fun scanDayOf(map: Map<String, Any?>): ScanDailyEntity {
        val f = Fields(map)
        return ScanDailyEntity(datasetId = "", date = f.str("date"), count = f.int("count"), lastAt = f.long("last_at"))
    }

    // ---- 先頭の項目の判断 ----

    /** 先頭の項目（形式の名前と版）を読んだ後の判断。 */
    sealed interface Header {
        data object Ok : Header
        data object NotAppWatch : Header
        data class TooNew(val version: Int) : Header
    }

    /** 形式の名前が違えば AppWatch のファイルではない。版がこのアプリより新しければ取り込まない（記録の部分は読まない）。 */
    fun checkHeader(format: String?, version: Int): Header = when {
        format != AppWatchFile.FORMAT_NAME || version < 1 -> Header.NotAppWatch
        version > AppWatchFile.CURRENT_VERSION -> Header.TooNew(version)
        else -> Header.Ok
    }

    // ---- 中身の確かめ ----

    /** データベースに入れられる形か（同じ行が2つない、日付の形、など）を確かめる。 */
    fun validate(file: AppWatchFile) {
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
            is List<*> -> v.map { it as? String ?: throw InvalidFileException("$name に文字でない値があります") }
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
