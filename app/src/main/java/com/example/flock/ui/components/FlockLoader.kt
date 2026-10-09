package com.example.flock.ui.components

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's mark — the leaning F rooster of the launcher icon — as six separate parts in a 200-unit
 * drawing space (the same numbers as design/make_app_icon.py): stem, top arm, middle arm, comb, beak, eye.
 */
private object Mark {
    const val LEAN = 0.21256f                   // tan(12°): the whole mark leans forward
    const val SHIFT = -4f                       // the lean pushes it right of centre; bring it back
    const val CENTRE_X = 102.5f; const val CENTRE_Y = 101f; const val SPAN = 132f   // what the canvas shows
    val White = Color(0xFFF2F2F0); val Red = Color(0xFFE5534B); val BeakColor = Color(0xFFF0A23A)
    val beak = Path().apply { moveTo(60f, 70f); lineTo(42f, 77f); lineTo(60f, 85f); close() }

    /** When a part is put in place (share of the cycle) and where it comes in from (drawing units). */
    class Step(val from: Float, val to: Float, val dx: Float, val dy: Float)
    val stem = Step(0.00f, 0.17f, 0f, 30f)       // rises from below
    val topArm = Step(0.14f, 0.31f, 34f, 0f)     // slides in from the right
    val midArm = Step(0.27f, 0.44f, 30f, 0f)
    val comb = Step(0.41f, 0.56f, 0f, -24f)      // set down on top
    val beakStep = Step(0.53f, 0.66f, -24f, 0f)  // from the left
    val eye = Step(0.64f, 0.74f, 0f, 0f)         // last, where it belongs
    const val BUILT = 0.74f                      // everything is in place from here
    const val FADE_FROM = 0.90f                  // … held, then it clears and starts again
}

/**
 * The mark at [progress] through being put together (0 = nothing yet, [Mark.BUILT] and above = whole).
 * Each part is moved into place whole — nothing is cut up, stretched or redrawn.
 */
private fun DrawScope.drawMark(progress: Float, alpha: Float) {
    val u = size.minDimension / Mark.SPAN
    val lean = Matrix().apply { values[Matrix.SkewX] = -Mark.LEAN; values[Matrix.TranslateX] = 100f * Mark.LEAN + 2f + Mark.SHIFT }
    fun part(s: Mark.Step, draw: DrawScope.(a: Float) -> Unit) {
        val t = ((progress - s.from) / (s.to - s.from)).coerceIn(0f, 1f)
        if (t <= 0f) return
        val e = 1f - (1f - t) * (1f - t) * (1f - t)             // arrives quickly, settles gently
        withTransform({
            translate(size.width / 2 - Mark.CENTRE_X * u, size.height / 2 - Mark.CENTRE_Y * u)
            scale(u, u, pivot = Offset.Zero)
            transform(lean)
            translate(s.dx * (1f - e), s.dy * (1f - e))
        }) { draw((t * 2.5f).coerceAtMost(1f) * alpha) }
    }
    val r = CornerRadius(8f, 8f)
    part(Mark.comb) { a ->
        drawCircle(Mark.Red.copy(alpha = a), 6.15f, Offset(63.16f, 54.1f))
        drawCircle(Mark.Red.copy(alpha = a), 7.38f, Offset(73f, 50f))
        drawCircle(Mark.Red.copy(alpha = a), 6.15f, Offset(82.84f, 54.1f))
    }
    part(Mark.stem) { a -> drawRoundRect(Mark.White.copy(alpha = a), Offset(60f, 56f), Size(26f, 104f), r) }
    part(Mark.topArm) { a -> drawRoundRect(Mark.White.copy(alpha = a), Offset(94f, 60f), Size(60f, 24f), r) }
    part(Mark.midArm) { a -> drawRoundRect(Mark.White.copy(alpha = a), Offset(94f, 100f), Size(42f, 24f), r) }
    part(Mark.beakStep) { a -> drawPath(Mark.beak, Mark.BeakColor.copy(alpha = a)) }
    part(Mark.eye) { a -> drawCircle(Color.Black.copy(alpha = a), 3.8f, Offset(72f, 74f)) }
}

/** The mark, [progress] of the way through being put together; 1 = whole. Drawn on nothing (no box behind it). */
@Composable
fun FlockMark(progress: Float, modifier: Modifier = Modifier, size: Dp = 56.dp, alpha: Float = 1f) {
    Canvas(modifier.size(size)) { drawMark(progress.coerceIn(0f, 1f) * Mark.BUILT + if (progress >= 1f) 0.01f else 0f, alpha) }
}

/**
 * The app's loading animation: the mark is put together part by part — stem, top arm, middle arm, comb,
 * beak, eye — held for a moment, then cleared and built again.
 */
@Composable
fun FlockLoader(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val t = rememberInfiniteTransition(label = "loader")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart), label = "build")
    Canvas(modifier.size(size).testTag("flockLoader")) {
        val fade = if (p > Mark.FADE_FROM) 1f - (p - Mark.FADE_FROM) / (1f - Mark.FADE_FROM) else 1f
        drawMark(p, fade)
    }
}

/**
 * Pull-to-refresh indicator, in the middle of the screen: pulling down puts the mark together (the further
 * the pull, the more of it is in place); while refreshing it builds over and over. Behind it only a soft
 * dark glow that fades out to nothing, so the mark reads over whatever is on the page — no box, no edge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.FlockPullIndicator(state: PullToRefreshState, refreshing: Boolean) {
    val f = if (refreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
    if (f <= 0.01f) return
    Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(190.dp)) {
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.92f * f), Color.Black.copy(alpha = 0.75f * f), Color.Transparent)), size.minDimension / 2)
        }
        if (refreshing) FlockLoader(size = 68.dp) else FlockMark(progress = f, size = 68.dp)
    }
}

/**
 * The app opening: the mark alone in the middle of a black screen, put together once and held.
 */
@Composable
fun FlockOpening(modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val p = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { p.animateTo(1f, tween(1250, easing = LinearEasing)) }
    FlockMark(progress = p.value, modifier = modifier.testTag("flockOpening"), size = size)
}

/** The mark and the name, side by side — for the sign-in screen and the Farms header. */
@Composable
fun FlockWordmark(modifier: Modifier = Modifier, markSize: Dp = 44.dp, textStyle: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.headlineMedium) {
    // the mark is the F of the name (no second F after it): its bar stands on the text's baseline, about as tall as the capitals
    androidx.compose.foundation.layout.Row(modifier.semantics(mergeDescendants = true) { contentDescription = "FlockIt" },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(-(markSize * 0.19f))) {
        FlockMark(progress = 1f, modifier = Modifier.alignBy { (it.measuredHeight * 0.80f).toInt() }, size = markSize)
        androidx.compose.material3.Text("lockIt", style = textStyle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = Color(0xFFF2F2F0), maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline().clearAndSetSemantics { })
    }
}
