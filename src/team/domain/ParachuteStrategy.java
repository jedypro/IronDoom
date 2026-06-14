package team.domain;

public class ParachuteStrategy implements MovementStrategy {
    private double fallSpeed = 100.0;  //fall speed in pixels per second
    private double swayAmplitude = 25.0; 
    private double swayFrequency = 2.0;  
    private double elapsedTime = 0.0;   

    @Override
    public double[] calculateNextPosition(double currentX, double currentY, double vx, double vy, double timeStep) {
        elapsedTime += timeStep;

       //calculating next Y position based on constant fall speed
        double nextY = currentY + (fallSpeed * timeStep);

        //calculating next X position based on a sinusoidal sway pattern
        double deltaX = Math.cos(elapsedTime * swayFrequency) * swayAmplitude * timeStep;
        double nextX = currentX + deltaX;

        return new double[]{nextX, nextY};
    }
}