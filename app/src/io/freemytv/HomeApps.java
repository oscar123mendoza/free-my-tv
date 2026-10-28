package io.freemytv;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The apps on the home screen, and how to open each one. */
final class HomeApps {
    private HomeApps() {}

    static final int APP = 0, ANDROID_SETTINGS = 5, SWITCH = 6;

    /** Shown first, in this order; every other installed app follows alphabetically. */
    private static final String[] PREFERRED = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.apple.atve.androidtv.appletv",
            "com.apple.atve.sony.appletv",
            "com.amazon.amazonvideo.livingroom",
            "com.disney.disneyplus",
            "com.hulu.livingroomplus",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.peacocktv.peacockandroid",
            "com.cbs.ott",
            "com.foxsports.android",
            "com.google.android.youtube.tvunplugged",
            "org.pbskids.video",
            "com.bamnetworks.mobile.android.gameday.atbat",
            "com.android.vending",   // Play Store: the way to download apps on the TV (a system app, so it is listed by name)
    };

    /** Never shown: kids' YouTube, which has its own app. */
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList("com.google.android.youtube.tvkids"));

    static final class Item {
        final int kind;
        final String key, label, pkg;

        Item(int kind, String key, String label, String pkg) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.pkg = pkg;
        }
    }

    /**
     * The apps on this TV that open from the home screen: every app the user installed that has a TV launcher entry,
     * the ones in PREFERRED first, then the rest by name. System apps are left out, except the ones listed in PREFERRED.
     */
    static List<String> installedPackages(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Set<String> seen = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(leanback, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || HIDDEN.contains(pkg) || !seen.add(pkg)) continue;
            boolean system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (system && rank(pkg) == PREFERRED.length) continue;
            out.add(pkg);
        }
        Collections.sort(out, (a, b) -> {
            int ra = rank(a), rb = rank(b);
            return ra != rb ? Integer.compare(ra, rb) : label(pm, a).compareToIgnoreCase(label(pm, b));
        });
        return out;
    }

    private static int rank(String pkg) {
        for (int i = 0; i < PREFERRED.length; i++) if (PREFERRED[i].equals(pkg)) return i;
        return PREFERRED.length;
    }

    /** The app's name as the TV shows it. */
    static String label(PackageManager pm, String pkg) {
        try {
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** An app on the home screen. */
    static Item app(String pkg, String label) {
        return new Item(APP, pkg, label, pkg);
    }

    /** The TV's own settings, as the Settings app on the home screen. */
    static Item settings() {
        return new Item(ANDROID_SETTINGS, "tv-settings", "Settings", null);
    }

    /** Free My TV's own Settings, where the home screen is switched between Free My TV and the original one. */
    static Item switchHome() {
        return new Item(SWITCH, "switch-home", "Free My TV", null);
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }

    /** Opens the item with the system zoom transition growing out of its tile (the app starts opening right away). */
    static boolean open(Activity a, Item it, View from, int x, int y, int w, int h) {
        Intent intent;
        switch (it.kind) {
            case APP:
                intent = launchIntent(a.getPackageManager(), it.pkg);
                if (intent != null) intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                break;
            case SWITCH:
                intent = new Intent().setComponent(new ComponentName(a, SettingsActivity.class));
                break;
            case ANDROID_SETTINGS:
                intent = new Intent(Settings.ACTION_SETTINGS);
                break;
            default:
                return false;
        }
        if (intent == null) return false;
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(intent, ActivityOptions.makeScaleUpAnimation(from, x, y, Math.max(w, 1), Math.max(h, 1)).toBundle());
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            AppLog.w("couldn't open " + it.label + ": " + e.getMessage());
            return false;
        }
    }
}
