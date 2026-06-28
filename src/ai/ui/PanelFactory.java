package ai.ui;

import base.Params;
import shared.MainRouter;

import javax.swing.*;
import java.awt.*;

/**
 * Creates every card panel used by the root {@link CardLayout}.
 *
 * <p>Each {@code create*Panel()} method is self-contained: it builds its
 * own layout, labels, and buttons, then wires action listeners that
 * delegate to {@link ScreenNavigator} or {@link MainRouter}.</p>
 */
public class PanelFactory {

    private final ImageLoader     imageLoader;
    private final ScreenNavigator navigator;
    private final MainRouter      mainRouter;
    private final GameCanvas      canvas;
    private final UiState         uiState;

    // References returned to callers for later mutation
    private JLabel   levelCompleteTitleLabel;
    private JSpinner difficultySpinner;
    private boolean  lastModeEndless = false;

    public PanelFactory(
            ImageLoader     imageLoader,
            ScreenNavigator navigator,
            MainRouter      mainRouter,
            GameCanvas      canvas,
            UiState         uiState) {

        this.imageLoader = imageLoader;
        this.navigator   = navigator;
        this.mainRouter  = mainRouter;
        this.canvas      = canvas;
        this.uiState     = uiState;
    }

    // ── Accessors for widgets created inside panels ───────────────────────────

    public JLabel   getLevelCompleteTitleLabel() { return levelCompleteTitleLabel; }

    public JSpinner getDifficultySpinner()       { return difficultySpinner; }

    // ── Panel builders ────────────────────────────────────────────────────────

    public JPanel createIntroPanel() {
        JPanel panel = backgroundPanel();

        JLabel title    = styledLabel("IronDoom", Font.BOLD, UIConstants.FONT_TITLE_SIZE, Color.WHITE);
        JLabel subtitle = styledLabel("Protect your cities and survive the waves",
                                      Font.PLAIN, 18f, new Color(220, 220, 220));

        JButton playBtn     = WidgetFactory.createStyledButton("Play", 20);
        JButton settingsBtn = WidgetFactory.createStyledButton("Settings", 20);


        
        playBtn.addActionListener(e -> navigator.showModeSelect());
        settingsBtn.addActionListener(e -> navigator.showSettings(UIConstants.CARD_INTRO));

        JPanel buttons = opaqueFlow(24, playBtn, settingsBtn);

        GridBagConstraints c = gbc(0, 0, new Insets(0, 0, 16, 0));
        panel.add(title, c);
        c.gridy = 1; panel.add(subtitle, c);
        c.gridy = 2; c.insets = new Insets(30, 0, 0, 0); panel.add(buttons, c);

        return panel;
    }

    public JPanel createGameOverPanel() {
        JPanel panel = backgroundPanel();

        JLabel title   = styledLabel("You lost the battle!", Font.BOLD, 24f, Color.WHITE);
        JLabel message = styledLabel("Try again or exit.", Font.PLAIN, 14f, new Color(220, 230, 255));

        JButton playAgain = new JButton("Play Again");
        JButton exit      = new JButton("Exit");

        playAgain.addActionListener(e -> {
            if (canvas != null) {
                canvas.clearAllEffects();
            }
            if (mainRouter != null) {
                mainRouter.route("/team/reset", Params.of());
                mainRouter.route("/team/setMode", Params.of(lastModeEndless));
                mainRouter.route("/team/setSameLevel", Params.of());
            }
            navigator.showGame();
        });
        exit.addActionListener(e -> System.exit(0));

        JPanel buttons = opaqueFlow(12, playAgain, exit);

        GridBagConstraints c = gbc(0, 0, new Insets(0, 0, 12, 0));
        panel.add(title, c);
        c.gridy = 1; panel.add(message, c);
        c.gridy = 2; panel.add(buttons, c);

        return panel;
    }

    public JPanel createLevelCompletePanel() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(UIConstants.COLOR_BACKGROUND);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(true);

        JLabel title = styledLabel("You Win!", Font.BOLD, 42f, Color.WHITE);
        levelCompleteTitleLabel = title; // mutable label for custom messages

        JLabel message = styledLabel("Congratulations! You completed the level.",
                                     Font.PLAIN, 20f, new Color(220, 230, 255));

        JButton next = WidgetFactory.createStyledButton("To the next level", 18);
        next.addActionListener(e -> {
            if (canvas != null) {
                canvas.clearAllEffects();
            }
            if (mainRouter != null) mainRouter.route("/team/nextLevel", Params.of());
            navigator.showGame();
        });

