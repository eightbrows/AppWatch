package io.github.eightbrows.appwatch.data.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 事前登録の一覧に出す「登録した日」（ステップ19）。 */
class PreRegistrationTest {

    private fun event(type: ChangeType, at: Long, pkg: String = "jp.example.app", undo: Boolean = false) =
        AppChange(
            type = type,
            packageName = pkg,
            name = pkg,
            occurredAt = at,
            reason = if (undo) StatusChangeReason.UNDO else StatusChangeReason.ADMIN,
        )

    @Test
    fun registered_isTheRegistrationTime() {
        assertEquals(100L, PreRegistration.registeredAt(listOf(event(ChangeType.PRE_REGISTERED, 100))))
    }

    @Test
    fun registeringAgain_usesTheLatestTime() {
        val events = listOf(event(ChangeType.PRE_REGISTERED, 100), event(ChangeType.PRE_REGISTERED, 200))
        assertEquals(200L, PreRegistration.registeredAt(events))
    }

    @Test
    fun unregistered_hasNoDate() {
        val events = listOf(event(ChangeType.PRE_REGISTERED, 100), event(ChangeType.UNREGISTERED, 200))
        assertNull(PreRegistration.registeredAt(events))
    }

    @Test
    fun undoingTheUnregistration_bringsBackTheOriginalDate() {
        val events = listOf(
            event(ChangeType.PRE_REGISTERED, 100),
            event(ChangeType.UNREGISTERED, 200),
            event(ChangeType.UNREGISTERED, 210, undo = true),
        )
        assertEquals(100L, PreRegistration.registeredAt(events))
    }

    @Test
    fun undoingARegistration_goesBackToTheStateBefore() {
        // 登録 → もう一度登録 → 2回目を取り消し：1回目の日のまま
        val again = listOf(
            event(ChangeType.PRE_REGISTERED, 100),
            event(ChangeType.PRE_REGISTERED, 200),
            event(ChangeType.PRE_REGISTERED, 210, undo = true),
        )
        assertEquals(100L, PreRegistration.registeredAt(again))
        // 1回だけ登録して取り消し：登録なし
        assertNull(PreRegistration.registeredAt(listOf(event(ChangeType.PRE_REGISTERED, 100), event(ChangeType.PRE_REGISTERED, 110, undo = true))))
    }

    @Test
    fun otherEvents_areIgnored_andDatesAreGroupedByApp() {
        val events = listOf(
            event(ChangeType.PRE_REGISTERED, 100, pkg = "a"),
            event(ChangeType.MEMO_CHANGED, 150, pkg = "a"),
            event(ChangeType.PRE_REGISTERED, 120, pkg = "b"),
            event(ChangeType.UNREGISTERED, 130, pkg = "b"),
            event(ChangeType.PRE_REGISTERED, 140, pkg = "c"),
        )
        assertEquals(mapOf("a" to 100L, "c" to 140L), PreRegistration.registeredDates(events))
    }
}
