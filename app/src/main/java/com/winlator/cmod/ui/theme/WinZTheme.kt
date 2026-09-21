package com.winlator.cmod.ui.theme

import android.R as AndroidR
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView
import com.winlator.cmod.R

enum class WinlatorThemeType(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int
) {
    WHITE("white", R.string.theme_white, R.string.theme_white_desc),
    BLACK("black", R.string.theme_black, R.string.theme_black_desc);

    companion object {
        /**
         * Two themes only: light and dark. Older builds offered amoled/blue/red/purple; a stored
         * preference from one of those falls through to BLACK rather than resetting the user.
         */
        fun fromId(id: String?): WinlatorThemeType = values().firstOrNull { it.id == id } ?: BLACK
    }
}

object WinlatorThemeManager {
    private const val PREF_KEY = "winlator_ui_theme"
    private val currentState = mutableStateOf(WinlatorThemeType.BLACK)
    private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        currentState.value = WinlatorThemeType.fromId(prefs.getString(PREF_KEY, WinlatorThemeType.BLACK.id))
        initialized = true
    }

    @Composable
    fun currentTheme(): WinlatorThemeType {
        val context = LocalContext.current
        ensureInitialized(context)
        return currentState.value
    }

    fun currentTheme(context: Context): WinlatorThemeType {
        ensureInitialized(context)
        return currentState.value
    }

    fun setTheme(context: Context, theme: WinlatorThemeType) {
        ensureInitialized(context)
        if (currentState.value == theme) return
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString(PREF_KEY, theme.id)
            .apply()
        currentState.value = theme
    }
}

private val BlackColors = darkColorScheme(
    primary = Color(0xFFF4F4F6), onPrimary = Color(0xFF090A0D),
    primaryContainer = Color(0xFF303138), onPrimaryContainer = Color(0xFFF7F7F8),
    secondary = Color(0xFFC7C8CF), onSecondary = Color(0xFF111217),
    secondaryContainer = Color(0xFF24252B), onSecondaryContainer = Color(0xFFE7E7EA),
    background = Color(0xFF06070A), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF101116), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF1C1D23), onSurfaceVariant = Color(0xFFA8A9B1),
    outline = Color(0xFF41434D), outlineVariant = Color(0xFF2A2B32),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)


private val WhiteColors = lightColorScheme(
    primary = Color(0xFF23252B), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E3E8), onPrimaryContainer = Color(0xFF17191E),
    secondary = Color(0xFF555861), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E8EC), onSecondaryContainer = Color(0xFF26282E),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF18191D),
    surface = Color.White, onSurface = Color(0xFF18191D),
    surfaceVariant = Color(0xFFE8E9ED), onSurfaceVariant = Color(0xFF60636B),
    outline = Color(0xFF92959D), outlineVariant = Color(0xFFD1D3D8),
    error = Color(0xFFBA1A1A), onError = Color.White
)




internal fun winlatorColorScheme(theme: WinlatorThemeType): ColorScheme = when (theme) {
    WinlatorThemeType.WHITE -> WhiteColors
    WinlatorThemeType.BLACK -> BlackColors
}

/**
 * Single accent used for primary actions (the "play" CTA) and selected states.
 *
 * The six Winlator colour schemes intentionally keep [ColorScheme.primary] near-white for the
 * dark themes, which would turn a primary action button into a white block. This accent keeps the
 * reference app's bright blue call-to-action while staying readable on every theme.
 */
private val DarkAccent = Color(0xFF1F8FFF)
private val LightAccent = Color(0xFF1F6FEB)

val LocalWinlatorAccent = staticCompositionLocalOf { DarkAccent }

internal fun winlatorAccent(theme: WinlatorThemeType): Color =
    if (theme == WinlatorThemeType.WHITE) LightAccent else DarkAccent

/**
 * MiSans (Variable Font) — the same typeface the reference "盖世游戏" app ships for its CJK + Latin
 * text. Xiaomi publishes MiSans under the SIL Open Font License, so it is safe to bundle and
 * redistribute. We point every typography slot at it so the whole UI shares one consistent face
 * instead of falling back to the device's system sans-serif.
 *
 * The file is a variable font with a `wght` axis; declaring it once per weight lets Compose pick
 * the correct instance for each [FontWeight] rather than faux-bolding.
 */
private val MisansFamily = FontFamily(
    Font(R.font.misans_vf, FontWeight.W300),
    Font(R.font.misans_vf, FontWeight.W400),
    Font(R.font.misans_vf, FontWeight.W500),
    Font(R.font.misans_vf, FontWeight.W600),
    Font(R.font.misans_vf, FontWeight.W700),
    Font(R.font.misans_vf, FontWeight.W800)
)

