package com.moonreader.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moonreader.app.ui.screens.browse.BrowseScreen
import com.moonreader.app.ui.screens.browse.CatalogBrowserScreen
import com.moonreader.app.ui.screens.detail.MangaDetailScreen
import com.moonreader.app.ui.screens.history.HistoryScreen
import com.moonreader.app.ui.screens.library.LibraryScreen
import com.moonreader.app.ui.screens.migrate.MigrateHubScreen
import com.moonreader.app.ui.screens.migrate.MigrateSearchScreen
import com.moonreader.app.ui.screens.more.MoreScreen
import com.moonreader.app.ui.screens.reader.ReaderScreen
import com.moonreader.app.ui.screens.updates.UpdatesScreen
import com.moonreader.app.ui.state.MainViewModel
import kotlinx.coroutines.launch

sealed class TopTab(val route: String, val label: String, val icon: ImageVector) {
    data object Library : TopTab("library", "Pustaka", Icons.Outlined.CollectionsBookmark)
    data object Updates : TopTab("updates", "Pembaruan", Icons.Outlined.NewReleases)
    data object History : TopTab("history", "Riwayat", Icons.Outlined.History)
    data object Browse : TopTab("browse", "Jelajah", Icons.Outlined.Explore)
    data object More : TopTab("more", "Lainnya", Icons.Outlined.MoreHoriz)
}

private val topTabs = listOf(
    TopTab.Library,
    TopTab.Updates,
    TopTab.History,
    TopTab.Browse,
    TopTab.More
)

