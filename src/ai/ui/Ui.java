package ai.ui;

import javax.swing.*;

import base.Params;
import shared.MainRouter;
import shared.ui_ports.TeamUiPort;


public class Ui {
    private MainRouter mainRouter;
    private TeamUiPortImpl uiInstance;

    public void setUiPorts() {
        uiInstance = new TeamUiPortImpl();
        TeamUiPort.setInstance((TeamUiPort) uiInstance);
    }

    public void start(MainRouter mainRouter) {
        this.mainRouter = mainRouter;
        createAndShowWindow();
        System.out.println("UI started");
        System.out.println("Calling backend start method via router /team/start ...");
        mainRouter.route("/team/start", Params.of());
    }

    private void createAndShowWindow() {
        System.out.println("Creating and showing UI window...");
    }
}
