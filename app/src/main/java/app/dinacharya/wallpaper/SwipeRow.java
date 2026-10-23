package app.dinacharya.wallpaper;

import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * A row you can swipe: left to delete, right to add a slot below. The row slides over a coloured
 * strip that names the action. The decision logic is in {@link SwipeTracker}.
 */
final class SwipeRow extends FrameLayout {
    interface Listener {
        void onSwipedLeft();

        void onSwipedRight();
    }

    private final SwipeTracker tracker;
    private final View card;
    private final FrameLayout strip;
    private final TextView addHint;
    private final TextView deleteHint;
    private Listener listener;

    SwipeRow(Context c, View card) {
        super(c);
        this.card = card;
        tracker = new SwipeTracker(ViewConfiguration.get(c).getScaledTouchSlop(), 0.33f);

        strip = new FrameLayout(c);
        addHint = Ui.label(c, "＋  Add below", 14, 0xFFFFFFFF, true);
        deleteHint = Ui.label(c, "Delete  ✕", 14, 0xFFFFFFFF, true);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        l.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        l.leftMargin = Ui.dp(c, 18);
        strip.addView(addHint, l);
        FrameLayout.LayoutParams r = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        r.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        r.rightMargin = Ui.dp(c, 18);
        strip.addView(deleteHint, r);
        strip.setVisibility(View.INVISIBLE);
        addView(strip, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    private void show(float armed) {
        if (armed == 0) {
            strip.setVisibility(View.INVISIBLE);
            return;
        }
        strip.setVisibility(View.VISIBLE);
        boolean add = armed > 0;
        strip.setBackground(Ui.box(getContext(), add ? Ui.TEAL : Ui.RED, 0, 18));
        addHint.setVisibility(add ? View.VISIBLE : View.INVISIBLE);
        deleteHint.setVisibility(add ? View.INVISIBLE : View.VISIBLE);
        float a = Math.min(1f, Math.abs(armed));
        strip.setAlpha(0.35f + 0.65f * a);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracker.down(e.getX(), e.getY());
                return false;
            case MotionEvent.ACTION_MOVE:
                if (tracker.move(e.getX(), e.getY())) {
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float w = Math.max(1, getWidth());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracker.down(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tracker.move(e.getX(), e.getY())) {
                    card.setTranslationX(tracker.offset(w * 0.6f));
                    show(tracker.armed(w));
                }
                return true;
            case MotionEvent.ACTION_UP: {
                int action = tracker.release(w);
                snapBack();
                if (listener != null) {
                    if (action == SwipeTracker.DELETE) listener.onSwipedLeft();
                    else if (action == SwipeTracker.ADD) listener.onSwipedRight();
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                tracker.release(w);
                snapBack();
                return true;
            default:
                return true;
        }
    }

    private void snapBack() {
        card.animate().translationX(0).setDuration(160).start();
        strip.setVisibility(View.INVISIBLE);
    }
}
