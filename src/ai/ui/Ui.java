package ai.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import base.Params;
import shared.MainRouter;
import shared.ui_ports.TeamUiPort;
import team.domain.AbstractThreat;
import team.domain.Damageable;
import team.domain.DefenseEntity;
import team.domain.LaserBattery;
import team.domain.GameState;
import team.domain.GroundAsset;
import team.domain.InterceptorBattery;
import team.domain.InterceptorMissile;
import team.domain.UAV;
import team.domain.AbstractDefenseSystem;
import team.domain.LightShield;

public class Ui {
    private MainRouter mainRouter;
    private TeamUiPortImpl uiInstance;
    
    // UI configuration constants extracted for maintainability
    private static final class UIConstants {
        // Card names
        static final String CARD_INTRO = "INTRO";
        static final String CARD_GAME = "GAME";
        static final String CARD_SETTINGS = "SETTINGS";
        static final String CARD_GAME_OVER = "GAME_OVER";
        static final String CARD_LEVEL_COMPLETE = "LEVEL_COMPLETE";

        // Default window sizing
        static final int DEFAULT_MAX_WIDTH = 1200;
        static final int DEFAULT_MAX_HEIGHT = 800;
        static final int DEFAULT_MIN_WIDTH = 900;
        static final int DEFAULT_MIN_HEIGHT = 650;

        // Colors
        static final Color COLOR_PRIMARY = new Color(30, 120, 190);
        static final Color COLOR_BACKGROUND = new Color(10, 15, 30);
        static final Color COLOR_BATTERY = new Color(58, 86, 49);
        static final Color COLOR_SELECTED_BATTERY = new Color(70, 150, 230);

        // Fonts
        static final float FONT_SCORE_SIZE = 16f;
        static final float FONT_TITLE_SIZE = 40f;
    }
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel rootPanel;
    private JPanel introPanel;
    private GameCanvas gameCanvas;
    private JPanel gameOverPanel;
    private JPanel levelCompletePanel;
    private JPanel settingsPanel;
    private javax.swing.JSpinner difficultySpinner;
    private JLabel scoreLabel;
    private JLabel statusLabel;
    private JLabel warningLabel;
    private JLabel levelCompleteTitleLabel;
    private int selectedBatteryId = -1;
    private javax.swing.JComboBox<Integer> batteryComboBox;
    private javax.swing.JLabel batteryInfoLabel;
    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private final List<DefenseEntity> interceptors = new ArrayList<>();
    private final GameState gameState = new GameState(100, 1, true);

    private boolean running = true;
    private int currentLevel = 1;
    private String currentStatusText = "Paused";
    private String currentScreen = "INTRO";
    private String lastScreenBeforeSettings = "INTRO";
    private java.awt.Image settingsBackgroundImage;
    private boolean paused = true;
    private JButton pauseButton;
    private boolean settingsScreenActive = false;
    private javax.swing.Timer aimTimer;
    private javax.swing.Timer warningTimer;
    private int aimDirection = 0; // -1 = left, +1 = right, 0 = none
    private static final int AIM_INTERVAL_MS = 30;
    private static final int AIM_DELTA = 4; // degrees per tick
    private CountDownLatch startupComplete = new CountDownLatch(1);
    private int currentSliderAngle = 90; // 90 מעלות = מכוון ישר למעלה

    public void setUiPorts() {
        uiInstance = new TeamUiPortImpl(this);
        TeamUiPort.setInstance(uiInstance);
    }

    public void start(MainRouter mainRouter) throws InterruptedException {
        this.mainRouter = mainRouter;
        SwingUtilities.invokeLater(() -> {
            createAndShowWindow();
            mainRouter.route("/team/start", Params.of());
            startupComplete.countDown();
        });
        
        startupComplete.await();
    }

    private String getSelectedDefenseType() {
        for (Damageable d : damageables) {
            if (d instanceof AbstractDefenseSystem) {
                AbstractDefenseSystem ds = (AbstractDefenseSystem) d;
                if (ds.getId() == selectedBatteryId) {
                    if (ds instanceof LaserBattery) return "LASER";
                    return "MISSILE";
                }
            }
        }
        return "MISSILE";
    }

    private void createAndShowWindow() {
        frame = new JFrame("IronDoom Scenario Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1200, screenSize.width - 80);
        int height = Math.min(800, screenSize.height - 120);
        frame.setPreferredSize(new java.awt.Dimension(width, height));
        frame.setMinimumSize(new java.awt.Dimension(900, 650));
        frame.setLocationRelativeTo(null);

        scoreLabel = new JLabel("Score: 100");
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel = new JLabel("Status: Paused | Level: 1");
        statusLabel.setFont(statusLabel.getFont().deriveFont(14f));
        
        warningLabel = new JLabel("");
        warningLabel.setFont(warningLabel.getFont().deriveFont(Font.BOLD, 18f));
        warningLabel.setForeground(Color.RED);

        settingsBackgroundImage = loadSettingsBackgroundImage();
        gameCanvas = new GameCanvas();
        frame.setLayout(new BorderLayout());

        // === Angle Slider Setup (0-90 Degrees) ===
        // 0 = משוחרר בכיוון שמאלה (קרקעית), 90 = למעלה (אנכי)
        javax.swing.JSlider angleSlider = new javax.swing.JSlider(0, 180, 90);
        angleSlider.setMajorTickSpacing(15);
        angleSlider.setPaintTicks(true);
        angleSlider.setPaintLabels(true);
        angleSlider.addChangeListener(e -> {
        int newAngle = angleSlider.getValue();
        if (newAngle != this.currentSliderAngle) {
            this.currentSliderAngle = newAngle;
            if (mainRouter != null && selectedBatteryId != -1) {
                mainRouter.route("/team/updateAim", Params.of(selectedBatteryId, (double) currentSliderAngle));
            }
            refresh(); 
        }
     });

        // === Battery Selection Dropdown ===
        batteryComboBox = new javax.swing.JComboBox<>();
        batteryComboBox.addActionListener(e -> {
            Integer selected = (Integer) batteryComboBox.getSelectedItem();
            if (selected != null) {
                this.selectedBatteryId = selected;
                updateBatteryInfoDisplay();
            }
        });

        // === Battery Info Label ===
        batteryInfoLabel = new javax.swing.JLabel("Status: No Battery Available | Ammo: 0");
        batteryInfoLabel.setFont(batteryInfoLabel.getFont().deriveFont(Font.BOLD, 14f));

        // === Fire Button Setup ===
        javax.swing.JButton fireButton = new javax.swing.JButton("FIRE!");
        fireButton.setFont(fireButton.getFont().deriveFont(Font.BOLD, 16f));
        fireButton.setBackground(Color.RED);
        fireButton.setForeground(Color.WHITE);

        JButton homeButton = new JButton("Home");
        homeButton.addActionListener(e -> showIntroScreen());
        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e -> showSettingsScreen("GAME"));
        JButton restartButton = new JButton("Restart");
        restartButton.addActionListener(e -> {
            if (mainRouter != null) {
                mainRouter.route("/team/reset", Params.of());
            }
        });

        pauseButton = new JButton("Pause");
        pauseButton.setFont(pauseButton.getFont().deriveFont(Font.BOLD, 14f));
        pauseButton.addActionListener(e -> {
            if (!paused) {
                paused = true;
                if (mainRouter != null) {
                    mainRouter.route("/team/pause", Params.of());
                }
                if (gameCanvas != null) gameCanvas.pauseAnimation();
                if (aimTimer != null) aimTimer.stop();
                pauseButton.setText("Resume");
                showStatus("Paused");
            } else {
                paused = false;
                if (mainRouter != null) {
                    mainRouter.route("/team/resume", Params.of());
                }
                if (gameCanvas != null) gameCanvas.resumeAnimation();
                if (aimTimer != null) aimTimer.start();
                pauseButton.setText("Pause");
                showStatus("Running");
            }
        });

        JPanel topControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        topControls.add(homeButton);
        topControls.add(settingsButton);
        topControls.add(restartButton);
        topControls.add(pauseButton);
        topControls.add(scoreLabel);
        topControls.add(statusLabel);
        topControls.add(warningLabel);

        JPanel bottomControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        bottomControls.add(new JLabel("Select Battery:"));
        bottomControls.add(batteryComboBox);
        bottomControls.add(batteryInfoLabel);
        bottomControls.add(new JLabel("Angle (0-90):"));
        bottomControls.add(angleSlider);
        bottomControls.add(fireButton);

        JPanel controlPanel = new JPanel();
        controlPanel.setOpaque(false); // Make control panel transparent
        controlPanel.setLayout(new javax.swing.BoxLayout(controlPanel, javax.swing.BoxLayout.Y_AXIS));
        controlPanel.add(topControls);
        controlPanel.add(bottomControls);

        JPanel gameScreen = new JPanel(new BorderLayout()); // This was already here.
        gameScreen.add(gameCanvas, BorderLayout.CENTER);

        gameScreen.add(controlPanel, BorderLayout.NORTH); // Add control panel to game screen
        gameOverPanel = createGameOverPanel();
        levelCompletePanel = createLevelCompletePanel();
        settingsPanel = createSettingsPanel();
        introPanel = createIntroPanel();
        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);
        rootPanel.add(introPanel, "INTRO");
        rootPanel.add(gameScreen, "GAME");
        rootPanel.add(gameOverPanel, "GAME_OVER");
        rootPanel.add(levelCompletePanel, "LEVEL_COMPLETE");
        rootPanel.add(settingsPanel, "SETTINGS");

