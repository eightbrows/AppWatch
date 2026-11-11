package io.github.eightbrows.appwatch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.eightbrows.appwatch.R
import io.github.eightbrows.appwatch.ui.theme.AppWatchTheme

/** 一覧の行の先頭のアイコンの大きさ（確認状態の画面・履歴と比較）。 */
val ROW_ICON_SIZE = 48.dp

/** アイコンの右下の角に重ねる印の大きさ。 */
val ROW_MARK_SIZE = 24.dp

/** 印の縁取り（行の背景の色）。どのアイコンの色の上でも印を見分けられるようにする。 */
private val ROW_MARK_RING = 2.5.dp
private val ROW_MARK_OUTER = ROW_MARK_SIZE + ROW_MARK_RING * 2

/**
 * 行の先頭のアイコンと、右下の角に置いた小さな印（確認状態の画面の確認状態、履歴と比較の変化の種類）。
 * 印の中心をアイコンの右下の角に置き、印の大部分をアイコンの外に出す（重なるのは印の 1/4 まで）。印は行の背景の色で縁取る。
 * 外に出た分も場所を取り、文字と重ならないようにする。
 * 端末にないアプリ（[present] が false）は灰色の頭文字の印、取り込んだデータを見ているとき（[loadIcon] が false）は色付きの頭文字の印。
 * 無効にしたアプリ（[dimmed]）は灰色で薄く出す。アイコンの読み込みは [AppIcon] と同じ（記憶あり・画面の外で読み込む）。
 */
@Composable
fun AppIconWithMark(
    name: String,
    packageName: String,
    versionKey: String,
    present: Boolean,
    loadIcon: Boolean,
    dimmed: Boolean,
    ringColor: Color,
    modifier: Modifier = Modifier,
    mark: @Composable () -> Unit,
) {
    Box(modifier.size(ROW_ICON_SIZE + ROW_MARK_OUTER / 2)) {
        if (present) {
            AppIcon(
                name = name,
                packageName = packageName,
                size = ROW_ICON_SIZE,
                versionKey = versionKey,
                loadIcon = loadIcon,
                dimmed = dimmed,
            )
        } else {
            AbsentMark(name, size = ROW_ICON_SIZE)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(ROW_MARK_OUTER)
                .clip(CircleShape)
                .background(ringColor)
                .padding(ROW_MARK_RING),
            contentAlignment = Alignment.Center,
        ) {
            mark()
        }
    }
}

/** 端末にないアプリの、アイコンの代わりの灰色の頭文字の印。 */
@Composable
fun AbsentMark(name: String, size: Dp = 56.dp) {
    val description = stringResource(R.string.detail_absent_mark)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(AppWatchTheme.colors.unconfirmed.copy(alpha = 0.35f))
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name.take(1), color = Color.White, fontSize = (size.value * 0.43f).sp, fontWeight = FontWeight.Bold)
    }
}
