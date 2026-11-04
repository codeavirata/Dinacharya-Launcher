package app.dinacharya.wallpaper;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

/**
 * A drop-down that opens the app's glass sheet instead of the system's pop-up list. It is a {@link Spinner} to the screen that holds it
 * (the same adapter, {@code setSelection}, {@code getSelectedItemPosition} and item-selected listener), drawn as a glass pill with an arrow.
 */
final class GlassSpinner extends Spinner {
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private CharSequence sheetTitle;

    GlassSpinner(Context context) {
        super(context, MODE_DIALOG);   // no pop-up list of the system's is ever made (not even by a long press)
        setBackground(Ui.box(context, Ui.SOFT, 0, 16));
        setMinimumHeight(Ui.dp(context, 50));
        setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 12), 0);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeWidth(Ui.dp(context, 2));
        arrowPaint.setColor(Ui.MUTED);
        setWillNotDraw(false);
    }

    /** The words at the top of the sheet; none by default. */
    GlassSpinner title(CharSequence title) {
        sheetTitle = title;
        return this;
    }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        super.setAdapter(adapter == null ? null : new Dressed(adapter));
    }

    /** Opens the sheet with the choices; the one picked is selected, as the system's list would have done. */
    @Override
    public boolean performClick() {
        final SpinnerAdapter ad = getAdapter();
        if (ad == null || ad.getCount() == 0 || !isEnabled()) return true;
        CharSequence[] items = new CharSequence[ad.getCount()];
        for (int i = 0; i < items.length; i++) {
            Object o = ad.getItem(i);
            items[i] = o == null ? "" : o.toString();
        }
        playSoundEffect(android.view.SoundEffectConstants.CLICK);
        DBuilder b = new DBuilder(getContext());
        if (sheetTitle != null) b.setTitle(sheetTitle);
        b.setMenu(items, getSelectedItemPosition(), (d, which) -> setSelection(which));
        b.show();
        return true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        float s = Ui.dp(getContext(), 5);
        float cx = getWidth() - getPaddingRight() - Ui.dp(getContext(), 8);
        float cy = getHeight() / 2f;
        arrow.reset();
        arrow.moveTo(cx - s, cy - s / 2f);
        arrow.lineTo(cx, cy + s / 2f);
        arrow.lineTo(cx + s, cy - s / 2f);
        canvas.drawPath(arrow, arrowPaint);
    }

    /** The adapter's lines, put in the sheet's colours (the theme of the system's list is not used). */
    private final class Dressed implements SpinnerAdapter {
        private final SpinnerAdapter inner;

        Dressed(SpinnerAdapter inner) {
            this.inner = inner;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            View v = inner.getView(position, convert, parent);
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                t.setTextColor(Ui.INK);
                t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                t.setMaxLines(2);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(0, Ui.dp(getContext(), 12), Ui.dp(getContext(), 26), Ui.dp(getContext(), 12));
            }
            return v;
        }

        @Override
        public View getDropDownView(int position, View convert, ViewGroup parent) {
            return inner.getDropDownView(position, convert, parent);
        }

        @Override
        public void registerDataSetObserver(DataSetObserver o) {
            inner.registerDataSetObserver(o);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver o) {
            inner.unregisterDataSetObserver(o);
        }

        @Override
        public int getCount() {
            return inner.getCount();
        }

        @Override
        public Object getItem(int position) {
            return inner.getItem(position);
        }

        @Override
        public long getItemId(int position) {
            return inner.getItemId(position);
        }

        @Override
        public boolean hasStableIds() {
            return inner.hasStableIds();
        }

        @Override
        public int getItemViewType(int position) {
            return inner.getItemViewType(position);
        }

        @Override
        public int getViewTypeCount() {
            return inner.getViewTypeCount();
        }

        @Override
        public boolean isEmpty() {
            return inner.isEmpty();
        }
    }
}
