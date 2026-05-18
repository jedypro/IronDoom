package my_base;

import ai.ui.Ui;
import base.PeriodicScheduler;
import shared.MainRouter;
import shared.routers.teamRouter;

public class App {

    private static MainRouter mainRouter = new MainRouter();
    private static Ui ui;
    private static AppContent content = new AppContent();

    // TO_DO: Register all routers here
    private static void registerRouters() {
        System.out.println("Registering team router ...");
        mainRouter.addRouter("team", new teamRouter());
    }

    // Allows all classes in teh system to access content
    // entities from everywhere.
    public static AppContent content() {
        return content;
    }

    public static Ui UI() {
        return ui;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("App started - Initializing content ...");
        content.initContent();
        System.out.println("Creating UI instance...");
        ui = new Ui();
        System.out.println("Setting UI ports ...");
        ui.setUiPorts();
        System.out.println("Registering routers ...");
        registerRouters();
        System.out.println("Starting UI ...");
        ui.start(mainRouter);
        PeriodicScheduler scheduler = new PeriodicScheduler();
        scheduler.setPeriodicLoop(new MyPeriodicLoop());
        System.out.println("Starting periodic scheduler ...");
        scheduler.start();
    }
}
