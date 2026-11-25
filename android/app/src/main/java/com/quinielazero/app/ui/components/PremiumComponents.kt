package com.quinielazero.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quinielazero.app.core.model.EstadoVerificacion
import com.quinielazero.app.core.model.PuntoEvolucion
import com.quinielazero.app.ui.theme.AlertRed
import com.quinielazero.app.ui.theme.AmberWarm
import com.quinielazero.app.ui.theme.CyanNeon
import com.quinielazero.app.ui.theme.GoldBright
import com.quinielazero.app.ui.theme.JetBrainsMonoFamily
import com.quinielazero.app.ui.theme.PendingAmber
import com.quinielazero.app.ui.theme.VerifiedGreen
import com.quinielazero.app.ui.theme.VioletNeon
import java.util.Locale

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    highlightBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = if (highlightBorder) {
        Brush.linearGradient(listOf(GoldBright.copy(alpha = 0.78f), CyanNeon.copy(alpha = 0.55f)))
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.outline.copy(alpha = 0.78f),
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, borderBrush),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun GoldNumberPill(
    number: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(70.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(listOf(GoldBright, AmberWarm)))
            .semantics { contentDescription = "Número destacado $number" },
        contentAlignment = Alignment.Center
    ) {
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
fun UniformPlusSlot(
    number: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val filled = number != null
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (filled) {
                    Modifier.background(Brush.linearGradient(listOf(GoldBright, AmberWarm)))
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
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
            fontSize = 21.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = if (filled) Color(0xFF141100) else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    val bgColor = when {
        selected -> GoldBright
        isSuggested -> CyanNeon.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }
    val borderColor = when {
        selected -> AmberWarm
        isSuggested -> CyanNeon.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Seleccionar número $number" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            color = when {
                selected -> Color(0xFF141100)
                isSuggested -> CyanNeon
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun QuantScienceSummaryCard(
    walkForwardPct: Double,
    entropyBits: Double,
    chiSqTerminations: Double,
    totalSorteos: Int
) {
    GlassCard {
        Text("Laboratorio Matemático · Variables Activas", style = MaterialTheme.typography.titleMedium, color = CyanNeon)
        Spacer(Modifier.height(6.dp))
        Text(
            "Ensamble multi-factor: Z-Score Binomial + EWMA (λ=0.08/0.012) + Supervivencia Weibull + Markov Lift + Cruce Prov↔Ciu + Balance Chi².",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(
                title = "Backtest 30d",
                value = String.format(Locale.US, "%.1f%%", walkForwardPct),
                accent = VerifiedGreen,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Entropía H",
                value = String.format(Locale.US, "%.2f b", entropyBits),
                accent = GoldBright,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Chi² Term.",
                value = String.format(Locale.US, "%.1f", chiSqTerminations),
                accent = CyanNeon,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "Muestra",
                value = "$totalSorteos",
                accent = AmberWarm,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = accent,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun VerificationBadge(estado: EstadoVerificacion) {
    val (color, text) = when (estado) {
        EstadoVerificacion.VERIFICADO -> VerifiedGreen to "VERIFICADO"
        EstadoVerificacion.NO_VERIFICADO -> PendingAmber to "NO VERIFICADO"
        EstadoVerificacion.PENDIENTE -> AlertRed to "PENDIENTE"
        EstadoVerificacion.HISTORIAL -> CyanNeon to "HISTORIAL"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
    val accentColor = CyanNeon
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

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
                brush = Brush.horizontalGradient(listOf(VioletNeon, accentColor, lineColor)),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            coords.forEach { pt ->
                drawCircle(color = lineColor, radius = 6f, center = pt)
                drawCircle(color = Color(0xFF0A0A0F), radius = 2.5f, center = pt)
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Text(
            text = "Las probabilidades mostradas son estimaciones estadísticas. Jugar implica riesgo. Jugá responsablemente (+18). Ayuda LOTBA: 0800-666-6006 · PBA: 0800-444-4000.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}
