package io.freemytv;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The home screen, in the style of Google TV's Apps-only grid: a dark room and a grid of app tiles, five to a row.
 * The grid shows the apps installed on this TV. The focused tile lifts and glows in its app's color, its name shows
 * underneath, and the room takes a soft tint from its colors.
 */
final class HomeView extends View {
    /** Tiles per row, as Google TV's grid. */
    static final int PER_ROW = 5;
    private static final int BASE = 0xFF0B0D12;
    private static final float FOCUS_SCALE = 1.16f;   // as Google TV's Apps-only grid
    private static final float TILE_WIDTH = 0.156f;   // of the screen width, as Google TV's grid
    private static final float CELL_WIDTH = 0.18f;    // column pitch, as Google TV's grid
    private static final int ICON_PX = 320;           // minimum tile width kept in memory, in pixels
    private static final float TILE_RATIO = 9f / 16f;
    private static final int ROOM_SCALE = 8;          // the room's glow is drawn at 1/8 size
    /** Tiles kept in memory at once. A tile that scrolls further away is rebuilt if it comes back. */
    private static final int MAX_TILES = PER_ROW * 4;   // four rows: enough for everything on screen while scrolling

    /** One app's tile: its picture, the glow that goes around it when it has focus, and its average color. */
    private static final class Tile {
        final Bitmap image, glow;
        final int average;

        Tile(Bitmap image, Bitmap glow, int average) {
            this.image = image;
            this.glow = glow;
            this.average = average;
        }

        void recycle() {
            image.recycle();
            if (glow != null) glow.recycle();
        }
    }

