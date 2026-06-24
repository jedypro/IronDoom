package team.domain.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import shared.ui_ports.TeamUiPort;
import team.domain.*;

public class RandomEventSystem {

    private final List<EventAction> goodActions = new ArrayList<>();
    private final List<EventAction> badActions = new ArrayList<>();
    private String[][] rows;

    public void initialize(String[][] rows, List<Damageable> damageables, GameState gameState, 
                           List<AbstractThreat> threats, ThreatSpawner spawner, 
                           TeamUiPort uiPort, Runnable onThreatsCleared) {
        this.rows = rows;
        goodActions.clear();
        badActions.clear();

        // --- Good Events ---
        goodActions.add(() -> {
            for (Damageable d : damageables) {
                if (d instanceof InterceptorBattery) {
                    InterceptorBattery b = (InterceptorBattery) d;
                    b.setMissilesAvailable(b.getMissilesAvailable() + 10);
                }
            }
            return "Added 10 missiles to all batteries!";
        });

        goodActions.add(() -> {
            gameState.updateScore(300);
            uiPort.updateScore(gameState.getScore());
            return "+300 Bonus Points!";
        });

        goodActions.add(() -> {
            if (threats.isEmpty()) return "No threats to clear.";
            threats.clear();
            uiPort.playExplosionSound();
            onThreatsCleared.run(); // רץ רק כשהאיומים מנוקים כדי לעדכן את המסך
            return "All visible threats neutralized!";
        });

        // --- Bad Events ---
        badActions.add(() -> {
            for (Damageable d : damageables) {
                if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) d).isActive()) {
                    ((Damageable) d).tookHit();
                    return "A random defense system was disabled!";
                }
            }
            return "Nothing. No active systems found to disable.";
        });

        badActions.add(() -> {
            for (int i = 0; i < 4; i++) {
                AbstractThreat t = spawner.createRandomThreat();
                if (t != null) threats.add(t);
            }
            return "4 sudden threats spawned!";
        });
        
        badActions.add(() -> {
            for (Damageable d : damageables) {
                if (d instanceof InterceptorBattery) {
                    InterceptorBattery b = (InterceptorBattery) d;
                    b.setMissilesAvailable(Math.max(0, b.getMissilesAvailable() - 5));
                }
            }
            return "Lost 5 missiles from all batteries due to sabotage!";
        });
    }

    public void maybeTrigger(double timeStep, TeamUiPort uiPort) {
        if (rows == null || rows.length == 0) return;

        double eventProbabilityThisFrame = (1.0 / 60) * timeStep;
        if (Math.random() < eventProbabilityThisFrame) {
            System.out.println("triggering random event");
            
            int rowIndex = ThreadLocalRandom.current().nextInt(rows.length);
            String description = rows[rowIndex][0];
            String type = rows[rowIndex][1].trim().toUpperCase();
            
            boolean isGood = "GOOD".equals(type);
            String resultText = "No effect";
            if (isGood && !goodActions.isEmpty()) {
                int actionIndex = ThreadLocalRandom.current().nextInt(goodActions.size());
                
                resultText = goodActions.get(actionIndex).execute();
            } else if (!isGood && !badActions.isEmpty()) {
                System.out.println("[log] event: "+ resultText);
                int actionIndex = ThreadLocalRandom.current().nextInt(badActions.size());
                resultText = badActions.get(actionIndex).execute();
                System.out.println("[log] event executed: "+ resultText);

            }

            System.out.println("[LOG] Random Event Triggered: " + description + " | Result: " + resultText);
            uiPort.showGameEvent(description, resultText, isGood);
        }
    }
}