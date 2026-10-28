package io.freemytv;

/**
 * Settings changed on the TV itself (Free My TV > Settings). config.properties only gives the first
 * values; anything the user changes here wins.
 */
final class Prefs {
    private Prefs() {}

    /** Bumped by every change: screens compare it to know when to redraw. */
    static volatile int version;
}
