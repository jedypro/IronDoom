package team.domain;
import java.util.Random;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import db.ExcelDB;
import db.ExcelTable;
import my_base.App;
import shared.ui_ports.TeamUiPort;

public class TeamBackend {

    private static final int WORLD_WIDTH = 1200;
    private static final int EXIT_MARGIN = 350;

    private static final int DEFAULT_SCORE = 300;

    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<DefenseEntity> activeInterceptors = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private final List<Gift> activeGifts = new ArrayList<>(); //for endless mode
    private final Map<Integer, Double> threatLaserContactTime = new java.util.HashMap<>();


    private GameState gameState= new GameState(DEFAULT_SCORE, 1, true);
    private ThreatSpawner spawner;
    private AssetSpawner assetSpawner;
    private PopulationManager populationManager = new PopulationManager();
    private GiftSpawner giftSpawner = new GiftSpawner(WORLD_WIDTH); 

    private ExcelTable events;
    private String[][] rows;
    private final List<EventAction> goodActions = new ArrayList<>();
    private final List<EventAction> badActions = new ArrayList<>();

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
    //getters
    public java.util.List<AbstractThreat> getThreats() {
        return Collections.unmodifiableList(threats);
    }

    public java.util.List<Damageable> getDamageables() {
        return Collections.unmodifiableList(damageables);
    }

    public java.util.List<DefenseEntity> getInterceptors() {
        return Collections.unmodifiableList(activeInterceptors);
    }

    public java.util.List<Gift> getGifts() {
        return Collections.unmodifiableList(activeGifts);
    }
    public GameState getGameState() {
        return gameState;
    }
    private TeamUiPort teamUiPort() {
        return TeamUiPort.getInstance();
    }

    //registering threats and assets
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
        if (this.gameState.getLevel() >= 2) {
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

    }
    private void assetsRegister() {
        this.assetSpawner = new AssetSpawner();

        // רישום נכסים רגילים
        // 1. רישום מתכון לעיר (פרופורציות רגילות, גודל משתנה מעט)
        assetSpawner.registerRegularAsset((id, x, groundY) -> {
            int width = ThreadLocalRandom.current().nextInt(130, 170);
            int height = ThreadLocalRandom.current().nextInt(70, 90);
            return new GroundAsset(id, "City " + id, x, groundY - height, width, height, this.gameState);
        });

        // 2. רישום מתכון למפעל (צר וגבוה יותר)
        if (this.gameState.getLevel() >= 2) {
            assetSpawner.registerRegularAsset((id, x, groundY) -> {
                int width = ThreadLocalRandom.current().nextInt(90, 120);
                int height = ThreadLocalRandom.current().nextInt(90, 130);
                return new GroundAsset(id, "Factory " + id, x, groundY - height, width, height, this.gameState);
            });
        }

        // 3. רישום מתכון לבסיס צבאי (רחב מאוד ונמוך)
        if (this.gameState.getLevel() >= 4) {
            assetSpawner.registerRegularAsset((id, x, groundY) -> {
                int width = ThreadLocalRandom.current().nextInt(180, 240);
                int height = ThreadLocalRandom.current().nextInt(40, 60);
                return new GroundAsset(id, "Military Base " + id, x, groundY - height, width, height, this.gameState);
            });
        }

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

    //register events
    private void eventsRegister() {
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
            teamUiPort().updateScore(gameState.getScore());
            return "+300 Bonus Points!";
        });

