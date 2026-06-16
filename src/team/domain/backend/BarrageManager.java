package team.domain.backend;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import shared.ui_ports.TeamUiPort;
import team.domain.*;

public class BarrageManager {

    private double timeSinceLastBarrage = 0;
    private double timeSinceWarning = 0;
    private boolean barrageWarningScheduled = false;
    private int pendingBarrageSize = 0;

    private long lastWarningTime = 0;
    private static final long WARNING_COOLDOWN_MS = 5000;
    private static final int BARRAGE_THRESHOLD = 6;

    private static final double BARRAGE_INTERVAL_BASE_SECONDS = 40.0;
    private static final double BARRAGE_WARNING_DELAY_SECONDS = 3.0;
    private static final int MIN_BARRAGE_SIZE = 5;
    private static final int MAX_BARRAGE_SIZE = 8;

    public void reset() {
        timeSinceLastBarrage = 0;
        timeSinceWarning = 0;
        barrageWarningScheduled = false;
        pendingBarrageSize = 0;
    }

    public void advanceBarrageTimers(double timeStep, GameState gameState, List<AbstractThreat> threats, 
                                     ThreatSpawner spawner, GiftSpawner giftSpawner, 
                                     List<Gift> activeGifts, List<Damageable> damageables, TeamUiPort uiPort) {
        if (gameState.getLevel() <= 1) {
            reset();
            return;
        }

        if (barrageWarningScheduled) {
            timeSinceWarning += timeStep;
            if (timeSinceWarning >= BARRAGE_WARNING_DELAY_SECONDS) {
                timeSinceWarning = 0;
                barrageWarningScheduled = false;
                timeSinceLastBarrage = 0;
                
                System.out.println("[LOG] Executing Barrage of " + pendingBarrageSize + " threats.");
                uiPort.showWarning("Barrage incoming now: " + pendingBarrageSize + " threats!");
                uiPort.playWarningSound();
                
                for (int i = 0; i < pendingBarrageSize; i++) {
                    AbstractThreat newThreat = spawner.createRandomThreat();
                    if (newThreat != null) {
                        threats.add(newThreat);
                    }
                }
            }
            return;
        }

        double interval = BARRAGE_INTERVAL_BASE_SECONDS / gameState.getLevel();
        timeSinceLastBarrage += timeStep;
        
        if (timeSinceLastBarrage >= interval) {
            timeSinceLastBarrage = interval;
            pendingBarrageSize = ThreadLocalRandom.current().nextInt(MIN_BARRAGE_SIZE, MAX_BARRAGE_SIZE + 1);
            barrageWarningScheduled = true;
            timeSinceWarning = 0;
            
            uiPort.showWarning("Barrage warning: " + pendingBarrageSize + " threats in 3 seconds!");
            
            boolean isThereDamagedBattery = false;
            for (Damageable d : damageables) {
                if (d instanceof AbstractDefenseSystem && !((AbstractDefenseSystem) d).isActive()) {
                    isThereDamagedBattery = true;
                    break;
                }
            }
            Gift newGift = giftSpawner.spawnGift(isThereDamagedBattery);
            activeGifts.add(newGift);
        }
    }

    public void checkBarrage(List<AbstractThreat> threats, TeamUiPort uiPort) {
        if (threats.size() >= BARRAGE_THRESHOLD) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastWarningTime > WARNING_COOLDOWN_MS) {
                lastWarningTime = currentTime;
                
                int missileCount = 0;
                int droneCount = 0;
                for (AbstractThreat threat : threats) {
                    if (threat instanceof BallisticMissile) {
                        missileCount++;
                    } else if (threat instanceof UAV) {
                        droneCount++;
                    }
                }
                
                String warningMsg = "⚠ INCOMING BARRAGE! ⚠ ";
                if (missileCount > 0 && droneCount > 0) {
                    warningMsg += "Missiles + Drones incoming!";
                } else if (missileCount > 0) {
                    warningMsg += "Missile barrage incoming!";
                } else if (droneCount > 0) {
                    warningMsg += "Drone swarm incoming!";
                }
                
                uiPort.showWarning(warningMsg);
            }
        }
    }
}