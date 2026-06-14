package shared.routers;

import base.Params;
import base.SubRouter;
import my_base.App;
import team.domain.TeamBackend;

public class teamRouter implements SubRouter {

    private final TeamBackend backend;

    public teamRouter() {
        this.backend = App.content().teamBackend();
    }

    @Override
    public Object route(String subPath, Params p) {
        // Uncomment next line to see routing commands in console
        // //System.out.println("Routing Ex3: " + subPath + " with params " + p);
        switch (subPath) {

            // UI calls once on startup
            case "/start":
                //System.out.println("teamRouter: Routing /team/start. Calling backend.start() ...");
                backend.start();
                return null;

            // UI input: drag point
            case "/doStep": {
                backend.doStep(p.getDouble(0));
                return null;
            }
            case "/launchDefense": { // Changed from /launch to /launchDefense
                int defenseSystemId = p.getInt(0);
                double angle = p.getDouble(1); // Angle can be double
                String defenseType = p.getString(2); // New parameter for defense type

                backend.launchDefense(defenseSystemId, angle, defenseType);
                return null;
            }
            
            case "/updateAim": {
                int defenseSystemId = p.getInt(0);
                double angle = p.getDouble(1);
                backend.updateAim(defenseSystemId, angle);
                return null;
            }

            case "/reset":
                backend.resetGame();
                return null;

            case "/updateSettings": {
                int level = p.getInt(0);
                backend.updateSettings(level);
                return null;
            }
            case "/nextLevel": {
                backend.nextLevel();
                return null;
            }
            case "/setMode": {
                boolean isEndlessMode = p.getBoolean(0);
                backend.getGameState().setMode(isEndlessMode);
                return null;
            }
            case "/pause":
                App.getPeriodicLoop().setPaused(true);
                return null;

            case "/resume":
                App.getPeriodicLoop().setPaused(false);
                return null;
            
            default:
                throw new RuntimeException("Unknown ex3 route: " + subPath);
        }
    }
}