package com.metrolist.remix.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.remix.innertube.InnerTubeScraper
import com.metrolist.remix.innertube.ScrapedSong
import com.metrolist.remix.playback.AuthenticRemixEngine
import com.metrolist.remix.playback.RemixPlaybackState
import com.metrolist.remix.playback.RemixSegment
import com.metrolist.remix.playback.SegmentRole
import kotlinx.coroutines.launch

/**
 * Strictly non-AI industrial monochrome palette inspired by Braun hardware & Swiss print grids.
 * Zero purple/cyan gradients, zero neon glows, zero glassmorphism.
 */
object IndustrialPalette {
    val PaperBg = Color(0xFFF2F0EA)
    val PaperSurface = Color(0xFFE8E5DD)
    val CarbonBg = Color(0xFF111110)
    val CarbonSurface = Color(0xFF1A1A18)

    val InkPrimaryLight = Color(0xFF141413)
    val InkSecondaryLight = Color(0xFF6B6963)
    val RuleLight = Color(0xFFD2CFC6)

    val InkPrimaryDark = Color(0xFFECEAE3)
    val InkSecondaryDark = Color(0xFF8A8780)
    val RuleDark = Color(0xFF2C2C29)

    // Single functional signal indicator (Braun International Orange)
    val SignalOrange = Color(0xFFD9381E)
}

