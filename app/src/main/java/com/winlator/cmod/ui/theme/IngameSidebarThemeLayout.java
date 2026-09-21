package com.winlator.cmod.ui.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.TextViewCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.ui.FpsLimiterControl;

public class IngameSidebarThemeLayout extends FrameLayout {
    private int background;
    private int surfaceVariant;
    private int onSurface;
    private int onSurfaceVariant;
    private int primary;
    private int primaryContainer;
    /** Hairline between rows; ~10% white reads as a divider on both the panel and the rail. */
    private static final int DIVIDER = 0x1AFFFFFF;

    public IngameSidebarThemeLayout(Context context) {
        super(context);
        applyChosenTheme(context);
    }

    public IngameSidebarThemeLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        applyChosenTheme(context);
    }

    public IngameSidebarThemeLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyChosenTheme(context);
    }

    /**
     * Applies the single in-game sidebar palette.
     *
     * The sidebar used to offer six palettes, but they only overrode colorAccent, so Material
     * components (Switch/SeekBar) picked up the app theme's colorPrimary instead -- which on MIUI
     * resolves to the wallpaper's dynamic colour and produced an arbitrary accent. There is one
     * palette now (see ingame_sidebar_themes.xml); the legacy preference values are ignored.
     */
    public static void applyChosenTheme(Context context) {
        context.getTheme().applyStyle(R.style.IngameSidebarTheme_Black, true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        readPalette();
        setBackgroundColor(background);
        detachRailFromScrollingContent();

        hideLegacyRailAndSetContentGutter();

        replaceLegacyFpsLimiter();
        applyCompactPremiumLayout();
        localizeSidebarText(this);
        normalizeLegacyTree(this);
        forceKnownLegacyIconTints();
        fitMetricText();

        post(() -> {
            hideLegacyRailAndSetContentGutter();
            localizeSidebarText(this);
            normalizeLegacyTree(this);
            forceKnownLegacyIconTints();
        });
        postDelayed(() -> {
            localizeSidebarText(this);
            normalizeLegacyTree(this);
            forceKnownLegacyIconTints();
            wrapLegacySpinnerAdapters(this);
        }, 500);
    }

    /** Hide the original left rail; only the fixed right rail (a sibling in the drawer XML) remains. */
    private void hideLegacyRailAndSetContentGutter() {
        if (getChildCount() == 0 || !(getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup legacyRoot = (ViewGroup) getChildAt(0);
        // left_sidebar_original has: old rail, divider, scrolling content. Do not use a child-count
        // threshold here: OEM inflater wrappers can omit the divider node.
        if (legacyRoot.getChildCount() >= 1) legacyRoot.getChildAt(0).setVisibility(View.GONE);
        if (legacyRoot.getChildCount() >= 2) legacyRoot.getChildAt(1).setVisibility(View.GONE);
        legacyRoot.setPadding(dp(16), legacyRoot.getPaddingTop(),
                dp(12), legacyRoot.getPaddingBottom());
    }

    /** Keep the icon rail fixed while only the settings content scrolls. */
    private void detachRailFromScrollingContent() {
        if (getChildCount() < 2) return;
        View rail = getChildAt(0);
        ViewGroup scrollingParent = getParent() instanceof ViewGroup
                ? (ViewGroup) getParent() : null;
        if (scrollingParent == null || !(scrollingParent.getParent() instanceof ViewGroup)) return;
        ViewGroup drawerFrame = (ViewGroup) scrollingParent.getParent();
        if (rail.getParent() != this || drawerFrame.findViewById(R.id.IngameSidebarRail) != null) return;

        removeView(rail);
        FrameLayout.LayoutParams railParams = new FrameLayout.LayoutParams(
                dp(58), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END);
        railParams.topMargin = dp(10);
        railParams.bottomMargin = dp(10);
        railParams.rightMargin = dp(6);
        drawerFrame.addView(rail, railParams);
    }

    /**
     * The legacy sidebar layout predates AppLocale and contains literal English labels. Convert
     * only known display labels here; configuration values and technical names are deliberately
     * left untouched.
     */
    private void localizeSidebarText(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            String value = String.valueOf(textView.getText());
            int resource = sidebarStringId(value);
            if (resource != 0) textView.setText(resource);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                localizeSidebarText(group.getChildAt(i));
            }
        }
    }

    private int sidebarStringId(String value) {
        switch (value) {
            case "Rendering": return R.string.sidebar_rendering;
            case "FPS Limiter": return R.string.sidebar_fps_limiter;
            case "Super Resolution": return R.string.sidebar_super_resolution;
            case "Upscaler Mode": return R.string.sidebar_upscaler_mode;
            case "Sharpness": return R.string.sidebar_sharpness;
            case "Post Effect": return R.string.sidebar_post_effect;
            case "ReShade": return R.string.sidebar_reshade;
            case "Effect": return R.string.sidebar_effect;
            case "Strength": return R.string.sidebar_strength;
            case "Frame Generation": return R.string.sidebar_frame_generation;
            case "Save Preset": return R.string.sidebar_save_preset;
            case "Display and Effects": return R.string.sidebar_display_effects;
            case "Picture in Picture": return R.string.sidebar_pip;
            case "Toggle Fullscreen": return R.string.sidebar_toggle_fullscreen;
            case "Magnifier": return R.string.sidebar_magnifier;
            case "Soft Stretch": return R.string.sidebar_soft_stretch;
            case "Controls": return R.string.sidebar_controls;
            case "Touch Controls Opacity": return R.string.sidebar_touch_opacity;
            case "Show Keyboard": return R.string.sidebar_show_keyboard;
            case "Vibration": return R.string.sidebar_vibration;
            case "Relative Mouse": return R.string.sidebar_relative_mouse;
            case "Disable Mouse": return R.string.sidebar_disable_mouse;
            case "HUD": return R.string.sidebar_hud;
            case "Enable HUD": return R.string.sidebar_enable_hud;
            case "Style": return R.string.sidebar_style;
            case "HUD Metrics": return R.string.sidebar_hud_metrics;
            case "FPS": return R.string.sidebar_fps;
            case "GPU": return R.string.sidebar_gpu;
            case "CPU": return R.string.sidebar_cpu;
            case "RAM": return R.string.sidebar_ram;
            case "Batt/Temp": return R.string.sidebar_batt_temp;
            case "GPU Name": return R.string.sidebar_gpu_name;
            case "GPU Usage": return R.string.sidebar_gpu_usage;
            case "CPU Usage": return R.string.sidebar_cpu_usage;
            case "CPU Temp": return R.string.sidebar_cpu_temp;
            case "Power": return R.string.sidebar_power;
            case "Battery Temp": return R.string.sidebar_battery_temp;
            case "Charge State": return R.string.sidebar_charge_state;
            case "Processes": return R.string.sidebar_process_count;
            case "Renderer": return R.string.sidebar_renderer;
            case "HUD Size": return R.string.sidebar_hud_size;
            case "HUD Opacity": return R.string.sidebar_hud_opacity;
            case "Reset HUD": return R.string.sidebar_reset_hud;
            case "Show Logs": return R.string.sidebar_show_logs;
            case "Task Manager": return R.string.sidebar_task_manager;
            case "Memory": return R.string.sidebar_memory;
            case "+ New Task": return R.string.sidebar_new_task;
            case "PERFORMANCE": return R.string.sidebar_performance_section;
            case "IMAGE QUALITY": return R.string.sidebar_image_quality_section;
            case "PRESETS": return R.string.sidebar_presets_section;
            case "GENERAL": return R.string.sidebar_general_section;
            case "APPEARANCE": return R.string.sidebar_appearance_section;
            case "ACTIONS": return R.string.sidebar_actions_section;
            case "TOUCH CONTROLS": return R.string.sidebar_touch_section;
            case "MOUSE": return R.string.sidebar_mouse_section;
            case "MORE": return R.string.sidebar_more_section;
            default: return 0;
        }
    }

    /** Re-run after a panel creates dynamic HUD/task-manager labels. */
    public void refreshLocalizedText() {
        hideLegacyRailAndSetContentGutter();
        localizeSidebarText(this);
        normalizeLegacyTree(this);
        forceKnownLegacyIconTints();
    }

    private void replaceLegacyFpsLimiter() {
        View oldSpinner = findViewById(R.id.SPNativeFPS);
        if (oldSpinner == null) return;
        if (!(oldSpinner.getParent() instanceof ViewGroup)) return;

        ViewGroup oldRow = (ViewGroup) oldSpinner.getParent();
        if (!(oldRow.getParent() instanceof ViewGroup)) return;
        ViewGroup holder = (ViewGroup) oldRow.getParent();
        int index = holder.indexOfChild(oldRow);

        int topMargin = 0;
        ViewGroup.LayoutParams oldParams = oldRow.getLayoutParams();
        if (oldParams instanceof ViewGroup.MarginLayoutParams) {
            topMargin = ((ViewGroup.MarginLayoutParams) oldParams).topMargin;
        }

        holder.removeView(oldRow);
        FpsLimiterControl control = new FpsLimiterControl(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        holder.addView(control, Math.max(0, index), params);
    }

    private void applyCompactPremiumLayout() {
        // Controls was one undifferentiated list of nine rows. Group it the same way the picture
        // section is grouped: nothing is removed, the rows just get headings and hairlines so the
        // panel is scannable.
        View inputControls = findViewById(R.id.LLSidebarInputControls);
        insertSectionLabelBefore(inputControls, "TOUCH CONTROLS");
        flattenSection(inputControls);
        applyGaishiRows(inputControls);

        View relativeMouse = findViewById(R.id.SWRelativeMouse);
        insertSectionLabelBefore(relativeMouse, "MOUSE");
        View disableMouse = findViewById(R.id.SWDisableMouse);
        if (relativeMouse != null && disableMouse != null
                && relativeMouse.getParent() == disableMouse.getParent()) {
            applyGaishiRows((View) relativeMouse.getParent());
        }

        View subKeyboard = findViewById(R.id.BTSubKeyboard);
        insertSectionLabelBefore(subKeyboard, "MORE");
        View subVibration = findViewById(R.id.BTSubVibration);
        if (subKeyboard != null && subVibration != null
                && subKeyboard.getParent() == subVibration.getParent()) {
            applyGaishiRows((View) subKeyboard.getParent());
        }

        FpsLimiterControl fps = findFirstFpsLimiter(this);
        insertSectionLabelBefore(fps, "PERFORMANCE");

        View imageQuality = findViewById(R.id.LLStandardOptions);
        insertSectionLabelBefore(imageQuality, "IMAGE QUALITY");
        flattenSection(imageQuality);
        applyGaishiRows(imageQuality);

        View frameGen = findViewById(R.id.LLFrameGenOptions);
        flattenSection(frameGen);
        applyGaishiRows(frameGen);

        View savePreset = findViewById(R.id.BTSaveGraphicsPreset);
        insertSectionLabelBefore(savePreset, "PRESETS");
        compactActionRow(savePreset);

        View hudStyle = findViewById(R.id.LLHudStyleRow);
        if (hudStyle != null && hudStyle.getParent() instanceof LinearLayout) {
            LinearLayout hudParent = (LinearLayout) hudStyle.getParent();
            int styleIndex = hudParent.indexOfChild(hudStyle);
            View enableHud = previousContentChild(hudParent, styleIndex);
            insertSectionLabelBefore(enableHud, "GENERAL");
            insertSectionLabelBefore(hudStyle, "APPEARANCE");

            flattenSection(enableHud);
            flattenSection(hudStyle);
            applyGaishiRows(enableHud);
            applyGaishiRows(hudStyle);

            // The HUD metric checkboxes live in their own card; flatten it too so the whole
            // section reads as one list rather than a card inside a list.
            View modernHud = findViewById(R.id.LLModernHudOptions);
            flattenSection(modernHud);
            applyGaishiRows(modernHud);

            TextView resetText = findTextView(this, "Reset HUD");
            View resetRow = directChildUnder(hudParent, resetText);
            insertSectionLabelBefore(resetRow, "ACTIONS");
            compactActionRow(resetRow);
        }
    }

    private void flattenSection(View view) {
        if (view == null) return;
        view.setBackground(null);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            group.setPadding(0, group.getPaddingTop() > 0 ? dp(2) : 0,
                    0, group.getPaddingBottom() > 0 ? dp(2) : 0);
        }
    }

    private View previousContentChild(LinearLayout parent, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != GONE) return child;
        }
        return null;
    }

    private void insertSectionLabelBefore(View target, String label) {
        if (target == null || !(target.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) target.getParent();
        int index = parent.indexOfChild(target);
        if (index < 0) return;

        if (index > 0) {
            Object tag = parent.getChildAt(index - 1).getTag();
            if (("winz-section-" + label).equals(tag)) return;
        }

        TextView section = new TextView(getContext());
        section.setTag("winz-section-" + label);
        section.setText(label);
        section.setTextColor(onSurfaceVariant);
        section.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        section.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        section.setLetterSpacing(0.08f);
        section.setAllCaps(false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(18);
        params.bottomMargin = dp(8);
        parent.addView(section, index, params);

        ViewGroup.LayoutParams targetParams = target.getLayoutParams();
        if (targetParams instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) targetParams).topMargin = 0;
            target.setLayoutParams(targetParams);
        }
    }

    private void compactActionRow(View row) {
        if (row instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) row;
            layout.setGravity(Gravity.CENTER_VERTICAL);
            layout.setPadding(dp(16), layout.getPaddingTop(), dp(16), layout.getPaddingBottom());
        }
    }

    /**
     * Gaishi-style rows: one hairline between consecutive settings rows, and a tighter row height.
     *
     * The sidebar used to group rows inside cards; those cards are removed by flattenSection(),
     * so a hairline is what gives the list its rhythm. Dividers carry a tag so repeated passes
     * (the layout patches itself again after layout and once more after 500ms) do not stack them.
     */
    private void applyGaishiRows(View section) {
        if (!(section instanceof LinearLayout)) return;
        LinearLayout group = (LinearLayout) section;

        for (int i = group.getChildCount() - 1; i > 0; i--) {
            View child = group.getChildAt(i);
            if (!isContentRow(child)) continue;
            tightenRow(child);
            View prev = group.getChildAt(i - 1);
            if (prev != null && "winz-divider".equals(prev.getTag())) continue;
            // No hairline straight after a section heading; the heading already separates.
            if (prev != null && prev.getTag() instanceof String
                    && ((String) prev.getTag()).startsWith("winz-section-")) continue;
            View line = new View(getContext());
            line.setTag("winz-divider");
            line.setBackgroundColor(DIVIDER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, (int) (getResources().getDisplayMetrics().density * 0.5f)));
            group.addView(line, i, lp);
        }
    }

    private boolean isContentRow(View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) return false;
        Object tag = v.getTag();
        return !(tag instanceof String && ((String) tag).startsWith("winz-"));
    }

    private void tightenRow(View row) {
        row.setMinimumHeight((int) dp(42));
        if (row instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) row;
            g.setPadding(g.getPaddingLeft(), (int) dp(2), g.getPaddingRight(), (int) dp(2));
        }
    }

    private FpsLimiterControl findFirstFpsLimiter(View view) {
        if (view instanceof FpsLimiterControl) return (FpsLimiterControl) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                FpsLimiterControl found = findFirstFpsLimiter(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private TextView findTextView(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findTextView(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View directChildUnder(ViewGroup ancestor, View descendant) {
        if (ancestor == null || descendant == null) return null;
        View current = descendant;
        while (current != null && current.getParent() instanceof View) {
            if (current.getParent() == ancestor) return current;
            current = (View) current.getParent();
        }
        return null;
    }

    private void readPalette() {
        background = resolveColor(R.attr.ingameSidebarBackground, Color.BLACK);
        surfaceVariant = resolveColor(R.attr.ingameSidebarSurfaceVariant, Color.rgb(28, 29, 35));
        onSurface = resolveColor(R.attr.ingameSidebarOnSurface, Color.WHITE);
        onSurfaceVariant = resolveColor(R.attr.ingameSidebarOnSurfaceVariant, Color.LTGRAY);
        primary = resolveColor(R.attr.ingameSidebarPrimary, Color.WHITE);
        primaryContainer = resolveColor(R.attr.ingameSidebarPrimaryContainer, surfaceVariant);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attr, value, true)) return value.data;
        return fallback;
    }

    private void normalizeLegacyTree(View view) {
        Drawable drawable = view.getBackground();
        if (drawable instanceof ColorDrawable) {
            int color = ((ColorDrawable) drawable).getColor();
            if (color == Color.BLACK || color == Color.rgb(3, 8, 13)) {
                view.setBackgroundColor(background);
            } else if (color == Color.rgb(14, 34, 49) || color == Color.rgb(15, 45, 66)) {
                view.setBackgroundColor(surfaceVariant);
            }
        }

        if (view instanceof TextView) {
            TextView text = (TextView) view;
            int current = text.getCurrentTextColor();
            if (current == Color.WHITE || current == Color.rgb(238, 247, 255)) {
                text.setTextColor(onSurface);
            } else if (isLegacyBlue(current)) {
                text.setTextColor(primary);
            } else if (current == Color.rgb(221, 246, 255)) {
                text.setTextColor(onSurfaceVariant);
            }
        }

        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            ColorStateList tint = ImageViewCompat.getImageTintList(image);
            if (tint != null) {
                int current = tint.getDefaultColor();
                if (isLegacyBlue(current)) {
                    ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(primary));
                } else if (current == Color.rgb(221, 246, 255)
                        || current == Color.rgb(238, 247, 255)) {
                    ImageViewCompat.setImageTintList(image, ColorStateList.valueOf(onSurface));
                }
            }
        }

        if (view instanceof Switch) {
            Switch toggle = (Switch) view;
            int[][] states = new int[][] {
                    new int[] { android.R.attr.state_checked },
                    new int[] { }
            };
            // Keep the track visible on near-black surfaces; an almost-black track makes a Switch
            // look like a lone white dot. The off state uses the outline token, while the on state
            // gets a filled accent container.
            boolean lightSidebar = Color.luminance(background) > 0.5f;
            int offThumb = lightSidebar ? Color.rgb(105, 111, 122) : onSurfaceVariant;
            int offTrack = lightSidebar ? Color.rgb(190, 196, 205) : surfaceVariant;
            int onTrack = lightSidebar ? Color.rgb(171, 183, 198) : primaryContainer;
            toggle.setThumbTintList(new ColorStateList(states,
                    new int[] { primary, offThumb }));
            toggle.setTrackTintList(new ColorStateList(states,
                    new int[] { onTrack, offTrack }));
            toggle.setMinimumWidth(dp(52));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                normalizeLegacyTree(group.getChildAt(i));
            }
        }
    }

    private void forceKnownLegacyIconTints() {
        ImageView inputSettings = findViewById(R.id.BTInputControlsSettings);
        if (inputSettings != null) {
            ImageViewCompat.setImageTintList(inputSettings, ColorStateList.valueOf(primary));
        }
    }

    private void wrapLegacySpinnerAdapters(View view) {
        if (view instanceof Spinner) {
            Spinner spinner = (Spinner) view;
            SpinnerAdapter adapter = spinner.getAdapter();
            if (adapter != null && !(adapter instanceof ThemeSpinnerAdapter)) {
                int selected = spinner.getSelectedItemPosition();
                spinner.setAdapter(new ThemeSpinnerAdapter(adapter));
                if (selected >= 0 && selected < spinner.getCount()) spinner.setSelection(selected, false);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) wrapLegacySpinnerAdapters(group.getChildAt(i));
        }
    }

    private View themeSpinnerView(View view, boolean dropdown) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(onSurface);
            text.setSingleLine(true);
            text.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            if (dropdown) {
                text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                text.setBackgroundColor(surfaceVariant);
            }
        } else {
            normalizeLegacyTree(view);
            if (dropdown && view.getBackground() instanceof ColorDrawable) {
                view.setBackgroundColor(surfaceVariant);
            }
        }
        return view;
    }

    private final class ThemeSpinnerAdapter implements SpinnerAdapter {
        private final SpinnerAdapter delegate;

        private ThemeSpinnerAdapter(SpinnerAdapter delegate) {
            this.delegate = delegate;
        }

        @Override public int getCount() { return delegate.getCount(); }
        @Override public Object getItem(int position) { return delegate.getItem(position); }
        @Override public long getItemId(int position) { return delegate.getItemId(position); }
        @Override public boolean hasStableIds() { return delegate.hasStableIds(); }
        @Override public int getItemViewType(int position) { return delegate.getItemViewType(position); }
        @Override public int getViewTypeCount() { return delegate.getViewTypeCount(); }
        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public void registerDataSetObserver(DataSetObserver observer) { delegate.registerDataSetObserver(observer); }
        @Override public void unregisterDataSetObserver(DataSetObserver observer) { delegate.unregisterDataSetObserver(observer); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return themeSpinnerView(delegate.getView(position, convertView, parent), false);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return themeSpinnerView(delegate.getDropDownView(position, convertView, parent), true);
        }
    }

    private boolean isLegacyBlue(int color) {
        return color == Color.rgb(0, 85, 255)
                || color == Color.rgb(0, 102, 255)
                || color == Color.rgb(2, 136, 209)
                || color == Color.rgb(64, 196, 255)
                || color == Color.rgb(143, 216, 255)
                || color == Color.rgb(130, 184, 255);
    }

    private void fitMetricText() {
        TextView cpu = findViewById(R.id.TVCPUInfoCompact);
        if (cpu != null) {
            cpu.setSingleLine(true);
            cpu.setTextColor(primary);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    cpu, 14, 24, 1, TypedValue.COMPLEX_UNIT_SP);
        }

        TextView memory = findViewById(R.id.TVMemoryInfo);
        if (memory != null) {
            memory.setSingleLine(true);
            memory.setEllipsize(null);
            memory.setTextColor(primary);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                    memory, 10, 18, 1, TypedValue.COMPLEX_UNIT_SP);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
