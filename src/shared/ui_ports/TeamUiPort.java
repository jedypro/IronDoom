package shared.ui_ports;

import java.util.List;

import team.domain.Gift;
import team.domain.AbstractThreat;
import team.domain.Damageable;
import team.domain.DefenseEntity;

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
    public abstract void showWarning(String message);
    public abstract void showLevelComplete(String message);
    public abstract void displayCivilians(java.util.List<team.domain.Civilian> civilians);
    public abstract void displayScene(List<AbstractThreat> threats, List<Damageable> damageables, List<DefenseEntity> interceptors, List<Gift> gifts, int score, boolean running, int groundY);


    //sound effects
    public abstract void playExplosionSound();
    public abstract void playInterceptSound();
    public abstract void playWarningSound();
    public abstract void playLevelCompleteSound();

    //events
    public abstract void showGameEvent(String description, String result, boolean isGood);

    public void showGiftCollected(String message) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'showGiftCollected'");
    }



}