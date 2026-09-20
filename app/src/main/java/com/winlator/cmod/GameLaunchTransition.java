package com.winlator.cmod;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.core.FileUtils;

import java.io.File;

/**
 * Short visual bridge between pressing Play and handing control to the game activity.
 *
 * The emulator startup can take a moment. Showing the game's own artwork here makes the transition
 * feel intentional instead of leaving the detail page frozen while the next activity is prepared.
 */
public final class GameLaunchTransition {
    private static final long TRANSITION_DELAY_MS = 550L;

    private GameLaunchTransition() {
    }

    public static void show(@NonNull Activity activity, @NonNull Shortcut shortcut,
                            @NonNull Runnable onReady) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            onReady.run();
            return;
        }

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(createContent(activity, shortcut));

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.24f;
            window.setAttributes(params);
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }

        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) dialog.dismiss();
            onReady.run();
        }, TRANSITION_DELAY_MS);
    }

    private static View createContent(Activity activity, Shortcut shortcut) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(Color.BLACK);

        ImageView artwork = new ImageView(activity);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = loadArtwork(shortcut);
        if (bitmap != null) {
            artwork.setImageBitmap(bitmap);
            artwork.setAlpha(0.62f);
        }
        root.addView(artwork, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        View shade = new View(activity);
        shade.setBackgroundColor(Color.argb(135, 0, 0, 0));
        root.addView(shade, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout status = new LinearLayout(activity);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        status.setPadding(32, 26, 32, 26);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(Color.argb(190, 12, 12, 16));
        panel.setCornerRadius(24);
        status.setBackground(panel);

        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        status.addView(progress, new LinearLayout.LayoutParams(42, 42));

        TextView title = new TextView(activity);
        title.setText(shortcut.name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = 16;
        status.addView(title, titleParams);

        TextView loading = new TextView(activity);
        loading.setText(R.string.starting_up);
        loading.setTextColor(Color.WHITE);
        loading.setAlpha(0.72f);
        loading.setTextSize(14);
        loading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        loadingParams.topMargin = 5;
        status.addView(loading, loadingParams);

        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        statusParams.leftMargin = 24;
        statusParams.rightMargin = 24;
        root.addView(status, statusParams);
        return root;
    }

    private static Bitmap loadArtwork(Shortcut shortcut) {
        try {
            if (shortcut.file == null) return shortcut.icon;
            String baseName = FileUtils.getBasename(shortcut.file.getPath());
            File root = Environment.getExternalStorageDirectory();
            File banner = new File(root, "Winlator/banners/" + baseName + ".png");
            File cover = new File(root, "Winlator/covers/" + baseName + ".png");
            if (banner.isFile()) return BitmapFactory.decodeFile(banner.getPath());
            if (cover.isFile()) return BitmapFactory.decodeFile(cover.getPath());
        } catch (Exception ignored) {
        }
        return shortcut.icon;
    }
}
