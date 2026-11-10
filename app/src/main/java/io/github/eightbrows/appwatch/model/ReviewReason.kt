package io.github.eightbrows.appwatch.model

/**
 * 確認が必要になった理由。画面では色付きの札で出す。宣言の順が優先の順（札を並べる順）。
 * [isCaution] は赤系（要注意）、それ以外はオレンジ系（警告）。
 */
enum class ReviewReason(val isCaution: Boolean = false) {
    CAUTION(isCaution = true),
    REINSTALLED,
    SIGNATURE_CHANGED,
    DEBUG_SIGNATURE,
    NOT_FROM_PLAY,
    UNKNOWN_SOURCE,
    ;

    /** 入手元についての理由か（同じことを書いた入手元の文を出さないために使う）。 */
    val isSource: Boolean get() = this == NOT_FROM_PLAY || this == UNKNOWN_SOURCE

    companion object {

        /**
         * 警告と確認状態から、札に出す理由を優先の順に並べる。
         * [includeCaution] が true なら、要注意のときに「要注意」の札を先頭に足す。
         * [isSelf]（本アプリ自身）なら、警告の札は出さない（警告の判定・記録はそのまま。表示だけ）。
         */
        fun of(
            warnings: Set<AppWarning>,
            status: ReviewStatus,
            includeCaution: Boolean = true,
            isSelf: Boolean = false,
        ): List<ReviewReason> {
            val reasons = if (isSelf) HashSet<ReviewReason>() else warnings.mapTo(HashSet()) { it.toReason() }
            if (includeCaution && status == ReviewStatus.CAUTION) reasons += CAUTION
            return reasons.sortedBy { it.ordinal }
        }

        /** トップのカード・一覧に出す札。「問題なし」にしたアプリは確認が必要ではないので、札を出さない。 */
        fun forTop(app: AppSummary): List<ReviewReason> =
            if (app.reviewStatus == ReviewStatus.OK) emptyList() else of(app.warnings, app.reviewStatus, isSelf = app.isSelf)

        /** 入手元の札を出すときは、説明の文に入手元を重ねて書かない（日時などだけにする）。 */
        fun hidesSourceText(shown: List<ReviewReason>): Boolean = shown.any { it.isSource }

        private fun AppWarning.toReason(): ReviewReason = when (this) {
            AppWarning.NOT_FROM_PLAY -> NOT_FROM_PLAY
            AppWarning.UNKNOWN_SOURCE -> UNKNOWN_SOURCE
            AppWarning.DEBUG_SIGNATURE -> DEBUG_SIGNATURE
            AppWarning.SIGNATURE_CHANGED -> SIGNATURE_CHANGED
            AppWarning.REINSTALLED -> REINSTALLED
        }
    }
}
