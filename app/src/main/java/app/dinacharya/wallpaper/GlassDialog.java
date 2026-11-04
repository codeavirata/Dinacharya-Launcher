package app.dinacharya.wallpaper;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The app's own dialog: a frosted-glass sheet that rises from the bottom over a dimmed screen, drawn here instead of by the system.
 * It is an {@link AlertDialog} to the rest of the program (the same buttons, lists, {@code getButton} and {@code getListView}), so every
 * dialog gets the look from {@link DBuilder} without changing its own code. The system's own layout is never installed.
 *
 * <p>What it shows, from the top: a grip, the title, then either the message and a view of the caller's own, or a list of choices (to
 * pick one and close, to tick one, or to tick several), and last the buttons: the main answer is a filled pill, the other a glass
 * pill, and a third is plain words. The head and the buttons stay in place while the middle scrolls.
 */
final class GlassDialog extends AlertDialog {
    /** What a list of choices is for. */
    static final int PLAIN = 0, SINGLE = 1, MULTI = 2;

    private final Context ctx;
    private final FrameLayout root;
    private final View scrim;
    private final Panel panel;
    private final LinearLayout head;
    private final LinearLayout buttonRow;
    private final Button[] buttons = new Button[3];   // positive, negative, neutral
    private final DialogInterface.OnClickListener[] clicks = new DialogInterface.OnClickListener[3];
    private TextView titleView;
    private View customTitle;
    private TextView messageView;
    private View customView;
    private ScrollView scroll;
    private LinearLayout scrollBody;
    private ListView list;
    private boolean closing;
    private boolean finished;
    private boolean cancelable = true;

    // the colours of the sheet, taken once when it is made (so a dialog keeps its own look whatever the screen under it does)
    private final int white;
    private final int soft;
    private final int mute;
    private final int fill;
    private final int fillHi;
    private final int edge;
    private final int navy;
    private final int ripple;
    private final int rippleMain;
    private final int pickedEdge;
    private final int panelTop;
    private final int panelBottom;
    private final int grip;
    private final int scrimColor;
    private final int accent;
    private final int onAccent;

    GlassDialog(Context context, boolean dark) {
        super(context, 0);
        ctx = context;
        boolean was = Ui.night;
        if (was != dark) Ui.mode(dark);
        try {
            white = Glass.WHITE;
            soft = Glass.SOFT;
            mute = Glass.MUTE;
            fill = Glass.FILL;
            fillHi = Glass.FILL_HI;
            edge = Glass.EDGE;
            navy = Glass.NAVY;
            ripple = Glass.RIPPLE;
            rippleMain = Glass.RIPPLE_MAIN;
            pickedEdge = Glass.PICKED_EDGE;
            panelTop = Glass.PANEL_TOP;
            panelBottom = Glass.PANEL_BOT;
            grip = Glass.GRIP;
            scrimColor = Glass.SCRIM;
            accent = Ui.TEAL;
            onAccent = Ui.ON_FILL;
        } finally {
            if (was != dark) Ui.mode(was);
        }

        root = new FrameLayout(ctx);
        scrim = new View(ctx);
        scrim.setBackgroundColor(scrimColor);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> {
            if (cancelable) cancel();
        });
        root.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new Panel(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        float r = Ui.dp(ctx, 30);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{panelTop, panelBottom});
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setStroke(Math.max(1, Ui.dp(ctx, 1)), edge);
        panel.setBackground(bg);
        panel.setClickable(true);   // a touch on the sheet is not a touch on the screen behind it

