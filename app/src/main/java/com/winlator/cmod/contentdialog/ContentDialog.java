package com.winlator.cmod.contentdialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.ui.theme.WinlatorLegacyTheme;

import java.util.ArrayList;

public class ContentDialog extends Dialog {
    public Runnable onConfirmCallback;
    private Runnable onCancelCallback;
    private final View contentView;

    public ContentDialog(@NonNull Context context) {
        this(context, 0);
    }

    private View inflatedLayout;

    /**
     * The dialog's light/dark look follows the UI theme the user actually picked
     * ({@code winlator_ui_theme}), not the legacy {@code dark_mode} preference.  That preference is
     * a leftover from the pre-theme-picker build: its checkbox is hidden and MainActivity forces it
     * to true on first run, so reading it made every dialog come out dark no matter which theme was
     * selected -- a black card on the light theme.
     */
    private static boolean isLightTheme(Context context) {
        return WinlatorLegacyTheme.isLight(context);
    }

    private static int getDialogStyle(Context context) {
        return isLightTheme(context) ? R.style.ContentDialog : R.style.ContentDialog_Dark;
    }

    public ContentDialog(@NonNull Context context, int layoutResId) {
        super(context, getDialogStyle(context));

        // Inflate against the dialog's own theme, not the Activity's.  Everything the layouts
        // resolve by theme attribute -- ?android:textColorPrimary, ?android:textViewStyle, the
        // button styles -- then follows the theme the dialog picked above.  Inflating with the
        // Activity context is what used to force white text onto the light card, because the
        // Activity theme is dark and its @style/TextView hardcodes #ffffff.
        LayoutInflater inflater = LayoutInflater.from(getContext());
        contentView = inflater.inflate(R.layout.content_dialog, null);

        // The card background comes from ?attr/contentDialogBackground in the layout, not from here.
        // Applying it programmatically would overwrite the layout's padding with the 9-patch's own
        // content area -- which is what silently collapsed the dialog's margins to ~4dp.
        if (getWindow() != null) getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));


        if (layoutResId > 0) {
            FrameLayout frameLayout = contentView.findViewById(R.id.FrameLayout);
            frameLayout.setVisibility(View.VISIBLE);
            View view = inflater.inflate(layoutResId, frameLayout, false);
            frameLayout.addView(view);
        }

        View confirmButton = contentView.findViewById(R.id.BTConfirm);
        confirmButton.setOnClickListener((v) -> {
            if (onConfirmCallback != null) onConfirmCallback.run();
            dismiss();
        });

        View cancelButton = contentView.findViewById(R.id.BTCancel);
        cancelButton.setOnClickListener((v) -> {
            if (onCancelCallback != null) onCancelCallback.run();
            dismiss();
        });

        setContentView(contentView);
    }

    /**
     * List rows are inflated by the adapter, so the constructor's pass does not reach them.  Build
     * the adapter from the dialog's own context so the framework resolves their default colours
     * against the dialog theme instead of the (dark) Activity theme, and use a row layout that
     * suits the theme -- {@code dialog_list_item_amoled} is an explicit black chip.
     */
    private static ArrayAdapter<String> themedAdapter(Context dialogContext,
                                                      int lightLayout, int darkLayout,
                                                      String[] items) {
        return new ArrayAdapter<>(dialogContext,
                WinlatorLegacyTheme.isLight(dialogContext) ? lightLayout : darkLayout, items);
    }

    public View getInflatedLayout() {
        return inflatedLayout;
    }

    public View getContentView() {
        return contentView;
    }

    public void setOnConfirmCallback(Runnable onConfirmCallback) {
        this.onConfirmCallback = onConfirmCallback;
    }

    public void setOnCancelCallback(Runnable onCancelCallback) {
        this.onCancelCallback = onCancelCallback;
    }

    @Override
    public void setTitle(int titleResId) {
        setTitle(getContext().getString(titleResId));
    }

    public void setIcon(int iconResId) {
        ImageView imageView = findViewById(R.id.IVIcon);
        imageView.setImageResource(iconResId);
        imageView.setVisibility(View.VISIBLE);
    }

    public void setTitle(String title) {
        LinearLayout titleBar = findViewById(R.id.LLTitleBar);
        TextView tvTitle = findViewById(R.id.TVTitle);

        if (title != null && !title.isEmpty()) {
            tvTitle.setText(title);
            titleBar.setVisibility(View.VISIBLE);
        }
        else {
            tvTitle.setText("");
            titleBar.setVisibility(View.GONE);
        }
    }

    public void setBottomBarText(String bottomBarText) {
        TextView tvBottomBarText = findViewById(R.id.TVBottomBarText);

        if (bottomBarText != null && !bottomBarText.isEmpty()) {
            tvBottomBarText.setText(bottomBarText);
            tvBottomBarText.setVisibility(View.VISIBLE);
        }
        else {
            tvBottomBarText.setText("");
            tvBottomBarText.setVisibility(View.GONE);
        }
    }

    public void setMessage(int msgResId) {
        setMessage(getContext().getString(msgResId));
    }

    public void setMessage(String message) {
        TextView tvMessage = findViewById(R.id.TVMessage);

        if (message != null && !message.isEmpty()) {
            tvMessage.setText(message);
            tvMessage.setVisibility(View.VISIBLE);
        }
        else {
            tvMessage.setText("");
            tvMessage.setVisibility(View.GONE);
        }
    }

    public static void alert(Context context, int msgResId, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msgResId);
        dialog.setOnConfirmCallback(callback);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        dialog.show();
    }

    public static void alert(Context context, String msg, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msg);
        dialog.setOnConfirmCallback(callback);
        dialog.findViewById(R.id.BTCancel).setVisibility(View.GONE);
        dialog.show();
    }

    public static void confirm(Context context, int msgResId, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msgResId);
        dialog.setOnConfirmCallback(callback);
        dialog.show();
    }

    public static void confirm(Context context, String msg, Runnable callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.setMessage(msg);
        dialog.setOnConfirmCallback(callback);
        dialog.show();
    }

    public static void prompt(Context context, int titleResId, String defaultText, Callback<String> callback) {
        ContentDialog dialog = new ContentDialog(context);

        final EditText editText = dialog.findViewById(R.id.EditText);

        applyDarkThemeToEditText(editText, !isLightTheme(context));

        editText.setHint(R.string.untitled);
        if (defaultText != null) editText.setText(defaultText);
        editText.setVisibility(View.VISIBLE);

        dialog.setTitle(titleResId);
        dialog.setOnConfirmCallback(() -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) callback.call(text);
        });

        dialog.show();
    }

    private static void applyDarkThemeToEditText(EditText editText, boolean isDarkMode) {
        if (isDarkMode) {
            editText.setTextColor(Color.WHITE); // Set text color to white for dark theme
            editText.setHintTextColor(Color.GRAY); // Set hint color to gray
            editText.setBackgroundResource(R.drawable.edit_text_dark); // Custom dark background drawable
        } else {
            editText.setTextColor(Color.BLACK); // Default text color
            editText.setHintTextColor(Color.GRAY); // Default hint color
            editText.setBackgroundResource(R.drawable.edit_text); // Custom light background drawable
        }
    }

    public static void showMultipleChoiceList(Context context, int titleResId, final String[] items, Callback<ArrayList<Integer>> callback) {
        ContentDialog dialog = new ContentDialog(context);

        final ListView listView = dialog.findViewById(R.id.ListView);
        listView.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(themedAdapter(dialog.getContext(),
                android.R.layout.simple_list_item_multiple_choice,
                android.R.layout.simple_list_item_multiple_choice, items));
        listView.setVisibility(View.VISIBLE);

        dialog.setTitle(titleResId);
        dialog.setOnConfirmCallback(() -> {
            ArrayList<Integer> result = new ArrayList<>();
            SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
            for (int i = 0; i < checkedItemPositions.size(); i++) {
                if (checkedItemPositions.valueAt(i)) result.add(checkedItemPositions.keyAt(i));
            }
            callback.call(result);
        });

        dialog.show();
    }

    public static void showSingleChoiceList(Context context, int titleResId, final String[] items, Callback<Integer> callback) {
        ContentDialog dialog = new ContentDialog(context);
        dialog.getContentView().findViewById(R.id.BTConfirm).setVisibility(View.GONE);

        final ListView listView = dialog.findViewById(R.id.ListView);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)listView.getLayoutParams();
        layoutParams.width = AppUtils.getPreferredDialogWidth(context);
        layoutParams.height = (int)UnitUtils.dpToPx(Math.min(items.length, 5) * 48);
        layoutParams.weight = 0;
        listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        listView.setAdapter(themedAdapter(dialog.getContext(),
                android.R.layout.simple_list_item_1,
                R.layout.dialog_list_item_amoled, items));
        listView.setVisibility(View.VISIBLE);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            callback.call(position);
            dialog.dismiss();
        });

        dialog.setTitle(titleResId);
        dialog.show();
    }
}
