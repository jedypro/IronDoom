package team.domain;

public class WavyMovementStrategy implements MovementStrategy {
    private final double gravity = -200.0;
    private final double waveAmplitude = 12.0;
    private final double waveFrequency = 3.5;
    private double elapsedTime = 0.0;

    @Override
    public double[] calculateNextPosition(double currentX, double currentY, double vx, double vy, double timeStep) {
        elapsedTime += timeStep;

        double nextX = currentX + vx * timeStep;
        double nextY = currentY + vy * timeStep - 0.5 * gravity * timeStep * timeStep;

        double waveOffset = waveAmplitude * Math.sin(waveFrequency * elapsedTime);
        nextY += waveOffset;

        double nextVx = vx;
        double nextVy = vy - gravity * timeStep;

        return new double[] { nextX, nextY, nextVx, nextVy };
    }
}
