package team.domain;

public class LaserTargetingParams implements TargetingParams {
    private final double angle;

    public LaserTargetingParams(double angle) {
        this.angle = angle;
    }

    public double getAngle() {
        return angle;
    }
}