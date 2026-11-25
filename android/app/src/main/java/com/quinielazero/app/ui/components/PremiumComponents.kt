package com.quinielazero.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quinielazero.app.core.model.EstadoVerificacion
import com.quinielazero.app.core.model.PuntoEvolucion
import com.quinielazero.app.ui.theme.AlertRed
import com.quinielazero.app.ui.theme.AmberWarm
import com.quinielazero.app.ui.theme.EmeraldDeep
import com.quinielazero.app.ui.theme.EmeraldNeon
import com.quinielazero.app.ui.theme.GoldBright
import com.quinielazero.app.ui.theme.GoldLightSpecular
import com.quinielazero.app.ui.theme.GoldRichDeep
import com.quinielazero.app.ui.theme.HotCoral
import com.quinielazero.app.ui.theme.JetBrainsMonoFamily
import com.quinielazero.app.ui.theme.PendingAmber
import com.quinielazero.app.ui.theme.VerifiedGreen

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlightBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (highlightBorder) {
        Brush.linearGradient(listOf(GoldBright.copy(alpha = 0.88f), EmeraldNeon.copy(alpha = 0.72f)))
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.90f),
                GoldBright.copy(alpha = 0.28f)
            )
        )
    }

    val onSurf = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        contentColor = onSurf,
        border = BorderStroke(1.2.dp, borderBrush),
        tonalElevation = 6.dp
    ) {
        CompositionLocalProvider(LocalContentColor provides onSurf) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun LuckyBannerCard(
    @DrawableRes drawableRes: Int,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    height: Dp = 132.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF051810),
        contentColor = Color.White,
        border = BorderStroke(
            1.3.dp,
            Brush.linearGradient(listOf(GoldBright.copy(alpha = 0.85f), EmeraldNeon.copy(alpha = 0.7f)))
        ),
        tonalElevation = 6.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF03110B).copy(alpha = 0.90f),
                                Color(0xFF04160E).copy(alpha = 0.56f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.62f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (badgeText != null) {
                    Surface(
                        shape = CircleShape,
                        color = GoldBright.copy(alpha = 0.22f),
                        contentColor = GoldBright,
                        border = BorderStroke(1.dp, GoldBright.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFD4ECE0),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun GoldNumberPill(
    number: String,
    size: Dp = 60.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(GoldLightSpecular, GoldBright, GoldRichDeep),
                    center = Offset(30f, 24f),
                    radius = 120f
                )
            )
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(GoldLightSpecular, AmberWarm)),
                shape = CircleShape
            )
            .semantics { contentDescription = "Número destacado $number" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .border(1.dp, Color(0xFF704200).copy(alpha = 0.35f), CircleShape)
        )
        Text(
            text = number,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = Color(0xFF141100)
        )
    }
}

