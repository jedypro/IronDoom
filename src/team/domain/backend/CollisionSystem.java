package team.domain.backend;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import shared.ui_ports.TeamUiPort;
import team.domain.*; // מייבא את כל המחלקות מהתיקייה הקודמת (איומים, הגנות, וכו')

public class CollisionSystem {

    private final Map<Integer, Double> threatLaserContactTime = new HashMap<>();
    private final Random random = new Random();

    // הפונקציה הראשית ש-TeamBackend יקרא לה
    public void checkCollisions(double timeStep, 
                                List<AbstractThreat> threats, 
                                List<Damageable> damageables, 
                                List<DefenseEntity> interceptors, 
                                List<Gift> gifts, 
                                GameState gameState,
                                PopulationManager populationManager,
                                AssetSpawner assetSpawner,
                                TeamUiPort uiPort) {
        
        try {
            //GameLogger.log("CollisionSystem", "--> Starting checkThreatCollisions");
            checkThreatCollisions(timeStep, threats, damageables, interceptors, gameState, populationManager, uiPort);
            
            //GameLogger.log("CollisionSystem", "--> Starting checkGiftCollisions");
            checkGiftCollisions(gifts, interceptors, damageables, gameState, assetSpawner, uiPort);
            
            //GameLogger.log("CollisionSystem", "<-- Finished checkCollisions successfully");
        } catch (Exception e) {
            GameLogger.log("CollisionSystem", "!!! CRITICAL CRASH in CollisionSystem: " + e.getClass().getSimpleName() + " !!!");
            e.printStackTrace(); // מדפיס את השגיאה המדויקת והשורה בה היא קרתה
        }
    }

