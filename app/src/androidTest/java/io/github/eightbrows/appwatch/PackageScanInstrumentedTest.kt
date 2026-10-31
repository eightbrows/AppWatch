package io.github.eightbrows.appwatch

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.eightbrows.appwatch.data.apps.AppClassifier
import io.github.eightbrows.appwatch.data.apps.PackageScanner
import io.github.eightbrows.appwatch.model.AppKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 実機・エミュレータで、本物のアプリ一覧の取得と判定を確かめる。
 * 画面を使わないので、端末がロック中でも動く。判定結果の件数は logcat（タグ AppWatchScan）に出る。
 */
@RunWith(AndroidJUnit4::class)
class PackageScanInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun scan_findsAppsAndClassifiesSelf() {
        val apps = PackageScanner(context).scan().map { AppClassifier.classify(it, context.packageName) }

        // 一覧が取れている（QUERY_ALL_PACKAGES が効いていれば、システムのアプリだけで数十件以上ある）
        assertTrue("apps=${apps.size}", apps.size > 20)
        assertTrue(apps.any { it.kind == AppKind.SYSTEM })

        // 本アプリが一覧にあり、他と同じに判定されている
        val self = apps.singleOrNull { it.isSelf }
        assertNotNull(self)
        assertEquals(context.packageName, self!!.packageName)
        assertEquals(AppKind.USER_INSTALLED, self.kind)
        assertTrue(self.signerSha256.isNotEmpty())

        // パッケージ名の重複がない
        assertEquals(apps.size, apps.map { it.packageName }.toSet().size)

        logSummary(apps)
    }

    private fun logSummary(apps: List<io.github.eightbrows.appwatch.model.InstalledApp>) {
        val tag = "AppWatchScan"
        Log.i(tag, "sdk=${android.os.Build.VERSION.SDK_INT} total=${apps.size}")
        Log.i(tag, "kinds=${apps.groupingBy { it.kind }.eachCount()}")
        Log.i(tag, "sources=${apps.groupingBy { it.kind to it.source }.eachCount()}")
        Log.i(tag, "warnings=${apps.flatMap { a -> a.warnings.map { it to a.kind } }.groupingBy { it }.eachCount()}")
        Log.i(tag, "disabled=${apps.count { !it.isEnabled }}")
        Log.i(tag, "installers=${apps.filter { it.kind == AppKind.USER_INSTALLED }.groupingBy { it.installerPackage }.eachCount()}")
    }
}
