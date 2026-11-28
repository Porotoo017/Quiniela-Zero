package com.quinielazero.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quinielazero.app.R
import com.quinielazero.app.core.plus.EstadoJugada
import com.quinielazero.app.core.plus.ModalidadPlus
import com.quinielazero.app.core.plus.PlusCsvMappingSuggestion
import com.quinielazero.app.core.plus.PlusJugada
import com.quinielazero.app.core.plus.PlusNumeroStats
import com.quinielazero.app.core.plus.PlusPesosMotor
import com.quinielazero.app.core.plus.PlusReglasPremios
import com.quinielazero.app.core.plus.PlusResultadoAnalisis
import com.quinielazero.app.core.plus.PlusSorteo
import com.quinielazero.app.core.plus.VentanaPlus
import com.quinielazero.app.ui.MainUiState
import com.quinielazero.app.ui.QuinielaViewModel
import com.quinielazero.app.ui.components.GlassCard
import com.quinielazero.app.ui.components.GoldNumberPill
import com.quinielazero.app.ui.components.LuckyBannerCard
import com.quinielazero.app.ui.components.ResponsibleGamingFooter
import com.quinielazero.app.ui.components.TempNumberBadge
import com.quinielazero.app.ui.components.UniformPlusSlot
import com.quinielazero.app.ui.components.UniformSelectorCell
import com.quinielazero.app.ui.theme.AlertRed
import com.quinielazero.app.ui.theme.EmeraldNeon
import com.quinielazero.app.ui.theme.GoldBright
import com.quinielazero.app.ui.theme.HotCoral
import com.quinielazero.app.ui.theme.NumericCellStyle
import com.quinielazero.app.ui.theme.NumericDisplayStyle
import com.quinielazero.app.ui.theme.PendingAmber
import com.quinielazero.app.ui.theme.VerifiedGreen
import java.util.Locale
import kotlin.math.roundToInt

private enum class PlusSubTab(val label: String) {
    CONTROL_JUGADAS("Control de Jugadas"),
    HISTORIAL_SORTEOS("Historial de Sorteos"),
    ANALISIS_PREDICTIVO("Análisis Predictivo")
}

private enum class JugadaEstadoFiltro(val label: String) {
    TODAS("Todas"),
    GANO("Ganó"),
    NO_GANO("No ganó"),
    PENDIENTE("Pendiente")
}

