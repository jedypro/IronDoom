package ai.ui.Images.newFiles;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.function.Consumer;

public class NetworkTransport extends WebSocketClient implements CommandTransport {

    // מאזין שיעביר את המידע ל-UI של התוקף
    private Consumer<String> stateListener;
    private Consumer<String> eventListener;

    public NetworkTransport(URI serverUri) {
        super(serverUri);
    }

    public void setStateListener(Consumer<String> listener) {
        this.stateListener = listener;
    }
    
    public void setEventListener(Consumer<String> listener) {
        this.eventListener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[NetworkTransport] Connected to the Defender successfully!");
    }

    @Override
    public void onMessage(String message) {
        // אם זו הודעת מצב (סטטוס) מהמגן, נעביר אותה למסך הרדאר
        if (message.startsWith("STATE|") && stateListener != null) {
            stateListener.accept(message);
        } else if (message.startsWith("EVENT|") && eventListener != null) {
            eventListener.accept(message);
        } else {
            System.out.println("[NetworkTransport] Message from Defender: " + message);
        }
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
        StringBuilder sb = new StringBuilder(route);
        for (Object arg : args) {
            sb.append("|").append(arg);
        }
        this.send(sb.toString());
    }
}