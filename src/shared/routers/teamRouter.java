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
        // System.out.println("Routing Ex3: " + subPath + " with params " + p);
        switch (subPath) {

            // UI calls once on startup
            case "/start":
                System.out.println("teamRouter: Routing /team/start. Calling backend.start() ...");
                backend.start();
                return null;

            // UI input: drag point
            case "/doStep": {
                backend.doStep(p.getInt(0));
                return null;
            }
            default:
                throw new RuntimeException("Unknown ex3 route: " + subPath);
        }
    }
}