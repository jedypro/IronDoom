package team.domain;

import shared.ui_ports.TeamUiPort;


public class TeamBackend {
    private boolean isFlying = false;
    private double x, y;    // position
    private double vx, vy;   // velocity
    private final double g = 9.81;

    /**
     * Use ex3UiPort() as a function and not a variableto get the UI port
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
        System.out.println("Calling UI method1 with elementId 42 ...");
        teamUiPort().method1(42);
    }

    // UI input events call these via router
    public void doStep(int step) {
        System.out.println("In TeamBackend doStep ...");

        if (!isFlying) return;

        x = x + vx;
        y = y + vy;
        vy = vy - g; // gravity effect

        TeamUiPort.getInstance().updateMissilePosition(x, y);

        if (y < 0) { //missile is out of frame
            isFlying = false;
            System.out.println("Missile landed.");
        }

        System.out.println("Calling UI method1 with elementId " + step + " ...");
        teamUiPort().method1(step);
    }

    public void launchMissile() {
        this.isFlying = true;
        this.x = 0;
        this.y = 0;
     
        this.vx = 30; // initial-constant horizontal velocity
        this.vy = 70; // initial vertical velocity
        
        System.out.println("Backend: Missile physics initialized.");
    }
}