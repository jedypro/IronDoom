package team.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import my_base.App;
import shared.ui_ports.TeamUiPort;

public class TeamBackend {

    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<DefenseEntity> activeInterceptors = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private GameState gameState = new GameState(300, 1, true);
    private ThreatSpawner spawner;
    private AssetSpawner assetSpawner;

    private double timeSinceLastBarrage = 0;
    private double timeSinceWarning = 0;
    private boolean barrageWarningScheduled = false;
    private int pendingBarrageSize = 0;
    private double levelElapsedTime = 0;
    private boolean levelCompleted = false;

    private static final double BARRAGE_INTERVAL_BASE_SECONDS = 40;
    private static final double BARRAGE_WARNING_DELAY_SECONDS = 3.0;
    private static final int MIN_BARRAGE_SIZE = 5;
    private static final int MAX_BARRAGE_SIZE = 8;
    private static final double BASE_LEVEL_DURATION_SECONDS = 10.0;
    private static final double LEVEL_DURATION_INCREMENT_SECONDS = 8.0;
    
    // Barrage warning system
    private long lastWarningTime = 0;
    private static final long WARNING_COOLDOWN_MS = 5000; // Only warn once every 5 seconds
    private static final int BARRAGE_THRESHOLD = 6; // Warn when 6+ threats are active

    /**
     * Use ex3UiPort() as a function and not a variable to get the UI port
     * to avoid trying to get it before it was set up by the UI
     * (which happens at UI startup, but this backend is constructed at app
     * startup).
     */
    private TeamUiPort teamUiPort() {
        return TeamUiPort.getInstance();
    }
    private void threatsRegister(){
        this.spawner = new ThreatSpawner(this.gameState);
    
        // רישום: טיל בליסטי
        spawner.registerThreatType((id) -> {
            int level = this.gameState.getLevel();
            int startX = -200; // Spawn off-screen to the left
            int startY = ThreadLocalRandom.current().nextInt(0, 200);

            double vxK = 1.4 - (0.4 / level);
            double vyK = 2.0 - (1.0 / level);
            int randomVx = ThreadLocalRandom.current().nextInt((int)(140 * vxK), (int)(550 * vxK));
            int randomVy = ThreadLocalRandom.current().nextInt((int)(5 * vyK), (int)(16 * vyK));

            int length = ThreadLocalRandom.current().nextInt(10, 15);
            int height = ThreadLocalRandom.current().nextInt(5, 9);
    // הגרלה: 50% סיכוי למסלול גלי, 50% למסלול בליסטי רגיל - אך ורק משלב 3 ומעלה
            MovementStrategy strategy;
            if (level >= 3 && ThreadLocalRandom.current().nextInt(7) == 0) {
                strategy = new WavyMovementStrategy();
            } else {
                strategy = new BallisticMovementStrategy();
            }
            return new BallisticMissile(id, startX, startY, randomVx, randomVy, length, height, this.gameState.getLevel(), strategy);        });

        // רישום: כטב"ם (UAV)
        spawner.registerThreatType((id) -> {
            int level = this.gameState.getLevel();
            
            // Spawn on left edge at random altitude
            int startX = -200;
            int startY = ThreadLocalRandom.current().nextInt(50, 400);

            // Initial velocities (overridden by strategy)
            int initialVx = 0;
            int initialVy = 0;

            // Randomize dimensions
            int length = ThreadLocalRandom.current().nextInt(15, 25);
            int height = ThreadLocalRandom.current().nextInt(8, 12);

            // Generate initial random target coordinates
            int targetX = ThreadLocalRandom.current().nextInt(200, 1600);
            int targetY = this.gameState.getGroundY();

            // Scale cruising speed based on difficulty
            double cruisingSpeed = 100.0 + (level * 25.0);

            // Initialize the flight strategy
            PoweredFlightStrategy strategy = new PoweredFlightStrategy(targetX, targetY, cruisingSpeed);

            // Return the instantiated UAV
            return new UAV(id, startX, startY, initialVx, initialVy, length, height, level, strategy, level);
        });

    }
    private void assetsRegister() {
        this.assetSpawner = new AssetSpawner();

        // רישום נכסים רגילים
        assetSpawner.registerRegularAsset((id, x, groundY) ->
            new GroundAsset(id, "City " + id, x, groundY - 80, 150, 80, this.gameState)
        );

        // רישום מערכות הגנה
        assetSpawner.registerDefenseSystem((id, x, groundY) ->
            new InterceptorBattery(id, x, groundY - 50)
        );
        // רישום מערכות הגנה - לייזר (רק משלב 3 ומעלה, ומקסימום 1)
        if (this.gameState.getLevel() >= 3) {
            assetSpawner.registerDefenseSystem((id, x, groundY) ->
                new LaserBattery(id, x, groundY - 50)
            , 1);
        }
    }
    // Called once at UI startup
    public void start() {
        teamUiPort().log("Logging: TeamBackend started");
        resetLevelTimer();
        resetBarrageTimer();
        initializeWorld();
        publishScene();
    }

