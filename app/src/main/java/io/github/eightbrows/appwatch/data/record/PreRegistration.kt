package io.github.eightbrows.appwatch.data.record

/**
 * 事前登録した日（これまでの経緯の「事前登録」の日時）を、変化の記録から求める。Android を使わない純粋な関数。
 * 事前登録・解除と、その「元に戻す」を古い順にたどる。「元に戻す」は直前の操作の前の状態に戻す。
 */
object PreRegistration {

    /** 1つのアプリの、今の事前登録の日時（登録されていなければ null）。[events] は古い順。 */
    fun registeredAt(events: List<AppChange>): Long? {
        var current: Long? = null
        // 操作の前の状態（「元に戻す」で戻す先）
        val before = ArrayDeque<Long?>()
        for (event in events) {
            val isUndo = event.reason == StatusChangeReason.UNDO
            when {
                event.type != ChangeType.PRE_REGISTERED && event.type != ChangeType.UNREGISTERED -> Unit
                isUndo -> current = if (before.isEmpty()) current else before.removeLast()
                event.type == ChangeType.PRE_REGISTERED -> {
                    before.addLast(current)
                    current = event.occurredAt
                }
                else -> {
                    before.addLast(current)
                    current = null
                }
            }
        }
        return current
    }

    /** アプリごとの事前登録の日時（登録されているアプリだけ）。[events] は古い順。 */
    fun registeredDates(events: List<AppChange>): Map<String, Long> =
        events.groupBy { it.packageName }
            .mapNotNull { (packageName, list) -> registeredAt(list)?.let { packageName to it } }
            .toMap()
}