private val WinlatorTypography = Typography(
    displaySmall = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = MisansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

private val WinlatorShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

@Composable
fun WinlatorTheme(content: @Composable () -> Unit) {
    val theme = WinlatorThemeManager.currentTheme()
    val colors = winlatorColorScheme(theme)
    ConfigureComposeHostFocus()
    ConfigureSystemBars(theme)
    ApplyLegacyChrome(colors)
    CompositionLocalProvider(LocalWinlatorAccent provides winlatorAccent(theme)) {
        MaterialTheme(colorScheme = colors, typography = WinlatorTypography, shapes = WinlatorShapes) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                content()
            }
        }
    }
}

@Composable
fun WinZTheme(content: @Composable () -> Unit) = WinlatorTheme(content)

@Composable
private fun ConfigureComposeHostFocus() {
    val owner = LocalView.current
    DisposableEffect(owner) {
        val previousHighlight = owner.defaultFocusHighlightEnabled
        owner.defaultFocusHighlightEnabled = false
        val host = owner.parent as? AbstractComposeView
        val previousHostFocusable = host?.focusable
        val previousHostTouchFocus = host?.isFocusableInTouchMode
        val previousHostHighlight = host?.defaultFocusHighlightEnabled
        val previousDescendantFocus = host?.descendantFocusability
        val previousAccessibility = host?.importantForAccessibility
        host?.apply {
            isFocusableInTouchMode = false
            isFocusable = false
            descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
            defaultFocusHighlightEnabled = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        onDispose {
            owner.defaultFocusHighlightEnabled = previousHighlight
            host?.apply {
                isFocusableInTouchMode = previousHostTouchFocus!!
                focusable = previousHostFocusable!!
                descendantFocusability = previousDescendantFocus!!
                defaultFocusHighlightEnabled = previousHostHighlight!!
                importantForAccessibility = previousAccessibility!!
            }
        }
    }
}

@Composable
private fun ConfigureSystemBars(theme: WinlatorThemeType) {
    val activity = LocalContext.current.findActivity()
    val colors = winlatorColorScheme(theme)
    DisposableEffect(activity, theme) {
        val window = activity?.window
        if (window != null) {
            // Keep the status bar (and navigation bar) visible and let the system inset the content,
            // so the clock / battery stay readable. In-game screens keep their own immersive theme.
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            val light = theme == WinlatorThemeType.WHITE
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
        }
        onDispose { }
    }
}

@Composable
private fun ApplyLegacyChrome(colors: ColorScheme) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, colors) {
        activity?.let { host ->
            val background = colors.background.toArgb()
            val surface = colors.surface.toArgb()
            val onSurface = colors.onSurface.toArgb()

            host.window.decorView.setBackgroundColor(background)
            host.findViewById<DrawerLayout>(R.id.DrawerLayout)?.let { drawerLayout ->
                drawerLayout.setBackgroundColor(background)
                if (drawerLayout.childCount > 0) {
                    drawerLayout.getChildAt(0)?.setBackgroundColor(background)
                }
            }
            host.findViewById<View>(R.id.FLFragmentContainer)?.setBackgroundColor(background)

            val toolbar = host.findViewById<Toolbar>(R.id.Toolbar)
            toolbar?.setBackgroundColor(surface)
            toolbar?.setTitleTextColor(onSurface)
            toolbar?.navigationIcon = toolbar?.navigationIcon?.mutate()?.apply { setTint(onSurface) }
            toolbar?.menu?.let { menu ->
                for (i in 0 until menu.size()) {
                    menu.getItem(i).icon?.mutate()?.setTint(onSurface)
                }
            }

            val selectedStates = arrayOf(
                intArrayOf(AndroidR.attr.state_checked),
                intArrayOf()
            )
            val navColors = intArrayOf(colors.onSurface.toArgb(), colors.onSurfaceVariant.toArgb())
            val tint = ColorStateList(selectedStates, navColors)

            host.findViewById<BottomNavigationView>(R.id.BottomNavigation)?.let { bottom ->
                ViewCompat.setBackgroundTintList(bottom, ColorStateList.valueOf(surface))
                bottom.itemIconTintList = tint
                bottom.itemTextColor = tint
                bottom.itemRippleColor = ColorStateList.valueOf(colors.primary.copy(alpha = 0.14f).toArgb())
            }

            host.findViewById<NavigationView>(R.id.NavigationView)?.let { drawer ->
                drawer.setBackgroundColor(surface)
                drawer.itemIconTintList = tint
                drawer.itemTextColor = ColorStateList.valueOf(onSurface)
            }
        }
        onDispose { }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
