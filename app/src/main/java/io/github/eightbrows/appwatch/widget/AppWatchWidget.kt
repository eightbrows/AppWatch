package io.github.eightbrows.appwatch.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as FixedColor
import io.github.eightbrows.appwatch.MainActivity
import io.github.eightbrows.appwatch.R
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ホーム画面のウィジェット（標準は 4×1、縦に 4×2 まで広げられる。仕様書 6章、画面サンプル 02_widget.png）。
 * 優先度の高い状態を1つだけ出す。タップでアプリを開く（開くとチェックし、ウィジェットも更新される）。
 * 高さに合わせて2つの形を使い分ける：低い形（4×1）は見出し1行と最終チェック、高い形（4×2）は補足の文も出す。
 * 色は res/values と res/values-night の色を使うので、端末のダークモードに従う。
 */
class AppWatchWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(LOW_SIZE, TALL_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = WidgetDataSource(context)
        // 最初の内容を読んでから描く（空のウィジェットが一瞬出ないように）
        val initial = source.observe().first()
        provideContent {
            val state by remember { source.observe() }.collectAsState(initial)
            WidgetContent(state)
        }
    }

    companion object {
        /** 低い形（4×1）。 */
        val LOW_SIZE = DpSize(180.dp, 40.dp)

        /** 高い形（4×2）。この高さ以上なら今までの形にする（4×1 の高さは端末で違うが、この高さには届かない）。 */
        val TALL_SIZE = DpSize(180.dp, 130.dp)

        /** 置かれているウィジェットをすべて最新にする。 */
        suspend fun updateAll(context: Context) {
            AppWatchWidget().updateAll(context)
        }
    }
}

class AppWatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AppWatchWidget()
}

/**
 * 文字の色（ライト／ダーク）。端末のダークモードに従う。
 * 枠と丸の色は res/values・values-night の colors.xml にあり、値をそろえてある。
 */
private object WidgetColors {
    val secondary = ColorProvider(day = Color(0xFF5F5B55), night = Color(0xFFBDB8B1))
    val caution = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFFF8A80))
    val warning = ColorProvider(day = Color(0xFFB8480F), night = Color(0xFFF2B37E))
    val neutral = ColorProvider(day = Color(0xFF5F6368), night = Color(0xFFC4C7CC))
    val ok = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF81C784))
}

/** 状態ごとの見た目。 */
private data class WidgetStyle(
    @DrawableRes val background: Int,
    @DrawableRes val circle: Int,
    /** 丸の中の絵。null なら [symbol] の文字を出す */
    @DrawableRes val icon: Int?,
    val symbol: String,
    val titleColor: androidx.glance.unit.ColorProvider,
)

private fun styleOf(context: Context, status: WidgetStatus): WidgetStyle = when (status) {
    WidgetStatus.CAUTION_APPS -> WidgetStyle(
        R.drawable.widget_bg_caution, R.drawable.widget_circle_caution, null,
        context.getString(R.string.mark_caution), WidgetColors.caution,
    )
    WidgetStatus.CHECK_STOPPED -> WidgetStyle(
        R.drawable.widget_bg_warning, R.drawable.widget_circle_warning, R.drawable.ic_widget_schedule,
        "", WidgetColors.warning,
    )
    WidgetStatus.NEEDS_REVIEW -> WidgetStyle(
        R.drawable.widget_bg_warning, R.drawable.widget_circle_warning, null,
        context.getString(R.string.mark_unconfirmed), WidgetColors.warning,
    )
    WidgetStatus.SETTINGS_NEEDED -> WidgetStyle(
        R.drawable.widget_bg_warning, R.drawable.widget_circle_warning, R.drawable.ic_widget_settings,
        "", WidgetColors.warning,
    )
    WidgetStatus.SETUP_REQUIRED -> WidgetStyle(
        R.drawable.widget_bg_neutral, R.drawable.widget_circle_neutral, R.drawable.ic_widget_list,
        "", WidgetColors.neutral,
    )
    WidgetStatus.ALL_CLEAR -> WidgetStyle(
        R.drawable.widget_bg_ok, R.drawable.widget_circle_ok, R.drawable.ic_widget_check,
        "", WidgetColors.ok,
    )
}

/** 見出し。2行になるときに言葉の途中で改行しないようにする（strings.xml の「|」の所でだけ改行）。 */
private fun titleOf(context: Context, state: WidgetState): String = WidgetText.keepWords(rawTitleOf(context, state))

private fun rawTitleOf(context: Context, state: WidgetState): String {
    val res = context.resources
    return when (state.status) {
        WidgetStatus.CAUTION_APPS -> res.getQuantityString(R.plurals.widget_caution_title, state.count, state.count)
        WidgetStatus.CHECK_STOPPED -> context.getString(R.string.widget_stopped_title)
        WidgetStatus.NEEDS_REVIEW -> res.getQuantityString(R.plurals.widget_review_title, state.count, state.count)
        WidgetStatus.SETTINGS_NEEDED -> context.getString(R.string.widget_settings_title)
        WidgetStatus.SETUP_REQUIRED -> context.getString(R.string.widget_setup_title)
        WidgetStatus.ALL_CLEAR -> context.getString(R.string.widget_clear_title)
    }
}

