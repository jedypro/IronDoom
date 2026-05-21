package team.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import shared.ui_ports.TeamUiPort;

public class TeamBackend {

    private final List<AbstractThreat> threats = new ArrayList<>();
    private final List<Damageable> damageables = new ArrayList<>();
    private final GameState gameState = new GameState(100, 1, true);

    /**
     * Use ex3UiPort() as a function and not a variable to get the UI port
     * to avoid trying to get it before it was set up by the UI
     * (which happens at UI startup, but this backend is constructed at app
     * startup).
     */
    private TeamUiPort teamUiPort() {
        return TeamUiPort.getInstance();
    }

    // Called once at UI startup
    public void start() {
        System.out.println("TeamBackend started");
        teamUiPort().log("Logging: TeamBackend started");

        // GroundAsset (Conference) placed far right, near bottom
        GroundAsset conference = new GroundAsset(1, "Conference", 1000, 650, 150, 80, gameState);
        damageables.add(conference);

        // InterceptorBattery placed nearby
        InterceptorBattery battery = new InterceptorBattery(2, 900, 700, 5);
        damageables.add(battery);

        // Missile spawns at top-left, horizontal throw (vx=400, vy=0)
        BallisticMissile threat = new BallisticMissile(100, 50, 100, 400, 0, 5, 5, 1,
                new BallisticMovementStrategy());
        threats.add(threat);

        System.out.println("Spawned threat " + threat.getId() + " aiming at Conference");
        publishScene();
    }

    public java.util.List<AbstractThreat> getThreats() {
        return Collections.unmodifiableList(threats);
    }

    public java.util.List<Damageable> getDamageables() {
        return Collections.unmodifiableList(damageables);
    }

    public GameState getGameState() {
        return gameState;
    }

    // UI input events call these via router
    public void doStep(double timeStep) {
        System.out.println("In TeamBackend doStep, timeStep=" + timeStep + " ...");
        updateThreatPositions(timeStep);
        checkCollisions();
        publishScene();
    }

    private void publishScene() {
        teamUiPort().displayScene(getThreats(), getDamageables(), gameState.getScore(), gameState.isStatus());
    }

    private void updateThreatPositions(double timeStep) {
        for (AbstractThreat threat : threats) {
            threat.updateTrajectory(timeStep);
            System.out.println("Threat " + threat.getId() + " moved to (" + threat.getX() + ", " + threat.getY() + ")");
        }
    }

    private void checkCollisions() {
        java.util.Iterator<AbstractThreat> threatIterator = threats.iterator();
        while (threatIterator.hasNext()) {
            AbstractThreat threat = threatIterator.next();
            for (Damageable damageable : damageables) {
                if (damageable.checkHit(threat.getX(), threat.getY())) {
                    System.out.println("Threat " + threat.getId() + " hit a damageable.");
                    damageable.tookHit();
                    teamUiPort().removeEntity(threat.getId());
                    teamUiPort().triggerExplosion(threat.getX(), threat.getY());
                    teamUiPort().updateScore(gameState.getScore());
                    threatIterator.remove();
                    return;
                }
            }
        }
    }
}