package org.openturnly.mobile.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.openturnly.mobile.Brand
import org.openturnly.mobile.R
import org.openturnly.mobile.localization.Copy

/**
 * What the app shows while it restores the session, and the sign-in header
 * while it signs in: the brand mark on its tile, with the brand's name under it.
 *
 * When the brand's logo is the example's clock ([Brand.animatedMark]), the mark
 * is drawn here and its hands turn: the long one once every 1.6 s, the short one
 * twelve times slower, while the square dot breathes. Otherwise the brand's own
 * logo pulses gently. With the phone's animations off it holds still. The iPhone
 * app draws the same mark with the same timings and proportions.
 */
@Composable
fun ClockLoader(
    modifier: Modifier = Modifier,
    /** Off: the mark at rest (the sign-in header when nothing is happening). */
    animating: Boolean = true,
    showsName: Boolean = true,
    size: Dp = 88.dp,
) {
    val moving = animating && ValueAnimator.areAnimatorsEnabled()
    val elapsed = rememberElapsedNanos(moving)
    val description = if (animating) "${Brand.name}, ${Copy.common.loading}" else Brand.name
    // The name hangs under the tile without moving it, so a centred loader keeps
    // the tile where the launch screen drew it.
    Layout(
        content = {
            Tile(elapsed, size)
            if (showsName) {
                Text(Brand.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = LocalExtended.current.muted, maxLines = 1)
            }
        },
        modifier = modifier.semantics { contentDescription = description },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tile = measurables[0].measure(loose)
        val name = measurables.getOrNull(1)?.measure(loose.copy(maxHeight = Int.MAX_VALUE))
        val width = maxOf(tile.width, name?.width ?: 0)
        layout(width, tile.height) {
            tile.placeRelative((width - tile.width) / 2, 0)
            name?.placeRelative((width - name.width) / 2, tile.height + 16.dp.roundToPx())
        }
    }
}

/** The tile: the drawn clock, or the brand's own logo, at [size]. */
@Composable
private fun Tile(elapsed: Long, size: Dp) {
    val brand = Palette.brand
    val shape = RoundedCornerShape(size * 0.23f)
    val shadow = Modifier.shadow(size * 0.12f, shape, ambientColor = brand, spotColor = brand)
    if (Brand.animatedMark) {
        Box(
            shadow.size(size).clip(shape).background(Brush.linearGradient(listOf(ClockMark.gradientStart, brand))),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size * 0.6f)) { ClockMark.draw(this, elapsed) }
        }
    } else {
        val scale = ClockMark.pulse(elapsed)
        Image(
            painterResource(R.drawable.brand_logo), null,
            shadow.graphicsLayer { scaleX = scale; scaleY = scale }.size(size).clip(shape),
        )
    }
}

/** Nanoseconds since the animation started, ticking every frame while [running]; 0 at rest. */
@Composable
private fun rememberElapsedNanos(running: Boolean): Long {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        elapsed = 0L
        if (!running) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { elapsed = it - start }
    }
    return elapsed
}

/**
 * The clock mark of `docs/brand/openturnly-mark.svg`, drawn in its 880 × 880
 * box: an open circle, an "L" of two hands and a rounded square dot.
 */
object ClockMark {
    /** One turn of the long hand, in seconds. */
    const val MINUTE_TURN = 1.6
    /** One turn of the short hand: twelve times slower. */
    const val HOUR_TURN = MINUTE_TURN * 12
    /** One breath of the dot: opacity 1 → 0.55 → 1. */
    const val DOT_BREATH = 1.6
    /** One pulse of a logo that isn't the clock: scale 1 → 1.04 → 1. */
    const val PULSE = 1.2

    /** The tile's gradient starts at this teal and ends at the brand colour. */
    val gradientStart = Color(0xFF14B8A6)

    private const val BOX = 880f
    private val pivot = Offset(441f, 469f)
    private const val MINUTE_LENGTH = 187.0
    private const val HOUR_LENGTH = 132.0

    private fun phase(nanos: Long, period: Double): Double {
        val periodNanos = (period * 1e9).toLong()
        return (nanos % periodNanos).toDouble() / periodNanos
    }

    /** The dot's opacity at a moment. */
    fun dotAlpha(nanos: Long): Float = (1 - 0.225 * (1 - cos(2 * PI * phase(nanos, DOT_BREATH)))).toFloat()

    /** A plain logo's scale at a moment. */
    fun pulse(nanos: Long): Float = (1 + 0.02 * (1 - cos(2 * PI * phase(nanos, PULSE)))).toFloat()

    /** The long hand's angle in radians, screen coordinates: straight up at rest. */
    fun minuteAngle(nanos: Long): Double = -PI / 2 + 2 * PI * phase(nanos, MINUTE_TURN)

    /** The short hand's angle: pointing right at rest. */
    fun hourAngle(nanos: Long): Double = 2 * PI * phase(nanos, HOUR_TURN)

    private fun end(angle: Double, length: Double) =
        Offset((pivot.x + length * cos(angle)).toFloat(), (pivot.y + length * sin(angle)).toFloat())

    fun draw(scope: DrawScope, nanos: Long, color: Color = Color.White) = with(scope) {
        val factor = minOf(size.width, size.height) / BOX
        translate((size.width - BOX * factor) / 2, (size.height - BOX * factor) / 2) {
            scale(factor, factor, pivot = Offset.Zero) {
                val stroke = Stroke(width = 96f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                // The circle, open at the top right: from (528.5, 81) round the left and the
                // bottom to (805.2, 387.2), radius 370 around (441, 441).
                val from = Math.toDegrees(kotlin.math.atan2(81.0 - 441, 528.5 - 441)).toFloat()
                val to = Math.toDegrees(kotlin.math.atan2(387.2 - 441, 805.2 - 441)).toFloat()
                val ring = Path().apply { arcTo(Rect(Offset(441f, 441f), 370f), from, -(from - (to - 360f)), true) }
                drawPath(ring, color, style = stroke)
                val hands = Path().apply {
                    val minute = end(minuteAngle(nanos), MINUTE_LENGTH)
                    val hour = end(hourAngle(nanos), HOUR_LENGTH)
                    moveTo(minute.x, minute.y)
                    lineTo(pivot.x, pivot.y)
                    lineTo(hour.x, hour.y)
                }
                drawPath(hands, color, style = stroke)
                drawRoundRect(color.copy(alpha = color.alpha * dotAlpha(nanos)), Offset(652f, 124f), Size(151f, 152f), CornerRadius(44f, 44f))
            }
        }
    }
}
