package com.winlator.cmod.ui.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R
import com.winlator.cmod.ui.KeepLandscapeChromeHidden
import com.winlator.cmod.ui.applySystemBars
import com.winlator.cmod.ui.components.LabeledValue
import com.winlator.cmod.ui.components.PillChip
import com.winlator.cmod.ui.components.PrimaryButton
import com.winlator.cmod.ui.components.SectionHeader
import com.winlator.cmod.ui.profile.PlaytimeStats
import com.winlator.cmod.ui.theme.LocalWinlatorAccent
import com.winlator.cmod.ui.theme.WinZTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Everything the detail page renders. All fields come from real data — [Shortcut] / [Container]
 * extras plus the aggregated `playtime_stats` — except [note], which is an optional user note.
 */
@Immutable
data class GameDetailModel(
    val title: String,
    val containerName: String,
    val runtimeLabel: String,
    val rendererLabel: String,
    val emulatorLabel: String,
    val resolution: String,
    val executablePath: String,
    val coverPath: String?,
    val bannerPath: String?,
    val iconPath: String?,
    val fallback: Bitmap?,
    val favorite: Boolean,
    val playtimeMillis: Long,
    val playCount: Int,
    val lastRunAt: Long,
    val note: String
)

interface GameDetailCallbacks {
    fun onPlay()
    fun onConfigure()
    fun onArguments()
    fun onGameFolder()
    fun onFavorite(favorite: Boolean)
    fun onRemove()
    fun onNoteChanged(note: String)
}

object GameDetailComposeHost {
    @JvmStatic
    fun create(
        context: Context,
        model: GameDetailModel,
        callbacks: GameDetailCallbacks
    ): ComposeView {
        applySystemBars(context as? MainActivity)
        return ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { WinZTheme { GameDetailScreen(model, callbacks) } }
        }
    }
}

