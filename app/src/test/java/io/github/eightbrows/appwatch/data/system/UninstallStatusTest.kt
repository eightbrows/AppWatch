package io.github.eightbrows.appwatch.data.system

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Test

class UninstallStatusTest {

    @Test
    fun 確認が要るときはAndroidの確認を出す() {
        assertEquals(UninstallStatus.Step.NeedsConfirmation, UninstallStatus.stepOf(PackageInstaller.STATUS_PENDING_USER_ACTION))
    }

    @Test
    fun 成功なら削除された() {
        assertEquals(UninstallStatus.Step.Finished(UninstallResult.DELETED), UninstallStatus.stepOf(PackageInstaller.STATUS_SUCCESS))
    }

    @Test
    fun キャンセルや失敗は削除されなかった() {
        val notDeleted = UninstallStatus.Step.Finished(UninstallResult.NOT_DELETED)
        for (status in listOf(
            PackageInstaller.STATUS_FAILURE_ABORTED, // Android の確認で「キャンセル」
            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
        )) {
            assertEquals(notDeleted, UninstallStatus.stepOf(status))
        }
    }
}
