package com.quinielazero.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.quinielazero.app.R
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.ThemeMode
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.core.model.VentanaAnalisis
import com.quinielazero.app.ui.MainUiState
import com.quinielazero.app.ui.QuinielaViewModel
import com.quinielazero.app.ui.components.EvolutionChart
import com.quinielazero.app.ui.components.GlassCard
import com.quinielazero.app.ui.components.GoldNumberPill
import com.quinielazero.app.ui.components.ResponsibleGamingFooter
import com.quinielazero.app.ui.components.VerificationBadge
import com.quinielazero.app.ui.theme.AlertRed
import com.quinielazero.app.ui.theme.AmberWarm
import com.quinielazero.app.ui.theme.CyanNeon
import com.quinielazero.app.ui.theme.GoldBright
import com.quinielazero.app.ui.theme.NumericCellStyle
import com.quinielazero.app.ui.theme.NumericDisplayStyle
import com.quinielazero.app.ui.theme.ObsidianBg
import com.quinielazero.app.ui.theme.PendingAmber
import com.quinielazero.app.ui.theme.VerifiedGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class AppTab(val label: String, val icon: ImageVector) {
    ESTIMACIONES("Estimación", Icons.Outlined.AutoGraph),
    HISTORIAL("Historial", Icons.Outlined.History),
    PLUS("Q. Plus", Icons.Outlined.StarOutline),
    JUGADAS("Mis Jugadas", Icons.Outlined.ConfirmationNumber),
    AJUSTES("Fuentes", Icons.Outlined.Settings)
}

@Composable
fun QuinielaZeroRoot(
    state: MainUiState,
    viewModel: QuinielaViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(AppTab.ESTIMACIONES) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        delay(1450)
        showSplash = false
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 600.dp

            if (isTablet) {
                Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Image(
                                painter = painterResource(id = R.drawable.logo_clover_circuit),
                                contentDescription = "Logo Quiniela-Zero",
                                modifier = Modifier.padding(top = 12.dp).size(44.dp)
                            )
                        }
                    ) {
                        AppTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { pad ->
                        TabContent(
                            tab = currentTab,
                            state = state,
                            viewModel = viewModel,
                            isTablet = true,
                            snackbarHostState = snackbarHostState,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            modifier = Modifier.padding(pad)
                        )
                    }
                }
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1) }
                                )
                            }
                        }
                    }
                ) { pad ->
                    TabContent(
                        tab = currentTab,
                        state = state,
                        viewModel = viewModel,
                        isTablet = false,
                        snackbarHostState = snackbarHostState,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        modifier = Modifier.padding(pad)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(450))
        ) {
            CinematicSplashOverlay()
        }
    }
}

@Composable
private fun CinematicSplashOverlay() {
    var animStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animStarted = true }
    val scale by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0.92f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "splashScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (animStarted) 1f else 0.4f,
        animationSpec = tween(800),
        label = "splashAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_clover_bg),
            contentDescription = "Splash Quiniela-Zero",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().scale(scale).alpha(alpha)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "QUINIELA-ZERO",
                style = MaterialTheme.typography.headlineMedium,
                color = GoldBright
            )
            Text(
                text = "PRO MAX · ESTADÍSTICA VERIFICADA",
                style = MaterialTheme.typography.labelMedium,
                color = CyanNeon
            )
        }
    }
}

