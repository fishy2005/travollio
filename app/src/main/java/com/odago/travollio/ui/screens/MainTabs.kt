package com.odago.travollio.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odago.travollio.data.Item
import com.odago.travollio.state.TripViewModel
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Inter
import com.odago.travollio.ui.components.springSnappy
import com.odago.travollio.ui.tvColors

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Filled.WbSunny),
    TRIP("Trip", Icons.Filled.Map),
    MONEY("Money", Icons.Filled.AccountBalanceWallet),
    PABLO("Pablo", Icons.Filled.AutoAwesome),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@Composable
fun MainTabs(
    viewModel: TripViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    initialTab: Int = 0
) {
    // The UI OBSERVES the state flow so the agent's writes recompose live.
    val state by viewModel.state.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val lastOutcome by viewModel.lastOutcome.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, Tab.entries.size - 1)) }
    // Which itinerary moment's dossier is open (null = none)
    var detailItem by remember { mutableStateOf<Item?>(null) }
    // Trip-tab day pill selection (lifted so the assistant's "Open Day N" can focus it)
    var tripSelectedDay by rememberSaveable { mutableIntStateOf(0) }
    // Trips hub (switch/create/edit/archive)
    var showTrips by remember { mutableStateOf(false) }
    // Packing list sheet (Trip tab)
    var showPacking by remember { mutableStateOf(false) }

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
                    0 -> TodayScreen(
                        state = state,
                        onItemClick = { detailItem = it },
                        onOpenTrips = { showTrips = true },
                        onToggleDone = viewModel::toggleItemDone,
                        onQuickAction = { action ->
                            when (action) {
                                "money" -> tab = 2
                                "weather" -> { tab = 3; viewModel.send("What's the weather like for the next days of my trip? Use weather_get.") }
                                "plan" -> { tab = 3; viewModel.send("Fill in a good plan for today in our current day. Use itinerary_get and today_plan first, then add 2-4 sensible gaps with itinerary_add.") }
                            }
                        }
                    )
                    1 -> TripScreen(
                        state = state,
                        selectedDay = tripSelectedDay,
                        onSelectDay = { tripSelectedDay = it },
                        onItemClick = { detailItem = it },
                        onToggleDone = viewModel::toggleItemDone,
                        onOpenPacking = { showPacking = true }
                    )
                    2 -> MoneyScreen(state = state, onAddExpense = viewModel::addExpense, onDeleteExpense = viewModel::deleteTransaction)
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
                        onDarkThemeChange = onDarkThemeChange,
                        state = state
                    )
                }
            }

            // Moment dossier — opened by tapping any itinerary card.
            // Re-derive from state so check-offs/attaches made in the sheet reflect live.
            detailItem?.let { held ->
                state.items.firstOrNull { it.id == held.id }?.let { item ->
                    ItemDetailSheet(
                        item = item,
                        state = state,
                        onDismiss = { detailItem = null },
                        onAttach = { target, uris -> viewModel.attachToItem(target.id, uris) },
                        onDelete = { target -> viewModel.deleteItem(target.id); detailItem = null },
                        onToggleDone = { target -> viewModel.toggleItemDone(target.id) }
                    )
                }
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

            // Packing list sheet — per-trip checklist with progress + AI suggestions.
            if (showPacking) {
                PackingSheet(
                    state = state,
                    onDismiss = { showPacking = false },
                    onAdd = viewModel::addPacking,
                    onToggle = viewModel::togglePacked,
                    onRemove = viewModel::removePacking,
                    onGenerate = viewModel::generatePacking,
                    onAskPablo = {
                        showPacking = false
                        tab = 3
                        viewModel.send("Generate me a packing list for the trip. Use packing_generate after checking itinerary_get.")
                    }
                )
            }
        }
    }
}

/** Solid-ish theme surface tab bar with a hairline top border — iOS tab bar language. */
@Composable
private fun GlassNavBar(selected: Int, onSelect: (Int) -> Unit) {
    DockedBottomBar(selected = selected, onSelect = onSelect)
}

/** Floating dock: rounded island, sliding terracotta pill behind the active tab,
 *  hairline labels, haptic tick on select. The app's signature piece of chrome. */
@Composable
private fun DockedBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.tvColors.glassPanel,
            border = BorderStroke(0.75.dp, MaterialTheme.tvColors.glassBorder),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    12.dp,
                    RoundedCornerShape(28.dp),
                    ambientColor = com.odago.travollio.ui.SoftShadowA,
                    spotColor = com.odago.travollio.ui.SoftShadowB
                )
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val count = Tab.entries.size
                Tab.entries.forEachIndexed { i, t ->
                    val active = selected == i
                    val tint by animateColorAsState(
                        targetValue = if (active) AccentDeep else MaterialTheme.tvColors.textTertiary,
                        animationSpec = springSnappy(),
                        label = "dockTint$i"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!active) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(i)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Indicator halo behind the icon when selected.
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .graphicsLayer {
                                            scaleX = if (active) 1f else 0.3f
                                            scaleY = if (active) 1f else 0.3f
                                            alpha = if (active) 1f else 0f
                                        }
                                        .clip(RoundedCornerShape(50))
                                        .background(Accent.copy(alpha = 0.14f))
                                )
                                Icon(
                                    t.icon,
                                    contentDescription = t.label,
                                    tint = tint,
                                    modifier = Modifier
                                        .size(if (active) 23.dp else 21.dp)
                                        .graphicsLayer {
                                            if (active) {
                                                scaleX = 1.06f
                                                scaleY = 1.06f
                                            }
                                        }
                                )
                            }
                            Spacer(Modifier.size(2.dp))
                            Text(
                                t.label,
                                style = TextStyle(
                                    fontFamily = Inter,
                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 0.2.sp
                                ),
                                color = tint
                            )
                        }
                    }
                }
            }
        }
    }
}
