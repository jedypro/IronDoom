package ai.ui.Images.newFiles;

import java.net.*;
import java.util.Random;

public class LanDiscoveryService {
    private static final int PORT = 8888;
    private static final String MAGIC_WORD = "IRONDOOM_DISCOVERY";
    private boolean running = false;
    private DatagramSocket socket;
    private String myName = "Player_" + new Random().nextInt(1000);

    public interface NetworkEventHandler {
        void onPlayerFound(String name, String ip);
        void onChallengeReceived(String fromName, String fromIp);
        void onChallengeDeclined(String fromName);
        void onStartGame(String myRole, String hostIp, String otherPlayerName);
        void onServerReady(String hostIp);
    }

    public void start(NetworkEventHandler handler) {
        running = true;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
            socket.setBroadcast(true);

            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength());
                        String senderIp = packet.getAddress().getHostAddress();
                        
                        String[] parts = message.split("\\|");
                        if (parts[0].equals(MAGIC_WORD) && parts.length == 2) {
                            String name = parts[1];
                            if (!name.equals(myName)) {
                                handler.onPlayerFound(name, senderIp);
                            }
                        } else if (parts[0].equals("CHALLENGE") && parts.length == 3) {
                            if (parts[2].equals(myName)) {
                                handler.onChallengeReceived(parts[1], senderIp);
                            }
                        } else if (parts[0].equals("DECLINED") && parts.length == 3) {
                            if (parts[2].equals(myName)) {
                                handler.onChallengeDeclined(parts[1]);
                            }
                        } else if (parts[0].equals("START_GAME") && parts.length == 4) {
                            if (parts[2].equals(myName)) {
                                handler.onStartGame(parts[3], senderIp, parts[1]);
                            }
                        } else if (parts[0].equals("SERVER_READY") && parts.length == 3) {
                            if (parts[2].equals(myName)) {
                                handler.onServerReady(senderIp);
                            }
                        }
                    } catch (Exception e) { if (running) e.printStackTrace(); }
                }
            }).start();

            new Thread(() -> {
                while (running) {
                    try {
                        String msg = MAGIC_WORD + "|" + myName;
                        byte[] data = msg.getBytes();
                        socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), PORT));
                        socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), PORT));
                        Thread.sleep(1500);
                    } catch (Exception e) { if (running) e.printStackTrace(); }
                }
            }).start();

        } catch (Exception e) { e.printStackTrace(); }
    }

    public void sendChallenge(String targetName, String targetIp) {
        sendMessage("CHALLENGE|" + myName + "|" + targetName, targetIp);
    }
    
    public void sendDeclined(String targetName, String targetIp) {
        sendMessage("DECLINED|" + myName + "|" + targetName, targetIp);
    }
    
    public void sendStartGame(String targetName, String targetIp, String roleForTarget) {
        sendMessage("START_GAME|" + myName + "|" + targetName + "|" + roleForTarget, targetIp);
    }

    public void sendServerReady(String targetName, String targetIp) {
        sendMessage("SERVER_READY|" + myName + "|" + targetName, targetIp);
    }

    private void sendMessage(String msg, String targetIp) {
        try {
            byte[] data = msg.getBytes();
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(targetIp), PORT));
            if (!targetIp.equals("127.0.0.1")) {
                socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), PORT));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}