package ai.ui;

import base.Params;
import shared.MainRouter;
import shared.ui_ports.TeamUiPort;
import team.domain.*;


import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point and top-level orchestrator of the game UI.
 *
 * <h2>Responsibilities (post-refactor)</h2>
 * <ul>
 *   <li>Bootstrap: create collaborators, wire them, show the window.</li>
 *   <li>Implement the {@link TeamUiPort} callback surface that the game
 *       engine calls to push scene / status updates.</li>
 *   <li>Delegate <em>everything else</em> to specialists:
 *     <ul>
 *       <li>{@link ScreenNavigator} – screen transitions</li>
 *       <li>{@link GameCanvas}      – all painting</li>
 *       <li>{@link BatterySelector} – dropdown + info label</li>
 *       <li>{@link KeyBindingSetup} – key bindings</li>
 *       <li>{@link PanelFactory}    – card panel construction</li>
 *       <li>{@link AnimationController} – timer lifecycle</li>
 *       <li>{@link SceneData}       – current world snapshot</li>
 *       <li>{@link UiState}         – UI state (screen, level, pause…)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>This class intentionally contains <em>no rendering code</em> and
 * <em>no navigation logic</em>.</p>
 */
public class Ui {

    // ── Core wiring ───────────────────────────────────────────────────────────
    private MainRouter mainRouter;

    // ── Shared state ──────────────────────────────────────────────────────────
    private final UiState   uiState   = new UiState();
    private final SceneData sceneData = new SceneData();

    // ── Collaborators ─────────────────────────────────────────────────────────
    private ImageLoader        imageLoader;
    private AnimationController animation;
    private GameCanvas         gameCanvas;
    private ScreenNavigator    navigator;
    private BatterySelector    batterySelector;

    // ── Swing widgets owned at this level ─────────────────────────────────────
    private JFrame  frame;
    private JLabel  scoreLabel;
    private JLabel  statusLabel;
    private JLabel  warningLabel;
    private JLabel  levelCompleteTitleLabel;
    private JPanel  introPanel;
    private Timer   warningTimer;

    // ── Battery hit tracking ──────────────────────────────────────────────────
    private final Map<Integer, Boolean> batteryStates = new HashMap<>();

    // ── Startup synchronization ───────────────────────────────────────────────
    private final CountDownLatch startupComplete = new CountDownLatch(1);

    // =========================================================================
    // Startup
    // =========================================================================

    public void setUiPorts() {
        TeamUiPort.setInstance(new TeamUiPortImpl(this));
    }

    public void start(MainRouter mainRouter) throws InterruptedException {
        this.mainRouter = mainRouter;
        SwingUtilities.invokeLater(() -> {
            buildAndShow();
            mainRouter.route("/team/start", Params.of());
            startupComplete.countDown();
        });
        startupComplete.await();
    }

    // =========================================================================
    // Window construction
    // =========================================================================