    public void resetGame() {
        threats.clear();
        activeInterceptors.clear();
        damageables.clear();
        gameState = new GameState(300, 1, true);
        resetLevelTimer();
        resetBarrageTimer();
        initializeWorld();
        App.getPeriodicLoop().setPaused(false);
        publishScene();
    }

    public void nextLevel() {
        gameState.setLevel(gameState.getLevel() + 1);
        resetLevelTimer();
        resetBarrageTimer();
        // Clear active threats and interceptors so the next level starts clean
        this.threats.clear();
        this.activeInterceptors.clear();

        // Recreate spawner so spawn interval is recalculated for the new level AND register threat types
        threatsRegister();
        // Give a short safe period before new threats spawn (3 seconds)
        final double POST_LEVEL_SPAWN_DELAY_SECONDS = 0.0;
        this.spawner.setTimeSinceLastSpawn(-POST_LEVEL_SPAWN_DELAY_SECONDS);

        this.damageables.clear();
        assetsRegister();
        int level = gameState.getLevel();
        int groundY = gameState.getGroundY();
        this.damageables.addAll(assetSpawner.spawnDefenseSystems(level, groundY));
        this.damageables.addAll(assetSpawner.spawnRegularAssets(level, groundY));

        App.getPeriodicLoop().setPaused(false);
        publishScene();
    }

    private void resetLevelTimer() {
        levelElapsedTime = 0;
        levelCompleted = false;
    }

    private void resetBarrageTimer() {
        timeSinceLastBarrage = 0;
        timeSinceWarning = 0;
        barrageWarningScheduled = false;
        pendingBarrageSize = 0;
    }

    private void initializeWorld() {
        threatsRegister();
        assetsRegister();

        int level = gameState.getLevel();
        int groundY = gameState.getGroundY();

        // קריאה לשתי פונקציות הנפרדות
        damageables.addAll(assetSpawner.spawnDefenseSystems(level, groundY));
        damageables.addAll(assetSpawner.spawnRegularAssets(level, groundY));
    }

    public java.util.List<AbstractThreat> getThreats() {
        return Collections.unmodifiableList(threats);
    }

    public java.util.List<Damageable> getDamageables() {
        return Collections.unmodifiableList(damageables);
    }

    public java.util.List<DefenseEntity> getInterceptors() {
        return Collections.unmodifiableList(activeInterceptors);
    }

    public GameState getGameState() {
        return gameState;
    }

    // UI input events call these via router
    public void doStep(double timeStep) {
        updateThreatPositions(timeStep);
        updateInterceptorPositions(timeStep);

        // בדיקה האם הזמן המוקצב לשלב כבר חלף
        double levelDuration = BASE_LEVEL_DURATION_SECONDS + (LEVEL_DURATION_INCREMENT_SECONDS * gameState.getLevel());
        boolean isTimeUp = (levelElapsedTime >= levelDuration);

        // ייצור איומים וגלים חדשים יקרה אך ורק אם הזמן של השלב עדיין לא נגמר
        if (!isTimeUp) {
            AbstractThreat newThreat = spawner.spawnThreat(timeStep / 2);
            if (newThreat != null) {
                threats.add(newThreat);
            }
            advanceBarrageTimers(timeStep);
            checkBarrage();
        }

        advanceLevelTimer(timeStep);
        
        if (levelCompleted) {
            return;
        }
        
        checkCollisions(timeStep);

        //  אם אף אמצעי הגנה לא מסוגל לירות יותר - המשחק נגמר מיד בהפסד
        if (!canAnyDefenseSystemFire()) {
            gameState.setStatus(false);
        }

        publishScene();
    }

