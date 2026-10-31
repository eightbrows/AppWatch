package io.github.eightbrows.appwatch.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 端末のアプリ一覧を PackageManager から読む（QUERY_ALL_PACKAGES が必要）。
 * 判定はせず、生の情報（PackageSnapshot）にするだけ。時間がかかるので、メインスレッド以外で呼ぶこと。
 */
class PackageScanner(context: Context) {

    private val packageManager: PackageManager = context.packageManager

    fun scan(): List<PackageSnapshot> {
        val launcherPackages = launcherPackages()
        return installedPackages().mapNotNull { info ->
            // 1件の読み取りに失敗しても（取得中に削除された等）、他のアプリは出す
            runCatching { toSnapshot(info, launcherPackages) }.getOrNull()
        }
    }

    private fun installedPackages(): List<PackageInfo> {
        val flags = signatureFlag() or
            PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }
    }

    /** 署名の取得方法は Android 9 以上と 8.x で異なる。 */
    private fun signatureFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    /** ホーム画面にアイコンを出しているアプリ。無効にされたアプリも含める。 */
    private fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            packageManager.queryIntentActivities(intent, flags)
        }
        return activities.mapNotNullTo(HashSet()) { it.activityInfo?.packageName }
    }

    private fun toSnapshot(info: PackageInfo, launcherPackages: Set<String>): PackageSnapshot {
        val appInfo: ApplicationInfo? = info.applicationInfo
        val packageName = info.packageName
        val (installer, initiating) = installSource(packageName)
        return PackageSnapshot(
            packageName = packageName,
            label = appInfo?.loadLabel(packageManager)?.toString().orEmpty(),
            versionName = info.versionName,
            versionCode = PackageInfoCompat.getLongVersionCode(info),
            isSystem = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            hasLauncherIcon = packageName in launcherPackages,
            isEnabled = appInfo?.enabled ?: true,
            installerPackage = installer,
            initiatingPackage = initiating,
            signers = signers(info).map { it.toSignerInfo() },
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
        )
    }

    /**
     * 入手元。Android 11 以上は getInstallSourceInfo（入手のきっかけになったアプリも分かる）、
     * Android 8〜10 は getInstallerPackageName（インストールしたアプリだけ）。
     */
    private fun installSource(packageName: String): Pair<String?, String?> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { packageManager.getInstallSourceInfo(packageName) }
                .map { it.installingPackageName to it.initiatingPackageName }
                .getOrDefault(null to null)
        } else {
            @Suppress("DEPRECATION")
            runCatching { packageManager.getInstallerPackageName(packageName) }.getOrNull() to null
        }

    private fun signers(info: PackageInfo): List<Signature> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners?.toList().orEmpty()
            } else {
                // 鍵の入れ替え（ローテーション）の履歴は古い順に並び、最後が今の証明書
                listOfNotNull(signingInfo.signingCertificateHistory?.lastOrNull())
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }

    private fun Signature.toSignerInfo(): SignerInfo {
        val bytes = toByteArray()
        val subject = runCatching {
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
            certificate.subjectX500Principal.name
        }.getOrDefault("")
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return SignerInfo(subjectDn = subject, sha256 = sha256)
    }
}
