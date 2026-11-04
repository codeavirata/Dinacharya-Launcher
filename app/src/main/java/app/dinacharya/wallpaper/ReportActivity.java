package app.dinacharya.wallpaper;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** One saved day: the report on screen, with buttons to save or share it as a PDF. */
public class ReportActivity extends LangActivity {
    static final String EXTRA_DAY = "day";
    private DayReport report;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String day = getIntent().getStringExtra(EXTRA_DAY);
        // saved days are part of Pro; this screen is only reached from the list of days, but a pass can end while it is open
        if (!Pro.has(this, DayPass.REPORT_DAYS)) {
            Lang.toast(this, "Saved days are part of Pro", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        report = day == null ? null : Store.history(this).loadReport(day);
        if (report == null) {
            Lang.toast(this, "That report is no longer on the phone", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Insets.pad(sv);
        setContentView(sv);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 4));
        addHalf(bar, "Save PDF", true, () -> PdfActions.save(this, report));
        addHalf(bar, "Share PDF", false, () -> PdfActions.share(this, report));
        root.addView(bar);
        TextView drive = Ui.label(this, "", 12, Ui.MUTED, false);
        drive.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6));
        AccountStore acc = new AccountStore(this);
        drive.setText(acc.signedIn()
                ? (acc.lastOk() >= report.endMs ? "In your Google Drive backup." : "Goes into your Google Drive backup next time it runs.")
                : "Tip: sign in under Backup to keep every day in your Google Drive.");
        root.addView(drive);

        boolean h24 = Store.load(this).fmt24;
        ReportView head = new ReportView(this);
        head.showDay(report, h24, ReportRenderer.HEAD);
        root.addView(head, ReportView.lp(this));
        try {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 8));
            root.addView(Limits.analysisAllowed(Pro.has(this, DayPass.ANALYSIS)) ? SlotCards.build(this, report, h24)
                    : SlotCards.locked(this, this::recreate), lp);
        } catch (RuntimeException e) {
            CrashLog.note(this, "slot analysis", e);
        }
        ReportView tail = new ReportView(this);
        tail.showDay(report, h24, ReportRenderer.TAIL);
        root.addView(tail, ReportView.lp(this));
    }

    private void addHalf(LinearLayout bar, String text, boolean primary, final Runnable r) {
        android.widget.Button b = Ui.button(this, text, primary, v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = Ui.dp(this, 8);
        bar.addView(b, lp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PdfActions.REQ_SAVE && resultCode == RESULT_OK && data != null) {
            PdfActions.saved(this, report, data.getData());
        }
    }
}
