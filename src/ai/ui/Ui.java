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
import team.domain.GameState;
import team.domain.GroundAsset;
import team.domain.InterceptorBattery;
import team.domain.InterceptorMissile;

public class Ui {
    private MainRouter mainRouter;
    private TeamUiPortImpl uiInstance;
    private JFrame frame;
    private CardLayout cardLayout;
    private JPanel rootPanel;
    private GameCanvas gameCanvas;
    private JPanel gameOverPanel;
    private JPanel settingsPanel;
    private JLabel scoreLabel;
    private JLabel statusLabel;
    private int selectedBatteryId = -1;
    private javax.swing.JComboBox<Integer> batteryComboBox;
    private javax.swing.JLabel batteryInfoLabel;
    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private final List<InterceptorMissile> interceptors = new ArrayList<>();
    private final GameState gameState = new GameState(100, 1, true);

    private boolean running = true;
    private boolean settingsScreenActive = false;
    private javax.swing.Timer aimTimer;
    private int aimDirection = 0; // -1 = left, +1 = right, 0 = none
    private static final int AIM_INTERVAL_MS = 30;
    private static final int AIM_DELTA = 2; // degrees per tick
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
        statusLabel = new JLabel("Status: Running");
        statusLabel.setFont(statusLabel.getFont().deriveFont(14f));

        gameCanvas = new GameCanvas();
        frame.setLayout(new BorderLayout());

        // === Angle Slider Setup (0-90 Degrees) ===
        // 0 = משוחרר בכיוון שמאלה (קרקעית), 90 = למעלה (אנכי)
        javax.swing.JSlider angleSlider = new javax.swing.JSlider(0, 90, 45);
        angleSlider.setMajorTickSpacing(15);
        angleSlider.setPaintTicks(true);
        angleSlider.setPaintLabels(true);
        angleSlider.addChangeListener(e -> {
            this.currentSliderAngle = angleSlider.getValue();
            refresh(); 
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
        homeButton.setEnabled(false);
        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e -> showSettingsScreen());
        JButton restartButton = new JButton("Restart");
        restartButton.addActionListener(e -> {
            if (mainRouter != null) {
                mainRouter.route("/team/reset", Params.of());
            }
        });

        JPanel topControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        topControls.add(homeButton);
        topControls.add(settingsButton);
        topControls.add(restartButton);
        topControls.add(scoreLabel);
        topControls.add(statusLabel);

        JPanel bottomControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        bottomControls.add(new JLabel("Select Battery:"));
        bottomControls.add(batteryComboBox);
        bottomControls.add(batteryInfoLabel);
        bottomControls.add(new JLabel("Angle (0-90):"));
        bottomControls.add(angleSlider);
        bottomControls.add(fireButton);

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new javax.swing.BoxLayout(controlPanel, javax.swing.BoxLayout.Y_AXIS));
        controlPanel.add(topControls);
        controlPanel.add(bottomControls);

        JPanel gameScreen = new JPanel(new BorderLayout());
        gameScreen.add(controlPanel, BorderLayout.NORTH);
        gameScreen.add(gameCanvas, BorderLayout.CENTER);

