package com.winlator.cmod.ui.profile

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.MainActivity
import com.winlator.cmod.core.AppLocale
import com.winlator.cmod.R
import com.winlator.cmod.ui.LandscapeMainNavigation
import com.winlator.cmod.ui.components.CircularIconButton
import com.winlator.cmod.ui.components.EmptyStateCard
import com.winlator.cmod.ui.components.InsetActionRow
import com.winlator.cmod.ui.components.MediaBanner
import com.winlator.cmod.ui.components.PillChip
import com.winlator.cmod.ui.components.PrimaryButton
import com.winlator.cmod.ui.components.SectionHeader
import com.winlator.cmod.ui.components.SettingsDivider
import com.winlator.cmod.ui.components.SettingsGroupCard
import com.winlator.cmod.ui.components.SettingsRow
import com.winlator.cmod.ui.components.StatPair
import com.winlator.cmod.ui.theme.LocalWinlatorAccent

/**
 * Avatar background palette. These are identity colours rather than theme colours, so they are the
 * one place a literal palette is appropriate.
 */
private val AvatarPalette = listOf(
    Color(0xFF8D6E63),
    Color(0xFFFF9F43),
    Color(0xFFFFC93C),
    Color(0xFF4CD97B),
    Color(0xFF4FC3F7),
    Color(0xFF90A4AE)
)

private fun avatarColor(index: Int): Color =
    AvatarPalette[index.coerceIn(0, AvatarPalette.size - 1)]

