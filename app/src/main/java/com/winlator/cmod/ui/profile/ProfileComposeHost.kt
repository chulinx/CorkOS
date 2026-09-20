package com.winlator.cmod.ui.profile

import android.content.Context
import android.view.View
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.winlator.cmod.ui.theme.WinZTheme

@Immutable
data class ProfileModel(
    val nickname: String,
    val bio: String,
    val avatarColor: Int,
    val uid: String,
    val totalPlaytimeMillis: Long,
    val playedGameCount: Int,
    val totalGameCount: Int,
    val totalPlayCount: Int,
    val containerCount: Int,
    val primaryContainerName: String?,
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val appVersion: String,
    val runtimeSummary: String,
    val languageTag: String
)

@Stable
interface ProfileCallbacks {
    fun onOpenSettings()
    fun onOpenContainers()
    fun onOpenComponents()
    fun onOpenInputControls()
    fun onOpenImportGames()
    fun onOpenAbout()
    fun onSaveIdentity(nickname: String, bio: String, avatarColor: Int)

    /** [tag] is one of [com.winlator.cmod.core.AppLocale.SYSTEM] / `CHINESE` / `ENGLISH`. */
    fun onLanguageChanged(tag: String)
}

object ProfileComposeHost {
    /**
     * Follows the ComposeHost pattern used by the rest of the app: the Java side owns the state and
     * pushes an immutable [ProfileModel] in; Compose only renders and calls back.
     */
    @JvmStatic
    fun create(context: Context, model: ProfileModel, callbacks: ProfileCallbacks): ComposeView {
        val state = mutableStateOf(model)
        return ComposeView(context).apply {
            tag = state
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { WinZTheme { ProfileScreen(state.value, callbacks) } }
        }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun update(view: View?, model: ProfileModel) {
        (view?.tag as? MutableState<ProfileModel>)?.value = model
    }
}
