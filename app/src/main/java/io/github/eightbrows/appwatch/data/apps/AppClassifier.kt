package io.github.eightbrows.appwatch.data.apps

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.InstalledApp

/**
 * 種類・入手元・警告の判定（仕様書 4章）。Android の API を使わない純粋な関数だけを置く。
 * 「署名が変わった」「再インストール」は記録と比べないと分からないので、ここでは判定しない。
 */
object AppClassifier {

    /** Google Play ストアとして扱うインストール元。古い端末ではフィードバック用のパッケージ名が返ることがある。 */
    val GOOGLE_PLAY_INSTALLERS: Set<String> = setOf("com.android.vending", "com.google.android.feedback")

    /** 開発用の署名（Android SDK が自動で作るデバッグ用の鍵）の持ち主の名前。 */
    private const val DEBUG_CERTIFICATE_CN = "Android Debug"

    fun kind(isSystem: Boolean, hasLauncherIcon: Boolean): AppKind = when {
        !isSystem -> AppKind.USER_INSTALLED
        hasLauncherIcon -> AppKind.PREINSTALLED
        else -> AppKind.SYSTEM
    }

    fun source(installerPackage: String?): InstallSource = when {
        installerPackage.isNullOrBlank() -> InstallSource.UNKNOWN
        installerPackage in GOOGLE_PLAY_INSTALLERS -> InstallSource.GOOGLE_PLAY
        else -> InstallSource.OTHER
    }

    /**
     * 端末の情報だけで決まる警告（Google Play 以外、入手元が分からない、開発用の署名）。
     * 警告は「後から導入」のアプリにだけ付ける（仕様書 4章）。プリインストールやシステムは
     * 入手元が取れないのが普通で、メーカーが開発用の鍵で署名していることもあるため。
     * 「署名が変わった」「再インストール」は記録と比べて決める（ChangeDetector）。
     */
    fun warnings(kind: AppKind, source: InstallSource, signerSubjects: List<String>): Set<AppWarning> = buildSet {
        if (kind != AppKind.USER_INSTALLED) return@buildSet
        when (source) {
            InstallSource.GOOGLE_PLAY -> Unit
            InstallSource.OTHER -> add(AppWarning.NOT_FROM_PLAY)
            InstallSource.UNKNOWN -> add(AppWarning.UNKNOWN_SOURCE)
        }
        if (signerSubjects.any { isDebugCertificate(it) }) add(AppWarning.DEBUG_SIGNATURE)
    }

    /** 証明書の持ち主の名前の CN が「Android Debug」なら開発用の署名。 */
    fun isDebugCertificate(subjectDn: String): Boolean =
        parseDistinguishedName(subjectDn).any { (key, value) ->
            key.equals("CN", ignoreCase = true) && value.equals(DEBUG_CERTIFICATE_CN, ignoreCase = true)
        }

    fun classify(snapshot: PackageSnapshot, selfPackageName: String): InstalledApp {
        val kind = kind(snapshot.isSystem, snapshot.hasLauncherIcon)
        val source = source(snapshot.installerPackage)
        return InstalledApp(
            packageName = snapshot.packageName,
            name = snapshot.label.ifBlank { snapshot.packageName },
            versionName = snapshot.versionName,
            versionCode = snapshot.versionCode,
            kind = kind,
            source = source,
            installerPackage = snapshot.installerPackage,
            initiatingPackage = snapshot.initiatingPackage,
            warnings = warnings(kind, source, snapshot.signers.map { it.subjectDn }),
            signerSha256 = snapshot.signers.map { it.sha256 },
            firstInstallTime = snapshot.firstInstallTime,
            lastUpdateTime = snapshot.lastUpdateTime,
            isEnabled = snapshot.isEnabled,
            isSelf = snapshot.packageName == selfPackageName,
        )
    }

    /**
     * RFC 2253 形式の名前（例：C=US,O=Android,CN=Android Debug）を「項目=値」の並びに分ける。
     * 「\,」のように逃がした区切りは値の一部として扱う。表記ゆれに備えて「, 」区切りも受け付ける。
     */
    internal fun parseDistinguishedName(dn: String): List<Pair<String, String>> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (c in dn) {
            when {
                escaped -> { current.append(c); escaped = false }
                c == '\\' -> escaped = true
                c == ',' || c == ';' || c == '+' -> { parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        parts += current.toString()
        return parts.mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
        }
    }
}
