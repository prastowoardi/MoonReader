package com.moonreader.app.ui.screens.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.SubcomposeAsyncImage
import com.moonreader.app.domain.model.ReaderMode
import com.moonreader.app.ui.state.ReaderUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    keepScreenOn: Boolean,
    brightnessOverride: Float?,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onSwitchMode: (ReaderMode) -> Unit,
    onAdjacentChapter: (Boolean) -> Unit,
    onSelectChapter: (String) -> Unit = {},
    onChangeBrightness: (Float?) -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showControls by remember { mutableStateOf(false) }
    var showModeMenu by remember { mutableStateOf(false) }
    var showChapterMenu by remember { mutableStateOf(false) }
    var showBrightnessBar by remember { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableFloatStateOf(1600f) }

    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(showControls) {
        val window = (context as? Activity)?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (showControls) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(brightnessOverride) {
        val activity = context as? Activity
        val window = activity?.window
        val original = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window != null && brightnessOverride != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = brightnessOverride
            }
        }
        onDispose {
            if (window != null) {
                window.attributes = window.attributes.apply {
                    screenBrightness = original
                }
            }
        }
    }

    val totalPages = state.pages.size.coerceAtLeast(1)
    val currentPage = state.currentPageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val progressFraction = ((currentPage + 1).toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.height > 0) viewportHeightPx = size.height.toFloat()
            }
    ) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Memuat halaman...", color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Kembali")
                        }
                    }
                }
            }
            state.pages.isNotEmpty() -> {
                if (state.readerMode == ReaderMode.WEBTOON) {
                    val listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = currentPage
                    )

                    LaunchedEffect(state.chapter?.id) {
                        val target = currentPage
                        if (listState.firstVisibleItemIndex != target) {
                            listState.scrollToItem(target)
                        }
                    }

                    LaunchedEffect(listState, state.chapter?.id) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collect { idx -> onPageChanged(idx) }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = state.pages,
                            key = { idx, p -> "${state.chapter?.id}_${idx}_${p.imageUrl}" }
                        ) { idx, page ->
                            ReaderPageImage(
                                imageUrl = page.imageUrl,
                                pageNumber = idx + 1,
                                isWebtoon = true,
                                viewportHeightPx = viewportHeightPx,
                                onCenterTap = { showControls = !showControls },
                                onWebtoonTapUp = {
                                    scope.launch {
                                        listState.animateScrollBy(-viewportHeightPx * 0.70f)
                                    }
                                },
                                onWebtoonTapDown = {
                                    scope.launch {
                                        listState.animateScrollBy(viewportHeightPx * 0.70f)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    val pagerState = rememberPagerState(
                        initialPage = currentPage,
                        pageCount = { state.pages.size }
                    )

                    LaunchedEffect(state.chapter?.id) {
                        val target = currentPage
                        if (pagerState.currentPage != target) {
                            pagerState.scrollToPage(target)
                        }
                    }

                    LaunchedEffect(pagerState, state.chapter?.id) {
                        snapshotFlow { pagerState.currentPage }
                            .distinctUntilChanged()
                            .collect { page -> onPageChanged(page) }
                    }

                    val reverseLayout = state.readerMode == ReaderMode.RTL

                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = reverseLayout,
                        modifier = Modifier.fillMaxSize()
                    ) { pageIdx ->
                        ReaderPageImage(
                            imageUrl = state.pages[pageIdx].imageUrl,
                            pageNumber = pageIdx + 1,
                            isWebtoon = false,
                            viewportHeightPx = viewportHeightPx,
                            onCenterTap = { showControls = !showControls },
                            onLeftEdgeTap = {
                                scope.launch {
                                    val target = if (reverseLayout) pageIdx + 1 else pageIdx - 1
                                    if (target in state.pages.indices) {
                                        pagerState.animateScrollToPage(target)
                                    }
                                }
                            },
                            onRightEdgeTap = {
                                scope.launch {
                                    val target = if (reverseLayout) pageIdx - 1 else pageIdx + 1
                                    if (target in state.pages.indices) {
                                        pagerState.animateScrollToPage(target)
                                    }
                                }
                            }
                        )
                    }
                }

                // Sleek minimal reading progress HUD when controls are hidden
                AnimatedVisibility(
                    visible = !showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xCC12141A),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${currentPage + 1} / ${state.pages.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${(progressFraction * 100).toInt()}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }
            }
        }

        // Top Control Bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color(0xEB151821),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.manga?.title ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.chapter?.name ?: "",
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (state.chapters.size > 1) {
                        Box {
                            IconButton(onClick = { showChapterMenu = true }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.List,
                                    contentDescription = "Daftar chapter",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showChapterMenu,
                                onDismissRequest = { showChapterMenu = false }
                            ) {
                                state.chapters.take(25).forEach { ch ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (ch.id == state.chapter?.id) "▶ ${ch.name}" else ch.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            showChapterMenu = false
                                            onSelectChapter(ch.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Floating HUD + Modern Progress Scrubber
        AnimatedVisibility(
            visible = showControls && state.pages.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                color = Color(0xEB151821),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (showBrightnessBar) {
                        Surface(
                            color = Color.White.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Brightness6,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Slider(
                                    value = brightnessOverride ?: 0.7f,
                                    onValueChange = { onChangeBrightness(it) },
                                    valueRange = 0.1f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${((brightnessOverride ?: 0.7f) * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }

                    // Page badge & chapter percentage header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Halaman ${currentPage + 1} dari ${state.pages.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${(progressFraction * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Chapter prev/next + smooth slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onAdjacentChapter(false) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.SkipPrevious,
                                    contentDescription = "Chapter sebelumnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { value -> onPageChanged(value.toInt()) },
                            valueRange = 0f..(state.pages.lastIndex.coerceAtLeast(1)).toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        )

                        Surface(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable { onAdjacentChapter(true) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.SkipNext,
                                    contentDescription = "Chapter berikutnya",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Action Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        Box {
                            ReaderActionPill(
                                icon = when (state.readerMode) {
                                    ReaderMode.WEBTOON -> Icons.Outlined.ViewDay
                                    ReaderMode.RTL -> Icons.AutoMirrored.Outlined.MenuBook
                                    ReaderMode.LTR -> Icons.Outlined.SwapHoriz
                                },
                                label = when (state.readerMode) {
                                    ReaderMode.WEBTOON -> "Webtoon"
                                    ReaderMode.RTL -> "Manga RTL"
                                    ReaderMode.LTR -> "Komik LTR"
                                },
                                active = false,
                                onClick = { showModeMenu = true }
                            )
                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false }
                            ) {
                                ReaderMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (state.readerMode == mode) "✓ ${mode.label}" else mode.label
                                            )
                                        },
                                        onClick = {
                                            onSwitchMode(mode)
                                            showModeMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        ReaderActionPill(
                            icon = Icons.Outlined.Brightness6,
                            label = "Kecerahan",
                            active = showBrightnessBar,
                            onClick = { showBrightnessBar = !showBrightnessBar }
                        )

                        if (state.chapters.size > 1) {
                            ReaderActionPill(
                                icon = Icons.AutoMirrored.Outlined.List,
                                label = "Chapter",
                                active = showChapterMenu,
                                onClick = { showChapterMenu = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderActionPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50),
        border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ReaderPageImage(
    imageUrl: String,
    pageNumber: Int,
    isWebtoon: Boolean,
    viewportHeightPx: Float,
    onCenterTap: () -> Unit,
    onWebtoonTapUp: () -> Unit = {},
    onWebtoonTapDown: () -> Unit = {},
    onLeftEdgeTap: () -> Unit = {},
    onRightEdgeTap: () -> Unit = {}
) {
    var scale by remember(imageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(imageUrl) { mutableStateOf(Offset.Zero) }
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val gestureModifier = if (isWebtoon) {
        // Single-tap only in Webtoon mode so tap-to-scroll responds instantly with zero 300ms delay
        Modifier.pointerInput(imageUrl, viewportHeightPx) {
            detectTapGestures(
                onTap = { tapOffset ->
                    val rootY = coords?.localToRoot(tapOffset)?.y ?: tapOffset.y
                    val ratio = (rootY / viewportHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    when {
                        ratio < 0.28f -> onWebtoonTapUp()
                        ratio > 0.72f -> onWebtoonTapDown()
                        else -> onCenterTap()
                    }
                }
            )
        }
    } else {
        Modifier
            .pointerInput(imageUrl) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1.1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.2f
                        }
                    },
                    onTap = { tapOffset ->
                        val width = size.width
                        when {
                            tapOffset.x < width * 0.28f -> onLeftEdgeTap()
                            tapOffset.x > width * 0.72f -> onRightEdgeTap()
                            else -> onCenterTap()
                        }
                    }
                )
            }
            .pointerInput(imageUrl, scale) {
                if (scale > 1f) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val nextScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = nextScale
                        offset = if (nextScale <= 1.02f) Offset.Zero else offset + pan
                    }
                }
            }
    }

    Box(
        modifier = (if (isWebtoon) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .onGloballyPositioned { coords = it }
            .then(gestureModifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "Halaman $pageNumber",
            contentScale = if (isWebtoon) ContentScale.FillWidth else ContentScale.Fit,
            modifier = if (isWebtoon) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isWebtoon) 420.dp else 560.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hal. $pageNumber",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Gagal memuat halaman $pageNumber",
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        )
    }
}
