package team.domain;

public class BallisticTargetingParams implements TargetingParams {
    private final double angle;
   
    public BallisticTargetingParams(double angle) {
        this.angle = angle;
    }

    public double getAngle() { return angle; }
}