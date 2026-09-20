package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.ui.library.GameDetailCallbacks;
import com.winlator.cmod.ui.library.GameDetailComposeHost;
import com.winlator.cmod.ui.library.GameDetailModel;
import com.winlator.cmod.ui.profile.PlaytimeSnapshot;
import com.winlator.cmod.ui.profile.PlaytimeStats;
import com.winlator.cmod.ui.shortcut.ShortcutSettingsComposeDialog;

import java.io.File;

public class GameDetailFragment extends Fragment {
    private final String shortcutPath;
    private Shortcut shortcut;

    public GameDetailFragment() {
        this("");
    }

    public GameDetailFragment(String shortcutPath) {
        this.shortcutPath = shortcutPath;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
                             @Nullable Bundle savedInstanceState) {
        ContainerManager manager = new ContainerManager(requireContext());
        for (Shortcut candidate : manager.loadShortcuts()) {
            if (candidate != null && candidate.file != null
                    && candidate.file.getPath().equals(shortcutPath)) {
                shortcut = candidate;
                break;
            }
        }

        if (shortcut == null) {
            getParentFragmentManager().popBackStack();
            return new FrameLayout(requireContext());
        }

        ((AppCompatActivity) requireActivity()).getSupportActionBar().setTitle(shortcut.name);

        View content = GameDetailComposeHost.create(
                requireContext(),
                buildModel(),
                new GameDetailCallbacks() {
                    @Override
                    public void onPlay() {
                        runShortcut();
                    }

                    @Override
                    public void onConfigure() {
                        ShortcutSettingsComposeDialog.show(GameDetailFragment.this, shortcut);
                    }

                    @Override
                    public void onArguments() {
                        runContainer();
                    }

                    @Override
                    public void onGameFolder() {
                        Toast.makeText(requireContext(), shortcut.container.getDesktopDir().getPath(),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFavorite(boolean favorite) {
                        shortcut.putExtra("favorite", favorite ? "1" : "0");
                        shortcut.saveData();
                    }

                    @Override
                    public void onRemove() {
                        ContentDialog.confirm(requireContext(), R.string.do_you_want_to_remove_this_shortcut, () -> {
                            if (shortcut.file.delete()) getParentFragmentManager().popBackStack();
                        });
                    }

                    @Override
                    public void onNoteChanged(String note) {
                        shortcut.putExtra("note", note == null ? "" : note);
                        shortcut.saveData();
                    }
                }
        );
        content.post(this::applyDetailChrome);
        return content;
    }

    private GameDetailModel buildModel() {
        String baseName = FileUtils.getBasename(shortcut.file.getPath());
        File root = Environment.getExternalStorageDirectory();
        File banner = new File(root, "Winlator/banners/" + baseName + ".png");
        File cover = new File(root, "Winlator/covers/" + baseName + ".png");
        File userIcon = new File(root, "Winlator/icons/" + baseName + ".user.png");
        File autoIcon = new File(root, "Winlator/icons/" + baseName + ".png");
        String iconPath = userIcon.exists() ? userIcon.getPath()
                : (autoIcon.exists() ? autoIcon.getPath() : null);

        PlaytimeSnapshot playtime = PlaytimeStats.load(requireContext());
        long lastRunAt = 0L;
        try {
            lastRunAt = Long.parseLong(shortcut.getExtra("lastRunAt", "0"));
        } catch (NumberFormatException ignored) {
        }

        return new GameDetailModel(
                shortcut.name,
                shortcut.container != null ? shortcut.container.getName() : "",
                buildRuntimeLabel(),
                buildRendererLabel(),
                shortcut.container != null && shortcut.container.getEmulator() != null
                        ? shortcut.container.getEmulator() : "Box64",
                shortcut.container != null ? shortcut.container.getScreenSize() : "",
                shortcut.path != null ? shortcut.path : "",
                cover.exists() ? cover.getPath() : null,
                banner.exists() ? banner.getPath() : null,
                iconPath,
                shortcut.icon,
                "1".equals(shortcut.getExtra("favorite", "0")),
                playtime.playtimeMillisFor(shortcut.name),
                playtime.playCountFor(shortcut.name),
                lastRunAt,
                shortcut.getExtra("note", "")
        );
    }

    private String buildRuntimeLabel() {
        String runtime = shortcut.container.getWineVersion();
        try {
            ContentsManager contents = new ContentsManager(requireContext());
            contents.syncContents();
            WineInfo info = WineInfo.fromIdentifier(requireContext(), contents, runtime);
            String version = info.fullVersion();
            if (version.endsWith(".0")) version = version.substring(0, version.length() - 2);
            runtime = ("proton".equalsIgnoreCase(info.type) ? "Proton " : "Wine ") + version + " " + info.getArch();
        } catch (Exception ignored) {
        }
        return runtime;
    }

    private String buildRendererLabel() {
        return shortcut.getUseDisplayX() ? "DisplayX" : shortcut.getRendererNative() ? "EGL" : "Vulkan";
    }

    private void runShortcut() {
        Activity activity = requireActivity();
        GameLaunchTransition.show(activity, shortcut, this::launchShortcutNow);
    }

    private void launchShortcutNow() {
        Activity activity = requireActivity();
        if (!XrActivity.isEnabled(requireContext())) {
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

    private void runContainer() {
        Activity activity = requireActivity();
        if (!XrActivity.isEnabled(requireContext())) {
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", shortcut.container.id);
            activity.startActivity(intent);
        } else {
            XrActivity.openIntent(activity, shortcut.container.id, null);
        }
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void applyDetailChrome() {
        if (!(getActivity() instanceof MainActivity)) return;
        MainActivity activity = (MainActivity) getActivity();
        activity.setDetailMode(true);
        if (isLandscape()) {
            activity.setBottomNavigationVisible(false);
            activity.setMainToolbarVisible(false);
        } else {
            activity.setMainToolbarVisible(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyDetailChrome();
    }
}
