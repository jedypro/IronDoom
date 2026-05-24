package ai.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
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

public class Ui {
    private MainRouter mainRouter;
    private TeamUiPortImpl uiInstance;
    private JFrame frame;
    private GameCanvas gameCanvas;
    private JLabel scoreLabel;
    private JLabel statusLabel;
    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private boolean running = true;
    private CountDownLatch startupComplete = new CountDownLatch(1);

    public void setUiPorts() {
        uiInstance = new TeamUiPortImpl(this);
        TeamUiPort.setInstance(uiInstance);
    }

    public void start(MainRouter mainRouter) throws InterruptedException {
        this.mainRouter = mainRouter;
        SwingUtilities.invokeLater(() -> {
            createAndShowWindow();
            System.out.println("UI started");
            System.out.println("Calling backend start method via router /team/start ...");
            mainRouter.route("/team/start", Params.of());
            startupComplete.countDown();
        });
        
        // Wait for UI and backend initialization to complete
        System.out.println("Waiting for UI initialization to complete...");
        startupComplete.await();
        System.out.println("UI initialization complete, ready to start scheduler");
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

    public void setScene(List<AbstractThreat> threats, List<Damageable> damageables, int score, boolean running) {
        this.threats.clear();
        this.threats.addAll(threats);
        this.damageables.clear();
        this.damageables.addAll(damageables);
        this.running = running;
        updateScore(score);
        showStatus(running ? "Running" : "Game Over");
        refresh();
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
                    g.setColor(Color.CYAN);
                    int radius = 20;
                    g.fillOval(battery.getX() - radius, battery.getY() - radius, radius * 2, radius * 2);
                    g.setColor(Color.WHITE);
                    g.drawString("Battery", battery.getX() - radius, battery.getY() - radius - 4);
                    if (!battery.isActive()) {
                        g.setColor(new Color(255, 0, 0, 128));
                        g.fillOval(battery.getX() - radius, battery.getY() - radius, radius * 2, radius * 2);
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
}