        goodActions.add(() -> {
            if (threats.isEmpty()) return "No threats to clear.";
            threats.clear();
            teamUiPort().playExplosionSound();
            publishScene();
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

    // Called once at UI startup
    public void start() {
        teamUiPort().log("Logging: TeamBackend started");
        resetLevelTimer();
        resetBarrageTimer();
        initializeWorld();
        events = ExcelDB.getInstance().getTable("events");
        rows = events.getTableAsMatrix();
        publishScene();
    }

    private void initializeWorld() {
        threatsRegister();
        assetsRegister();
        eventsRegister();

        int level = gameState.getLevel();
        int groundY = gameState.getGroundY();

        // קריאה לשתי פונקציות הנפרדות
        damageables.addAll(assetSpawner.spawnDefenseSystems(level, groundY));
        damageables.addAll(assetSpawner.spawnRegularAssets(level, groundY));
    }

    //reset
    public void resetGame() {
        threats.clear();
        populationManager.reset();
        activeInterceptors.clear();
        damageables.clear();
        // Preserve the current difficulty level when resetting the game.
        int currentLevel = (this.gameState != null) ? this.gameState.getLevel() : 1;
        gameState = new GameState(DEFAULT_SCORE, currentLevel, true);
        resetLevelTimer();
        resetBarrageTimer();
        initializeWorld();
        App.getPeriodicLoop().setPaused(false);
        publishScene();
    }

    public void nextLevel() {
        gameState.setLevel(gameState.getLevel() + 1);
        populationManager.reset();
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

    //barrage system
    private void resetBarrageTimer() {
        timeSinceLastBarrage = 0;
        timeSinceWarning = 0;
        barrageWarningScheduled = false;
        pendingBarrageSize = 0;
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
                teamUiPort().playWarningSound();
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
    private double barrageIntervalForLevel(int level) {
        return BARRAGE_INTERVAL_BASE_SECONDS / level;
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

    private void advanceLevelTimer(double timeStep) {
        if (levelCompleted || !gameState.isStatus()) {
            return;
        }

        levelElapsedTime += timeStep;
        double levelDuration = BASE_LEVEL_DURATION_SECONDS + (LEVEL_DURATION_INCREMENT_SECONDS * gameState.getLevel());
        if (levelElapsedTime >= levelDuration&& threats.isEmpty()) {
            if(gameState.isEndlessMode()) {
                gameState.advanceLevel();
                resetLevelTimer();
                resetBarrageTimer();
                threatsRegister();
                Gift newGift = giftSpawner.spawnGift();
                activeGifts.add(newGift);
                
            
                publishScene();
            } else {
            levelCompleted = true;
            teamUiPort().showLevelComplete("Level " + gameState.getLevel() + " complete!");
            teamUiPort().playLevelCompleteSound();
            App.getPeriodicLoop().setPaused(true);
            }
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
        for (Gift gift : activeGifts) {
            gift.updatePosition(timeStep);
        }
    }

    private void checkCollisions(double timeStep) {
        Iterator<AbstractThreat> threatIterator = threats.iterator();
        while (threatIterator.hasNext()) {
            AbstractThreat threat = threatIterator.next();
            boolean threatDestroyed = false;

            for (Damageable damageable : damageables) {
                if (damageable.checkHit(threat.getX(), threat.getY())) {
                    damageable.tookHit();
                    if (damageable instanceof GroundAsset) {
                        populationManager.notifyBuildingHit((GroundAsset) damageable);
                    }
                    teamUiPort().removeEntity(threat.getId());
                    teamUiPort().triggerExplosion(threat.getX(), threat.getY());
                    teamUiPort().playExplosionSound();
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
                            teamUiPort().playInterceptSound();
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
                    teamUiPort().playInterceptSound();
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

          

        // --- הוספה: לולאת בדיקת חיתוכים עבור איסוף מתנות ---
        Iterator<Gift> giftIterator = activeGifts.iterator();
        while (giftIterator.hasNext()) {
            Gift gift = giftIterator.next();
            boolean giftCollected = false;

            Iterator<DefenseEntity> interceptorIterator = activeInterceptors.iterator();
            while (interceptorIterator.hasNext()) {
                DefenseEntity interceptor = interceptorIterator.next();

                // מתעלמים ממיירטים שכבר התפוצצו או סיימו את תפקידם
                if (!interceptor.isActive()) {
                    continue;
                }

                // חישוב מרחק בסיסי
                double dx = gift.getX() - interceptor.getX();
                double dy = gift.getY() - interceptor.getY();
                double distance = Math.sqrt(dx * dx + dy * dy);

                // אם המיירט פגע במתנה (רדיוס 50 פיקסלים)
                if (distance < 50) {
                    
                    // 1. זיהוי סוג המתנה וחלוקת הפרס
                    if (gift.getGiftType() == "NEW_BATTERY") {

                        List<Damageable> newDefense = assetSpawner.spawnDefenseSystems(gameState.getLevel(), gameState.getGroundY());
                        damageables.add(newDefense.get(0));
                        teamUiPort().showGameEvent("Reinforcements!", "New Battery Deployed", true);
                    } 
                    else if (gift.getGiftType() == "AMMO_REFILL") {
                        // אם מה שפגע במתנה הוא טיל (ולא מגן לייזר), ניתן תחמושת לסוללה הספציפית שירתה אותו
                        if (interceptor instanceof InterceptorMissile) {
                            int shooterId = ((InterceptorMissile) interceptor).getSourceBatteryId();
                            InterceptorBattery shooterBattery = findBatteryById(shooterId);
                            
                            if (shooterBattery != null) {
                                 Random random = new Random();
                                int numMissiles=  20 + random.nextInt(9) * 5;
                                shooterBattery.setMissilesAvailable(shooterBattery.getMissilesAvailable() + numMissiles);
                                teamUiPort().showGameEvent("Ammo Secured!", "+" + numMissiles + " Missiles", true);
                            }
                        }
                    }
                    else if (gift.getGiftType() == "BATTERY_REPAIR") {
                        for (Damageable d : damageables) {
                            if (d instanceof AbstractDefenseSystem && !((AbstractDefenseSystem) d).isActive()) {
                                ((AbstractDefenseSystem) d).repair();
                        
                                teamUiPort().showGameEvent("Reinforcements!", "Battery repaired", true);
                                break; // מתקן רק מערכת אחת פגועה, אז יוצאים מהלולאה אחרי שמצאנו אחת
                            }
                            
                        }
                    }
                    else if (gift.getGiftType() == "ADD_SCORE") {
                        Random random = new Random();
                        int score = random.nextInt(2, 4) * 100;
                        gameState.updateScore(score);
                        teamUiPort().updateScore(gameState.getScore());
                        teamUiPort().showGameEvent("Reinforcements!", score +" points added", true);
                    }
                    

                    // 2. הפעלת אפקטים ויזואליים וניקוד
                    gameState.updateScore(25); // קצת ניקוד בונוס על עצם האיסוף
                    teamUiPort().updateScore(gameState.getScore());
                    teamUiPort().triggerExplosion(
                        (int) ((gift.getX() + interceptor.getX()) / 2),
                        (int) ((gift.getY() + interceptor.getY()) / 2)
                    );
                    teamUiPort().playInterceptSound();

                    // 3. מחיקת האובייקטים מהמסך (כדי לא לאסוף את אותה מתנה פעמיים)
                    if (interceptor instanceof InterceptorMissile) {
                        interceptor.explode();
                        interceptorIterator.remove(); // הטיל מתפוצץ על המתנה ונעלם
                    }
                    // (אם זה לייזר שפגע במתנה, הוא לא יימחק וימשיך להאיר לפי הזמן שנשאר לו)

                    giftIterator.remove(); // מוחקים את המתנה עצמה
                    giftCollected = true;
                    break; // עוצרים את בדיקת המיירטים למתנה הזו ועוברים למתנה הבאה במסך
                }
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

    private void triggerRandomEvent() {
        if (rows == null || rows.length == 0) return;

        // Pick a random narrative row from Excel
        int rowIndex = ThreadLocalRandom.current().nextInt(rows.length);
        String description = rows[rowIndex][0];
        String type = rows[rowIndex][1].trim().toUpperCase();
        
        boolean isGood = "GOOD".equals(type);
        String resultText = "No effect";

        // Pick a random mechanical result based on the 'GOOD'/'BAD' tag
        if (isGood && !goodActions.isEmpty()) {
            int actionIndex = ThreadLocalRandom.current().nextInt(goodActions.size());
            resultText = goodActions.get(actionIndex).execute();
        } else if (!isGood && !badActions.isEmpty()) {
            int actionIndex = ThreadLocalRandom.current().nextInt(badActions.size());
            resultText = badActions.get(actionIndex).execute();
        }

        // Send to UI
        teamUiPort().showGameEvent(description, resultText, isGood);
    }

    // UI input events call these via router
    public void doStep(double timeStep) {
        updateThreatPositions(timeStep);
        updateInterceptorPositions(timeStep);
        updateGiftPositions(timeStep);

        populationManager.update(timeStep, gameState.getGroundY(), damageables);

        // בדיקה האם הזמן המוקצב לשלב כבר חלף
        double levelDuration = BASE_LEVEL_DURATION_SECONDS + (LEVEL_DURATION_INCREMENT_SECONDS * gameState.getLevel());
        boolean isTimeUp = (levelElapsedTime >= levelDuration);

        double eventProbabilityThisFrame = (1.0 / 60) * timeStep;
        if (Math.random() < eventProbabilityThisFrame) {
            triggerRandomEvent();
        }

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
        if (!canAnyDefenseSystemFire() || gameState.getScore() <= 0) {
            gameState.setStatus(false);
            App.getPeriodicLoop().setPaused(true); // עצירת הלולאה של המשחק
        }

        publishScene();
    }


}
