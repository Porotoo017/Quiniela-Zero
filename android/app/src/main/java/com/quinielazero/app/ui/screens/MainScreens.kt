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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.quinielazero.app.R
import com.quinielazero.app.core.model.Jurisdiccion
import com.quinielazero.app.core.model.NumeroConStats
import com.quinielazero.app.core.model.ThemeMode
import com.quinielazero.app.core.model.TurnoSorteo
import com.quinielazero.app.core.model.VentanaAnalisis
import com.quinielazero.app.ui.MainUiState
import com.quinielazero.app.ui.QuinielaViewModel
import com.quinielazero.app.ui.components.EvolutionChart
import com.quinielazero.app.ui.components.DrawNumberCircle
import com.quinielazero.app.ui.components.GlassCard
import com.quinielazero.app.ui.components.LuckyBannerCard
import com.quinielazero.app.ui.components.TicketBallBadge
import com.quinielazero.app.ui.components.GoldNumberPill
import com.quinielazero.app.ui.components.ResponsibleGamingFooter
import com.quinielazero.app.ui.components.TempNumberBadge
import com.quinielazero.app.ui.components.UniformPlusSlot
import com.quinielazero.app.ui.components.UniformSelectorCell
import com.quinielazero.app.ui.components.VerificationBadge
import com.quinielazero.app.ui.theme.AlertRed
import com.quinielazero.app.ui.theme.CyanNeon
import com.quinielazero.app.ui.theme.GoldBright
import com.quinielazero.app.ui.theme.HotCoral
import com.quinielazero.app.ui.theme.IvoryBg
import com.quinielazero.app.ui.theme.NumericDisplayStyle
import com.quinielazero.app.ui.theme.ObsidianBg
import com.quinielazero.app.ui.theme.PendingAmber
import com.quinielazero.app.ui.theme.VerifiedGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

enum class AppTab(val label: String, val icon: ImageVector) {
    ESTIMACIONES("Inicio", Icons.Outlined.AutoGraph),
    HISTORIAL("Historial", Icons.Outlined.History),
    PLUS("Q. Plus", Icons.Outlined.StarOutline),
    SIGNIFICADOS("Sueños", Icons.Outlined.MenuBook),
    JUGADAS("Jugadas", Icons.Outlined.ConfirmationNumber),
    AJUSTES("Fuentes", Icons.Outlined.Settings)
}

