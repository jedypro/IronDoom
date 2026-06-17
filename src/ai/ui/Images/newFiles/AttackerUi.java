package ai.ui.Images.newFiles;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;

public class AttackerUi extends JFrame {
    
    private final CommandTransport transport;

    public AttackerUi(CommandTransport transport) {
        this.transport = transport;
        
        setTitle("IronDoom - Attacker Console");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(4, 1, 10, 10));

        // כפתור שיגור טיל בליסטי
        JButton btnBallistic = new JButton("🚀 Launch Ballistic Missile");
        btnBallistic.setFont(new Font("Arial", Font.BOLD, 18));
        btnBallistic.addActionListener(e -> {
            // מגרילים מיקום X כדי שלא ייצא תמיד מאותה נקודה
            int randomX = ThreadLocalRandom.current().nextInt(-100, 1000);
            double vy = 15.0; // מהירות נפילה
            
            // הפקודה הזו תיסע ברשת ותגיע ל-teamRouter!
            transport.sendCommand("team/attacker/spawn", "BallisticMissile", randomX, vy);        });

        // כפתור שיגור כטב"ם (UAV)
        JButton btnUAV = new JButton("✈️ Launch UAV");
        btnUAV.setFont(new Font("Arial", Font.BOLD, 18));
        btnUAV.addActionListener(e -> {
            int randomX = ThreadLocalRandom.current().nextInt(-100, 100);
            double vy = 5.0; // כטב"מ זז לאט יותר אופקית/אנכית
            transport.sendCommand("team/attacker/spawn", "UAV", randomX, vy);
        });

        add(new JLabel("Select Threat to Launch against Defender:", SwingConstants.CENTER));
        add(btnBallistic);
        add(btnUAV);
    }

    // קובץ ה-Main של התוקף!
    public static void main(String[] args) {
        try {
            // מתחברים למחשב המגן (כרגע מוגדר ל-localhost, למשחק ברשת שים פה IP)
            NetworkTransport client = new NetworkTransport(new URI("ws://localhost:8080"));
            
            // מתחבר וממתין להצלחה
            boolean connected = client.connectBlocking(); 
            
            if (connected) {
                SwingUtilities.invokeLater(() -> {
                    AttackerUi ui = new AttackerUi(client);
                    ui.setLocationRelativeTo(null); // ממורכז במסך
                    ui.setVisible(true);
                });
            } else {
                System.err.println("Could not connect to Defender at port 8080");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}