        head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Ui.dp(ctx, 22), 0, Ui.dp(ctx, 22), 0);
        View gripView = new View(ctx);
        gripView.setBackground(Glass.shape(ctx, grip, 0, 2));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(ctx, 38), Ui.dp(ctx, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.topMargin = Ui.dp(ctx, 10);
        head.addView(gripView, gp);
        panel.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.head = head;

        buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        buttonRow.setPadding(Ui.dp(ctx, 22), Ui.dp(ctx, 12), Ui.dp(ctx, 22), Ui.dp(ctx, 18));
        buttonRow.setVisibility(View.GONE);
        panel.addView(buttonRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.foot = buttonRow;

        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        Insets.watch(root, (l, t, rr, b) -> panel.setPadding(l, 0, rr, b));
    }

    // ------------------------------------------------------------------ what is in it

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (customTitle != null) return;
        if (title == null || title.length() == 0) {
            if (titleView != null) titleView.setVisibility(View.GONE);
            return;
        }
        if (titleView == null) {
            titleView = Glass.display(ctx, "", 25, white);
            head.addView(titleView, Glass.gap(ctx, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 14));
        }
        titleView.setText(title);
        titleView.setVisibility(View.VISIBLE);
    }

    @Override
    public void setCustomTitle(View view) {
        if (customTitle != null) head.removeView(customTitle);
        customTitle = view;
        if (view == null) return;
        if (titleView != null) titleView.setVisibility(View.GONE);
        head.addView(view, Glass.gap(ctx, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 14));
    }

    @Override
    public void setMessage(CharSequence message) {
        if (message == null || message.length() == 0) {
            if (messageView != null) messageView.setVisibility(View.GONE);
            return;
        }
        if (messageView == null) {
            messageView = Glass.text(ctx, "", 15, soft, false);
            messageView.setLineSpacing(Ui.dp(ctx, 3), 1f);
            messageView.setPadding(Ui.dp(ctx, 22), 0, Ui.dp(ctx, 22), 0);
            body().addView(messageView, 0, Glass.gap(ctx, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 10));
        }
        messageView.setText(message);
        messageView.setVisibility(View.VISIBLE);
    }

    /** The caller's own content (its own padding stays as it made it). */
    @Override
    public void setView(View view) {
        if (customView != null && scrollBody != null) scrollBody.removeView(customView);
        customView = view;
        if (view == null) return;
        body().addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout body() {
        if (scrollBody == null) {
            scroll = new ScrollView(ctx);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setVerticalScrollBarEnabled(false);
            scrollBody = new LinearLayout(ctx);
            scrollBody.setOrientation(LinearLayout.VERTICAL);
            scrollBody.setPadding(0, 0, 0, Ui.dp(ctx, 4));
            scroll.addView(scrollBody, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            insertMiddle(scroll);
        }
        return scrollBody;
    }

    private void insertMiddle(View v) {
        panel.middle = v;
        panel.addView(v, 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * A list of choices instead of a message: {@link #PLAIN} (a tap picks one and closes), {@link #SINGLE} (a tap marks one, and the
     * caller closes) or {@link #MULTI} (a tap ticks or unticks one; {@code checked} is kept up to date).
     */
    void setChoices(CharSequence[] items, int kind, int checkedOne, boolean[] checked, boolean keepWords,
                    final DialogInterface.OnClickListener onClick, final DialogInterface.OnMultiChoiceClickListener onTick) {
        list = new ListView(ctx);
        if (keepWords) Lang.keep(list);   // the names of the person's apps stay as the phone gives them
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setVerticalScrollBarEnabled(false);
        list.setPadding(0, Ui.dp(ctx, 6), 0, Ui.dp(ctx, 6));
        list.setClipToPadding(false);
        list.setChoiceMode(kind == MULTI ? ListView.CHOICE_MODE_MULTIPLE : kind == SINGLE ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
        list.setAdapter(new Rows(items, kind, kind == PLAIN ? checkedOne : -1));
        if (kind == MULTI && checked != null) {
            for (int i = 0; i < checked.length && i < items.length; i++) list.setItemChecked(i, checked[i]);
        } else if (kind == SINGLE && checkedOne >= 0) {
            list.setItemChecked(checkedOne, true);
        }
        list.setOnItemClickListener((AdapterView<?> p, View v, int pos, long id) -> {
            if (kind == MULTI) {
                boolean on = list.isItemChecked(pos);
                if (checked != null && pos < checked.length) checked[pos] = on;
                if (onTick != null) onTick.onClick(GlassDialog.this, pos, on);
            } else {
                if (onClick != null) onClick.onClick(GlassDialog.this, pos);
                if (kind == PLAIN) dismiss();
            }
        });
        insertMiddle(list);
    }

    /** One line of a list: the words, and for a choice a round or square mark at the right. */
    private final class Rows extends BaseAdapter {
        private final CharSequence[] items;
        private final int kind;
        private final int marked;   // in a menu (PLAIN) the line that is the current one, or -1

        Rows(CharSequence[] items, int kind, int marked) {
            this.items = items;
            this.kind = kind;
            this.marked = marked;
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public Object getItem(int position) {
            return items[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Row row = convert instanceof Row ? (Row) convert : new Row(ctx, kind);
            row.text.setText(items[position]);
            row.current(position == marked);
            return row;
        }
    }

    private final class Row extends LinearLayout implements Checkable {
        final TextView text;
        private final TextView mark;
        private final int kind;
        private boolean checked;

        Row(Context c, int kind) {
            super(c);
            this.kind = kind;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(c, 52));
            setPadding(Ui.dp(c, 22), Ui.dp(c, 10), Ui.dp(c, 22), Ui.dp(c, 10));
            setBackground(Glass.press(c, 0, 0, 0, ripple));
            text = Glass.text(c, "", 16, white, false);
            text.setLineSpacing(Ui.dp(c, 2), 1f);
            addView(text, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            mark = Glass.text(c, "", 14, onAccent, true);
            mark.setGravity(Gravity.CENTER);
            LayoutParams mp = new LayoutParams(Ui.dp(c, 24), Ui.dp(c, 24));
            mp.leftMargin = Ui.dp(c, 12);
            addView(mark, mp);
            if (kind != PLAIN) paint();
            else mark.setVisibility(GONE);
        }

        /** In a menu: a tick on the line that is the current one. */
        void current(boolean on) {
            if (kind != PLAIN) return;
            mark.setText(on ? "✓" : "");
            mark.setTextColor(accent);
            mark.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17);
            mark.setVisibility(on ? VISIBLE : GONE);
        }

        private void paint() {
            boolean round = kind == SINGLE;
            mark.setBackground(Glass.shape(getContext(), checked ? accent : 0, checked ? 0 : (pickedEdge & 0x00FFFFFF) | 0x80000000, round ? 12 : 7));
            mark.setText(checked ? (round ? "●" : "✓") : "");
            mark.setTextColor(onAccent);
        }

        @Override
        public void setChecked(boolean on) {
            if (checked == on) return;
            checked = on;
            paint();
        }

        @Override
        public boolean isChecked() {
            return checked;
        }

        @Override
        public void toggle() {
            setChecked(!checked);
        }
    }

    // ------------------------------------------------------------------ the buttons

    /** Sets one of the three buttons: {@link #BUTTON_POSITIVE}, {@link #BUTTON_NEGATIVE} or {@link #BUTTON_NEUTRAL}. */
    @Override
    public void setButton(int which, CharSequence text, DialogInterface.OnClickListener listener) {
        int i = which == BUTTON_POSITIVE ? 0 : which == BUTTON_NEGATIVE ? 1 : which == BUTTON_NEUTRAL ? 2 : -1;
        if (i < 0) return;
        clicks[i] = listener;
        if (text == null || text.length() == 0) {
            if (buttons[i] != null) buttonRow.removeView(buttons[i]);
            buttons[i] = null;
        } else {
            if (buttons[i] == null) buttons[i] = makeButton(which, i);
            buttons[i].setText(text);
            buttons[i].setContentDescription(text);
        }
        arrangeButtons();
    }

    private Button makeButton(final int which, int index) {
        Button b = new Button(ctx) {
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                setAlpha(enabled ? 1f : 0.38f);
            }
        };
        b.setAllCaps(false);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        b.setMaxLines(2);
        b.setGravity(Gravity.CENTER);
        b.setStateListAnimator(null);
        b.setMinHeight(0);
        b.setMinimumHeight(Ui.dp(ctx, 50));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 12), 0);
        if (which == BUTTON_POSITIVE) {
            b.setTextColor(navy);
            b.setBackground(Glass.press(ctx, white, 0, 25, rippleMain));
        } else if (which == BUTTON_NEGATIVE) {
            b.setTextColor(white);
            b.setBackground(Glass.press(ctx, fill, edge, 25, ripple));
        } else {
            b.setTextColor(mute);
            b.setBackground(Glass.press(ctx, 0, 0, 25, ripple));
        }
        b.setOnClickListener(v -> {
            DialogInterface.OnClickListener l = clicks[index];
            if (l != null) l.onClick(GlassDialog.this, which);
            dismiss();
        });
        return b;
    }

    /** Neutral at the left, then negative, then positive; they share the width. */
    private void arrangeButtons() {
        buttonRow.removeAllViews();
        int[] order = {2, 1, 0};
        int count = 0;
        for (int i : order) {
            Button b = buttons[i];
            if (b == null) continue;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (count > 0) p.leftMargin = Ui.dp(ctx, 10);
            buttonRow.addView(b, p);
            count++;
        }
        buttonRow.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public Button getButton(int whichButton) {
        return whichButton == BUTTON_POSITIVE ? buttons[0] : whichButton == BUTTON_NEGATIVE ? buttons[1] : whichButton == BUTTON_NEUTRAL ? buttons[2] : null;
    }

    @Override
    public ListView getListView() {
        return list;
    }

    @Override
    public void setCancelable(boolean flag) {
        super.setCancelable(flag);
        cancelable = flag;
    }

    // ------------------------------------------------------------------ showing and closing

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // the system's own dialog layout is not used: the sheet above is the whole dialog
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(root);
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setWindowAnimations(0);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Lang.watch(root);
        panel.setVisibility(View.INVISIBLE);
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                panel.setTranslationY(panel.getHeight() + Ui.dp(ctx, 40));
                panel.setVisibility(View.VISIBLE);
                scrim.animate().alpha(1f).setDuration(200).start();
                panel.animate().translationY(0f).setDuration(260).setInterpolator(new DecelerateInterpolator(2f)).start();
                return true;
            }
        });
    }

    /** The sheet slides down, then the dialog goes. */
    @Override
    public void dismiss() {
        if (finished) return;
        if (closing) return;
        closing = true;
        try {
            if (!isShowing() || !panel.isAttachedToWindow()) {
                finish();
                return;
            }
            scrim.animate().alpha(0f).setDuration(150).start();
            panel.animate().translationY(panel.getHeight() + Ui.dp(ctx, 40)).setDuration(170).start();
            root.postDelayed(this::finish, 190);
        } catch (RuntimeException e) {
            finish();
        }
    }

    private void finish() {
        if (finished) return;
        finished = true;
        try {
            super.dismiss();
        } catch (RuntimeException ignored) {
            // the screen is already gone
        }
    }

    /** The sheet: head and buttons keep their room, the middle (message and view, or list) takes what is left and scrolls. */
    private static final class Panel extends LinearLayout {
        View head;
        View middle;
        View foot;

        Panel(Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int parentH = MeasureSpec.getSize(heightSpec);
            int maxH = (int) ((MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED || parentH <= 0
                    ? getResources().getDisplayMetrics().heightPixels : parentH) * 0.9f);
            int inner = MeasureSpec.makeMeasureSpec(Math.max(0, width - getPaddingLeft() - getPaddingRight()), MeasureSpec.EXACTLY);
            int used = getPaddingTop() + getPaddingBottom();
            used += measureFixed(head, inner, maxH);
            if (foot != null && foot.getVisibility() != GONE) used += measureFixed(foot, inner, maxH);
            if (middle != null && middle.getVisibility() != GONE) {
                LayoutParams lp = (LayoutParams) middle.getLayoutParams();
                int avail = Math.max(0, maxH - used - lp.topMargin - lp.bottomMargin);
                middle.measure(inner, MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST));
                used += middle.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }
            setMeasuredDimension(width, used);
        }

        private int measureFixed(View v, int innerWidth, int maxH) {
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            v.measure(getChildMeasureSpec(innerWidth, lp.leftMargin + lp.rightMargin, lp.width),
                    MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            return v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
        }
    }
}
