package ai.ui;

import java.util.List;

import javax.swing.SwingUtilities;

import shared.ui_ports.TeamUiPort;
import team.domain.AbstractThreat;
import team.domain.Damageable;
import team.domain.InterceptorMissile;

public class TeamUiPortImpl extends TeamUiPort {
    private final Ui ui;

    public TeamUiPortImpl(Ui ui) {
        this.ui = ui;
    }

    @Override
    public void method1(int elementId) {
        //System.out.println("Method1 called with elementId: " + elementId);
    }

    @Override
    public void log(String message) {
        //System.out.println(message);
    }

    @Override
    public void removeEntity(int id) {
        SwingUtilities.invokeLater(() -> ui.refresh());
    }

    @Override
    public void triggerExplosion(int x, int y) {
        SwingUtilities.invokeLater(() -> ui.triggerExplosionEffect(x, y));
    }

    @Override
    public void updateScore(int score) {
        SwingUtilities.invokeLater(() -> {
            ui.updateScore(score);
            if (score <= 0) {
                ui.showStatus("Game Over");
            }
        });
    }

    @Override
    public void displayScene(List<AbstractThreat> threats, List<Damageable> damageables, List<InterceptorMissile> interceptors, int score, boolean running) {
        SwingUtilities.invokeLater(() -> ui.setScene(threats, damageables, interceptors, score, running));
    }
}