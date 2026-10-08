package com.winlator.cmod;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.bigpicture.steamgrid.SteamGridDBApi;
import com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponse;
import com.winlator.cmod.bigpicture.steamgrid.SteamGridGridsResponseDeserializer;
import com.winlator.cmod.bigpicture.steamgrid.SteamGridSearchResponse;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.ui.shortcut.ShortcutSettingsComposeDialog;
import com.winlator.cmod.core.ExeIconExtractor;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.ShortcutPermission;
import com.winlator.cmod.ui.library.LibraryCallbacks;
import com.winlator.cmod.ui.library.LibraryComposeBinding;
import com.winlator.cmod.ui.library.LibraryComposeController;
import com.winlator.cmod.ui.library.LibraryComposeHost;
import com.winlator.cmod.ui.library.LibraryItem;
import com.winlator.cmod.ui.profile.PlaytimeSnapshot;
import com.winlator.cmod.ui.profile.PlaytimeStats;
import com.winlator.cmod.ui.settings.ContainersSettingsActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

public class ShortcutsFragment extends Fragment {
    private static final String TAG = "ShortcutsFragment";

    /**
     * Set from {@link ShortcutBroadcastReceiver} when the launcher confirms a pin request. Used to
     * detect launchers whose pin confirmation never completes (see addShortcutToScreen).
     */
    private static final java.util.concurrent.atomic.AtomicBoolean PIN_CONFIRMED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void onPinConfirmed() {
        PIN_CONFIRMED.set(true);
    }
    private static final String GRID_DEFAULT_MIGRATION = "enhanced_library_grid_default_v2";
    private static final int MENU_VIEW_MODE = 1;
    private static final int MENU_SEARCH = 2;
    private static final int MENU_FILE_MANAGER = 3;
    private static final int MENU_MORE = 4;
    private static final int MENU_LOCK_ORIENTATION = 5;
    private static final int MENU_VERTICAL_MODE = 6;
    private static final int MENU_HORIZONTAL_MODE = 7;
    private static final int MENU_GROUP_LOCK = 8;
    private static final int MENU_GROUP_ORIENTATION_MODE = 9;
    private static final String STEAMGRID_BASE_URL = "https://www.steamgriddb.com/api/v2/";
    private static String STEAMGRID_API_KEY = "0324c52513634547a7b32d6d323635d0";

    private ContainerManager manager;
    private SharedPreferences preferences;
    private LibraryComposeController libraryController;
    
    private boolean isGridView = false;
    private final ArrayList<Shortcut> allShortcuts = new ArrayList<>();
    private final Set<String> artworkRequests = Collections.synchronizedSet(new HashSet<>());

    private Shortcut shortcutForIconUpdate;
    private ActivityResultLauncher<String> iconPickerLauncher;
    private ActivityResultLauncher<String> contentPickerLauncher;
    private com.winlator.cmod.core.Callback<Uri> pendingContentPickerCallback;

    public static final int IMPORT_SHORTCUT = 1005;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The library owns its own search / sort / view controls inside the content area.
        setHasOptionsMenu(false);

        iconPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && shortcutForIconUpdate != null) {
                updateShortcutIcon(uri, shortcutForIconUpdate);
            }
        });
        contentPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            com.winlator.cmod.core.Callback<Uri> cb = pendingContentPickerCallback;
            pendingContentPickerCallback = null;
            if (cb != null) cb.call(uri);
        });
    }

    public void pickContentArchive(com.winlator.cmod.core.Callback<Uri> callback) {
        pendingContentPickerCallback = callback;
        contentPickerLauncher.launch("*/*");
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadShortcutsList();
        if (getActivity() != null && ((AppCompatActivity) getActivity()).getSupportActionBar() != null) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.library);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (manager != null && libraryController != null) loadShortcutsList();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        // The redesigned library defaults to the cover-card grid. Apply this once for users who
        // arrived from the old list-first layout, then preserve any later user choice.
        if (!preferences.getBoolean(GRID_DEFAULT_MIGRATION, false)) {
            isGridView = true;
            preferences.edit()
                    .putBoolean("shortcuts_grid_view", true)
                    .putBoolean(GRID_DEFAULT_MIGRATION, true)
                    .putBoolean("enhanced_library_migrated", true)
                    .apply();
        } else {
            isGridView = preferences.getBoolean("shortcuts_grid_view", true);
        }

        LibraryComposeBinding binding = LibraryComposeHost.create(
                requireContext(),
                isGridView,
                new LibraryCallbacks() {
                    @Override
                    public void onOpen(@NonNull String shortcutPath) {
                        Shortcut shortcut = findShortcut(shortcutPath);
                        if (shortcut != null) openGameDetails(shortcut);
                    }

                    @Override
                    public void onRun(@NonNull String shortcutPath) {
                        Shortcut shortcut = findShortcut(shortcutPath);
                        if (shortcut != null) {
                            GameLaunchTransition.show(requireActivity(), shortcut,
                                    () -> runFromShortcut(shortcut));
                        }
                    }

                    @Override
                    public void onGridViewChanged(boolean gridView) {
                        setGridView(gridView);
                    }

                    @Override
                    public void onAction(@NonNull String shortcutPath, @NonNull String action) {
                        Shortcut shortcut = findShortcut(shortcutPath);
                        if (shortcut != null) handleShortcutAction(shortcut, action);
                    }

                    @Override
                    public void onArtworkNeeded(@NonNull String shortcutPath, @NonNull String kind) {
                        Shortcut shortcut = findShortcut(shortcutPath);
                        if (shortcut != null) requestArtwork(shortcut, kind);
                    }

                    @Override
                    public void onOpenImport() {
                        navigateTo(R.id.main_menu_file_manager);
                    }

                    @Override
                    public void onOpenContainers() {
                        startActivity(new Intent(requireContext(), ContainersSettingsActivity.class));
                    }

                    @Override
                    public void onOpenComponents() {
                        Intent intent = new Intent(requireContext(), OnboardingActivity.class);
                        intent.putExtra(OnboardingActivity.EXTRA_COMPONENT_MANAGER, true);
                        startActivity(intent);
                    }
                }
        );
        libraryController = binding.getController();
        return binding.getView();
    }

    private void setGridView(boolean gridView) {
        isGridView = gridView;
        preferences.edit().putBoolean("shortcuts_grid_view", isGridView).apply();
        if (libraryController != null) libraryController.setGridView(isGridView);
    }

    private void fetchCoverFromSteamGrid(Shortcut shortcut, File destFile,
                                          Runnable onSuccess, Runnable onFail) {
        fetchArtworkFromSteamGrid(shortcut, destFile, "600x900", onSuccess, onFail);
    }

    private void fetchBannerFromSteamGrid(Shortcut shortcut, File destFile,
                                           Runnable onSuccess, Runnable onFail) {
        fetchArtworkFromSteamGrid(shortcut, destFile, "460x215", onSuccess, onFail);
    }

    private void fetchArtworkFromSteamGrid(Shortcut shortcut, File destFile, String dimensions,
                                            Runnable onSuccess, Runnable onFail) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        if (prefs.getBoolean("enable_custom_api_key", false)) {
            String custom = prefs.getString("custom_api_key", "");
            if (custom != null && !custom.isEmpty()) STEAMGRID_API_KEY = custom;
        }
        if (STEAMGRID_API_KEY.isEmpty()) {
            if (onFail != null) onFail.run();
            return;
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(STEAMGRID_BASE_URL)
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);
        api.searchGame("Bearer " + STEAMGRID_API_KEY, shortcut.name)
                .enqueue(new Callback<SteamGridSearchResponse>() {
                    @Override
                    public void onResponse(Call<SteamGridSearchResponse> call,
                                           Response<SteamGridSearchResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().data != null
                                && !response.body().data.isEmpty()) {
                            fetchSteamGridArtwork(response.body().data.get(0).id, destFile,
                                    dimensions, onSuccess, onFail);
                        } else if (onFail != null) {
                            onFail.run();
                        }
                    }

                    @Override
                    public void onFailure(Call<SteamGridSearchResponse> call, Throwable error) {
                        Log.e(TAG, "SteamGridDB search failed: " + error.getMessage());
                        if (onFail != null) onFail.run();
                    }
                });
    }

    private void fetchSteamGridArtwork(int gameId, File destFile, String dimensions,
                                        Runnable onSuccess, Runnable onFail) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(SteamGridGridsResponse.class,
                        new SteamGridGridsResponseDeserializer())
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(STEAMGRID_BASE_URL)
                .client(new OkHttpClient())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);
        api.getGridsByGameId("Bearer " + STEAMGRID_API_KEY, gameId,
                "alternate", dimensions, "static")
                .enqueue(new Callback<SteamGridGridsResponse>() {
                    @Override
                    public void onResponse(Call<SteamGridGridsResponse> call,
                                           Response<SteamGridGridsResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().data != null
                                && !response.body().data.isEmpty()) {
                            downloadAndSaveCover(response.body().data.get(0).url,
                                    destFile, onSuccess, onFail);
                        } else if (onFail != null) {
                            onFail.run();
                        }
                    }

                    @Override
                    public void onFailure(Call<SteamGridGridsResponse> call, Throwable error) {
                        Log.e(TAG, "SteamGridDB artwork failed: " + error.getMessage());
                        if (onFail != null) onFail.run();
                    }
                });
    }

    private void downloadAndSaveCover(String url, File destFile,
                                       Runnable onSuccess, Runnable onFail) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.connect();
                Bitmap bmp = BitmapFactory.decodeStream(conn.getInputStream());
                if (bmp == null) { if (onFail != null) onFail.run(); return; }

                if (destFile.getParentFile() != null) destFile.getParentFile().mkdirs();

                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                bmp.recycle();
                Log.d(TAG, "SteamGridDB cover salvo: " + destFile.getAbsolutePath());
                if (onSuccess != null) onSuccess.run();
            } catch (Exception e) {
                Log.e(TAG, "Falha ao baixar cover do SteamGridDB: " + e.getMessage());
                if (onFail != null) onFail.run();
            }
        });
    }

    private File getImagesDir(boolean isCover) {
        File targetDir = new File(Environment.getExternalStorageDirectory(), isCover ? "Winlator/covers" : "Winlator/icons");
        if (!targetDir.exists()) targetDir.mkdirs();
        
        File nomedia = new File(targetDir, ".nomedia");
        if (!nomedia.exists()) {
            try { nomedia.createNewFile(); } catch (IOException e) {}
        }
        return targetDir;
    }

    private File getBannerDir() {
        File targetDir = new File(Environment.getExternalStorageDirectory(), "Winlator/banners");
        if (!targetDir.exists()) targetDir.mkdirs();
        File nomedia = new File(targetDir, ".nomedia");
        if (!nomedia.exists()) {
            try { nomedia.createNewFile(); } catch (IOException ignored) {}
        }
        return targetDir;
    }

    public void loadShortcutsList() {
        ArrayList<Shortcut> shortcuts = manager.loadShortcuts();
        allShortcuts.clear();
        if (shortcuts != null) {
            shortcuts.removeIf(shortcut -> shortcut == null || shortcut.file == null || shortcut.file.getName().isEmpty());
            Bitmap defaultIcon = BitmapFactory.decodeResource(getResources(), R.drawable.icon_wine);
            for (Shortcut shortcut : shortcuts) {
                if (shortcut.icon == null) shortcut.icon = defaultIcon;
            }
            allShortcuts.addAll(shortcuts);
        }
        publishLibraryItems();
    }

    private Shortcut findShortcut(String shortcutPath) {
        for (Shortcut shortcut : allShortcuts) {
            if (shortcut.file != null && shortcut.file.getPath().equals(shortcutPath)) return shortcut;
        }
        return null;
    }

    private void publishLibraryItems() {
        if (libraryController == null) return;
        PlaytimeSnapshot playtime = PlaytimeStats.load(requireContext());
        ArrayList<LibraryItem> items = new ArrayList<>();
        for (Shortcut shortcut : allShortcuts) {
            String baseName = FileUtils.getBasename(shortcut.file.getPath());
            File userIcon = new File(getImagesDir(false), baseName + ".user.png");
            File autoIcon = new File(getImagesDir(false), baseName + ".png");
            File cover = new File(getImagesDir(true), baseName + ".png");
            File banner = new File(getBannerDir(), baseName + ".png");
            String iconPath = userIcon.exists() ? userIcon.getPath() :
                    (autoIcon.exists() ? autoIcon.getPath() : null);

            items.add(new LibraryItem(
                    shortcut.file.getPath(),
                    shortcut.file.getPath(),
                    shortcut.name,
                    shortcut.container != null ? shortcut.container.getName() : "",
                    cover.exists() ? cover.getPath() : null,
                    banner.exists() ? banner.getPath() : null,
                    iconPath,
                    shortcut.icon,
                    "1".equals(shortcut.getExtra("favorite", "0")),
                    parseLastRunAt(shortcut),
                    playtime.playtimeMillisFor(shortcut.name)
            ));
        }
        libraryController.setItems(items);
    }

    private void navigateTo(int menuItemId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToSubDestination(menuItemId);
        }
    }

    private long parseLastRunAt(Shortcut shortcut) {
        try {
            return Long.parseLong(shortcut.getExtra("lastRunAt", "0"));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void requestArtwork(Shortcut shortcut, String kind) {
        String baseName = FileUtils.getBasename(shortcut.file.getPath());
        File autoIcon = new File(getImagesDir(false), baseName + ".png");
        File cover = new File(getImagesDir(true), baseName + ".png");
        File banner = new File(getBannerDir(), baseName + ".png");

        if ("cover".equals(kind)) {
            requestCover(shortcut, cover, autoIcon);
        } else if ("banner".equals(kind)) {
            requestBanner(shortcut, banner);
        }
    }

    private void requestCover(Shortcut shortcut, File cover, File autoIcon) {
        final String coverKey = "cover:" + shortcut.file.getPath();
        if (!cover.exists() && artworkRequests.add(coverKey)) {
            fetchCoverFromSteamGrid(shortcut, cover, () -> {
                artworkRequests.remove(coverKey);
                if (getActivity() != null) getActivity().runOnUiThread(this::publishLibraryItems);
            }, () -> {
                artworkRequests.remove(coverKey);
                File exeFile = resolveExeFile(shortcut);
                if (exeFile != null && !autoIcon.exists()) {
                    ExeIconExtractor.extractAsync(exeFile, autoIcon, false, () -> {
                        if (getActivity() != null) getActivity().runOnUiThread(this::publishLibraryItems);
                    });
                }
            });
        }
    }

    private void requestBanner(Shortcut shortcut, File banner) {
        final String bannerKey = "banner:" + shortcut.file.getPath();
        if (!banner.exists() && artworkRequests.add(bannerKey)) {
            fetchBannerFromSteamGrid(shortcut, banner, () -> {
                artworkRequests.remove(bannerKey);
                if (getActivity() != null) getActivity().runOnUiThread(this::publishLibraryItems);
            }, () -> artworkRequests.remove(bannerKey));
        }
    }

    private void openGameDetails(Shortcut shortcut) {
        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_up, R.anim.slide_out_down, R.anim.slide_in_down, R.anim.slide_out_up)
                .addToBackStack(null)
                .replace(R.id.FLFragmentContainer, new GameDetailFragment(shortcut.file.getPath()))
                .commit();
    }

    private void updateShortcutIcon(Uri sourceUri, Shortcut shortcut) {
        try {
            File targetDir = getImagesDir(false);
            String baseName = FileUtils.getBasename(shortcut.file.getPath());
            File destFile = new File(targetDir, baseName + ".user.png");

            try (InputStream is = getContext().getContentResolver().openInputStream(sourceUri);
                 OutputStream os = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
            }

            Toast.makeText(getContext(), "Icon updated!", Toast.LENGTH_SHORT).show();
            loadShortcutsList();

        } catch (Exception e) {
            Toast.makeText(getContext(), "Error saving icon", Toast.LENGTH_SHORT).show();
        }
    }

    
    private File resolveExeFile(Shortcut item) {
        if (item.path == null || item.path.isEmpty()) return null;

        String path = item.path.replace("\\", "/").trim();

        if (path.startsWith("\"") && path.endsWith("\""))
            path = path.substring(1, path.length() - 1);

        if (path.startsWith("/")) {
            File f = new File(path);
            if (f.exists()) return f;
        }

        if (path.length() >= 3 && path.charAt(1) == ':' && path.charAt(2) == '/') {
            String drive    = path.substring(0, 1).toLowerCase();
            String relative = path.substring(3);

            if (item.container != null) {
                for (String[] entry : item.container.drivesIterator()) {
                    if (entry == null || entry.length < 2 || entry[0] == null || entry[1] == null) continue;
                    if (entry[0].replace(":", "").trim().equalsIgnoreCase(drive)) {
                        File f = new File(entry[1], relative);
                        if (f.exists()) return f;
                    }
                }
            }

            switch (drive) {
                case "c": {
                    File root = item.container != null ? item.container.getRootDir() : null;
                    if (root != null) {
                        File f = new File(root, ".wine/drive_c/" + relative);
                        if (f.exists()) return f;
                    }
                    break;
                }
                case "d": {
                    File f = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), relative);
                    if (f.exists()) return f;
                    f = new File(Environment.getExternalStorageDirectory(), relative);
                    if (f.exists()) return f;
                    break;
                }
                case "z": {
                    File f = new File("/" + relative);
                    if (f.exists()) return f;
                    break;
                }
            }
        }

        return null;
    }

    private void runFromShortcut(Shortcut shortcut) {
        Activity activity = getActivity();
        if (activity == null) return;
        shortcut.putExtra("lastRunAt", String.valueOf(System.currentTimeMillis()));
        shortcut.saveData();
        if (libraryController != null) {
            libraryController.setSelectedShortcutPath(shortcut.file.getPath());
            publishLibraryItems();
        }
        if (!XrActivity.isEnabled(getContext())) {
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", shortcut.container.id);
            intent.putExtra("shortcut_path", shortcut.file.getPath());
            intent.putExtra("shortcut_name", shortcut.name);
            intent.putExtra("disableXinput", shortcut.getExtra("disableXinput", "0"));
            intent.putExtra("native_rendering", shortcut.getRendererNative());
            activity.startActivity(intent);
        } else {
            XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }
    }

    private void handleShortcutAction(Shortcut shortcut, String action) {
        Context context = getContext();
        if (context == null) return;

        if (LibraryComposeHost.ACTION_FAVORITE.equals(action)) {
            boolean favorite = "1".equals(shortcut.getExtra("favorite", "0"));
            shortcut.putExtra("favorite", favorite ? "0" : "1");
            shortcut.saveData();
            loadShortcutsList();
        }
        else if (LibraryComposeHost.ACTION_SETTINGS.equals(action)) {
            ShortcutSettingsComposeDialog.show(this, shortcut);
        }
        else if (LibraryComposeHost.ACTION_ICON.equals(action)) {
            shortcutForIconUpdate = shortcut;
            iconPickerLauncher.launch("image/*");
        }
        else if (LibraryComposeHost.ACTION_REMOVE.equals(action)) {
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_shortcut, () -> {
                boolean fileDeleted = shortcut.file.delete();
                try {
                    String basePath = shortcut.file.getPath().substring(0, shortcut.file.getPath().lastIndexOf("."));
                    new File(basePath + ".lnk").delete();
                    new File(basePath + ".bat").delete();
                } catch (Exception ignored) {}

                if (fileDeleted) {
                    disableShortcutOnScreen(requireContext(), shortcut);
                    loadShortcutsList();
                    Toast.makeText(context, "Shortcut removed.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        else if (LibraryComposeHost.ACTION_CLONE.equals(action)) {
            ContainerManager containerManager = new ContainerManager(context);
            ArrayList<Container> containers = containerManager.getContainers();
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(getString(R.string.select_a_container));
            String[] containerNames = new String[containers.size()];
            for (int i = 0; i < containers.size(); i++) {
                containerNames[i] = containers.get(i).getName();
            }
            builder.setItems(containerNames, (dialog, which) -> {
                if (shortcut.cloneToContainer(containers.get(which))) {
                    Toast.makeText(context, "Cloned successfully.", Toast.LENGTH_SHORT).show();
                    loadShortcutsList();
                }
            });
            builder.show();
        }
        else if (LibraryComposeHost.ACTION_HOME.equals(action)) {
            Log.d(TAG, "ACTION_HOME for " + shortcut.name + " uuid='" + shortcut.getExtra("uuid") + "'");
            if (shortcut.getExtra("uuid").equals("")) shortcut.genUUID();
            Log.d(TAG, "ACTION_HOME resolved uuid='" + shortcut.getExtra("uuid") + "'");
            addShortcutToScreen(shortcut);
        }
        else if (LibraryComposeHost.ACTION_EXPORT.equals(action)) {
            exportShortcut(shortcut);
        }
    }

    private void exportShortcut(Shortcut shortcut) {
        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(getContext());
        String uriString = sharedPreferences.getString("shortcuts_export_path_uri", null);
        File shortcutsDir;

        if (uriString != null) {
            Uri folderUri = Uri.parse(uriString);
            DocumentFile pickedDir = DocumentFile.fromTreeUri(requireContext(), folderUri);
            if (pickedDir == null || !pickedDir.canWrite()) return;
            shortcutsDir = new File(FileUtils.getFilePathFromUri(requireContext(), folderUri));
        } else {
            shortcutsDir = new File(SettingsFragment.DEFAULT_SHORTCUT_EXPORT_PATH);
        }

        if (!shortcutsDir.exists() && !shortcutsDir.mkdirs()) return;
        File exportFile = new File(shortcutsDir, shortcut.file.getName());
        boolean containerIdFound = false;

        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(shortcut.file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        lines.add("container_id:" + shortcut.container.id);
                        containerIdFound = true;
                    } else {
                        lines.add(line);
                    }
                }
            }
            if (!containerIdFound) lines.add("container_id:" + shortcut.container.id);
            try (FileWriter writer = new FileWriter(exportFile, false)) {
                for (String line : lines) writer.write(line + "\n");
                writer.flush();
            }
            Toast.makeText(getContext(), exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException ignored) {}
    }

    /**
     * The intent a home-screen shortcut launches.  Every extra is a String on purpose: launchers
     * that persist a pinned shortcut (MIUI among them) keep only String extras, so an int or
     * boolean extra is dropped and the shortcut would open with "container id 0".
     * XServerDisplayActivity.readContainerIdFromIntent() accepts the int form as well.
     */
    private Intent buildShortcutLaunchIntent(int containerId, String shortcutPath, String shortLabel) {
        Intent intent = new Intent(getActivity(), XServerDisplayActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("container_id", String.valueOf(containerId));
        intent.putExtra("shortcut_path", shortcutPath);
        intent.putExtra("shortcut_name", shortLabel);
        // Marks a game launched straight from an Android home-screen shortcut, so that on exit we
        // can send the user back to the launcher instead of restarting into the app.
        intent.putExtra("launch_source", "shortcut");
        return intent;
    }

    private ShortcutInfo buildScreenShortCut(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        Intent intent = buildShortcutLaunchIntent(containerId, shortcutPath, shortLabel);
        return new ShortcutInfo.Builder(getActivity(), uuid)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(icon)
                // The owning activity must be the app's LAUNCHER activity (MainActivity), not the
                // activity the shortcut actually opens.  Launchers key a shortcut's app grouping on
                // this, and MIUI's launcher silently drops shortcuts whose owner is not the launcher
                // activity -- which is why neither the pin confirmation nor the long-press menu
                // showed anything.  The launch target stays in setIntent() below.
                .setActivity(new ComponentName(getActivity(), MainActivity.class))
                .setIntent(intent)
                .build();
    }

    private void addShortcutToScreen(Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        boolean pinSupported = shortcutManager != null && shortcutManager.isRequestPinShortcutSupported();
        Log.d(TAG, "addShortcutToScreen: manager=" + (shortcutManager != null)
                + " pinSupported=" + pinSupported);

        File iconDir = getImagesDir(false);
        File imgFile = new File(iconDir, FileUtils.getBasename(shortcut.file.getPath()) + ".png");
        Bitmap bmp = imgFile.exists() ? BitmapFactory.decodeFile(imgFile.getPath()) : shortcut.icon;
        if (bmp == null) bmp = BitmapFactory.decodeResource(getResources(), R.drawable.icon_wine);

        // MIUI / HyperOS gate home-screen shortcuts behind a per-app "桌面快捷方式" switch that
        // they expose as a private AppOps op.  While it is off requestPinShortcut() fails without
        // saying why -- sometimes even reporting pin as unsupported -- and the launcher ends up
        // looking broken.  Check the real cause first and say so.
        if (ShortcutPermission.check(requireContext()) == ShortcutPermission.DENIED) {
            Log.w(TAG, "home-screen shortcut permission is denied; explaining instead of requesting");
            showShortcutPermissionDialog(shortcut, bmp, pinSupported, true);
            return;
        }

        // NOTE: the legacy INSTALL_SHORTCUT broadcast is a dead end on modern Android --
        // ActivityManager rejects it outright ("no longer supported. It will not be delivered"),
        // even though MIUI's launcher still declares a receiver for it.  requestPinShortcut() is
        // the only supported route.
        // Several launchers do not implement pin requests. The old code just fell through in that
        // case, so tapping "add to home screen" looked like it did nothing.
        if (!pinSupported) {
            Toast.makeText(requireContext(), R.string.add_to_home_screen_unsupported,
                    Toast.LENGTH_LONG).show();
            return;
        }

        requestPinShortcut(shortcut, bmp, false);
    }

    /**
     * Explains why "add to home screen" did not work, instead of blaming the launcher.
     *
     * @param permissionKnown true when the OEM switch was actually read and found to be off; false
     *                        when the cause could only be inferred, because the lookup goes through
     *                        a private ROM op and may legitimately be unavailable.
     */
    private void showShortcutPermissionDialog(Shortcut shortcut, Bitmap iconBitmap,
                                              boolean pinSupported, boolean permissionKnown) {
        ContentDialog dialog = new ContentDialog(requireContext());
        dialog.setTitle(permissionKnown
                ? R.string.add_to_home_screen_permission_title
                : R.string.add_to_home_screen_failed_title);
        dialog.setMessage(getString(permissionKnown
                        ? R.string.add_to_home_screen_permission_message
                        : R.string.add_to_home_screen_failed_message,
                getString(R.string.app_name)));
        ((TextView) dialog.findViewById(R.id.BTConfirm))
                .setText(R.string.add_to_home_screen_open_settings);
        ((TextView) dialog.findViewById(R.id.BTCancel))
                .setText(R.string.add_to_home_screen_try_anyway);
        dialog.setOnConfirmCallback(() -> ShortcutPermission.openPermissionSettings(requireContext()));
        // Replaces ContentDialog's own cancel handler so we can act on the choice before dismissing.
        dialog.findViewById(R.id.BTCancel).setOnClickListener(v -> {
            dialog.dismiss();
            if (pinSupported) requestPinShortcut(shortcut, iconBitmap, true);
            else Toast.makeText(requireContext(), R.string.add_to_home_screen_unsupported,
                    Toast.LENGTH_LONG).show();
        });
        dialog.show();
    }

    /**
     * @param userOverrodePermission true when the user pressed "try anyway" on the permission
     *                               dialog, so a second failure does not reopen it in a loop.
     */
    private void requestPinShortcut(Shortcut shortcut, Bitmap bmp, boolean userOverrodePermission) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        final Bitmap iconBitmap = bmp;
        if (shortcutManager == null) return;

        try {
            // A callback that only fires once the launcher has really pinned the shortcut; it makes
            // "the request went out" distinguishable from "the icon is on the home screen".
            Intent callback = new Intent(ShortcutBroadcastReceiver.ACTION_PIN_RESULT)
                    .setPackage(requireContext().getPackageName());
            PendingIntent callbackIntent = PendingIntent.getBroadcast(requireContext(), 0, callback,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean requested = shortcutManager.requestPinShortcut(buildScreenShortCut(shortcut.name,
                    shortcut.name, shortcut.container.id, shortcut.file.getPath(),
                    Icon.createWithBitmap(bmp), shortcut.getExtra("uuid")),
                    callbackIntent != null ? callbackIntent.getIntentSender() : null);
            Log.d(TAG, "requestPinShortcut returned " + requested);
            // No immediate toast: "confirm in the dialog" is wrong on ROMs whose confirm page never
            // renders, which is exactly when the fallback below kicks in.  The user is told what to
            // do once we know which of the two paths actually happened.

            // Some ROMs (MIUI OS4.0 here) open their pin confirmation with an empty surface and
            // close it without pinning anything, so the callback never fires.  Fall back to a
            // dynamic shortcut, which the user can drag out of the app's long-press menu.
            PIN_CONFIRMED.set(false);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (PIN_CONFIRMED.get() || !isAdded()) return;
                Log.w(TAG, "pin was not confirmed by the launcher; publishing a dynamic shortcut");
                publishDynamicShortcut(shortcut, iconBitmap);
                reportPinFailure(shortcut, iconBitmap, userOverrodePermission);
            }, 1500);
        } catch (Exception e) {
            Log.e(TAG, "requestPinShortcut threw", e);
            reportPinFailure(shortcut, bmp, userOverrodePermission);
        }
    }

    /**
     * The pin never happened. Name the real cause: the OEM shortcut permission (the common case on
     * MIUI / HyperOS) versus a launcher whose confirmation page never completes. Only the latter is
     * about the launcher.
     */
    private void reportPinFailure(Shortcut shortcut, Bitmap iconBitmap, boolean userOverrodePermission) {
        if (!isAdded()) return;
        int permission = userOverrodePermission
                ? ShortcutPermission.GRANTED
                : ShortcutPermission.check(requireContext());

        if (permission == ShortcutPermission.DENIED) {
            showShortcutPermissionDialog(shortcut, iconBitmap, true, true);
        }
        else if (permission == ShortcutPermission.UNKNOWN && ShortcutPermission.isMiui()) {
            // No readable op on an MIUI-family ROM: the permission is still the likeliest cause,
            // and the "could not add" message says so without asserting it.
            showShortcutPermissionDialog(shortcut, iconBitmap, true, false);
        }
        else {
            // The permission is fine, so this really is the launcher not completing the pin.
            Toast.makeText(requireContext(),
                    getString(R.string.add_to_home_screen_long_press_hint,
                            getString(R.string.app_name)),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Publishes the game as a dynamic shortcut so it shows up when the user long-presses the
     * CorkOS icon, from where it can be dragged onto the home screen.  This does not depend on the
     * launcher's pin confirmation at all.
     */
    private void publishDynamicShortcut(Shortcut shortcut, Bitmap icon) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        if (shortcutManager == null) return;
        try {
            ShortcutInfo info = buildScreenShortCut(shortcut.name, shortcut.name,
                    shortcut.container.id, shortcut.file.getPath(),
                    Icon.createWithBitmap(icon), shortcut.getExtra("uuid"));
            ArrayList<ShortcutInfo> list = new ArrayList<>(shortcutManager.getDynamicShortcuts());
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(info.getId())) {
                    list.remove(i);
                    break;
                }
            }
            list.add(0, info);
            int max = shortcutManager.getMaxShortcutCountPerActivity();
            if (max > 0 && list.size() > max) list = new ArrayList<>(list.subList(0, max));
            shortcutManager.setDynamicShortcuts(list);
            Log.d(TAG, "published dynamic shortcut for " + shortcut.name);
        } catch (Exception e) {
            Log.e(TAG, "failed to publish dynamic shortcut", e);
        }
    }


    public static void disableShortcutOnScreen(Context context, Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(context, ShortcutManager.class);
        try {
            shortcutManager.disableShortcuts(Collections.singletonList(shortcut.getExtra("uuid")), context.getString(R.string.shortcut_not_available));
        } catch (Exception e) {}
    }

    public void updateShortcutOnScreen(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        try {
            for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
                if (shortcutInfo.getId().equals(uuid)) {
                    shortcutManager.updateShortcuts(Collections.singletonList(
                            buildScreenShortCut(shortLabel, longLabel, containerId, shortcutPath, icon, uuid)));
                    break;
                }
            }
        } catch (Exception e) {}
    }
}
