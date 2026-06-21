package team.domain.backend;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import db.ExcelDB;
import db.ExcelTable;
import my_base.App;
import shared.ui_ports.TeamUiPort;
import team.domain.*;


public class TeamBackend {

    private static final int WORLD_WIDTH = 1200;
    private static final int EXIT_MARGIN = 350;
    private static final int DEFAULT_SCORE = 300;


    // --- רשימות ונתונים ---
    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<DefenseEntity> activeInterceptors = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private final List<Gift> activeGifts = new ArrayList<>();
    private GameState gameState = new GameState(DEFAULT_SCORE, 1, true);

    // --- יוצרי אובייקטים (Spawners) ---
    private ThreatSpawner spawner;
    private AssetSpawner assetSpawner;
    private PopulationManager populationManager = new PopulationManager();
    private GiftSpawner giftSpawner = new GiftSpawner(WORLD_WIDTH);

    // --- מערכות ניהול (המנהלים החדשים שלנו!) ---
    private final LevelManager levelManager = new LevelManager();
    private final BarrageManager barrageManager = new BarrageManager();
    private final CollisionSystem collisionSystem = new CollisionSystem();
    private final RandomEventSystem eventSystem = new RandomEventSystem();

    private TeamUiPort teamUiPort() {
        return TeamUiPort.getInstance();
    }

    public void start() {
        teamUiPort().log("Logging: TeamBackend started");
        levelManager.reset();
        barrageManager.reset();
        initializeWorld();
        
        // אתחול מערכת האירועים הרנדומליים
        ExcelTable events = ExcelDB.getInstance().getTable("events");
        if (events != null) {
            String[][] rows = events.getTableAsMatrix();
            eventSystem.initialize(rows, damageables, gameState, threats, spawner, teamUiPort(), this::publishScene);
        }
        
        publishScene();
    }

    public void resetGame() {
        threats.clear();
        populationManager.reset();
        activeInterceptors.clear();
        damageables.clear();
        activeGifts.clear();
        
        int currentLevel = (this.gameState != null) ? this.gameState.getLevel() : 1;
        gameState = new GameState(300, currentLevel, true);
        
        levelManager.reset();
        barrageManager.reset();
        initializeWorld();
        
        App.getPeriodicLoop().setPaused(false);
        publishScene();
    }

    public void nextLevel() {
        gameState.setLevel(gameState.getLevel() + 1);
        populationManager.reset();
        levelManager.reset();
        barrageManager.reset();
        
        this.threats.clear();
        this.activeInterceptors.clear();
        this.activeGifts.clear();

        threatsRegister();
        this.spawner.setTimeSinceLastSpawn(0.0);

        this.damageables.clear();
        assetsRegister();
        int level = gameState.getLevel();
        int groundY = gameState.getGroundY();
        this.damageables.addAll(assetSpawner.spawnDefenseSystems(level, groundY));
        this.damageables.addAll(assetSpawner.spawnRegularAssets(level, groundY));

        App.getPeriodicLoop().setPaused(false);
        publishScene();
    }

    public void updateSettings(int newLevel) {
        this.gameState.setLevel(newLevel);
        levelManager.reset();
        barrageManager.reset();
        
        threatsRegister();
        
        this.damageables.clear();
        assetsRegister();
        
        int groundY = gameState.getGroundY();
        this.damageables.addAll(assetSpawner.spawnDefenseSystems(newLevel, groundY));
        this.damageables.addAll(assetSpawner.spawnRegularAssets(newLevel, groundY));

        publishScene();
    }

    private void initializeWorld() {
        threatsRegister();
        assetsRegister();

        int level = gameState.getLevel();
        int groundY = gameState.getGroundY();

        damageables.addAll(assetSpawner.spawnDefenseSystems(level, groundY));
        damageables.addAll(assetSpawner.spawnRegularAssets(level, groundY));
    }

