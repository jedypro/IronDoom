package team.domain;

public class UAV extends AbstractThreat {
    int difficultyLevel; // Default difficulty level, can be set from outside
    
    // בנאי דמה עבור ה-AttackerUi (מעביר ערכי 0 ל-super כדי למנוע את האדומים)
    public UAV() {
        super(0, 0, 0, 0, 0, 0, 0, 0, null);
    }

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
        if (this.getMovementStrategy() instanceof PoweredFlightStrategy) {
            PoweredFlightStrategy strategy = (PoweredFlightStrategy) this.getMovementStrategy();
            
            // Randomize new target within valid screen bounds
            int newTargetX = (int) (Math.random() * 800);
            
            strategy.setTargetX(newTargetX);
        }
    }
}