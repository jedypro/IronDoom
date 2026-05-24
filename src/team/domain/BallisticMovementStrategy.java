package team.domain;

public class BallisticMovementStrategy implements MovementStrategy {
    private double gravity = -200; // Scaled up negative gravity for visible arc in 2D coords
    @Override
    public double[] calculateNextPosition(double currentX, double currentY, double vx, double vy, double timeStep) {
        // Simple ballistic motion and velocity calculation
        double nextX = currentX + vx * timeStep;
        double nextY = currentY + vy * timeStep - 0.5 * gravity * timeStep * timeStep;
        double nextVx = vx; // Assuming no horizontal acceleration
        double nextVy = vy - gravity * timeStep; // Vertical velocity affected by gravity
        return new double[]{nextX, nextY, nextVx, nextVy};
    }
}