    private void checkThreatCollisions(double timeStep, List<AbstractThreat> threats, List<Damageable> damageables, 
                                       List<DefenseEntity> interceptors, GameState gameState, 
                                       PopulationManager populationManager, TeamUiPort uiPort) {
                                        
        Iterator<AbstractThreat> threatIterator = threats.iterator();
        while (threatIterator.hasNext()) {
            AbstractThreat threat = threatIterator.next();
            boolean threatDestroyed = false;

            // 1. פגיעה בקרקע / מבנים / מערכות הגנה
            for (Damageable damageable : damageables) {
                if (damageable.checkHit(threat.getX(), threat.getY())) {
                    String targetName = (damageable instanceof GroundAsset) ? ((GroundAsset) damageable).getName() : "Defense System";
                    System.out.println("[LOG] Threat " + threat.getId() + " hit " + targetName + "!");
                    
                    damageable.tookHit();
                    if (damageable instanceof GroundAsset) {
                        if (populationManager.isBuildingPopulated((GroundAsset) damageable)) {
                            uiPort.playScreamSound();
                        }
                        populationManager.notifyBuildingHit((GroundAsset) damageable);

                    }
                    
                    uiPort.removeEntity(threat.getId());
                    uiPort.triggerExplosion(threat.getX(), threat.getY());
                    uiPort.playExplosionSound();
                    uiPort.updateScore(gameState.getScore());
                    
                    threatIterator.remove();
                    threatDestroyed = true;
                    threatLaserContactTime.remove(threat.getId());
                    break;
                }
            }
            if (threatDestroyed) continue;

            // 2. פגיעה ממיירטים (לייזר או טילים)
            boolean hitByLaserThisTick = false;
            Iterator<DefenseEntity> interceptorIterator = interceptors.iterator();
            while (interceptorIterator.hasNext()) {
                DefenseEntity interceptor = interceptorIterator.next();

                // טיפול בלייזר (LightShield)
                if (interceptor instanceof LightShield) {
                    LightShield laser = (LightShield) interceptor;
                    if (!laser.isActive()) {
                        interceptorIterator.remove();
                        continue;
                    }
                    
                    if (laser.intersects(threat)) {
                        hitByLaserThisTick = true;
                        double contactTime = threatLaserContactTime.getOrDefault(threat.getId(), 0.0) + timeStep;
                        threatLaserContactTime.put(threat.getId(), contactTime);
                        
                        if (contactTime >= 0.05) { // הלייזר חזק ומהיר יותר
                            System.out.println("[LOG] Threat " + threat.getId() + " destroyed by Laser.");
                            gameState.updateScore(10);
                            threatIterator.remove();
                            uiPort.updateScore(gameState.getScore());
                            uiPort.triggerExplosion(
                                (int) (threat.getX() + threat.getLength() / 2.0),
                                (int) (threat.getY() + threat.getHeight() / 2.0)
                            );
                            uiPort.playInterceptSound();
                            threatDestroyed = true;
                            threatLaserContactTime.remove(threat.getId());
                            break;
                        }
                    }
                    continue; // LightShields לא נמחקים כמו טילים רגילים
                }

                // טיפול בטילים רגילים
                if (!interceptor.isActive()) {
                    interceptorIterator.remove();
                    continue;
                }

                double dx = threat.getX() - interceptor.getX();
                double dy = threat.getY() - interceptor.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance < 50) {
                    System.out.println("[LOG] Threat " + threat.getId() + " intercepted by Missile.");
                    gameState.updateScore(10);
                    interceptor.explode();
                    interceptorIterator.remove();
                    threatIterator.remove();
                    uiPort.updateScore(gameState.getScore());
                    uiPort.triggerExplosion(
                        (threat.getX() + interceptor.getX()) / 2,
                        (threat.getY() + interceptor.getY()) / 2
                    );
                    uiPort.playInterceptSound();
                    threatDestroyed = true;
                    threatLaserContactTime.remove(threat.getId());
                    break;
                }
            }
            if (!threatDestroyed && !hitByLaserThisTick) {
                threatLaserContactTime.remove(threat.getId());
            }
        }
    }

    private void checkGiftCollisions(List<Gift> gifts, List<DefenseEntity> interceptors, List<Damageable> damageables, 
                                     GameState gameState, AssetSpawner assetSpawner, TeamUiPort uiPort) {
                                        
        Iterator<Gift> giftIterator = gifts.iterator();
        while (giftIterator.hasNext()) {
            Gift gift = giftIterator.next();
            boolean giftCollected = false;

            Iterator<DefenseEntity> interceptorIterator = interceptors.iterator();
            while (interceptorIterator.hasNext()) {
                DefenseEntity interceptor = interceptorIterator.next();

                if (!interceptor.isActive()) {
                    interceptorIterator.remove(); 
                    continue;
                }

                boolean isHit = false;
                if (interceptor instanceof LightShield) {
                    isHit = ((LightShield) interceptor).intersects(gift);
                } else {
                    double dx = gift.getX() - interceptor.getX();
                    double dy = gift.getY() - interceptor.getY();
                    isHit = Math.sqrt(dx * dx + dy * dy) < 50;
                }

                if (isHit) {
                    System.out.println("[LOG] Gift collected! Type: " + gift.getGiftType());
                    
                    // הפעלת האפקט של המתנה
                    applyGiftEffect(gift, interceptor, damageables, gameState, assetSpawner, uiPort);

                    // הפעלת אפקטים ויזואליים וניקוד של עצם האיסוף
                    uiPort.triggerExplosion(
                        (int) ((gift.getX() + interceptor.getX()) / 2),
                        (int) ((gift.getY() + interceptor.getY()) / 2)
                    );
                    uiPort.playInterceptSound();

                    // מחיקת האובייקטים
                    if (interceptor instanceof InterceptorMissile) {
                        interceptor.explode();
                        interceptorIterator.remove(); 
                    }

                    giftIterator.remove();
                    giftCollected = true;
                    break; 
                }
            }
        }
    }

    private void applyGiftEffect(Gift gift, DefenseEntity interceptor, List<Damageable> damageables, 
                                 GameState gameState, AssetSpawner assetSpawner, TeamUiPort uiPort) {
        switch (gift.getGiftType()) {
            case NEW_BATTERY:
                List<Damageable> newDefense = assetSpawner.spawnDefenseSystems(gameState.getLevel(), gameState.getGroundY());
                // Check if a battery was successfully created
                if (!newDefense.isEmpty()) {
                    // Safely add exactly one battery as requested
                    damageables.add(newDefense.get(0));
                    uiPort.showGiftCollected("Reinforcements!\nNew Battery Deployed");
                    System.out.println("[INFO] New battery successfully deployed from gift.");
                    break; 
                }
                
                // Log capacity limit and fall back to ammo refill
                System.out.println("[WARN] Screen full for new battery. Triggering ammo refill fallback.");
                executeAmmoRefill(interceptor, uiPort, damageables);
                break;

            case AMMO_REFILL:
            {
                executeAmmoRefill(interceptor, uiPort, damageables);
                break;
            }

            case BATTERY_REPAIR:
                for (Damageable d : damageables) {
                    if (d instanceof AbstractDefenseSystem) {
                        AbstractDefenseSystem defense = (AbstractDefenseSystem) d;
                        if (!defense.isActive()) {
                            defense.repair();
                            uiPort.showGiftCollected("Reinforcements! \n Battery repaired");
                            break; 
                        }
                    }
                }
                break;
        }
    }

    private void executeAmmoRefill(DefenseEntity interceptor, TeamUiPort uiPort, List<Damageable> damageables) {
    try {
        if (interceptor == null) {
            System.err.println("[ERROR] Cannot refill ammo: interceptor reference is null.");
            return;
        }

        int sourceId = interceptor.getSourceBatteryId();
        AbstractDefenseSystem shooterSystem = findDefenseSystemById(sourceId, damageables);
        
        if (shooterSystem instanceof InterceptorBattery) {
            InterceptorBattery b = (InterceptorBattery) shooterSystem;
            int numMissiles = 20 + random.nextInt(9) * 5;
            b.setMissilesAvailable(b.getMissilesAvailable() + numMissiles);
            uiPort.showGiftCollected("Ammo Secured!\n+" + numMissiles + " Missiles");
            System.out.println("[INFO] Interceptor battery " + sourceId + " refilled with " + numMissiles + " missiles.");
            
        } else if (shooterSystem instanceof LaserBattery) {
            LaserBattery b = (LaserBattery) shooterSystem;
            int numCharges = 15 + random.nextInt(4) * 5;
            b.setLaserChargesAvailable(b.getLaserChargesAvailable() + numCharges);
            uiPort.showGiftCollected("Ammo Secured!\n+" + numCharges + " Laser Charges");
            System.out.println("[INFO] Laser battery " + sourceId + " refilled with " + numCharges + " charges.");
        }
    } catch (Exception e) {
        System.err.println("[ERROR] Exception caught during ammo refill execution: " + e.getMessage());
    }
}

    private AbstractDefenseSystem findDefenseSystemById(int id, List<Damageable> damageables) {
        for (Damageable system : damageables) {
            if (system instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) system).getId() == id) {
                return (AbstractDefenseSystem) system;
            }
        }
        return null;
    }
}