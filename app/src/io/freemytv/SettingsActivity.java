package io.freemytv;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Free My TV's home-screen choice: Free My TV or the TV's original home screen. The change is made by Android itself:
 * it asks once on screen (the home-app dialog), so no adb connection is needed.
 */
public class SettingsActivity extends Activity {
    private static final int FREE = 0, ORIGINAL = 1;
    private static final int REQUEST_HOME = 1;
    private final TextView[] rows = new TextView[2];
    private int focus;
    /** Sent to the system's "Default home app" screen to hand the home screen back to the TV's own launcher. */
    private boolean handingBack;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setBackgroundColor(Color.rgb(11, 13, 18));

        column.addView(text("Home Screen", 36, Color.WHITE));
        rows[FREE] = row("Free My TV");
        rows[ORIGINAL] = row("Original");
        column.addView(rows[FREE]);
        column.addView(rows[ORIGINAL]);
        setContentView(column);
    }

    @Override protected void onResume() {
        super.onResume();
        if (handingBack) {
            handingBack = false;
            if (!freeActive()) {
                // The TV's own launcher was chosen on the Default home app screen: go straight to it.
                HomeMode.recordMode(this, HomeMode.ORIGINAL);
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return;
            }
        }
        focus = freeActive() ? FREE : ORIGINAL;
        paint();
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 24, 0, 24);
        return t;
    }

    private TextView row(String s) {
        TextView t = text(s, 26, Color.WHITE);
        t.setMinWidth(560);
        t.setPadding(48, 22, 48, 22);
        return t;
    }

    /** Free My TV is the home screen now: whichever app the Home button opens is the one Android uses. */
    private boolean freeActive() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo app = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return app != null && getPackageName().equals(app.activityInfo.packageName);
    }

    private void paint() {
        boolean free = freeActive();
        for (int i = 0; i < rows.length; i++) {
            boolean selected = i == focus;
            boolean current = (i == FREE) == free;
            GradientDrawable box = new GradientDrawable();
            box.setCornerRadius(18);
            box.setColor(selected ? Color.rgb(44, 48, 60) : Color.TRANSPARENT);
            rows[i].setBackground(box);
            String label = i == FREE ? "Free My TV" : "Original";
            rows[i].setText(current ? label + "  ✓" : label);
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:
                focus = Math.max(0, focus - 1);
                paint();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                focus = Math.min(rows.length - 1, focus + 1);
                paint();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                choose();
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    /** Already the choice in use: leave. Free My TV is asked for on screen; the TV's own launcher is picked in Android's screen. */
    private void choose() {
        if ((focus == FREE) == freeActive()) {
            finish();
            return;
        }
        if (focus == ORIGINAL) {
            // Android only lets the home app be changed from its own screen, and it has the TV's launcher on it.
            handingBack = true;
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            return;
        }
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_HOME)) return;
        startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_HOME || resultCode != RESULT_OK) return;   // cancelled: stay here
        HomeMode.recordMode(this, freeActive() ? HomeMode.FREE : HomeMode.ORIGINAL);
        // Straight to the new home screen: Free My TV's grid, or the TV's own.
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }
}