@Composable
fun MoonNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val preferences by viewModel.preferences.collectAsState()
    val library by viewModel.library.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val history by viewModel.history.collectAsState()
    val updates by viewModel.updates.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val isRefreshingLibrary by viewModel.isRefreshingLibrary.collectAsState()
    val isRefreshingDetail by viewModel.isRefreshingDetail.collectAsState()
    val isRestoringBackup by viewModel.isRestoringBackup.collectAsState()
    val browseState by viewModel.browseState.collectAsState()
    val readerState by viewModel.readerState.collectAsState()
    val migrateState by viewModel.migrateState.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in topTabs.map { it.route }

    fun navigateToDetail(mangaId: String) {
        navController.navigate("detail/${Uri.encode(mangaId)}")
    }

    fun navigateToReader(mangaId: String, chapterId: String) {
        viewModel.openReaderDirect(mangaId, chapterId)
        navController.navigate("reader/${Uri.encode(mangaId)}/${Uri.encode(chapterId)}")
    }

    fun navigateToSourceCatalog(sourceId: String) {
        viewModel.openSourceCatalog(sourceId)
        navController.navigate("catalog/${Uri.encode(sourceId)}")
    }

    fun navigateToMigrateSearch(mangaId: String) {
        viewModel.openMigrationSearch(mangaId)
        navController.navigate("migrate_search/${Uri.encode(mangaId)}")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            val snackModifier = if (showBottomBar) {
                Modifier.imePadding()
            } else {
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = snackModifier
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(TopTab.Library.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopTab.Library.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopTab.Library.route) {
                LibraryScreen(
                    library = library,
                    categories = categories,
                    preferences = preferences,
                    isRefreshing = isRefreshingLibrary,
                    onRefreshLibrary = viewModel::refreshLibrary,
                    onMangaClick = ::navigateToDetail,
                    onGoToBrowse = {
                        navController.navigate(TopTab.Browse.route) {
                            popUpTo(TopTab.Library.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onChangeDisplayMode = viewModel::setLibraryDisplayMode
                )
            }

            composable(TopTab.Updates.route) {
                UpdatesScreen(
                    updates = updates,
                    isRefreshing = isRefreshingLibrary,
                    onRefresh = viewModel::refreshLibrary,
                    onOpenChapter = ::navigateToReader,
                    onOpenManga = ::navigateToDetail
                )
            }

            composable(TopTab.History.route) {
                HistoryScreen(
                    history = history,
                    onResumeChapter = ::navigateToReader,
                    onOpenManga = ::navigateToDetail,
                    onRemoveEntry = viewModel::removeHistoryEntry,
                    onClearAll = viewModel::clearHistory
                )
            }

            composable(TopTab.Browse.route) {
                BrowseScreen(
                    sources = sources,
                    onOpenSource = ::navigateToSourceCatalog,
                    onImportLocalCbz = viewModel::importLocalCbz,
                    onRestoreBackupUri = viewModel::restoreBackupFromUri,
                    onAddOpdsServer = viewModel::addOpdsServer,
                    onRemoveOpdsServer = viewModel::removeOpdsServer
                )
            }

            composable(
                route = "catalog/{sourceId}",
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType })
            ) {
                CatalogBrowserScreen(
                    state = browseState,
                    libraryIds = remember(library) { library.map { it.id }.toSet() },
                    onBack = {
                        viewModel.closeSourceCatalog()
                        navController.popBackStack()
                    },
                    onQueryChange = viewModel::updateCatalogSearchQuery,
                    onSearchSubmit = viewModel::submitCatalogSearch,
                    onSwitchMode = viewModel::switchCatalogMode,
                    onLoadNextPage = viewModel::loadNextCatalogPage,
                    onImportLocalCbz = viewModel::importLocalCbz,
                    onSelectManga = { manga ->
                        viewModel.prepareBrowseManga(manga) {
                            navigateToDetail(manga.id)
                        }
                    }
                )
            }

            composable(TopTab.More.route) {
                MoreScreen(
                    preferences = preferences,
                    libraryCount = library.size,
                    categories = categories,
                    sources = sources,
                    downloadTasks = downloadTasks,
                    isRestoringBackup = isRestoringBackup,
                    appUpdateState = appUpdateState,
                    onSetThemeMode = viewModel::setThemeMode,
                    onSetDefaultReaderMode = viewModel::setDefaultReaderMode,
                    onSetGridColumns = viewModel::setGridColumns,
                    onSetShowUnreadBadge = viewModel::setShowUnreadBadge,
                    onSetKeepScreenOn = viewModel::setKeepScreenOn,
                    onSetPreferIndonesian = viewModel::setPreferIndonesian,
                    onToggleSource = viewModel::toggleSourceEnabled,
                    onAddCategory = viewModel::addCategory,
                    onDeleteCategory = viewModel::deleteCategory,
                    onClearCompletedDownloads = viewModel::clearCompletedDownloads,
                    onGenerateBackupJson = viewModel::generateBackupJson,
                    onRestoreBackupUri = viewModel::restoreBackupFromUri,
                    onOpenMigrateHub = { navController.navigate("migrate_hub") },
                    onCheckAppUpdate = viewModel::checkAppUpdate,
                    onInstallAppUpdate = viewModel::downloadAndInstallAppUpdate,
                    onDismissAppUpdate = viewModel::clearAppUpdateState,
                    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )
            }

            composable("migrate_hub") {
                MigrateHubScreen(
                    library = library,
                    onBack = { navController.popBackStack() },
                    onMigrateManga = ::navigateToMigrateSearch
                )
            }

            composable(
                route = "migrate_search/{mangaId}",
                arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
            ) {
                MigrateSearchScreen(
                    state = migrateState,
                    onBack = { navController.popBackStack() },
                    onQueryChange = viewModel::updateMigrationQuery,
                    onSearchSubmit = viewModel::runMigrationSearch,
                    onSelectTarget = viewModel::selectMigrationTarget,
                    onDismissTarget = viewModel::dismissMigrationTarget,
                    onConfirmMigration = { copyRead, keepCategory, deleteOld ->
                        val oldId = migrateState.sourceManga?.id
                        viewModel.confirmMigration(copyRead, keepCategory, deleteOld) { newId ->
                            navController.navigate("detail/${Uri.encode(newId)}") {
                                if (oldId != null) {
                                    popUpTo("detail/${Uri.encode(oldId)}") { inclusive = true }
                                } else {
                                    popUpTo("migrate_search/{mangaId}") { inclusive = true }
                                }
                            }
                        }
                    }
                )
            }

            composable(
                route = "detail/{mangaId}",
                arguments = listOf(navArgument("mangaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val mangaId = Uri.decode(backStackEntry.arguments?.getString("mangaId").orEmpty())
                val manga by viewModel.observeManga(mangaId).collectAsState(initial = null)
                val chapters by viewModel.observeChapters(mangaId).collectAsState(initial = emptyList())

                MangaDetailScreen(
                    manga = manga,
                    chapters = chapters,
                    categories = categories,
                    downloadTasks = downloadTasks,
                    isRefreshing = isRefreshingDetail,
                    onBack = { navController.popBackStack() },
                    onRefresh = { viewModel.refreshMangaDetail(mangaId) },
                    onToggleLibrary = { catId -> viewModel.toggleLibrary(mangaId, catId) },
                    onMoveCategory = { catId -> viewModel.moveMangaToCategory(mangaId, catId) },
                    onOpenChapter = { chapterId -> navigateToReader(mangaId, chapterId) },
                    onToggleBookmark = viewModel::toggleBookmark,
                    onToggleRead = viewModel::markChapterRead,
                    onMarkAllRead = { read -> viewModel.markAllChaptersRead(mangaId, read) },
                    onDownloadChapter = viewModel::enqueueDownload,
                    onDeleteDownload = { chapterId -> viewModel.deleteDownload(mangaId, chapterId) },
                    onMigrateClick = { navigateToMigrateSearch(mangaId) }
                )
            }

            composable(
                route = "reader/{mangaId}/{chapterId}",
                arguments = listOf(
                    navArgument("mangaId") { type = NavType.StringType },
                    navArgument("chapterId") { type = NavType.StringType }
                )
            ) {
                ReaderScreen(
                    state = readerState,
                    keepScreenOn = preferences.keepScreenOnInReader,
                    brightnessOverride = preferences.readerBrightnessOverride,
                    onBack = { navController.popBackStack() },
                    onPageChanged = viewModel::onReaderPageChanged,
                    onSwitchMode = viewModel::switchReaderMode,
                    onAdjacentChapter = viewModel::openAdjacentChapter,
                    onSelectChapter = { chapterId ->
                        val mangaId = readerState.manga?.id ?: return@ReaderScreen
                        viewModel.openReaderDirect(mangaId, chapterId)
                    },
                    onChangeBrightness = viewModel::setReaderBrightness
                )
            }
        }
    }
}