        gameOverPanel = createGameOverPanel();
        settingsPanel = createSettingsPanel();
        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);
        rootPanel.add(gameScreen, "GAME");
        rootPanel.add(gameOverPanel, "GAME_OVER");
        rootPanel.add(settingsPanel, "SETTINGS");

        frame.setContentPane(rootPanel);
        cardLayout.show(rootPanel, "GAME");
        frame.pack();
        frame.setSize(width, height);
        frame.setLocationRelativeTo(null);

        fireButton.addActionListener(e -> {
            if (selectedBatteryId == -1) {
                System.out.println("Cannot fire: No battery selected.");
                return;
            }
            int angle = angleSlider.getValue();
            int power = 120; // Increased default power for faster interceptors
            
            Params params = Params.of(selectedBatteryId, angle, power);
            System.out.println("Launching from Battery " + selectedBatteryId + " | Angle: " + angle + " | Power: " + power);
            mainRouter.route("/team/launch", params);
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

        // Fire with Z
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("pressed Z"), "triggerFire");
        actionMap.put("triggerFire", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireButton.doClick();
            }
        });

        frame.setVisible(true);

        gameCanvas.startAnimation();
    }

    public void updateScore(int score) {
        if (scoreLabel != null) {
            SwingUtilities.invokeLater(() -> scoreLabel.setText("Score: " + score));
        }
    }

    public void showStatus(String status) {
        if (statusLabel != null) {
            SwingUtilities.invokeLater(() -> statusLabel.setText("Status: " + status));
        }
    }

    private JPanel createGameOverPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setBackground(new Color(8, 12, 24));

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

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setBackground(new Color(8, 12, 24));

        JLabel title = new JLabel("Game Settings");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setForeground(Color.WHITE);

        JLabel diffLabel = new JLabel("Difficulty Level:");
        diffLabel.setFont(diffLabel.getFont().deriveFont(Font.BOLD, 14f));
        diffLabel.setForeground(Color.WHITE);

        javax.swing.JSpinner difficultySpinner = new javax.swing.JSpinner(
            new javax.swing.SpinnerNumberModel(1, 1, 10, 1)
        );
        difficultySpinner.setFont(difficultySpinner.getFont().deriveFont(14f));

        JButton applyButton = new JButton("Apply");
        applyButton.setFont(applyButton.getFont().deriveFont(Font.BOLD, 14f));
        applyButton.addActionListener(e -> {
            int level = (Integer) difficultySpinner.getValue();
            if (mainRouter != null) {
                mainRouter.route("/team/updateSettings", Params.of(level));
            }
            // Return to game after applying settings
            showGameScreenFromSettings();
        });

        JButton backButton = new JButton("Back to Game");
        backButton.setFont(backButton.getFont().deriveFont(Font.BOLD, 14f));
        backButton.addActionListener(e -> showGameScreenFromSettings());

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

    private void showGameOverScreen() {
        if (cardLayout != null && rootPanel != null) {
            if (gameCanvas != null) {
                gameCanvas.pauseAnimation();
            }
            if (aimTimer != null) {
                aimTimer.stop();
            }
            cardLayout.show(rootPanel, "GAME_OVER");
        }
    }

    private void showGameScreen() {
        if (cardLayout != null && rootPanel != null) {
            if (gameCanvas != null) {
                gameCanvas.resumeAnimation();
            }
            if (aimTimer != null) {
                aimTimer.start();
            }
            cardLayout.show(rootPanel, "GAME");
        }
    }

    private void showSettingsScreen() {
        if (cardLayout != null && rootPanel != null) {
            settingsScreenActive = true;
            if (mainRouter != null) {
                mainRouter.route("/team/pause", Params.of());
            }
            if (gameCanvas != null) {
                gameCanvas.pauseAnimation();
            }
            if (aimTimer != null) {
                aimTimer.stop();
            }
            cardLayout.show(rootPanel, "SETTINGS");
        }
    }

    private void showGameScreenFromSettings() {
        if (cardLayout != null && rootPanel != null) {
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
            cardLayout.show(rootPanel, "GAME");
        }
    }

    public void setScene(List<AbstractThreat> threats, List<Damageable> damageables,List<InterceptorMissile> interceptors, int score, boolean running) {
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
        } else if (!settingsScreenActive) {
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
            g2d.setColor(new Color(10, 15, 30));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            
            g2d.setColor(new Color(255, 255, 220, 180));
            for (int i = 0; i < starXs.length; i++) {
                int sx = starXs[i] % getWidth();
                int sy = starYs[i] % getHeight();
                g2d.fillRect(sx, sy, 2, 2);
            }
            
            int screenGroundY = toScreenY(groundY);
            g2d.setColor(new Color(50, 35, 20));
            g2d.fillRect(0, screenGroundY, getWidth(), Math.max(10, getHeight() - screenGroundY));
            
            g2d.setColor(new Color(30, 85, 30));
            g2d.fillRect(0, screenGroundY, getWidth(), toScreenLen(8));
        }

        private void drawGroundAssets(Graphics g, int groundY) {
            for (Damageable damageable : damageables) {
                if (damageable instanceof GroundAsset) {
                    GroundAsset city = (GroundAsset) damageable;
                    int sx = toScreenX(city.getX());
                    int sy = toScreenY(city.getY());
                    int sw = toScreenLen(city.getWidth());
                    int sh = toScreenLen(city.getHeight());

                    g.setColor(new Color(30, 45, 55));
                    g.fillRect(sx, sy, sw, sh);
                    g.setColor(new Color(70, 95, 110));
                    g.fillRect(sx + toScreenLen(4), sy + toScreenLen(4), Math.max(1, sw - toScreenLen(8)), Math.max(1, sh - toScreenLen(8)));
                    g.setColor(Color.BLACK);
                    g.drawRect(sx, sy, sw, sh);
                    g.drawRect(sx + toScreenLen(4), sy + toScreenLen(4), Math.max(1, sw - toScreenLen(8)), Math.max(1, sh - toScreenLen(8)));

                    int blockW = Math.max(toScreenLen(18), sw / 6);
                    for (int i = 0; i < sw; i += blockW) {
                        int blockH = toScreenLen(18 + ((i / blockW) % 3) * 10 + (city.getHeight() % 7));
                        int blockY = sy + sh - blockH;
                        int currentBlockW = Math.min(blockW, sw - i);

                        g.setColor(new Color(45, 60, 75));
                        g.fillRect(sx + i, blockY, currentBlockW, blockH);
                        g.setColor(Color.BLACK);
                        g.drawRect(sx + i, blockY, currentBlockW, blockH);

                        g.setColor(new Color(170, 210, 255));
                        for (int wx = sx + i + toScreenLen(4); wx < sx + i + currentBlockW - toScreenLen(4); wx += toScreenLen(8)) {
                            for (int wy = blockY + toScreenLen(4); wy < blockY + blockH - toScreenLen(4); wy += toScreenLen(8)) {
                                g.fillRect(wx, wy, Math.max(1, toScreenLen(3)), Math.max(1, toScreenLen(3)));
                            }
                        }
                    }

                    g.setColor(new Color(255, 255, 220));
                    g.drawString(city.getName(), sx + toScreenLen(6), sy + toScreenLen(16));
                } else if (damageable instanceof InterceptorBattery) {
                    InterceptorBattery battery = (InterceptorBattery) damageable;
                    int bx = toScreenX(battery.getX());
                    int by = toScreenY(groundY);

                    int baseW = toScreenLen(60);
                    int baseH = toScreenLen(15);
                    int halfBaseW = toScreenLen(30);

                    g.setColor(new Color(58, 86, 49));
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

                    gRotated.setColor(new Color(100, 130, 80));
                    for (int i = 0; i < 4; i++) {
                        int tubeX = toScreenDelta(-25 + (i * 14));
                        gRotated.fillRect(tubeX, toScreenDelta(-25), toScreenLen(10), toScreenLen(25));
                        gRotated.setColor(Color.BLACK);
                        gRotated.drawRect(tubeX, toScreenDelta(-25), toScreenLen(10), toScreenLen(25));
                        
                        gRotated.setColor(Color.DARK_GRAY);
                        gRotated.fillRect(tubeX + toScreenLen(2), toScreenDelta(-30), toScreenLen(6), toScreenLen(5));
                    }
                    gRotated.dispose();

                    g.setColor(Color.WHITE);
                    g.drawString("Battery", bx - toScreenLen(20), by - toScreenLen(45));

                    if (!battery.isActive()) {
                        g.setColor(new Color(255, 0, 0, 128));
                        g.fillOval(bx - toScreenLen(15), by - toScreenLen(15), toScreenLen(30), toScreenLen(30));
                    }
                }
            }
        }

        private void drawThreats(Graphics2D g, boolean isTick) {
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

                if (isTick) {
                    gRotated.setColor(Color.ORANGE);
                    gRotated.fillRect(-toScreenLen(5), -toScreenLen(24), toScreenLen(10), toScreenLen(9));
                    gRotated.setColor(Color.YELLOW);
                    gRotated.fillRect(-toScreenLen(2), -toScreenLen(27), toScreenLen(4), toScreenLen(3));
                } else {
                    gRotated.setColor(Color.ORANGE);
                    gRotated.fillRect(-toScreenLen(4), -toScreenLen(22), toScreenLen(8), toScreenLen(7));
                    gRotated.setColor(Color.YELLOW);
                    gRotated.fillRect(-toScreenLen(2), -toScreenLen(25), toScreenLen(4), toScreenLen(3));
                }
                
                gRotated.setColor(Color.RED);
                gRotated.fillRect(-toScreenLen(6), -toScreenLen(15), toScreenLen(12), toScreenLen(25));
                gRotated.setColor(Color.BLACK);
                gRotated.drawRect(-toScreenLen(6), -toScreenLen(15), toScreenLen(12), toScreenLen(25));
                
                gRotated.setColor(Color.BLACK);
                gRotated.fillRect(-toScreenLen(4), toScreenLen(10), toScreenLen(8), toScreenLen(6));
                gRotated.fillRect(-toScreenLen(2), toScreenLen(16), toScreenLen(4), toScreenLen(4));

                gRotated.dispose();

                g.setColor(Color.WHITE);
                g.drawString("Threat", tx + toScreenLen(8), ty);
            }
        }

        private void drawInterceptors(Graphics2D g, boolean isTick) {
            for (InterceptorMissile interceptor : interceptors) {
                int ix = toScreenX(interceptor.getX());
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

                if (isTick) {
                    gRotated.setColor(Color.CYAN);
                    gRotated.fillRect(-toScreenLen(4), toScreenLen(10), toScreenLen(8), toScreenLen(10));
                    gRotated.setColor(Color.WHITE);
                    gRotated.fillRect(-toScreenLen(2), toScreenLen(20), toScreenLen(4), toScreenLen(6));
                } else {
                    gRotated.setColor(Color.CYAN);
                    gRotated.fillRect(-toScreenLen(3), toScreenLen(10), toScreenLen(6), toScreenLen(8));
                    gRotated.setColor(Color.WHITE);
                    gRotated.fillRect(-toScreenLen(1), toScreenLen(18), toScreenLen(2), toScreenLen(6));
                }
                
                gRotated.setColor(Color.LIGHT_GRAY);
                gRotated.fillRect(-toScreenLen(4), -toScreenLen(10), toScreenLen(8), toScreenLen(20));
                gRotated.setColor(Color.BLACK);
                gRotated.drawRect(-toScreenLen(4), -toScreenLen(10), toScreenLen(8), toScreenLen(20));
                
                gRotated.setColor(Color.BLUE);
                gRotated.fillRect(-toScreenLen(3), -toScreenLen(14), toScreenLen(6), toScreenLen(4));
                gRotated.fillRect(-toScreenLen(1), -toScreenLen(18), toScreenLen(2), toScreenLen(4));

                gRotated.dispose();
            }
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
            g.setColor(Color.WHITE);
            g.drawString(message, 20, 20);
        }
    }

    private void updateBatteryComboBoxItems(List<Damageable> currentDamageables) {
        List<Integer> batteryIds = new ArrayList<>();
        for (Damageable d : currentDamageables) {
            if (d instanceof InterceptorBattery) {
                batteryIds.add(((InterceptorBattery) d).getId());
            }
        }

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
        if (selectedBatteryId == -1) {
            batteryInfoLabel.setText("Status: No Battery Available | Ammo: 0");
            batteryInfoLabel.setForeground(Color.BLACK);
            return;
        }

        InterceptorBattery selectedBattery = null;
        for (Damageable d : damageables) {
            if (d instanceof InterceptorBattery) {
                InterceptorBattery b = (InterceptorBattery) d;
                if (b.getId() == selectedBatteryId) {
                    selectedBattery = b;
                    break;
                }
            }
        }

        if (selectedBattery == null) {
            for (Damageable d : damageables) {
                if (d instanceof InterceptorBattery) {
                    selectedBattery = (InterceptorBattery) d;
                    selectedBatteryId = selectedBattery.getId();
                    break;
                }
            }
        }

        if (selectedBattery != null) {
            String status = selectedBattery.isActive() ? "ACTIVE" : "DAMAGED";
            int ammo = selectedBattery.getMissilesAvailable();
            batteryInfoLabel.setText(String.format("Status: %s | Interceptors: %d", status, ammo));
            
            if (!selectedBattery.isActive()) {
                batteryInfoLabel.setForeground(Color.RED);
            } else {
                batteryInfoLabel.setForeground(Color.BLUE);
            }
        } else {
            batteryInfoLabel.setText("Status: No Battery Available | Ammo: 0");
            batteryInfoLabel.setForeground(Color.BLACK);
        }
    }
}