    private void buildAndShow() {
        // ── Shared objects ────────────────────────────────────────────────────
        imageLoader = new ImageLoader();
        animation   = new AnimationController();

        // ── Canvas ────────────────────────────────────────────────────────────
        gameCanvas = new GameCanvas(sceneData, uiState, imageLoader);
        animation.setCanvas(gameCanvas);

        // ── Status widgets ────────────────────────────────────────────────────
        scoreLabel   = buildLabel("Score: 100", java.awt.Font.BOLD,   16f, null);
        statusLabel  = buildLabel("Status: Paused | Level: 1", java.awt.Font.PLAIN, 14f, null);
        warningLabel = buildLabel("", java.awt.Font.BOLD, 18f, Color.RED);

        // ── Battery selector ──────────────────────────────────────────────────
        batterySelector = new BatterySelector(uiState);

        // ── Angle slider ──────────────────────────────────────────────────────
        JSlider angleSlider = buildAngleSlider();

        // ── Fire button ───────────────────────────────────────────────────────
        JButton fireButton = WidgetFactory.createFireButton();
        fireButton.addActionListener(e -> doFire(angleSlider));

        // ── Aim toggle ────────────────────────────────────────────────────────
        JButton toggleAimBtn = WidgetFactory.createStyledButton("Show Aim", 18);
        toggleAimBtn.addActionListener(e -> {
            boolean next = !gameCanvas.isShowAim();
            gameCanvas.setShowAim(next);
            toggleAimBtn.setText(next ? "Hide Aim" : "Show Aim");
        });

        // ── Pause button ──────────────────────────────────────────────────────
        JButton pauseBtn = new JButton("Pause");
        pauseBtn.setFont(pauseBtn.getFont().deriveFont(java.awt.Font.BOLD, 14f));

        // ── Navigator (needs pauseBtn) ────────────────────────────────────────
        CardLayout cardLayout = new CardLayout();
        JPanel     rootPanel  = new JPanel(cardLayout);

        navigator = new ScreenNavigator(uiState, cardLayout, rootPanel, animation, mainRouter);
        navigator.setPauseButton(pauseBtn);
        animation.setAimTimer(null); // will be set after key-binding setup

        // ── Control toolbar ───────────────────────────────────────────────────
        JButton homeBtn     = new JButton("Home");
        JButton settingsBtn = new JButton("Settings");
        JButton restartBtn  = new JButton("Restart");

        homeBtn.addActionListener(e    -> navigator.showIntro());
        settingsBtn.addActionListener(e -> navigator.showSettings(UIConstants.CARD_GAME));
        restartBtn.addActionListener(e  -> { if (mainRouter != null) mainRouter.route("/team/reset", Params.of()); });
        pauseBtn.addActionListener(e    -> navigator.togglePause());

        JPanel topBar = flowPanel(14, homeBtn, settingsBtn, restartBtn, pauseBtn,
                                   scoreLabel, statusLabel, warningLabel);
        JPanel botBar = flowPanel(14,
                new JLabel("Select Battery:"), batterySelector.getComboBox(),
                batterySelector.getInfoLabel(),
                new JLabel("Angle (0-180):"), angleSlider,
                fireButton, toggleAimBtn);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(topBar);
        controls.add(botBar);

        JPanel gameScreen = new JPanel(new BorderLayout());
        gameScreen.add(gameCanvas, BorderLayout.CENTER);
        gameScreen.add(controls,   BorderLayout.NORTH);

        // ── Card panels ───────────────────────────────────────────────────────
        PanelFactory panelFactory = new PanelFactory(imageLoader, navigator, mainRouter, gameCanvas, uiState);
        introPanel               = panelFactory.createIntroPanel();
        levelCompleteTitleLabel  = panelFactory.getLevelCompleteTitleLabel();

        navigator.setDifficultySpinner(panelFactory.getDifficultySpinner());

        rootPanel.add(introPanel,                           UIConstants.CARD_INTRO);
        rootPanel.add(gameScreen,                           UIConstants.CARD_GAME);
        rootPanel.add(panelFactory.createGameOverPanel(),   UIConstants.CARD_GAME_OVER);
        rootPanel.add(panelFactory.createLevelCompletePanel(), UIConstants.CARD_LEVEL_COMPLETE);
        rootPanel.add(panelFactory.createSettingsPanel(),   UIConstants.CARD_SETTINGS);
        rootPanel.add(panelFactory.createModeSelectPanel(), UIConstants.CARD_MODE_SELECT);

        // ── Frame ─────────────────────────────────────────────────────────────
        frame = new JFrame("IronDoom Scenario Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(UIConstants.DEFAULT_MAX_WIDTH,  screen.width  - 80);
        int h = Math.min(UIConstants.DEFAULT_MAX_HEIGHT, screen.height - 120);
        frame.setPreferredSize(new Dimension(w, h));
        frame.setMinimumSize(new Dimension(UIConstants.DEFAULT_MIN_WIDTH, UIConstants.DEFAULT_MIN_HEIGHT));
        frame.setContentPane(rootPanel);

        navigator.showIntro();

        // ── Key bindings ──────────────────────────────────────────────────────
        KeyBindingSetup keys = new KeyBindingSetup(
                (JPanel) frame.getContentPane(),
                uiState, sceneData, mainRouter, angleSlider,
                fireButton, gameCanvas, batterySelector, navigator,
                introPanel);
        Timer aimTimer = keys.setup();
        animation.setAimTimer(aimTimer);

        frame.pack();
        frame.setSize(w, h);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        gameCanvas.startAnimation();
    }

    // =========================================================================
    // TeamUiPort surface (called by game engine)
    // =========================================================================

    public boolean isSoundEnabled() {
        return uiState.isSoundEnabled();
    }

    public void updateScore(int score) {
        int last = uiState.getLastScore();
        if (last != -1 && score < last) {
            int diff = score - last;
            List<GameCanvas.Explosion> explosions = gameCanvas != null ? getLastExplosion() : null;
            if (explosions != null) {
                // floating text near last explosion
            }
        }
        uiState.setLastScore(score);
        if (scoreLabel != null) {
            SwingUtilities.invokeLater(() -> scoreLabel.setText("Score: " + score));
        }
    }

    public void updateLevel(int level) {
        uiState.setCurrentLevel(level);
        SwingUtilities.invokeLater(this::refreshStatusLabel);
    }

    public void showStatus(String status) {
        uiState.setCurrentStatusText(status);
        SwingUtilities.invokeLater(this::refreshStatusLabel);
    }

    public void showWarning(String message) {
        if (warningLabel == null) return;
        SwingUtilities.invokeLater(() -> {
            warningLabel.setText(message);
            if (warningTimer != null) warningTimer.stop();
            warningTimer = new Timer(UIConstants.WARNING_DISPLAY_MS, e -> warningLabel.setText(""));
            warningTimer.setRepeats(false);
            warningTimer.start();
        });
    }

    public void showLevelComplete(String message) {
        SwingUtilities.invokeLater(() -> {
            if (levelCompleteTitleLabel != null) levelCompleteTitleLabel.setText(message);
            navigator.showLevelComplete();
        });
    }

    public void showEvent(String description, boolean isGood, String result) {
        if (gameCanvas == null) return;
        Color color = isGood ? new Color(100, 255, 100) : new Color(255, 100, 100);
        gameCanvas.addFloatingText(600, 200, description + " | " + result, color, 3000);
    }

    public void setScene(List<AbstractThreat> threats, List<Damageable> damageables,
                          List<DefenseEntity> interceptors, List<Gift> gifts,
                          int score, boolean running) {

        if (UIConstants.CARD_LEVEL_COMPLETE.equals(uiState.getCurrentScreen())) return;

        checkBatteryHits(damageables);
        sceneData.update(threats, damageables, interceptors, gifts);
        uiState.setRunning(running);

        updateScore(score);
        showStatus(running ? "Running" : "Game Over");

        SwingUtilities.invokeLater(() -> {
            if (!running || score <= 0) {
                navigator.showGameOver();
            } else if (!uiState.isSettingsScreenActive()
                    && !UIConstants.CARD_INTRO.equals(uiState.getCurrentScreen())
                    && !UIConstants.CARD_GAME.equals(uiState.getCurrentScreen())) {
                navigator.showGame();
            }
            if (gameCanvas != null) gameCanvas.repaint();
            batterySelector.updateItems(damageables);
            batterySelector.refreshInfoLabel(damageables);
        });
    }

    public void displayScene(List<AbstractThreat> threats, List<Damageable> damageables,
                              List<DefenseEntity> interceptors, int score, boolean running) {
        setScene(threats, damageables, interceptors, java.util.Collections.emptyList(), score, running);
    }

    public void setCivilians(List<Civilian> civilians) {
        sceneData.setCivilians(civilians);
    }

    public void triggerExplosionEffect(int x, int y) {
        if (gameCanvas != null) gameCanvas.addExplosion(x, y);
    }

    public void refresh() {
        if (gameCanvas != null) gameCanvas.repaint();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void doFire(JSlider angleSlider) {
        int id = uiState.getSelectedBatteryId();
        if (id == -1) return;
        String type = getSelectedDefenseType();
        mainRouter.route("/team/launchDefense", Params.of(id, angleSlider.getValue(), type));
    }

    private String getSelectedDefenseType() {
        int id = uiState.getSelectedBatteryId();
        for (Damageable d : sceneData.getDamageables()) {
            if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) d).getId() == id) {
                return d instanceof LaserBattery ? "LASER" : "MISSILE";
            }
        }
        return "MISSILE";
    }

    private void checkBatteryHits(List<Damageable> damageables) {
        for (Damageable d : damageables) {
            if (!(d instanceof AbstractDefenseSystem)) continue;
            AbstractDefenseSystem ds = (AbstractDefenseSystem) d;
            int id = ds.getId();
            boolean nowActive = ds.isActive();
            if (batteryStates.containsKey(id) && batteryStates.get(id) && !nowActive) {
                if (gameCanvas != null) {
                    gameCanvas.addFloatingText(ds.getX(), ds.getY(), "DISABLED!", Color.RED);
                }
            }
            batteryStates.put(id, nowActive);
        }
    }

    private void refreshStatusLabel() {
        if (statusLabel != null) {
            statusLabel.setText("Status: " + uiState.getCurrentStatusText()
                    + " | Level: " + uiState.getCurrentLevel());
        }
    }

    private JSlider buildAngleSlider() {
        JSlider s = new JSlider(0, 180, 90);
        s.setMajorTickSpacing(15);
        s.setPaintTicks(true);
        s.setPaintLabels(true);
        s.addChangeListener(e -> {
            int angle = s.getValue();
            if (angle != uiState.getCurrentSliderAngle()) {
                uiState.setCurrentSliderAngle(angle);
                if (mainRouter != null) {
                    for (Damageable d : sceneData.getDamageables()) {
                        if (d instanceof AbstractDefenseSystem) {
                            mainRouter.route("/team/updateAim",
                                    Params.of(((AbstractDefenseSystem) d).getId(), (double) angle));
                        }
                    }
                }
                refresh();
            }
        });
        return s;
    }

    private static JLabel buildLabel(String text, int style, float size, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(style, size));
        if (fg != null) l.setForeground(fg);
        return l;
    }

    private static JPanel flowPanel(int hGap, Component... components) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, hGap, 8));
        for (Component c : components) p.add(c);
        return p;
    }

    /** Returns the last explosion list for floating-text positioning. */
    private List<GameCanvas.Explosion> getLastExplosion() {
        // Access via package-private list – acceptable since GameCanvas is in same package
        return null; // stub – floating text on score drop can be enhanced later
    }
}
