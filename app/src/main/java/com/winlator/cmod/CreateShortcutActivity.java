package com.winlator.cmod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.util.ArrayList;

/**
 * Implements the legacy "create shortcut" contract
 * ({@code android.intent.action.CREATE_SHORTCUT}).
 *
 * <p>This is the pre-Oreo way of adding a shortcut, and it is still the most reliable one on
 * launchers whose pinned-shortcut flow is broken.  The launcher (MIUI's "Add tools / Shortcuts"
 * entry, or AOSP's "Add to home screen") starts this activity, the user picks a game, and the
 * launcher itself places the resulting shortcut — no {@code requestPinShortcut} and no
 * confirmation activity involved.
 */
public class CreateShortcutActivity extends Activity {

    private static final String TAG = "CreateShortcutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<Shortcut> shortcuts;
        try {
            shortcuts = new ContainerManager(this).loadShortcuts();
        } catch (Exception e) {
            Log.e(TAG, "could not load shortcuts", e);
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        if (shortcuts.isEmpty()) {
            Log.w(TAG, "no shortcuts to offer");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        final ArrayList<Shortcut> items = shortcuts;
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            Shortcut s = items.get(i);
            names[i] = s.name != null && !s.name.isEmpty() ? s.name : s.file.getName();
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.action_home_screen)
                .setItems(names, (dialog, which) -> {
                    setResult(RESULT_OK, buildResult(items.get(which)));
                    finish();
                })
                .setOnCancelListener(dialog -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .show();
    }

    /** Builds the intent the launcher turns into a home-screen shortcut. */
    private Intent buildResult(Shortcut shortcut) {
        Intent launch = new Intent(this, XServerDisplayActivity.class);
        launch.setAction(Intent.ACTION_VIEW);
        // String extras only: launchers that persist a shortcut keep String extras and drop the
        // rest (XServerDisplayActivity.readContainerIdFromIntent() also accepts the int form).
        launch.putExtra("container_id", String.valueOf(shortcut.container.id));
        launch.putExtra("shortcut_path", shortcut.file.getPath());
        launch.putExtra("shortcut_name", shortcut.name);
        launch.putExtra("launch_source", "shortcut");

        Intent result = new Intent();
        result.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch);
        result.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcut.name);
        Bitmap icon = loadIcon(shortcut);
        if (icon != null) result.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        return result;
    }

    private Bitmap loadIcon(Shortcut shortcut) {
        try {
            File dir = new File(Environment.getExternalStorageDirectory(), "Winlator/icons");
            File file = new File(dir, FileUtils.getBasename(shortcut.file.getPath()) + ".png");
            if (file.exists()) {
                Bitmap bmp = BitmapFactory.decodeFile(file.getPath());
                if (bmp != null) return bmp;
            }
        } catch (Exception e) {
            Log.w(TAG, "could not load the shortcut icon", e);
        }
        return shortcut.icon;
    }
}
