package io.github.eightbrows.appwatch.data.settings

/** 初回設定の状態（DataStore）。 */
data class SetupState(
    val completed: Boolean,
) {
    /** 起動したときに開く画面。 */
    val startDestination: StartDestination
        get() = if (completed) StartDestination.TOP else StartDestination.SETUP

    companion object {
        /** 前の版で「閲覧専用」を選んでいたときに保存されていた、使い方の値。 */
        const val LEGACY_VIEW_ONLY: String = "VIEW_ONLY"

        /**
         * 保存されている値から読む。
         * 前の版には「見守る端末／閲覧専用」の区別があった。閲覧専用だった端末はこの端末のアプリを記録していないので、
         * 更新後は初回設定が未完了の扱いにして、初回設定から始める（この端末の記録・取り込んだデータは消さない）。
         * @param legacyUsageMode 前の版の使い方の値（初回設定を終えると消す）
         */
        fun fromStored(completed: Boolean, legacyUsageMode: String?): SetupState =
            SetupState(completed = completed && legacyUsageMode != LEGACY_VIEW_ONLY)
    }
}

/** 起動したときに開く画面。 */
enum class StartDestination {
    /** 初回設定 */
    SETUP,

    /** トップ */
    TOP,
}
