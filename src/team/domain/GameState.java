package team.domain;

public class GameState {
    private static final int DEFAULT_GROUND_Y = 730;

    private int score;
    private int level;
    private boolean status;
    private int groundY = DEFAULT_GROUND_Y;
    private boolean isEndlessMode = false;
    // Constructor
    public GameState(int score, int level, boolean status) {
        this.score = score;
        this.level = level;
        this.status = status;
    }

    //stetter and getter
    public int getScore() {
        return score;
    }
    public int getLevel() {
        return level;
    }
    public boolean isStatus() {
        return status;
    }

    public int getGroundY() {
        return groundY;
    }

    public void setGroundY(int groundY) {
        this.groundY = groundY;
    }

    public void setScore(int score) {
        this.score = score;
    }
    public void setLevel(int level) {
        this.level = level;
    }
    public void setStatus(boolean status) {
        this.status = status;
    }

    // Method to update the game score and delegate game-over handling to isGameOver
    public void updateScore(int points) {
        this.score += points;
        isGameOver();
    }
    // method to advance to the next level
    public void advanceLevel() {
        this.level++;
    }
    // Check if the game is over and update status when needed
    public boolean isGameOver() {
        if (this.score <= 0) {
            this.score = 0;
            this.status = false;
        }
        return !status;
    }
    public boolean isEndlessMode() {
        return isEndlessMode;
    }
    public void setMode(boolean isEndlessMode) {
        this.isEndlessMode = isEndlessMode;
    }
}
