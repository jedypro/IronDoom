package team.domain;

public class PoweredFlightStrategy implements MovementStrategy {
    private int targetX;
    private int targetY;
    private double cruisingSpeed;

    public PoweredFlightStrategy(int targetX, int targetY, double cruisingSpeed) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.cruisingSpeed = cruisingSpeed;
    }
    // Getters and setters
    public int getTargetX() {
        return targetX;
    }
    public void setTargetX(int targetX) {
        this.targetX = targetX;
    }
    public int getTargetY() {
        return targetY;
    }
    public void setTargetY(int targetY) {
        this.targetY = targetY;
    }
    public double getCruisingSpeed() {
        return cruisingSpeed;
    }
    public void setCruisingSpeed(double cruisingSpeed) {
        this.cruisingSpeed = cruisingSpeed;
    }

    @Override
    public double[] calculateNextPosition(double currentX, double currentY, double vx, double vy, double timeStep) {
        // Calculate direction vector towards target
        double dirX = targetX - currentX;
        double dirY = targetY - currentY;
        double distance = Math.sqrt(dirX * dirX + dirY * dirY);
        
        // Calculate the maximum distance the object can travel in this frame
        double stepDistance = cruisingSpeed * timeStep;

        // Overshoot protection: If the remaining distance is smaller than the step, snap to target
        if (distance <= stepDistance) {
            return new double[]{targetX, targetY, 0, 0}; 
        }

        // Normalize direction and scale by cruising speed
        double normDirX = dirX / distance;
        double normDirY = dirY / distance;
        double nextVx = normDirX * cruisingSpeed;
        double nextVy = normDirY * cruisingSpeed;

        // Calculate next position based on velocity
        double nextX = currentX + nextVx * timeStep;
        double nextY = currentY + nextVy * timeStep;

        return new double[]{nextX, nextY, nextVx, nextVy};
    }


    
}