@Composable
private fun appChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    selectedLabelColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
fun QuinielaZeroRoot(
    state: MainUiState,
    viewModel: QuinielaViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(AppTab.ESTIMACIONES) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isDarkActive = when (state.config.themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    LaunchedEffect(Unit) {
        delay(1300)
        showSplash = false
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.app_wallpaper_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDarkActive) Color(0xFF03100A).copy(alpha = 0.18f)
                        else IvoryBg.copy(alpha = 0.78f)
                    )
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTablet = maxWidth >= 600.dp

                if (isTablet) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            header = {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_clover_circuit),
                                    contentDescription = "Logo Quiniela-Zero",
                                    modifier = Modifier.padding(top = 12.dp).size(46.dp).clip(CircleShape)
                                )
                            }
                        ) {
                            AppTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, maxLines = 1, softWrap = false) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    )
                                )
                            }
                        }
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ) { pad ->
                            TabContent(
                                tab = currentTab,
                                state = state,
                                viewModel = viewModel,
                                isTablet = true,
                                snackbarHostState = snackbarHostState,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onNavigateToTab = { currentTab = it },
                                modifier = Modifier.padding(pad)
                            )
                        }
                    }
                } else {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                tonalElevation = 8.dp
                            ) {
                                AppTab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = currentTab == tab,
                                        onClick = { currentTab = tab },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = {
                                            Text(
                                                text = tab.label,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        )
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
                            onNavigateToTab = { currentTab = it },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Transparent, Color(0xFF020C07).copy(alpha = 0.88f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("QUINIELA-ZERO", style = MaterialTheme.typography.headlineMedium, color = GoldBright)
            Text("PRO MAX", style = MaterialTheme.typography.labelMedium, color = CyanNeon)
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
    onNavigateToTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (tab) {
            AppTab.ESTIMACIONES -> EstimacionesScreen(state, viewModel, isTablet, snackbarHostState)
            AppTab.HISTORIAL -> HistorialScreen(state, viewModel)
            AppTab.PLUS -> QuinielaPlusScreen(state, viewModel)
            AppTab.SIGNIFICADOS -> SignificadosScreen(state, viewModel, isTablet, onNavigateToTab)
            AppTab.JUGADAS -> MisJugadasScreen(state, viewModel)
            AppTab.AJUSTES -> FuentesYAjustesScreen(state, viewModel, onRequestNotificationPermission)
        }
    }
}

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_clover_circuit),
                        contentDescription = "Emblema Quiniela-Zero",
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Quiniela-Zero", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Provincia & Ciudad",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { viewModel.refreshFromSources(true) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "Actualizar resultados",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        item {
            LuckyBannerCard(
                drawableRes = R.drawable.banner_lucky_leprechaun,
                title = "Suerte y Estadística",
                subtitle = "Tréboles y duendes de la fortuna · Provincia & Ciudad",
                badgeText = "PRO MAX"
            )
        }
        if (state.isSyncing) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary) }
        }

        if (state.offlineMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PendingAmber.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
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

        if (est != null && (est.esDomingo || est.esFeriado)) {
            item {
                GlassCard(highlightBorder = true) {
                    Text(
                        text = if (est.esDomingo) "Domingo · Planificación semanal" else "Modo Feriado activo",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (est.esDomingo) {
                            "Hoy es domingo y no hay sorteos oficiales. Referencias sugeridas para la semana."
                        } else {
                            "Sin sorteos oficiales por feriado configurado."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            GlassCard {
                Text("Selección de análisis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Jurisdiccion.entries.forEach { jur ->
                        FilterChip(
                            selected = state.selectedJurisdiccion == jur,
                            onClick = { viewModel.selectJurisdiccion(jur) },
                            label = { Text(jur.label) },
                            colors = appChipColors(),
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
                            colors = appChipColors(),
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
                    Text("Números destacados: ${state.config.topCount}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5).forEach { cnt ->
                            FilterChip(
                                selected = state.config.topCount == cnt,
                                onClick = { viewModel.updateConfig { it.copy(topCount = cnt) } },
                                label = { Text("Top $cnt") },
                                colors = appChipColors(),
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
                    Text("Evitar repetición inmediata", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = state.config.avoidImmediateRepeat,
                        onCheckedChange = { checked -> viewModel.updateConfig { it.copy(avoidImmediateRepeat = checked) } }
                    )
                }
            }
        }

        if (est != null) {
            item {
                GlassCard(highlightBorder = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Números destacados (${est.jurisdiccion.label})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
                        Text("Calculando estadísticas...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        est.candidatos.forEach { cand ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoldNumberPill(number = cand.numero)
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (cand.significado.isNotEmpty()) cand.significado else "Número ${cand.numero}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Confianza ${cand.confianza} · Atraso: ${cand.atrasoSorteos} sorteos",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = cand.resumen,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Números Calientes y Fríos lado a lado o apilados con píldoras uniformes
            if (est.numerosCalientes.isNotEmpty() || est.numerosFrios.isNotEmpty()) {
            item {
                val hotCard: @Composable () -> Unit = {
                    GlassCard {
                        Text("Números Calientes", style = MaterialTheme.typography.titleMedium, color = HotCoral)
                        Text("Mayor impulso en los últimos sorteos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            est.numerosCalientes.forEach { item ->
                                TempNumberBadge(
                                    number = item.numero,
                                    subLabel = "${item.apariciones}x",
                                    isHot = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                val coldCard: @Composable () -> Unit = {
                    GlassCard {
                        Text("Números Fríos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("Mayor cantidad de sorteos sin salir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            est.numerosFrios.forEach { item ->
                                TempNumberBadge(
                                    number = item.numero,
                                    subLabel = "${item.atrasoSorteos}s",
                                    isHot = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (isTablet) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { hotCard() }
                        Box(Modifier.weight(1f)) { coldCard() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        hotCard()
                        coldCard()
                    }
                }
            }

            }
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

            state.estadisticaOpuesta?.let { opp ->
                item {
                    GlassCard {
                        Text("Cruce rápido · ${opp.jurisdiccion.label}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = opp.candidatos.joinToString("  ·  ") { it.numero }.ifEmpty { "Calculando..." },
                            style = NumericDisplayStyle,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        item { ResponsibleGamingFooter() }
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
            .filter { drawItem ->
                val clean = query.trim()
                clean.isEmpty() || drawItem.fechaIso.contains(clean) || drawItem.numeros.any { n -> n.contains(clean) }
            }
            .take(120)
            .toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LuckyBannerCard(
                drawableRes = R.drawable.banner_dreams_clover,
                title = "Historial Oficial",
                subtitle = "${state.allDraws.size} sorteos guardados de Provincia y Ciudad",
                height = 112.dp
            )
        }
        item {
            Text("Historial (${state.allDraws.size} sorteos)", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Buscar fecha (YYYY-MM-DD) o número") },
                singleLine = true,
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filterJur == null, onClick = { filterJur = null }, label = { Text("Ambas") }, colors = appChipColors(), modifier = Modifier.heightIn(min = 48.dp))
                Jurisdiccion.entries.forEach { j ->
                    FilterChip(selected = filterJur == j, onClick = { filterJur = j }, label = { Text(j.label) }, colors = appChipColors(), modifier = Modifier.heightIn(min = 48.dp))
                }
                TurnoSorteo.entries.forEach { t ->
                    FilterChip(
                        selected = filterTurno == t,
                        onClick = { filterTurno = if (filterTurno == t) null else t },
                        label = { Text(t.label) },
                        colors = appChipColors(),
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
                        Text("${draw.loteria.label} · ${draw.turno.label}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(draw.fechaIso, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VerificationBadge(draw.estado)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    draw.numeros.chunked(4).forEachIndexed { rIdx, rowNums ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowNums.forEachIndexed { cIdx, n ->
                                val pos = rIdx * 4 + cIdx + 1
                                DrawNumberCircle(
                                    position = pos,
                                    number = n,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuinielaPlusScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel
) {
    var seed by remember { mutableStateOf(0) }
    var boletasTxt by remember { mutableStateOf("1") }
    val eval = state.plusEvaluacion
    val suggestedSet = remember(state.estadistica) {
        state.estadistica?.candidatos?.map { it.numero }?.toSet().orEmpty()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LuckyBannerCard(
                drawableRes = R.drawable.banner_lucky_leprechaun,
                title = "Quiniela Plus · Provincia",
                subtitle = "8 números distintos (00–99) · Plus, Súper Plus y Chance Plus",
                badgeText = "BOLETA",
                height = 118.dp
            )
        }

        item {
            GlassCard(highlightBorder = eval.esValida) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tu boleta (${state.plusNumbers.size}/8)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
                Spacer(Modifier.height(12.dp))

                val slots = List(8) { idx -> state.plusNumbers.getOrNull(idx) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    slots.chunked(4).forEach { rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

                Spacer(Modifier.height(12.dp))
                Text(
                    text = eval.motivo,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (eval.esValida) VerifiedGreen else AlertRed
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = boletasTxt,
                        onValueChange = {
                            boletasTxt = it.filter(Char::isDigit)
                            viewModel.setPlusBoletasCount(boletasTxt.toIntOrNull() ?: 1)
                        },
                        label = { Text("Boletas") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = appTextFieldColors(),
                        modifier = Modifier.width(110.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Costo estimado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = String.format(Locale.US, "$ %,.0f ARS", eval.costoArs),
                            style = NumericDisplayStyle,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Control última Nocturna Provincia: ${eval.premioTier} (Plus ${eval.aciertosPlus}/8 · Súper ${eval.aciertosSuperPlus}/8 · Chance ${eval.aciertosChancePlus}/8)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Selector uniforme 00–99", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Destacados marcados", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(10.dp))
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
                                    onClick = { viewModel.togglePlusNumber(num) }
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
fun SignificadosScreen(
    state: MainUiState,
    viewModel: QuinielaViewModel,
    isTablet: Boolean,
    onNavigateToTab: (AppTab) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Todos") }
    var activeItem by remember { mutableStateOf<NumeroConStats?>(null) }

    val categorias = listOf("Todos", "Tradición", "Animales", "Personas", "Naturaleza", "Objetos", "Cotidiano", "Suerte")

    val filtered = remember(state.significadosConStats, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        state.significadosConStats.filter { item ->
            val matchCat = selectedCategory == "Todos" || item.significado.categoria == selectedCategory
            val matchQ = q.isEmpty() ||
                item.significado.numero.contains(q) ||
                item.significado.significado.lowercase().contains(q) ||
                item.significado.detalle.lowercase().contains(q)
            matchCat && matchQ
        }
    }

    val columns = if (isTablet) 3 else 2

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            LuckyBannerCard(
                drawableRes = R.drawable.banner_dreams_clover,
                title = "Números y Sueños (00–99)",
                subtitle = "Tabla tradicional con estadísticas de ${state.selectedJurisdiccion.label}",
                badgeText = "SUEÑOS",
                height = 118.dp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar número (ej. 48) o sueño (ej. agua, perro)") },
                singleLine = true,
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categorias.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        colors = appChipColors(),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        items(filtered.chunked(columns)) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    GlassCard(
                        highlightBorder = item.enTopMotor,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeItem = item }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GoldNumberPill(number = item.significado.numero, size = 52.dp)
                            if (item.enTopMotor) {
                                Text("TOP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(item.significado.significado, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text(item.significado.categoria, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Salidas: ${item.apariciones} · Atraso: ${item.atrasoSorteos}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    activeItem?.let { detail ->
        AlertDialog(
            onDismissRequest = { activeItem = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = {
                Text("${detail.significado.numero} · ${detail.significado.significado}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Categoría: ${detail.significado.categoria}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(detail.significado.detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Historial (${state.selectedJurisdiccion.label}): ${detail.apariciones} apariciones totales · Atraso actual: ${detail.atrasoSorteos} sorteos · Última vez: ${detail.ultimaFecha}.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.togglePlusNumber(detail.significado.numero)
                        activeItem = null
                        onNavigateToTab(AppTab.PLUS)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldBright, contentColor = Color(0xFF141100))
                ) {
                    Text("Sumar a Q. Plus")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeItem = null }) {
                    Text("Cerrar")
                }
            }
        )
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
            LuckyBannerCard(
                drawableRes = R.drawable.banner_dreams_clover,
                title = "Mis Jugadas",
                subtitle = "Boletas cifradas y control automático contra extractos",
                badgeText = "BOLETAS",
                height = 114.dp
            )
        }
        item {
            Text("Presupuesto y registro", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
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
                    color = if (totalHoy > state.config.dailyLimitArs) AlertRed else MaterialTheme.colorScheme.primary
                )
            }
        }

        item {
            GlassCard {
                Text("Registrar boleta física", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Jurisdiccion.entries.forEach { j ->
                        FilterChip(selected = jur == j, onClick = { jur = j }, label = { Text(j.label) }, colors = appChipColors(), modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TurnoSorteo.entries.forEach { t ->
                        FilterChip(selected = turno == t, onClick = { turno = t }, label = { Text(t.label) }, colors = appChipColors(), modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = numsInput,
                    onValueChange = { numsInput = it },
                    label = { Text("Números jugados (ej: 14, 32, 4820)") },
                    colors = appTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = montoInput,
                    onValueChange = { montoInput = it.filter(Char::isDigit) },
                    label = { Text("Monto apostado (ARS)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appTextFieldColors(),
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
                        Text("${ticket.jurisdiccion.label} · ${ticket.turno.label} (${ticket.fechaIso})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Apostado: $ ${ticket.montoArs.toInt()} ARS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ticket.numeros.forEach { num ->
                                TicketBallBadge(
                                    number = num,
                                    isHit = ticket.aciertos.any { hit -> hit == num || hit.endsWith(num) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
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
                    IconButton(onClick = { viewModel.deleteTicket(ticket.id) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Eliminar boleta", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        item { ResponsibleGamingFooter() }
    }
}

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
            LuckyBannerCard(
                drawableRes = R.drawable.banner_lucky_leprechaun,
                title = "Fuentes y Ajustes",
                subtitle = "Tema automático/oscuro, feriados y notificaciones ART",
                height = 112.dp
            )
        }

        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Estado técnico de fuentes", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    OutlinedButton(onClick = { viewModel.refreshFromSources(true) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Probar ahora")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (state.fuentesSalud.isEmpty()) {
                    Text("Sin chequeos registrados todavía.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.fuentesSalud.forEach { src ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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
                Text("Apariencia y Calendario", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { tm ->
                        FilterChip(
                            selected = state.config.themeMode == tm,
                            onClick = { viewModel.updateConfig { it.copy(themeMode = tm) } },
                            label = { Text(tm.label) },
                            colors = appChipColors(),
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
                        Text("Modo Feriado (sin sorteos)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
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
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Notificaciones ART (${state.config.morningHour}:00 y ${state.config.nightHour}:00)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
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
                        onClick = { viewModel.updateConfig { it.copy(morningHour = if (it.morningHour == 9) 8 else 9) } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Mañana: ${state.config.morningHour}:00")
                    }
                    OutlinedButton(
                        onClick = { viewModel.updateConfig { it.copy(nightHour = if (it.nightHour == 23) 22 else 23) } },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Noche: ${state.config.nightHour}:00")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Silenciar días específicos:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                            colors = appChipColors(),
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }

        item {
            GlassCard {
                Text("Juego Responsable y Privacidad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tus boletas y topes se guardan únicamente en tu dispositivo con cifrado AES-256 (Android Keystore). Sin cuentas ni rastreo externo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://saberjugar.gob.ar/"))) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("Saber Jugar") }
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.loteria.gba.gov.ar/"))) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text("Lotería PBA") }
                }
            }
        }

        item { ResponsibleGamingFooter() }
    }
}
