package io.github.eightbrows.appwatch.data.apps

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppWarning
import io.github.eightbrows.appwatch.model.InstallSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppClassifierTest {

    private val debugDn = "C=US,O=Android,CN=Android Debug"
    private val platformTestKeyDn =
        "1.2.840.113549.1.9.1=#16126164726f696440616e64726f69642e636f6d,CN=Android,OU=Android," +
            "O=Android,L=Mountain View,ST=California,C=US"
    private val releaseDn = "CN=Example Inc.,O=Example,C=JP"

    // ---- 種類 ----

    @Test
    fun kind_noSystemFlag_isUserInstalled() {
        assertEquals(AppKind.USER_INSTALLED, AppClassifier.kind(isSystem = false, hasLauncherIcon = true))
        assertEquals(AppKind.USER_INSTALLED, AppClassifier.kind(isSystem = false, hasLauncherIcon = false))
    }

    @Test
    fun kind_systemFlagWithLauncherIcon_isPreinstalled() {
        assertEquals(AppKind.PREINSTALLED, AppClassifier.kind(isSystem = true, hasLauncherIcon = true))
    }

    @Test
    fun kind_systemFlagWithoutLauncherIcon_isSystem() {
        assertEquals(AppKind.SYSTEM, AppClassifier.kind(isSystem = true, hasLauncherIcon = false))
    }

    // ---- 入手元 ----

    @Test
    fun source_playStore() {
        assertEquals(InstallSource.GOOGLE_PLAY, AppClassifier.source("com.android.vending"))
        assertEquals(InstallSource.GOOGLE_PLAY, AppClassifier.source("com.google.android.feedback"))
    }

    @Test
    fun source_otherInstaller() {
        assertEquals(InstallSource.OTHER, AppClassifier.source("com.google.android.packageinstaller"))
        assertEquals(InstallSource.OTHER, AppClassifier.source("com.amazon.venezia"))
    }

    @Test
    fun source_unknownWhenMissing() {
        assertEquals(InstallSource.UNKNOWN, AppClassifier.source(null))
        assertEquals(InstallSource.UNKNOWN, AppClassifier.source(""))
        assertEquals(InstallSource.UNKNOWN, AppClassifier.source("  "))
    }

    // ---- 開発用の署名 ----

    @Test
    fun debugCertificate_detected() {
        assertTrue(AppClassifier.isDebugCertificate(debugDn))
        assertTrue(AppClassifier.isDebugCertificate("CN=Android Debug, O=Android, C=US"))
        assertTrue(AppClassifier.isDebugCertificate("cn=android debug,o=Android"))
    }

    @Test
    fun debugCertificate_notDetectedForOtherKeys() {
        assertFalse(AppClassifier.isDebugCertificate(platformTestKeyDn))
        assertFalse(AppClassifier.isDebugCertificate(releaseDn))
        assertFalse(AppClassifier.isDebugCertificate("CN=Android Debugger,O=Android"))
        assertFalse(AppClassifier.isDebugCertificate("O=Android Debug,CN=Release"))
        assertFalse(AppClassifier.isDebugCertificate(""))
    }

    @Test
    fun debugCertificate_escapedCommaIsPartOfValue() {
        // 逃がしたカンマを含む1つの値「Foo, CN=Android Debug」は、開発用の署名ではない
        assertFalse(AppClassifier.isDebugCertificate("CN=Foo\\, CN=Android Debug,O=Example"))
    }

    // ---- 警告 ----

    @Test
    fun warnings_userInstalledFromOutsidePlay() {
        assertEquals(
            setOf(AppWarning.NOT_FROM_PLAY),
            AppClassifier.warnings(AppKind.USER_INSTALLED, InstallSource.OTHER, listOf(releaseDn)),
        )
        assertEquals(
            setOf(AppWarning.UNKNOWN_SOURCE),
            AppClassifier.warnings(AppKind.USER_INSTALLED, InstallSource.UNKNOWN, listOf(releaseDn)),
        )
        assertEquals(
            emptySet<AppWarning>(),
            AppClassifier.warnings(AppKind.USER_INSTALLED, InstallSource.GOOGLE_PLAY, listOf(releaseDn)),
        )
    }

    @Test
    fun warnings_sourceIsIgnoredForPreinstalledAndSystem() {
        assertEquals(
            emptySet<AppWarning>(),
            AppClassifier.warnings(AppKind.PREINSTALLED, InstallSource.UNKNOWN, listOf(platformTestKeyDn)),
        )
        assertEquals(emptySet<AppWarning>(), AppClassifier.warnings(AppKind.SYSTEM, InstallSource.OTHER, emptyList()))
    }

    @Test
    fun warnings_debugSignatureForAnyKind() {
        assertEquals(
            setOf(AppWarning.UNKNOWN_SOURCE, AppWarning.DEBUG_SIGNATURE),
            AppClassifier.warnings(AppKind.USER_INSTALLED, InstallSource.UNKNOWN, listOf(debugDn)),
        )
        assertEquals(
            setOf(AppWarning.DEBUG_SIGNATURE),
            AppClassifier.warnings(AppKind.SYSTEM, InstallSource.UNKNOWN, listOf(releaseDn, debugDn)),
        )
    }

    // ---- まとめて ----

    private fun snapshot(
        packageName: String = "com.example.app",
        label: String = "Example",
        isSystem: Boolean = false,
        installer: String? = "com.android.vending",
        signers: List<String> = listOf(releaseDn),
    ) = PackageSnapshot(
        packageName = packageName,
        label = label,
        versionName = "1.2.3",
        versionCode = 123,
        isSystem = isSystem,
        hasLauncherIcon = true,
        isEnabled = true,
        installerPackage = installer,
        initiatingPackage = "com.android.chrome",
        signers = signers.map { SignerInfo(subjectDn = it, sha256 = "ab") },
        firstInstallTime = 1_000L,
        lastUpdateTime = 2_000L,
    )

    @Test
    fun classify_playApp() {
        val app = AppClassifier.classify(snapshot(), selfPackageName = "io.github.eightbrows.appwatch")
        assertEquals(AppKind.USER_INSTALLED, app.kind)
        assertEquals(InstallSource.GOOGLE_PLAY, app.source)
        assertTrue(app.warnings.isEmpty())
        assertFalse(app.isSelf)
        assertEquals("com.android.chrome", app.initiatingPackage)
        assertEquals(listOf("ab"), app.signerSha256)
    }

    @Test
    fun classify_selfApp_isLabelledAndJudgedLikeOthers() {
        val app = AppClassifier.classify(
            snapshot(packageName = "io.github.eightbrows.appwatch", installer = null, signers = listOf(debugDn)),
            selfPackageName = "io.github.eightbrows.appwatch",
        )
        assertTrue(app.isSelf)
        assertEquals(setOf(AppWarning.UNKNOWN_SOURCE, AppWarning.DEBUG_SIGNATURE), app.warnings)
    }

    @Test
    fun classify_blankLabel_fallsBackToPackageName() {
        val app = AppClassifier.classify(snapshot(label = " "), selfPackageName = "x")
        assertEquals("com.example.app", app.name)
    }

    @Test
    fun classify_preinstalledWithoutInstaller_hasNoWarning() {
        val app = AppClassifier.classify(snapshot(isSystem = true, installer = null), selfPackageName = "x")
        assertEquals(AppKind.PREINSTALLED, app.kind)
        assertEquals(InstallSource.UNKNOWN, app.source)
        assertTrue(app.warnings.isEmpty())
    }

    @Test
    fun parseDistinguishedName_splitsAndTrims() {
        assertEquals(
            listOf("CN" to "Android Debug", "O" to "Android", "C" to "US"),
            AppClassifier.parseDistinguishedName("CN=Android Debug, O=Android, C=US"),
        )
        assertTrue(AppClassifier.parseDistinguishedName("garbage").isEmpty())
    }
}