    // --- הליבה: doStep נהיה קריא וברור ---
    public void doStep(double timeStep) {
        // 1. עדכון מיקומים
        updateThreatPositions(timeStep);
        updateInterceptorPositions(timeStep);
        updateGiftPositions(timeStep);
        populationManager.update(timeStep, gameState.getGroundY(), damageables);
        int numActiveDefenses = 0;
        for (Damageable d : damageables) {
            if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem)d).isActive()) {
                numActiveDefenses++;
            }
        // only allow disabling batteries if there are at least 2 active defenses
        eventSystem.maybeTrigger(timeStep, teamUiPort(), numActiveDefenses>=2);
        }

        // 3. ייצור איומים וגלים (רק אם הזמן לא נגמר)
        if (!levelManager.isTimeUp(gameState.getLevel())) {
                AbstractThreat newThreat = spawner.spawnThreat(timeStep / 2);
                if (newThreat != null) {
                    threats.add(newThreat);
                }
                barrageManager.advanceBarrageTimers(timeStep, gameState, threats, spawner, giftSpawner, activeGifts, damageables, teamUiPort());
                barrageManager.checkBarrage(threats, teamUiPort());
        }

        // 4. התקדמות זמן השלב ובדיקת ניצחון
        LevelManager.LevelState levelState = levelManager.advanceTime(timeStep, gameState, threats.isEmpty(), teamUiPort());
        if (levelState == LevelManager.LevelState.ENDLESS_NEXT_LEVEL) {
            threatsRegister();
            publishScene();
            return;
        } else if (levelState == LevelManager.LevelState.LEVEL_WON) {
            App.getPeriodicLoop().setPaused(true);
            return;
        }

        // 5. בדיקת התנגשויות
        if (!levelManager.isLevelCompleted()) {
            collisionSystem.checkCollisions(timeStep, threats, damageables, activeInterceptors, activeGifts, gameState, populationManager, assetSpawner, teamUiPort());
        }

        // 6. תנאי הפסד (אין תחמושת או ניקוד 0)
        if (!canAnyDefenseSystemFire() || gameState.getScore() <= 0) {
            if (gameState.isStatus()) {
                System.out.println("[LOG] Game Over! Out of ammo or score reached 0. Final score: " + gameState.getScore());
            }
            gameState.setStatus(false);
            App.getPeriodicLoop().setPaused(true);
        }

        // 7. עדכון מסך
        publishScene();
    }

    // --- פונקציות עזר ורישום (נשארו כמעט ללא שינוי כי הן קשורות להגדרות המשחק) ---
    private void threatsRegister() {
        this.spawner = new ThreatSpawner(this.gameState);
    
        spawner.registerThreatType((id) -> {
            int level = this.gameState.getLevel();
            int startX = -200; 
            int startY = ThreadLocalRandom.current().nextInt(0, 200);

            double vxK = 1.4 - (0.4 / level);
            double vyK = 2.0 - (1.0 / level);
            int randomVx = ThreadLocalRandom.current().nextInt((int)(140 * vxK), (int)(550 * vxK));
            int randomVy = ThreadLocalRandom.current().nextInt((int)(5 * vyK), (int)(16 * vyK));

            int length = ThreadLocalRandom.current().nextInt(10, 15);
            int height = ThreadLocalRandom.current().nextInt(5, 9);
            
            MovementStrategy strategy;
            if (level >= 3 && ThreadLocalRandom.current().nextInt(7) == 0) {
                strategy = new WavyMovementStrategy();
            } else {
                strategy = new BallisticMovementStrategy();
            }
            return new BallisticMissile(id, startX, startY, randomVx, randomVy, length, height, this.gameState.getLevel(), strategy);        
        });
        
        if (this.gameState.getLevel() >= 2) {
            spawner.registerThreatType((id) -> {
                int level = this.gameState.getLevel();
                int startX = -200;
                int startY = ThreadLocalRandom.current().nextInt(50, 400);
                int length = ThreadLocalRandom.current().nextInt(15, 25);
                int height = ThreadLocalRandom.current().nextInt(8, 12);
                int targetX = ThreadLocalRandom.current().nextInt(200, 1600);
                int targetY = this.gameState.getGroundY();
                double cruisingSpeed = 100.0 + (level * 25.0);

                PoweredFlightStrategy strategy = new PoweredFlightStrategy(targetX, targetY, cruisingSpeed);
                return new UAV(id, startX, startY, 0, 0, length, height, level, strategy, level);
            });
        }
    }

    private void assetsRegister() {
        this.assetSpawner = new AssetSpawner();

        assetSpawner.registerRegularAsset((id, x, groundY) -> {
            int width = ThreadLocalRandom.current().nextInt(130, 170);
            int height = ThreadLocalRandom.current().nextInt(70, 90);
            return new GroundAsset(id, "City " + id, x, groundY - height, width, height, this.gameState);
        });

        if (this.gameState.getLevel() >= 2) {
            assetSpawner.registerRegularAsset((id, x, groundY) -> {
                int width = ThreadLocalRandom.current().nextInt(90, 120);
                int height = ThreadLocalRandom.current().nextInt(90, 130);
                return new GroundAsset(id, "Factory " + id, x, groundY - height, width, height, this.gameState);
            });
        }

        if (this.gameState.getLevel() >= 4) {
            assetSpawner.registerRegularAsset((id, x, groundY) -> {
                int width = ThreadLocalRandom.current().nextInt(180, 240);
                int height = ThreadLocalRandom.current().nextInt(40, 60);
                return new GroundAsset(id, "Military Base " + id, x, groundY - height, width, height, this.gameState);
            });
        }

        assetSpawner.registerDefenseSystem((id, x, groundY) ->
            new InterceptorBattery(id, x, groundY - 50)
        );
        
        if (this.gameState.getLevel() >= 3) {
            assetSpawner.registerDefenseSystem((id, x, groundY) ->
                new LaserBattery(id, x, groundY - 50)
            , 1);
        }
    }

    private void publishScene() {
        teamUiPort().updateLevel(gameState.getLevel());
        teamUiPort().displayScene(getThreats(), getDamageables(), getInterceptors(), getGifts(), gameState.getScore(), gameState.isStatus());
        teamUiPort().displayCivilians(populationManager.getCivilians());
    }

    private void updateThreatPositions(double timeStep) {
        Iterator<AbstractThreat> threatIterator = threats.iterator();
        while (threatIterator.hasNext()) {
            AbstractThreat threat = threatIterator.next();
            threat.updateTrajectory(timeStep);

            if (threat.getY() + threat.getHeight() >= gameState.getGroundY()) {
                teamUiPort().removeEntity(threat.getId());
                teamUiPort().triggerExplosion(threat.getX(), threat.getY());
                teamUiPort().playExplosionSound();
                threatIterator.remove();
            }
        }
    }

    private void updateInterceptorPositions(double timeStep) {
        Iterator<DefenseEntity> interceptorIterator = activeInterceptors.iterator();
        while (interceptorIterator.hasNext()) {
            DefenseEntity interceptor = interceptorIterator.next();
            interceptor.updatePosition(timeStep);

            if (!interceptor.isActive() || 
                interceptor.getY() < -EXIT_MARGIN || 
                interceptor.getX() < -EXIT_MARGIN || 
                interceptor.getX() > WORLD_WIDTH + EXIT_MARGIN) {
                interceptor.explode();
                interceptorIterator.remove();
            }
        }
    }

    private void updateGiftPositions(double timeStep) {
        Iterator<Gift> giftIterator = activeGifts.iterator();
        while (giftIterator.hasNext()) {
            Gift gift = giftIterator.next();
            gift.updatePosition(timeStep);

            if (gift.getY() + gift.getHeight() >= gameState.getGroundY()) {
                giftIterator.remove();
            }
        }
    }

    public void launchDefense(int defenseSystemId, double angle, String defenseType) {
        AbstractDefenseSystem defenseSystem = findDefenseSystemById(defenseSystemId);
        
        if (defenseSystem != null) {
            TargetingParams params;
            DefenseEntity newDefense = null;

            if ("MISSILE".equalsIgnoreCase(defenseType) && defenseSystem instanceof InterceptorBattery) {
                params = new BallisticTargetingParams(angle);
                newDefense = defenseSystem.attemptDefense(params);
            } else if ("LASER".equalsIgnoreCase(defenseType) && defenseSystem instanceof LaserBattery) {
                params = new LaserTargetingParams(angle);
                newDefense = defenseSystem.attemptDefense(params);
            } else {
                System.err.println("Unknown defense type: " + defenseType);
                return;
            }

            if (newDefense != null) {
                this.activeInterceptors.add(newDefense);
            }
        }
    }

    public void updateAim(int defenseSystemId, double angle) {
        AbstractDefenseSystem system = findDefenseSystemById(defenseSystemId);
        if (system instanceof LaserBattery) {
            ((LaserBattery) system).setCurrentAimAngle(angle);
        }
    }

    private AbstractDefenseSystem findDefenseSystemById(int id) {
        for (Damageable system : damageables) {
            if (system instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) system).getId() == id) {
                return (AbstractDefenseSystem) system;
            }
        }
        return null;
    }

    private boolean canAnyDefenseSystemFire() {
        for (Damageable d : damageables) {
            if (d instanceof AbstractDefenseSystem) {
                AbstractDefenseSystem system = (AbstractDefenseSystem) d;
                if (system.isActive()) {
                    if (system instanceof InterceptorBattery) {
                        if (((InterceptorBattery) system).getMissilesAvailable() > 0) {
                            return true;
                        }
                    } else if (system instanceof LaserBattery) {
                        if (((LaserBattery) system).getLaserChargesAvailable() > 0) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }



    // --- Getters ---
    public java.util.List<AbstractThreat> getThreats() { return Collections.unmodifiableList(threats); }
    public java.util.List<Damageable> getDamageables() { return Collections.unmodifiableList(damageables); }
    public java.util.List<DefenseEntity> getInterceptors() { return Collections.unmodifiableList(activeInterceptors); }
    public java.util.List<Gift> getGifts() { return Collections.unmodifiableList(activeGifts); }
    public GameState getGameState() { return gameState; }
}