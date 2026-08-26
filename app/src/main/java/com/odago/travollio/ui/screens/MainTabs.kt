package com.odago.travollio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.Item
import com.odago.travollio.state.TripViewModel
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.DesignTokens
import com.odago.travollio.ui.SoftShadowA
import com.odago.travollio.ui.SoftShadowB
import com.odago.travollio.ui.components.pressScale
import com.odago.travollio.ui.components.springSnappy
import com.odago.travollio.ui.tvColors

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.CalendarMonth),
    TRIP("Trip", Icons.Filled.Map),
    MONEY("Money", Icons.Filled.AccountBalanceWallet),
    ASSISTANT("Assistant", Icons.Filled.SmartToy),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun MainTabs(
    viewModel: TripViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    // The UI OBSERVES the state flow so the agent's writes recompose live.
    val state by viewModel.state.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val lastOutcome by viewModel.lastOutcome.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Which itinerary moment's dossier is open (null = none)
    var detailItem by remember { mutableStateOf<Item?>(null) }
    // Trip-tab day pill selection (lifted so the assistant's "Open Day N" can focus it)
    var tripSelectedDay by rememberSaveable { mutableIntStateOf(0) }
    // Trips hub (switch/create/edit/archive)
    var showTrips by remember { mutableStateOf(false) }

    // Deep-link from an assistant "Go to" button → the relevant tab/item/day.
    fun handleGoto(actionJson: String) {
        val o = try { org.json.JSONObject(actionJson) } catch (_: Exception) { return }
        when (o.optString("type")) {
            "item" -> {
                val id = o.optLong("itemId", -1L)
                val item = state.items.firstOrNull { it.id == id }
                if (item != null) detailItem = item else tab = 1
            }
            "day" -> {
                val d = o.optInt("day", -1)
                if (d >= 1) tripSelectedDay = (d - 1).coerceAtLeast(0)
                tab = 1
            }
            "money" -> tab = 2
            "trip" -> tab = 0
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { GlassDock(selected = tab, onSelect = { tab = it }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Spring tab transitions: directional push + crossfade (iOS-like).
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(springSnappy()) { it / 4 * dir } + fadeIn(springSnappy())) togetherWith
                        (slideOutHorizontally(springSnappy()) { -it / 4 * dir } + fadeOut(springSnappy()))
                },
                label = "tab"
            ) { i ->
                when (i) {
                    0 -> TodayScreen(state = state, onItemClick = { detailItem = it }, onOpenTrips = { showTrips = true })
                    1 -> TripScreen(
                        state = state,
                        selectedDay = tripSelectedDay,
                        onSelectDay = { tripSelectedDay = it },
                        onItemClick = { detailItem = it }
                    )
                    2 -> MoneyScreen(state = state, onAddExpense = viewModel::addExpense)
                    3 -> AssistantScreen(
                        state = state,
                        isThinking = isThinking,
                        lastOutcome = lastOutcome,
                        streamingText = streamingText,
                        onSend = viewModel::send,
                        onGoto = ::handleGoto,
                        onReset = viewModel::resetConversation
                    )
                    else -> SettingsScreen(
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange
                    )
                }
            }

            // Moment dossier — opened by tapping any itinerary card.
            detailItem?.let { item ->
                ItemDetailSheet(
                    item = item,
                    state = state,
                    onDismiss = { detailItem = null },
                    onAttach = { target, uris -> viewModel.attachToItem(target.id, uris) },
                    onDelete = { target -> viewModel.deleteItem(target.id); detailItem = null }
                )
            }

            // Trips hub — switch/create/edit/archive/delete trips.
            if (showTrips) {
                TripManagerSheet(
                    trips = state.availableTrips,
                    activeTripId = state.trip?.id,
                    onDismiss = { showTrips = false },
                    onSwitch = { viewModel.switchTrip(it) },
                    onArchive = { viewModel.archiveTrip(it) },
                    onCreate = { viewModel.createTrip(it) },
                    onUpdate = { viewModel.updateTrip(it) },
                    onDelete = { viewModel.deleteTrip(it) }
                )
            }
        }
    }
}

/** Floating glass dock: a frosted pill bar, centered with side + bottom margins,
 *  each tab behind an animated pill-highlight "active" indicator, iOS-style. */
@Composable
private fun GlassDock(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(DesignTokens.radiusDock)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = shape,
            color = MaterialTheme.tvColors.glassPanel,
            border = BorderStroke(0.5.dp, MaterialTheme.tvColors.glassBorder),
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                .shadow(elevation = 14.dp, shape = shape, ambientColor = SoftShadowA, spotColor = SoftShadowB)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Tab.entries.forEachIndexed { i, t ->
                    DockItem(tab = t, selected = selected == i, onClick = { onSelect(i) })
                }
            }
        }
    }
}

@Composable
private fun DockItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) Accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = springSnappy(),
        label = "dockBg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) AccentDeep else MaterialTheme.tvColors.textTertiary,
        animationSpec = springSnappy(),
        label = "dockFg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bg,
        contentColor = fg,
        modifier = Modifier.pressScale()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(tab.icon, contentDescription = tab.label, tint = fg, modifier = Modifier.size(22.dp))
            Text(tab.label, style = MaterialTheme.typography.labelSmall, color = fg)
        }
    }
}
