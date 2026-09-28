package com.winlator.cmod;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.Html;
import android.text.format.Formatter;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.winlator.cmod.core.AppLocale;
import com.winlator.cmod.FileManagerFragment;
import com.winlator.cmod.R;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.ImageUtils;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.xenvironment.ImageFsInstaller;
import com.winlator.cmod.services.NotificationService;

import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(AppLocale.wrap(newBase));
    }
    public static final @IntRange(from = 1, to = 19) byte CONTAINER_PATTERN_COMPRESSION_LEVEL = 9;
    public static final int PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 500;
    public static final int PERMISSION_POST_NOTIFICATIONS_REQUEST_CODE = 501;
    public static final byte OPEN_FILE_REQUEST_CODE = 2;
    public static final byte EDIT_INPUT_CONTROLS_REQUEST_CODE = 3;
    public static final byte OPEN_DIRECTORY_REQUEST_CODE = 4;
    public static final byte OPEN_IMAGE_REQUEST_CODE = 5;
    public static final String NOTIFICATION_CHANNEL_ID = "Winlator";
    public static final int NOTIFICATION_ID = 100;
    private static final String ORIENTATION_MODE_AUTO = "auto";
    private static final String ORIENTATION_MODE_VERTICAL = "vertical";
    private static final String ORIENTATION_MODE_HORIZONTAL = "horizontal";
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigation;
    private View mainToolbar;
    public final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private boolean editInputControls = false;
    private int selectedProfileId;
    private Intent notificationService;
    private SharedPreferences sharedPreferences;
    private ContainerManager containerManager;
    private boolean isDarkMode;
    private boolean syncingBottomNavigation;
    private boolean orientationLocked;
    private String orientationMode = ORIENTATION_MODE_AUTO;

    private void createNotificationChannel() {
        String name = getString(R.string.app_name);
        String description = getString(R.string.app_name) + " XServer Messages";
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        // Persist the default value on first run so all other components
        // (dialogs, fragments) read the correct value instead of their own default
        if (!sharedPreferences.contains("dark_mode")) {
            sharedPreferences.edit().putBoolean("dark_mode", true).apply();
        }

        isDarkMode = sharedPreferences.getBoolean("dark_mode", true);

        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            setTheme(R.style.AppTheme_Dark);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);

        // Edge-to-edge: let the layout extend under the status bar, navigation bar and
        // display cutout instead of stopping at their boundaries. Combined with the transparent
        // statusBarColor / navigationBarColor in AppTheme, this kills the hard white strip that
        // otherwise shows up behind the gesture nav handle and the punch-hole camera in
        // landscape.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }

        showSystemBars();

        if (!sharedPreferences.getBoolean(OnboardingActivity.PREF_ONBOARDING_COMPLETE, false)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        notificationService = new Intent(this, NotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED))
            createNotificationChannel();

        boolean isBigPictureModeEnabled = sharedPreferences.getBoolean("enable_big_picture_mode", false);

        if (isBigPictureModeEnabled) {
            Intent intent = new Intent(MainActivity.this, BigPictureActivity.class);
            startActivity(intent);
        }

        setContentView(R.layout.main_activity);

        drawerLayout = findViewById(R.id.DrawerLayout);
        navigationView = findViewById(R.id.NavigationView);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setBackgroundColor(Color.parseColor("#0B0D12"));
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        bottomNavigation = findViewById(R.id.BottomNavigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (syncingBottomNavigation) return true;
            int target;
            if (item.getItemId() == R.id.bottom_nav_library) target = R.id.main_menu_shortcuts;
            else if (item.getItemId() == R.id.bottom_nav_profile) target = R.id.main_menu_profile;
            else return false;
            MenuItem destination = navigationView.getMenu().findItem(target);
            navigationView.setCheckedItem(target);
            return onNavigationItemSelected(destination);
        });
        getSupportFragmentManager().addOnBackStackChangedListener(this::syncChromeForCurrentFragment);
        updateStorageFooter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }

        mainToolbar = findViewById(R.id.Toolbar);
        setSupportActionBar((androidx.appcompat.widget.Toolbar) mainToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        setNavigationViewItemTextColor(navigationView, Color.WHITE);

        File winlatorDir = new File(SettingsFragment.DEFAULT_WINLATOR_PATH);
        if (!winlatorDir.exists())
            winlatorDir.mkdirs();

        containerManager = new ContainerManager(this);

        Intent intent = getIntent();
        editInputControls = intent.getBooleanExtra("edit_input_controls", false);
        if (editInputControls) {
            selectedProfileId = intent.getIntExtra("selected_profile_id", 0);
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeAsUpIndicator(R.drawable.ui_ic_back);
            }
            onNavigationItemSelected(navigationView.getMenu().findItem(R.id.main_menu_input_controls));
            navigationView.setCheckedItem(R.id.main_menu_input_controls);
        } else {
            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            int menuItemId = selectedMenuItemId > 0 ? selectedMenuItemId : R.id.main_menu_shortcuts;

            if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(false);
            onNavigationItemSelected(navigationView.getMenu().findItem(menuItemId));
            navigationView.setCheckedItem(menuItemId);
            selectBottomDestination(menuItemId);

            if (!ImageFsInstaller.installIfNeeded(this, () -> requestAppPermissions())) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                    startForegroundService(notificationService);
            }
        }
    }

    private void updateStorageFooter() {
        TextView storageView = findViewById(R.id.TVStorageUsage);
        ProgressBar storageProgress = findViewById(R.id.PBStorageUsage);
        if (storageView == null || storageProgress == null) return;

        File storageRoot = Environment.getExternalStorageDirectory();
        StatFs statFs = new StatFs(storageRoot.getPath());
        long totalBytes = statFs.getTotalBytes();
        long freeBytes = statFs.getAvailableBytes();
        long usedBytes = Math.max(0, totalBytes - freeBytes);
        int usedPercent = totalBytes > 0 ? Math.min(100, Math.round((usedBytes * 100f) / totalBytes)) : 0;

        storageView.setText(Formatter.formatShortFileSize(this, usedBytes) + " / " + Formatter.formatShortFileSize(this, totalBytes));
        storageProgress.setProgress(usedPercent);
    }

    private void showAllFilesAccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.perm_all_files_title))
                .setMessage("In order to grant access to additional storage devices such as USB storage device, the All Files Access permission must be granted. Press Okay to grant All Files Access in your Android Settings.")
                .setPositiveButton(getString(R.string.perm_okay), (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_POST_NOTIFICATIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                startForegroundService(notificationService);
        } else if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                requestAppPermissions();
            else
                finish();
        }
    }

    @Override
    public void onBackPressed() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            return;
        }
        if (editInputControls) {
            super.onBackPressed();
            return;
        }
        finish();
    }

    private void requestAppPermissions() {
        boolean hasWritePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasReadPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasManageStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
        boolean hasPostNotificationPermission = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;

        if (!hasWritePermission || !hasReadPermission) {
            String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
            return;
        }

        if (!hasPostNotificationPermission) {
            createNotificationChannel();
            String[] permissions = new String[]{Manifest.permission.POST_NOTIFICATIONS};
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_POST_NOTIFICATIONS_REQUEST_CODE);
        }

        if (!hasManageStoragePermission) {
            showAllFilesAccessDialog();
        }
    }

    public boolean isOrientationLocked() {
        return orientationLocked;
    }

    public boolean isVerticalModeEnabled() {
        return ORIENTATION_MODE_VERTICAL.equals(orientationMode);
    }

    public boolean isHorizontalModeEnabled() {
        return ORIENTATION_MODE_HORIZONTAL.equals(orientationMode);
    }

    public boolean toggleOrientationLock() {
        orientationLocked = !orientationLocked;
        applyOrientationMode();
        return orientationLocked;
    }

    public boolean toggleVerticalMode() {
        orientationMode = isVerticalModeEnabled() ? ORIENTATION_MODE_AUTO : ORIENTATION_MODE_VERTICAL;
        applyOrientationMode();
        return isVerticalModeEnabled();
    }

    public boolean toggleHorizontalMode() {
        orientationMode = isHorizontalModeEnabled() ? ORIENTATION_MODE_AUTO : ORIENTATION_MODE_HORIZONTAL;
        applyOrientationMode();
        return isHorizontalModeEnabled();
    }

    private void applyOrientationMode() {
        switch (orientationMode) {
            case ORIENTATION_MODE_VERTICAL:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case ORIENTATION_MODE_HORIZONTAL:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                setRequestedOrientation(orientationLocked
                        ? ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
                break;
        }
        invalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            // The toolbar's home button used to be a no-op for every fragment that was not in a
            // hard-coded list, so Back did nothing on Settings, Components and the rest. Decide by
            // what is actually on the back stack instead of by fragment class.
            if (getSupportFragmentManager().getBackStackEntryCount() > 0 || editInputControls) {
                onBackPressed();
            } else {
                toggleDrawer();
            }
            return true;
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
    }

    public void toggleDrawer() {
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        setDetailMode(false);
        selectBottomDestination(item.getItemId());

        if (item.getItemId() == R.id.main_menu_about) {
            showAboutDialog();
            return true;
        }

        Fragment fragment = createFragmentForDestination(item.getItemId());
        if (fragment == null) return true;

        // Tapping the already-selected root tab is intentionally a no-op. In particular, do not
        // recreate the Fragment or replay its transition animation.
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (tabIndexForDestination(item.getItemId()) >= 0
                && tabIndexForDestination(item.getItemId()) == tabIndexForFragment(
                        fragmentManager.findFragmentById(R.id.FLFragmentContainer))) {
            return true;
        }


        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        // Switching between the two bottom tabs animates like the reference app: the pages slide
        // horizontally in the direction of travel with a light cross-fade. Other destinations keep
        // the vertical transition.
        int targetTab = tabIndexForDestination(item.getItemId());
        int currentTab = tabIndexForFragment(fragmentManager.findFragmentById(R.id.FLFragmentContainer));
        if (targetTab >= 0 && currentTab >= 0 && targetTab != currentTab) {
            showTab(fragment, targetTab < currentTab);
        } else {
            show(fragment, false);
        }
        syncChromeForCurrentFragment();
        return true;
    }

    /** Position of a main tab in the bottom bar: library = 0, mine = 1; -1 for anything else. */
    private static int tabIndexForDestination(int menuItemId) {
        if (menuItemId == R.id.main_menu_shortcuts) return 0;
        if (menuItemId == R.id.main_menu_profile) return 1;
        return -1;
    }

    private static int tabIndexForFragment(Fragment fragment) {
        if (fragment instanceof ShortcutsFragment) return 0;
        if (fragment instanceof ProfileFragment) return 1;
        return -1;
    }

    private Fragment createFragmentForDestination(int menuItemId) {
        switch (menuItemId) {
            case R.id.main_menu_shortcuts:
                return new ShortcutsFragment();
            case R.id.main_menu_profile:
                return new ProfileFragment();
            case R.id.main_menu_containers:
                return new ContainersFragment();
            case R.id.main_menu_input_controls:
                return InputControlsFragment.newInstance(selectedProfileId);
            case R.id.main_menu_file_manager:
                return new FileManagerFragment();
            case R.id.main_menu_settings:
                return new SettingsFragment();
            default:
                return null;
        }
    }

    /**
     * Opens a page that belongs to the currently selected tab (the entries listed inside "Mine").
     * Unlike {@link #onNavigationItemSelected} it pushes onto the back stack so the user lands back
     * on the tab they came from, and it reveals the toolbar because the tab roots hide it.
     */
    public void navigateToSubDestination(int menuItemId) {
        if (menuItemId == R.id.main_menu_about) {
            showAboutDialog();
            return;
        }

        Fragment fragment = createFragmentForDestination(menuItemId);
        if (fragment == null) return;

        setMainToolbarVisible(true);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_down,
                        R.anim.slide_in_down, R.anim.slide_out_up)
                .addToBackStack(null)
                .replace(R.id.FLFragmentContainer, fragment)
                .commit();
        // The back stack listener re-derives the chrome once this transaction is applied.
    }

    /**
     * The two tab roots (library / mine) own the whole screen, everything else is a sub page with a
     * back arrow. Deriving the chrome from the current fragment keeps this correct after a back
     * press, which used to leave the bottom bar in the wrong state.
     */
    private void syncChromeForCurrentFragment() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.FLFragmentContainer);
        // Null while a transaction is still being applied — leave the chrome alone.
        if (current == null) return;
        boolean topLevel = current instanceof ShortcutsFragment || current instanceof ProfileFragment;
        setDetailMode(!topLevel);
        setMainToolbarVisible(!topLevel);
    }


    private void selectBottomDestination(int menuItemId) {
        if (bottomNavigation == null) return;
        int bottomId = 0;
        if (menuItemId == R.id.main_menu_shortcuts) bottomId = R.id.bottom_nav_library;
        else if (menuItemId == R.id.main_menu_profile) bottomId = R.id.bottom_nav_profile;
        // Pages reached from inside "Mine" (settings, input controls, ...) keep the current tab.
        if (bottomId != 0 && bottomNavigation.getSelectedItemId() != bottomId) {
            syncingBottomNavigation = true;
            bottomNavigation.setSelectedItemId(bottomId);
            syncingBottomNavigation = false;
        }
    }

    public void setBottomNavigationVisible(boolean visible) {
        if (bottomNavigation != null) bottomNavigation.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setMainToolbarVisible(boolean visible) {
        if (mainToolbar != null) mainToolbar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void navigateToMainDestination(int menuItemId) {
        if (navigationView == null) return;
        MenuItem destination = navigationView.getMenu().findItem(menuItemId);
        if (destination == null) return;
        navigationView.setCheckedItem(menuItemId);
        onNavigationItemSelected(destination);
    }

    /** Landscape library pages hide the status bar to reclaim vertical space. */
    private boolean statusBarHidden = false;

    /**
     * Lets a page hide the status bar (landscape library) or restore it. The navigation bar stays
     * visible either way so the user can always leave the app.
     */
    public void setStatusBarVisible(boolean visible) {
        if (statusBarHidden == !visible) return;
        statusBarHidden = !visible;
        showSystemBars();
    }

    /** Pages that call {@code applySystemBars()} must not re-show a deliberately hidden status bar. */
    public boolean isStatusBarHidden() {
        return statusBarHidden;
    }

    /**
     * The main UI keeps the system bars visible so the status bar (clock / battery / notifications)
     * stays readable. In-game screens use {@code AppThemeFullscreen} and stay immersive instead.
     */
    private void showSystemBars() {
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        if (statusBarHidden) controller.hide(WindowInsetsCompat.Type.statusBars());
        else controller.show(WindowInsetsCompat.Type.statusBars());
        controller.show(WindowInsetsCompat.Type.navigationBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showSystemBars();
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindow().getDecorView().post(this::syncChromeForCurrentFragment);
    }

    public void setDetailMode(boolean detail) {
        setBottomNavigationVisible(!detail);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(detail);
            if (detail) actionBar.setHomeAsUpIndicator(R.drawable.ui_ic_back);
        }
    }

    private void show(Fragment fragment, boolean reverse) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        // commitNow so syncChromeForCurrentFragment() can see the new fragment straight away.
        if (reverse) {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_down, R.anim.slide_out_up)
                    .replace(R.id.FLFragmentContainer, fragment)
                    .commitNow();
        } else {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_down)
                    .replace(R.id.FLFragmentContainer, fragment)
                    .commitNow();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
    }

    /** Horizontal slide + cross-fade used when switching between the two bottom tabs. */
    private void showTab(Fragment fragment, boolean toLeft) {
        int enter = toLeft ? R.anim.tab_in_left : R.anim.tab_in_right;
        int exit = toLeft ? R.anim.tab_out_right : R.anim.tab_out_left;
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(enter, exit)
                .replace(R.id.FLFragmentContainer, fragment)
                .commitNow();
        drawerLayout.closeDrawer(GravityCompat.START);
    }

    public void showAboutDialog() {
        ContentDialog dialog = new ContentDialog(this, R.layout.about_dialog);
        dialog.findViewById(R.id.LLBottomBar).setVisibility(View.GONE);

        if (isDarkMode) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
        } else {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background);
        }

        try {
            final PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

            TextView tvWebpage = dialog.findViewById(R.id.TVWebpage);
            tvWebpage.setText(Html.fromHtml("<a href=\"https://www.winlator.org\">winlator.org</a>", Html.FROM_HTML_MODE_LEGACY));
            tvWebpage.setMovementMethod(LinkMovementMethod.getInstance());

            ((TextView) dialog.findViewById(R.id.TVAppVersion)).setText(getString(R.string.version) + " " + pInfo.versionName);

            String creditsAndThirdPartyAppsHTML = String.join("<br />",
                    "Winlator Ludashi by StevenMX, pipetto-crypto (<a href=\"https://github.com/StevenMXZ/Winlator-Ludashi\">Fork</a>, <a href=\"https://github.com/Pipetto-crypto/winlator\">Fork</a>)",
                    "Big Picture Mode Music by",
                    "Dale Melvin Blevens III (Fumer)",
                    "---",
                    "Termux Package(<a href=\"https://github.com/termux/termux-packages\">github.com/termux/termux-package</a>)",
                    "Wine (<a href=\"https://www.winehq.org\">winehq.org</a>)",
                    "Box64 (<a href=\"https://github.com/ptitSeb/box64\">github.com/ptitSeb/box64</a>)",
                    "Mesa (Turnip/Zink/Wrapper) (<a href=\"https://github.com/xMeM/mesa/tree/wrapper\">github.com/xMeM/mesa</a>)",
                    "DXVK (<a href=\"https://github.com/doitsujin/dxvk\">github.com/doitsujin/dxvk</a>)",
                    "VKD3D (<a href=\"https://gitlab.winehq.org/wine/vkd3d\">gitlab.winehq.org/wine/vkd3d</a>)",
                    "D8VK (<a href=\"https://github.com/AlpyneDreams/d8vk\">github.com/AlpyneDreams/d8vk</a>)",
                    "CNC DDraw (<a href=\"https://github.com/FunkyFr3sh/cnc-ddraw\">github.com/FunkyFr3sh/cnc-ddraw</a>)",
                    "dxwrapper (<a href=\"https://github.com/elishacloud/dxwrapper\">github.com/elishacloud/dxwrapper</a>)",
                    "FEX-Emu (<a href=\"https://github.com/FEX-Emu/FEX\">github.com/FEX-Emu/FEX</a>)",
                    "libadrenotools (<a href=\"https://github.com/bylaws/libadrenotools\">github.com/bylaws/libadrenotools</a>)"
            );

            TextView tvCreditsAndThirdPartyApps = dialog.findViewById(R.id.TVCreditsAndThirdPartyApps);
            tvCreditsAndThirdPartyApps.setText(Html.fromHtml(creditsAndThirdPartyAppsHTML, Html.FROM_HTML_MODE_LEGACY));
            tvCreditsAndThirdPartyApps.setMovementMethod(LinkMovementMethod.getInstance());

            String glibcExpVersionForkHTML = String.join("<br />",
                    "longjunyu2's <a href=\"https://github.com/longjunyu2/winlator/tree/use-glibc-instead-of-proot\">(GLIBC Fork)</a>");
            TextView tvGlibcExpVersionFork = dialog.findViewById(R.id.TVGlibcExpVersionFork);
            tvGlibcExpVersionFork.setText(Html.fromHtml(glibcExpVersionForkHTML, Html.FROM_HTML_MODE_LEGACY));
            tvGlibcExpVersionFork.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        dialog.show();
    }

    private void setNavigationViewItemTextColor(NavigationView navigationView, int color) {
        for (int i = 0; i < navigationView.getMenu().size(); i++) {
            MenuItem menuItem = navigationView.getMenu().getItem(i);
            setMenuItemTextColor(menuItem, color);

            if (menuItem.hasSubMenu()) {
                for (int j = 0; j < menuItem.getSubMenu().size(); j++) {
                    MenuItem subMenuItem = menuItem.getSubMenu().getItem(j);
                    setMenuItemTextColor(subMenuItem, color);
                }
            }
        }
    }

    private void setMenuItemTextColor(MenuItem menuItem, int color) {
        SpannableString spanString = new SpannableString(menuItem.getTitle());
        spanString.setSpan(new ForegroundColorSpan(color), 0, spanString.length(), 0);
        menuItem.setTitle(spanString);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_IMAGE_REQUEST_CODE && resultCode == RESULT_OK) {
            Bitmap bitmap = ImageUtils.getBitmapFromUri(this, data.getData(), 1280);
            if (bitmap == null) return;
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(this);
            ImageUtils.save(bitmap, userWallpaperFile, Bitmap.CompressFormat.PNG, 100);
        }
    }
}