@Composable
fun ProfileScreen(model: ProfileModel, callbacks: ProfileCallbacks) {
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    var editing by remember { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }

    if (landscape) {
        LandscapeProfile(model, callbacks, { editing = true }, { languageOpen = true })
    } else {
        PortraitProfile(model, callbacks, { editing = true }, { languageOpen = true })
    }

    if (editing) {
        ProfileEditSheet(
            model = model,
            onDismiss = { editing = false },
            onSave = { nickname, bio, colorIndex ->
                editing = false
                callbacks.onSaveIdentity(nickname, bio, colorIndex)
            }
        )
    }

    if (languageOpen) {
        LanguageSheet(
            currentTag = model.languageTag,
            onDismiss = { languageOpen = false },
            onSelected = { tag ->
                languageOpen = false
                callbacks.onLanguageChanged(tag)
            }
        )
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Portrait                                                                                       */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun PortraitProfile(
    model: ProfileModel,
    callbacks: ProfileCallbacks,
    onEdit: () -> Unit,
    onOpenLanguage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Fixed action row — stays visible while the content below scrolls, matching the
        // reference app's persistent header buttons.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            CircularIconButton(
                icon = Icons.Outlined.FileDownload,
                contentDescription = stringResource(R.string.import_games)
            ) { callbacks.onOpenImportGames() }
            Spacer(Modifier.width(10.dp))
            CircularIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings)
            ) { callbacks.onOpenSettings() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            IdentityHeader(model, Modifier.padding(top = 10.dp), onEdit)

            Spacer(Modifier.height(22.dp))
            // The runtime card is the single home for container/runtime/components info — the old
            // version banner and duplicate "my containers" card were removed as redundant.
            ContainerRuntimeCard(model, Modifier.padding(horizontal = 16.dp))

            SectionHeader(stringResource(R.string.more))
            MoreGroup(model, callbacks, onOpenLanguage)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MoreGroup(
    model: ProfileModel,
    callbacks: ProfileCallbacks,
    onOpenLanguage: () -> Unit,
    horizontalPadding: Dp = 16.dp
) {
    SettingsGroupCard(horizontalPadding = horizontalPadding) {
        // "设置" lives only in the fixed top-right button now, so it is no longer listed here.
        SettingsRow(Icons.Outlined.Dns, stringResource(R.string.containers)) {
            callbacks.onOpenContainers()
        }
        SettingsDivider()
        SettingsRow(Icons.Outlined.SportsEsports, stringResource(R.string.input_controls)) {
            callbacks.onOpenInputControls()
        }
        SettingsDivider()
        SettingsRow(Icons.Outlined.FolderOpen, stringResource(R.string.import_games)) {
            callbacks.onOpenImportGames()
        }
        SettingsDivider()
        SettingsRow(Icons.Outlined.Widgets, stringResource(R.string.components)) {
            callbacks.onOpenComponents()
        }
        SettingsDivider()
        SettingsRow(
            icon = Icons.Outlined.Storage,
            title = stringResource(R.string.storage),
            trailingText = storageLabel(model),
            showChevron = false,
            onClick = null
        )
        SettingsDivider()
        SettingsRow(
            icon = Icons.Outlined.Translate,
            title = stringResource(R.string.settings_language),
            trailingText = languageLabel(model.languageTag),
            onClick = onOpenLanguage
        )
        SettingsDivider()
        SettingsRow(Icons.Outlined.Info, stringResource(R.string.about)) {
            callbacks.onOpenAbout()
        }
    }
}

@Composable
private fun languageLabel(tag: String): String = when (tag) {
    AppLocale.CHINESE -> stringResource(R.string.settings_language_zh)
    AppLocale.ENGLISH -> stringResource(R.string.settings_language_en)
    else -> stringResource(R.string.settings_language_system)
}

/* --------------------------------------------------------------------------------------------- */
/* Landscape                                                                                      */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun LandscapeProfile(
    model: ProfileModel,
    callbacks: ProfileCallbacks,
    onEdit: () -> Unit,
    onOpenLanguage: () -> Unit
) {
    val activity = LocalContext.current as? MainActivity
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LandscapeMainNavigation(
            activity = activity,
            selected = R.id.main_menu_profile,
            title = stringResource(R.string.profile)
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .verticalScroll(rememberScrollState())
            ) {
                IdentityHeader(model, Modifier.padding(top = 6.dp), onEdit)
                Spacer(Modifier.height(24.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                ContainerRuntimeCard(model)
                Spacer(Modifier.height(16.dp))
                SectionHeader(stringResource(R.string.more), horizontalPadding = 0.dp)
                MoreGroup(model, callbacks, onOpenLanguage, horizontalPadding = 0.dp)
            }
        }
    }
}

/* --------------------------------------------------------------------------------------------- */
/* Pieces                                                                                         */
/* --------------------------------------------------------------------------------------------- */

@Composable
private fun IdentityHeader(model: ProfileModel, modifier: Modifier = Modifier, onEdit: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarBubble(model.nickname, model.avatarColor, 96.dp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.nickname,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            PillChip(stringResource(R.string.local_account))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${stringResource(R.string.uid)} ${model.uid}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AvatarBubble(nickname: String, colorIndex: Int, size: Dp) {
    val base = avatarColor(colorIndex)
    Box(
        modifier = Modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(base, base.copy(alpha = .62f))),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = nickname.trim().take(1).ifBlank { "?" }.uppercase(),
            fontSize = (size.value / 2.4f).sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun ContainerRuntimeCard(
    model: ProfileModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Dns,
                    null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(15.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Memory,
                    null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.runtime),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(7.dp))
                Box(
                    Modifier
                        .size(7.dp)
                        .background(LocalWinlatorAccent.current, CircleShape)
                )
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Dns,
                                null,
                                modifier = Modifier.size(21.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = model.primaryContainerName
                                ?: stringResource(R.string.add_container),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = model.runtimeSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = model.containerCount.toString(),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            StatPair(
                leftValue = PlaytimeStats.formatDuration(model.totalPlaytimeMillis),
                leftLabel = stringResource(R.string.playtime),
                rightValue = model.totalGameCount.toString(),
                rightLabel = stringResource(R.string.game_count)
            )

        }
    }
}

@Composable
private fun InfoBanner(model: ProfileModel, modifier: Modifier = Modifier) {
    MediaBanner(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Info,
                null,
                modifier = Modifier.size(26.dp),
                tint = LocalWinlatorAccent.current
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "CorkOS ${model.appVersion}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = model.runtimeSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MyContainersCard(
    model: ProfileModel,
    callbacks: ProfileCallbacks,
    modifier: Modifier = Modifier
) {
    if (model.containerCount <= 0) {
        EmptyStateCard(
            icon = Icons.Outlined.Add,
            title = stringResource(R.string.add_container),
            subtitle = stringResource(R.string.manage_containers),
            modifier = modifier,
            onClick = { callbacks.onOpenContainers() }
        )
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Dns,
                            null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = model.primaryContainerName ?: stringResource(R.string.containers),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = model.runtimeSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            InsetActionRow(
                icon = Icons.Outlined.Dns,
                title = stringResource(R.string.manage_containers)
            ) { callbacks.onOpenContainers() }
        }
    }
}

@Composable
private fun storageLabel(model: ProfileModel): String {
    if (model.storageTotalBytes <= 0L) return "—"
    val context = LocalContext.current
    val used = Formatter.formatShortFileSize(context, model.storageUsedBytes)
    val total = Formatter.formatShortFileSize(context, model.storageTotalBytes)
    return "$used / $total"
}

/* --------------------------------------------------------------------------------------------- */
/* Edit sheet                                                                                     */
/* --------------------------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    currentTag: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_language_title),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        Icons.Outlined.Close,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LanguageOption(stringResource(R.string.settings_language_system), AppLocale.SYSTEM, currentTag, onSelected)
            LanguageOption(stringResource(R.string.settings_language_zh), AppLocale.CHINESE, currentTag, onSelected)
            LanguageOption(stringResource(R.string.settings_language_en), AppLocale.ENGLISH, currentTag, onSelected)
        }
    }
}

@Composable
private fun LanguageOption(
    label: String,
    tag: String,
    currentTag: String,
    onSelected: (String) -> Unit
) {
    val selected = tag == currentTag
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onSelected(tag) }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                null,
                modifier = Modifier.size(20.dp),
                tint = LocalWinlatorAccent.current
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSheet(
    model: ProfileModel,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var nickname by remember { mutableStateOf(model.nickname) }
    var bio by remember { mutableStateOf(model.bio) }
    var colorIndex by remember { mutableIntStateOf(model.avatarColor) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.edit_profile),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarPalette.forEachIndexed { index, color ->
                    val selected = index == colorIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 7.dp)
                            .size(if (selected) 54.dp else 40.dp)
                            .background(color, CircleShape)
                            .clickable { colorIndex = index },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Text(
                                text = nickname.trim().take(1).ifBlank { "?" }.uppercase(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = model.uid,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(R.string.uid)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(ProfileIdentityStore.MAX_NICKNAME_LENGTH) },
                label = { Text(stringResource(R.string.nickname)) },
                supportingText = {
                    Text("${nickname.length}/${ProfileIdentityStore.MAX_NICKNAME_LENGTH}")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(ProfileIdentityStore.MAX_BIO_LENGTH) },
                label = { Text(stringResource(R.string.about_you)) },
                placeholder = { Text(stringResource(R.string.about_you_hint)) },
                supportingText = {
                    Text("${bio.length}/${ProfileIdentityStore.MAX_BIO_LENGTH}")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 4
            )

            Spacer(Modifier.height(22.dp))

            PrimaryButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(nickname, bio, colorIndex) }
            )
        }
    }
}
