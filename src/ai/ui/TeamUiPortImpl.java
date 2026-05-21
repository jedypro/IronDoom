package ai.ui;

import java.util.List;
import shared.ui_ports.TeamUiPort;
import team.domain.AbstractThreat;
import team.domain.Damageable;

public class TeamUiPortImpl extends TeamUiPort {
    private final Ui ui;

    public TeamUiPortImpl(Ui ui) {
        this.ui = ui;
    }

    @Override
    public void method1(int elementId) {
        System.out.println("Method1 called with elementId: " + elementId);
    }

    @Override
    public void log(String message) {
        System.out.println(message);
    }

    @Override
    public void removeEntity(int id) {
        System.out.println("UI: remove entity " + id);
        ui.refresh();
    }

    @Override
    public void triggerExplosion(int x, int y) {
        System.out.println("UI: trigger explosion at (" + x + ", " + y + ")");
        ui.triggerExplosionEffect(x, y);
    }

    @Override
    public void updateScore(int score) {
        System.out.println("UI: update score to " + score);
        ui.updateScore(score);
        if (score <= 0) {
            ui.showStatus("Game Over");
        }
    }

    @Override
    public void displayScene(List<AbstractThreat> threats, List<Damageable> damageables, int score, boolean running) {
        System.out.println("UI: display scene with " + threats.size() + " threats and " + damageables.size() + " damageables");
        ui.setScene(threats, damageables, score, running);
    }
}