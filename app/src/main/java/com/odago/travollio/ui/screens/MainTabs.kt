package com.odago.travollio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.Item
import com.odago.travollio.state.TripViewModel
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
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
        bottomBar = { GlassNavBar(selected = tab, onSelect = { tab = it }) }
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

/** Solid-ish theme surface tab bar with a hairline top border — iOS tab bar language. */
@Composable
private fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit) {
    Box {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 0.dp
        ) {
            Tab.entries.forEachIndexed { i, t ->
                NavigationBarItem(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    icon = { Icon(t.icon, contentDescription = t.label) },
                    label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentDeep,
                        selectedTextColor = AccentDeep,
                        indicatorColor = Accent.copy(alpha = 0.16f),
                        unselectedIconColor = MaterialTheme.tvColors.textTertiary,
                        unselectedTextColor = MaterialTheme.tvColors.textTertiary
                    )
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.tvColors.hairline,
            thickness = 0.5.dp
        )
    }
}
