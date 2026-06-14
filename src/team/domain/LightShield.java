package team.domain;

import base.IdentifiedObject;

public class LightShield extends IdentifiedObject implements DefenseEntity {
    private double x;
    private double y;
    private double angle; // Angle in degrees, 0 is right, 90 is up
    private double length;
    private double remainingDuration; // How long the laser stays visible
    private boolean active = true;
    private LaserBattery source;
    private int sourceBatteryId; // To track which battery launched this missile

    public LightShield(int id, LaserBattery source, double angle, double length, double duration, int sourceBatteryId) {
        super(id);
        this.source = source;
        this.x = source.getX();
        this.y = source.getY();
        this.angle = angle;
        this.length = length;
        this.remainingDuration = duration;
        this.sourceBatteryId = sourceBatteryId;
    }

    public int getSourceBatteryId() {
        return sourceBatteryId;
    }
    @Override
    public void updatePosition(double dt) {
        if (active) {
            if (source != null && source.isActive()) {
                this.x = source.getX();
                this.y = source.getY();
                this.angle = source.getCurrentAimAngle(); 
            }
            remainingDuration -= dt;
            if (remainingDuration <= 0) {
                explode();
            }
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void explode() {
        this.active = false;
        this.remainingDuration = 0;
    }

    @Override
    public int getX() {
        return (int) x;
    }

    @Override
    public int getY() {
        return (int) y;
    }

    // Specific getters for LightShield rendering and collision
    public double getAngle() {
        return angle;
    }

    public double getLength() {
        return length;
    }

    // Method to get the end point of the laser beam
    public int getEndX() {
        return (int) (x - length * Math.cos(Math.toRadians(angle)));
    }

    public int getEndY() {
        return (int) (y - length * Math.sin(Math.toRadians(angle)));
    }

    // Checks if the laser beam intersects with a given threat's bounding box.
    // For simplicity, this checks if the threat's center is "near" the laser line segment.
    public boolean intersects(AbstractThreat threat) {
        double p1x = x; double p1y = y; // Laser start
        double p2x = getEndX(); double p2y = getEndY(); // Laser end
        double cx = threat.getX() + threat.getLength() / 2.0; // Threat center X
        double cy = threat.getY() + threat.getHeight() / 2.0; // Threat center Y
        double threatRadius = Math.max(threat.getLength(), threat.getHeight()) / 2.0;
        double collisionThreshold = threatRadius + 5; // Add a small buffer for collision

        double L2 = (p2x - p1x) * (p2x - p1x) + (p2y - p1y) * (p2y - p1y);
        if (L2 == 0.0) { // Laser is a point
            return Math.sqrt((cx - p1x) * (cx - p1x) + (cy - p1y) * (cy - p1y)) < collisionThreshold;
        }

        // Project threat center onto the line segment
        double t = ((cx - p1x) * (p2x - p1x) + (cy - p1y) * (p2y - p1y)) / L2;
        t = Math.max(0, Math.min(1, t)); // Clamp t to [0, 1] to stay within segment

        double closestX = p1x + t * (p2x - p1x);
        double closestY = p1y + t * (p2y - p1y);

        // Calculate distance from threat center to the closest point on the segment
        double distance = Math.sqrt((cx - closestX) * (cx - closestX) + (cy - closestY) * (cy - closestY));

        return distance < collisionThreshold;
    }
}