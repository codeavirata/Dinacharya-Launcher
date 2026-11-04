package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

/**
 * The builder of every dialog of the app. It puts everything it is given into the language in use, and builds a {@link GlassDialog}: the
 * app's own frosted sheet, not the system's dialog. It keeps the {@link AlertDialog.Builder} calls the screens already make.
 */
final class DBuilder extends AlertDialog.Builder {
    private final boolean dark;
    private CharSequence title;
    private CharSequence message;
    private View customTitle;
    private View view;
    private final CharSequence[] buttonText = new CharSequence[3];            // positive, negative, neutral
    private final DialogInterface.OnClickListener[] buttonClick = new DialogInterface.OnClickListener[3];
    private CharSequence[] items;
    private boolean keepWords;
    private int kind = GlassDialog.PLAIN;
    private int checkedOne = -1;
    private boolean[] checked;
    private DialogInterface.OnClickListener itemClick;
    private DialogInterface.OnMultiChoiceClickListener tickClick;
    private boolean cancelable = true;
    private DialogInterface.OnCancelListener onCancel;
    private DialogInterface.OnDismissListener onDismiss;

    DBuilder(Context context) {
        this(context, 0);
    }

    DBuilder(Context context, int theme) {
        super(context, theme);
        // the sheet is dark or light as the theme given says, otherwise as the screen is
        dark = theme == R.style.GlassDialogNight ? true : theme == R.style.GlassDialogDay ? false : Ui.night;
    }

    @Override
    public AlertDialog.Builder setTitle(CharSequence title) {
        this.title = Lang.tr(title);
        return this;
    }

    @Override
    public AlertDialog.Builder setMessage(CharSequence message) {
        this.message = Lang.tr(message);
        return this;
    }

    @Override
    public AlertDialog.Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
        buttonText[0] = Lang.tr(text);
        buttonClick[0] = l;
        return this;
    }

    @Override
    public AlertDialog.Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) {
        buttonText[1] = Lang.tr(text);
        buttonClick[1] = l;
        return this;
    }

    @Override
    public AlertDialog.Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) {
        buttonText[2] = Lang.tr(text);
        buttonClick[2] = l;
        return this;
    }

    @Override
    public AlertDialog.Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
        this.items = Lang.tr(items);
        this.kind = GlassDialog.PLAIN;
        this.itemClick = l;
        return this;
    }

    /** A menu: a tap picks a line and closes; the line {@code current} (or -1) carries a tick. */
    DBuilder setMenu(CharSequence[] items, int current, DialogInterface.OnClickListener l) {
        this.items = Lang.tr(items);
        this.kind = GlassDialog.PLAIN;
        this.checkedOne = current;
        this.itemClick = l;
        return this;
    }

    @Override
    public AlertDialog.Builder setMultiChoiceItems(CharSequence[] items, boolean[] checked, DialogInterface.OnMultiChoiceClickListener l) {
        this.items = Lang.tr(items);
        this.kind = GlassDialog.MULTI;
        this.checked = checked;
        this.tickClick = l;
        return this;
    }

    /** Choices that are names of the person's apps: shown as the phone gives them, not translated. */
    DBuilder setAppChoices(CharSequence[] names, boolean[] checked, DialogInterface.OnMultiChoiceClickListener l) {
        this.items = names;
        this.keepWords = true;
        this.kind = GlassDialog.MULTI;
        this.checked = checked;
        this.tickClick = l;
        return this;
    }

    @Override
    public AlertDialog.Builder setSingleChoiceItems(CharSequence[] items, int checked, DialogInterface.OnClickListener l) {
        this.items = Lang.tr(items);
        this.kind = GlassDialog.SINGLE;
        this.checkedOne = checked;
        this.itemClick = l;
        return this;
    }

    @Override
    public AlertDialog.Builder setView(View view) {
        Lang.watch(view);
        this.view = view;
        return this;
    }

    @Override
    public AlertDialog.Builder setCustomTitle(View view) {
        Lang.watch(view);
        this.customTitle = view;
        return this;
    }

    @Override
    public AlertDialog.Builder setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    @Override
    public AlertDialog.Builder setOnCancelListener(DialogInterface.OnCancelListener l) {
        this.onCancel = l;
        return this;
    }

    @Override
    public AlertDialog.Builder setOnDismissListener(DialogInterface.OnDismissListener l) {
        this.onDismiss = l;
        return this;
    }

    /** Builds the sheet; it keeps the dialog in the language in use while it is open (text changed after it showed). */
    @Override
    public AlertDialog create() {
        GlassDialog d = new GlassDialog(getContext(), dark);
        if (customTitle != null) d.setCustomTitle(customTitle);
        else d.setTitle(title);
        if (items != null) d.setChoices(items, kind, checkedOne, checked, keepWords, itemClick, tickClick);
        else d.setMessage(message);
        if (view != null) d.setView(view);
        d.setButton(AlertDialog.BUTTON_POSITIVE, buttonText[0], buttonClick[0]);
        d.setButton(AlertDialog.BUTTON_NEGATIVE, buttonText[1], buttonClick[1]);
        d.setButton(AlertDialog.BUTTON_NEUTRAL, buttonText[2], buttonClick[2]);
        d.setCancelable(cancelable);
        if (onCancel != null) d.setOnCancelListener(onCancel);
        if (onDismiss != null) d.setOnDismissListener(onDismiss);
        return d;
    }
}
