package team.domain.backend;

import shared.ui_ports.TeamUiPort;
import team.domain.GameState;

public class LevelManager {

    private double levelElapsedTime = 0;
    private boolean levelCompleted = false;

    private static final double BASE_LEVEL_DURATION_SECONDS = 20.0;
    private static final double LEVEL_DURATION_INCREMENT_SECONDS = 8.0;
    private static final double MAX_LEVEL_DURATION_SECONDS = 60.0;

    public void reset() {
        levelElapsedTime = 0;
        levelCompleted = false;
    }

    public boolean isTimeUp(int level) {
        double levelDuration = BASE_LEVEL_DURATION_SECONDS + (LEVEL_DURATION_INCREMENT_SECONDS * level);
        levelDuration = Math.min(levelDuration, MAX_LEVEL_DURATION_SECONDS);
        return levelElapsedTime >= levelDuration;
    }

    public boolean isLevelCompleted() {
        return levelCompleted;
    }

    // הוספתי Enum שיעזור ל-TeamBackend להבין מה הייתה התוצאה של קידום הזמן
    public enum LevelState { IN_PROGRESS, LEVEL_WON, ENDLESS_NEXT_LEVEL }

    public LevelState advanceTime(double timeStep, GameState gameState, boolean threatsEmpty, TeamUiPort uiPort) {
        if (levelCompleted || !gameState.isStatus()) {
            return LevelState.IN_PROGRESS;
        }

        levelElapsedTime += timeStep;

        if (isTimeUp(gameState.getLevel()) && threatsEmpty) {
            if (gameState.isEndlessMode()) {
                System.out.println("[LOG] Endless Mode: Advancing to Level " + (gameState.getLevel() + 1));
                gameState.advanceLevel();
                reset();
                return LevelState.ENDLESS_NEXT_LEVEL;
            } else {
                System.out.println("[LOG] Level " + gameState.getLevel() + " Complete!");
                levelCompleted = true;
                uiPort.showLevelComplete("Level " + gameState.getLevel() + " complete!");
                uiPort.playLevelCompleteSound();
                return LevelState.LEVEL_WON;
            }
        }
        return LevelState.IN_PROGRESS;
    }
}