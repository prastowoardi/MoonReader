package com.moonreader.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moonreader.app.data.local.AppPreferences
import com.moonreader.app.domain.model.Category
import com.moonreader.app.domain.model.LibraryDisplayMode
import com.moonreader.app.domain.model.Manga
import com.moonreader.app.ui.components.EmptyStateView
import com.moonreader.app.ui.components.MangaCoverCard
import com.moonreader.app.ui.components.MangaListRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    library: List<Manga>,
    categories: List<Category>,
    preferences: AppPreferences,
    isRefreshing: Boolean,
    onRefreshLibrary: () -> Unit,
    onMangaClick: (String) -> Unit,
    onGoToBrowse: () -> Unit,
    onChangeDisplayMode: (LibraryDisplayMode) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val safeCategories = categories.ifEmpty { listOf(Category(1L, "Umum", 0)) }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { safeCategories.size }
    )
    val clampedTab = pagerState.currentPage.coerceIn(0, (safeCategories.size - 1).coerceAtLeast(0))

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (searchActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchActive = false
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Tutup pencarian")
                        }
                    },
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Cari judul di pustaka...") },
                            singleLine = true,
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
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Hapus teks")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pustaka", fontWeight = FontWeight.SemiBold)
                            if (library.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = "${library.size}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = "Cari")
                        }
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "Filter & Tampilan")
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (unreadOnly) "✓ Hanya belum dibaca" else "Hanya belum dibaca") },
                                    onClick = {
                                        unreadOnly = !unreadOnly
                                        showFilterMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.GridView, null) },
                                    text = { Text("Grid Padat") },
                                    onClick = {
                                        onChangeDisplayMode(LibraryDisplayMode.COMPACT_GRID)
                                        showFilterMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.GridView, null) },
                                    text = { Text("Grid Nyaman") },
                                    onClick = {
                                        onChangeDisplayMode(LibraryDisplayMode.COMFORTABLE_GRID)
                                        showFilterMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ViewList, null) },
                                    text = { Text("Tampilan Daftar") },
                                    onClick = {
                                        onChangeDisplayMode(LibraryDisplayMode.LIST)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = onRefreshLibrary, enabled = !isRefreshing) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Perbarui semua")
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
            if (safeCategories.size > 1) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = clampedTab,
                    edgePadding = 12.dp
                ) {
                    safeCategories.forEachIndexed { index, category ->
                        val count = library.count { it.categoryId == category.id }
                        Tab(
                            selected = clampedTab == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(
                                    text = if (count > 0) "${category.name} ($count)" else category.name,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            if (unreadOnly) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { unreadOnly = false },
                        label = { Text("Belum dibaca saja") },
                        trailingIcon = { Icon(Icons.Outlined.Close, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pageCategory = safeCategories.getOrNull(pageIndex)
                val pageFiltered = remember(library, pageCategory, searchQuery, unreadOnly, safeCategories.size) {
                    library.filter { manga ->
                        val catMatch = pageCategory == null || manga.categoryId == pageCategory.id || safeCategories.size == 1
                        val queryMatch = searchQuery.isBlank() || manga.title.contains(searchQuery, ignoreCase = true)
                        val unreadMatch = !unreadOnly || manga.unreadCount > 0
                        catMatch && queryMatch && unreadMatch
                    }
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefreshLibrary,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pageFiltered.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Outlined.CollectionsBookmark,
                            title = if (library.isEmpty()) "Pustaka masih kosong" else "Tidak ada komik di kategori ini",
                            subtitle = if (library.isEmpty()) {
                                "Jelajahi MangaDex, impor file .cbz/.zip, atau pulihkan backup .tachibk."
                            } else {
                                "Geser kanan/kiri untuk kategori lain atau reset filter pencarian."
                            },
                            action = {
                                if (library.isEmpty()) {
                                    Button(onClick = onGoToBrowse) {
                                        Text("Jelajahi Sumber")
                                    }
                                }
                            }
                        )
                    } else if (preferences.libraryDisplayMode == LibraryDisplayMode.LIST) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(pageFiltered, key = { it.id }) { manga ->
                                MangaListRow(
                                    manga = manga,
                                    showUnreadBadge = preferences.showUnreadBadge,
                                    onClick = { onMangaClick(manga.id) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(preferences.gridColumns),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(pageFiltered, key = { it.id }) { manga ->
                                MangaCoverCard(
                                    manga = manga,
                                    showUnreadBadge = preferences.showUnreadBadge,
                                    compactOverlayTitle = preferences.libraryDisplayMode == LibraryDisplayMode.COMPACT_GRID,
                                    onClick = { onMangaClick(manga.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
