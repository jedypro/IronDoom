package ai.ui.Images.newFiles;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AttackerUi extends JPanel {
    
    private CommandTransport transport;
    private final RemoteCanvas canvas;

    public AttackerUi() {
        setLayout(new BorderLayout());

        // --- פאנל הרדאר ---
        canvas = new RemoteCanvas();
        add(canvas, BorderLayout.CENTER);

        // --- פאנל השליטה ---
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controlPanel.setBackground(new Color(40, 40, 40));

        JLabel lblInstructions = new JLabel("Select weapon & click on radar to launch:");
        lblInstructions.setForeground(Color.WHITE);
        
        JRadioButton rbtnBallistic = new JRadioButton("🚀 Ballistic Missile", true);
        rbtnBallistic.setBackground(new Color(40, 40, 40));
        rbtnBallistic.setForeground(Color.WHITE);
        
        JRadioButton rbtnUAV = new JRadioButton("✈️ UAV");
        rbtnUAV.setBackground(new Color(40, 40, 40));
        rbtnUAV.setForeground(Color.WHITE);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbtnBallistic);
        bg.add(rbtnUAV);

        controlPanel.add(lblInstructions);
        controlPanel.add(rbtnBallistic);
        controlPanel.add(rbtnUAV);
        add(controlPanel, BorderLayout.SOUTH);
        
        canvas.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (transport != null) {
                    String type = rbtnBallistic.isSelected() ? "BallisticMissile" : "UAV";
                    int worldX = canvas.screenToWorldX(e.getX());
                    double vy = type.equals("UAV") ? 5.0 : 15.0;
                    transport.sendCommand("/team/attacker/spawn", type, worldX, vy);
                }
            }
        });
    }

    public void connect(String ipAddress) {
        new Thread(() -> {
            try {
                NetworkTransport client = new NetworkTransport(new URI("ws://" + ipAddress + ":8080"));
                if (client.connectBlocking()) {
                    this.transport = client;
                    client.setStateListener(this::updateRadar);
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Could not connect to " + ipAddress));
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Connection error: " + e.getMessage()));
            }
        }).start();
    }

    // זו הפונקציה שהרשת קוראת לה כשמגיעים נתונים מהמגן
    public void updateRadar(String stateMessage) {
        canvas.parseAndUpdate(stateMessage);
    }

    // --- מחלקת הציור הטקטי ---
    private class RemoteCanvas extends JPanel {
        private List<RenderEntity> entities = new ArrayList<>();

        public RemoteCanvas() {
            setBackground(new Color(10, 15, 30));
        }

        public int screenToWorldX(int screenX) {
            double scale = Math.min(getWidth() / 1200.0, getHeight() / 800.0);
            int offsetX = (int) ((getWidth() - 1200 * scale) / 2);
            int worldX = (int) ((screenX - offsetX) / scale);
            return Math.max(0, Math.min(1200, worldX));
        }

        public void parseAndUpdate(String message) {
            List<RenderEntity> newEntities = new ArrayList<>();
            String[] parts = message.split("\\|");
            for (int i = 1; i < parts.length; i++) {
                try {
                    String[] data = parts[i].split(",");
                    RenderEntity e = new RenderEntity();
                    e.type = data[0];
                    e.x = Integer.parseInt(data[2]);
                    e.y = Integer.parseInt(data[3]);
                    
                    // קריאת נתונים נוספים לפי סוג האובייקט
                    if (e.type.equals("A")) { 
                        e.w = Integer.parseInt(data[4]); e.h = Integer.parseInt(data[5]); 
                    } else if (e.type.equals("L")) {
                        // נקודות הסיום של הלייזר
                        e.endX = Integer.parseInt(data[4]); e.endY = Integer.parseInt(data[5]);
                    } else if (e.type.equals("B") || e.type.equals("LB")) {
                        e.isActive = Integer.parseInt(data[4]) == 1;
                    }
                    newEntities.add(e);
                } catch (Exception ignored) {}
            }
            this.entities = newEntities;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            double scale = Math.min(getWidth() / 1200.0, getHeight() / 800.0);
            int offsetX = (int) ((getWidth() - 1200 * scale) / 2);
            int offsetY = (int) ((getHeight() - 800 * scale) / 2);

            for (RenderEntity e : entities) {
                int sx = offsetX + (int) (e.x * scale);
                int sy = offsetY + (int) (e.y * scale);

                if (e.type.equals("T")) {
                    g2d.setColor(Color.RED);
                    g2d.fillOval(sx-5, sy-5, 10, 10);
                } else if (e.type.equals("I")) { // טיל יירוט
                    g2d.setColor(Color.YELLOW);
                    g2d.fillOval(sx-3, sy-3, 6, 6);
                } else if (e.type.equals("L")) { // קרן לייזר
                    g2d.setColor(Color.CYAN);
                    int ex = offsetX + (int) (e.endX * scale);
                    int ey = offsetY + (int) (e.endY * scale);
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawLine(sx, sy, ex, ey);
                } else if (e.type.equals("A")) {
                    g2d.setColor(new Color(100, 100, 100)); // אפור כהה
                    g2d.fillRect(sx, sy, (int)(e.w * scale), (int)(e.h * scale));
                } else if (e.type.equals("B")) { // סוללת טילים
                    g2d.setColor(e.isActive ? Color.GREEN : Color.DARK_GRAY);
                    g2d.fillRect(sx - 15, sy - 10, 30, 20);
                    g2d.setColor(Color.WHITE);
                    g2d.drawString("B", sx - 4, sy + 5);
                } else if (e.type.equals("LB")) { // סוללת לייזר
                    g2d.setColor(e.isActive ? Color.BLUE : Color.DARK_GRAY);
                    g2d.fillRect(sx - 15, sy - 10, 30, 20);
                    g2d.setColor(Color.WHITE);
                    g2d.drawString("LB", sx - 7, sy + 5);
                }
            }
        }
    }

    private static class RenderEntity {
        String type;
        int x, y, w, h;
        int endX, endY; // עבור קרני לייזר
        boolean isActive; // עבור סוללות
    }
}