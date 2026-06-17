package ai.ui.Images.newFiles;

import java.net.*;
import java.util.Random;
import java.util.function.BiConsumer;

public class LanDiscoveryService {
    private static final int PORT = 8888;
    private static final String MAGIC_WORD = "IRONDOOM_DISCOVERY";
    private boolean running = false;
    private DatagramSocket socket;
    private String myName = "Player_" + new Random().nextInt(1000);

    public void start(BiConsumer<String, String> onPlayerFound) {
        running = true;
        try {
            // פתיחת שקע UDP מיוחד שמאפשר האזנה מרובה
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
            socket.setBroadcast(true);

            // 1. תהליכון שמאזין לשחקנים אחרים ברשת
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength());
                        
                        if (message.startsWith(MAGIC_WORD)) {
                            String[] parts = message.split("\\|");
                            if (parts.length == 2) {
                                String name = parts[1];
                                // סינון: נוסיף לרשימה רק אם זה שחקן אחר ולא אנחנו
                                if (!name.equals(myName)) {
                                    String ip = packet.getAddress().getHostAddress();
                                    onPlayerFound.accept(name, ip); // מודיע ל-UI שמצאנו מישהו!
                                }
                            }
                        }
                    } catch (Exception e) { if (running) e.printStackTrace(); }
                }
            }).start();

            // 2. תהליכון שמשדר את הנוכחות שלנו לכולם
            new Thread(() -> {
                while (running) {
                    try {
                        String msg = MAGIC_WORD + "|" + myName;
                        byte[] data = msg.getBytes();
                        // שידור לכל הרשת
                        socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), PORT));
                        // שידור פנימי למחשב עצמו (חובה כשבודקים 2 חלונות על אותו מחשב)
                        socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), PORT));
                        Thread.sleep(1500); // משדר כל שניה וחצי
                    } catch (Exception e) { if (running) e.printStackTrace(); }
                }
            }).start();

        } catch (Exception e) { e.printStackTrace(); }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}