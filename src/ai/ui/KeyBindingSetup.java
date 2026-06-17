package ai.ui;

import base.Params;
import shared.MainRouter;
import team.domain.AbstractDefenseSystem;
import team.domain.Damageable;
import team.domain.LaserBattery;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Registers all global key bindings on the root content pane.
 *
 * <p>Previously these were wired inline inside {@code createAndShowWindow()},
 * making the method over 200 lines long.  Moving them here lets each
 * binding be found, read, and changed in isolation.</p>
 *
 * <p>Bindings:</p>
 * <ul>
 *   <li>LEFT / RIGHT – continuous aim rotation</li>
 *   <li>UP / DOWN    – cycle battery selection</li>
 *   <li>Z            – single fire</li>
 *   <li>X            – triple fire (selected battery)</li>
 *   <li>C            – fire from all active batteries</li>
 *   <li>V            – triple fire from all active batteries + screen shake</li>
 *   <li>A            – toggle aim line</li>
 *   <li>SPACE        – toggle pause / resume</li>
 *   <li>CTRL+SPACE   – reveal hidden multiplayer button</li>
 * </ul>
 */
public class KeyBindingSetup {

    private final JPanel         contentPane;
    private final UiState        uiState;
    private final SceneData      sceneData;
    private final MainRouter     mainRouter;
    private final JSlider        angleSlider;
    private final JButton        fireButton;
    private final GameCanvas     canvas;
    private final BatterySelector batterySelector;
    private final ScreenNavigator navigator;
    private final JButton         multiplayerBtn;
    private final JPanel          introPanel;
    private       Timer           aimTimer;

    public KeyBindingSetup(
            JPanel          contentPane,
            UiState         uiState,
            SceneData       sceneData,
            MainRouter      mainRouter,
            JSlider         angleSlider,
            JButton         fireButton,
            GameCanvas      canvas,
            BatterySelector batterySelector,
            ScreenNavigator navigator,
            JButton         multiplayerBtn,
            JPanel          introPanel) {

        this.contentPane     = contentPane;
        this.uiState         = uiState;
        this.sceneData       = sceneData;
        this.mainRouter      = mainRouter;
        this.angleSlider     = angleSlider;
        this.fireButton      = fireButton;
        this.canvas          = canvas;
        this.batterySelector = batterySelector;
        this.navigator       = navigator;
        this.multiplayerBtn  = multiplayerBtn;
        this.introPanel      = introPanel;
    }

    /**
     * Creates the aim timer and registers every key binding.
     * Must be called after the frame is constructed.
     */
    public Timer setup() {
        aimTimer = createAimTimer();

        InputMap  inputMap  = contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = contentPane.getActionMap();

        bindAimLeft(inputMap, actionMap);
        bindAimRight(inputMap, actionMap);
        bindCycleBattery(inputMap, actionMap);
        bindSingleFire(inputMap, actionMap);
        bindTripleFire(inputMap, actionMap);
        bindFireAll(inputMap, actionMap);
        bindTripleFireAll(inputMap, actionMap);
        bindToggleAimLine(inputMap, actionMap);
        bindTogglePause(inputMap, actionMap);
        bindShowMultiplayer(inputMap, actionMap);

        return aimTimer;
    }

    // ── Aim timer ─────────────────────────────────────────────────────────────

    private Timer createAimTimer() {
        return new Timer(UIConstants.AIM_INTERVAL_MS, e -> {
            int direction = uiState.getAimDirection();
            if (direction == 0) return;

            int delta = "LASER".equals(getSelectedDefenseType())
                    ? UIConstants.AIM_DELTA_LASER
                    : UIConstants.AIM_DELTA_DEFAULT;

            int current = angleSlider.getValue();
            int next    = clamp(current + direction * delta,
                                angleSlider.getMinimum(),
                                angleSlider.getMaximum());
            if (next != current) angleSlider.setValue(next);
        });
    }

    // ── Individual bindings ───────────────────────────────────────────────────

    private void bindAimLeft(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed LEFT"), "startAimLeft");
        am.put("startAimLeft", action(e -> {
            uiState.setAimDirection(-1);
            aimTimer.start();
        }));
        im.put(KeyStroke.getKeyStroke("released LEFT"), "stopAimLeft");
        am.put("stopAimLeft", action(e -> {
            if (uiState.getAimDirection() == -1) uiState.setAimDirection(0);
            if (uiState.getAimDirection() == 0)  aimTimer.stop();
        }));
    }

    private void bindAimRight(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed RIGHT"), "startAimRight");
        am.put("startAimRight", action(e -> {
            uiState.setAimDirection(+1);
            aimTimer.start();
        }));
        im.put(KeyStroke.getKeyStroke("released RIGHT"), "stopAimRight");
        am.put("stopAimRight", action(e -> {
            if (uiState.getAimDirection() == +1) uiState.setAimDirection(0);
            if (uiState.getAimDirection() == 0)  aimTimer.stop();
        }));
    }

