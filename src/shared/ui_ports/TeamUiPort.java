package shared.ui_ports;

public abstract class TeamUiPort {

    private static TeamUiPort instance;

    public static void setInstance(TeamUiPort ui) {
        if (ui == null) throw new IllegalArgumentException("TeamUiPort instance cannot be null");
        if (instance != null) throw new IllegalStateException("TeamUiPort instance already set");
        instance = ui;
    }

    public static TeamUiPort getInstance() {
        if (instance == null) throw new IllegalStateException("TeamUiPort instance not set yet");
        return instance;
    }

    // Your UI commands here, for example:
    public abstract void method1(int id);
    public abstract void log(String message);
    public abstract void removeEntity(int id);
    public abstract void triggerExplosion(int x, int y);
    public abstract void updateScore(int score);
    public abstract void updateLevel(int level);
    public abstract void displayScene(java.util.List<team.domain.AbstractThreat> threats, java.util.List<team.domain.Damageable> damageables, java.util.List<team.domain.InterceptorMissile> interceptors,  int score, boolean running);
}