package team.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import shared.ui_ports.TeamUiPort;

public class TeamBackend {

    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<InterceptorMissile> activeInterceptors = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private GameState gameState = new GameState(100, 1, true);
    private ThreatSpawner spawner;

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
            int startX = 0;
            int startY = ThreadLocalRandom.current().nextInt(0, 200);

            double vxK = 1.4 - (0.4 / level);
            double vyK = 2.0 - (1.0 / level);
            int randomVx = ThreadLocalRandom.current().nextInt((int)(140 * vxK), (int)(550 * vxK));
            int randomVy = ThreadLocalRandom.current().nextInt((int)(5 * vyK), (int)(16 * vyK));

            int length = ThreadLocalRandom.current().nextInt(10, 15);
            int height = ThreadLocalRandom.current().nextInt(5, 9);
    
            return new BallisticMissile(id, startX, startY, randomVx, randomVy, length, height, this.gameState.getLevel(), new BallisticMovementStrategy());
        });
    }

    // Called once at UI startup
    public void start() {
        teamUiPort().log("Logging: TeamBackend started");
        initializeWorld();
        publishScene();
    }

    public void resetGame() {
        threats.clear();
        activeInterceptors.clear();
        damageables.clear();
        gameState = new GameState(100, 1, true);
        initializeWorld();
        publishScene();
    }

    private void initializeWorld() {
        threatsRegister();

        GroundAsset conference = new GroundAsset(1, "Conference", 1000, gameState.getGroundY() - 80, 150, 80, gameState);
        damageables.add(conference);

        InterceptorBattery battery = new InterceptorBattery(2, 900, 700);
        damageables.add(battery);
    }

    public java.util.List<AbstractThreat> getThreats() {
        return Collections.unmodifiableList(threats);
    }

    public java.util.List<Damageable> getDamageables() {
        return Collections.unmodifiableList(damageables);
    }

    public java.util.List<InterceptorMissile> getInterceptors() {
        return Collections.unmodifiableList(activeInterceptors);
    }

    public GameState getGameState() {
        return gameState;
    }

    // UI input events call these via router
    public void doStep(double timeStep) {
        //System.out.println("In TeamBackend doStep, timeStep=" + timeStep + " ...");
        updateThreatPositions(timeStep);
        updateInterceptorPositions(timeStep);

        AbstractThreat newThreat = spawner.spawnThreat(timeStep/2);
        if (newThreat != null) {
            threats.add(newThreat);
        }
        
        checkCollisions();
        publishScene();
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
        Iterator<InterceptorMissile> interceptorIterator = activeInterceptors.iterator();
        while (interceptorIterator.hasNext()) {
            InterceptorMissile interceptor = interceptorIterator.next();
            interceptor.updatePosition(timeStep);

            if (!interceptor.isActive() || interceptor.getY() < 0 || interceptor.getX() < 0 || interceptor.getX() > 1920) {
                interceptor.explode();
                interceptorIterator.remove();
            }
        }
    }

    private void checkCollisions() {
        Iterator<AbstractThreat> threatIterator = threats.iterator();
        while (threatIterator.hasNext()) {
            AbstractThreat threat = threatIterator.next();

            for (Damageable damageable : damageables) {
                if (damageable.checkHit(threat.getX(), threat.getY())) {
                    damageable.tookHit();
                    teamUiPort().removeEntity(threat.getId());
                    teamUiPort().triggerExplosion(threat.getX(), threat.getY());
                    teamUiPort().updateScore(gameState.getScore());
                    threatIterator.remove();
                    return;
                }
            }

            Iterator<InterceptorMissile> interceptorIterator = activeInterceptors.iterator();
            while (interceptorIterator.hasNext()) {
                InterceptorMissile interceptor = interceptorIterator.next();
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
                    return;
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

   public void launchInterceptor(int batteryId, double angle, double power) {
    InterceptorBattery battery = findBatteryById(batteryId); 
    
    if (battery != null) {
        
        InterceptorMissile newMissile = battery.attemptDefense(angle, power);     
        if (newMissile != null) {
            this.activeInterceptors.add(newMissile);
    }
    }
}

    public void updateSettings(int newLevel) {
        this.gameState.setLevel(newLevel);
        publishScene();
    }

}