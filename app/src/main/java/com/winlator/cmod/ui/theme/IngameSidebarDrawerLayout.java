package com.winlator.cmod.ui.theme;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.winlator.cmod.R;

/**
 * Theme-aware host for the in-game drawer. It applies the selected sidebar palette before child
 * drawables are inflated, so the fixed rail (which is a sibling of the scrolling panel) receives
 * the same light/dark theme as the controls.
 */
public class IngameSidebarDrawerLayout extends FrameLayout {
    public IngameSidebarDrawerLayout(Context context) {
        super(context);
        applyThemeBackground(context);
    }

    public IngameSidebarDrawerLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        applyThemeBackground(context);
    }

    public IngameSidebarDrawerLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        applyThemeBackground(context);
    }

    /**
     * The XML background is resolved against the activity theme inside the View super-constructor,
     * i.e. before the sidebar palette overlay exists. Re-inflate it after applying the overlay so
     * {@code ?attr/ingameSidebar*} tokens (light or dark) resolve correctly.
     */
    private void applyThemeBackground(Context context) {
        IngameSidebarThemeLayout.applyChosenTheme(context);
        super.setBackgroundResource(R.drawable.ingame_drawer_bg);
    }
}