/** 低い形（4×1）の見出し。1行に入るよう、長い見出しは短い言い方にする。 */
private fun shortTitleOf(context: Context, state: WidgetState): String {
    val res = context.resources
    return when (state.status) {
        WidgetStatus.CAUTION_APPS -> res.getQuantityString(R.plurals.widget_caution_title_short, state.count, state.count)
        WidgetStatus.CHECK_STOPPED -> context.getString(R.string.widget_stopped_title_short)
        WidgetStatus.NEEDS_REVIEW -> res.getQuantityString(R.plurals.widget_review_title_short, state.count, state.count)
        WidgetStatus.SETTINGS_NEEDED -> context.getString(R.string.widget_settings_title_short)
        WidgetStatus.SETUP_REQUIRED -> context.getString(R.string.widget_setup_title_short)
        WidgetStatus.ALL_CLEAR -> context.getString(R.string.widget_clear_title_short)
    }
}

private fun noteOf(context: Context, status: WidgetStatus): String? = when (status) {
    WidgetStatus.CAUTION_APPS -> context.getString(R.string.widget_caution_note)
    WidgetStatus.CHECK_STOPPED -> context.getString(R.string.widget_stopped_note)
    WidgetStatus.NEEDS_REVIEW -> context.getString(R.string.widget_review_note)
    WidgetStatus.SETTINGS_NEEDED -> context.getString(R.string.widget_settings_note)
    WidgetStatus.SETUP_REQUIRED -> context.getString(R.string.widget_setup_note)
    WidgetStatus.ALL_CLEAR -> null
}

private fun lastCheckText(context: Context, lastCheckAt: Long?): String {
    if (lastCheckAt == null) return context.getString(R.string.widget_last_check_none)
    val formatter = DateTimeFormatter.ofPattern(context.getString(R.string.format_widget_date_time), WidgetLocale.localeOf(context))
    val text = formatter.format(Instant.ofEpochMilli(lastCheckAt).atZone(ZoneId.systemDefault()))
    return context.getString(R.string.widget_last_check, text)
}

@Composable
private fun WidgetContent(state: WidgetState) {
    // アプリで選んだ言語の文字にする（言語を切り替えたときは、アプリがウィジェットを更新する）
    val context = WidgetLocale.localized(LocalContext.current)
    if (LocalSize.current.height < AppWatchWidget.TALL_SIZE.height) {
        LowContent(context, state)
    } else {
        TallContent(context, state)
    }
}

/** 状態の印（色付きの丸の中に絵か記号）。 */
@Composable
private fun StatusCircle(style: WidgetStyle, size: Dp, iconSize: Dp, symbolSize: TextUnit) {
    Box(
        modifier = GlanceModifier.size(size).background(ImageProvider(style.circle)),
        contentAlignment = Alignment.Center,
    ) {
        if (style.icon != null) {
            Image(
                provider = ImageProvider(style.icon),
                contentDescription = null,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(FixedColor(Color.White)),
            )
        } else {
            Text(
                text = style.symbol,
                style = TextStyle(color = FixedColor(Color.White), fontSize = symbolSize, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** これより狭い低い形では、見出しの文字を少し小さくする。 */
private val NARROW_WIDTH = 300.dp

/** 低い形（4×1）：左に状態の印、右に見出し1行と最終チェック。補足の文は出さない。 */
@Composable
private fun LowContent(context: Context, state: WidgetState) {
    val style = styleOf(context, state.status)
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(style.background))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusCircle(style, size = 36.dp, iconSize = 22.dp, symbolSize = 20.sp)
        Spacer(GlanceModifier.width(12.dp))
        // 幅の狭いホーム画面（5列など）では、見出しが1行に入るよう文字を少し小さくする
        val titleSize = if (LocalSize.current.width < NARROW_WIDTH) 15.sp else 16.sp
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = shortTitleOf(context, state),
                style = TextStyle(color = style.titleColor, fontSize = titleSize, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = lastCheckText(context, state.lastCheckAt),
                style = TextStyle(color = WidgetColors.secondary, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

/** 高い形（4×2）：今までの形（見出し・補足の文・最終チェック）。 */
@Composable
private fun TallContent(context: Context, state: WidgetState) {
    val style = styleOf(context, state.status)
    val secondary = WidgetColors.secondary
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(style.background))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatusCircle(style, size = 40.dp, iconSize = 24.dp, symbolSize = 22.sp)
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = titleOf(context, state),
                    style = TextStyle(color = style.titleColor, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                )
                noteOf(context, state.status)?.let { note ->
                    Text(text = note, style = TextStyle(color = secondary, fontSize = 12.sp), maxLines = 3)
                }
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = lastCheckText(context, state.lastCheckAt),
            style = TextStyle(color = secondary, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}