@Composable
fun MetroMixMinimalScreen(
    scraper: InnerTubeScraper,
    remixEngine: AuthenticRemixEngine
) {
    val coroutineScope = rememberCoroutineScope()
    val playbackState by remixEngine.state.collectAsState()

    var darkTheme by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<ScrapedSong>>(emptyList()) }
    var selectedCrate by remember { mutableStateOf<List<ScrapedSong>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    val bg = if (darkTheme) IndustrialPalette.CarbonBg else IndustrialPalette.PaperBg
    val surface = if (darkTheme) IndustrialPalette.CarbonSurface else IndustrialPalette.PaperSurface
    val ink = if (darkTheme) IndustrialPalette.InkPrimaryDark else IndustrialPalette.InkPrimaryLight
    val muted = if (darkTheme) IndustrialPalette.InkSecondaryDark else IndustrialPalette.InkSecondaryLight
    val rule = if (darkTheme) IndustrialPalette.RuleDark else IndustrialPalette.RuleLight

    fun executeSearch() {
        if (searchQuery.isBlank()) return
        coroutineScope.launch {
            isSearching = true
            searchError = null
            scraper.searchSongs(searchQuery, maxResults = 18)
                .onSuccess { songs -> searchResults = songs }
                .onFailure { err -> searchError = err.message ?: "SEARCH FAILED" }
            isSearching = false
        }
    }

    fun toggleSongInCrate(song: ScrapedSong) {
        val exists = selectedCrate.any { it.videoId == song.videoId }
        if (exists) {
            selectedCrate = selectedCrate.filterNot { it.videoId == song.videoId }
        } else if (selectedCrate.size < 5) {
            selectedCrate = selectedCrate + song
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top Architectural Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "METROMIX // 40·20 DECK",
                    color = ink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "YTM INNER-TUBE FREE CLIENT · DUAL EXOPLAYER",
                    color = muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp
                )
            }

            Box(
                modifier = Modifier
                    .border(1.dp, rule, RoundedCornerShape(2.dp))
                    .clickable { darkTheme = !darkTheme }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (darkTheme) "PAPER [LIGHT]" else "CARBON [DARK]",
                    color = ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = rule)

        // 2. 2-to-5 Song Crate Strip (Hardware Slot Selector)
        CrateSlotBar(
            selectedSongs = selectedCrate,
            onRemoveSong = { videoId ->
                selectedCrate = selectedCrate.filterNot { it.videoId == videoId }
            },
            onStartRemix = {
                remixEngine.loadAndStartRemix(selectedCrate)
            },
            surface = surface,
            ink = ink,
            muted = muted,
            rule = rule
        )

        HorizontalDivider(thickness = 1.dp, color = rule)

        // 3. YouTube Music Free Scraper Search Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YTM >",
                color = muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 10.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search YouTube Music catalog (add 2 to 5 songs)...",
                        color = muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    ),
                    cursorBrush = SolidColor(IndustrialPalette.SignalOrange),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .border(1.dp, ink, RoundedCornerShape(2.dp))
                    .clickable { executeSearch() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isSearching) "WAIT" else "FIND",
                    color = ink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = rule)

        // 4. Split View: Active 40s/20s Remix Sequence OR Search Catalog Results
        var activeTab by remember { mutableStateOf(0) } // 0 = SEARCH CATALOG, 1 = REMIX TIMELINE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
        ) {
            TabCell(
                title = "01 / YTM CATALOG (${searchResults.size})",
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                ink = ink,
                muted = muted,
                rule = rule,
                modifier = Modifier.weight(1f)
            )
            TabCell(
                title = "02 / 40s·20s SEQUENCE (${playbackState.segments.size})",
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                ink = ink,
                muted = muted,
                rule = rule,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(thickness = 1.dp, color = rule)

        Box(modifier = Modifier.weight(1f)) {
            if (activeTab == 0) {
                CatalogResultsList(
                    results = searchResults,
                    selectedCrate = selectedCrate,
                    searchError = searchError,
                    onToggleSelect = ::toggleSongInCrate,
                    ink = ink,
                    muted = muted,
                    rule = rule
                )
            } else {
                RemixTimelineList(
                    state = playbackState,
                    onJumpToSegment = { idx -> remixEngine.triggerManualTransitionTo(idx) },
                    ink = ink,
                    muted = muted,
                    rule = rule
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = rule)

        // 5. Bottom Hardware Dual-Deck Transport & Crossfader Console
        DualDeckTransportConsole(
            state = playbackState,
            onTogglePlay = { remixEngine.togglePlayPause() },
            onNextSegment = { remixEngine.triggerManualTransitionTo() },
            surface = surface,
            ink = ink,
            muted = muted,
            rule = rule
        )
    }
}

@Composable
private fun CrateSlotBar(
    selectedSongs: List<ScrapedSong>,
    onRemoveSong: (String) -> Unit,
    onStartRemix: () -> Unit,
    surface: Color,
    ink: Color,
    muted: Color,
    rule: Color
) {
    val canBuildRemix = selectedSongs.size in 2..5

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REMIX CRATE SLOTS [${selectedSongs.size}/5 SELECTED · MIN 2]",
                color = muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .background(
                        color = if (canBuildRemix) IndustrialPalette.SignalOrange else Color.Transparent,
                        shape = RoundedCornerShape(2.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (canBuildRemix) IndustrialPalette.SignalOrange else rule,
                        shape = RoundedCornerShape(2.dp)
                    )
                    .clickable(enabled = canBuildRemix) { onStartRemix() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (canBuildRemix) "START 40s/20s REMIX" else "ADD ${2 - selectedSongs.size} MORE",
                    color = if (canBuildRemix) Color.White else muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5 Hardware-style Tactile Slots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (slotIndex in 0 until 5) {
                val song = selectedSongs.getOrNull(slotIndex)
                val roleHint = if (slotIndex % 2 == 0) "40s" else "20s"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .border(
                            width = 1.dp,
                            color = if (song != null) ink else rule,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .clickable(enabled = song != null) {
                            if (song != null) onRemoveSong(song.videoId)
                        }
                        .padding(6.dp)
                ) {
                    if (song != null) {
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0${slotIndex + 1}·$roleHint",
                                    color = IndustrialPalette.SignalOrange,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "×",
                                    color = muted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = song.title,
                                color = ink,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "SLOT 0${slotIndex + 1}",
                                color = muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "EMPTY",
                                color = rule,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCell(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    ink: Color,
    muted: Color,
    rule: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 16.dp)
    ) {
        Text(
            text = title,
            color = if (selected) ink else muted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) IndustrialPalette.SignalOrange else Color.Transparent)
        )
    }
}

@Composable
private fun CatalogResultsList(
    results: List<ScrapedSong>,
    selectedCrate: List<ScrapedSong>,
    searchError: String?,
    onToggleSelect: (ScrapedSong) -> Unit,
    ink: Color,
    muted: Color,
    rule: Color
) {
    if (searchError != null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "ERR // $searchError",
                color = IndustrialPalette.SignalOrange,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        return
    }

    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "SEARCH YOUTUBE MUSIC ABOVE AND TAP 2–5 SONGS TO POPULATE REMIX SLOTS.",
                color = muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(results, key = { _, s -> s.videoId }) { index, song ->
            val slotIdx = selectedCrate.indexOfFirst { it.videoId == song.videoId }
            val isSelected = slotIdx >= 0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleSelect(song) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%02d", index + 1),
                    color = if (isSelected) IndustrialPalette.SignalOrange else muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        color = ink,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} · ${formatSeconds(song.durationSec)}",
                        color = muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (isSelected) IndustrialPalette.SignalOrange else rule,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSelected) "SLOT 0${slotIdx + 1}" else "+ ADD",
                        color = if (isSelected) IndustrialPalette.SignalOrange else ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = rule)
        }
    }
}

