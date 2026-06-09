package team.domain;

public class BallisticMissile extends AbstractThreat {
    // Constructor
    public BallisticMissile(int id, int x, int y, int vx, int vy, int length, int height, int threatLevel, MovementStrategy movementStrategy) {
        super(id, x, y, vx, vy, length, height, threatLevel, movementStrategy);
    }
}