package com.moonreader.app.ui.screens.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.Chapter
import com.moonreader.app.domain.model.DownloadStatus
import com.moonreader.app.domain.model.DownloadTask
import com.moonreader.app.domain.model.Manga
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MangaDetailScreen(
    manga: Manga?,
    chapters: List<Chapter>,
    categories: List<Category>,
    downloadTasks: List<DownloadTask>,
    isRefreshing: Boolean = false,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleLibrary: (Long) -> Unit,
    onMoveCategory: (Long) -> Unit,
    onOpenChapter: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onToggleRead: (String, Boolean) -> Unit,
    onMarkAllRead: (Boolean) -> Unit,
    onDownloadChapter: (Manga, Chapter) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onMigrateClick: () -> Unit = {},
    trackedCount: Int = 0,
    onTrackingClick: () -> Unit = {}
) {
    var sortDescending by remember { mutableStateOf(true) }
    var unreadOnly by remember { mutableStateOf(false) }
    var bookmarkedOnly by remember { mutableStateOf(false) }
    var expandDescription by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showChapterFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(manga?.id) {
        if (manga != null && chapters.isEmpty()) {
            onRefresh()
        }
    }

    val filteredChapters = remember(chapters, sortDescending, unreadOnly, bookmarkedOnly) {
        chapters
            .filter { ch ->
                (!unreadOnly || !ch.read) && (!bookmarkedOnly || ch.bookmark)
            }
            .let { list ->
                if (sortDescending) list.sortedByDescending { it.chapterNumber }
                else list.sortedBy { it.chapterNumber }
            }
    }

    val resumeChapter = remember(chapters) {
        chapters.sortedBy { it.chapterNumber }.firstOrNull { !it.read }
            ?: chapters.minByOrNull { it.chapterNumber }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yy", Locale("id", "ID")) }

    if (showCategoryDialog && manga != null) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Pilih Kategori") },
            text = {
                Column {
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMoveCategory(cat.id)
                                    showCategoryDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = manga.categoryId == cat.id,
                                onClick = {
                                    onMoveCategory(cat.id)
                                    showCategoryDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cat.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategoryDialog = false }) { Text("Tutup") }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Kembali")
                    }
                },
                title = {
                    Text(
                        text = manga?.title ?: "Detail Komik",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = {
                        val allRead = chapters.isNotEmpty() && chapters.all { it.read }
                        onMarkAllRead(!allRead)
                    }) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = "Tandai semua terbaca")
                    }
                }
            )
        },
        floatingActionButton = {
            if (resumeChapter != null) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenChapter(resumeChapter.id) },
                    icon = { Icon(Icons.Outlined.PlayArrow, contentDescription = null) },
                    text = {
                        val anyStarted = chapters.any { it.read || it.lastPageRead > 0 }
                        Text(if (anyStarted) "Lanjutkan" else "Mulai Baca")
                    }
                )
            }
        }
    ) { innerPadding ->
        if (manga == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val surfaceColor = MaterialTheme.colorScheme.surface

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 92.dp)
            ) {
            item(key = "hero") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (manga.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = manga.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .alpha(0.18f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, surfaceColor)
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        AsyncImage(
                            model = manga.coverUrl.ifBlank { null },
                            contentDescription = manga.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(108.dp)
                                .height(158.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (manga.author.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = manga.author,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${manga.status.label} • ${manga.sourceId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Tachiyomi-style Action Buttons Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DetailActionButton(
                        icon = if (manga.inLibrary) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                        label = if (manga.inLibrary) "Di Pustaka" else "Simpan",
                        active = manga.inLibrary,
                        onClick = { onToggleLibrary(categories.firstOrNull()?.id ?: 1L) }
                    )
                    if (manga.inLibrary) {
                        DetailActionButton(
                            icon = Icons.Outlined.Category,
                            label = categories.firstOrNull { it.id == manga.categoryId }?.name ?: "Kategori",
                            active = true,
                            onClick = { showCategoryDialog = true }
                        )
                    }
                    DetailActionButton(
                        icon = Icons.Outlined.Refresh,
                        label = "Segarkan",
                        active = false,
                        onClick = onRefresh
                    )
                    DetailActionButton(
                        icon = Icons.Outlined.SwapVert,
                        label = "Migrasi",
                        active = false,
                        onClick = onMigrateClick
                    )
                    DetailActionButton(
                        icon = Icons.Outlined.Sync,
                        label = if (trackedCount > 0) "Track ($trackedCount)" else "Track",
                        active = trackedCount > 0,
                        onClick = onTrackingClick
                    )
                    Box {
                        DetailActionButton(
                            icon = Icons.Outlined.FilterList,
                            label = "Filter",
                            active = unreadOnly || bookmarkedOnly,
                            onClick = { showChapterFilterMenu = true }
                        )
                        DropdownMenu(
                            expanded = showChapterFilterMenu,
                            onDismissRequest = { showChapterFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (unreadOnly) "✓ Belum dibaca" else "Belum dibaca") },
                                onClick = {
                                    unreadOnly = !unreadOnly
                                    showChapterFilterMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (bookmarkedOnly) "✓ Di-bookmark" else "Di-bookmark") },
                                onClick = {
                                    bookmarkedOnly = !bookmarkedOnly
                                    showChapterFilterMenu = false
                                }
                            )
                        }
                    }
                }

                // Description & Genres
                if (manga.description.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                            .clickable { expandDescription = !expandDescription }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = manga.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expandDescription) 40 else 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (expandDescription) "Ringkas" else "Selengkapnya",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (manga.genres.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        manga.genres.take(8).forEach { genre ->
                            AssistChip(
                                onClick = {},
                                label = { Text(genre, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val unreadCount = chapters.count { !it.read }
                    Text(
                        text = "${filteredChapters.size} Chapter" +
                            if (unreadCount > 0) " ($unreadCount belum dibaca)" else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { sortDescending = !sortDescending }) {
                        Icon(Icons.Outlined.SwapVert, contentDescription = "Urutkan chapter")
                    }
                }
            }

            if (isRefreshing && filteredChapters.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(filteredChapters, key = { it.id }) { chapter ->
                val task = downloadTasks.firstOrNull { it.chapterId == chapter.id }
                ChapterRow(
                    chapter = chapter,
                    downloadTask = task,
                    dateText = dateFormat.format(Date(chapter.uploadedAt)),
                    onClick = { onOpenChapter(chapter.id) },
                    onToggleBookmark = { onToggleBookmark(chapter.id) },
                    onToggleRead = { onToggleRead(chapter.id, !chapter.read) },
                    onDownloadClick = {
                        if (chapter.downloaded) {
                            onDeleteDownload(chapter.id)
                        } else {
                            onDownloadChapter(manga, chapter)
                        }
                    }
                )
            }
        }
        }
    }
}

@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    downloadTask: DownloadTask?,
    dateText: String,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleRead: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (chapter.read) 0.50f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (chapter.bookmark) {
            Icon(
                imageVector = Icons.Outlined.Bookmark,
                contentDescription = "Bookmark",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 4.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (chapter.read) FontWeight.Normal else FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                append(dateText)
                if (chapter.scanlator.isNotBlank()) append(" • ").append(chapter.scanlator)
                if (!chapter.read && chapter.lastPageRead > 0) {
                    append(" • Hal. ").append(chapter.lastPageRead + 1)
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onToggleBookmark, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = if (chapter.bookmark) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Bookmark",
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onToggleRead, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Tandai terbaca",
                tint = if (chapter.read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onDownloadClick, modifier = Modifier.size(38.dp)) {
            when {
                downloadTask?.status == DownloadStatus.DOWNLOADING || downloadTask?.status == DownloadStatus.QUEUED -> {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                chapter.downloaded -> {
                    Icon(
                        imageVector = Icons.Outlined.DownloadDone,
                        contentDescription = "Hapus unduhan",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                else -> {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = "Unduh chapter",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
