package ai.ui;

/**
 * Holds the current navigational and runtime state of the UI.
 *
 * <p>Extracted from {@code Ui} to give the state a single home.
 * The canvas and panels read from this object; the navigation
 * controller writes to it.</p>
 */
public class UiState {

    // ── Screen navigation ────────────────────────────────────────────────────
    private String currentScreen              = UIConstants.CARD_INTRO;
    private String lastScreenBeforeSettings   = UIConstants.CARD_INTRO;
    private boolean settingsScreenActive      = false;

    // ── Game runtime ─────────────────────────────────────────────────────────
    private boolean paused    = true;
    private boolean running   = true;
    private int     currentLevel = 1;
    private String  currentStatusText = "Paused";
    private int     lastScore = -1;

    // ── Aim ──────────────────────────────────────────────────────────────────
    private int  currentSliderAngle = 90;   // 90° = straight up
    private int  aimDirection       = 0;    // -1 left, 0 none, +1 right
    private int  selectedBatteryId  = -1;

    // ── Sound ────────────────────────────────────────────────────────────────
    private boolean soundEnabled = true;

    // =========================================================================
    // Getters / setters
    // =========================================================================

    public String getCurrentScreen() { return currentScreen; }
    public void   setCurrentScreen(String s) { currentScreen = s; }

    public String getLastScreenBeforeSettings() { return lastScreenBeforeSettings; }
    public void   setLastScreenBeforeSettings(String s) { lastScreenBeforeSettings = s; }

    public boolean isSettingsScreenActive() { return settingsScreenActive; }
    public void    setSettingsScreenActive(boolean b) { settingsScreenActive = b; }

    public boolean isPaused() { return paused; }
    public void    setPaused(boolean b) { paused = b; }

    public boolean isRunning() { return running; }
    public void    setRunning(boolean b) { running = b; }

    public int  getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int l) { currentLevel = l; }

    public String getCurrentStatusText() { return currentStatusText; }
    public void   setCurrentStatusText(String s) { currentStatusText = s; }

    public int  getLastScore() { return lastScore; }
    public void setLastScore(int s) { lastScore = s; }

    public int  getCurrentSliderAngle() { return currentSliderAngle; }
    public void setCurrentSliderAngle(int a) { currentSliderAngle = a; }

    public int  getAimDirection() { return aimDirection; }
    public void setAimDirection(int d) { aimDirection = d; }

    public int  getSelectedBatteryId() { return selectedBatteryId; }
    public void setSelectedBatteryId(int id) { selectedBatteryId = id; }

    public boolean isSoundEnabled() { return soundEnabled; }
    public void    setSoundEnabled(boolean b) { soundEnabled = b; }
}
