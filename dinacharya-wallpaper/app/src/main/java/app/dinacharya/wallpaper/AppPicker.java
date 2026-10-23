package app.dinacharya.wallpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/** A dialog with a search box and a list of apps to choose one from. */
final class AppPicker {
    interface Result {
        void picked(String key);
    }

    private AppPicker() {
    }

    /**
     * @param extraLabels choices shown above the apps (such as "Nothing"), with their keys in {@code extraKeys}; may be null
     */
    static void show(Activity a, int theme, String title, List<AppEntry> apps, String[] extraLabels, String[] extraKeys,
                     final Result result) {
        final AlertDialog.Builder b = new DBuilder(a, theme);
        final Context c = b.getContext();
        final List<String> labels = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        final List<AppEntry> all = new ArrayList<>(apps);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 20);
        box.setPadding(pad, Ui.dp(c, 8), pad, 0);

        final EditText search = new EditText(c);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        box.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ListView list = new ListView(c);
        Lang.keep(list);   // app names stay as the phone gives them
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(c, android.R.layout.simple_list_item_1, labels);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 320)));

        final Runnable fill = new Runnable() {
            @Override
            public void run() {
                labels.clear();
                keys.clear();
                String q = search.getText().toString();
                if (q.trim().isEmpty() && extraLabels != null) {
                    for (int i = 0; i < extraLabels.length; i++) {
                        labels.add(extraLabels[i]);
                        keys.add(extraKeys[i]);
                    }
                }
                for (AppEntry e : AppSearch.filter(all, q)) {
                    labels.add(e.shown());
                    keys.add(e.key);
                }
                adapter.notifyDataSetChanged();
            }
        };
        fill.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c1, int a1) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b1, int c1) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                fill.run();
            }
        });

        b.setTitle(title).setView(box).setNegativeButton("Cancel", null);
        final AlertDialog d = b.create();
        list.setOnItemClickListener((p, v, pos, id) -> {
            if (pos < 0 || pos >= keys.size()) return;
            hide(c, search);
            d.dismiss();
            result.picked(keys.get(pos));
        });
        d.show();
    }

    private static void hide(Context c, View v) {
        try {
            InputMethodManager m = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (m != null) m.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (RuntimeException ignored) {
            // the keyboard closes with the dialog anyway
        }
    }
}
