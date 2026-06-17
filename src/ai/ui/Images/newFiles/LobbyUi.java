package ai.ui.Images.newFiles;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class LobbyUi extends JPanel {
    private DefaultListModel<String> listModel;
    private JList<String> playerList;
    private Map<String, String> nameToIpMap = new HashMap<>();
    private LanDiscoveryService discoveryService;

    public LobbyUi(Runnable onHostAction, java.util.function.Consumer<String> onJoinAction, Runnable onBackAction) {
        setLayout(new BorderLayout(10, 10));
        setOpaque(false); // הופך את הפאנל לשקוף כדי שיראו את תמונת הרקע של המשחק

        listModel = new DefaultListModel<>();
        playerList = new JList<>(listModel);
        playerList.setBackground(new Color(30, 30, 30, 200));
        playerList.setForeground(Color.GREEN);
        playerList.setFont(playerList.getFont().deriveFont(16f));
        
        JLabel title = new JLabel("LAN Multiplayer Lobby", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setForeground(Color.WHITE);
        add(title, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), 
                "Searching for players...", 
                javax.swing.border.TitledBorder.LEFT, 
                javax.swing.border.TitledBorder.TOP, 
                new Font("Arial", Font.BOLD, 14), 
                Color.WHITE));
        add(scrollPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        btnPanel.setOpaque(false);
        
        JButton btnHost = new JButton("Host Game (Defender)");
        btnHost.setFont(btnHost.getFont().deriveFont(Font.BOLD, 14f));
        btnHost.addActionListener(e -> {
            discoveryService.stop();
            onHostAction.run(); // מפעיל את הלוגיקה של המגן
        });

        JButton btnJoin = new JButton("Join Game (Attacker)");
        btnJoin.setFont(btnJoin.getFont().deriveFont(Font.BOLD, 14f));
        btnJoin.addActionListener(e -> {
            String selected = playerList.getSelectedValue();
            if (selected != null) {
                String ip = nameToIpMap.get(selected);
                discoveryService.stop();
                onJoinAction.accept(ip); // מפעיל את הלוגיקה של התוקף ב-UI הראשי
            } else {
                JOptionPane.showMessageDialog(this, "Please select a player to join!");
            }
        });

        JButton btnBack = new JButton("Back");
        btnBack.setFont(btnBack.getFont().deriveFont(Font.BOLD, 14f));
        btnBack.addActionListener(e -> {
            discoveryService.stop();
            onBackAction.run();
        });

        btnPanel.add(btnHost);
        btnPanel.add(btnJoin);
        btnPanel.add(btnBack);
        add(btnPanel, BorderLayout.SOUTH);

        discoveryService = new LanDiscoveryService();
        
        // מאזין לאירועי תצוגה: יתחיל את חיפוש השחקנים רק כשהמסך של המולטיפלייר מוצג
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                listModel.clear();
                nameToIpMap.clear();
                discoveryService.start((name, ip) -> {
                    SwingUtilities.invokeLater(() -> {
                        if (!nameToIpMap.containsKey(name)) {
                            nameToIpMap.put(name, ip);
                            listModel.addElement(name);
                        }
                    });
                });
            }

            @Override
            public void componentHidden(java.awt.event.ComponentEvent e) {
                discoveryService.stop();
            }
        });
    }
    
    public void stopDiscovery() {
        if (discoveryService != null) {
            discoveryService.stop();
        }
    }
}