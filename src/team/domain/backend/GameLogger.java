package team.domain.backend;

import java.text.SimpleDateFormat;
import java.util.Date;

public class GameLogger {
    public static boolean ENABLED = true;
    private static final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss.SSS");

    public static void log(String source, String message) {
        if (ENABLED) {
            String time = formatter.format(new Date());
            System.out.println("[" + time + "] [" + source + "] " + message);
        }
    }
}
