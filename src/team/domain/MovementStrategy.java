package team.domain;

public interface MovementStrategy {

    public double[] calculateNextPosition(double currentX, double currentY, double vx, double vy, double timeStep);
    
}
