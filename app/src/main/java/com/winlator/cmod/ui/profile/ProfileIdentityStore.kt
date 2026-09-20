package com.winlator.cmod.ui.profile

import android.content.Context
import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * The locally stored identity shown on the "Mine" page.
 *
 * CorkOS has no account system, so this is a purely local, user editable placeholder. Keeping it
 * behind a store means a real account backend can replace this later without touching the UI.
 */
@Immutable
data class ProfileIdentity(
    val nickname: String,
    val bio: String,
    val avatarColor: Int,
    val uid: String
)

object ProfileIdentityStore {
    private const val PREFS = "corkos_profile"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_BIO = "bio"
    private const val KEY_AVATAR_COLOR = "avatar_color"
    private const val KEY_UID = "uid"

    const val DEFAULT_NICKNAME = "Player"
    const val AVATAR_COLOR_COUNT = 6
    const val MAX_NICKNAME_LENGTH = 20
    const val MAX_BIO_LENGTH = 100

    @JvmStatic
    fun load(context: Context): ProfileIdentity {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var uid = prefs.getString(KEY_UID, null)
        if (uid.isNullOrEmpty()) {
            uid = generateUid()
            prefs.edit().putString(KEY_UID, uid).apply()
        }

        return ProfileIdentity(
            nickname = prefs.getString(KEY_NICKNAME, null)?.takeIf { it.isNotBlank() }
                ?: DEFAULT_NICKNAME,
            bio = prefs.getString(KEY_BIO, "").orEmpty(),
            avatarColor = prefs.getInt(KEY_AVATAR_COLOR, 0)
                .coerceIn(0, AVATAR_COLOR_COUNT - 1),
            uid = uid
        )
    }

    @JvmStatic
    fun save(context: Context, nickname: String, bio: String, avatarColor: Int) {
        val safeNickname = nickname.trim().take(MAX_NICKNAME_LENGTH).ifBlank { DEFAULT_NICKNAME }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NICKNAME, safeNickname)
            .putString(KEY_BIO, bio.trim().take(MAX_BIO_LENGTH))
            .putInt(KEY_AVATAR_COLOR, avatarColor.coerceIn(0, AVATAR_COLOR_COUNT - 1))
            .apply()
    }

    private fun generateUid(): String =
        UUID.randomUUID().toString().replace("-", "").take(10)
}
