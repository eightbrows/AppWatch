package io.github.eightbrows.appwatch.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// 画面サンプル（docs/mockups）に合わせた色。ダークは同じ意味の色を暗い背景向けに調整したもの。

// ---- ライト ----
internal val LightBackground = Color(0xFFF5F4F0)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF1C1B1A)
internal val LightOnSurfaceVariant = Color(0xFF66625C)
internal val LightOutline = Color(0xFFCFCBC4)
internal val LightOutlineVariant = Color(0xFFE6E3DD)
internal val LightPrimary = Color(0xFF1F4E9C)

// ---- ダーク ----
internal val DarkBackground = Color(0xFF141414)
internal val DarkSurface = Color(0xFF222120)
internal val DarkOnSurface = Color(0xFFEDEAE5)
internal val DarkOnSurfaceVariant = Color(0xFFB4AFA8)
internal val DarkOutline = Color(0xFF5A5650)
internal val DarkOutlineVariant = Color(0xFF34322F)
internal val DarkPrimary = Color(0xFFA9C4F5)

/** Material 3 の色の組にない、このアプリ用の色。 */
@Immutable
data class AppWatchColors(
    /** 要注意・削除ボタン */
    val danger: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    /** 警告あり */
    val warning: Color,
    val warningBorder: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** 問題なし・許可済み */
    val ok: Color,
    val onOk: Color,
    /** 未確認の印 */
    val unconfirmed: Color,
    /** 切り替えボタンの背景と、選ばれているもの */
    val segmentTrack: Color,
    val segmentSelected: Color,
    /** 「管理者用」の印 */
    val adminBadge: Color,
    val onAdminBadge: Color,
    /** トップ右上の歯車（薄い色） */
    val subtleIcon: Color,
)

internal val LightAppWatchColors = AppWatchColors(
    danger = Color(0xFFB3261E),
    onDanger = Color(0xFFFFFFFF),
    dangerContainer = Color(0xFFFDECEA),
    onDangerContainer = Color(0xFF9C1F18),
    warning = Color(0xFF9A4A00),
    warningBorder = Color(0xFFEBA77A),
    warningContainer = Color(0xFFFFF3E5),
    onWarningContainer = Color(0xFF8A4300),
    ok = Color(0xFF2E7D32),
    onOk = Color(0xFFFFFFFF),
    unconfirmed = Color(0xFF8A867F),
    segmentTrack = Color(0xFFE8E5DF),
    segmentSelected = Color(0xFFFFFFFF),
    adminBadge = Color(0xFF4A4F55),
    onAdminBadge = Color(0xFFFFFFFF),
    subtleIcon = Color(0xFF9E9A94),
)

internal val DarkAppWatchColors = AppWatchColors(
    danger = Color(0xFFC6352C),
    onDanger = Color(0xFFFFFFFF),
    dangerContainer = Color(0xFF3D1E1B),
    onDangerContainer = Color(0xFFFFB4AB),
    warning = Color(0xFFF2B37E),
    warningBorder = Color(0xFFA8693F),
    warningContainer = Color(0xFF3A2A1A),
    onWarningContainer = Color(0xFFF6C38F),
    ok = Color(0xFF81C784),
    onOk = Color(0xFF0D2B10),
    unconfirmed = Color(0xFF9A958E),
    segmentTrack = Color(0xFF2F2D2B),
    segmentSelected = Color(0xFF4A4744),
    adminBadge = Color(0xFF5A6068),
    onAdminBadge = Color(0xFFFFFFFF),
    subtleIcon = Color(0xFF77736D),
)
