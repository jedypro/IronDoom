package ai.ui;

import javax.swing.*;
import java.awt.*;

import base.Params;
import shared.MainRouter;
import shared.ui_ports.TeamUiPort;

public class Ui {
    private MainRouter mainRouter;
    private TeamUiPortImpl uiInstance;
    private MissileCanvas canvas;

    public void setUiPorts() {
        uiInstance = new TeamUiPortImpl();
        TeamUiPort.setInstance((TeamUiPort) uiInstance);
    }

    public void start(MainRouter mainRouter) {
        this.mainRouter = mainRouter;
        createAndShowWindow();
        System.out.println("UI started");
        System.out.println("Calling backend start method via router /team/start ...");
        mainRouter.route("/team/start", Params.of());
    }

    private void createAndShowWindow() {
        System.out.println("Creating and showing UI window...");
        
        JFrame frame = new JFrame("Ballistic Missile Launcher");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Initialize canvas for drawing the missile
        canvas = new MissileCanvas();
        frame.add(canvas, BorderLayout.CENTER);

        // Initialize launch button and route action
        JButton launchButton = new JButton("שגר");
        launchButton.addActionListener(e -> {
            mainRouter.route("/team/launchMissile", Params.of());
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(launchButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    // Called by TeamUiPortImpl to update the graphic
    public void moveMissileGraphic(double x, double y) {
        if (canvas != null) {
            canvas.updatePosition(x, y);
        }
    }

    // Custom JPanel to handle graphics rendering
    private class MissileCanvas extends JPanel {
        private int missileX = 0;
        private int missileY = 0;
        private boolean isFlying = false;

        public void updatePosition(double x, double y) {
            this.missileX = (int) x;
            // Invert Y axis: screen coordinates start from top-left
            this.missileY = getHeight() - (int) y; 
            this.isFlying = true;
            repaint(); // Trigger paintComponent
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g); // Clear previous frame
            
            if (isFlying) {
                g.setColor(Color.RED);
                g.fillOval(missileX, missileY, 10, 10);
            }
        }
    }
}