@Composable
private fun GameDetailScreen(model: GameDetailModel, callbacks: GameDetailCallbacks) {
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val activity = LocalContext.current as? MainActivity

    if (landscape) {
        KeepLandscapeChromeHidden(activity, restoreChromeOnPortrait = false)
    }

    DisposableEffect(activity, landscape) {
        if (!landscape) {
            activity?.setBottomNavigationVisible(false)
            activity?.setMainToolbarVisible(true)
        }
        onDispose { }
    }

    var favorite by remember(model.favorite) { mutableStateOf(model.favorite) }
    val toggleFavorite = {
        favorite = !favorite
        callbacks.onFavorite(favorite)
    }
    var note by remember(model.note) { mutableStateOf(model.note) }

    if (landscape) {
        LandscapeDetail(model, favorite, callbacks, toggleFavorite)
    } else {
        PortraitDetail(
            model = model,
            favorite = favorite,
            note = note,
            callbacks = callbacks,
            toggleFavorite = toggleFavorite,
            onNoteChanged = {
                note = it
                callbacks.onNoteChanged(it)
            }
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Portrait                                                                                       */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun PortraitDetail(
    model: GameDetailModel,
    favorite: Boolean,
    note: String,
    callbacks: GameDetailCallbacks,
    toggleFavorite: () -> Unit,
    onNoteChanged: (String) -> Unit
) {
    var editingNote by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MediaPager(model, favorite, toggleFavorite)

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                TitleBlock(model)

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillChip(model.emulatorLabel)
                    PillChip(model.runtimeLabel)
                    PillChip(model.rendererLabel)
                }

                Spacer(Modifier.height(18.dp))
                MetaGrid(model)

                Spacer(Modifier.height(20.dp))
                RuntimeCard(model)

                SectionHeader(
                    title = stringResource(R.string.game_intro),
                    horizontalPadding = 0.dp
                )
                NoteCard(note) { editingNote = true }

                SectionHeader(
                    title = stringResource(R.string.related_actions),
                    horizontalPadding = 0.dp
                )
                ActionGrid(callbacks)
                Spacer(Modifier.height(20.dp))
            }
        }

        StickyPlayBar(callbacks)
    }

    if (editingNote) {
        NoteEditorDialog(
            initial = note,
            onDismiss = { editingNote = false },
            onConfirm = {
                editingNote = false
                onNoteChanged(it)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaPager(
    model: GameDetailModel,
    favorite: Boolean,
    toggleFavorite: () -> Unit
) {
    val coverLabel = stringResource(R.string.detail_media_cover)
    val bannerLabel = stringResource(R.string.detail_media_banner)
    val iconLabel = stringResource(R.string.icon)
    val pages = remember(model.coverPath, model.bannerPath, model.iconPath, coverLabel, bannerLabel, iconLabel) {
        buildList {
            model.coverPath?.let { add(coverLabel to it) }
            model.bannerPath?.let { add(bannerLabel to it) }
            model.iconPath?.let { add(iconLabel to it) }
        }
    }
    val pagerState = rememberPagerState(pageCount = { maxOf(pages.size, 1) })
    val current = pages.getOrNull(pagerState.currentPage)

    Box(Modifier.fillMaxWidth().aspectRatio(1.34f).background(MaterialTheme.colorScheme.surface)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = pages.size > 1
        ) { page ->
            val path = pages.getOrNull(page)?.second
            DetailArtwork(path, model.fallback, Modifier.fillMaxSize())
        }

        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(.35f),
                        Color.Transparent,
                        Color.Black.copy(.55f)
                    )
                )
            )
        )

        if (pages.size > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(.55f),
                contentColor = Color.White
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${pages.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    fontSize = 12.sp
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 14.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(.55f),
                contentColor = Color.White
            ) {
                Text(
                    text = current?.first ?: "",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp
                )
            }
        }

        IconButton(
            onClick = toggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(42.dp)
        ) {
            Icon(
                if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                stringResource(R.string.action_favorite),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun TitleBlock(model: GameDetailModel) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = model.containerName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = PlaytimeStats.formatDuration(model.playtimeMillis),
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalWinlatorAccent.current
            )
            Text(
                text = stringResource(R.string.playtime),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetaGrid(model: GameDetailModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            LabeledValue(
                value = model.containerName.ifBlank { stringResource(R.string.value_none) },
                label = stringResource(R.string.container_label),
                modifier = Modifier.weight(1f)
            )
            LabeledValue(
                value = model.resolution.ifBlank { stringResource(R.string.value_none) },
                label = stringResource(R.string.resolution),
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth()) {
            LabeledValue(
                value = model.executablePath.substringAfterLast('/')
                    .ifBlank { stringResource(R.string.value_none) },
                label = stringResource(R.string.executable),
                modifier = Modifier.weight(1f)
            )
            LabeledValue(
                value = formatLastPlayed(model.lastRunAt),
                label = stringResource(R.string.last_played),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RuntimeCard(model: GameDetailModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Memory,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = stringResource(R.string.runtime_environment),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${model.runtimeLabel} · ${model.rendererLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = model.emulatorLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                )
                Spacer(Modifier.width(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule,
                        null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(7.dp))
                    Column {
                        Text(
                            text = PlaytimeStats.formatDuration(
                                model.playtimeMillis,
                                stringResource(R.string.playtime_unknown)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Text(
                            text = "${model.playCount}×",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: String, onEdit: () -> Unit) {
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = note.ifBlank { stringResource(R.string.about_you_hint) },
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (note.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActionGrid(callbacks: GameDetailCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailActionTile(
                Icons.Outlined.Settings,
                stringResource(R.string.configure),
                Modifier.weight(1f),
                callbacks::onConfigure
            )
            DetailActionTile(
                Icons.Outlined.PlayArrow,
                stringResource(R.string.enter_container),
                Modifier.weight(1f),
                callbacks::onArguments
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailActionTile(
                Icons.Outlined.Folder,
                stringResource(R.string.game_folder),
                Modifier.weight(1f),
                callbacks::onGameFolder
            )
            DetailActionTile(
                Icons.Outlined.DeleteOutline,
                stringResource(R.string.remove),
                Modifier.weight(1f),
                callbacks::onRemove,
                destructive = true
            )
        }
    }
}

@Composable
private fun StickyPlayBar(callbacks: GameDetailCallbacks) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                onClick = callbacks::onConfigure,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.GridView, stringResource(R.string.configure), modifier = Modifier.size(22.dp))
                }
            }
            PrimaryButton(
                text = stringResource(R.string.play_now),
                icon = Icons.Outlined.PlayArrow,
                modifier = Modifier.weight(1f),
                onClick = callbacks::onPlay
            )
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Landscape                                                                                      */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun LandscapeDetail(
    model: GameDetailModel,
    favorite: Boolean,
    callbacks: GameDetailCallbacks,
    toggleFavorite: () -> Unit
) {
    val activity = LocalContext.current as? MainActivity
    val artwork by produceState<Bitmap?>(model.fallback, model.bannerPath, model.fallback) {
        value = withContext(Dispatchers.IO) {
            model.bannerPath?.takeIf { File(it).isFile }?.let(BitmapFactory::decodeFile)
                ?: model.fallback
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (artwork != null) {
            Image(artwork!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Black.copy(.93f), Color.Black.copy(.70f), Color.Black.copy(.28f))
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(.18f), Color.Transparent, Color.Black.copy(.55f))
                )
            )
        )

        Surface(
            onClick = { activity?.onBackPressedDispatcher?.onBackPressed() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 16.dp)
                .size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(.62f),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(.16f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ArrowBack, stringResource(R.string.action_back), modifier = Modifier.size(24.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 78.dp, end = 34.dp, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .widthIn(max = 590.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(.42f),
                border = BorderStroke(1.dp, Color.White.copy(.16f))
            ) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.title,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${model.runtimeLabel}  •  ${model.rendererLabel}",
                                color = Color.White.copy(.72f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        IconButton(onClick = toggleFavorite) {
                            Icon(
                                if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                stringResource(R.string.action_favorite),
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LandscapeChip(model.emulatorLabel)
                        LandscapeChip(model.containerName)
                        LandscapeChip(model.resolution)
                    }

                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        text = stringResource(R.string.play_now),
                        icon = Icons.Outlined.PlayArrow,
                        onClick = callbacks::onPlay
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LandscapeAction(
                            Icons.Outlined.Settings,
                            stringResource(R.string.configure),
                            Modifier.weight(1f),
                            callbacks::onConfigure
                        )
                        LandscapeAction(
                            Icons.Outlined.PlayArrow,
                            stringResource(R.string.enter_container),
                            Modifier.weight(1f),
                            callbacks::onArguments
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LandscapeAction(
                            Icons.Outlined.Folder,
                            stringResource(R.string.game_folder),
                            Modifier.weight(1f),
                            callbacks::onGameFolder
                        )
                        LandscapeAction(
                            Icons.Outlined.DeleteOutline,
                            stringResource(R.string.remove),
                            Modifier.weight(1f),
                            callbacks::onRemove,
                            destructive = true
                        )
                    }
                }
            }
            Spacer(Modifier.weight(.75f))
        }
    }
}

@Composable
private fun LandscapeChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(.16f),
        contentColor = Color.White
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp), fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun LandscapeAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (destructive) Color.Black.copy(.45f) else Color.White.copy(.14f),
        contentColor = if (destructive) Color(0xFFFF8A8F) else Color.White,
        border = BorderStroke(1.dp, Color.White.copy(.18f))
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Text(label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Shared pieces                                                                                  */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun DetailArtwork(path: String?, fallback: Bitmap?, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(fallback, path) {
        value = withContext(Dispatchers.IO) {
            path?.takeIf { File(it).isFile }?.let(BitmapFactory::decodeFile) ?: fallback
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
private fun DetailActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = .22f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        contentColor = tint
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NoteEditorDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_intro)) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(300) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.about_you_hint)) },
                supportingText = { Text("${draft.length}/300") },
                minLines = 3,
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) {
                Text(stringResource(R.string.save_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about))
            }
        }
    )
}

@Composable
private fun formatLastPlayed(lastRunAt: Long): String {
    if (lastRunAt <= 0L) return stringResource(R.string.never)
    val formatter = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
    return remember(lastRunAt) { formatter.format(java.util.Date(lastRunAt)) }
}