@Composable
private fun TabContent(
    tab: AppTab,
    state: MainUiState,
    viewModel: QuinielaViewModel,
    isTablet: Boolean,
    snackbarHostState: SnackbarHostState,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (tab) {
            AppTab.ESTIMACIONES -> EstimacionesScreen(state, viewModel, isTablet, snackbarHostState)
            AppTab.HISTORIAL -> HistorialScreen(state, viewModel)
            AppTab.PLUS -> QuinielaPlusScreen(state, viewModel)
            AppTab.JUGADAS -> MisJugadasScreen(state, viewModel)
            AppTab.AJUSTES -> FuentesYAjustesScreen(state, viewModel, onRequestNotificationPermission)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EstimacionesScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel,
    isTablet: Boolean,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val est = state.estadistica

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            // Encabezado con Logo + Botón Sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_clover_circuit),
                        contentDescription = "Emblema Quiniela-Zero",
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Quiniela-Zero", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = state.syncStatusBanner.ifEmpty { "Historial Provincia & Ciudad" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.refreshFromSources(true) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar resultados desde fuentes")
                }
            }
        }

        if (state.isSyncing) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GoldBright) }
        }

        if (state.offlineMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PendingAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, PendingAmber)
                ) {
                    Text(
                        text = "Sin conexión a fuentes externas: operando con la base histórica local guardada.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PendingAmber,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Selector de Jurisdicción + Ventana + Top 3/5
        item {
            GlassCard {
                Text("Configuración de análisis", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Jurisdiccion.entries.forEach { jur ->
                        FilterChip(
                            selected = state.selectedJurisdiccion == jur,
                            onClick = { viewModel.selectJurisdiccion(jur) },
                            label = { Text(jur.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VentanaAnalisis.entries.forEach { win ->
                        FilterChip(
                            selected = state.selectedVentana == win,
                            onClick = { viewModel.selectVentana(win) },
                            label = { Text(win.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cantidad destacados: ${state.config.topCount}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5).forEach { cnt ->
                            FilterChip(
                                selected = state.config.topCount == cnt,
                                onClick = { viewModel.updateConfig { it.copy(topCount = cnt) } },
                                label = { Text("Top $cnt") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Evitar repetición inmediata", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.config.avoidImmediateRepeat,
                        onCheckedChange = { checked -> viewModel.updateConfig { it.copy(avoidImmediateRepeat = checked) } }
                    )
                }
            }
        }

        // Calendario real y modo del día (Domingo / Feriado / Lun-Sáb)
        if (est != null) {
            item {
                GlassCard(highlightBorder = true) {
                    Text(
                        text = est.tituloModo,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (est.esDomingo || est.esFeriado) CyanNeon else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = est.subtituloCalendario,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (est.esDomingo) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Hoy es domingo: no se emiten predicciones de sorteo diario. Usá estos números solo como planificación semanal.",
                            style = MaterialTheme.typography.labelMedium,
                            color = PendingAmber
                        )
                    }
                }
            }

            // Números destacados con confianza y botón copiar
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Números destacados (${est.jurisdiccion.label})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(
                            onClick = {
                                val text = est.candidatos.joinToString(", ") { it.numero }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Quiniela-Zero Picks", text))
                                scope.launch { snackbarHostState.showSnackbar("Copiados: $text") }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar números", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copiar")
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    if (est.candidatos.isEmpty()) {
                        Text("Cargando historial estadístico...", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        est.candidatos.forEach { cand ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoldNumberPill(number = cand.numero)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Confianza ${cand.confianza} · Atraso: ${cand.atrasoSorteos} sorteos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = cand.resumen,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (cand.rachaCaliente || cand.cruceCoincidente) {
                                        Text(
                                            text = listOfNotNull(
                                                if (cand.rachaCaliente) "Racha reciente" else null,
                                                if (cand.cruceCoincidente) "Cruce Prov↔Ciu" else null
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = CyanNeon
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Gráfico de evolución + Números fríos + Cruce opuesto
            if (est.evolucionTop.size >= 2) {
                item {
                    GlassCard {
                        EvolutionChart(
                            points = est.evolucionTop,
                            leadNumber = est.candidatos.firstOrNull()?.numero ?: "--"
                        )
                    }
                }
            }

            item {
                val opp = state.estadisticaOpuesta
                if (isTablet && opp != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            ColdAndCrossCard(est.numerosFrios.map { "${it.numero} (${it.atrasoSorteos}d)" })
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OppositePreviewCard(opp.jurisdiccion.label, opp.candidatos.map { it.numero })
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ColdAndCrossCard(est.numerosFrios.map { "${it.numero} (${it.atrasoSorteos} sort.)" })
                        if (opp != null) {
                            OppositePreviewCard(opp.jurisdiccion.label, opp.candidatos.map { it.numero })
                        }
                    }
                }
            }
        }

        item { ResponsibleGamingFooter() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColdAndCrossCard(coldLabels: List<String>) {
    GlassCard {
        Text("Números fríos (mayor atraso)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            coldLabels.forEach { lbl ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(lbl, style = NumericCellStyle, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun OppositePreviewCard(label: String, picks: List<String>) {
    GlassCard {
        Text("Cruce rápido · $label", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = picks.joinToString("  ·  ").ifEmpty { "Calculando..." },
            style = NumericDisplayStyle,
            color = CyanNeon
        )
    }
}

@Composable
fun HistorialScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel
) {
    var filterJur by remember { mutableStateOf<Jurisdiccion?>(null) }
    var filterTurno by remember { mutableStateOf<TurnoSorteo?>(null) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(state.allDraws, filterJur, filterTurno, query) {
        state.allDraws
            .asSequence()
            .filter { filterJur == null || it.loteria == filterJur }
            .filter { filterTurno == null || it.turno == filterTurno }
            .filter { q ->
                val clean = query.trim()
                clean.isEmpty() || it.fechaIso.contains(clean) || it.numeros.any { n -> n.contains(clean) }
            }
            .take(120)
            .toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Historial Auto-Actualizable (${state.allDraws.size} sorteos)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar fecha (YYYY-MM-DD) o número") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterJur == null,
                    onClick = { filterJur = null },
                    label = { Text("Ambas") },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                Jurisdiccion.entries.forEach { j ->
                    FilterChip(
                        selected = filterJur == j,
                        onClick = { filterJur = j },
                        label = { Text(j.label) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
                TurnoSorteo.entries.forEach { t ->
                    FilterChip(
                        selected = filterTurno == t,
                        onClick = { filterTurno = if (filterTurno == t) null else t },
                        label = { Text(t.label) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { viewModel.exportCsv() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar CSV")
                }
                OutlinedButton(onClick = { viewModel.exportPdf() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar PDF")
                }
            }
        }

        items(filtered, key = { "${it.loteria.dbValue}_${it.fechaIso}_${it.turno.dbValue}" }) { draw ->
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${draw.loteria.label} · ${draw.turno.label}", style = MaterialTheme.typography.titleMedium)
                        Text(draw.fechaIso, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VerificationBadge(draw.estado)
                }
                Spacer(Modifier.height(10.dp))
                // Los 20 números completos en 4 filas x 5 columnas
                draw.numeros.chunked(5).forEachIndexed { rIdx, rowNums ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowNums.forEachIndexed { cIdx, n ->
                            val pos = rIdx * 5 + cIdx + 1
                            Text(
                                text = String.format(Locale.US, "%02d:%s", pos, n),
                                style = NumericCellStyle,
                                color = if (pos == 1) GoldBright else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuinielaPlusScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel
) {
    var seed by remember { mutableStateOf(0) }
    var boletasTxt by remember { mutableStateOf("1") }
    val eval = state.plusEvaluacion

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Quiniela Plus · Provincia", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                "Regla oficial: 8 números distintos (00–99) junto a la Nocturna de Provincia (Lun–Sáb). Cubre Plus, Súper Plus y Chance Plus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            GlassCard(highlightBorder = eval.esValida) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tu boleta (${state.plusNumbers.size}/8)", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = {
                            seed++
                            viewModel.autocompletarPlusDesdeMotor(seed)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Sugerir c/Motor")
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.plusNumbers.forEach { n ->
                        GoldNumberPill(
                            number = n,
                            modifier = Modifier.clickable { viewModel.togglePlusNumber(n) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = eval.motivo,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (eval.esValida) VerifiedGreen else AlertRed
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = boletasTxt,
                        onValueChange = {
                            boletasTxt = it.filter(Char::isDigit)
                            viewModel.setPlusBoletasCount(boletasTxt.toIntOrNull() ?: 1)
                        },
                        label = { Text("Boletas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp)
                    )
                    Column {
                        Text("Costo estimado", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = String.format(Locale.US, "$ %,.0f ARS", eval.costoArs),
                            style = NumericDisplayStyle,
                            color = GoldBright
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Control último extracto Nocturna Provincia: ${eval.premioTier} (Plus ${eval.aciertosPlus}/8 · Súper ${eval.aciertosSuperPlus}/8 · Chance ${eval.aciertosChancePlus}/8)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanNeon
                )
            }
        }

        item {
            GlassCard {
                Text("Selector rápido 00–99", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                for (row in 0 until 10) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (col in 0 until 10) {
                            val num = String.format(Locale.US, "%02d", row * 10 + col)
                            val selected = num in state.plusNumbers
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) GoldBright else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.togglePlusNumber(num) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = num,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color(0xFF141100) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        item { ResponsibleGamingFooter() }
    }
}

@Composable
fun MisJugadasScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel
) {
    var jur by remember { mutableStateOf(Jurisdiccion.PROVINCIA) }
    var turno by remember { mutableStateOf(TurnoSorteo.NOCTURNA) }
    var numsInput by remember { mutableStateOf("") }
    var montoInput by remember { mutableStateOf("500") }

    val totalHoy = remember(state.tickets, state.calendarStatus) {
        val hoy = state.calendarStatus?.date?.toString().orEmpty()
        state.tickets.filter { it.fechaIso == hoy }.sumOf { it.montoArs }
    }
    val limitRatio = (totalHoy / state.config.dailyLimitArs.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Mis Jugadas (Almacén Cifrado)", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            GlassCard {
                Text(
                    text = String.format(Locale.US, "Presupuesto de hoy: $ %,.0f / $ %,.0f ARS", totalHoy, state.config.dailyLimitArs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (totalHoy > state.config.dailyLimitArs) AlertRed else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { limitRatio },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (totalHoy > state.config.dailyLimitArs) AlertRed else GoldBright
                )
            }
        }

        item {
            GlassCard {
                Text("Registrar boleta física", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Jurisdiccion.entries.forEach { j ->
                        FilterChip(
                            selected = jur == j,
                            onClick = { jur = j },
                            label = { Text(j.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TurnoSorteo.entries.forEach { t ->
                        FilterChip(
                            selected = turno == t,
                            onClick = { turno = t },
                            label = { Text(t.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = numsInput,
                    onValueChange = { numsInput = it },
                    label = { Text("Números jugados (ej: 14, 32, 4820)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = montoInput,
                    onValueChange = { montoInput = it.filter(Char::isDigit) },
                    label = { Text("Monto apostado (ARS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        viewModel.addTicket(jur, turno, numsInput, montoInput.toDoubleOrNull() ?: 0.0)
                        numsInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100)),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text("Guardar boleta cifrada")
                }
            }
        }

        items(state.tickets, key = { it.id }) { ticket ->
            GlassCard(highlightBorder = ticket.aciertos.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${ticket.jurisdiccion.label} · ${ticket.turno.label} (${ticket.fechaIso})", style = MaterialTheme.typography.titleMedium)
                        Text("Jugados: ${ticket.numeros.joinToString(", ")} · $ ${ticket.montoArs.toInt()} ARS", style = MaterialTheme.typography.bodyMedium)
                        val estadoTxt = when {
                            !ticket.controlado -> "Esperando extracto oficial para comparar"
                            ticket.aciertos.isEmpty() -> "Sin aciertos registrados en el extracto"
                            else -> "¡Aciertos encontrados!: ${ticket.aciertos.joinToString(", ")}"
                        }
                        Text(
                            text = estadoTxt,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (ticket.aciertos.isNotEmpty()) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.deleteTicket(ticket.id) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Eliminar boleta")
                    }
                }
            }
        }

        item { ResponsibleGamingFooter() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FuentesYAjustesScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val hasNotifPerm = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    val diasSemana = listOf(
        1 to "Lun", 2 to "Mar", 3 to "Mié", 4 to "Jue", 5 to "Vie", 6 to "Sáb", 7 to "Dom"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Estado de Fuentes y Ajustes", style = MaterialTheme.typography.titleLarge)
        }

        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Estado técnico de fuentes", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { viewModel.refreshFromSources(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Probar ahora")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.fuentesSalud.isEmpty()) {
                    Text("Sin chequeos registrados todavía.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.fuentesSalud.forEach { src ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${src.jurisdiccion} · ${src.detalle} (${src.latenciaMs}ms)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = src.estado,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (src.estado == "OK" || src.estado == "ESPERA") VerifiedGreen else AlertRed
                            )
                        }
                    }
                }
            }
        }

        item {
            GlassCard {
                Text("Apariencia y Calendario", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { tm ->
                        FilterChip(
                            selected = state.config.themeMode == tm,
                            onClick = { viewModel.updateConfig { it.copy(themeMode = tm) } },
                            label = { Text(tm.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Feriado (sin sorteos)", style = MaterialTheme.typography.bodyMedium)
                        Text("Desactiva predicciones de sorteo en días no laborables.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = state.config.holidayMode,
                        onCheckedChange = { checked -> viewModel.updateConfig { it.copy(holidayMode = checked) } }
                    )
                }
            }
        }

        item {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = GoldBright)
                    Spacer(Modifier.width(8.dp))
                    Text("Notificaciones ART (${state.config.morningHour}:00 y ${state.config.nightHour}:00)", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (!hasNotifPerm) {
                    Button(
                        onClick = onRequestNotificationPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Habilitar permisos de notificación")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.updateConfig { it.copy(morningHour = if (it.morningHour == 9) 8 else 9) }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Mañana: ${state.config.morningHour}:00")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.updateConfig { it.copy(nightHour = if (it.nightHour == 23) 22 else 23) }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Noche: ${state.config.nightHour}:00")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Silenciar días específicos:", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    diasSemana.forEach { (dow, lbl) ->
                        val muted = dow in state.config.mutedDays
                        FilterChip(
                            selected = muted,
                            onClick = {
                                viewModel.updateConfig { cfg ->
                                    val next = cfg.mutedDays.toMutableSet()
                                    if (muted) next.remove(dow) else next.add(dow)
                                    cfg.copy(mutedDays = next)
                                }
                            },
                            label = { Text(if (muted) "$lbl (Sil.)" else lbl) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                Text("Juego Responsable y Privacidad", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tus boletas y topes se guardan únicamente en tu dispositivo con cifrado AES-256 (Android Keystore). Sin cuentas ni rastreo externo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://saberjugar.gob.ar/")))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Saber Jugar")
                    }
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.loteria.gba.gov.ar/")))
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Lotería PBA")
                    }
                }
            }
        }

        item { ResponsibleGamingFooter() }
    }
}
