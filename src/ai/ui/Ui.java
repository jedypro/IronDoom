package ai.ui;

import java.awt.BorderLayout;
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
import team.domain.GroundAsset;
import team.domain.InterceptorBattery;
import team.domain.InterceptorMissile;

public class Ui {
    private MainRouter mainRouter;
    private TeamUiPortImpl uiInstance;
    private JFrame frame;
    private GameCanvas gameCanvas;
    private JLabel scoreLabel;
    private JLabel statusLabel;
    private int selectedBatteryId = -1;
    private javax.swing.JComboBox<Integer> batteryComboBox;
    private javax.swing.JLabel batteryInfoLabel;
    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private final List<InterceptorMissile> interceptors = new ArrayList<>();

    private boolean running = true;
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
        frame.setSize(1200, 800);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setLocationRelativeTo(null);

        scoreLabel = new JLabel("Score: 100");
        scoreLabel.setFont(scoreLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel = new JLabel("Status: Running");
        statusLabel.setFont(statusLabel.getFont().deriveFont(14f));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        topPanel.add(scoreLabel);
        topPanel.add(statusLabel);

        gameCanvas = new GameCanvas();
        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(gameCanvas, BorderLayout.CENTER);

        // === Angle Slider Setup (0-180 Degrees) ===
        // 0 = ימינה, 90 = למעלה, 180 = שמאלה
        javax.swing.JSlider angleSlider = new javax.swing.JSlider(0, 180, 90);
        angleSlider.setMajorTickSpacing(45);
        angleSlider.setPaintTicks(true);
        angleSlider.setPaintLabels(true);
        angleSlider.setInverted(true); // הופך את הסליידר כך ששמאל באמת יצביע שמאלה (180)
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
        batteryInfoLabel = new javax.swing.JLabel("Status: Unknown | Ammo: 0");
        batteryInfoLabel.setFont(batteryInfoLabel.getFont().deriveFont(Font.BOLD, 14f));

        // === Fire Button Setup ===
        javax.swing.JButton fireButton = new javax.swing.JButton("FIRE!");
        fireButton.setFont(fireButton.getFont().deriveFont(Font.BOLD, 16f));
        fireButton.setBackground(Color.RED);
        fireButton.setForeground(Color.WHITE);

        fireButton.addActionListener(e -> {
            if (selectedBatteryId == -1) {
                System.out.println("Cannot fire: No battery selected.");
                return;
            }
            int angle = angleSlider.getValue();
            int power = 60; // Fixed default power
            
            Params params = Params.of(selectedBatteryId, angle, power);
            System.out.println("Launching from Battery " + selectedBatteryId + " | Angle: " + angle);
            mainRouter.route("/team/launch", params);
        });

        // === Global Key Bindings (Arrows & Spacebar) ===
        javax.swing.JPanel contentPane = (javax.swing.JPanel) frame.getContentPane();
        javax.swing.InputMap inputMap = contentPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        javax.swing.ActionMap actionMap = contentPane.getActionMap();

        // שמאלה צריך להקטין את הזווית, וימינה להגדיל אותה,
        // כך שהכפתורים יהיו הפוכים מהקונבנציה הקודמת.
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("LEFT"), "aimLeft");
        actionMap.put("aimLeft", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int val = angleSlider.getValue();
                if (val > angleSlider.getMinimum()) angleSlider.setValue(val - 5);
            }
        });

        inputMap.put(javax.swing.KeyStroke.getKeyStroke("RIGHT"), "aimRight");
        actionMap.put("aimRight", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int val = angleSlider.getValue();
                if (val < angleSlider.getMaximum()) angleSlider.setValue(val + 5);
            }
        });

        inputMap.put(javax.swing.KeyStroke.getKeyStroke("SPACE"), "triggerFire");
        actionMap.put("triggerFire", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireButton.doClick(); 
            }
        });

        // === Assemble Control Panel Layout ===
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controlPanel.add(new JLabel("Select Battery:"));
        controlPanel.add(batteryComboBox);
        controlPanel.add(batteryInfoLabel);
        controlPanel.add(new JLabel("Angle (0-180):"));
        controlPanel.add(angleSlider);
        controlPanel.add(fireButton);
        
        frame.add(controlPanel, BorderLayout.NORTH);
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
            
            boolean animationTick = (System.currentTimeMillis() / 100) % 2 == 0;

            // 1. קביעת גובה הקרקע המדויק לפי בסיסי הנכסים
            int groundY = getHeight() - 50;
            if (!damageables.isEmpty()) {
                int maxAssetY = 0;
                for (Damageable d : damageables) {
                    if (d instanceof GroundAsset) {
                        maxAssetY = Math.max(maxAssetY, ((GroundAsset)d).getY() + ((GroundAsset)d).getHeight());
                    }
                }
                if (maxAssetY > 0) {
                    groundY = maxAssetY; 
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
            
            // ציור אדמה ודשא בדיוק מהנקודה הנמוכה ביותר
            g2d.setColor(new Color(50, 35, 20));
            g2d.fillRect(0, groundY, getWidth(), Math.max(10, getHeight() - groundY));
            
            g2d.setColor(new Color(30, 85, 30));
            g2d.fillRect(0, groundY, getWidth(), 8);
        }

        private void drawGroundAssets(Graphics g, int groundY) {
            for (Damageable damageable : damageables) {
                if (damageable instanceof GroundAsset) {
                    GroundAsset city = (GroundAsset) damageable;
                    g.setColor(new Color(40, 100, 40));
                    g.fillRect(city.getX(), city.getY(), city.getWidth(), city.getHeight());
                    g.setColor(Color.BLACK);
                    g.drawRect(city.getX(), city.getY(), city.getWidth(), city.getHeight());
                    g.setColor(Color.WHITE);
                    g.drawString(city.getName(), city.getX() + 2, city.getY() + 14);
                } else if (damageable instanceof InterceptorBattery) {
                    InterceptorBattery battery = (InterceptorBattery) damageable;
                    int bx = battery.getX();
                    // עיגון הסוללה כך שתשב בול על הדשא
                    int by = groundY; 

                    // בסיס המשגר
                    g.setColor(new Color(58, 86, 49));
                    g.fillRect(bx - 30, by - 15, 60, 15);
                    g.setColor(Color.BLACK);
                    g.drawRect(bx - 30, by - 15, 60, 15);
                    
                    // כיפת החיבור
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(bx - 15, by - 20, 30, 5);
                    g.setColor(Color.BLACK);
                    g.drawRect(bx - 15, by - 20, 30, 5);

                    // --- תנועת סיבוב לקנים ביחס לזווית הנכונה ---
                    Graphics2D gRotated = (Graphics2D) g.create();
                    gRotated.translate(bx, by - 20);
                    
                    // חישוב זווית ציור כך שהסיומת תזוז לפי הכיוון האמיתי של השיגור
                    // ומתחברת לאותה קונבנציה כמו הלוגיקת השיגור ב-backend
                    double rotationAngle = Math.toRadians(currentSliderAngle - 90);
                    gRotated.rotate(rotationAngle);

                    gRotated.setColor(new Color(100, 130, 80));
                    for (int i = 0; i < 4; i++) {
                        int tubeX = -25 + (i * 14);
                        gRotated.fillRect(tubeX, -25, 10, 25);
                        gRotated.setColor(Color.BLACK);
                        gRotated.drawRect(tubeX, -25, 10, 25);
                        
                        gRotated.setColor(Color.DARK_GRAY);
                        gRotated.fillRect(tubeX + 2, -30, 6, 5);
                    }
                    gRotated.dispose();
                    // ---------------------------------------------

                    g.setColor(Color.WHITE);
                    g.drawString("Battery", bx - 20, by - 45);

                    if (!battery.isActive()) {
                        g.setColor(new Color(255, 0, 0, 128));
                        g.fillOval(bx - 15, by - 15, 30, 30);
                    }
                }
            }
        }

        private void drawThreats(Graphics2D g, boolean isTick) {
            for (AbstractThreat threat : threats) {
                int tx = threat.getX();
                int ty = threat.getY();
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

                // ציור האיום ביחס לציר (0,0) כשהוא מסובב לכיוון התנועה
                if (isTick) {
                    gRotated.setColor(Color.ORANGE);
                    gRotated.fillRect(-5, -24, 10, 9);
                    gRotated.setColor(Color.YELLOW);
                    gRotated.fillRect(-2, -27, 4, 3);
                } else {
                    gRotated.setColor(Color.ORANGE);
                    gRotated.fillRect(-4, -22, 8, 7);
                    gRotated.setColor(Color.YELLOW);
                    gRotated.fillRect(-2, -25, 4, 3);
                }
                
                gRotated.setColor(Color.RED);
                gRotated.fillRect(-6, -15, 12, 25);
                gRotated.setColor(Color.BLACK);
                gRotated.drawRect(-6, -15, 12, 25);
                
                gRotated.setColor(Color.BLACK);
                gRotated.fillRect(-4, 10, 8, 6);
                gRotated.fillRect(-2, 16, 4, 4);

                gRotated.dispose();

                // הטקסט מצויר בקונטקסט הרגיל כדי שלא יסתובב או יתהפך
                g.setColor(Color.WHITE);
                g.drawString("Threat", tx + 8, ty);
            }
        }

        private void drawInterceptors(Graphics2D g, boolean isTick) {
            for (InterceptorMissile interceptor : interceptors) {
                int ix = interceptor.getX();
                int iy = interceptor.getY();
                int id = interceptor.getId();
                
                // חישוב זווית התנועה באוויר למיירט
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

                // ציור המיירט סביב ציר (0,0) בהטיה מושלמת
                if (isTick) {
                    gRotated.setColor(Color.CYAN);
                    gRotated.fillRect(-4, 10, 8, 10);
                    gRotated.setColor(Color.WHITE);
                    gRotated.fillRect(-2, 20, 4, 6);
                } else {
                    gRotated.setColor(Color.CYAN);
                    gRotated.fillRect(-3, 10, 6, 8);
                    gRotated.setColor(Color.WHITE);
                    gRotated.fillRect(-1, 18, 2, 6);
                }
                
                gRotated.setColor(Color.LIGHT_GRAY);
                gRotated.fillRect(-4, -10, 8, 20);
                gRotated.setColor(Color.BLACK);
                gRotated.drawRect(-4, -10, 8, 20);
                
                gRotated.setColor(Color.BLUE);
                gRotated.fillRect(-3, -14, 6, 4);
                gRotated.fillRect(-1, -18, 2, 4);

                gRotated.dispose();
            }
        }

        private void drawExplosions(Graphics g) {
            for (Explosion explosion : explosions) {
                long age = System.currentTimeMillis() - explosion.createdAt;
                int alpha = (int) Math.max(0, 255 - age * 255 / 600);
                
                int size = 20 + (int) (age / 10);
                
                g.setColor(new Color(255, 100, 0, alpha));
                g.fillRect(explosion.x - size / 2, explosion.y - size / 2, size, size);
                
                g.setColor(new Color(255, 220, 40, alpha));
                g.fillRect(explosion.x - size / 3, explosion.y - size / 3, size * 2 / 3, size * 2 / 3);
                
                g.setColor(new Color(255, 255, 200, alpha));
                g.fillRect(explosion.x - size / 6, explosion.y - size / 6, size / 3, size / 3);
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
            } else if (!batteryIds.isEmpty()) {
                batteryComboBox.setSelectedIndex(0);
                this.selectedBatteryId = batteryIds.get(0);
            }
        }
    }

    private void updateBatteryInfoDisplay() {
        if (selectedBatteryId == -1) {
            batteryInfoLabel.setText("No Battery Available");
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

        if (selectedBattery != null) {
            String status = selectedBattery.isActive() ? "ACTIVE" : "DESTROYED";
            int ammo = selectedBattery.getMissilesAvailable();
            batteryInfoLabel.setText(String.format("Status: %s | Interceptors: %d", status, ammo));
            
            if (!selectedBattery.isActive()) {
                batteryInfoLabel.setForeground(Color.RED);
            } else {
                batteryInfoLabel.setForeground(Color.BLUE);
            }
        } else {
            batteryInfoLabel.setText("Selected battery not found");
        }
    }
}