    private void advanceBarrageTimers(double timeStep) {
        if (gameState.getLevel() <= 1) {
            // Level 1 has no barrages.
            resetBarrageTimer();
            return;
        }

        if (barrageWarningScheduled) {
            timeSinceWarning += timeStep;
            if (timeSinceWarning >= BARRAGE_WARNING_DELAY_SECONDS) {
                timeSinceWarning = 0;
                barrageWarningScheduled = false;
                timeSinceLastBarrage = 0;
                teamUiPort().showWarning("Barrage incoming now: " + pendingBarrageSize + " threats!");

                for (int i = 0; i < pendingBarrageSize; i++) {
                    AbstractThreat newThreat = spawner.createRandomThreat();
                    if (newThreat != null) {
                        threats.add(newThreat);
                    }
                }
            }
            return;
        }

        double interval = barrageIntervalForLevel(gameState.getLevel());
        timeSinceLastBarrage += timeStep;
        if (timeSinceLastBarrage >= interval) {
            timeSinceLastBarrage = interval;
            pendingBarrageSize = ThreadLocalRandom.current().nextInt(MIN_BARRAGE_SIZE, MAX_BARRAGE_SIZE + 1);
            barrageWarningScheduled = true;
            timeSinceWarning = 0;
            teamUiPort().showWarning("Barrage warning: " + pendingBarrageSize + " threats in 3 seconds!");
        }
    }

    private double barrageIntervalForLevel(int level) {
        return BARRAGE_INTERVAL_BASE_SECONDS / level;
    }

    private void advanceLevelTimer(double timeStep) {
        if (levelCompleted || !gameState.isStatus()) {
            return;
        }

        levelElapsedTime += timeStep;
        double levelDuration = BASE_LEVEL_DURATION_SECONDS + (LEVEL_DURATION_INCREMENT_SECONDS * gameState.getLevel());
        if (levelElapsedTime >= levelDuration&& threats.isEmpty()) {
            levelCompleted = true;
            teamUiPort().showLevelComplete("Level " + gameState.getLevel() + " complete!");
            App.getPeriodicLoop().setPaused(true);
        }
    }

    private void publishScene() {
        teamUiPort().updateLevel(gameState.getLevel());
        teamUiPort().displayScene(getThreats(), getDamageables(), getInterceptors(), gameState.getScore(), gameState.isStatus());
    }

    private void updateThreatPositions(double timeStep) {
        Iterator<AbstractThreat> threatIterator = threats.iterator();
        while (threatIterator.hasNext()) {
            AbstractThreat threat = threatIterator.next();
            threat.updateTrajectory(timeStep);

            if (threat.getY() + threat.getHeight() >= gameState.getGroundY()) {
                teamUiPort().removeEntity(threat.getId());
                teamUiPort().triggerExplosion(threat.getX(), threat.getY());
                threatIterator.remove();
            }
        }
    }

    private void updateInterceptorPositions(double timeStep) {
        Iterator<DefenseEntity> interceptorIterator = activeInterceptors.iterator();
        while (interceptorIterator.hasNext()) {
            DefenseEntity interceptor = interceptorIterator.next();
            interceptor.updatePosition(timeStep);

            // Only explode once the missile exits the visible screen with margin
            // World width is 1200, so add buffer before exploding
            final int WORLD_WIDTH = 1200;
            final int EXIT_MARGIN = 350; // Buffer before missile fully disappears
            
            if (!interceptor.isActive() || 
                interceptor.getY() < -EXIT_MARGIN || 
                interceptor.getX() < -EXIT_MARGIN || 
                interceptor.getX() > WORLD_WIDTH + EXIT_MARGIN) {
                interceptor.explode();
                interceptorIterator.remove();
            }
        }
    }

    private Map<Integer, Double> threatLaserContactTime = new java.util.HashMap<>();

