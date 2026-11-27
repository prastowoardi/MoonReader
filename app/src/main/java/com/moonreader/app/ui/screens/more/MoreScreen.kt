package com.moonreader.app.ui.screens.more

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonreader.app.data.local.AppPreferences
import com.moonreader.app.data.local.PreferencesStore
import com.moonreader.app.domain.model.AppThemeMode
import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.DownloadStatus
import com.moonreader.app.domain.model.DownloadTask
import com.moonreader.app.domain.model.ReaderMode
import com.moonreader.app.domain.model.SourceInfo
import com.moonreader.app.ui.state.AppUpdateUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    preferences: AppPreferences,
    libraryCount: Int,
    categories: List<Category>,
    sources: List<SourceInfo>,
    downloadTasks: List<DownloadTask>,
    isRestoringBackup: Boolean,
    appUpdateState: AppUpdateUiState = AppUpdateUiState(),
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetDefaultReaderMode: (ReaderMode) -> Unit,
    onSetGridColumns: (Int) -> Unit,
    onSetShowUnreadBadge: (Boolean) -> Unit,
    onSetKeepScreenOn: (Boolean) -> Unit,
    onSetPreferIndonesian: (Boolean) -> Unit,
    onToggleSource: (String, Boolean) -> Unit,
    onAddCategory: (String) -> Unit,
    onDeleteCategory: (Long) -> Unit,
    onClearCompletedDownloads: () -> Unit,
    onGenerateBackupJson: suspend () -> String,
    onRestoreBackupUri: (Uri) -> Unit,
    onOpenMigrateHub: () -> Unit = {},
    onCheckAppUpdate: (String) -> Unit = {},
    onInstallAppUpdate: () -> Unit = {},
    onDismissAppUpdate: () -> Unit = {},
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showReaderDialog by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var showDownloadsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUpdaterDialog by remember { mutableStateOf(false) }
    var updateUrlInput by remember(preferences.updateFeedUrl) {
        mutableStateOf(preferences.updateFeedUrl.ifBlank { "http://192.168.1.21:8787/version.json" })
    }
    var newCategoryName by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = onGenerateBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    onNotify("Backup JSON berhasil diekspor")
                }.onFailure { err ->
                    onNotify("Gagal ekspor: ${err.message}")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onRestoreBackupUri(uri)
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Tema Aplikasi") },
            text = {
                Column {
                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = preferences.themeMode == mode, onClick = {
                                onSetThemeMode(mode)
                                showThemeDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Tutup") } }
        )
    }

    if (showReaderDialog) {
        AlertDialog(
            onDismissRequest = { showReaderDialog = false },
            title = { Text("Mode Baca Default") },
            text = {
                Column {
                    ReaderMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetDefaultReaderMode(mode)
                                    showReaderDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = preferences.defaultReaderMode == mode, onClick = {
                                onSetDefaultReaderMode(mode)
                                showReaderDialog = false
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showReaderDialog = false }) { Text("Tutup") } }
        )
    }

    if (showCategoriesDialog) {
        AlertDialog(
            onDismissRequest = { showCategoriesDialog = false },
            title = { Text("Kelola Kategori") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("Kategori baru") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = {
                            if (newCategoryName.isNotBlank()) {
                                onAddCategory(newCategoryName)
                                newCategoryName = ""
                            }
                        }) { Text("Tambah") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(cat.name)
                            if (cat.id != 1L) {
                                IconButton(onClick = { onDeleteCategory(cat.id) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus kategori")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoriesDialog = false }) { Text("Selesai") } }
        )
    }

    if (showDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadsDialog = false },
            title = { Text("Antrean Unduhan") },
            text = {
                if (downloadTasks.isEmpty()) {
                    Text("Belum ada tugas unduhan saat ini.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        downloadTasks.take(10).forEach { task ->
                            Column {
                                Text(
                                    "${task.mangaTitle} — ${task.chapterName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val statusLabel = when (task.status) {
                                    DownloadStatus.QUEUED -> "Menunggu..."
                                    DownloadStatus.DOWNLOADING -> "Mengunduh ${task.progress}/${task.total}"
                                    DownloadStatus.COMPLETED -> "Selesai (${task.total} hal.)"
                                    DownloadStatus.FAILED -> "Gagal: ${task.error ?: "-"}"
                                }
                                Text(statusLabel, style = MaterialTheme.typography.labelSmall)
                                if (task.status == DownloadStatus.DOWNLOADING && task.total > 0) {
                                    LinearProgressIndicator(
                                        progress = { task.progress.toFloat() / task.total.toFloat() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadsDialog = false }) { Text("Tutup") }
            },
            dismissButton = {
                if (downloadTasks.isNotEmpty()) {
                    TextButton(onClick = onClearCompletedDownloads) { Text("Bersihkan Selesai") }
                }
            }
        )
    }

    if (isRestoringBackup) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Memulihkan Backup") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Sedang memproses kategori, komik, dan chapter...")
                }
            },
            confirmButton = {}
        )
    }

    if (showUpdaterDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!appUpdateState.isDownloading) {
                    showUpdaterDialog = false
                    onDismissAppUpdate()
                }
            },
            title = { Text("Pembaruan Aplikasi (OTA)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = updateUrlInput,
                        onValueChange = { updateUrlInput = it },
                        label = { Text("URL version.json / Repo GitHub") },
                        placeholder = { Text("http://192.168.1.21:8787/version.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Bisa pakai URL WiFi Mac lokal (scripts/serve-ota.sh) atau URL repo GitHub Releases.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when {
                        appUpdateState.isChecking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                                Text("Memeriksa pembaruan...")
                            }
                        }
                        appUpdateState.isDownloading -> {
                            Column {
                                Text("Mengunduh APK (${appUpdateState.downloadProgress}%)...")
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { appUpdateState.downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        appUpdateState.error != null -> {
                            Text(
                                text = appUpdateState.error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        appUpdateState.releaseInfo != null -> {
                            val rel = appUpdateState.releaseInfo
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Tersedia v${rel.versionName} (code ${rel.versionCode})",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (rel.changelog.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rel.changelog,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (appUpdateState.releaseInfo != null && !appUpdateState.isDownloading) {
                    Button(onClick = onInstallAppUpdate) {
                        Text("Unduh & Pasang")
                    }
                } else {
                    Button(
                        enabled = !appUpdateState.isChecking && !appUpdateState.isDownloading && updateUrlInput.isNotBlank(),
                        onClick = { onCheckAppUpdate(updateUrlInput) }
                    ) {
                        Text("Cek Pembaruan")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !appUpdateState.isDownloading,
                    onClick = {
                        showUpdaterDialog = false
                        onDismissAppUpdate()
                    }
                ) { Text("Tutup") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Tentang MoonReader") },
            text = {
                Text(
                    "MoonReader v1.0.0\n\n" +
                        "Pembaca komik & manga Android native (Kotlin + Jetpack Compose) dengan arsitektur Source modular.\n\n" +
                        "Sumber bawaan:\n" +
                        "• MangaDex Public API v5\n" +
                        "• Pembaca Arsip Lokal (.cbz / .zip)\n" +
                        "• Moon Demo Catalog (Offline)"
                )
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("OK") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Lainnya", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MoonReader",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$libraryCount komik di pustaka • ${categories.size} kategori",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                SectionLabel("Manajemen")
                MoreItem(
                    icon = Icons.Outlined.Download,
                    title = "Antrean Unduhan",
                    subtitle = "${downloadTasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }} aktif",
                    onClick = { showDownloadsDialog = true }
                )
                MoreItem(
                    icon = Icons.Outlined.Category,
                    title = "Kategori Perpustakaan",
                    subtitle = "${categories.size} kategori",
                    onClick = { showCategoriesDialog = true }
                )
                MoreItem(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "Migrasi Sumber Komik",
                    subtitle = "Cari & pindahkan komik ke sumber lain yang aktif",
                    onClick = onOpenMigrateHub
                )
                MoreItem(
                    icon = Icons.Outlined.Backup,
                    title = "Ekspor Backup JSON",
                    subtitle = "Simpan koleksi, kategori, dan progres baca",
                    onClick = { exportLauncher.launch("moonreader-backup.json") }
                )
                MoreItem(
                    icon = Icons.Outlined.Restore,
                    title = "Pulihkan Backup (JSON / .tachibk)",
                    subtitle = "Impor backup MoonReader (.json) atau Tachiyomi/Mihon (.tachibk)",
                    onClick = { importLauncher.launch(arrayOf("*/*")) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }

            item {
                SectionLabel("Tampilan & Reader")
                MoreItem(
                    icon = Icons.Outlined.Palette,
                    title = "Tema Aplikasi",
                    subtitle = preferences.themeMode.label,
                    onClick = { showThemeDialog = true }
                )
                MoreItem(
                    icon = Icons.Outlined.Book,
                    title = "Mode Baca Default",
                    subtitle = preferences.defaultReaderMode.label,
                    onClick = { showReaderDialog = true }
                )
                MoreItem(
                    icon = Icons.Outlined.Settings,
                    title = "Jumlah Kolom Grid",
                    subtitle = "${preferences.gridColumns} kolom (ketuk untuk ubah)",
                    onClick = {
                        val next = if (preferences.gridColumns >= 4) 2 else preferences.gridColumns + 1
                        onSetGridColumns(next)
                    }
                )
                ListItem(
                    headlineContent = { Text("Tampilkan lencana belum dibaca") },
                    trailingContent = {
                        Switch(
                            checked = preferences.showUnreadBadge,
                            onCheckedChange = onSetShowUnreadBadge
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Layar tetap menyala saat membaca") },
                    trailingContent = {
                        Switch(
                            checked = preferences.keepScreenOnInReader,
                            onCheckedChange = onSetKeepScreenOn
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Prioritaskan chapter Bahasa Indonesia (MangaDex)") },
                    trailingContent = {
                        Switch(
                            checked = preferences.preferIndonesianLanguage,
                            onCheckedChange = onSetPreferIndonesian
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }

            item {
                SectionLabel("Sumber Modular Aktif")
                sources.forEach { src ->
                    ListItem(
                        headlineContent = { Text(src.name) },
                        supportingContent = { Text(src.description) },
                        trailingContent = {
                            Switch(
                                checked = src.isEnabled,
                                onCheckedChange = { enabled -> onToggleSource(src.id, enabled) }
                            )
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                MoreItem(
                    icon = Icons.Outlined.SystemUpdate,
                    title = "Pembaruan Aplikasi (Tanpa Kabel)",
                    subtitle = preferences.updateFeedUrl.ifBlank { "Cek & pasang APK terbaru via WiFi / GitHub" },
                    onClick = { showUpdaterDialog = true }
                )
                MoreItem(
                    icon = Icons.Outlined.Info,
                    title = "Tentang MoonReader",
                    subtitle = "v1.0.0 • Arsitektur Tachiyomi-style",
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun MoreItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
