package com.winlator.cmod.ui.profile

import android.content.Context
import androidx.compose.runtime.Immutable

/**
 * Aggregated, read-only view of the `playtime_stats` SharedPreferences.
 *
 * The write side lives in [com.winlator.cmod.XServerDisplayActivity] (`savePlaytimeData` /
 * `incrementPlayCount`) and is intentionally untouched — this object only reads.
 *
 * Key layout written by the runtime:
 *   "<shortcutName>_playtime"   -> Long (milliseconds)
 *   "<shortcutName>_play_count" -> Int
 */
@Immutable
data class PlaytimeSnapshot(
    val totalPlaytimeMillis: Long,
    val playedGameCount: Int,
    val totalPlayCount: Int,
    val perGameMillis: Map<String, Long>,
    val perGamePlayCount: Map<String, Int>
) {
    fun playtimeMillisFor(shortcutName: String): Long = perGameMillis[shortcutName] ?: 0L

    fun playCountFor(shortcutName: String): Int = perGamePlayCount[shortcutName] ?: 0

    companion object {
        @JvmField
        val EMPTY = PlaytimeSnapshot(0L, 0, 0, emptyMap(), emptyMap())
    }
}

object PlaytimeStats {
    const val PREFS = "playtime_stats"

    private const val SUFFIX_PLAYTIME = "_playtime"
    private const val SUFFIX_PLAY_COUNT = "_play_count"
    private const val MINUTE = 60_000L
    private const val HOUR = 60L * MINUTE

    @JvmStatic
    fun load(context: Context): PlaytimeSnapshot {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var totalMillis = 0L
        var playedGames = 0
        var totalPlays = 0
        val perGameMillis = HashMap<String, Long>()
        val perGamePlayCount = HashMap<String, Int>()

        for ((key, value) in prefs.all) {
            when {
                key.endsWith(SUFFIX_PLAYTIME) -> {
                    val millis = (value as? Number)?.toLong() ?: continue
                    totalMillis += millis
                    if (millis > 0L) playedGames++
                    perGameMillis[key.removeSuffix(SUFFIX_PLAYTIME)] = millis
                }
                key.endsWith(SUFFIX_PLAY_COUNT) -> {
                    val count = (value as? Number)?.toInt() ?: continue
                    totalPlays += count
                    perGamePlayCount[key.removeSuffix(SUFFIX_PLAY_COUNT)] = count
                }
            }
        }

        return PlaytimeSnapshot(
            totalPlaytimeMillis = totalMillis,
            playedGameCount = playedGames,
            totalPlayCount = totalPlays,
            perGameMillis = perGameMillis,
            perGamePlayCount = perGamePlayCount
        )
    }

    /** Compact duration label: "45m" under an hour, otherwise "12h". */
    @JvmStatic
    fun formatDuration(millis: Long): String = when {
        millis <= 0L -> "0h"
        millis < HOUR -> "${millis / MINUTE}m"
        else -> "${millis / HOUR}h"
    }

    /** Same as [formatDuration] but falls back to [fallback] when nothing has been recorded. */
    @JvmStatic
    fun formatDuration(millis: Long, fallback: String): String =
        if (millis <= 0L) fallback else formatDuration(millis)
}
