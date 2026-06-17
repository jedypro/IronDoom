package ai.ui.Images.newFiles;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import shared.MainRouter;
import base.Params;

public class GameServer extends WebSocketServer {
    
    private final MainRouter router;
    private static GameServer instance;

    public GameServer(MainRouter router, int port) {
        super(new InetSocketAddress(port));
        this.router = router;
        instance = this;
    }
    // פונקציה סטטית ש-TeamBackend יכול לקרוא לה כדי לשדר לכולם
    public static void broadcastToAttacker(String message) {
        if (instance != null) {
            // הפונקציה broadcast קיימת מובנית ב-WebSocketServer ושולחת את הטקסט לכל הלקוחות
            instance.broadcast(message);
        }
    }


    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[GameServer] Attacker connected from: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[GameServer] Attacker disconnected.");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // מפענחים את ההודעה. פורמט מצופה: "route|arg1|arg2|arg3"
        try {
            // הוספת \ כדי שה-Regex יתייחס לזה כתו רגיל
            String[] parts = message.split("\\|"); 
            String route = parts[0];
            
            // אוספים את שאר הארגומנטים
            Object[] args = new Object[parts.length - 1];
            for (int i = 1; i < parts.length; i++) {
                args[i - 1] = parts[i];
            }
            
            // מעבירים לראוטר הראשי של המשחק - Params ימיר את הסטרינגים למספרים אוטומטית!
            router.route(route, Params.of(args));
            
        } catch (Exception e) {
            System.err.println("[GameServer] Failed to process message: " + message);
            e.printStackTrace();
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[GameServer] Error occurred:");
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("[GameServer] Server started and listening on port " + getPort());
    }
}