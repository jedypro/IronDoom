package ai.ui;

import java.awt.Color;

/**
 * Centralized UI constants.
 * Single source of truth for all magic numbers, colors, and string keys.
 */
public final class UIConstants {

    private UIConstants() {}

    // ── Card names ──────────────────────────────────────────────────────────
    public static final String CARD_INTRO          = "INTRO";
    public static final String CARD_GAME           = "GAME";
    public static final String CARD_SETTINGS       = "SETTINGS";
    public static final String CARD_GAME_OVER      = "GAME_OVER";
    public static final String CARD_LEVEL_COMPLETE = "LEVEL_COMPLETE";
    public static final String CARD_MODE_SELECT    = "MODE_SELECT";
    public static final String CARD_LOBBY          = "LOBBY";
    public static final String CARD_ATTACKER       = "ATTACKER";

    // ── Window sizing ────────────────────────────────────────────────────────
    public static final int DEFAULT_MAX_WIDTH  = 1200;
    public static final int DEFAULT_MAX_HEIGHT = 800;
    public static final int DEFAULT_MIN_WIDTH  = 900;
    public static final int DEFAULT_MIN_HEIGHT = 650;

    // ── World dimensions (logical game space) ────────────────────────────────
    public static final double WORLD_WIDTH  = 1200.0;
    public static final double WORLD_HEIGHT = 800.0;

    // ── Colors ───────────────────────────────────────────────────────────────
    public static final Color COLOR_PRIMARY          = new Color(30, 120, 190);
    public static final Color COLOR_BACKGROUND       = new Color(10, 15, 30);
    public static final Color COLOR_BATTERY          = new Color(58, 86, 49);
    public static final Color COLOR_SELECTED_BATTERY = new Color(70, 150, 230);

    // ── Fonts ────────────────────────────────────────────────────────────────
    public static final float FONT_SCORE_SIZE = 16f;
    public static final float FONT_TITLE_SIZE = 40f;

    // ── Aiming control ───────────────────────────────────────────────────────
    public static final int AIM_INTERVAL_MS   = 30;
    public static final int AIM_DELTA_DEFAULT = 4;   // degrees per tick
    public static final int AIM_DELTA_LASER   = 2;   // slower for precision

    // ── Triple-fire spread ───────────────────────────────────────────────────
    public static final int TRIPLE_FIRE_SPREAD = 4;  // degrees off-center

    // ── Warning banner ───────────────────────────────────────────────────────
    public static final int WARNING_DISPLAY_MS = 3000;

    // ── Explosion ────────────────────────────────────────────────────────────
    public static final int EXPLOSION_DURATION_MS = 600;

    // ── Floating text ────────────────────────────────────────────────────────
    public static final int FLOATING_TEXT_DEFAULT_DURATION_MS = 1500;

    // ── Theme level thresholds ───────────────────────────────────────────────
    public static final int THEME_DESERT_MIN  = 4;
    public static final int THEME_ARCTIC_MIN  = 7;
}
