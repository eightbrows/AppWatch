package io.github.eightbrows.appwatch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// フォントは全画面で共通（端末の標準フォント）。大人向けに、本文はやや大きめにしている。
private val Base = TextStyle(fontFamily = FontFamily.Default)

val Typography = Typography(
    headlineMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp),
    bodyLarge = Base.copy(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = Base.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = Base.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Base.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    labelMedium = Base.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = Base.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
)
