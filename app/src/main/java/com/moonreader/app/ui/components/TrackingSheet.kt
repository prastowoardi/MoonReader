package com.moonreader.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moonreader.app.domain.model.TrackRecord
import com.moonreader.app.domain.model.TrackSearchResult
import com.moonreader.app.domain.model.TrackStatus
import com.moonreader.app.domain.model.TrackerAccountInfo
import com.moonreader.app.ui.state.TrackSearchUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrackingBottomSheet(
    mangaTitle: String,
    tracks: List<TrackRecord>,
    accounts: List<TrackerAccountInfo>,
    searchState: TrackSearchUiState?,
    onDismiss: () -> Unit,
    onOpenSearch: (trackerId: String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onCloseSearch: () -> Unit,
    onSelectCandidate: (TrackSearchResult) -> Unit,
    onUpdateTrack: (TrackRecord) -> Unit,
    onRemoveTrack: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (searchState != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onCloseSearch) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                    Text(
                        text = "Cari di ${searchState.trackerName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = searchState.query,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Judul komik...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    trailingIcon = {
                        IconButton(onClick = onSearchSubmit) {
                            Icon(Icons.Outlined.Search, contentDescription = "Cari")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                when {
                    searchState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    searchState.error != null -> {
                        Text(
                            text = searchState.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    searchState.results.isEmpty() -> {
                        Text(
                            text = "Tidak ditemukan hasil untuk \"${searchState.query}\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchState.results, key = { "${it.trackerId}_${it.remoteId}" }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectCandidate(item) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = item.coverUrl.ifBlank { null },
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(46.dp)
                                            .height(66.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val meta = buildString {
                                            if (item.publishingStatus.isNotBlank()) append(item.publishingStatus)
                                            if (item.totalChapters > 0) {
                                                if (isNotEmpty()) append(" • ")
                                                append("${item.totalChapters} ch")
                                            }
                                        }
                                        if (meta.isNotBlank()) {
                                            Text(
                                                text = meta,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Tracking • $mangaTitle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                accounts.forEach { account ->
                    val linked = tracks.firstOrNull { it.trackerId == account.id }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (linked == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (account.isLoggedIn) {
                                            "Akun: ${account.username}"
                                        } else {
                                            "Belum login (login di menu Lainnya untuk sinkron)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(onClick = { onOpenSearch(account.id) }) {
                                    Text("Tautkan")
                                }
                            }
                        } else {
                            LinkedTrackCard(
                                account = account,
                                track = linked,
                                onSync = onUpdateTrack,
                                onRemove = { onRemoveTrack(linked.id) }
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
private fun LinkedTrackCard(
    account: TrackerAccountInfo,
    track: TrackRecord,
    onSync: (TrackRecord) -> Unit,
    onRemove: () -> Unit
) {
    var selectedStatus by remember(track) { mutableStateOf(track.status) }
    var chapterRead by remember(track) { mutableFloatStateOf(track.lastChapterRead) }
    var score by remember(track) { mutableFloatStateOf(track.score) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.coverUrl.ifBlank { null },
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(40.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus tautan")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TrackStatus.entries.forEach { st ->
                FilterChip(
                    selected = selectedStatus == st,
                    onClick = { selectedStatus = st },
                    label = { Text(st.label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        HorizontalDivider()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ch:", style = MaterialTheme.typography.labelMedium)
                IconButton(
                    onClick = { chapterRead = (chapterRead - 1f).coerceAtLeast(0f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Kurangi", modifier = Modifier.size(16.dp))
                }
                val chLabel = if (track.totalChapters > 0) {
                    "${chapterRead.toInt()}/${track.totalChapters}"
                } else {
                    "${chapterRead.toInt()}"
                }
                Text(chLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { chapterRead += 1f },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Tambah", modifier = Modifier.size(16.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Skor:", style = MaterialTheme.typography.labelMedium)
                IconButton(
                    onClick = { score = (score - 1f).coerceAtLeast(0f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Kurangi skor", modifier = Modifier.size(16.dp))
                }
                Text("${score.toInt()}/10", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { score = (score + 1f).coerceAtMost(10f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Tambah skor", modifier = Modifier.size(16.dp))
                }
            }
        }

        Button(
            enabled = account.isLoggedIn,
            onClick = {
                onSync(
                    track.copy(
                        status = selectedStatus,
                        lastChapterRead = chapterRead,
                        score = score,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (account.isLoggedIn) "Sinkronkan ke ${account.name}"
                else "Login di menu Lainnya untuk sinkron"
            )
        }
    }
}
