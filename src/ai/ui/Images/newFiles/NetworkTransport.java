package ai.ui.Images.newFiles;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;

public class NetworkTransport extends WebSocketClient implements CommandTransport {

    public NetworkTransport(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[NetworkTransport] Connected to the Defender successfully!");
    }

    @Override
    public void onMessage(String message) {
        // כאן אפשר לקבל עדכונים מהמגן בעתיד (למשל, כדי להציג לתוקף את מצב הניקוד)
        System.out.println("[NetworkTransport] Message from Defender: " + message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[NetworkTransport] Connection closed.");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void sendCommand(String route, Object... args) {
        // אורזים את הנתיב והארגומנטים למחרוזת אחת עם מפריד "|"
        StringBuilder sb = new StringBuilder(route);
        for (Object arg : args) {
            sb.append("|").append(arg);
        }
        
        // שולחים לשרת
        this.send(sb.toString());
    }
}