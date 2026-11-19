package com.horizon.game.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.horizon.game.ui.theme.HorizonBlue
import com.horizon.game.ui.theme.HorizonOnSurface
import com.horizon.game.ui.theme.HorizonRed

/** Deux teintes stables dérivées d'un identifiant, pour les visuels de remplacement. */
fun tileColors(seed: String): Pair<Color, Color> {
    val hue = ((seed.hashCode().toLong() and 0x7fffffffL) % 360L).toFloat()
    return Color.hsv(hue, 0.60f, 0.55f) to Color.hsv((hue + 40f) % 360f, 0.70f, 0.22f)
}

/** Visuel de remplacement : aucun visuel sous licence tant que le Store n'en fournit pas. */
@Composable
fun GameCover(
    title: String,
    seed: String,
    modifier: Modifier = Modifier,
    symbol: ImageVector? = null
) {
    val colors = tileColors(seed)
    Box(
        modifier.background(Brush.linearGradient(listOf(colors.first, colors.second))),
        contentAlignment = Alignment.Center
    ) {
        if (symbol != null) {
            Icon(symbol, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(40.dp))
        } else {
            Text(
                title.take(1).uppercase(),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Zone cliquable avec anneau de focus et léger agrandissement : navigation manette / clavier. */
fun Modifier.osClickable(
    shape: Shape = RoundedCornerShape(16.dp),
    onClick: () -> Unit
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(150),
        label = "osScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .then(if (focused) Modifier.border(2.dp, HorizonBlue, shape) else Modifier)
        .onFocusChanged { focused = it.isFocused }
        .clickable(onClick = onClick)
}

/** Tuile de jeu : visuel, titre, badge d'état et favori. */
@Composable
fun GameTile(
    title: String,
    seed: String,
    modifier: Modifier = Modifier,
    symbol: ImageVector? = null,
    badge: String? = null,
    badgeColor: Color = HorizonBlue,
    favorite: Boolean = false,
    highlighted: Boolean = false,
    reduce: Boolean = false,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val active = focused || highlighted
    val scale by animateFloatAsState(
        targetValue = if (active) 1.07f else 1f,
        animationSpec = tween(if (reduce) 0 else 160),
        label = "tileScale"
    )
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .aspectRatio(0.78f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(if (active) Modifier.border(2.dp, HorizonBlue, shape) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus?.invoke()
            }
            .clickable(onClick = onClick)
    ) {
        GameCover(title, seed, Modifier.fillMaxSize(), symbol)
        if (badge != null) {
            Text(
                badge,
                color = badgeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            )
        }
        if (favorite) {
            Text("★", color = HorizonBlue, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                .padding(10.dp)
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** En-tête commun des écrans secondaires : retour, titre, actions. */
@Composable
fun OsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = HorizonOnSurface)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = HorizonOnSurface)
            Spacer(Modifier.weight(1f))
            actions()
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

/** Ligne de réglage avec interrupteur, entièrement focalisable. */
@Composable
fun OsSwitchRow(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .osClickable(RoundedCornerShape(12.dp)) { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = HorizonOnSurface)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Pastille de statut colorée (installé, bientôt...). */
@Composable
fun StatusPill(text: String, color: Color = HorizonRed, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}
