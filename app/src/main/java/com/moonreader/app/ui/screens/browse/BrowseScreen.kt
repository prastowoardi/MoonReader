package com.moonreader.app.ui.screens.browse

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.moonreader.app.domain.model.CatalogMode
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.ui.components.EmptyStateView
import com.moonreader.app.ui.components.MangaCoverCard
import com.moonreader.app.ui.state.BrowseCatalogUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    sources: List<SourceInfo>,
    onOpenSource: (String) -> Unit,
    onImportLocalCbz: (Uri) -> Unit,
    onRestoreBackupUri: (Uri) -> Unit = onImportLocalCbz,
    onAddOpdsServer: (name: String, url: String, user: String, pass: String) -> Unit = { _, _, _, _ -> },
    onRemoveOpdsServer: (String) -> Unit = {}
) {
    val cbzPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLocalCbz(uri)
    }

    val backupPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onRestoreBackupUri(uri)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddOpdsDialog by remember { mutableStateOf(false) }

    if (showAddOpdsDialog) {
        AddOpdsServerDialog(
            onDismiss = { showAddOpdsDialog = false },
            onConfirm = { name, url, user, pass ->
                onAddOpdsServer(name, url, user, pass)
                showAddOpdsDialog = false
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Jelajah", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Sumber") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Impor CBZ Lokal") }
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sources.filter { it.isEnabled }, key = { it.id }) { source ->
                        SourceCard(
                            source = source,
                            canDelete = source.id.startsWith("opds_"),
                            onDelete = { onRemoveOpdsServer(source.id) },
                            onClick = { onOpenSource(source.id) }
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { showAddOpdsDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tambah Server OPDS (Tanpa Login / Auth)")
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Konektor OPDS & Source Modular",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Mendukung server OPDS v1.2 / OPDS-PSE seperti Suwayomi-Server, Komga, Kavita, atau katalog OPDS publik. Kosongkan username/password untuk server tanpa login.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                EmptyStateView(
                    icon = Icons.Outlined.FolderZip,
                    title = "Impor Komik Lokal atau Backup",
                    subtitle = "Pilih file .cbz/.zip untuk dibaca offline, atau pilih file backup .tachibk/.json untuk memulihkan koleksi.",
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = {
                                cbzPicker.launch(arrayOf("*/*"))
                            }) {
                                Icon(Icons.Outlined.FileOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pilih File CBZ / ZIP")
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = {
                                backupPicker.launch(arrayOf("*/*"))
                            }) {
                                Icon(Icons.Outlined.Restore, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pulihkan Backup (.tachibk / .json)")
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = { onOpenSource("local_cbz") }) {
                                Text("Lihat Koleksi Lokal")
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: SourceInfo,
    canDelete: Boolean = false,
    onDelete: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            source.isLocal -> Icons.Outlined.FolderZip
                            source.id == "mangadex" -> Icons.Outlined.Public
                            else -> Icons.Outlined.CloudQueue
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = source.lang,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus server OPDS")
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddOpdsServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, user: String, pass: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("http://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Server OPDS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Server") },
                    placeholder = { Text("Misal: Suwayomi PC / Komga") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL Katalog OPDS") },
                    placeholder = { Text("http://192.168.1.x:4567/api/opds/v1.2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username (opsional)") },
                    placeholder = { Text("Kosongkan jika tanpa login") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password (opsional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = url.startsWith("http://") || url.startsWith("https://"),
                onClick = { onConfirm(name, url, user, pass) }
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogBrowserScreen(
    state: BrowseCatalogUiState,
    libraryIds: Set<String>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSwitchMode: (CatalogMode) -> Unit,
    onLoadNextPage: () -> Unit,
    onImportLocalCbz: (Uri) -> Unit,
    onSelectManga: (Manga) -> Unit
) {
    var searchExpanded by remember { mutableStateOf(false) }

    val cbzPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLocalCbz(uri)
    }

    BackHandler(onBack = {
        if (searchExpanded) {
            searchExpanded = false
        } else {
            onBack()
        }
    })

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            if (searchExpanded) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            onQueryChange("")
                            searchExpanded = false
                            onSwitchMode(CatalogMode.POPULAR)
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Tutup")
                        }
                    },
                    title = {
                        TextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Cari judul di ${state.sourceName}...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    actions = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Bersihkan")
                            }
                        }
                        IconButton(onClick = onSearchSubmit) {
                            Icon(Icons.Outlined.Search, contentDescription = "Cari")
                        }
                    }
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                        }
                    },
                    title = { Text(state.sourceName, fontWeight = FontWeight.SemiBold) },
                    actions = {
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Cari")
                        }
                        if (state.sourceId == "local_cbz") {
                            IconButton(onClick = {
                                cbzPicker.launch(
                                    arrayOf("application/zip", "application/x-cbz", "application/octet-stream", "*/*")
                                )
                            }) {
                                Icon(Icons.Outlined.FileOpen, contentDescription = "Impor CBZ")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.sourceId != "local_cbz") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.mode == CatalogMode.POPULAR,
                        onClick = { onSwitchMode(CatalogMode.POPULAR) },
                        label = { Text("Populer") }
                    )
                    FilterChip(
                        selected = state.mode == CatalogMode.LATEST,
                        onClick = { onSwitchMode(CatalogMode.LATEST) },
                        label = { Text("Terbaru") }
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isLoading && state.items.isNotEmpty(),
                onRefresh = { onSwitchMode(state.mode) },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading && state.items.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null && state.items.isEmpty() -> {
                        EmptyStateView(
                            icon = Icons.Outlined.Explore,
                            title = "Gagal memuat katalog",
                            subtitle = state.error,
                            action = {
                                Button(onClick = { onSwitchMode(state.mode) }) {
                                    Text("Coba Lagi")
                                }
                            }
                        )
                    }
                    state.items.isEmpty() -> {
                        EmptyStateView(
                            icon = Icons.Outlined.Explore,
                            title = "Tidak ada hasil",
                            subtitle = if (state.sourceId == "local_cbz") {
                                "Belum ada file CBZ/ZIP yang diimpor. Ketuk ikon file di kanan atas untuk memilih arsip."
                            } else {
                                "Coba kata kunci pencarian lain."
                            }
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.items, key = { it.id }) { manga ->
                                val marked = manga.copy(inLibrary = manga.id in libraryIds)
                                MangaCoverCard(
                                    manga = marked,
                                    showUnreadBadge = false,
                                    showInLibraryTag = true,
                                    compactOverlayTitle = true,
                                    onClick = { onSelectManga(manga) }
                                )
                            }
                            if (state.hasNextPage) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (state.isLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        } else {
                                            OutlinedButton(onClick = onLoadNextPage) {
                                                Text("Muat Lebih Banyak")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