    private void checkCollisions(double timeStep) {
        Iterator<AbstractThreat> threatIterator = threats.iterator();
        while (threatIterator.hasNext()) {
            AbstractThreat threat = threatIterator.next();
            boolean threatDestroyed = false;

            for (Damageable damageable : damageables) {
                if (damageable.checkHit(threat.getX(), threat.getY())) {
                    damageable.tookHit();
                    teamUiPort().removeEntity(threat.getId());
                    teamUiPort().triggerExplosion(threat.getX(), threat.getY());
                    teamUiPort().updateScore(gameState.getScore());
                    threatIterator.remove();
                    threatDestroyed = true;
                    threatLaserContactTime.remove(threat.getId());
                    break;
                }
            }
            if (threatDestroyed) continue;

            boolean hitByLaserThisTick = false;
            Iterator<DefenseEntity> interceptorIterator = activeInterceptors.iterator();
            while (interceptorIterator.hasNext()) {
                DefenseEntity interceptor = interceptorIterator.next();

                // Handle LightShield collisions
                if (interceptor instanceof LightShield) {
                    LightShield laser = (LightShield) interceptor;
                    if (!laser.isActive()) { // Laser might have expired
                        interceptorIterator.remove();
                        continue;
                    }
                    if (laser.intersects(threat)) {
                        hitByLaserThisTick = true;
                        double contactTime = threatLaserContactTime.getOrDefault(threat.getId(), 0.0) + timeStep;
                        threatLaserContactTime.put(threat.getId(), contactTime);
                        
                        if (contactTime >= 0.1) {
                            gameState.updateScore(10); // Score for hitting a threat with laser
                            threatIterator.remove();
                            teamUiPort().updateScore(gameState.getScore());
                            teamUiPort().triggerExplosion(
                                (int) (threat.getX() + threat.getLength() / 2.0),
                                (int) (threat.getY() + threat.getHeight() / 2.0)
                            );
                            threatDestroyed = true;
                            threatLaserContactTime.remove(threat.getId());
                            break; // Stop checking this threat, it's destroyed
                        }
                    }
                    continue; // LightShields don't have traditional "active" state for removal like missiles
                }

                if (!interceptor.isActive()) {
                    interceptorIterator.remove();
                    continue;
                }

                double dx = threat.getX() - interceptor.getX();
                double dy = threat.getY() - interceptor.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance < 50) {
                    gameState.updateScore(10);
                    interceptor.explode();
                    interceptorIterator.remove();
                    threatIterator.remove();
                    teamUiPort().updateScore(gameState.getScore());
                    teamUiPort().triggerExplosion(
                        (threat.getX() + interceptor.getX()) / 2,
                        (threat.getY() + interceptor.getY()) / 2
                    );
                    threatDestroyed = true;
                    threatLaserContactTime.remove(threat.getId());
                    break;
                }
            }
            if (!threatDestroyed && !hitByLaserThisTick) {
                // Decay the contact time if not hit by a laser this tick?
                // Or just reset it? The instruction says it needs 0.3 sec to destroy. Let's reset it if it's not hit.
                threatLaserContactTime.remove(threat.getId());
            }
        }
    }

    private void checkBarrage() {
        if (threats.size() >= BARRAGE_THRESHOLD) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastWarningTime > WARNING_COOLDOWN_MS) {
                lastWarningTime = currentTime;
                
                // Determine warning message based on threat types
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
                
                teamUiPort().showWarning(warningMsg);
            }
        }
    }

    private InterceptorBattery findBatteryById(int id) {
    for (Damageable system : damageables) {
        if (system instanceof InterceptorBattery && ((InterceptorBattery) system).getId() == id) {
            return (InterceptorBattery) system;
        }
    }
    return null;
}

   private AbstractDefenseSystem findDefenseSystemById(int id) {
    for (Damageable system : damageables) {
        if (system instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) system).getId() == id) {
            return (AbstractDefenseSystem) system;
        }
    }
    return null;
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
    
    public void updateSettings(int newLevel) {
        this.gameState.setLevel(newLevel);
        resetLevelTimer();
        resetBarrageTimer();
        
        // Recreate spawner with new level so spawn interval updates and threat types are registered
        threatsRegister();
        
        this.damageables.clear();
        assetsRegister();
        
        // הגרלה מחודשת של נכסים לפי הרמה החדשה
        int groundY = gameState.getGroundY();
        this.damageables.addAll(assetSpawner.spawnDefenseSystems(newLevel, groundY));
        this.damageables.addAll(assetSpawner.spawnRegularAssets(newLevel, groundY));

        publishScene();
    }
    //נרצה לבדוק האם יש לפחות מערכת אחת שיכולה לירות (פעילה ויש לה תחמושת)
    private boolean canAnyDefenseSystemFire() {
        for (Damageable d : damageables) {
            if (d instanceof AbstractDefenseSystem) {
                AbstractDefenseSystem system = (AbstractDefenseSystem) d;
                // בדיקה האם המערכת פעילה ולא הושמדה
                if (system.isActive()) {
                    // אם זו סוללת טילים - נבדוק שיש טילים
                    if (system instanceof InterceptorBattery) {
                        if (((InterceptorBattery) system).getMissilesAvailable() > 0) {
                            return true;
                        }
                    // אם זו סוללת לייזר - נבדוק שיש טעינות
                    } else if (system instanceof LaserBattery) {
                        if (((LaserBattery) system).getLaserChargesAvailable() > 0) {
                            return true;
                        }
                    }
                }
            }
        }
        // אם עברנו על כל המערכות ואף אחת לא יכולה לירות
        return false;
    }

}