@Composable
fun QuinielaPlusModuleScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel
) {
    LaunchedEffect(Unit) {
        viewModel.onQuinielaPlusOpened()
    }

    var activeSubTab by remember { mutableStateOf(PlusSubTab.CONTROL_JUGADAS) }
    val plusState = state.plusModule

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra interna de submódulos exclusiva de Quiniela Plus
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlusSubTab.entries.forEach { tab ->
                val countHint = when (tab) {
                    PlusSubTab.CONTROL_JUGADAS -> " (${plusState.jugadasEvaluadas.size})"
                    PlusSubTab.HISTORIAL_SORTEOS -> " (${plusState.historial.size})"
                    PlusSubTab.ANALISIS_PREDICTIVO -> ""
                }
                FilterChip(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    label = { Text("${tab.label}$countHint") },
                    leadingIcon = {
                        Icon(
                            imageVector = when (tab) {
                                PlusSubTab.CONTROL_JUGADAS -> Icons.Outlined.CheckCircle
                                PlusSubTab.HISTORIAL_SORTEOS -> Icons.Outlined.History
                                PlusSubTab.ANALISIS_PREDICTIVO -> Icons.Outlined.Analytics
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = plusChipColors(),
                    modifier = Modifier.heightIn(min = 42.dp)
                )
            }
        }

        when (activeSubTab) {
            PlusSubTab.CONTROL_JUGADAS -> ControlDeJugadasSection(
                state = state,
                viewModel = viewModel,
                onOpenHistorial = { activeSubTab = PlusSubTab.HISTORIAL_SORTEOS },
                onOpenAnalisis = { activeSubTab = PlusSubTab.ANALISIS_PREDICTIVO }
            )
            PlusSubTab.HISTORIAL_SORTEOS -> HistorialDeSorteosSection(
                state = state,
                viewModel = viewModel
            )
            PlusSubTab.ANALISIS_PREDICTIVO -> AnalisisPredictivoPlusSection(
                state = state,
                viewModel = viewModel,
                onGoToControl = { activeSubTab = PlusSubTab.CONTROL_JUGADAS },
                onGoToHistorial = { activeSubTab = PlusSubTab.HISTORIAL_SORTEOS }
            )
        }
    }

    // Diálogo único de confirmación de mapeo si un CSV externo tiene columnas ambiguas
    plusState.pendingCsvMapping?.let { suggestion ->
        PlusCsvMappingDialog(
            suggestion = suggestion,
            onConfirm = { dateCol, numCols, packedCol ->
                viewModel.confirmPlusCsvMapping(dateCol, numCols, packedCol)
            },
            onDismiss = { viewModel.dismissPlusCsvMapping() }
        )
    }
}

// =============================================================================
// SUBMÓDULO 1: CONTROL DE JUGADAS
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlDeJugadasSection(
    state: MainUiState,
    viewModel: QuinielaViewModel,
    onOpenHistorial: () -> Unit,
    onOpenAnalisis: () -> Unit
) {
    val plus = state.plusModule
    val resumen = plus.resumenJugadas
    val editing = plus.editingJugada

    var fechaTxt by remember(plus.defaultFechaIso) { mutableStateOf(plus.defaultFechaIso.ifBlank { "2026-11-24" }) }
    var modalidad by remember { mutableStateOf(ModalidadPlus.QUINIELA_PLUS) }
    var montoTxt by remember { mutableStateOf("1000") }
    var notasTxt by remember { mutableStateOf("") }
    var manualNumsTxt by remember { mutableStateOf("") }
    var showGridSelector by remember { mutableStateOf(true) }
    var seed by remember { mutableStateOf(0) }

    var filtroEstado by remember { mutableStateOf(JugadaEstadoFiltro.TODAS) }
    var filtroFechaTxt by remember { mutableStateOf("") }

    var showOfficialDrawDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }

    // Sincronizar campos al entrar en modo edición
    LaunchedEffect(editing?.id) {
        if (editing != null) {
            fechaTxt = editing.fechaIso
            modalidad = editing.modalidad
            montoTxt = editing.montoApostado.toLong().toString()
            notasTxt = editing.notas
            manualNumsTxt = editing.numeros.joinToString(" ")
        }
    }

    val filteredJugadas = remember(plus.jugadasEvaluadas, filtroEstado, filtroFechaTxt) {
        plus.jugadasEvaluadas.filter { j ->
            val okEstado = when (filtroEstado) {
                JugadaEstadoFiltro.TODAS -> true
                JugadaEstadoFiltro.GANO -> j.estado == EstadoJugada.GANO
                JugadaEstadoFiltro.NO_GANO -> j.estado == EstadoJugada.NO_GANO
                JugadaEstadoFiltro.PENDIENTE -> j.estado == EstadoJugada.PENDIENTE
            }
            val q = filtroFechaTxt.trim()
            val okFecha = q.isEmpty() || j.fechaIso.contains(q, ignoreCase = true) ||
                j.numeros.any { it.contains(q) }
            okEstado && okFecha
        }
    }

    val suggestedSet = remember(plus.analisis) {
        plus.analisis?.rankingCandidatos?.take(12)?.map { it.numero }?.toSet().orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LuckyBannerCard(
                drawableRes = R.drawable.banner_lucky_leprechaun,
                title = "Quiniela Plus · Control de Jugadas",
                subtitle = "Base real: ${plus.historial.size} sorteos · Comparación automática contra resultados oficiales",
                badgeText = "CONTROL",
                height = 112.dp
            )
        }

        // Resumen estadístico
        item {
            GlassCard(highlightBorder = resumen.balanceNeto > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Resumen de Control Plus", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${resumen.jugadasResueltas} resueltas · ${resumen.jugadasPendientes} pend.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PlusStatMiniBox(
                        label = "Total jugado",
                        value = "$${String.format(Locale.US, "%,.0f", resumen.totalApostado)}",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    PlusStatMiniBox(
                        label = "Total ganado",
                        value = "$${String.format(Locale.US, "%,.0f", resumen.totalGanado)}",
                        valueColor = VerifiedGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val balColor = when {
                        resumen.balanceNeto > 0.0 -> VerifiedGreen
                        resumen.balanceNeto < 0.0 -> AlertRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    PlusStatMiniBox(
                        label = "Balance neto",
                        value = (if (resumen.balanceNeto >= 0) "+$" else "-$") +
                            String.format(Locale.US, "%,.0f", kotlin.math.abs(resumen.balanceNeto)),
                        valueColor = balColor,
                        modifier = Modifier.weight(1f)
                    )
                    PlusStatMiniBox(
                        label = "% de acierto",
                        value = "${String.format(Locale.US, "%.1f", resumen.porcentajeAcierto)}% (${resumen.jugadasGanadoras}/${resumen.jugadasResueltas})",
                        valueColor = GoldBright,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showOfficialDrawDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100)),
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Outlined.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cargar resultado oficial")
                    }
                    OutlinedButton(
                        onClick = { showRulesDialog = true },
                        modifier = Modifier.heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reglas")
                    }
                }
            }
        }

        // Formulario cargar / editar jugada
        item {
            GlassCard(highlightBorder = editing != null || state.plusEvaluacion.esValida) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (editing != null) "Editando jugada" else "Cargar jugada Plus",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (editing != null) {
                        TextButton(onClick = {
                            viewModel.cancelEditPlusJugada()
                            manualNumsTxt = ""
                            notasTxt = ""
                        }) {
                            Text("Cancelar edición", color = AlertRed)
                        }
                    } else {
                        Button(
                            onClick = {
                                seed++
                                viewModel.autocompletarPlusDesdeMotor(seed)
                                manualNumsTxt = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100)),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Text("Sugerir c/Motor")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = fechaTxt,
                        onValueChange = { fechaTxt = it },
                        label = { Text("Fecha (AAAA-MM-DD)") },
                        singleLine = true,
                        colors = plusTextFieldColors(),
                        modifier = Modifier.weight(1.1f)
                    )
                    OutlinedTextField(
                        value = montoTxt,
                        onValueChange = { montoTxt = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Monto ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = plusTextFieldColors(),
                        modifier = Modifier.weight(0.9f)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("Modalidad", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModalidadPlus.entries.forEach { mod ->
                        FilterChip(
                            selected = modalidad == mod,
                            onClick = { modalidad = mod },
                            label = { Text(mod.label) },
                            colors = plusChipColors()
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Números seleccionados (${state.plusNumbers.size}/8)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    TextButton(onClick = { showGridSelector = !showGridSelector }) {
                        Text(if (showGridSelector) "Ocultar grilla 00–99" else "Mostrar grilla 00–99")
                    }
                }

                val slots = List(8) { idx -> state.plusNumbers.getOrNull(idx) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slots.chunked(4).forEach { rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSlots.forEach { num ->
                                UniformPlusSlot(
                                    number = num,
                                    modifier = Modifier.weight(1f),
                                    onClick = { if (num != null) viewModel.togglePlusNumber(num) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = manualNumsTxt,
                    onValueChange = { manualNumsTxt = it },
                    label = { Text("O escribí números manuales (ej: 07 14 22 33 48 59 71 88)") },
                    singleLine = true,
                    colors = plusTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notasTxt,
                    onValueChange = { notasTxt = it },
                    label = { Text("Nota opcional (agencia, boleta, etc.)") },
                    singleLine = true,
                    colors = plusTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showGridSelector) {
                    Spacer(Modifier.height(10.dp))
                    Text("Selector uniforme 00–99 (dorado = sugerido por análisis)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (row in 0 until 10) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (col in 0 until 10) {
                                    val num = String.format(Locale.US, "%02d", row * 10 + col)
                                    UniformSelectorCell(
                                        number = num,
                                        selected = num in state.plusNumbers,
                                        isSuggested = num in suggestedSet,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            manualNumsTxt = ""
                                            viewModel.togglePlusNumber(num)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val chosenNums = if (manualNumsTxt.isNotBlank()) {
                                Regex("\\d{1,4}").findAll(manualNumsTxt).map { it.value }.toList()
                            } else {
                                state.plusNumbers
                            }
                            val monto = montoTxt.toDoubleOrNull() ?: 1000.0
                            val ok = viewModel.saveOrUpdatePlusJugada(
                                fechaRaw = fechaTxt,
                                numerosInput = chosenNums,
                                modalidad = modalidad,
                                montoApostado = monto,
                                notas = notasTxt
                            )
                            if (ok) {
                                manualNumsTxt = ""
                                notasTxt = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon, contentColor = Color(0xFF002112)),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (editing != null) "Actualizar jugada" else "Guardar y controlar jugada")
                    }
                    OutlinedButton(
                        onClick = onOpenAnalisis,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Ver Análisis")
                    }
                }
            }
        }

        // Filtros y listado de jugadas guardadas
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Jugadas registradas (${filteredJugadas.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = onOpenHistorial) {
                        Text("Ver sorteos (${plus.historial.size})")
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = filtroFechaTxt,
                    onValueChange = { filtroFechaTxt = it },
                    label = { Text("Filtrar por fecha o número") },
                    singleLine = true,
                    colors = plusTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    JugadaEstadoFiltro.entries.forEach { est ->
                        FilterChip(
                            selected = filtroEstado == est,
                            onClick = { filtroEstado = est },
                            label = { Text(est.label) },
                            colors = plusChipColors()
                        )
                    }
                }
            }
        }

        if (filteredJugadas.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        "No hay jugadas Plus que coincidan con el filtro actual. Cargá una arriba para compararla automáticamente contra el historial oficial.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredJugadas, key = { it.id }) { jugada ->
                PlusJugadaCard(
                    jugada = jugada,
                    onEdit = { viewModel.startEditPlusJugada(jugada) },
                    onDelete = { viewModel.deletePlusJugada(jugada.id) }
                )
            }
        }

        item { ResponsibleGamingFooter() }
    }

    if (showOfficialDrawDialog) {
        RegisterOfficialPlusDrawDialog(
            defaultFecha = plus.defaultFechaIso.ifBlank { "2026-11-24" },
            onSave = { fecha, rawNums, notas ->
                val ok = viewModel.registerOfficialPlusResult(fecha, rawNums, notas)
                if (ok) showOfficialDrawDialog = false
            },
            onDismiss = { showOfficialDrawDialog = false }
        )
    }

    if (showRulesDialog) {
        ConfigurePlusRulesDialog(
            reglas = plus.reglasPremios,
            onSave = { updated ->
                viewModel.updatePlusReglasPremios { updated }
                showRulesDialog = false
            },
            onDismiss = { showRulesDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlusJugadaCard(
    jugada: PlusJugada,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val (statusColor, statusBg) = when (jugada.estado) {
        EstadoJugada.GANO -> VerifiedGreen to VerifiedGreen.copy(alpha = 0.16f)
        EstadoJugada.NO_GANO -> AlertRed to AlertRed.copy(alpha = 0.16f)
        EstadoJugada.PENDIENTE -> PendingAmber to PendingAmber.copy(alpha = 0.16f)
    }

    GlassCard(highlightBorder = jugada.estado == EstadoJugada.GANO) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${jugada.fechaIso} · ${jugada.modalidad.label}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (jugada.notas.isNotBlank()) {
                    Text(jugada.notas, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusBg)
                    .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(jugada.estado.label, style = MaterialTheme.typography.labelMedium, color = statusColor, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            jugada.numeros.forEach { num ->
                val isExact = num in jugada.aciertosExactos
                val isPartial = num in jugada.aciertosParciales
                val chipColor = when {
                    isExact -> VerifiedGreen
                    isPartial -> PendingAmber
                    else -> MaterialTheme.colorScheme.outline
                }
                val txtColor = when {
                    isExact -> VerifiedGreen
                    isPartial -> PendingAmber
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(chipColor.copy(alpha = if (isExact || isPartial) 0.18f else 0.10f))
                        .border(1.dp, chipColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = when {
                            isExact -> "$num ★"
                            isPartial -> "$num ≈"
                            else -> num
                        },
                        style = NumericCellStyle,
                        color = txtColor
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(jugada.detalleComparacion, style = MaterialTheme.typography.labelMedium, color = statusColor)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Apostado: $${String.format(Locale.US, "%,.0f", jugada.montoApostado)} · Premio: $${String.format(Locale.US, "%,.0f", jugada.premioGanado)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar", tint = AlertRed)
                }
            }
        }
    }
}

@Composable
private fun RegisterOfficialPlusDrawDialog(
    defaultFecha: String,
    onSave: (fecha: String, numbersRaw: String, notas: String) -> Unit,
    onDismiss: () -> Unit
) {
    var fechaTxt by remember { mutableStateOf(defaultFecha) }
    var numsTxt by remember { mutableStateOf("") }
    var notasTxt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Sorteo Oficial Plus") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Al guardarlo se incorpora automáticamente al Historial de Sorteos, compara todas tus jugadas de esa fecha y recalcula el Análisis Predictivo.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = fechaTxt,
                    onValueChange = { fechaTxt = it },
                    label = { Text("Fecha (AAAA-MM-DD o DD/MM/AAAA)") },
                    singleLine = true,
                    colors = plusTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = numsTxt,
                    onValueChange = { numsTxt = it },
                    label = { Text("20 números sorteados (2 o 4 cifras)") },
                    placeholder = { Text("Ej: 12 48 07 93 21 ...") },
                    minLines = 3,
                    maxLines = 5,
                    colors = plusTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notasTxt,
                    onValueChange = { notasTxt = it },
                    label = { Text("Nota opcional") },
                    singleLine = true,
                    colors = plusTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(fechaTxt, numsTxt, notasTxt) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100))
            ) {
                Text("Guardar sorteo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ConfigurePlusRulesDialog(
    reglas: PlusReglasPremios,
    onSave: (PlusReglasPremios) -> Unit,
    onDismiss: () -> Unit
) {
    var m8 by remember { mutableStateOf(reglas.mult8Aciertos.toString()) }
    var m7 by remember { mutableStateOf(reglas.mult7Aciertos.toString()) }
    var m6 by remember { mutableStateOf(reglas.mult6Aciertos.toString()) }
    var m5 by remember { mutableStateOf(reglas.mult5Aciertos.toString()) }
    var mCab by remember { mutableStateOf(reglas.multCabezaExacta.toString()) }
    var approx by remember { mutableStateOf(reglas.habilitarParcialAproximacion) }
    var term by remember { mutableStateOf(reglas.habilitarParcialTerminacion) }
    var mParcial by remember { mutableStateOf(reglas.multAciertoParcial.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reglas de Premios Plus") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Multiplicadores sobre el monto apostado:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = m8, onValueChange = { m8 = it }, label = { Text("8 ac.") }, singleLine = true, modifier = Modifier.weight(1f), colors = plusTextFieldColors())
                    OutlinedTextField(value = m7, onValueChange = { m7 = it }, label = { Text("7 ac.") }, singleLine = true, modifier = Modifier.weight(1f), colors = plusTextFieldColors())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = m6, onValueChange = { m6 = it }, label = { Text("6 ac.") }, singleLine = true, modifier = Modifier.weight(1f), colors = plusTextFieldColors())
                    OutlinedTextField(value = m5, onValueChange = { m5 = it }, label = { Text("5 ac.") }, singleLine = true, modifier = Modifier.weight(1f), colors = plusTextFieldColors())
                }
                OutlinedTextField(value = mCab, onValueChange = { mCab = it }, label = { Text("Cabeza Exacta (x monto)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = plusTextFieldColors())
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Acierto parcial ±1 (aprox.)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = approx, onCheckedChange = { approx = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Acierto parcial terminación", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = term, onCheckedChange = { term = it })
                }
                OutlinedTextField(value = mParcial, onValueChange = { mParcial = it }, label = { Text("Multiplicador por parcial (0 = sin pago)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = plusTextFieldColors())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        PlusReglasPremios(
                            mult8Aciertos = m8.toDoubleOrNull() ?: reglas.mult8Aciertos,
                            mult7Aciertos = m7.toDoubleOrNull() ?: reglas.mult7Aciertos,
                            mult6Aciertos = m6.toDoubleOrNull() ?: reglas.mult6Aciertos,
                            mult5Aciertos = m5.toDoubleOrNull() ?: reglas.mult5Aciertos,
                            multCabezaExacta = mCab.toDoubleOrNull() ?: reglas.multCabezaExacta,
                            habilitarParcialAproximacion = approx,
                            habilitarParcialTerminacion = term,
                            multAciertoParcial = mParcial.toDoubleOrNull() ?: reglas.multAciertoParcial
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100))
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// =============================================================================
// SUBMÓDULO 2: HISTORIAL AUTOMÁTICO DE SORTEOS PLUS
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorialDeSorteosSection(
    state: MainUiState,
    viewModel: QuinielaViewModel
) {
    val context = LocalContext.current
    val plus = state.plusModule
    val historial = plus.historial

    var searchQuery by remember { mutableStateOf("") }
    var visibleLimit by remember { mutableStateOf(40) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            }.getOrNull()
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "archivo_importado"
            if (!content.isNullOrBlank()) {
                viewModel.importPlusFileContent(content, name)
            }
        }
    }

    val filteredDraws = remember(historial, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) historial
        else historial.filter { d ->
            d.fechaIso.contains(q, ignoreCase = true) ||
                d.origen.contains(q, ignoreCase = true) ||
                d.numerosPlus.any { it.contains(q) }
        }
    }

    val newestDate = historial.firstOrNull()?.fechaIso ?: "--"
    val oldestDate = historial.lastOrNull()?.fechaIso ?: "--"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(highlightBorder = historial.isNotEmpty()) {
                Text("Base Histórica de Quiniela Plus", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Total sorteos persistidos: ${historial.size} · Rango: $oldestDate a $newestDate",
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldBright
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Se carga automáticamente desde el CSV histórico del proyecto, guarda cada resultado oficial registrado en Control de Jugadas y recalcula el Análisis Predictivo ante cada cambio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { filePicker.launch(arrayOf("text/*", "application/json", "application/octet-stream", "*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100))
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Importar CSV/JSON/TXT")
                    }
                    OutlinedButton(onClick = { showPasteDialog = true }) {
                        Text("Pegar texto")
                    }
                    OutlinedButton(onClick = { viewModel.exportPlusHistoryCsv() }) {
                        Icon(Icons.Outlined.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Exportar CSV")
                    }
                    OutlinedButton(onClick = { viewModel.reimportBaseCsvToPlus() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restaurar CSV base")
                    }
                    if (historial.isNotEmpty()) {
                        OutlinedButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Vaciar todo", color = AlertRed)
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    visibleLimit = 40
                },
                label = { Text("Filtrar historial (fecha, número u origen)") },
                singleLine = true,
                colors = plusTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filteredDraws.isEmpty()) {
            item {
                GlassCard {
                    Text(
                        "No hay sorteos en el historial Plus para mostrar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val pageItems = filteredDraws.take(visibleLimit)
            items(pageItems, key = { it.fechaIso }) { sorteo ->
                PlusSorteoHistoryCard(
                    sorteo = sorteo,
                    onDelete = { viewModel.deletePlusSorteo(sorteo.fechaIso) }
                )
            }
            if (filteredDraws.size > visibleLimit) {
                item {
                    OutlinedButton(
                        onClick = { visibleLimit += 50 },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Text("Mostrar más (${filteredDraws.size - visibleLimit} restantes)")
                    }
                }
            }
        }

        item { ResponsibleGamingFooter() }
    }

    if (showPasteDialog) {
        PastePlusHistoryDialog(
            onImport = { raw, name ->
                viewModel.importPlusFileContent(raw, name)
                showPasteDialog = false
            },
            onDismiss = { showPasteDialog = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Vaciar historial Quiniela Plus") },
            text = { Text("¿Querés eliminar los ${historial.size} sorteos del historial Plus? Podrás restaurar el CSV base o importar otro archivo cuando quieras.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllPlusHistory()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White)
                ) {
                    Text("Vaciar historial")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlusSorteoHistoryCard(
    sorteo: PlusSorteo,
    onDelete: () -> Unit
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sorteo ${sorteo.fechaIso}", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Text(
                    "${sorteo.origen}${if (sorteo.notas.isNotBlank()) " · ${sorteo.notas}" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Borrar sorteo", tint = AlertRed)
            }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            sorteo.numerosPlus.forEachIndexed { idx, num ->
                val isHead = idx == 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isHead) GoldBright.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                        .border(
                            1.dp,
                            if (isHead) GoldBright else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = num,
                        style = NumericCellStyle,
                        color = if (isHead) GoldBright else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PastePlusHistoryDialog(
    onImport: (raw: String, fileName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar CSV / JSON / TXT pegado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Pegá filas con fecha y números (CSV, TXT separado por coma/; o JSON). Se detectan duplicados automáticamente por fecha.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Contenido del archivo") },
                    minLines = 5,
                    maxLines = 8,
                    colors = plusTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(text, "pegado_manual.txt") },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100))
            ) {
                Text("Analizar e importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlusCsvMappingDialog(
    suggestion: PlusCsvMappingSuggestion,
    onConfirm: (dateCol: Int, numberCols: List<Int>, packedCol: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDateCol by remember(suggestion) { mutableStateOf(suggestion.suggestedDateCol) }
    var selectedPackedCol by remember(suggestion) { mutableStateOf(suggestion.suggestedPackedCol) }
    val selectedNumCols = remember(suggestion) {
        mutableStateListOf<Int>().apply { addAll(suggestion.suggestedNumberCols) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar columnas (${suggestion.fileName})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "El archivo no tiene encabezados estándar exactos. Confirmá por única vez qué columna es la fecha y cuáles contienen los números:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Columna de FECHA:", style = MaterialTheme.typography.labelMedium, color = GoldBright)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestion.headers.forEachIndexed { idx, header ->
                        FilterChip(
                            selected = selectedDateCol == idx,
                            onClick = { selectedDateCol = idx },
                            label = { Text(header.ifBlank { "Col ${idx + 1}" }) },
                            colors = plusChipColors()
                        )
                    }
                }
                Text("Columnas con NÚMEROS (tocá una columna agrupada o múltiples individuales):", style = MaterialTheme.typography.labelMedium, color = EmeraldNeon)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestion.headers.forEachIndexed { idx, header ->
                        if (idx == selectedDateCol) return@forEachIndexed
                        val isNum = idx in selectedNumCols || selectedPackedCol == idx
                        FilterChip(
                            selected = isNum,
                            onClick = {
                                selectedPackedCol = null
                                if (idx in selectedNumCols) selectedNumCols.remove(idx) else selectedNumCols.add(idx)
                            },
                            label = { Text(header.ifBlank { "Col ${idx + 1}" }) },
                            colors = plusChipColors()
                        )
                    }
                }
                if (suggestion.sampleRows.isNotEmpty()) {
                    Text("Vista previa (primeras filas):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    suggestion.sampleRows.take(3).forEach { row ->
                        Text(
                            row.joinToString(" | "),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val packed = if (selectedNumCols.size == 1 && selectedPackedCol == null) {
                        val sample = suggestion.sampleRows.firstOrNull()?.getOrNull(selectedNumCols.first()).orEmpty()
                        if (Regex("\\d{1,4}").findAll(sample).count() >= 8) selectedNumCols.first() else null
                    } else selectedPackedCol
                    onConfirm(selectedDateCol, selectedNumCols.sorted(), packed)
                },
                enabled = selectedNumCols.isNotEmpty() || selectedPackedCol != null,
                colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100))
            ) {
                Text("Confirmar e importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

// =============================================================================
// SUBMÓDULO 3: ANÁLISIS PREDICTIVO PLUS (100% DETERMINÍSTICO)
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalisisPredictivoPlusSection(
    state: MainUiState,
    viewModel: QuinielaViewModel,
    onGoToControl: () -> Unit,
    onGoToHistorial: () -> Unit
) {
    val plus = state.plusModule
    val analisis = plus.analisis
    var showWeightsPanel by remember { mutableStateOf(false) }

    if (analisis == null || !analisis.tieneDatos) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassCard {
                    Text("Sin datos históricos cargados", style = MaterialTheme.typography.titleMedium, color = PendingAmber)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        analisis?.mensajeSinDatos ?: "No hay sorteos históricos de Quiniela Plus cargados. El motor predictivo nunca inventa números ni usa generación aleatoria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.reimportBaseCsvToPlus() },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100))
                        ) {
                            Text("Cargar CSV histórico")
                        }
                        OutlinedButton(onClick = onGoToHistorial) {
                            Text("Ir a Historial")
                        }
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cabecera de metadata + ventana + ponderación configurable
        item {
            GlassCard(highlightBorder = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Análisis Predictivo Plus", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Último sorteo: ${analisis.fechaUltimoSorteo} · Ventana: ${analisis.totalSorteosAnalizados}/${analisis.totalSorteosHistorico} sorteos",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldBright
                        )
                    }
                    OutlinedButton(onClick = { showWeightsPanel = !showWeightsPanel }) {
                        Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pesos")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cálculo 100% determinístico sobre el historial real (desde ${analisis.fechaPrimerSorteo}). Sin generación aleatoria.",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldNeon
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    VentanaPlus.entries.forEach { win ->
                        FilterChip(
                            selected = plus.ventanaAnalisis == win,
                            onClick = { viewModel.selectPlusVentana(win) },
                            label = { Text(win.label) },
                            colors = plusChipColors()
                        )
                    }
                }

                if (showWeightsPanel) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    WeightsControlPanel(
                        pesos = plus.pesosMotor,
                        topCombinaciones = plus.topCombinaciones,
                        onUpdatePesos = { viewModel.updatePlusPesos(it) },
                        onUpdateTopN = { viewModel.setPlusTopCombinaciones(it) }
                    )
                }
            }
        }

        // D) Combinaciones sugeridas coherentes
        item {
            GlassCard(highlightBorder = true) {
                Text("D) Top ${analisis.combinacionesSugeridas.size} Combinaciones Coherentes (8 números)", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Generadas determinísticamente cruzando frecuencia (${(analisis.pesosAplicados.pesoFrecuencia * 100).roundToInt()}%), atraso (${(analisis.pesosAplicados.pesoAtraso * 100).roundToInt()}%), patrones (${(analisis.pesosAplicados.pesoPatrones * 100).roundToInt()}%) y temporal/Markov (${(analisis.pesosAplicados.pesoTemporalMarkov * 100).roundToInt()}%) con filtro de suma, paridad, altos/bajos y decenas.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    analisis.combinacionesSugeridas.forEach { combo ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .border(1.dp, GoldBright.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Sugerencia #${combo.ranking}", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                                TextButton(onClick = {
                                    viewModel.cargarCombinacionEnControl(combo.numeros)
                                    onGoToControl()
                                }) {
                                    Text("Cargar en boleta")
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                combo.numeros.forEach { num ->
                                    GoldNumberPill(number = num)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                combo.justificacion,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // D) Ranking de candidatos ponderados con explicación
        item {
            GlassCard {
                Text("Ranking Ponderado de Candidatos (Top 15)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("Tocá cualquier número para sumarlo o quitarlo de tu boleta en Control de Jugadas.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    analisis.rankingCandidatos.take(15).forEachIndexed { idx, stat ->
                        CandidateExplanationRow(
                            rank = idx + 1,
                            stat = stat,
                            selected = stat.numero in state.plusNumbers,
                            onToggle = { viewModel.togglePlusNumber(stat.numero) }
                        )
                    }
                }
            }
        }

        // A) Análisis Estadístico Básico
        item {
            BasicStatsCard(analisis = analisis)
        }

        // B) Análisis de Patrones
        item {
            PatternsStatsCard(analisis = analisis)
        }

        // C) Análisis Temporal, Ciclos y Markov
        item {
            TemporalStatsCard(analisis = analisis)
        }

        item { ResponsibleGamingFooter() }
    }
}

@Composable
private fun WeightsControlPanel(
    pesos: PlusPesosMotor,
    topCombinaciones: Int,
    onUpdatePesos: (PlusPesosMotor) -> Unit,
    onUpdateTopN: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ponderación personalizada del motor:", style = MaterialTheme.typography.labelMedium, color = GoldBright)
        WeightSliderRow("Frecuencia", pesos.pesoFrecuencia) { v -> onUpdatePesos(pesos.copy(pesoFrecuencia = v)) }
        WeightSliderRow("Atraso (gap)", pesos.pesoAtraso) { v -> onUpdatePesos(pesos.copy(pesoAtraso = v)) }
        WeightSliderRow("Patrones", pesos.pesoPatrones) { v -> onUpdatePesos(pesos.copy(pesoPatrones = v)) }
        WeightSliderRow("Temporal / Markov", pesos.pesoTemporalMarkov) { v -> onUpdatePesos(pesos.copy(pesoTemporalMarkov = v)) }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cantidad combinaciones:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(2, 4, 6).forEach { n ->
                    FilterChip(
                        selected = topCombinaciones == n,
                        onClick = { onUpdateTopN(n) },
                        label = { Text("Top $n") },
                        colors = plusChipColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightSliderRow(
    label: String,
    value: Double,
    onChange: (Double) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${(value * 100).roundToInt()}%", style = NumericCellStyle, color = GoldBright)
        }
        Slider(
            value = value.toFloat().coerceIn(0f, 1f),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = GoldBright, activeTrackColor = EmeraldNeon)
        )
    }
}

@Composable
private fun CandidateExplanationRow(
    rank: Int,
    stat: PlusNumeroStats,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) EmeraldNeon.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .border(
                1.dp,
                if (selected) EmeraldNeon else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(10.dp)
            )
            .clickable { onToggle() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("#$rank", style = NumericCellStyle, color = GoldBright)
        TempNumberBadge(number = stat.numero, subLabel = "${stat.puntaje0a100} pts", isHot = stat.puntaje0a100 >= 75)
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Score ${stat.puntaje0a100}/100", style = MaterialTheme.typography.labelMedium, color = EmeraldNeon, fontWeight = FontWeight.Bold)
                Text("F:${(stat.scoreFrecuencia * 100).roundToInt()} A:${(stat.scoreAtraso * 100).roundToInt()} P:${(stat.scorePatrones * 100).roundToInt()} M:${(stat.scoreTemporal * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stat.explicacion, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BasicStatsCard(analisis: PlusResultadoAnalisis) {
    GlassCard {
        Text("A) Análisis Estadístico Básico", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "Media de apariciones: ${String.format(Locale.US, "%.2f", analisis.mediaApariciones)} · Desvío estándar: ±${String.format(Locale.US, "%.2f", analisis.desvioEstandarApariciones)}",
            style = MaterialTheme.typography.labelMedium,
            color = GoldBright
        )
        Spacer(Modifier.height(10.dp))

        Text("Calientes (mayor frecuencia reciente y acumulada):", style = MaterialTheme.typography.labelMedium, color = HotCoral)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            analisis.calientes.take(10).forEach { s ->
                MiniBadgePill("${s.numero} (${s.frecuenciaAbsoluta}x)", HotCoral)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Fríos (menor aparición en ventana):", style = MaterialTheme.typography.labelMedium, color = EmeraldNeon)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            analisis.frios.take(10).forEach { s ->
                MiniBadgePill("${s.numero} (${s.frecuenciaAbsoluta}x)", EmeraldNeon)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Mayor atraso (sorteos sin salir):", style = MaterialTheme.typography.labelMedium, color = PendingAmber)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            analisis.mayorAtraso.take(10).forEach { s ->
                MiniBadgePill("${s.numero} (-${s.atrasoSorteos})", PendingAmber)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Valores atípicos (|z| >= 1.8): ${if (analisis.outliers.isEmpty()) "Ninguno en la ventana actual" else "${analisis.outliers.size} detectados"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (analisis.outliers.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                analisis.outliers.take(8).forEach { o ->
                    val sign = if (o.zScore >= 0) "+" else ""
                    MiniBadgePill("${o.numero} (z=$sign${String.format(Locale.US, "%.1f", o.zScore)})", GoldBright)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternsStatsCard(analisis: PlusResultadoAnalisis) {
    val p = analisis.patrones
    GlassCard {
        Text("B) Análisis de Patrones y Co-ocurrencia", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlusStatMiniBox(
                label = "Paridad",
                value = "${String.format(Locale.US, "%.1f", p.pctPares)}% Par / ${String.format(Locale.US, "%.1f", p.pctImpares)}% Imp",
                valueColor = GoldBright,
                modifier = Modifier.weight(1f)
            )
            PlusStatMiniBox(
                label = "Altos (50–99) / Bajos",
                value = "${String.format(Locale.US, "%.1f", p.pctAltos)}% Alt / ${String.format(Locale.US, "%.1f", p.pctBajos)}% Baj",
                valueColor = EmeraldNeon,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Suma de dígitos modal: ${p.rangoSumaDigitosModal} (boleta 8: ${p.rangoSuma8SugeridaMin}–${p.rangoSuma8SugeridaMax}) · Repeticiones del sorteo previo: ${String.format(Locale.US, "%.2f", p.promedioRepeticionesAnterior)} (último: ${p.ultimoSorteoRepetidos})",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))
        Text("Pares más frecuentes:", style = MaterialTheme.typography.labelMedium, color = GoldBright)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.paresFrecuentes.take(8).forEach { pair ->
                MiniBadgePill("${pair.numeros.joinToString("-")} (${pair.apariciones}x)", GoldBright)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Tríos más frecuentes:", style = MaterialTheme.typography.labelMedium, color = EmeraldNeon)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.triosFrecuentes.take(6).forEach { trio ->
                MiniBadgePill("${trio.numeros.joinToString("-")} (${trio.apariciones}x)", EmeraldNeon)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Terminaciones (0–9) con frecuencia y atraso:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            p.terminaciones.forEach { t ->
                MiniBadgePill("Term.${t.digito}: ${String.format(Locale.US, "%.1f", t.porcentaje)}% · atr.${t.atrasoSorteos}", MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Distribución por decena:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        val maxDecPct = p.distribucionDecenas.maxOfOrNull { it.porcentaje }?.coerceAtLeast(1.0) ?: 10.0
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            p.distribucionDecenas.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.etiqueta, style = NumericCellStyle, modifier = Modifier.width(56.dp))
                    LinearProgressIndicator(
                        progress = { (d.porcentaje / maxDecPct).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = EmeraldNeon,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text("${String.format(Locale.US, "%.1f", d.porcentaje)}%", style = NumericCellStyle, modifier = Modifier.width(46.dp))
                }
            }
        }
    }
}

@Composable
private fun TemporalStatsCard(analisis: PlusResultadoAnalisis) {
    GlassCard {
        Text("C) Análisis Temporal, Ciclos y Markov", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))

        Text("Top transición Markov (respecto al último sorteo ${analisis.fechaUltimoSorteo}):", style = MaterialTheme.typography.labelMedium, color = GoldBright)
        Spacer(Modifier.height(4.dp))
        val topMarkov = analisis.rankingCandidatos.sortedByDescending { it.markovLift }.take(8)
        topMarkov.forEach { m ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Número ${m.numero} · Lift ${String.format(Locale.US, "%.2f", m.markovLift)}x", style = MaterialTheme.typography.labelMedium)
                Text("P=${String.format(Locale.US, "%.1f", m.probabilidadMarkov * 100)}%", style = NumericCellStyle, color = EmeraldNeon)
            }
        }

        if (analisis.tendenciasDiaSemana.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Tendencia por día de la semana:", style = MaterialTheme.typography.labelMedium, color = EmeraldNeon)
            Spacer(Modifier.height(4.dp))
            analisis.tendenciasDiaSemana.forEach { dow ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${dow.diaNombre} (${dow.cantidadSorteos} sort.)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dow.numerosTop.joinToString(" · "), style = NumericCellStyle, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        if (analisis.estacionalidadMensual.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Estacionalidad mensual:", style = MaterialTheme.typography.labelMedium, color = GoldBright)
            Spacer(Modifier.height(4.dp))
            analisis.estacionalidadMensual.forEach { mes ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${mes.etiquetaMes} (${mes.cantidadSorteos})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(mes.numerosTop.joinToString(" · "), style = NumericCellStyle, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PlusStatMiniBox(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MiniBadgePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontSize = 11.sp)
    }
}

@Composable
private fun plusChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    selectedLabelColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun plusTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = MaterialTheme.colorScheme.primary
)