        JPanel buttons = opaqueFlow(12, next);

        GridBagConstraints c = gbc(0, 0, new Insets(0, 0, 24, 0));
        panel.add(title, c);
        c.gridy = 1; panel.add(message, c);
        c.gridy = 2; panel.add(buttons, c);

        return panel;
    }

    public JPanel createSettingsPanel() {
        JPanel panel = backgroundPanel();

        JLabel titleLabel = styledLabel("Game Settings", Font.BOLD, 24f, Color.WHITE);

        JLabel diffLabel = styledLabel("Difficulty Level:", Font.BOLD, 14f, Color.WHITE);
        difficultySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        difficultySpinner.setFont(difficultySpinner.getFont().deriveFont(14f));

        JLabel soundLabel = styledLabel("Sound:", Font.BOLD, 14f, Color.WHITE);
        ToggleSwitch soundToggle = new ToggleSwitch(uiState.isSoundEnabled());
        soundToggle.addActionListener(e -> uiState.setSoundEnabled(soundToggle.isSelected()));

        JLabel aimLabel = styledLabel("Aiming Line:", Font.BOLD, 14f, Color.WHITE);
        ToggleSwitch aimToggle = new ToggleSwitch(canvas != null && canvas.isShowAim());
        aimToggle.addActionListener(e -> { if (canvas != null) canvas.setShowAim(aimToggle.isSelected()); });

        JButton apply = WidgetFactory.createStyledButton("Apply", 16);
        JButton back  = WidgetFactory.createStyledButton("Back", 16);

        apply.addActionListener(e -> {
            int level = (Integer) difficultySpinner.getValue();
            if (mainRouter != null) mainRouter.route("/team/updateSettings", Params.of(level));
            navigator.returnFromSettings();
        });
        back.addActionListener(e -> navigator.returnFromSettings());

        JPanel buttons = opaqueFlow(12, apply, back);

        GridBagConstraints c = gbc(0, 0, new Insets(0, 0, 20, 0));
        panel.add(titleLabel, c);
        c.gridy = 1; c.insets = new Insets(0, 0, 10, 0); panel.add(diffLabel, c);
        c.gridy = 2; c.insets = new Insets(0, 0, 20, 0); panel.add(difficultySpinner, c);
        c.gridy = 3; c.insets = new Insets(0, 0, 10, 0); panel.add(soundLabel, c);
        c.gridy = 4; c.insets = new Insets(0, 0, 20, 0); panel.add(soundToggle, c);
        c.gridy = 5; c.insets = new Insets(0, 0, 10, 0); panel.add(aimLabel, c);
        c.gridy = 6; c.insets = new Insets(0, 0, 20, 0); panel.add(aimToggle, c);
        c.gridy = 7; c.insets = new Insets(0, 0, 0, 0);  panel.add(buttons, c);

        return panel;
    }

    public JPanel createModeSelectPanel() {
        JPanel panel = backgroundPanel();

        JLabel title = styledLabel("Select Mission Type", Font.BOLD, 32f, Color.WHITE);

        JButton levelBtn   = WidgetFactory.createStyledButton("Classic Levels",   24);
        JButton endlessBtn = WidgetFactory.createStyledButton("Endless Survival", 18);
        JButton backBtn    = WidgetFactory.createStyledButton("Back",             18);

        levelBtn.addActionListener(e -> {
            lastModeEndless = false;
            navigator.showGameFromModeSelect(false);
        });
        endlessBtn.addActionListener(e -> {
            lastModeEndless = true;
            navigator.showGameFromModeSelect(true);
        });
        backBtn.addActionListener(e -> navigator.showIntro());

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.insets = new Insets(15, 0, 15, 0);

        c.gridy = 0; panel.add(title,     c);
        c.gridy = 1; panel.add(levelBtn,  c);
        c.gridy = 2; panel.add(endlessBtn,c);
        c.gridy = 3; panel.add(backBtn,   c);

        return panel;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Panel that draws the background image when available. */
    private JPanel backgroundPanel() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Image bg = imageLoader.getBackgroundImage();
                if (bg != null) g.drawImage(bg, 0, 0, getWidth(), getHeight(), null);
            }
        };
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel styledLabel(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(style, size));
        label.setForeground(color);
        return label;
    }

    private static JPanel opaqueFlow(int hGap, JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, hGap, 0));
        p.setOpaque(false);
        for (JButton b : buttons) p.add(b);
        return p;
    }

    private static GridBagConstraints gbc(int x, int y, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx  = x;
        c.gridy  = y;
        c.insets = insets;
        return c;
    }
}