    private void bindCycleBattery(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed UP"), "selectPrevBattery");
        am.put("selectPrevBattery", action(e -> {
            if (UIConstants.CARD_GAME.equals(uiState.getCurrentScreen()))
                batterySelector.cycleSelection(-1);
        }));
        im.put(KeyStroke.getKeyStroke("pressed DOWN"), "selectNextBattery");
        am.put("selectNextBattery", action(e -> {
            if (UIConstants.CARD_GAME.equals(uiState.getCurrentScreen()))
                batterySelector.cycleSelection(+1);
        }));
    }

    private void bindSingleFire(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed Z"), "fire");
        am.put("fire", action(e -> fireButton.doClick()));
    }

    private void bindTripleFire(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed X"), "tripleFire");
        am.put("tripleFire", action(e -> {
            int id = uiState.getSelectedBatteryId();
            if (id == -1) return;
            int angle = angleSlider.getValue();
            String type = getSelectedDefenseType();
            fireAt(id, angle, type);
            fireAt(id, clamp(angle + UIConstants.TRIPLE_FIRE_SPREAD, angleSlider.getMinimum(), angleSlider.getMaximum()), type);
            fireAt(id, clamp(angle - UIConstants.TRIPLE_FIRE_SPREAD, angleSlider.getMinimum(), angleSlider.getMaximum()), type);
        }));
    }

    private void bindFireAll(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed C"), "fireAll");
        am.put("fireAll", action(e -> {
            int angle = angleSlider.getValue();
            forEachActiveSystem((ds, type) -> fireAt(ds.getId(), angle, type));
        }));
    }

    private void bindTripleFireAll(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed V"), "tripleFireAll");
        am.put("tripleFireAll", action(e -> {
            int base   = angleSlider.getValue();
            int spread = UIConstants.TRIPLE_FIRE_SPREAD;
            int a2 = clamp(base + spread, angleSlider.getMinimum(), angleSlider.getMaximum());
            int a3 = clamp(base - spread, angleSlider.getMinimum(), angleSlider.getMaximum());

            boolean[] fired = {false};
            forEachActiveSystem((ds, type) -> {
                fireAt(ds.getId(), base, type);
                fireAt(ds.getId(), a2,   type);
                fireAt(ds.getId(), a3,   type);
                fired[0] = true;
            });
            if (fired[0] && canvas != null) {
                canvas.triggerScreenShake(400, 15);
            }
        }));
    }

    private void bindToggleAimLine(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed A"), "toggleAim");
        am.put("toggleAim", action(e -> {
            if (canvas != null) canvas.setShowAim(!canvas.isShowAim());
        }));
    }

    private void bindTogglePause(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke("pressed SPACE"), "togglePause");
        am.put("togglePause", action(e -> {
            if (UIConstants.CARD_GAME.equals(uiState.getCurrentScreen())) {
                navigator.togglePause();
            }
        }));
    }

    private void bindShowMultiplayer(InputMap im, ActionMap am) {
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK), "showMultiplayer");
        am.put("showMultiplayer", action(e -> {
            if (UIConstants.CARD_INTRO.equals(uiState.getCurrentScreen())
                    && multiplayerBtn != null && !multiplayerBtn.isVisible()) {
                multiplayerBtn.setVisible(true);
                if (introPanel != null) {
                    introPanel.revalidate();
                    introPanel.repaint();
                }
            }
        }));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface SystemConsumer {
        void accept(AbstractDefenseSystem ds, String defenseType);
    }

    private void forEachActiveSystem(SystemConsumer consumer) {
        for (Damageable d : sceneData.getDamageables()) {
            if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) d).isActive()) {
                AbstractDefenseSystem ds = (AbstractDefenseSystem) d;
                consumer.accept(ds, ds instanceof LaserBattery ? "LASER" : "MISSILE");
            }
        }
    }

    private void fireAt(int batteryId, int angle, String defenseType) {
        if (mainRouter != null) {
            mainRouter.route("/team/launchDefense", Params.of(batteryId, angle, defenseType));
        }
    }

    private String getSelectedDefenseType() {
        int id = uiState.getSelectedBatteryId();
        for (Damageable d : sceneData.getDamageables()) {
            if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) d).getId() == id) {
                return d instanceof LaserBattery ? "LASER" : "MISSILE";
            }
        }
        return "MISSILE";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static AbstractAction action(java.util.function.Consumer<ActionEvent> handler) {
        return new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { handler.accept(e); }
        };
    }
}
