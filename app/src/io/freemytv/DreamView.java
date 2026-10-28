package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;

import java.util.Date;

/** The screensaver, as on Apple TV: a dark screen with the time. Nothing else, no photos and no network. */
final class DreamView extends View {
    private static final long CLOCK_MS = 15_000;

    private final Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean running;

    DreamView(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        clock.setColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setTextAlign(Paint.Align.CENTER);
    }

    void start() {
        running = true;
        invalidate();
        postDelayed(tick, CLOCK_MS);
    }

    void stop() {
        running = false;
        removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            invalidate();
            if (running) postDelayed(this, CLOCK_MS);
        }
    };

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        clock.setTextSize(h * 0.3f);
        String time = DateFormat.getTimeFormat(getContext()).format(new Date());
        // Centered on the baseline: the digits sit in the middle of the screen.
        float y = h / 2f - (clock.descent() + clock.ascent()) / 2f;
        c.drawText(time, w / 2f, y, clock);
    }
}
