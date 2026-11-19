package com.horizon.game.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.horizon.game.ui.theme.HorizonBackground
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonRed
import kotlin.math.PI
import kotlin.math.sin

/** Durée d'une boucle complète : toutes les vitesses sont des multiples entiers, donc la boucle est invisible. */
private const val LOOP_MS = 90_000

private class Wave(val baseY: Float, val amp: Float, val length: Float, val cycles: Int, val phase: Float, val alpha: Float, val warm: Float)

private class Spark(val x: Float, val y: Float, val cycles: Int, val twinkle: Int, val phase: Float, val big: Boolean)

private val waves = listOf(
    Wave(0.62f, 0.060f, 1.6f, 1, 0.0f, 0.20f, 0.0f),
    Wave(0.70f, 0.070f, 1.1f, 2, 1.7f, 0.17f, 0.5f),
    Wave(0.78f, 0.050f, 0.8f, 1, 3.1f, 0.15f, 1.0f),
    Wave(0.86f, 0.045f, 1.3f, 3, 4.6f, 0.13f, 0.2f)
)

private val sparks = List(16) { i ->
    Spark(
        x = ((i * 0.381966f) % 1f),
        y = 0.08f + ((i * 0.618034f) % 1f) * 0.84f,
        cycles = 1 + i % 3,
        twinkle = 2 + i % 4,
        phase = (i * 0.37f) % 1f,
        big = i % 5 == 0
    )
}

/**
 * Fond animé commun à tous les écrans : vagues lumineuses qui ondulent et petits « HORIZON GAME »
 * qui flottent. Les couleurs suivent [accent] (jeu sélectionné). Figé si [reduce] est vrai.
 */
@Composable
fun HorizonBackdrop(accent: Color?, reduce: Boolean, modifier: Modifier = Modifier) {
    val target = accent ?: HorizonBlue
    val tint by animateColorAsState(target, tween(700), label = "backdropTint")
    if (reduce) {
        BackdropCanvas(tint, { 0.35f }, modifier)
    } else {
        val transition = rememberInfiniteTransition(label = "backdrop")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "t"
        )
        BackdropCanvas(tint, { t }, modifier)
    }
}

@Composable
private fun BackdropCanvas(tint: Color, time: () -> Float, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val small = remember(measurer) {
        measurer.measure("HORIZON GAME", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp))
    }
    val large = remember(measurer) {
        measurer.measure("HORIZON GAME", TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
    }
    val cool = lerp(HorizonBlue, tint, 0.65f)
    val warm = lerp(HorizonRed, tint, 0.35f)

    Canvas(modifier.fillMaxSize()) {
        val t = time()
        val w = size.width
        val h = size.height

        // Ciel : dégradé sombre teinté par le jeu sélectionné.
        drawRect(
            Brush.verticalGradient(
                listOf(lerp(HorizonBackground, cool, 0.20f), HorizonBackground, lerp(HorizonBackground, cool, 0.10f))
            )
        )
        // Halo diffus en haut à gauche.
        drawCircle(
            Brush.radialGradient(listOf(cool.copy(alpha = 0.22f), Color.Transparent), center = Offset(w * 0.2f, h * 0.1f), radius = w * 0.55f),
            radius = w * 0.55f,
            center = Offset(w * 0.2f, h * 0.1f)
        )

        // Vagues lumineuses.
        val step = 28f
        for (wave in waves) {
            val color = lerp(cool, warm, wave.warm)
            val path = Path()
            var x = 0f
            val baseY = h * wave.baseY
            val amp = h * wave.amp
            val shift = (2.0 * PI * (t * wave.cycles + wave.phase / (2f * PI.toFloat()))).toFloat()
            path.moveTo(0f, h)
            while (x <= w + step) {
                val a = (x / w) * wave.length * 2f * PI.toFloat()
                val y = baseY + sin(a + shift) * amp + sin(a * 0.5f - shift * 0.7f) * amp * 0.5f
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(w + step, h)
            path.close()
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = wave.alpha), color.copy(alpha = 0.02f)),
                    startY = baseY - amp,
                    endY = h
                )
            )
        }

        // Petits « HORIZON GAME » lumineux qui dérivent lentement.
        for (s in sparks) {
            val layout = if (s.big) large else small
            val frac = (s.x + t * s.cycles) % 1f
            val px = frac * (w + layout.size.width) - layout.size.width
            val py = s.y * h + sin(2.0 * PI * (t * s.cycles + s.phase)).toFloat() * h * 0.025f
            val pulse = 0.5f + 0.5f * sin(2.0 * PI * (t * s.twinkle + s.phase)).toFloat()
            val alpha = (if (s.big) 0.22f else 0.14f) + 0.20f * pulse
            drawText(
                textLayoutResult = layout,
                color = Color.White,
                topLeft = Offset(px, py),
                alpha = alpha,
                shadow = Shadow(color = cool.copy(alpha = 0.9f), offset = Offset.Zero, blurRadius = if (s.big) 18f else 12f)
            )
        }
    }
}
