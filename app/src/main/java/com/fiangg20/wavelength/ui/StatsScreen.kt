package com.fiangg20.wavelength.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private fun timeLabel(ms: Long): String {
    val minutes = ms / 60_000L
    return when {
        minutes < 1L -> "0 min"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** What you listen to most, worked out from the play counts kept on this phone. */
@Composable
fun StatsScreen(vm: PlayerViewModel, onBack: () -> Unit, bottomPad: Dp) {
    val scheme = MaterialTheme.colorScheme
    val stats by vm.stats.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PageHeader("Listening stats", onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottomPad),
        ) {
            if (stats.plays == 0) {
                item {
                    Text(
                        "Nothing to show yet. A song counts once it has played for a few seconds, " +
                            "and the counts stay on this phone.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                return@LazyColumn
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatTile("Plays", "%,d".format(stats.plays), scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f))
                    StatTile("Listened", timeLabel(stats.listenedMs), scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                StatTile(
                    "Songs played",
                    "${stats.songsPlayed} of ${stats.librarySongs}",
                    scheme.secondaryContainer,
                    scheme.onSecondaryContainer,
                    Modifier.fillMaxWidth(),
                )
                Text(
                    "Listening time is the length of each song times how often you played it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                )
            }

            item { SectionTitle("Top songs") }
            val topCount = stats.topTracks.firstOrNull()?.second ?: 1
            items(stats.topTracks.size) { i ->
                val (t, n) = stats.topTracks[i]
                RankRow(
                    rank = i + 1,
                    title = t.title,
                    subtitle = t.artist,
                    count = n,
                    fraction = n.toFloat() / topCount,
                    onClick = { vm.playQueue(stats.topTracks.map { it.first }, i) },
                    active = ui.current?.id == t.id,
                )
            }

            item { SectionTitle("Top artists") }
            val topArtist = stats.topArtists.firstOrNull()?.second ?: 1
            items(stats.topArtists.size) { i ->
                val (name, n) = stats.topArtists[i]
                RankRow(i + 1, name, "", n, n.toFloat() / topArtist, onClick = null, active = false)
            }

            if (stats.topAlbums.isNotEmpty()) {
                item { SectionTitle("Top albums") }
                val topAlbum = stats.topAlbums.first().second
                items(stats.topAlbums.size) { i ->
                    val (name, n) = stats.topAlbums[i]
                    RankRow(i + 1, name, "", n, n.toFloat() / topAlbum, onClick = null, active = false)
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(container)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String,
    count: Int,
    fraction: Float,
    onClick: (() -> Unit)?,
    active: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.primary,
            modifier = Modifier.width(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "$count×",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}
