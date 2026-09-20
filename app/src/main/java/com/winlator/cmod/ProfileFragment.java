package com.winlator.cmod;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.AppLocale;
import com.winlator.cmod.ui.profile.PlaytimeSnapshot;
import com.winlator.cmod.ui.profile.PlaytimeStats;
import com.winlator.cmod.ui.profile.ProfileCallbacks;
import com.winlator.cmod.ui.profile.ProfileComposeHost;
import com.winlator.cmod.ui.profile.ProfileIdentity;
import com.winlator.cmod.ui.profile.ProfileIdentityStore;
import com.winlator.cmod.ui.profile.ProfileModel;
import com.winlator.cmod.ui.profile.StorageSnapshot;
import com.winlator.cmod.ui.profile.StorageStats;
import com.winlator.cmod.ui.settings.ContainersSettingsActivity;

import java.util.ArrayList;

/**
 * The "Mine" tab.
 *
 * CorkOS has no account system, so the identity shown here is a local placeholder
 * ({@link ProfileIdentityStore}) while the statistics are aggregated from real data: playtime comes
 * from the {@code playtime_stats} preferences written by the runtime, and the game count from the
 * shortcuts actually present in the library.
 */
public class ProfileFragment extends Fragment {
    private ComposeView composeView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        composeView = ProfileComposeHost.create(context, buildModel(context), createCallbacks());
        return composeView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setMainToolbarVisible(false);
        }
    }

    @Override
    public void onDestroyView() {
        composeView = null;
        super.onDestroyView();
    }

    private void refresh() {
        if (composeView != null && isAdded()) {
            ProfileComposeHost.update(composeView, buildModel(requireContext()));
        }
    }

    private ProfileModel buildModel(Context context) {
        ProfileIdentity identity = ProfileIdentityStore.load(context);
        PlaytimeSnapshot playtime = PlaytimeStats.load(context);
        StorageSnapshot storage = StorageStats.read();

        ContainerManager containerManager = new ContainerManager(context);

        ArrayList<Container> containers = containerManager.getContainers();
        int containerCount = containers != null ? containers.size() : 0;
        Container primary = containerCount > 0 ? containers.get(0) : null;

        int totalGames = 0;
        try {
            ArrayList<?> shortcuts = containerManager.loadShortcuts();
            totalGames = shortcuts != null ? shortcuts.size() : 0;
        } catch (Exception ignored) {
        }

        return new ProfileModel(
                identity.getNickname(),
                identity.getBio(),
                identity.getAvatarColor(),
                identity.getUid(),
                playtime.getTotalPlaytimeMillis(),
                playtime.getPlayedGameCount(),
                totalGames,
                playtime.getTotalPlayCount(),
                containerCount,
                primary != null ? primary.getName() : null,
                storage.getUsedBytes(),
                storage.getTotalBytes(),
                appVersion(context),
                describeRuntime(primary),
                AppLocale.currentTag(context)
        );
    }

    private String describeRuntime(Container container) {
        if (container == null) return getString(R.string.runtime_wine_on_android);
        String wine = container.getWineVersion();
        String emulator = container.getEmulator();
        if (emulator == null || emulator.isEmpty()) return wine;
        return wine + " · " + emulator;
    }

    private String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName != null ? info.versionName : "";
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private ProfileCallbacks createCallbacks() {
        return new ProfileCallbacks() {
            @Override
            public void onOpenSettings() {
                navigate(R.id.main_menu_settings);
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

            @Override
            public void onOpenInputControls() {
                navigate(R.id.main_menu_input_controls);
            }

            @Override
            public void onOpenImportGames() {
                navigate(R.id.main_menu_file_manager);
            }

            @Override
            public void onOpenAbout() {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showAboutDialog();
                }
            }

            @Override
            public void onSaveIdentity(String nickname, String bio, int avatarColor) {
                ProfileIdentityStore.save(requireContext(), nickname, bio, avatarColor);
                refresh();
            }

            @Override
            public void onLanguageChanged(String tag) {
                AppLocale.setLanguage(requireContext(), tag);
                // Locales are applied in attachBaseContext, so the activity must be rebuilt.
                // Keep the user on the "Mine" tab instead of falling back to the library.
                requireActivity().getIntent().putExtra("selected_menu_item_id", R.id.main_menu_profile);
                requireActivity().recreate();
            }
        };
    }

    private void navigate(int menuItemId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToSubDestination(menuItemId);
        }
    }
}