    private final Activity activity;
    private final List<HomeApps.Item> items = new ArrayList<>();
    private final Map<String, Tile> tiles = new LinkedHashMap<String, Tile>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Tile> eldest) {
            if (size() <= MAX_TILES) return false;
            eldest.getValue().recycle();
            return true;
        }
    };
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint room = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF roomBox = new RectF();
    private final RectF glowBox = new RectF();
    private final Paint name = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private Bitmap roomBitmap;
    private int roomColor, roomW, roomH;

    private int focus;                   // index into items
    private float[] lift = new float[0]; // 0..1 per item, eased toward focused
    private float slideY;                // the row on the top line, eased toward the focused row
    private final float[] tint = {11, 13, 18};   // room color, eased
    private final float[] tintGoal = {11, 13, 18};

    HomeView(Activity activity) {
        super(activity);
        this.activity = activity;
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setTextAlign(Paint.Align.CENTER);
        name.setColor(Color.WHITE);
        glowShadow.setColor(Color.BLACK);
        gear.setColor(Color.WHITE);
        ring.setColor(Color.WHITE);
        ring.setStyle(Paint.Style.STROKE);
        loadItems();
    }

    /** The apps installed on this TV, then Free My TV's switch and the TV's settings. */
    private void loadItems() {
        List<String> before = keys();
        PackageManager pm = activity.getPackageManager();
        items.clear();
        for (String pkg : HomeApps.installedPackages(activity)) items.add(HomeApps.app(pkg, HomeApps.label(pm, pkg)));
        items.add(HomeApps.switchHome());
        items.add(HomeApps.settings());
        if (before.equals(keys())) return;
        // The list changed (an app was installed or removed): start again from the first tile, and let go of
        // the tiles of apps that are gone.
        lift = new float[items.size()];
        focus = 0;
        slideY = 0;
        List<String> now = keys();
        for (Iterator<Map.Entry<String, Tile>> it = tiles.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Tile> e = it.next();
            if (!now.contains(e.getKey())) {
                e.getValue().recycle();
                it.remove();
            }
        }
    }

    private List<String> keys() {
        List<String> out = new ArrayList<>();
        for (HomeApps.Item it : items) out.add(it.key);
        return out;
    }

    /** The tile's width on screen, in pixels, so it stays sharp on a 4K TV and costs nothing extra on 1080p. */
    private int tileW() {
        return Math.max(ICON_PX, Math.round(getWidth() * TILE_WIDTH * FOCUS_SCALE));
    }

    /** Tiles are 16:9, as on Google TV's home screen. */
    private int tileH() {
        return Math.round(tileW() * TILE_RATIO);
    }

    /**
     * The app's tile, kept while it is in the cache (see MAX_TILES). It is built from the app's TV banner cropped to
     * 16:9, as Google TV shows it. An app without a banner gets its icon, centered on its own color.
     */
    private Tile tileOf(HomeApps.Item it) {
        int w = tileW();
        Tile t = tiles.get(it.key);
        if (t != null && t.image.getWidth() == w) return t;
        if (t != null) {
            t.recycle();
            tiles.remove(it.key);
        }
        t = buildTile(it, w, tileH());
        tiles.put(it.key, t);
        return t;
    }

    private Tile buildTile(HomeApps.Item it, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Drawable banner = it.kind == HomeApps.APP ? artwork(it.pkg, true) : null;
        if (banner != null) {
            int bw = Math.max(1, banner.getIntrinsicWidth()), bh = Math.max(1, banner.getIntrinsicHeight());
            float scale = Math.max(w / (float) bw, h / (float) bh);
            int dw = Math.round(bw * scale), dh = Math.round(bh * scale);
            banner.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
            banner.draw(c);
        } else if (it.kind == HomeApps.ANDROID_SETTINGS) {
            // The TV's settings: a gray card with a gear.
            c.drawColor(0xFF3A3D45);
            drawGear(c, w / 2f, h / 2f, h * 0.2f);
        } else if (it.kind == HomeApps.SWITCH) {
            // The switch: a gray card with Free My TV's own icon.
            c.drawColor(0xFF3A3D45);
            Drawable own = artwork(activity.getPackageName(), false);
            if (own != null) {
                int side = Math.round(h * 0.5f);
                own.setBounds((w - side) / 2, (h - side) / 2, (w + side) / 2, (h + side) / 2);
                own.draw(c);
            }
        } else {
            int side = Math.round(h * 0.72f);
            Bitmap square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            Canvas sc = new Canvas(square);
            Drawable icon = it.kind == HomeApps.APP ? artwork(it.pkg, false) : null;
            if (icon == null) {
                sc.drawColor(0xFF3A3D45);
            } else {
                icon.setBounds(0, 0, side, side);
                icon.draw(sc);
            }
            Bitmap one = Bitmap.createScaledBitmap(square, 1, 1, true);
            int px = one.getPixel(0, 0);
            one.recycle();
            c.drawColor(Color.rgb(Color.red(px) / 2, Color.green(px) / 2, Color.blue(px) / 2));
            c.drawBitmap(square, (w - side) / 2f, (h - side) / 2f, null);
            square.recycle();
        }
        // The rounded corners are part of the image, so the tile draws without a clip (which is slow on the GPU).
        Bitmap rounded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint corners = new Paint(Paint.ANTI_ALIAS_FLAG);
        corners.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(rounded).drawRoundRect(0, 0, w, h, h * 0.08f, h * 0.08f, corners);
        b.recycle();
        // The app's color and its glow are made with the tile, so moving focus to it later costs nothing.
        Bitmap one = Bitmap.createScaledBitmap(rounded, 1, 1, true);
        int px = one.getPixel(0, 0);
        one.recycle();
        Bitmap glow = glowFor(Color.argb(160, Color.red(px), Color.green(px), Color.blue(px)));
        return new Tile(rounded, glow, px);
    }

    /** A settings gear in white, drawn in code (no image file), centered at cx, cy with radius r. */
    private void drawGear(Canvas c, float cx, float cy, float r) {
        gear.setStyle(Paint.Style.STROKE);
        gear.setStrokeWidth(r * 0.34f);
        c.drawCircle(cx, cy, r * 0.62f, gear);
        gear.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cy);
            c.drawRoundRect(cx - r * 0.2f, cy - r, cx + r * 0.2f, cy - r * 0.6f, r * 0.08f, r * 0.08f, gear);
            c.restore();
        }
    }

    /** The room's glow, drawn at 1/8 of the screen and stretched to fill it: soft enough that the size doesn't show. */
    private Bitmap roomFor(int w, int h, int argb) {
        int sw = Math.max(1, w / ROOM_SCALE), sh = Math.max(1, h / ROOM_SCALE);
        Bitmap b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Paint p = new Paint();
        float r = Math.max(sw, sh) * 0.75f;
        p.setShader(new RadialGradient(sw * 0.5f, sh * 0.55f, r, new int[] {argb, Color.argb(0, 0, 0, 0)},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        new Canvas(b).drawRect(0, 0, sw, sh, p);
        if (roomBitmap != null) roomBitmap.recycle();
        return b;
    }

    /**
     * The glow around a tile, in the app's color, blurred once here instead of on every frame. It is drawn at half size
     * and stretched when shown: it is so soft that the size doesn't show, and it is a quarter of the pixels to fill.
     * Null before the screen has a size.
     */
    private Bitmap glowFor(int color) {
        float size = tileWidth(), height = size * TILE_RATIO;
        float m = height * 0.6f, half = 0.5f;
        int gw = Math.round((size + 2 * m) * half), gh = Math.round((height + 2 * m) * half);
        if (gw <= 0 || gh <= 0) return null;   // no layout yet
        Bitmap g = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
        glowShadow.setShadowLayer(height * 0.45f * half, 0, 0, color);
        new Canvas(g).drawRoundRect(m * half, m * half, (m + size) * half, (m + height) * half,
                height * 0.08f * half, height * 0.08f * half, glowShadow);
        return g;
    }

    /**
     * The app's banner or icon at the largest density it ships. The default lookup picks the screen's density, which is
     * what made the artwork look soft on a 4K TV. Null if the app has none.
     */
    private Drawable artwork(String pkg, boolean banner) {
        PackageManager pm = activity.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            int res = banner ? info.banner : info.icon;
            if (res == 0) return banner ? null : pm.getApplicationIcon(info);
            Drawable d = pm.getResourcesForApplication(info).getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
            return d != null || banner ? d : pm.getApplicationIcon(info);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            return null;
        }
    }

    /** The average color of the focused tile, which tints the room. */
    private void tintFor(int index) {
        int px = tileOf(items.get(index)).average;
        tintGoal[0] = Color.red(px) * 0.5f;
        tintGoal[1] = Color.green(px) * 0.5f;
        tintGoal[2] = Color.blue(px) * 0.5f;
    }

    /** Free My TV's home screen, when the TV turns it on or the Home button is pressed. The app list is read again here. */
    void resume() {
        loadItems();
        postInvalidateOnAnimation();
    }

    void pause() {
        // Nothing animates while the screen is away.
    }

    void destroy() {
        pause();
        for (Tile t : tiles.values()) t.recycle();
        tiles.clear();
        if (roomBitmap != null) roomBitmap.recycle();
        roomBitmap = null;
    }

    /** Back to the entry state: focus on the first app. */
    void reset(boolean animated) {
        focus = 0;
        if (!animated) {
            slideY = 0;
            Arrays.fill(lift, 0);
        }
        retarget();
    }

    private void retarget() {
        if (items.isEmpty()) return;
        tintFor(focus);
        invalidate();
        postInvalidateOnAnimation();
    }

    /** Layout: the cell is the width of one icon slot; rows are one screen-height apart, scrolled to the focused row. */
    private float cell() { return getWidth() * CELL_WIDTH; }
    private float tileWidth() { return getWidth() * TILE_WIDTH; }
    private float left() { return getWidth() * 0.06f + tileWidth() / 2f; }
    private float rowPitch() { return getHeight() * 0.254f; }
    private float topRow() { return getHeight() * 0.11f + tileWidth() * TILE_RATIO / 2f; }

    private float centerX(int i) { return left() + cell() * (i % PER_ROW); }
    private float centerY(int i) { return topRow() + (i / PER_ROW - slideY) * rowPitch(); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        // The room: the base color, with a soft tint from the focused tile.
        c.drawColor(BASE);
        boolean moving = false;
        for (int i = 0; i < 3; i++) {
            float d = tintGoal[i] - tint[i];
            tint[i] += d * 0.18f;
            if (Math.abs(d) > 0.4f) moving = true;
        }
        // The room's glow is made again only when the tint changes, and drawn small and stretched (it is soft).
        int argb = Color.argb(45, (int) tint[0], (int) tint[1], (int) tint[2]);
        if (argb != roomColor || w != roomW || h != roomH) {
            roomColor = argb;
            roomW = w;
            roomH = h;
            roomBitmap = roomFor(w, h, argb);
        }
        roomBox.set(0, 0, w, h);
        c.drawBitmap(roomBitmap, null, roomBox, room);

        // The grid. Scrolling eases toward the focused row.
        // The focused row sits on the lower line, so the row above stays on screen.
        float focusRow = Math.max(0, focus / PER_ROW - 1);
        float dy = focusRow - slideY;
        slideY += dy * 0.25f;
        if (Math.abs(dy) > 0.002f) moving = true;

        float size = tileWidth();
        float tileHeight = size * TILE_RATIO;
        for (int i = 0; i < items.size(); i++) {
            float target = i == focus ? 1 : 0;
            float d = target - lift[i];
            lift[i] += d * 0.25f;
            if (Math.abs(d) > 0.002f) moving = true;

            float x = centerX(i), y = centerY(i);
            if (y < -size || y > h + size) continue;   // off the screen: no tile is built or drawn
            float s = 1 + (FOCUS_SCALE - 1) * lift[i];
            float halfW = size * s / 2f, halfH = tileHeight * s / 2f;
            box.set(x - halfW, y - halfH, x + halfW, y + halfH);
            float radius = tileHeight * 0.08f * s;

            Tile tile = tileOf(items.get(i));

            // The glow in the app's own color, made with its tile (see buildTile).
            if (lift[i] > 0.01f && tile.glow != null) {
                glowPaint.setAlpha((int) (255 * lift[i]));
                // The glow is stored at half size: its full size on screen is twice its pixel size.
                glowBox.set(x - tile.glow.getWidth() * s, y - tile.glow.getHeight() * s,
                        x + tile.glow.getWidth() * s, y + tile.glow.getHeight() * s);
                c.drawBitmap(tile.glow, null, glowBox, glowPaint);
            }

            c.drawBitmap(tile.image, null, box, icon);

            if (i == focus) {
                // A light border on the lifted tile.
                ring.setStrokeWidth(h * 0.003f);
                ring.setAlpha((int) (200 * lift[i]));
                c.drawRoundRect(box, radius, radius, ring);
                name.setTextSize(h * 0.024f);
                name.setAlpha((int) (217 * lift[i]));
                c.drawText(items.get(i).label, x, y + halfH + h * 0.03f, name);
            }
        }

        if (moving) postInvalidateOnAnimation();
    }

    /** Remote: arrows move through the grid, Select opens. Other keys are left to the system. */
    boolean onKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ours = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (!ours) return false;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  move(focus % PER_ROW > 0 ? -1 : 0); break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(focus % PER_ROW < PER_ROW - 1 ? 1 : 0); break;
            case KeyEvent.KEYCODE_DPAD_UP:    move(focus - PER_ROW >= 0 ? -PER_ROW : 0); break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  move(focus + PER_ROW < items.size() ? PER_ROW : 0); break;
            default: open();
        }
        return true;
    }

    private void move(int step) {
        if (step == 0) return;
        int next = Math.max(0, Math.min(items.size() - 1, focus + step));
        if (next == focus) return;
        focus = next;
        retarget();
    }

    /** Opens the focused item, growing out of its tile. */
    private void open() {
        if (items.isEmpty()) return;
        HomeApps.Item it = items.get(focus);
        float size = tileWidth(), tileHeight = size * TILE_RATIO;
        int[] at = new int[2];
        getLocationOnScreen(at);
        float x = centerX(focus), y = centerY(focus);
        HomeApps.open(activity, it, this, at[0] + (int) (x - size / 2), at[1] + (int) (y - tileHeight / 2),
                (int) size, (int) tileHeight);
    }
}
