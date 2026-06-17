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

    public LobbyUi(Runnable onStartAsHost, java.util.function.Consumer<String> onStartAsClient, Runnable onBackAction) {
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

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        btnPanel.setOpaque(false);
        
        JButton btnChallenge = new JButton("Challenge Player");
        btnChallenge.setFont(btnChallenge.getFont().deriveFont(Font.BOLD, 14f));
        btnChallenge.addActionListener(e -> {
            String selected = playerList.getSelectedValue();
            if (selected != null) {
                String ip = nameToIpMap.get(selected);
                discoveryService.sendChallenge(selected, ip);
                JOptionPane.showMessageDialog(this, "Challenge sent to " + selected + ".\nWaiting for response...");
            } else {
                JOptionPane.showMessageDialog(this, "Please select a player to challenge!");
            }
        });

        JButton btnBack = new JButton("Back");
        btnBack.setFont(btnBack.getFont().deriveFont(Font.BOLD, 14f));
        btnBack.addActionListener(e -> {
            discoveryService.stop();
            onBackAction.run();
        });

        btnPanel.add(btnChallenge);
        btnPanel.add(btnBack);
        add(btnPanel, BorderLayout.SOUTH);

        discoveryService = new LanDiscoveryService();
        
        // מאזין לאירועי תצוגה: יתחיל את חיפוש השחקנים רק כשהמסך של המולטיפלייר מוצג
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                listModel.clear();
                nameToIpMap.clear();
                discoveryService.start(new LanDiscoveryService.NetworkEventHandler() {
                    @Override
                    public void onPlayerFound(String name, String ip) {
                        SwingUtilities.invokeLater(() -> {
                            if (!nameToIpMap.containsKey(name)) {
                                nameToIpMap.put(name, ip);
                                listModel.addElement(name);
                            }
                        });
                    }

                    @Override
                    public void onChallengeReceived(String fromName, String fromIp) {
                        SwingUtilities.invokeLater(() -> {
                            int response = JOptionPane.showConfirmDialog(LobbyUi.this,
                                    fromName + " is challenging you to a game!\nDo you accept?",
                                    "Challenge Received", JOptionPane.YES_NO_OPTION);
                            if (response == JOptionPane.YES_OPTION) {
                                boolean iAmDefender = Math.random() > 0.5;
                                if (iAmDefender) {
                                    discoveryService.sendStartGame(fromName, fromIp, "ATTACKER");
                                    discoveryService.stop();
                                    onStartAsHost.run();
                                } else {
                                    discoveryService.sendStartGame(fromName, fromIp, "DEFENDER");
                                }
                            } else {
                                discoveryService.sendDeclined(fromName, fromIp);
                            }
                        });
                    }

                    @Override
                    public void onChallengeDeclined(String fromName) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(LobbyUi.this, fromName + " declined your challenge."));
                    }

                    @Override
                    public void onStartGame(String myRole, String hostIp, String otherPlayerName) {
                        SwingUtilities.invokeLater(() -> {
                            if (myRole.equals("DEFENDER")) {
                                JOptionPane.showMessageDialog(LobbyUi.this, "Roles decided!\nYou are the DEFENDER.\nStarting server...");
                                discoveryService.sendServerReady(otherPlayerName, hostIp);
                                discoveryService.stop();
                                onStartAsHost.run();
                            } else {
                                JOptionPane.showMessageDialog(LobbyUi.this, "Roles decided!\nYou are the ATTACKER.\nConnecting...");
                                discoveryService.stop();
                                onStartAsClient.accept(hostIp);
                            }
                        });
                    }

                    @Override
                    public void onServerReady(String hostIp) {
                        SwingUtilities.invokeLater(() -> {
                            discoveryService.stop();
                            onStartAsClient.accept(hostIp);
                        });
                    }
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