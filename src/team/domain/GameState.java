package team.domain;

public class GameState {
    private int score;
    private int level;
    private boolean status;
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

    public void setScore(int score) {
        this.score = score;
    }
    public void setLevel(int level) {
        this.level = level;
    }
    public void setStatus(boolean status) {
        this.status = status;
    }

    // Method to update the game score
    public void updateScore(int points) {
        this.score += points;
    }
    // method to advance to the next level
    public void advanceLevel() {
        this.level++;
    }
    // Check if the game is over
    public boolean isGameOver() {
        return !status || score <= 0;
    }
}
