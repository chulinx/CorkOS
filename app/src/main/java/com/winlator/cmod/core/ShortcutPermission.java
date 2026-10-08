package com.winlator.cmod.core;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Reads the OEM-specific "create home-screen shortcut" permission.
 *
 * <p>AOSP has nothing to query here: {@code REQUEST_PIN_SHORTCUT} is confirmed by the launcher at
 * request time, so {@code isRequestPinShortcutSupported()} is the only signal the SDK exposes.
 * MIUI / HyperOS instead put a user-facing switch in front of it — 设置 → 应用设置 → 权限管理 →
 * 其他权限 → 桌面快捷方式 — backed by a private AppOps op. While that switch is off,
 * {@code ShortcutManager.requestPinShortcut()} either throws or does nothing at all, and the
 * launcher gets blamed for what is really a permission problem.
 *
 * <p>The op id lives in the OEM range, so on any other ROM the lookup reports the op as unknown and
 * this class returns {@link #UNKNOWN} rather than blocking a feature that would have worked. Only
 * an explicit {@link #DENIED} should stop the caller, and even then the UI offers an override.
 */
public final class ShortcutPermission {

    private static final String TAG = "ShortcutPermission";

    /**
     * MIUI / HyperOS AppOps op for "创建桌面快捷方式", as found in MIUI's own
     * {@code /data/system/appOps/*.xml}.
     */
    private static final int MIUI_OP_CREATE_SHORTCUT = 10017;

    /** The switch is on. */
    public static final int GRANTED = 0;
    /** The switch is off — requestPinShortcut() cannot succeed until the user turns it back on. */
    public static final int DENIED = 1;
    /** Not decided yet; the ROM shows its own prompt when the request is made. */
    public static final int ASK = 5;
    /** Not MIUI, op unknown, or the lookup is not permitted on this ROM. Never block on this. */
    public static final int UNKNOWN = -1;

    private ShortcutPermission() { }

    /** True on MIUI / HyperOS, where the AppOps op below is meaningful. */
    public static boolean isMiui() {
        try {
            Class.forName("miui.os.Build");
            return true;
        }
        catch (ClassNotFoundException ignored) { }
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        return manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco");
    }

    /**
     * @return one of {@link #GRANTED}, {@link #DENIED}, {@link #ASK} or {@link #UNKNOWN}.
     */
    public static int check(Context context) {
        if (context == null || !isMiui()) return UNKNOWN;
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return UNKNOWN;

        Integer mode = readOp(appOps, context.getApplicationInfo().uid, context.getPackageName());
        if (mode == null) return UNKNOWN;

        Log.d(TAG, "op " + MIUI_OP_CREATE_SHORTCUT + " for " + context.getPackageName()
                + " -> mode " + mode);
        if (mode == AppOpsManager.MODE_ALLOWED) return GRANTED;
        if (mode == AppOpsManager.MODE_IGNORED) return DENIED;
        if (mode == AppOpsManager.MODE_ERRORED) return UNKNOWN;
        // MODE_DEFAULT and anything ROM-specific (MIUI uses 5 for "ask") mean "not decided yet".
        return ASK;
    }

    /**
     * The int-based {@code checkOpNoThrow(int, int, String)} was public API until it was replaced
     * by the string-op form, and it is no longer part of the public SDK. The framework method is
     * still present (this op only exists on ROMs that carry it), so call it reflectively and treat
     * every failure — including hidden-API blocking — as "unknown".
     */
    private static Integer readOp(AppOpsManager appOps, int uid, String packageName) {
        try {
            Method method = AppOpsManager.class.getDeclaredMethod(
                    "checkOpNoThrow", int.class, int.class, String.class);
            method.setAccessible(true);
            Object result = method.invoke(appOps, MIUI_OP_CREATE_SHORTCUT, uid, packageName);
            return result instanceof Integer ? (Integer) result : null;
        }
        catch (Throwable t) {
            Log.w(TAG, "the OEM shortcut AppOps op is not readable here: " + t);
            return null;
        }
    }

    /**
     * Opens the screen where the user can flip the switch. MIUI's own permission editor is tried
     * first because it lands on the list that contains 桌面快捷方式; the standard app-info page is
     * the fallback and is also where a non-MIUI user would look.
     */
    public static void openPermissionSettings(Context context) {
        if (context == null) return;
        if (isMiui()) {
            Intent miui = new Intent("miui.intent.action.APP_PERM_EDITOR");
            miui.setClassName("com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity");
            miui.putExtra("extra_pkgname", context.getPackageName());
            miui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(miui);
                return;
            }
            catch (Throwable t) {
                Log.w(TAG, "MIUI permission editor unavailable, falling back to app info", t);
            }
        }
        Intent standard = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
        standard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(standard);
        }
        catch (Throwable t) {
            Log.w(TAG, "could not open the app settings page", t);
        }
    }
}
