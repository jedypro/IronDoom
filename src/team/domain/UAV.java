package team.domain;

public class UAV extends AbstractThreat {
    int difficultyLevel; // Default difficulty level, can be set from outside
    // Constructor
    public UAV(int id, int x, int y, int vx, int vy, int length, int height, int threatLevel, PoweredFlightStrategy poweredFlightStrategy, int difficultyLevel) {
        super(id, x, y, vx, vy, length, height, threatLevel, poweredFlightStrategy);
        this.difficultyLevel = difficultyLevel;
    }
    //getters and setters
    public int getDifficultyLevel() {
        return difficultyLevel;
    }
    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    @Override
    public void updateTrajectory(double timeStep) {
        // Base turn chance per second scaled by difficulty level (e.g., 20% per level)
        double turnChancePerSecond = difficultyLevel * 0.2; 
        
        // Frame-rate independent probability
        double turnChanceThisFrame = turnChancePerSecond * timeStep;

        // Try to change target randomly
        if (Math.random() < turnChanceThisFrame) {
            changeTargetRandomly();
        }

        // Execute movement via parent class
        super.updateTrajectory(timeStep);
    }

    private void changeTargetRandomly() {
        // Safe cast to update coordinates
        if (this.movementStrategy instanceof PoweredFlightStrategy) {
            PoweredFlightStrategy strategy = (PoweredFlightStrategy) this.movementStrategy;
            
            // Randomize new target within valid screen bounds
            int newTargetX = (int) (Math.random() * 800);
            
            strategy.setTargetX(newTargetX);
        }
    }
}
