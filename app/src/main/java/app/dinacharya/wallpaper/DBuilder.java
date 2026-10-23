package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

/** An {@link AlertDialog.Builder} that puts everything it is given into the language in use. */
final class DBuilder extends AlertDialog.Builder {
    DBuilder(Context context) {
        super(context);
    }

    DBuilder(Context context, int theme) {
        super(context, theme);
    }

    @Override
    public AlertDialog.Builder setTitle(CharSequence title) {
        return super.setTitle(Lang.tr(title));
    }

    @Override
    public AlertDialog.Builder setMessage(CharSequence message) {
        return super.setMessage(Lang.tr(message));
    }

    @Override
    public AlertDialog.Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
        return super.setPositiveButton(Lang.tr(text), l);
    }

    @Override
    public AlertDialog.Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) {
        return super.setNegativeButton(Lang.tr(text), l);
    }

    @Override
    public AlertDialog.Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) {
        return super.setNeutralButton(Lang.tr(text), l);
    }

    @Override
    public AlertDialog.Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
        return super.setItems(Lang.tr(items), l);
    }

    @Override
    public AlertDialog.Builder setMultiChoiceItems(CharSequence[] items, boolean[] checked, DialogInterface.OnMultiChoiceClickListener l) {
        return super.setMultiChoiceItems(Lang.tr(items), checked, l);
    }

    /** Choices that are names of the person's apps: shown as the phone gives them, not translated. */
    DBuilder setAppChoices(CharSequence[] names, boolean[] checked, DialogInterface.OnMultiChoiceClickListener l) {
        super.setMultiChoiceItems(names, checked, l);
        return this;
    }

    @Override
    public AlertDialog.Builder setSingleChoiceItems(CharSequence[] items, int checked, DialogInterface.OnClickListener l) {
        return super.setSingleChoiceItems(Lang.tr(items), checked, l);
    }

    @Override
    public AlertDialog.Builder setView(View view) {
        Lang.watch(view);
        return super.setView(view);
    }

    @Override
    public AlertDialog.Builder setCustomTitle(View view) {
        Lang.watch(view);
        return super.setCustomTitle(view);
    }

    /** Also keeps the dialog in the language in use while it is open (text changed after it showed). */
    @Override
    public AlertDialog create() {
        final AlertDialog d = super.create();
        d.setOnShowListener(x -> Lang.watch(d.getWindow().getDecorView()));
        return d;
    }
}
