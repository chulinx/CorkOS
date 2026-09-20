package com.winlator.cmod.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap as composeAsImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.ui.components.CircularIconButton
import com.winlator.cmod.ui.components.EmptyHint
import com.winlator.cmod.ui.components.SourceCard
import com.winlator.cmod.ui.components.TextTabRow
import com.winlator.cmod.ui.theme.LocalWinlatorAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal fun Bitmap.asImageBitmap(): ImageBitmap = this.composeAsImageBitmap()

@Composable
internal fun LibraryRoot(
    items: List<LibraryItem>,
    grid: Boolean,
    queryState: MutableState<String>,
    selectedShortcutPath: MutableState<String?>,
    cb: LibraryCallbacks
) {
    val query = queryState.value
    var filterName by rememberSaveable { mutableStateOf(LibraryFilter.All.name) }
    val filter = LibraryFilter.valueOf(filterName)
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.Recent.name) }
    val sort = LibrarySort.valueOf(sortName)
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val visible = remember(items, filter, query, sort) {
        val source = when (filter) {
            LibraryFilter.All -> items
            LibraryFilter.Favorites -> items.filter { it.favorite }
            LibraryFilter.Recent -> items.sortedByDescending { it.lastRunAt }
        }
        val searched =
            if (query.isBlank()) source else source.filter { it.name.contains(query, true) }
        when (sort) {
            LibrarySort.Recent -> searched.sortedByDescending { it.lastRunAt }
            LibrarySort.Alpha -> searched.sortedBy { it.name.lowercase() }
            LibrarySort.Playtime -> searched.sortedByDescending { it.playtimeMillis }
        }
    }

    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val activity = LocalContext.current as? MainActivity

    DisposableEffect(activity, landscape) {
        if (landscape) {
            activity?.setBottomNavigationVisible(false)
            activity?.setMainToolbarVisible(false)
        }
        onDispose { }
    }

    if (landscape && visible.isNotEmpty() && !grid) {
        var menu by remember { mutableStateOf<LibraryItem?>(null) }
        LandscapePagerCore(
            items = visible,
            selectedShortcutPath = selectedShortcutPath,
            callbacks = cb,
            header = {
                LibraryLandscapeHeader(
                    activity = activity,
                    grid = grid,
                    onArtwork = true,
                    onGridViewChanged = cb::onGridViewChanged
                )
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LibraryFilter.values().forEach { option ->
                        LibraryFilterChip(option.name, option == filter) { filterName = option.name }
                    }
                }
            },
            footerActions = { item ->
                IconButton(onClick = { menu = item }) {
                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more_options), tint = Color.White)
                }
            }
        )
        menu?.let { LibraryItemMenuCompat(it, cb) { menu = null } }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp)
    ) {
        if (landscape) {
            LibraryLandscapeHeader(
                activity = activity,
                grid = grid,
                onArtwork = false,
                onGridViewChanged = cb::onGridViewChanged
            )
            Spacer(Modifier.height(7.dp))
        }

        val tabs = listOf(
            stringResource(R.string.all_games),
            stringResource(R.string.favorites)
        )
        val tabIndex = if (filter == LibraryFilter.Favorites) 1 else 0
        TextTabRow(
            tabs = tabs,
            selectedIndex = tabIndex,
            onSelected = { index ->
                filterName = if (index == 1) LibraryFilter.Favorites.name else LibraryFilter.All.name
            }
        )

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SourceCard(
                icon = Icons.Outlined.FileDownload,
                label = stringResource(R.string.import_games),
                highlighted = items.isEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { cb.onOpenImport() }
        }

        LibrarySectionHeader(
            count = visible.size,
            grid = grid,
            sort = sort,
            searchOpen = searchOpen,
            activity = activity,
            onToggleSearch = {
                searchOpen = !searchOpen
                if (!searchOpen) queryState.value = ""
            },
            onSortSelected = { sortName = it.name },
            onGridViewChanged = cb::onGridViewChanged
        )

        if (searchOpen) {
            LibrarySearchField(
                value = query,
                onValueChange = { queryState.value = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        when {
            visible.isEmpty() && (query.isNotBlank() || filter == LibraryFilter.Favorites) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.no_games_yet),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            visible.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyHint(
                        icon = Icons.Outlined.SportsEsports,
                        text = stringResource(R.string.no_games_yet)
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (grid) 2 else 1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visible, key = { it.id }) { item ->
                        if (grid) CoverArtworkCard(item, cb) else CompactArtworkCard(item, cb)
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Header                                                                                         */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun LibrarySectionHeader(
    count: Int,
    grid: Boolean,
    sort: LibrarySort,
    searchOpen: Boolean,
    activity: MainActivity?,
    onToggleSearch: () -> Unit,
    onSortSelected: (LibrarySort) -> Unit,
    onGridViewChanged: (Boolean) -> Unit
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    var orientationRevision by remember { mutableIntStateOf(0) }
    val orientationState = remember(activity, orientationRevision) {
        Triple(
            activity?.isOrientationLocked ?: false,
            activity?.isVerticalModeEnabled ?: false,
            activity?.isHorizontalModeEnabled ?: false
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.my_games),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = count.toString(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))

        CircularIconButton(
            icon = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
            contentDescription = stringResource(R.string.search_games),
            selected = searchOpen,
            size = 34.dp,
            onClick = onToggleSearch
        )
        Spacer(Modifier.width(6.dp))

        Box {
            Surface(
                onClick = { sortMenuOpen = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Sort, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = sortLabel(sort),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                Text(
                    text = stringResource(R.string.sort),
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 6.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LibrarySort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(option)) },
                        onClick = {
                            sortMenuOpen = false
                            onSortSelected(option)
                        }
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f)
                )
                OrientationToggleMenuItem(stringResource(R.string.orientation_lock), orientationState.first) {
                    activity?.toggleOrientationLock()
                    orientationRevision++
                }
                OrientationToggleMenuItem(stringResource(R.string.orientation_vertical), orientationState.second) {
                    activity?.toggleVerticalMode()
                    orientationRevision++
                }
                OrientationToggleMenuItem(stringResource(R.string.orientation_horizontal), orientationState.third) {
                    activity?.toggleHorizontalMode()
                    orientationRevision++
                }
            }
        }

        Spacer(Modifier.width(6.dp))

        CircularIconButton(
            icon = if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView,
            contentDescription = stringResource(
                if (grid) R.string.switch_to_list else R.string.switch_to_grid
            ),
            size = 34.dp
        ) { onGridViewChanged(!grid) }
    }
}

@Composable
private fun sortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.Recent -> stringResource(R.string.sort_recent)
    LibrarySort.Alpha -> stringResource(R.string.sort_alpha)
    LibrarySort.Playtime -> stringResource(R.string.sort_playtime)
}

@Composable
private fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_games),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryLandscapeHeader(
    activity: MainActivity?,
    grid: Boolean,
    onArtwork: Boolean,
    onGridViewChanged: (Boolean) -> Unit
) {
    var moreOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.library),
            color = if (onArtwork) Color.White else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.weight(1f))
        LibraryTopIcon(if (grid) Icons.Outlined.ViewList else Icons.Outlined.GridView, false) {
            onGridViewChanged(!grid)
        }
        LibraryTopIcon(Icons.Outlined.Add, false) { activity?.navigateToSubDestination(R.id.main_menu_file_manager) }
        LibraryTopIcon(Icons.Outlined.Home, true) {}
        LibraryTopIcon(Icons.Outlined.Person, false) { activity?.navigateToMainDestination(R.id.main_menu_profile) }
        Box {
            LibraryTopIcon(Icons.Outlined.MoreVert, false) { moreOpen = true }
            LibraryOrientationMenu(
                expanded = moreOpen,
                onDismiss = { moreOpen = false },
                activity = activity
            )
        }
    }
}

