package ai.ui;

import base.Params;
import shared.MainRouter;

import java.awt.CardLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSpinner;

/**
 * Owns all screen-transition logic.
 *
 * <p>Previously the {@code Ui} god-class had {@code showGameScreen()},
 * {@code showIntroScreen()}, etc. scattered throughout its 1 900-line
 * body.  This class collects them in one place, keeps {@link UiState}
 * consistent, and delegates the animation/timer side-effects to the
 * {@link AnimationController}.</p>
 */
public class ScreenNavigator {

    private final UiState             uiState;
    private final CardLayout          cardLayout;
    private final JPanel              rootPanel;
    private final AnimationController animation;
    private final MainRouter          mainRouter;

    // Widgets whose text we need to flip on pause/resume
    private JButton  pauseButton;
    private JSpinner difficultySpinner;

    public ScreenNavigator(
            UiState             uiState,
            CardLayout          cardLayout,
            JPanel              rootPanel,
            AnimationController animation,
            MainRouter          mainRouter) {

        this.uiState    = uiState;
        this.cardLayout = cardLayout;
        this.rootPanel  = rootPanel;
        this.animation  = animation;
        this.mainRouter = mainRouter;
    }

    public void setPauseButton(JButton btn)        { this.pauseButton       = btn; }
    public void setDifficultySpinner(JSpinner sp)  { this.difficultySpinner = sp; }

    // ── Public navigation methods ─────────────────────────────────────────────

    public void showGame() {
        uiState.setCurrentScreen(UIConstants.CARD_GAME);
        uiState.setSettingsScreenActive(false);
        uiState.setPaused(false);

        route("/team/resume");
        animation.resume();

        if (pauseButton != null) pauseButton.setText("Pause");
        updateStatus("Running");

        cardLayout.show(rootPanel, UIConstants.CARD_GAME);
    }

    public void showGameFromModeSelect(boolean endlessMode) {
        uiState.setCurrentScreen(UIConstants.CARD_GAME);
        uiState.setSettingsScreenActive(false);
        uiState.setPaused(false);

        route("/team/reset");
        route("/team/resume");
        animation.resume();

        mainRouter.route("/team/setMode", Params.of(endlessMode));

        if (pauseButton != null) pauseButton.setText("Pause");
        updateStatus("Running");
        cardLayout.show(rootPanel, UIConstants.CARD_GAME);
    }

    public void showIntro() {
        uiState.setCurrentScreen(UIConstants.CARD_INTRO);
        uiState.setSettingsScreenActive(false);
        uiState.setPaused(true);

        route("/team/pause");
        animation.pause();

        if (pauseButton != null) pauseButton.setText("Resume");
        updateStatus("Paused");

        cardLayout.show(rootPanel, UIConstants.CARD_INTRO);
    }

    public void showSettings(String fromScreen) {
        uiState.setSettingsScreenActive(true);
        uiState.setLastScreenBeforeSettings(fromScreen);
        uiState.setPaused(true);

        if (difficultySpinner != null) {
            difficultySpinner.setValue(uiState.getCurrentLevel());
        }

        route("/team/pause");
        animation.pause();

        if (pauseButton != null) pauseButton.setText("Resume");
        updateStatus("Paused");

        cardLayout.show(rootPanel, UIConstants.CARD_SETTINGS);
    }

    public void returnFromSettings() {
        if (UIConstants.CARD_GAME.equals(uiState.getLastScreenBeforeSettings())) {
            showGame();
        } else {
            showIntro();
        }
    }

    public void showGameOver() {
        uiState.setCurrentScreen(UIConstants.CARD_GAME_OVER);
        uiState.setSettingsScreenActive(false);
        uiState.setPaused(true);

        animation.pause();
        if (pauseButton != null) pauseButton.setText("Resume");
        updateStatus("Game Over");

        cardLayout.show(rootPanel, UIConstants.CARD_GAME_OVER);
    }

    public void showLevelComplete() {
        uiState.setCurrentScreen(UIConstants.CARD_LEVEL_COMPLETE);
        uiState.setSettingsScreenActive(false);
        uiState.setPaused(true);

        animation.pause();
        if (pauseButton != null) pauseButton.setText("Resume");
        updateStatus("Level Complete");

        cardLayout.show(rootPanel, UIConstants.CARD_LEVEL_COMPLETE);
    }

    public void showModeSelect() {
        cardLayout.show(rootPanel, UIConstants.CARD_MODE_SELECT);
    }

    // ── Pause / resume toggle (used by pauseButton and Space key) ────────────

    public void togglePause() {
        if (!uiState.isPaused()) {
            pause();
        } else {
            resume();
        }
    }

    public void pause() {
        uiState.setPaused(true);
        route("/team/pause");
        animation.pause();
        if (pauseButton != null) pauseButton.setText("Resume");
        updateStatus("Paused");
    }

    public void resume() {
        uiState.setPaused(false);
        route("/team/resume");
        animation.resume();
        if (pauseButton != null) pauseButton.setText("Pause");
        updateStatus("Running");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void route(String path) {
        if (mainRouter != null) {
            mainRouter.route(path, Params.of());
        }
    }

    private void updateStatus(String text) {
        uiState.setCurrentStatusText(text);
    }
}
