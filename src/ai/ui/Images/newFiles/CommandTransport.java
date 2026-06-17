package ai.ui.Images.newFiles;

public interface CommandTransport {
    // מקבל נתיב (Route) ורשימה של ארגומנטים מכל סוג שהוא
    void sendCommand(String route, Object... args);
}