@Composable
private fun LibraryOrientationMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    activity: MainActivity?
) {
    var orientationRevision by remember { mutableIntStateOf(0) }
    val state = remember(activity, orientationRevision) {
        Triple(
            activity?.isOrientationLocked ?: false,
            activity?.isVerticalModeEnabled ?: false,
            activity?.isHorizontalModeEnabled ?: false
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        OrientationToggleMenuItem(stringResource(R.string.orientation_lock), state.first) {
            activity?.toggleOrientationLock()
            orientationRevision++
        }
        OrientationToggleMenuItem(stringResource(R.string.orientation_vertical), state.second) {
            activity?.toggleVerticalMode()
            orientationRevision++
        }
        OrientationToggleMenuItem(stringResource(R.string.orientation_horizontal), state.third) {
            activity?.toggleHorizontalMode()
            orientationRevision++
        }
    }
}

@Composable
private fun OrientationToggleMenuItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = { Switch(checked = checked, onCheckedChange = null) },
        onClick = onClick
    )
}

@Composable
private fun LibraryTopIcon(icon: ImageVector, selected: Boolean, click: () -> Unit) {
    val whiteTheme = MaterialTheme.colorScheme.background.luminance() > .65f
    val background = if (whiteTheme) Color.Black.copy(if (selected) .90f else .78f)
    else if (selected) Color.White.copy(.16f) else Color.Transparent
    val content = if (whiteTheme) Color.White else Color.White.copy(if (selected) 1f else .68f)
    Surface(
        onClick = click,
        modifier = Modifier.padding(horizontal = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = background,
        contentColor = content
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun LibraryFilterChip(label: String, selected: Boolean, click: () -> Unit) {
    Surface(
        onClick = click,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(label, Modifier.padding(horizontal = 15.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Cards                                                                                          */
/* --------------------------------------------------------------------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactArtworkCard(item: LibraryItem, cb: LibraryCallbacks) {
    RequestArtworkCompat(item, cb)
    Surface(
        modifier = Modifier.fillMaxWidth().height(92.dp).combinedClickable(
            onClick = { cb.onOpen(item.shortcutPath) },
            onLongClick = { cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_SETTINGS) }
        ),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box {
            ArtworkCompat(
                item.bannerPath ?: item.coverPath ?: item.iconPath,
                item.fallbackIcon,
                Modifier.fillMaxSize().alpha(.52f)
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface.copy(.38f),
                            MaterialTheme.colorScheme.surface.copy(.82f),
                            MaterialTheme.colorScheme.surface.copy(.96f)
                        )
                    )
                )
            )
            Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtworkCompat(
                    item.coverPath ?: item.bannerPath ?: item.iconPath,
                    item.fallbackIcon,
                    Modifier.size(68.dp).clip(RoundedCornerShape(11.dp))
                )
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.containerName, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                PlayCompat(item, cb, false)
                MenuButtonCompat(item, cb, false)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CoverArtworkCard(item: LibraryItem, cb: LibraryCallbacks) {
    RequestArtworkCompat(item, cb)
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { cb.onOpen(item.shortcutPath) },
            onLongClick = { cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_SETTINGS) }
        ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.aspectRatio(.72f)) {
            ArtworkCompat(item.coverPath ?: item.bannerPath ?: item.iconPath, item.fallbackIcon, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(.92f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 50.dp, bottom = 13.dp)) {
                Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.containerName, color = Color.White.copy(.72f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.align(Alignment.TopEnd)) { MenuButtonCompat(item, cb, true) }
            Box(Modifier.align(Alignment.BottomEnd).padding(9.dp)) { PlayCompat(item, cb, true) }
        }
    }
}

@Composable
private fun RequestArtworkCompat(item: LibraryItem, cb: LibraryCallbacks) {
    LaunchedEffect(item.id, item.coverPath, item.bannerPath) {
        if (item.coverPath == null) cb.onArtworkNeeded(item.shortcutPath, "cover")
        if (item.bannerPath == null) cb.onArtworkNeeded(item.shortcutPath, "banner")
    }
}

@Composable
private fun ArtworkCompat(path: String?, fallback: Bitmap?, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(fallback, path) {
        value = withContext(Dispatchers.IO) {
            path?.takeIf { File(it).isFile }?.let(BitmapFactory::decodeFile) ?: fallback
        }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
private fun PlayCompat(item: LibraryItem, cb: LibraryCallbacks, overlay: Boolean) {
    Surface(
        onClick = { cb.onRun(item.shortcutPath) },
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = if (overlay) LocalWinlatorAccent.current else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (overlay) Color.White else MaterialTheme.colorScheme.onSurface
    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.play)) } }
}

@Composable
private fun MenuButtonCompat(item: LibraryItem, cb: LibraryCallbacks, light: Boolean) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more_options), tint = if (light) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) LibraryItemMenuCompat(item, cb) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryItemMenuCompat(item: LibraryItem, cb: LibraryCallbacks, close: () -> Unit) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = close,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp).size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .42f)
            ) {}
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = if (landscape) 920.dp else 760.dp)
                .padding(horizontal = if (landscape) 22.dp else 16.dp)
                .padding(bottom = if (landscape) 10.dp else 24.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                item.name,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)
            )
            val favoriteLabel = if (item.favorite) stringResource(R.string.action_unfavorite) else stringResource(R.string.action_favorite)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryActionTileCompat(
                    if (item.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    favoriteLabel,
                    Modifier.weight(1f),
                    horizontal = landscape
                ) {
                    close()
                    cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_FAVORITE)
                }
                LibraryActionTileCompat(Icons.Outlined.Settings, stringResource(R.string.configure), Modifier.weight(1f), horizontal = landscape) {
                    close()
                    cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_SETTINGS)
                }
                LibraryActionTileCompat(Icons.Outlined.Photo, stringResource(R.string.action_artwork), Modifier.weight(1f), horizontal = landscape) {
                    close()
                    cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_ICON)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryActionTileCompat(Icons.Outlined.Home, stringResource(R.string.action_home_screen), Modifier.weight(1f), horizontal = landscape) {
                    close()
                    cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_HOME)
                }
                LibraryActionTileCompat(Icons.Outlined.ContentCopy, stringResource(R.string.duplicate), Modifier.weight(1f), horizontal = landscape) {
                    close()
                    cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_CLONE)
                }
                LibraryActionTileCompat(Icons.Outlined.FileUpload, stringResource(R.string.export), Modifier.weight(1f), horizontal = landscape) {
                    close()
                    cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_EXPORT)
                }
            }
            LibraryActionTileCompat(
                Icons.Outlined.DeleteOutline,
                stringResource(R.string.action_remove_from_library),
                Modifier.fillMaxWidth().padding(top = 8.dp),
                destructive = true,
                horizontal = landscape
            ) {
                close()
                cb.onAction(item.shortcutPath, LibraryComposeHost.ACTION_REMOVE)
            }
        }
    }
}

@Composable
private fun LibraryActionTileCompat(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    destructive: Boolean = false,
    horizontal: Boolean = false,
    click: () -> Unit
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = click,
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = if (destructive)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = .22f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f),
        border = BorderStroke(
            1.dp,
            if (destructive) MaterialTheme.colorScheme.error.copy(alpha = .46f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .70f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (horizontal) 13.dp else 12.dp,
                vertical = if (horizontal) 9.dp else 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(if (horizontal) 21.dp else 23.dp), tint = tint)
            Spacer(Modifier.width(9.dp))
            Text(
                label,
                style = if (horizontal) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
