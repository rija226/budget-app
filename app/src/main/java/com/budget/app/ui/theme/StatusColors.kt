package com.budget.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extended semantic colors. "Over limit" uses MaterialTheme.colorScheme.error / errorContainer. */
@Immutable
data class StatusColors(
    val ok: Color,
    val onOk: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val LightStatus = StatusColors(
    ok = Color(0xFF2E6A44),
    onOk = Color(0xFFFFFFFF),
    okContainer = Color(0xFFC8EECF),
    onOkContainer = Color(0xFF0F5130),
    warning = Color(0xFF845F00),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDF9F),
    onWarningContainer = Color(0xFF5F4400),
)

val DarkStatus = StatusColors(
    ok = Color(0xFF93D5A6),
    onOk = Color(0xFF00391D),
    okContainer = Color(0xFF15522F),
    onOkContainer = Color(0xFFAFF2C1),
    warning = Color(0xFFF4BF4F),
    onWarning = Color(0xFF412D00),
    warningContainer = Color(0xFF5F4400),
    onWarningContainer = Color(0xFFFFDF9F),
)

val LocalStatus = staticCompositionLocalOf { LightStatus }

enum class LimitStatus { Ok, Warning, Over, NoLimit }

/** Amounts in cents. Ok < 80%, Warning 80-99%, Over >= 100% (exactly 100% is labeled "Limit dostignut"). */
fun limitStatus(spentCents: Long, limitCents: Long?): LimitStatus = when {
    limitCents == null -> LimitStatus.NoLimit
    spentCents >= limitCents -> LimitStatus.Over
    spentCents * 100 >= limitCents * 80 -> LimitStatus.Warning
    else -> LimitStatus.Ok
}