@Composable
private fun RemixTimelineList(
    state: RemixPlaybackState,
    onJumpToSegment: (Int) -> Unit,
    ink: Color,
    muted: Color,
    rule: Color
) {
    if (state.segments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "NO ACTIVE REMIX SCHEDULE. SELECT 2–5 SONGS AND TAP 'START 40s/20s REMIX'.",
                color = muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.segments, key = { _, seg -> seg.index }) { idx, seg ->
            val isActive = idx == state.activeSegmentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onJumpToSegment(idx) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isActive) "▶" else String.format("%02d", idx + 1),
                    color = if (isActive) IndustrialPalette.SignalOrange else muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.width(28.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = seg.song.title,
                        color = ink,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val cueWindow = "${formatSeconds((seg.startOffsetMs / 1000).toInt())} → ${
                        formatSeconds((seg.endOffsetMs / 1000).toInt())
                    }"
                    Text(
                        text = "${seg.song.artist} · CUE $cueWindow",
                        color = muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .border(
                            width = 1.dp,
                            color = if (seg.role == SegmentRole.ANCHOR_40S) ink else IndustrialPalette.SignalOrange,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = seg.role.label,
                        color = if (seg.role == SegmentRole.ANCHOR_40S) ink else IndustrialPalette.SignalOrange,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = rule)
        }
    }
}

@Composable
private fun DualDeckTransportConsole(
    state: RemixPlaybackState,
    onTogglePlay: () -> Unit,
    onNextSegment: () -> Unit,
    surface: Color,
    ink: Color,
    muted: Color,
    rule: Color
) {
    val rawProgress = if (state.currentSegmentTotalMs > 0) {
        (state.currentSegmentElapsedMs.toFloat() / state.currentSegmentTotalMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 80),
        label = "segmentProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(surface)
            .padding(16.dp)
    ) {
        // Live Deck Telemetry Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${state.activeDeckLabel} · A:${state.deckAGainPct}% / B:${state.deckBGainPct}%",
                color = if (state.isCrossfading) IndustrialPalette.SignalOrange else ink,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = state.activeStreamInfo,
                color = muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = state.statusNote,
            color = ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Crisp 4px Linear Segment Bar (with Crossfade Zone Marker at last 3.6s)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(rule)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(IndustrialPalette.SignalOrange)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val elapsedSec = (state.currentSegmentElapsedMs / 1000).toInt()
            val totalSec = (state.currentSegmentTotalMs / 1000).toInt()
            Text(
                text = "${formatSeconds(elapsedSec)} / ${formatSeconds(totalSec)} SEGMENT WINDOW",
                color = muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .border(1.dp, ink, RoundedCornerShape(2.dp))
                        .clickable { onTogglePlay() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (state.isPlaying) "PAUSE" else "PLAY",
                        color = ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .border(1.dp, ink, RoundedCornerShape(2.dp))
                        .clickable { onNextSegment() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "CROSSFADE NOW →",
                        color = ink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatSeconds(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", m, s)
}
