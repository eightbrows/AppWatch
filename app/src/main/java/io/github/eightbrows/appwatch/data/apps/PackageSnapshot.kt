package io.github.eightbrows.appwatch.data.apps

/**
 * 端末から読んだアプリ1件の生の情報。Android の型を含まないので、判定の処理を単体テストできる。
 * 時刻はエポックミリ秒。
 */
data class PackageSnapshot(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    /** ApplicationInfo.FLAG_SYSTEM（更新済みのプリインストールも含む） */
    val isSystem: Boolean,
    /** ホーム画面（ランチャー）にアイコンがあるか */
    val hasLauncherIcon: Boolean,
    val isEnabled: Boolean,
    val installerPackage: String?,
    val initiatingPackage: String?,
    /** 今の署名の証明書 */
    val signers: List<SignerInfo>,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
)

/** 署名の証明書1つ。 */
data class SignerInfo(
    /** 証明書の持ち主の名前（RFC 2253 形式。例：C=US,O=Android,CN=Android Debug） */
    val subjectDn: String,
    /** 証明書の SHA-256（16進の小文字） */
    val sha256: String,
)