        frame.setContentPane(rootPanel);
        showIntroScreen();
        // wire events separately for clarity
        setupEventHandlers();
        frame.pack();
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);

        fireButton.addActionListener(e -> {
            if (selectedBatteryId == -1) {
                System.out.println("Cannot fire: No battery selected.");
                return;
            }
            int angle = angleSlider.getValue();
            String defenseType = getSelectedDefenseType();
            
            Params params = Params.of(selectedBatteryId, angle, defenseType);
            System.out.println("Launching " + defenseType + " from System " + selectedBatteryId + " | Angle: " + angle);
            mainRouter.route("/team/launchDefense", params);
        });

        // === Global Key Bindings (continuous arrows & Z) ===
        // Initialize continuous aim timer (uses angleSlider)
        aimTimer = new javax.swing.Timer(AIM_INTERVAL_MS, ev -> {
            if (aimDirection != 0) {
                int val = angleSlider.getValue();
                int delta = aimDirection * AIM_DELTA;
                int newVal = Math.max(angleSlider.getMinimum(), Math.min(angleSlider.getMaximum(), val + delta));
                if (newVal != val) angleSlider.setValue(newVal);
            }
        });

        javax.swing.JPanel contentPane = (javax.swing.JPanel) frame.getContentPane();
        javax.swing.InputMap inputMap = contentPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap actionMap = contentPane.getActionMap();

        // Start/stop aiming left
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed LEFT"), "startAimLeft");
        actionMap.put("startAimLeft", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                aimDirection = -1;
                aimTimer.start();
            }
        });
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("released LEFT"), "stopAimLeft");
        actionMap.put("stopAimLeft", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (aimDirection == -1) aimDirection = 0;
                if (aimDirection == 0) aimTimer.stop();
            }
        });

        // Start/stop aiming right
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed RIGHT"), "startAimRight");
        actionMap.put("startAimRight", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                aimDirection = +1;
                aimTimer.start();
            }
        });
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("released RIGHT"), "stopAimRight");
        actionMap.put("stopAimRight", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (aimDirection == +1) aimDirection = 0;
                if (aimDirection == 0) aimTimer.stop();
            }
        });

        // Cycle defense battery with up/down arrows
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed UP"), "selectPrevBattery");
        actionMap.put("selectPrevBattery", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ("GAME".equals(currentScreen)) selectBatteryIndex(-1);
            }
        });
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed DOWN"), "selectNextBattery");
        actionMap.put("selectNextBattery", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ("GAME".equals(currentScreen)) selectBatteryIndex(+1);
            }
        });

        // Fire with Z
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed Z"), "triggerFire");
        actionMap.put("triggerFire", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireButton.doClick();
            }
        });

        // Fire three missiles with X (current angle, +4°, -4°)
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed X"), "triggerTripleFire");
        actionMap.put("triggerTripleFire", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (selectedBatteryId == -1) {
                    System.out.println("Cannot fire: No battery selected.");
                    return;
                }
                int currentAngle = angleSlider.getValue();
                String defenseType = getSelectedDefenseType();
                
                // Fire at current angle
                Params params1 = Params.of(selectedBatteryId, currentAngle, defenseType);
                System.out.println("Triple Fire - " + defenseType + " 1: System " + selectedBatteryId + " | Angle: " + currentAngle);
                mainRouter.route("/team/launchDefense", params1);
                
                // Fire at current angle + 4 degrees
                int angle2 = Math.min(angleSlider.getMaximum(), currentAngle + 4);
                Params params2 = Params.of(selectedBatteryId, angle2, defenseType);
                System.out.println("Triple Fire - " + defenseType + " 2: System " + selectedBatteryId + " | Angle: " + angle2);
                mainRouter.route("/team/launchDefense", params2);
                
                // Fire at current angle - 4 degrees
                int angle3 = Math.max(angleSlider.getMinimum(), currentAngle - 4);
                Params params3 = Params.of(selectedBatteryId, angle3, defenseType);
                System.out.println("Triple Fire - " + defenseType + " 3: System " + selectedBatteryId + " | Angle: " + angle3);
                mainRouter.route("/team/launchDefense", params3);
            }
        });

        // Toggle pause/resume with Space
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed SPACE"), "togglePause");
        actionMap.put("togglePause", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if ("GAME".equals(currentScreen) && pauseButton != null) pauseButton.doClick();
            }
        });

        frame.setVisible(true);

        gameCanvas.startAnimation();
    }

    // Centralized event wiring entrypoint (will be populated during refactor)
    private void setupEventHandlers() {
        // Intentionally left as a placeholder for extracting listeners
        // Existing listeners are still active in the current codebase.
    }

    public void updateScore(int score) {
        if (scoreLabel != null) {
            SwingUtilities.invokeLater(() -> scoreLabel.setText("Score: " + score));
        }
    }

    public void updateLevel(int level) {
        this.currentLevel = level;
        if (statusLabel != null) {
            SwingUtilities.invokeLater(this::updateStatusLabel);
        }
    }

    public void showStatus(String status) {
        this.currentStatusText = status;
        if (statusLabel != null) {
            SwingUtilities.invokeLater(this::updateStatusLabel);
        }
    }

    private void updateStatusLabel() {
        if (statusLabel != null) {
            statusLabel.setText("Status: " + currentStatusText + " | Level: " + currentLevel);
        }
    }

    public void showWarning(String message) {
        if (warningLabel != null) {
            SwingUtilities.invokeLater(() -> {
                warningLabel.setText(message);
                // Auto-hide warning after 3 seconds
                if (warningTimer != null) {
                    warningTimer.stop();
                }
                warningTimer = new Timer(3000, e -> warningLabel.setText(""));
                warningTimer.setRepeats(false);
                warningTimer.start();
            });
        }
    }

    public void showLevelComplete(String message) {
        if (levelCompletePanel == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (levelCompleteTitleLabel != null) {
                levelCompleteTitleLabel.setText(message);
            }
            showLevelCompleteScreen();
        });
    }

    private JPanel createGameOverPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (settingsBackgroundImage != null) {
                    g.drawImage(settingsBackgroundImage, 0, 0, getWidth(), getHeight(), null);
                }
            }
        };
        panel.setOpaque(false);

        JLabel title = new JLabel("You lost the battle!");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(Color.WHITE);

        JLabel message = new JLabel("Your score reached 0. Try again or exit.");
        message.setForeground(new Color(220, 230, 255));

        JButton playAgainButton = new JButton("Play Again");
        playAgainButton.addActionListener(e -> {
            if (mainRouter != null) {
                mainRouter.route("/team/reset", Params.of());
            }
            // Game loop will resume automatically in showGameScreen()
            showGameScreen();
        });

        JButton exitButton = new JButton("Exit");
        exitButton.addActionListener(e -> System.exit(0));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(playAgainButton);
        buttons.add(exitButton);

        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 12, 0);
        panel.add(title, c);
        c.gridy = 1;
        panel.add(message, c);
        c.gridy = 2;
        panel.add(buttons, c);
        return panel;
    }

    private JPanel createLevelCompletePanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(10, 15, 30));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(true);

        JLabel title = new JLabel("You Win!");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 42f));
        title.setForeground(Color.WHITE);
        levelCompleteTitleLabel = title;

        JLabel message = new JLabel("Congratulations! You completed the level.");
        message.setFont(message.getFont().deriveFont(Font.PLAIN, 20f));
        message.setForeground(new Color(220, 230, 255));

        JButton nextLevelButton = createStyledButton("To the next level", 18);
        nextLevelButton.addActionListener(e -> {
            if (mainRouter != null) {
                mainRouter.route("/team/nextLevel", Params.of());
            }
            showGameScreen();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(nextLevelButton);

        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 24, 0);
        panel.add(title, c);
        c.gridy = 1;
        panel.add(message, c);
        c.gridy = 2;
        panel.add(buttonPanel, c);
        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (settingsBackgroundImage != null) {
                    g.drawImage(settingsBackgroundImage, 0, 0, getWidth(), getHeight(), null);
                }
            }
        };
        panel.setOpaque(false);

        JLabel title = new JLabel("Game Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(Color.WHITE);

        JLabel diffLabel = new JLabel("Difficulty Level:");
        diffLabel.setFont(diffLabel.getFont().deriveFont(Font.BOLD, 14f));
        diffLabel.setForeground(Color.WHITE);

        javax.swing.JSpinner difficultySpinner = new javax.swing.JSpinner(
            new javax.swing.SpinnerNumberModel(1, 1, 10, 1)
        );
        this.difficultySpinner = difficultySpinner;
        difficultySpinner.setFont(difficultySpinner.getFont().deriveFont(14f));

        JButton applyButton = createStyledButton("Apply", 16);
        applyButton.addActionListener(e -> {
            int level = (Integer) difficultySpinner.getValue();
            if (mainRouter != null) {
                mainRouter.route("/team/updateSettings", Params.of(level));
            }
            returnFromSettings();
        });

        JButton backButton = createStyledButton("Back", 16);
        backButton.addActionListener(e -> returnFromSettings());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(applyButton);
        buttonPanel.add(backButton);

        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 20, 0);
        panel.add(title, c);

        c.gridy = 1;
        c.insets = new java.awt.Insets(0, 0, 10, 0);
        panel.add(diffLabel, c);

        c.gridy = 2;
        c.insets = new java.awt.Insets(0, 0, 20, 0);
        panel.add(difficultySpinner, c);

        c.gridy = 3;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        panel.add(buttonPanel, c);

        return panel;
    }

    private JPanel createIntroPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (settingsBackgroundImage != null) {
                    g.drawImage(settingsBackgroundImage, 0, 0, getWidth(), getHeight(), null);
                }
            }
        };
        panel.setOpaque(false);

        JLabel title = new JLabel("IronDoom");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 40f));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Protect your cities and survive the waves");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 18f));
        subtitle.setForeground(new Color(220, 220, 220));

        JButton playButton = createStyledButton("Play", 20);
        playButton.addActionListener(e -> showGameScreenFromIntro());

        JButton settingsButton = createStyledButton("Settings", 20);
        settingsButton.addActionListener(e -> showSettingsScreen("INTRO"));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(playButton);
        buttonPanel.add(settingsButton);

        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 16, 0);
        panel.add(title, c);

        c.gridy = 1;
        panel.add(subtitle, c);

        c.gridy = 2;
        c.insets = new java.awt.Insets(30, 0, 0, 0);
        panel.add(buttonPanel, c);

        return panel;
    }

    private java.awt.Image loadSettingsBackgroundImage() {
        java.net.URL resource = Ui.class.getResource("/ai/ui/Images/open_pic.png");
        if (resource != null) {
            return new javax.swing.ImageIcon(resource).getImage();
        }
        return new javax.swing.ImageIcon("src/ai/ui/Images/open_pic.png").getImage();
    }

    private JButton createStyledButton(String text, int fontSize) {
        JButton button = new JButton(text);
        button.setFont(button.getFont().deriveFont(Font.BOLD, (float) fontSize));
        button.setPreferredSize(new java.awt.Dimension(170, 52));
        button.setBackground(new Color(30, 120, 190));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(javax.swing.BorderFactory.createLineBorder(new Color(255, 255, 255), 2));
        button.setOpaque(true);
        return button;
    }

    private void showGameOverScreen() {
        if (cardLayout != null && rootPanel != null) {
            currentScreen = UIConstants.CARD_GAME_OVER;
            settingsScreenActive = false;
            paused = true;
            if (pauseButton != null) pauseButton.setText("Resume");
            if (gameCanvas != null) {
                gameCanvas.pauseAnimation();
            }
            if (aimTimer != null) {
                aimTimer.stop();
            }
            showStatus("Game Over");
            cardLayout.show(rootPanel, "GAME_OVER");
        }
    }

    private void showLevelCompleteScreen() {
        if (cardLayout != null && rootPanel != null) {
            currentScreen = "LEVEL_COMPLETE";
            settingsScreenActive = false;
            if (gameCanvas != null) {
                gameCanvas.pauseAnimation();
            }
            if (aimTimer != null) {
                aimTimer.stop();
            }
            paused = true;
            if (pauseButton != null) pauseButton.setText("Resume");
            showStatus("Level Complete");
            cardLayout.show(rootPanel, "LEVEL_COMPLETE");
        }
    }

    private void showGameScreen() {
        if (cardLayout != null && rootPanel != null) {
            currentScreen = "GAME";
            settingsScreenActive = false;
            if (mainRouter != null) {
                mainRouter.route("/team/resume", Params.of());
            }
            if (gameCanvas != null) {
                gameCanvas.resumeAnimation();
            }
            if (aimTimer != null) {
                aimTimer.start();
            }
            paused = false;
            if (pauseButton != null) pauseButton.setText("Pause");
            showStatus("Running");
            cardLayout.show(rootPanel, "GAME");
        }
    }

    private void showSettingsScreen(String fromScreen) {
        if (cardLayout != null && rootPanel != null) {
            settingsScreenActive = true;
            lastScreenBeforeSettings = fromScreen;
            // Update spinner to show current level from backend
            if (difficultySpinner != null && mainRouter != null) {
                difficultySpinner.setValue(currentLevel);
            }
            if (mainRouter != null) {
                mainRouter.route("/team/pause", Params.of());
            }
            if (gameCanvas != null) {
                gameCanvas.pauseAnimation();
            }
            if (aimTimer != null) {
                aimTimer.stop();
            }
            paused = true;
            if (pauseButton != null) pauseButton.setText("Resume");
            showStatus("Paused");
            cardLayout.show(rootPanel, "SETTINGS");
        }
    }

    private void returnFromSettings() {
        if ("GAME".equals(lastScreenBeforeSettings)) {
            showGameScreen();
        } else {
            showIntroScreen();
        }
    }

    private void showGameScreenFromIntro() {
        if (cardLayout != null && rootPanel != null) {
            currentScreen = "GAME";
            settingsScreenActive = false;
            if (mainRouter != null) {
                mainRouter.route("/team/reset", Params.of());
                mainRouter.route("/team/resume", Params.of());
            }
            if (gameCanvas != null) {
                gameCanvas.resumeAnimation();
            }
            if (aimTimer != null) {
                aimTimer.start();
            }
            paused = false;
            if (pauseButton != null) pauseButton.setText("Pause");
            showStatus("Running");
            cardLayout.show(rootPanel, "GAME");
        }
    }

    private void showIntroScreen() {
        if (cardLayout != null && rootPanel != null) {
            currentScreen = "INTRO";
            settingsScreenActive = false;
            if (mainRouter != null) {
                mainRouter.route("/team/pause", Params.of());
            }
            if (gameCanvas != null) {
                gameCanvas.pauseAnimation();
            }
            if (aimTimer != null) {
                aimTimer.stop();
            }
            paused = true;
            if (pauseButton != null) pauseButton.setText("Resume");
            showStatus("Paused");
            cardLayout.show(rootPanel, "INTRO");
        }
    }

    public void displayScene(List<AbstractThreat> threats, List<Damageable> damageables,List<DefenseEntity> interceptors, int score, boolean running) {
        this.threats.clear();
        this.threats.addAll(threats);
        this.damageables.clear();
        this.damageables.addAll(damageables);
        
        this.interceptors.clear();
        this.interceptors.addAll(interceptors);
        
        this.running = running;
        updateScore(score);
        showStatus(running ? "Running" : "Game Over");
        if (!running || score <= 0) {
            showGameOverScreen();
        } else if (!settingsScreenActive && !"INTRO".equals(currentScreen) && !UIConstants.CARD_LEVEL_COMPLETE.equals(currentScreen)) {
            showGameScreen();
        }
        refresh();
        updateBatteryComboBoxItems(damageables);
        updateBatteryInfoDisplay();
    }

    public void triggerExplosionEffect(int x, int y) {
        if (gameCanvas != null) {
            gameCanvas.addExplosion(x, y);
        }
    }

    public void refresh() {
        if (gameCanvas != null) {
            gameCanvas.repaint();
        }
    }

     public void setScene(List<AbstractThreat> threats, List<Damageable> damageables,List<DefenseEntity> interceptors, int score, boolean running) {
        // Ignore incoming game state if we are currently displaying the victory screen
        if ("LEVEL_COMPLETE".equals(this.currentScreen)) {
        return; 
        }
        this.threats.clear();
        this.threats.addAll(threats);
        this.damageables.clear();
        this.damageables.addAll(damageables);
        
        this.interceptors.clear();
        this.interceptors.addAll(interceptors);
        
        this.running = running;
        updateScore(score);
        showStatus(running ? "Running" : "Game Over");
        if (!running || score <= 0) {
            showGameOverScreen();
        } else if (!settingsScreenActive && !"INTRO".equals(currentScreen)) {
            showGameScreen();
        }
        refresh();
        updateBatteryComboBoxItems(damageables);
        updateBatteryInfoDisplay();
    }
    
    private static class Explosion {
        final int x;
        final int y;
        final long createdAt;

        Explosion(int x, int y) {
            this.x = x;
            this.y = y;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 600;
        }
    }

    private class GameCanvas extends JPanel implements ActionListener {
        private final List<Explosion> explosions = new ArrayList<>();
        private final Timer repaintTimer = new Timer(33, this);
        
        private final int[] starXs = new int[70];
        private final int[] starYs = new int[70];

        private static final double WORLD_WIDTH = 1200.0;
        private static final double WORLD_HEIGHT = 800.0;
        private double scale = 1.0;
        private int offsetX = 0;
        private int offsetY = 0;

        private int toScreenX(double worldX) {
            return offsetX + (int) Math.round(worldX * scale);
        }

        private int toScreenY(double worldY) {
            return offsetY + (int) Math.round(worldY * scale);
        }

        private int toScreenLen(double worldLength) {
            return Math.max(1, (int) Math.round(worldLength * scale));
        }

        private int toScreenDelta(double worldDelta) {
            return (int) Math.round(worldDelta * scale);
        }

        // מנגנון זכרון פנימי לחישוב זווית הווקטור של הטילים (לפי שינוי מיקום מפריים קודם)
        private final Map<Integer, Point> prevThreatPositions = new HashMap<>();
        private final Map<Integer, Double> threatAngles = new HashMap<>();
        private final Map<Integer, Point> prevInterceptorPositions = new HashMap<>();
        private final Map<Integer, Double> interceptorAngles = new HashMap<>();

        GameCanvas() {
            setBackground(new Color(10, 15, 30));
            for (int i = 0; i < starXs.length; i++) {
                starXs[i] = (int) (Math.random() * 2000);
                starYs[i] = (int) (Math.random() * 2000);
            }
        }

        void startAnimation() {
            repaintTimer.start();
        }

        void pauseAnimation() {
            repaintTimer.stop();
        }

        void resumeAnimation() {
            repaintTimer.start();
        }

        void addExplosion(int x, int y) {
            explosions.add(new Explosion(x, y));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            explosions.removeIf(Explosion::isExpired);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            if (threats.isEmpty() && damageables.isEmpty()) {
                drawMessage(g2d, "Waiting for scene data...");
                g2d.dispose();
                return;
            }
            
            scale = Math.min(getWidth() / WORLD_WIDTH, getHeight() / WORLD_HEIGHT);
            offsetX = (int) Math.round((getWidth() - WORLD_WIDTH * scale) / 2);
            offsetY = (int) Math.round((getHeight() - WORLD_HEIGHT * scale) / 2);

            boolean animationTick = (System.currentTimeMillis() / 100) % 2 == 0;

            // 1. קביעת גובה הקרקע המדויק לפי בסיסי הנכסים
            int groundY = gameState.getGroundY();
            if (!damageables.isEmpty()) {
                int maxAssetY = 0;
                for (Damageable d : damageables) {
                    if (d instanceof GroundAsset) {
                        maxAssetY = Math.max(maxAssetY, ((GroundAsset)d).getY() + ((GroundAsset)d).getHeight());
                    }
                }
                if (maxAssetY > 0) {
                    groundY = Math.max(gameState.getGroundY(), maxAssetY);
                }
            }

            drawBackground(g2d, groundY);
            drawGroundAssets(g2d, groundY);
            drawThreats(g2d, animationTick);
            drawInterceptors(g2d, animationTick);
            drawExplosions(g2d);
            g2d.dispose();
        }

        private void drawBackground(Graphics2D g2d, int groundY) {
            int screenGroundY = toScreenY(groundY);

            if (currentLevel >= 7) {
                // Bright Arctic/Iceberg theme
                java.awt.GradientPaint gpSky = new java.awt.GradientPaint(0, 0, new Color(180, 220, 255), 0, getHeight(), new Color(220, 240, 255));
                g2d.setPaint(gpSky);
                g2d.fillRect(0, 0, getWidth(), getHeight());

                // Bright, cool sun
                g2d.setColor(new Color(255, 255, 240, 200));
                g2d.fillOval(toScreenX(850), toScreenY(100), toScreenLen(100), toScreenLen(100));
                g2d.setColor(new Color(255, 255, 240, 100));
                g2d.fillOval(toScreenX(850) - toScreenLen(15), toScreenY(100) - toScreenLen(15), toScreenLen(130), toScreenLen(130));

                // Ground
                g2d.setColor(new Color(240, 245, 255)); // Base snow color
                g2d.fillRect(0, screenGroundY, getWidth(), Math.max(10, getHeight() - screenGroundY));
                
                // Background icebergs
                g2d.setColor(new Color(200, 220, 240, 150));
                int[] iceberg1X = { toScreenX(100), toScreenX(300), toScreenX(200) };
                int[] iceberg1Y = { screenGroundY, screenGroundY, screenGroundY - toScreenLen(150) };
                g2d.fillPolygon(iceberg1X, iceberg1Y, 3);

                g2d.setColor(new Color(210, 230, 250, 180));
                int[] iceberg2X = { toScreenX(700), toScreenX(950), toScreenX(800) };
                int[] iceberg2Y = { screenGroundY, screenGroundY, screenGroundY - toScreenLen(200) };
                g2d.fillPolygon(iceberg2X, iceberg2Y, 3);

                g2d.setColor(new Color(250, 250, 255));
                g2d.fillRect(0, screenGroundY, getWidth(), toScreenLen(8));
            } else if (currentLevel >= 4) {
                // Desert theme - more detailed
                java.awt.GradientPaint gpSky = new java.awt.GradientPaint(0, 0, new Color(135, 206, 235), 0, getHeight(), new Color(240, 240, 220));
                g2d.setPaint(gpSky);
                g2d.fillRect(0, 0, getWidth(), getHeight());

                g2d.setColor(new Color(255, 220, 100));
                g2d.fillOval(toScreenX(900), toScreenY(600), toScreenLen(80), toScreenLen(80));
                g2d.setColor(new Color(255, 255, 180, 100));
                g2d.fillOval(toScreenX(900) - toScreenLen(10), toScreenY(600) - toScreenLen(10), toScreenLen(100), toScreenLen(100));

                g2d.setColor(new Color(210, 180, 140)); 
                g2d.fillRect(0, screenGroundY, getWidth(), Math.max(10, getHeight() - screenGroundY));
                g2d.setColor(new Color(190, 160, 120));
                g2d.fillRoundRect(-50, screenGroundY - toScreenLen(10), getWidth() / 2, toScreenLen(40), toScreenLen(80), toScreenLen(80));
                g2d.fillRoundRect(getWidth() / 2 - 50, screenGroundY - toScreenLen(20), getWidth() / 2, toScreenLen(50), toScreenLen(100), toScreenLen(100));

                g2d.setColor(new Color(230, 200, 150)); 
                g2d.fillRect(0, screenGroundY, getWidth(), toScreenLen(8));
            } else {
                // Original night theme
                g2d.setColor(new Color(10, 15, 30));
                g2d.fillRect(0, 0, getWidth(), getHeight());
                g2d.setColor(new Color(255, 255, 220, 180));
                for (int i = 0; i < starXs.length; i++) {
                    int sx = starXs[i] % getWidth();
                    int sy = starYs[i] % getHeight();
                    g2d.fillRect(sx, sy, 2, 2);
                }
                g2d.setColor(new Color(50, 35, 20));
                g2d.fillRect(0, screenGroundY, getWidth(), Math.max(10, getHeight() - screenGroundY));
                g2d.setColor(new Color(30, 85, 30));
                g2d.fillRect(0, screenGroundY, getWidth(), toScreenLen(8));
            }
        }

        private void drawGroundAssets(Graphics g, int groundY) {
            Color bldgBg, bldgInner, blockBg, windowColor, battBase, battSelected, tubesColor;

            if (currentLevel >= 7) {
                bldgBg = new Color(110, 100, 90); // Rusty metal
                bldgInner = new Color(130, 120, 110); // Lighter rust
                blockBg = new Color(80, 75, 70); // Dark scrap metal
                windowColor = new Color(100, 255, 100); // Green glow
                battBase = new Color(90, 90, 85); // Dark concrete
                battSelected = new Color(100, 255, 100); // Bright green selection
                tubesColor = new Color(70, 70, 65); // Dark metal
            } else if (currentLevel >= 4) {
                bldgBg = new Color(180, 150, 110); // Sandstone
                bldgInner = new Color(200, 170, 130); // Lighter sandstone
                blockBg = new Color(190, 160, 120); // Block color
                windowColor = new Color(40, 50, 90); // Dark blue for windows
                battBase = new Color(120, 110, 90); // Metallic gray-brown
                battSelected = new Color(120, 200, 250); // Light blue selection
                tubesColor = new Color(100, 90, 80); // Darker metal
            } else {
                bldgBg = new Color(30, 45, 55);
                bldgInner = new Color(70, 95, 110);
                blockBg = new Color(45, 60, 75);
                windowColor = new Color(170, 210, 255);
                battBase = new Color(58, 86, 49);
                battSelected = new Color(70, 150, 230);
                tubesColor = new Color(100, 130, 80);
            }

            for (Damageable damageable : damageables) {
                if (damageable instanceof GroundAsset) {
                    GroundAsset city = (GroundAsset) damageable;
                    int sx = toScreenX(city.getX());
                    int sy = toScreenY(city.getY());
                    int sw = toScreenLen(city.getWidth());
                    int sh = toScreenLen(city.getHeight());

                    g.setColor(bldgBg);
                    g.fillRect(sx, sy, sw, sh);
                    g.setColor(bldgInner);
                    g.fillRect(sx + toScreenLen(4), sy + toScreenLen(4), Math.max(1, sw - toScreenLen(8)), Math.max(1, sh - toScreenLen(8)));
                    g.setColor(Color.BLACK);
                    g.drawRect(sx, sy, sw, sh);
                    g.drawRect(sx + toScreenLen(4), sy + toScreenLen(4), Math.max(1, sw - toScreenLen(8)), Math.max(1, sh - toScreenLen(8)));

                    int blockW = Math.max(toScreenLen(20), sw / 5);
                    for (int i = 0; i < sw; i += blockW) {
                        int currentBlockW = Math.min(blockW, sw - i);
                        int blockSeed = (i / blockW);
                        int citySeed = city.getHeight();

                        if (currentLevel >= 7) {
                            // Ruined/scrap building design
                            int blockH = toScreenLen(10 + (blockSeed % 4) * 8 + (citySeed % 10));
                            int blockY = sy + sh - blockH;
                            
                            // Reverting to simple blocky design to prevent crashes, but with new colors.
                            g.setColor(blockBg);
                            g.fillRect(sx + i, blockY, currentBlockW, blockH);
                            g.setColor(Color.BLACK);
                            g.drawRect(sx + i, blockY, currentBlockW, blockH);
                            for (int wx = sx + i + toScreenLen(4); wx < sx + i + currentBlockW - toScreenLen(4); wx += toScreenLen(8)) {
                                for (int wy = blockY + toScreenLen(4); wy < blockY + blockH - toScreenLen(4); wy += toScreenLen(8)) {
                                    g.setColor(windowColor);
                                    g.fillRect(wx, wy, toScreenLen(3), toScreenLen(3));
                                }
                            }
                        } else if (currentLevel >= 4) {
                            int blockH = toScreenLen(15 + (blockSeed % 2) * 8 + (citySeed % 5));
                            int blockY = sy + sh - blockH;
                            if (blockSeed % 3 == 1) {
                                g.setColor(blockBg); g.fillRoundRect(sx + i, blockY, currentBlockW, blockH, toScreenLen(10), toScreenLen(10));
                                g.setColor(Color.BLACK); g.drawRoundRect(sx + i, blockY, currentBlockW, blockH, toScreenLen(10), toScreenLen(10));
                            } else {
                                g.setColor(blockBg); g.fillRect(sx + i, blockY, currentBlockW, blockH);
                                g.setColor(Color.BLACK); g.drawRect(sx + i, blockY, currentBlockW, blockH);
                            }
                            if (blockSeed % 3 == 0) {
                                g.setColor(bldgInner); g.fillArc(sx + i, blockY - toScreenLen(8), currentBlockW, toScreenLen(16), 0, 180);
                                g.setColor(Color.BLACK); g.drawArc(sx + i, blockY - toScreenLen(8), currentBlockW, toScreenLen(16), 0, 180);
                            }
                            for (int wx = sx + i + toScreenLen(4); wx < sx + i + currentBlockW - toScreenLen(4); wx += toScreenLen(8)) {
                                for (int wy = blockY + toScreenLen(4); wy < blockY + blockH - toScreenLen(4); wy += toScreenLen(8)) {
                                    g.setColor(windowColor); g.fillRect(wx, wy, toScreenLen(3), toScreenLen(3));
                                }
                            }
                        } else {
                            int blockH = toScreenLen(18 + (blockSeed % 3) * 10 + (citySeed % 7));
                            int blockY = sy + sh - blockH;
                            g.setColor(blockBg); g.fillRect(sx + i, blockY, currentBlockW, blockH);
                            g.setColor(Color.BLACK); g.drawRect(sx + i, blockY, currentBlockW, blockH);
                            for (int wx = sx + i + toScreenLen(4); wx < sx + i + currentBlockW - toScreenLen(4); wx += toScreenLen(8)) {
                                for (int wy = blockY + toScreenLen(4); wy < blockY + blockH - toScreenLen(4); wy += toScreenLen(8)) {
                                    g.setColor(windowColor); g.fillRect(wx, wy, toScreenLen(3), toScreenLen(3));
                                }
                            }
                        }
                    }

                    g.setColor(currentLevel >= 7 ? Color.BLACK : new Color(255, 255, 220));
                    g.drawString(city.getName(), sx + toScreenLen(6), sy + toScreenLen(16));
                } else if (damageable instanceof InterceptorBattery) {
                    InterceptorBattery battery = (InterceptorBattery) damageable;
                    int bx = toScreenX(battery.getX());
                    int by = toScreenY(groundY);

                    int baseW = toScreenLen(60);
                    int baseH = toScreenLen(15);
                    int halfBaseW = toScreenLen(30);

                    boolean isSelected = battery.getId() == selectedBatteryId;
                    if (isSelected) {
                        Graphics2D gHighlight = (Graphics2D) g.create();
                        gHighlight.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int glowPadding = toScreenLen(10);
                        int ringX = bx - halfBaseW - glowPadding;
                        int ringY = by - baseH - glowPadding;
                        int ringW = baseW + glowPadding * 2;
                        int ringH = baseH + glowPadding * 2;

                        Color glowColor1, glowColor2, burstColor;
                        if (currentLevel >= 7) {
                            // Green glow for wasteland theme
                            glowColor1 = new Color(100, 255, 100, 90);
                            glowColor2 = new Color(150, 255, 150, 180);
                            burstColor = new Color(200, 255, 200, 160);
                        } else {
                            // Original cyan for other themes
                            glowColor1 = new Color(80, 230, 255, 90);
                            glowColor2 = new Color(150, 245, 255, 180);
                            burstColor = new Color(190, 245, 255, 160);
                        }

                        gHighlight.setColor(glowColor1);
                        gHighlight.fillRoundRect(ringX, ringY, ringW, ringH, toScreenLen(24), toScreenLen(24));

                        gHighlight.setStroke(new BasicStroke(toScreenLen(4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        gHighlight.setColor(glowColor2);
                        gHighlight.drawRoundRect(ringX + toScreenLen(3), ringY + toScreenLen(3), ringW - toScreenLen(6), ringH - toScreenLen(6), toScreenLen(24), toScreenLen(24));

                        int burstLength = toScreenLen(14);
                        int burstRadius = halfBaseW + toScreenLen(6);
                        gHighlight.setStroke(new BasicStroke(toScreenLen(2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        gHighlight.setColor(burstColor);
                        for (int i = 0; i < 3; i++) {
                            double angle = Math.PI / 4 + i * Math.PI / 2;
                            int x1 = bx + (int) (Math.cos(angle) * burstRadius);
                            int y1 = by - baseH + (int) (Math.sin(angle) * burstRadius * 0.8);
                            int x2 = bx + (int) (Math.cos(angle) * (burstRadius + burstLength));
                            int y2 = by - baseH + (int) (Math.sin(angle) * (burstRadius + burstLength) * 0.8);
                            gHighlight.drawLine(x1, y1, x2, y2);
                        }

                        gHighlight.dispose();
                    }

                    g.setColor(isSelected ? battSelected : battBase);
                    g.fillRect(bx - halfBaseW, by - baseH, baseW, baseH);
                    g.setColor(Color.BLACK);
                    g.drawRect(bx - halfBaseW, by - baseH, baseW, baseH);
                    
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(bx - toScreenLen(15), by - toScreenLen(20), toScreenLen(30), toScreenLen(5));
                    g.setColor(Color.BLACK);
                    g.drawRect(bx - toScreenLen(15), by - toScreenLen(20), toScreenLen(30), toScreenLen(5));

                    Graphics2D gRotated = (Graphics2D) g.create();
                    gRotated.translate(bx, by - toScreenLen(20));
                    double rotationAngle = Math.toRadians(currentSliderAngle - 90);
                    gRotated.rotate(rotationAngle);

                    if (currentLevel <= 3 || currentLevel >= 7) { // Use blocky design for 1-3 AND 7+
                        for (int i = 0; i < 4; i++) {
                            int tubeX = toScreenDelta(-25 + (i * 14));
                            gRotated.setColor(tubesColor);
                            gRotated.fillRect(tubeX, toScreenDelta(-25), toScreenLen(10), toScreenLen(25));
                            gRotated.setColor(Color.BLACK);
                            gRotated.drawRect(tubeX, toScreenDelta(-25), toScreenLen(10), toScreenLen(25));
                        }
                    } else { // Use sharp design ONLY for 4-6
                        for (int i = 0; i < 4; i++) {
                            int tubeX = toScreenDelta(-25 + (i * 14));
                            int[] launcherX = {
                                tubeX, tubeX + toScreenLen(10), tubeX + toScreenLen(8), tubeX + toScreenLen(2)
                            };
                            int[] launcherY = {
                                toScreenDelta(0), toScreenDelta(0), toScreenDelta(-28), toScreenDelta(-28)
                            };
                            gRotated.setColor(tubesColor);
                            gRotated.fillPolygon(launcherX, launcherY, 4);
                            gRotated.setColor(Color.BLACK);
                            gRotated.drawPolygon(launcherX, launcherY, 4);
                        }
                    }
                    gRotated.dispose();

                    g.setColor(currentLevel >= 7 ? Color.BLACK : Color.WHITE);
                    g.drawString("Battery", bx - toScreenLen(20), by - toScreenLen(45));
                    int missiles = battery.getMissilesAvailable();
                    g.setColor(missiles < 20 ? Color.RED : (currentLevel >= 7 ? Color.BLACK : Color.WHITE));
                    g.drawString("Ammo: " + missiles, bx - toScreenLen(20), by + toScreenLen(20));
                    if (!battery.isActive()) {
                        g.setColor(new Color(255, 0, 0, 128));
                        g.fillOval(bx - toScreenLen(15), by - toScreenLen(15), toScreenLen(30), toScreenLen(30));
                    }
                } else if (damageable instanceof LaserBattery) {
                    LaserBattery laserBatt = (LaserBattery) damageable;
                    int bx = toScreenX(laserBatt.getX());
                    int by = toScreenY(groundY);

                    int baseW = toScreenLen(60);
                    int baseH = toScreenLen(20);
                    int halfBaseW = toScreenLen(30);

                    boolean isSelected = laserBatt.getId() == selectedBatteryId;
                    
                    // Base Colors
                    Color battBaseColor = new Color(50, 60, 90);
                    Color battSelectedColor = new Color(70, 150, 230);
                    
                    if (isSelected) {
                        Graphics2D gHighlight = (Graphics2D) g.create();
                        gHighlight.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                        int glowPadding = toScreenLen(10);
                        int ringX = bx - halfBaseW - glowPadding;
                        int ringY = by - baseH - glowPadding;
                        int ringW = baseW + glowPadding * 2;
                        int ringH = baseH + glowPadding * 2;

                        gHighlight.setColor(new Color(80, 130, 255, 90));
                        gHighlight.fillRoundRect(ringX, ringY, ringW, ringH, toScreenLen(24), toScreenLen(24));

                        gHighlight.setStroke(new BasicStroke(toScreenLen(4), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        gHighlight.setColor(new Color(150, 195, 255, 180));
                        gHighlight.drawRoundRect(ringX + toScreenLen(3), ringY + toScreenLen(3), ringW - toScreenLen(6), ringH - toScreenLen(6), toScreenLen(24), toScreenLen(24));
                        gHighlight.dispose();
                    }

                    g.setColor(isSelected ? battSelectedColor : battBaseColor);
                    g.fillRoundRect(bx - halfBaseW, by - baseH, baseW, baseH, toScreenLen(10), toScreenLen(10));
                    g.setColor(Color.BLACK);
                    g.drawRoundRect(bx - halfBaseW, by - baseH, baseW, baseH, toScreenLen(10), toScreenLen(10));

                    // Dome/Lens for Laser
                    g.setColor(new Color(100, 200, 255, 150));
                    g.fillArc(bx - toScreenLen(20), by - baseH - toScreenLen(15), toScreenLen(40), toScreenLen(30), 0, 180);
                    g.setColor(Color.BLACK);
                    g.drawArc(bx - toScreenLen(20), by - baseH - toScreenLen(15), toScreenLen(40), toScreenLen(30), 0, 180);
                    
                    // Central "Core"
                    g.setColor(new Color(255, 255, 255, 200));
                    g.fillOval(bx - toScreenLen(8), by - baseH - toScreenLen(10), toScreenLen(16), toScreenLen(16));

                    // Laser Direction indicator (rotating barrel/nozzle)
                    Graphics2D gRotated = (Graphics2D) g.create();
                    gRotated.translate(bx, by - baseH - toScreenLen(5));
                    double rotationAngle = Math.toRadians(currentSliderAngle - 90);
                    gRotated.rotate(rotationAngle);
                    
                    gRotated.setColor(new Color(200, 220, 255));
                    gRotated.fillRect(-toScreenLen(4), -toScreenLen(25), toScreenLen(8), toScreenLen(25));
                    gRotated.setColor(Color.BLACK);
                    gRotated.drawRect(-toScreenLen(4), -toScreenLen(25), toScreenLen(8), toScreenLen(25));
                    
                    gRotated.setColor(Color.RED);
                    gRotated.fillRect(-toScreenLen(2), -toScreenLen(25), toScreenLen(4), toScreenLen(5));
                    
                    gRotated.dispose();

                    g.setColor(currentLevel >= 7 ? Color.BLACK : Color.WHITE);
                    g.drawString("Laser", bx - toScreenLen(15), by - toScreenLen(45));
                    // הצגת כמות התחמושת עבור סוללת לייזר
                    int charges = laserBatt.getLaserChargesAvailable();
                    g.setColor(charges < 20 ? Color.RED : (currentLevel >= 7 ? Color.BLACK : Color.WHITE));
                    // כאן התיקון: שינינו ל- plus toScreenLen(20)
                    g.drawString("Ammo: " + charges, bx - toScreenLen(15), by + toScreenLen(20));

                    if (!laserBatt.isActive()) {
                        g.setColor(new Color(255, 0, 0, 128));
                        g.fillOval(bx - toScreenLen(15), by - toScreenLen(15), toScreenLen(30), toScreenLen(30));
                    }
                }
            }
        }

        private void drawThreats(Graphics2D g, boolean isTick) {
            Color uavBodyColor, uavCockpitColor, missileBodyColor;

            if (currentLevel >= 7) { // Arctic
                uavBodyColor = new Color(110, 100, 90); // Rusty metal
                uavCockpitColor = new Color(100, 255, 100); // Green glow
                missileBodyColor = new Color(80, 75, 70); // Dark scrap metal
            } else if (currentLevel >= 4) { // Desert
                uavBodyColor = new Color(160, 140, 110); // Sandy brown
                uavCockpitColor = new Color(255, 180, 50, 200); // Orange glow
                missileBodyColor = new Color(180, 120, 90); // Dark sand
            } else { // Default
                uavBodyColor = new Color(100, 120, 140);
                uavCockpitColor = new Color(255, 100, 100, 200);
                missileBodyColor = new Color(200, 80, 80);
            }

            for (AbstractThreat threat : threats) {
                int tx = toScreenX(threat.getX());
                int ty = toScreenY(threat.getY());
                int id = threat.getId();
                
                // חישוב זווית האוריינטציה לפי וקטור ההתקדמות במרחב
                double angle = Math.PI / 2; // ברירת מחדל: טס ישר למטה
                if (prevThreatPositions.containsKey(id)) {
                    Point prev = prevThreatPositions.get(id);
                    if (prev.x != tx || prev.y != ty) {
                        angle = Math.atan2(ty - prev.y, tx - prev.x);
                        threatAngles.put(id, angle);
                    } else {
                        angle = threatAngles.getOrDefault(id, Math.PI / 2);
                    }
                } else {
                    threatAngles.put(id, angle);
                }
                prevThreatPositions.put(id, new Point(tx, ty));

                Graphics2D gRotated = (Graphics2D) g.create();
                gRotated.translate(tx, ty);
                gRotated.rotate(angle - Math.PI / 2); // מתאים את הסיבוב לציור הדיפולטיבי (שפונה למטה)

                if (currentLevel <= 3 || currentLevel >= 7) { // Use blocky design for 1-3 AND 7+
                    Color uavBody, uavCockpit, missileBody;
                    if (currentLevel >= 7) {
                        uavBody = new Color(110, 100, 90);
                        uavCockpit = new Color(100, 255, 100);
                        missileBody = new Color(80, 75, 70);
                    } else {
                        uavBody = new Color(80, 180, 220);
                        uavCockpit = new Color(30, 90, 120);
                        missileBody = Color.RED;
                    }

                    if (threat instanceof UAV) {
                        gRotated.setColor(uavBody);
                        gRotated.fillRoundRect(-toScreenLen(12), -toScreenLen(10), toScreenLen(24), toScreenLen(16), toScreenLen(6), toScreenLen(6));
                        gRotated.setColor(currentLevel >= 7 ? new Color(40, 30, 25) : Color.BLACK);
                        gRotated.drawRoundRect(-toScreenLen(12), -toScreenLen(10), toScreenLen(24), toScreenLen(16), toScreenLen(6), toScreenLen(6));
                        gRotated.setColor(uavCockpit);
                        gRotated.fillOval(-toScreenLen(6), -toScreenLen(8), toScreenLen(12), toScreenLen(10));
                    } else {
                        if (isTick) {
                            gRotated.setColor(currentLevel >= 7 ? new Color(255, 120, 0) : Color.ORANGE);
                            gRotated.fillRect(-toScreenLen(5), -toScreenLen(10) - toScreenLen(9), toScreenLen(10), toScreenLen(9));
                            gRotated.setColor(currentLevel >= 7 ? new Color(255, 220, 100) : Color.YELLOW);
                            gRotated.fillRect(-toScreenLen(2), -toScreenLen(10) - toScreenLen(12), toScreenLen(4), toScreenLen(3));
                        } else {
                            gRotated.setColor(currentLevel >= 7 ? new Color(255, 120, 0) : Color.ORANGE);
                            gRotated.fillRect(-toScreenLen(4), -toScreenLen(10) - toScreenLen(7), toScreenLen(8), toScreenLen(7));
                            gRotated.setColor(currentLevel >= 7 ? new Color(255, 220, 100) : Color.YELLOW);
                            gRotated.fillRect(-toScreenLen(2), -toScreenLen(10) - toScreenLen(10), toScreenLen(4), toScreenLen(3));
                        }
                        gRotated.setColor(missileBody);
                        gRotated.fillRect(-toScreenLen(6), -toScreenLen(10), toScreenLen(12), toScreenLen(25));
                        gRotated.setColor(currentLevel >= 7 ? new Color(40, 30, 25) : Color.BLACK);
                        gRotated.drawRect(-toScreenLen(6), -toScreenLen(10), toScreenLen(12), toScreenLen(25));
                    }
                } else { // Use sharp design ONLY for 4-6
                    if (threat instanceof UAV) {
                        int[] uavX = { 0, toScreenLen(-8), toScreenLen(-14), toScreenLen(-8), 0, toScreenLen(8), toScreenLen(14), toScreenLen(8) };
                        int[] uavY = { toScreenLen(-15), toScreenLen(-5), toScreenLen(8), toScreenLen(5), toScreenLen(10), toScreenLen(5), toScreenLen(8), toScreenLen(-5) };
                        gRotated.setColor(uavBodyColor);
                        gRotated.fillPolygon(uavX, uavY, 8);
                        gRotated.setColor(Color.BLACK);
                        gRotated.drawPolygon(uavX, uavY, 8);
                        gRotated.setColor(uavCockpitColor);
                        gRotated.fillOval(-toScreenLen(2), -toScreenLen(12), toScreenLen(4), toScreenLen(6));
                    } else {
                        int[] missileX = { 0, toScreenLen(-5), toScreenLen(-5), toScreenLen(-9), toScreenLen(-3), 0, toScreenLen(3), toScreenLen(9), toScreenLen(5), toScreenLen(5) };
                        int[] missileY = { toScreenLen(20), toScreenLen(12), toScreenLen(-6), toScreenLen(-14), toScreenLen(-12), 0, toScreenLen(-12), toScreenLen(-14), toScreenLen(-6), toScreenLen(12) };
                        gRotated.setColor(missileBodyColor);
                        gRotated.fillPolygon(missileX, missileY, 10);
                        gRotated.setColor(Color.BLACK);
                        gRotated.drawPolygon(missileX, missileY, 10);

                        int flameY = -toScreenLen(20);
                        int flameWidth = toScreenLen(10);
                        int flameHeight = isTick ? toScreenLen(12) : toScreenLen(10);
                        gRotated.setColor(Color.ORANGE);
                        gRotated.fillOval(-toScreenLen(4), flameY, flameWidth, flameHeight);
                        gRotated.setColor(Color.YELLOW);
                        gRotated.fillOval(-toScreenLen(2), flameY + toScreenLen(2), toScreenLen(6), flameHeight - toScreenLen(2));
                    }
                }

                gRotated.dispose();

                g.setColor(currentLevel >= 7 ? Color.BLACK : Color.WHITE);
                g.drawString(threat instanceof UAV ? "UAV" : "Threat", tx + toScreenLen(8), ty);
            }
        }

        private void drawInterceptors(Graphics2D g, boolean isTick) {
            Color interceptorBodyColor;
            if (currentLevel >= 7) { // Arctic
                interceptorBodyColor = new Color(90, 90, 85); // Dark concrete
            } else if (currentLevel >= 4) { // Desert
                interceptorBodyColor = new Color(200, 190, 170); // Bone white
            } else { // Default
                interceptorBodyColor = Color.LIGHT_GRAY;
            }

            for (DefenseEntity interceptor : interceptors) {
                if (interceptor instanceof LightShield) {
                    team.domain.LightShield laser = (team.domain.LightShield) interceptor;
                    if (!laser.isActive()) continue;

                    int sx = toScreenX(laser.getX());
                    int sy = toScreenY(laser.getY());
                    int ex = toScreenX(laser.getEndX());
                    int ey = toScreenY(laser.getEndY());

                    Graphics2D g2 = (Graphics2D) g;
                    
                    // יצירת אפקט אנימציה פועם שמשתנה בזמן אמת
                    long time = System.currentTimeMillis();
                    double pulse = 1.0 + 0.2 * Math.sin(time / 40.0); // קצב הפעימה של הלייזר

                    int coreWidth = Math.max(1, (int)(toScreenLen(4) * pulse));
                    int innerGlowWidth = Math.max(2, (int)(toScreenLen(12) * pulse));
                    int outerGlowWidth = Math.max(3, (int)(toScreenLen(26) * pulse));

                    // שכבה 1: הילה חיצונית (כחול כהה שקוף)
                    g2.setColor(new Color(0, 100, 255, 60));
                    g2.setStroke(new BasicStroke(outerGlowWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(sx, sy, ex, ey);

                    // שכבה 2: הילה פנימית (תכלת זוהר שקוף למחצה)
                    g2.setColor(new Color(0, 200, 255, 150));
                    g2.setStroke(new BasicStroke(innerGlowWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(sx, sy, ex, ey);

                    // שכבה 3: ליבת הלייזר (לבן-תכלת בוהק)
                    g2.setColor(new Color(220, 255, 255, 255));
                    g2.setStroke(new BasicStroke(coreWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(sx, sy, ex, ey);

                    g2.setStroke(new BasicStroke(1)); // איפוס סגנון הציור למצב הרגיל
                } else if(interceptor instanceof InterceptorMissile)
                {int ix = toScreenX(interceptor.getX());
                int iy = toScreenY(interceptor.getY());
                int id = interceptor.getId();
                
                double angle = -Math.PI / 2; // ברירת מחדל: טס ישר למעלה
                if (prevInterceptorPositions.containsKey(id)) {
                    Point prev = prevInterceptorPositions.get(id);
                    if (prev.x != ix || prev.y != iy) {
                        angle = Math.atan2(iy - prev.y, ix - prev.x);
                        interceptorAngles.put(id, angle);
                    } else {
                        angle = interceptorAngles.getOrDefault(id, -Math.PI / 2);
                    }
                } else {
                    interceptorAngles.put(id, angle);
                }
                prevInterceptorPositions.put(id, new Point(ix, iy));

                Graphics2D gRotated = (Graphics2D) g.create();
                gRotated.translate(ix, iy);
                gRotated.rotate(angle + Math.PI / 2); // מתאים את הסיבוב לציור הדיפולטיבי (שפונה למעלה)

                if (currentLevel <= 3 || currentLevel >= 7) { 
                    Color bodyColor, cockpitColor;
                    if (currentLevel >= 7) {
                        bodyColor = new Color(90, 90, 85);
                        cockpitColor = new Color(100, 255, 100);
                    } else {
                        bodyColor = Color.LIGHT_GRAY;
                        cockpitColor = Color.BLUE;
                    }

                    if (isTick) {
                        gRotated.setColor(Color.CYAN); gRotated.fillRect(-toScreenLen(4), toScreenLen(10), toScreenLen(8), toScreenLen(10));
                        gRotated.setColor(Color.WHITE); gRotated.fillRect(-toScreenLen(2), toScreenLen(20), toScreenLen(4), toScreenLen(6));
                    } else {
                        gRotated.setColor(Color.CYAN); gRotated.fillRect(-toScreenLen(3), toScreenLen(10), toScreenLen(6), toScreenLen(8));
                        gRotated.setColor(Color.WHITE); gRotated.fillRect(-toScreenLen(1), toScreenLen(18), toScreenLen(2), toScreenLen(6));
                    }
                    gRotated.setColor(bodyColor);
                    gRotated.fillRect(-toScreenLen(4), -toScreenLen(10), toScreenLen(8), toScreenLen(20));
                    gRotated.setColor(currentLevel >= 7 ? new Color(40, 30, 25) : Color.BLACK);
                    gRotated.drawRect(-toScreenLen(10), -toScreenLen(14), toScreenLen(8), toScreenLen(20));
                    gRotated.setColor(cockpitColor);
                    gRotated.fillRect(-toScreenLen(5), -toScreenLen(14), toScreenLen(6), toScreenLen(4));
                } else { // Use sharp design ONLY for 4-6
                    int[] interceptorX = { 0, toScreenLen(-4), toScreenLen(-4), toScreenLen(-8), toScreenLen(-3), 0, toScreenLen(3), toScreenLen(8), toScreenLen(4), toScreenLen(4) };
                    int[] interceptorY = { toScreenLen(-20), toScreenLen(-12), toScreenLen(6), toScreenLen(14), toScreenLen(12), toScreenLen(18), toScreenLen(12), toScreenLen(14), toScreenLen(6), toScreenLen(-12) };
                    gRotated.setColor(interceptorBodyColor);
                    gRotated.fillPolygon(interceptorX, interceptorY, 10);
                    gRotated.setColor(Color.BLACK);
                    gRotated.drawPolygon(interceptorX, interceptorY, 10);

                    int flameY = toScreenLen(20);
                    int flameWidth = toScreenLen(10);
                    int flameHeight = isTick ? toScreenLen(12) : toScreenLen(10);
                    gRotated.setColor(new Color(100, 255, 255));
                    gRotated.fillOval(-toScreenLen(4), flameY, flameWidth, flameHeight);
                    gRotated.setColor(new Color(190, 255, 255));
                    gRotated.fillOval(-toScreenLen(2), flameY + toScreenLen(2), toScreenLen(6), flameHeight - toScreenLen(2));
                }

                gRotated.dispose();
            }}
        }

        private void drawExplosions(Graphics g) {
            for (Explosion explosion : explosions) {
                long age = System.currentTimeMillis() - explosion.createdAt;
                int alpha = (int) Math.max(0, 255 - age * 255 / 600);
                
                int baseSize = toScreenLen(20);
                int size = Math.max(2, baseSize + (int) Math.round(age / 10.0 * scale));
                int cx = toScreenX(explosion.x);
                int cy = toScreenY(explosion.y);

                g.setColor(new Color(255, 100, 0, alpha));
                g.fillRect(cx - size / 2, cy - size / 2, size, size);
                
                g.setColor(new Color(255, 220, 40, alpha));
                g.fillRect(cx - size / 3, cy - size / 3, size * 2 / 3, size * 2 / 3);
                
                g.setColor(new Color(255, 255, 200, alpha));
                g.fillRect(cx - size / 6, cy - size / 6, size / 3, size / 3);
            }
        }

        private void drawMessage(Graphics g, String message) {
            g.setColor(currentLevel >= 7 ? Color.BLACK : Color.WHITE);
            g.drawString(message, 20, 20);
        }
    }

    private void updateBatteryComboBoxItems(List<Damageable> currentDamageables) {
        List<Integer> batteryIds = new ArrayList<>();
        for (Damageable d : currentDamageables) {
            if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) d).isActive()) {                // Cast to AbstractDefenseSystem to access getId()
                // This is safe because both InterceptorBattery and LaserBattery extend AbstractDefenseSystem
                batteryIds.add(((AbstractDefenseSystem) d).getId());
            }
        }
        // Sort IDs for consistent display
        batteryIds.sort(Integer::compare);

        boolean refreshNeeded = batteryComboBox.getItemCount() != batteryIds.size();
        if (!refreshNeeded) {
            for (int i = 0; i < batteryComboBox.getItemCount(); i++) {
                if (!batteryIds.contains(batteryComboBox.getItemAt(i))) {
                    refreshNeeded = true;
                    break;
                }
            }
        }

        if (refreshNeeded) {
            Object previousSelection = batteryComboBox.getSelectedItem();
            batteryComboBox.removeAllItems();
            for (int id : batteryIds) {
                batteryComboBox.addItem(id);
            }
            if (previousSelection != null && batteryIds.contains(previousSelection)) {
                batteryComboBox.setSelectedItem(previousSelection);
                this.selectedBatteryId = (Integer) previousSelection;
            } else if (!batteryIds.isEmpty()) {
                batteryComboBox.setSelectedIndex(0);
                this.selectedBatteryId = batteryIds.get(0);
            }
        } else if (selectedBatteryId == -1 && !batteryIds.isEmpty()) {
            batteryComboBox.setSelectedIndex(0);
            this.selectedBatteryId = batteryIds.get(0);
        }
    }

    private void updateBatteryInfoDisplay() {
        // Default message if no defense system is selected or available
        if (selectedBatteryId == -1) {
            batteryInfoLabel.setText("Status: No Defense System Selected | Ammo: 0");
            batteryInfoLabel.setForeground(Color.BLACK);
            return;
        }

        AbstractDefenseSystem selectedDefenseSystem = null;
        for (Damageable d : damageables) {
            if (d instanceof AbstractDefenseSystem) { // Check if it's a defense system
                AbstractDefenseSystem ds = (AbstractDefenseSystem) d;
                if (ds.getId() == selectedBatteryId) {
                    selectedDefenseSystem = ds;
                    break;
                }
            }
        }

        // If the previously selected system is no longer available, try to select the first available one
        if (selectedDefenseSystem == null) {
            for (Damageable d : damageables) {
                if (d instanceof AbstractDefenseSystem) {
                    selectedDefenseSystem = (AbstractDefenseSystem) d;
                    selectedBatteryId = selectedDefenseSystem.getId();
                    break;
                }
            }
        }

        if (selectedDefenseSystem != null) {
            String status = selectedDefenseSystem.isActive() ? "ACTIVE" : "DAMAGED";
            String ammoInfo;
            if (selectedDefenseSystem instanceof InterceptorBattery) {
                ammoInfo = String.format("Interceptors: %d", ((InterceptorBattery) selectedDefenseSystem).getMissilesAvailable());
            } else if (selectedDefenseSystem instanceof LaserBattery) {
                ammoInfo = String.format("Laser Charges: %d", ((LaserBattery) selectedDefenseSystem).getLaserChargesAvailable());
            } else {
                ammoInfo = "Ammo: N/A"; // Fallback for other defense types
            }
            batteryInfoLabel.setText(String.format("System %d SELECTED | %s | %s", selectedBatteryId, status, ammoInfo));
            
            if (!selectedDefenseSystem.isActive()) {
                batteryInfoLabel.setForeground(new Color(200, 40, 40));
            } else {
                batteryInfoLabel.setForeground(new Color(20, 90, 180));
            }
        }
    }

    private void selectBatteryIndex(int delta) {
        if (batteryComboBox == null || batteryComboBox.getItemCount() == 0) {
            return;
        }
        int currentIndex = batteryComboBox.getSelectedIndex();
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = (currentIndex + delta + batteryComboBox.getItemCount()) % batteryComboBox.getItemCount();
        batteryComboBox.setSelectedIndex(nextIndex);
        Integer selected = (Integer) batteryComboBox.getSelectedItem();
        if (selected != null && selectedBatteryId != selected) { // Only update if selection actually changed
            selectedBatteryId = selected;
            updateBatteryInfoDisplay();
        }
    }
}
