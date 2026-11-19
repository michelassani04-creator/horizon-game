package com.horizon.game.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.game.ui.theme.HorizonBackground
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonOnSurfaceMuted
import com.horizon.game.ui.theme.HorizonRed
import com.horizon.game.ui.theme.HorizonSurfaceVariant
import kotlinx.coroutines.delay

private fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun ease(x: Float): Float = FastOutSlowInEasing.transform(x)

/**
 * Splash « lever d'horizon » : la ligne d'horizon s'étire, un halo bleu se lève,
 * puis le logo apparaît avec un espacement de lettres qui se resserre.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Le contrôle de version (F-014) sera branché sur GET /v1/config au Lot 3.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 3200, easing = LinearEasing))
        delay(250)
        onFinished()
    }

    val t = progress.value
    val lineIn = ease(seg(t, 0.00f, 0.30f))
    val sunIn = ease(seg(t, 0.18f, 0.68f))
    val titleIn = ease(seg(t, 0.45f, 0.80f))
    val subIn = ease(seg(t, 0.68f, 0.92f))

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(HorizonBackground)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val horizonY = h * 0.50f

            // Halo qui se lève sur l'horizon
            val radius = minOf(w, h) * 0.60f * sunIn
            if (radius > 1f) {
                val center = Offset(cx, horizonY)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HorizonBlue.copy(alpha = 0.85f * sunIn),
                            HorizonBlue.copy(alpha = 0.25f * sunIn),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            // Le sol masque la moitié basse du halo
            drawRect(
                color = HorizonBackground,
                topLeft = Offset(0f, horizonY),
                size = Size(w, h - horizonY)
            )

            // Ligne d'horizon qui s'étire depuis le centre
            val half = (w / 2f) * lineIn
            if (half > 2f) {
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, HorizonBlue, HorizonRed, HorizonBlue, Color.Transparent),
                        startX = cx - half,
                        endX = cx + half
                    ),
                    start = Offset(cx - half, horizonY),
                    end = Offset(cx + half, horizonY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = maxHeight * 0.54f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HORIZON",
                color = HorizonOnSurface,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (22f - 18f * titleIn).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleIn
                    translationY = (1f - titleIn) * 28f
                }
            )
            Text(
                text = "GAME",
                color = HorizonBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (16f - 8f * subIn).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = subIn
                    translationY = (1f - subIn) * 16f
                }
            )
            Text(
                text = "Jouez au-delà de l'horizon",
                color = HorizonOnSurfaceMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .graphicsLayer { alpha = subIn }
            )
        }

        // Barre de progression fine
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .width(160.dp)
                .height(3.dp)
                .background(HorizonSurfaceVariant, RoundedCornerShape(2.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(t)
                    .height(3.dp)
                    .background(HorizonBlue, RoundedCornerShape(2.dp))
            )
        }
    }
}
