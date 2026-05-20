package ai.ui;

import javax.swing.JPanel;
import javax.swing.Timer;

import shared.ui_ports.TeamUiPort;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class TeamUiPortImpl extends TeamUiPort {
    public TeamUiPortImpl() {
    }

    @Override
    public void method1(int elementId) {
        System.out.println("Method1 called with elementId: " + elementId);
    }

    @Override
    public void log(String message) {
        System.out.println(message);
    }
}