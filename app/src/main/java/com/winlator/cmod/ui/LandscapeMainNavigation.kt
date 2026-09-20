package com.winlator.cmod.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.winlator.cmod.MainActivity
import com.winlator.cmod.R

/**
 * Keeps the system bars visible (status bar readable) while letting the app draw a normal, inset
 * layout. Previously this forced a fullscreen window; in-game screens still do that via their own
 * `AppThemeFullscreen` theme, so the main UI no longer needs to.
 */
fun applySystemBars(activity: Activity?) {
    if (activity == null) return

    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, true)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    // Respect a page that deliberately hid the status bar (the landscape library carousel);
    // otherwise this helper would immediately bring it back.
    val hideStatusBar = (activity as? MainActivity)?.isStatusBarHidden == true
    WindowInsetsControllerCompat(window, window.decorView).apply {
        if (hideStatusBar) hide(WindowInsetsCompat.Type.statusBars())
        else show(WindowInsetsCompat.Type.statusBars())
        show(WindowInsetsCompat.Type.navigationBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
}

@Composable
fun KeepLandscapeChromeHidden(activity: MainActivity?, restoreChromeOnPortrait: Boolean = true) {
    DisposableEffect(activity, restoreChromeOnPortrait) {
        val toolbar = activity?.findViewById<View>(R.id.Toolbar)
        val bottomNavigation = activity?.findViewById<View>(R.id.BottomNavigation)
        val drawer = activity?.findViewById<DrawerLayout>(R.id.DrawerLayout)
        val decor = activity?.window?.decorView

        fun forceLandscapeChrome() {
            if (activity?.resources?.configuration?.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                toolbar?.visibility = View.GONE
                bottomNavigation?.visibility = View.GONE
                drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        }

        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            forceLandscapeChrome()
            true
        }

        applySystemBars(activity)
        forceLandscapeChrome()
        decor?.viewTreeObserver?.addOnPreDrawListener(preDrawListener)

        onDispose {
            if (decor?.viewTreeObserver?.isAlive == true) {
                decor.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            }
            if (restoreChromeOnPortrait &&
                activity?.resources?.configuration?.orientation != Configuration.ORIENTATION_LANDSCAPE
            ) {
                toolbar?.visibility = View.VISIBLE
                bottomNavigation?.visibility = View.VISIBLE
                drawer?.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
        }
    }

    LaunchedEffect(activity) {
        applySystemBars(activity)
        activity?.setBottomNavigationVisible(false)
        activity?.setMainToolbarVisible(false)
        withFrameNanos { }
        applySystemBars(activity)
        activity?.setBottomNavigationVisible(false)
        activity?.setMainToolbarVisible(false)
    }
}

@Composable
fun LandscapeMainNavigation(
    activity: MainActivity?,
    selected: Int,
    title: String,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    actionDescription: String = "Action",
    onAction: (() -> Unit)? = null
) {
    KeepLandscapeChromeHidden(activity)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 20.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.weight(1f))
        if (actionIcon != null && onAction != null) {
            Destination(actionIcon, actionDescription, false, onAction)
        }
        Destination(Icons.Outlined.Home, "Library", selected == R.id.main_menu_shortcuts) {
            activity?.navigateToMainDestination(R.id.main_menu_shortcuts)
        }
        // Every non-library destination lives inside the "Mine" tab.
        Destination(Icons.Outlined.Person, "Mine", selected != R.id.main_menu_shortcuts) {
            activity?.navigateToMainDestination(R.id.main_menu_profile)
        }
    }
}

@Composable
private fun Destination(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val whiteTheme = MaterialTheme.colorScheme.background.luminance() > .65f
    val normalIcon = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground.copy(alpha = .68f)
    val iconColor = if (whiteTheme) Color.White else normalIcon
    val selectedBackground = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
    val background = if (whiteTheme) {
        Color.Black.copy(alpha = if (selected) .90f else .78f)
    } else if (selected) selectedBackground else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = background,
        contentColor = iconColor
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(icon, description, modifier = Modifier.size(23.dp))
        }
    }
}