@Composable
fun TempNumberBadge(
    number: String,
    subLabel: String,
    isHot: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isHot) HotCoral else MaterialTheme.colorScheme.secondary
    val orbBrush = if (isHot) {
        Brush.radialGradient(
            colors = listOf(Color(0xFFFFB199), HotCoral, Color(0xFF9E2B0E)),
            center = Offset(24f, 20f),
            radius = 90f
        )
    } else {
        Brush.radialGradient(
            colors = listOf(Color(0xFFA7FFE0), EmeraldNeon, Color(0xFF0A6641)),
            center = Offset(24f, 20f),
            radius = 90f
        )
    }
    Column(
        modifier = modifier.padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(orbBrush)
                .border(1.5.dp, accent.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color(0xFF0A120E),
                maxLines = 1,
                softWrap = false
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = subLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun UniformPlusSlot(
    number: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val filled = number != null
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .then(
                    if (filled) {
                        Modifier
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(GoldLightSpecular, GoldBright, GoldRichDeep),
                                    center = Offset(26f, 22f),
                                    radius = 110f
                                )
                            )
                            .border(2.dp, GoldLightSpecular, CircleShape)
                    } else {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .border(1.4.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.85f), CircleShape)
                    }
                )
                .clickable(enabled = filled, onClick = onClick)
                .semantics { contentDescription = if (filled) "Quiniela Plus número $number" else "Casillero vacío" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number ?: "--",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                color = if (filled) Color(0xFF141100) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun UniformSelectorCell(
    number: String,
    selected: Boolean,
    isSuggested: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = when {
        selected -> GoldLightSpecular
        isSuggested -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .then(
                when {
                    selected -> Modifier.background(
                        Brush.radialGradient(
                            colors = listOf(GoldLightSpecular, GoldBright, GoldRichDeep)
                        )
                    )
                    isSuggested -> Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
                    else -> Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f))
                }
            )
            .border(if (selected || isSuggested) 1.5.dp else 1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Seleccionar número $number" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = when {
                selected -> Color(0xFF141100)
                isSuggested -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun DrawNumberCircle(
    position: Int,
    number: String,
    modifier: Modifier = Modifier
) {
    val isHead = position == 1
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(
                    if (isHead) {
                        Modifier
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(GoldLightSpecular, GoldBright, GoldRichDeep),
                                    center = Offset(24f, 20f),
                                    radius = 100f
                                )
                            )
                            .border(1.8.dp, GoldLightSpecular, CircleShape)
                    } else {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${position}°",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHead) Color(0xFF3E2900) else MaterialTheme.colorScheme.secondary,
                    lineHeight = 10.sp,
                    maxLines = 1
                )
                Text(
                    text = number,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (isHead) Color(0xFF141100) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun TicketBallBadge(
    number: String,
    isHit: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .then(
                if (isHit) {
                    Modifier
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFA7FFE0), EmeraldNeon, Color(0xFF0A6641))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                } else {
                    Modifier
                        .background(
                            Brush.radialGradient(
                                colors = listOf(GoldLightSpecular.copy(alpha = 0.35f), EmeraldDeep)
                            )
                        )
                        .border(1.2.dp, GoldBright.copy(alpha = 0.75f), CircleShape)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (number.length >= 4) 12.sp else 15.sp,
            color = if (isHit) Color(0xFF031A0F) else Color.White,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun VerificationBadge(estado: EstadoVerificacion) {
    val (color, text) = when (estado) {
        EstadoVerificacion.VERIFICADO -> VerifiedGreen to "VERIFICADO"
        EstadoVerificacion.NO_VERIFICADO -> PendingAmber to "NO VERIFICADO"
        EstadoVerificacion.PENDIENTE -> AlertRed to "PENDIENTE"
        EstadoVerificacion.HISTORIAL -> MaterialTheme.colorScheme.secondary to "HISTORIAL"
    }
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EvolutionChart(
    points: List<PuntoEvolucion>,
    leadNumber: String,
    modifier: Modifier = Modifier
) {
    if (points.size < 2) return
    val lineColor = GoldBright
    val accentColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Gráfico de evolución estadística para el número $leadNumber" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Evolución de frecuencia · #$leadNumber",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${points.size} períodos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val maxVal = (points.maxOfOrNull { it.frecuenciaPct } ?: 1.0).coerceAtLeast(1.0)
            val minVal = (points.minOfOrNull { it.frecuenciaPct } ?: 0.0)
            val span = (maxVal - minVal).coerceAtLeast(0.5)

            for (g in 0..2) {
                val y = size.height * (g / 2f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f)
            }

            val coords = points.mapIndexed { idx, pt ->
                val x = (idx.toFloat() / (points.size - 1).coerceAtLeast(1)) * (size.width - 16f) + 8f
                val normY = ((pt.frecuenciaPct - minVal) / span).toFloat().coerceIn(0f, 1f)
                val y = size.height - 12f - normY * (size.height - 24f)
                Offset(x, y)
            }

            val path = Path().apply {
                coords.forEachIndexed { idx, pt ->
                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
            }
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(listOf(EmeraldNeon, accentColor, lineColor)),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            coords.forEach { pt ->
                drawCircle(color = lineColor, radius = 6f, center = pt)
                drawCircle(color = Color(0xFF04100B), radius = 2.5f, center = pt)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.etiquetaPeriodo,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ResponsibleGamingFooter() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Text(
            text = "Las probabilidades mostradas son estimaciones estadísticas. Jugar implica riesgo. Jugá responsablemente (+18). Ayuda LOTBA: 0800-666-6006 · PBA: 0800-444-4000.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}
