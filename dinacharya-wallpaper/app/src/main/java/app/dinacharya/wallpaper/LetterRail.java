package app.dinacharya.wallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** The thin A to Z strip beside the list of all apps: slide a finger along it to jump to a letter. */
final class LetterRail extends View {
    interface Listener {
        void onLetter(char letter);
    }

    private static final String LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean[] present = new boolean[LETTERS.length()];
    private Listener listener;
    private int ink = 0xFFFFFFFF;
    private int soft = 0x66FFFFFF;
    private int active = -1;

    LetterRail(Context c) {
        super(c);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
    }

    void setColors(int ink, int soft) {
        this.ink = ink;
        this.soft = soft;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setPresent(boolean[] p) {
        present = p;
        invalidate();
    }

    static int indexOf(char letter) {
        return Math.max(0, LETTERS.indexOf(letter));
    }

    @Override
    protected void onDraw(Canvas c) {
        float h = getHeight() / (float) LETTERS.length();
        paint.setTextSize(Math.min(h * 0.72f, getWidth() * 0.62f));
        for (int i = 0; i < LETTERS.length(); i++) {
            paint.setColor(i == active ? ink : (present[i] ? ink : soft));
            paint.setAlpha(i == active ? 255 : (present[i] ? 200 : 80));
            c.drawText(String.valueOf(LETTERS.charAt(i)), getWidth() / 2f, h * i + h * 0.74f, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            active = -1;
            invalidate();
            return true;
        }
        float h = getHeight() / (float) LETTERS.length();
        int i = Math.max(0, Math.min(LETTERS.length() - 1, (int) (e.getY() / h)));
        if (i != active) {
            active = i;
            invalidate();
            if (listener != null) listener.onLetter(LETTERS.charAt(i));
        }
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }
}
