package io.github.eightbrows.appwatch.ui.setup

import io.github.eightbrows.appwatch.model.AppKind
import io.github.eightbrows.appwatch.model.AppSummary
import io.github.eightbrows.appwatch.model.InstallSource
import io.github.eightbrows.appwatch.model.ReviewStatus

/** 初回設定の2「最初のチェック」の項目（仕様書 6章の表の順）。 */
enum class SetupCategory {
    SYSTEM,
    PREINSTALLED,
    GOOGLE_PLAY,
    OTHER_SOURCE,
    UNKNOWN_SOURCE,

    /** このアプリ（本アプリ自身） */
    SELF,
}

/**
 * 「最初のチェック」で、まとめて「問題なし」にするアプリを選ぶ。Android を使わない純粋な関数だけを置く。
 */
object SetupSelection {

    /** 最初にチェックが付いている項目（仕様書 6章の表）。 */
    val DEFAULT_CHECKED: Set<SetupCategory> = setOf(
        SetupCategory.SYSTEM,
        SetupCategory.PREINSTALLED,
        SetupCategory.GOOGLE_PLAY,
        SetupCategory.SELF,
    )

    /** アプリがどの項目に入るか。本アプリ自身は「このアプリ」の行だけで数える。 */
    fun categoryOf(app: AppSummary): SetupCategory = when {
        app.isSelf -> SetupCategory.SELF
        app.kind == AppKind.SYSTEM -> SetupCategory.SYSTEM
        app.kind == AppKind.PREINSTALLED -> SetupCategory.PREINSTALLED
        app.source == InstallSource.GOOGLE_PLAY -> SetupCategory.GOOGLE_PLAY
        app.source == InstallSource.OTHER -> SetupCategory.OTHER_SOURCE
        else -> SetupCategory.UNKNOWN_SOURCE
    }

    /** 項目ごとの件数（端末にあるアプリの数。どの項目も 0 件なら 0 を入れる）。 */
    fun countByCategory(presentApps: List<AppSummary>): Map<SetupCategory, Int> {
        val counts = presentApps.groupingBy { categoryOf(it) }.eachCount()
        return SetupCategory.entries.associateWith { counts[it] ?: 0 }
    }

    /**
     * まとめて「問題なし」にするアプリのパッケージ名。
     * - チェックを付けた項目のアプリだけ
     * - 「未確認」のアプリだけ（「要注意」や、すでに「問題なし」のアプリは変えない）
     * - 警告のあるアプリは除く。ただし本アプリ自身は「このアプリ」にチェックがあれば警告があっても含める
     */
    fun targets(presentApps: List<AppSummary>, checked: Set<SetupCategory>): List<String> =
        presentApps
            .filter { app ->
                val category = categoryOf(app)
                category in checked &&
                    app.reviewStatus == ReviewStatus.UNCONFIRMED &&
                    (app.warnings.isEmpty() || category == SetupCategory.SELF)
            }
            .map { it.packageName }
}
