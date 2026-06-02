package ai.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
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
    private int currentSliderAngle = 45;

    public void setUiPorts() {
        uiInstance = new TeamUiPortImpl(this);
        TeamUiPort.setInstance(uiInstance);
    }

    public void start(MainRouter mainRouter) throws InterruptedException {
        this.mainRouter = mainRouter;
        SwingUtilities.invokeLater(() -> {
            createAndShowWindow();
            //System.out.println("UI started");
            //System.out.println("Calling backend start method via router /team/start ...");
            mainRouter.route("/team/start", Params.of());
            startupComplete.countDown();
        });
        
        // Wait for UI and backend initialization to complete
        //System.out.println("Waiting for UI initialization to complete...");
        startupComplete.await();
        //System.out.println("UI initialization complete, ready to start scheduler");
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

        // === Angle Slider Setup ===
        javax.swing.JSlider angleSlider = new javax.swing.JSlider(0, 90, 45);
        angleSlider.setMajorTickSpacing(45);
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
                updateBatteryInfoDisplay(); // Refresh info immediately on selection change
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

        // Left Arrow
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("LEFT"), "decreaseAngle");
        actionMap.put("decreaseAngle", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int val = angleSlider.getValue();
                if (val > angleSlider.getMinimum()) angleSlider.setValue(val - 2);
            }
        });

        // Right Arrow
        inputMap.put(javax.swing.KeyStroke.getKeyStroke("RIGHT"), "increaseAngle");
        actionMap.put("increaseAngle", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int val = angleSlider.getValue();
                if (val < angleSlider.getMaximum()) angleSlider.setValue(val + 2);
            }
        });

        // Spacebar - Simulates a mouse click on the fire button
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
        controlPanel.add(new JLabel("Angle (0-90):"));
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
        
        // Update the local interceptors list for rendering
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

        GameCanvas() {
            setBackground(Color.BLACK);
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
            if (threats.isEmpty() && damageables.isEmpty()) {
                drawMessage(g, "Waiting for scene data...");
                return;
            }

            drawGroundAssets(g);
            drawThreats(g);
            drawInterceptors(g);
            drawExplosions(g);
        }

        private void drawGroundAssets(Graphics g) {
            for (Damageable damageable : damageables) {
                if (damageable instanceof GroundAsset) {
                    GroundAsset city = (GroundAsset) damageable;
                    g.setColor(Color.GREEN);
                    g.fillRect(city.getX(), city.getY(), city.getWidth(), city.getHeight());
                    g.setColor(Color.WHITE);
                    g.drawString(city.getName(), city.getX() + 2, city.getY() + 14);
                } else if (damageable instanceof InterceptorBattery) {
                    InterceptorBattery battery = (InterceptorBattery) damageable;
                    
                    Graphics2D g2d = (Graphics2D) g.create();
                    
                    int bx = battery.getX();
                    int by = battery.getY();
                    
                    //move position to (0,0)
                    g2d.translate(bx, by);
                    
                    g2d.rotate(Math.toRadians(currentSliderAngle-180));
                    
                   //drawing the battery
                    g2d.setColor(Color.GRAY);
                    int boxWidth = 40;
                    int boxHeight = 30;
                    g2d.fillRect(0, -boxHeight / 2, boxWidth, boxHeight); // (0,0) הוא בסיס המשגר
                    
                    //battery base
                    g2d.setColor(Color.DARK_GRAY);
                    g2d.fillOval(-8, -8, 16, 16);
                    g2d.dispose();
                    
                    g.setColor(Color.WHITE);
                    g.drawString("Battery", bx - 20, by - 25);

                    if (!battery.isActive()) {
                        g.setColor(new Color(255, 0, 0, 128));
                        g.fillOval(bx - 15, by - 15, 30, 30);
                    }
                }
            }
        }

        private void drawThreats(Graphics g) {
            for (AbstractThreat threat : threats) {
                g.setColor(Color.ORANGE);
                int size = 10;
                g.fillOval(threat.getX() - size / 2, threat.getY() - size / 2, size, size);
                g.setColor(Color.WHITE);
                g.drawString("Threat", threat.getX() + 8, threat.getY());
            }
        }

        private void drawExplosions(Graphics g) {
            for (Explosion explosion : explosions) {
                long age = System.currentTimeMillis() - explosion.createdAt;
                int alpha = (int) Math.max(0, 255 - age * 255 / 600);
                g.setColor(new Color(255, 220, 40, alpha));
                int radius = 20 + (int) (age / 20);
                g.fillOval(explosion.x - radius / 2, explosion.y - radius / 2, radius, radius);
            }
        }

        private void drawMessage(Graphics g, String message) {
            g.setColor(Color.WHITE);
            g.drawString(message, 20, 20);
        }
    }

    private void drawInterceptors(Graphics g) {

    for (InterceptorMissile interceptor : interceptors) {
        g.setColor(Color.RED); 
        int size = 14;
        g.fillOval(interceptor.getX() - size / 2, interceptor.getY() - size / 2, size, size);
    }
}

/**
 * Dynamically updates the combo box items based on available interceptor batteries.
 * Rebuilds the list safely without interrupting the user's current selection.
 */
private void updateBatteryComboBoxItems(List<Damageable> currentDamageables) {
    List<Integer> batteryIds = new ArrayList<>();
    for (Damageable d : currentDamageables) {
        if (d instanceof InterceptorBattery) {
            batteryIds.add(((InterceptorBattery) d).getId());
        }
    }

    // Check if the dropdown needs to be updated to avoid unnecessary flickering
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
        // Restore previous selection if it still exists
        if (previousSelection != null && batteryIds.contains(previousSelection)) {
            batteryComboBox.setSelectedItem(previousSelection);
        } else if (!batteryIds.isEmpty()) {
            batteryComboBox.setSelectedIndex(0);
            this.selectedBatteryId = batteryIds.get(0);
        }
    }
}

/**
 * Finds the currently selected battery and updates its live status and inventory on the UI.
 */
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
        
        // Visual indicator if the battery is knocked out
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