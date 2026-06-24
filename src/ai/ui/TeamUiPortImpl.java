package ai.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.SwingUtilities;

import base.AudioPlayer;
import shared.ui_ports.TeamUiPort;
import team.domain.AbstractThreat;
import team.domain.Damageable;
import team.domain.DefenseEntity;
import team.domain.Gift;
import team.domain.Civilian;

public class TeamUiPortImpl extends TeamUiPort {
    private final Ui ui;

    public TeamUiPortImpl(Ui ui) {
        this.ui = ui;
    }

    @Override
    public void displayCivilians(List<Civilian> civilians) {
        SwingUtilities.invokeLater(() -> ui.setCivilians(civilians));
    }
    

    @Override
    public void method1(int elementId) {
        //System.out.println("Method1 called with elementId: " + elementId);
    }

    @Override
    public void log(String message) {
        //System.out.println(message);
    }

    @Override
    public void removeEntity(int id) {
        SwingUtilities.invokeLater(() -> ui.refresh());
    }

   @Override
    public void triggerExplosion(int x, int y) {
        SwingUtilities.invokeLater(() -> ui.triggerExplosionEffect(x, y));
        // השורה שמשדרת לתוקף את האפקט:
    }

    @Override
    public void updateScore(int score) {
        SwingUtilities.invokeLater(() -> {
            ui.updateScore(score);
            if (score <= 0) {
                ui.showStatus("Game Over");
            }
        });
    }

    @Override
    public void updateLevel(int level) {
        SwingUtilities.invokeLater(() -> ui.updateLevel(level));
    }

    @Override
    public void displayScene(List<AbstractThreat> threats, List<Damageable> damageables, List<DefenseEntity> interceptors, List<Gift> gifts, int score, boolean running, int groundY) {
        SwingUtilities.invokeLater(() -> ui.setScene(threats, damageables, interceptors, gifts, score, running, groundY));
    }

    @Override
    public void showWarning(String message) {
        SwingUtilities.invokeLater(() -> ui.showWarning(message));
    }

    @Override
    public void showLevelComplete(String message) {
        SwingUtilities.invokeLater(() -> ui.showLevelComplete(message));
    }

    //sound effects
    @Override
    public void playExplosionSound() {
        if (ui.isSoundEnabled()) {
            AudioPlayer.play("resources/sounds/explosion.wav", 1);
        }
    }

    @Override
    public void playInterceptSound() {
        if (ui.isSoundEnabled()) {
            AudioPlayer.play("resources/sounds/intercept.wav", 1);
        }
    }

    @Override
    public void playWarningSound() {
        if (ui.isSoundEnabled()) {
            AudioPlayer.play("resources/sounds/warning.wav", 1);
        }
    }

    @Override
    public void playLevelCompleteSound() {
        if (ui.isSoundEnabled()) {
            AudioPlayer.play("resources/sounds/level_complete.wav", 1);
        }
    }

    @Override
    public void playScreamSound() {
        if (ui.isSoundEnabled()) {
            // הגדרת הנתיב לתיקיית הצעקות
            File folder = new File("resources/sounds/screams");
            File[] listOfFiles = folder.listFiles();

            // מוודאים שהתיקייה קיימת ושיש בה קבצים
            if (listOfFiles != null && listOfFiles.length > 0) {
                // ממירים את המערך לרשימה כדי שנוכל לערבב אותה
                List<File> fileList = new ArrayList<>(Arrays.asList(listOfFiles));
                Collections.shuffle(fileList);

                // מנגנים את הקובץ האקראי הראשון
                AudioPlayer.play(fileList.get(0).getPath().replace("\\", "/"), 1);

                // אם יש בתיקייה יותר מקובץ אחד, מנגנים גם את הקובץ האקראי השני
                if (fileList.size() > 1) {
                    AudioPlayer.play(fileList.get(1).getPath().replace("\\", "/"), 1);
                }
            }
        }
    }

    //events
    @Override
    public void showGameEvent(String description, String result, boolean isGood) {
        SwingUtilities.invokeLater(() -> ui.showEvent(description, isGood, result));
    }
    @Override
    public void showGiftCollected(String message) {
        SwingUtilities.invokeLater(() -> ui.showGiftCollected(message));
    }
}