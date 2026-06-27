package team.domain;

import java.util.UUID;

public class LaserBattery extends AbstractDefenseSystem implements Damageable {
    private int laserChargesAvailable = 50; // Default starting ammo for laser
    public static final double LASER_RANGE = 1500.0; // How far the laser reaches
    public static final double LASER_DURATION_SECONDS = 2.0; // How long the laser stays visible
    private double currentAimAngle = 90.0; // ברירת מחדל למעלה

    // בנאי דמה עבור ה-AttackerUi (מונע שגיאות קומפילציה)
    public LaserBattery() {
        super(0, 0, 0); 
    }

    // Constructor
    public LaserBattery(int id, int x, int y, int inventory) {
        super(id, x, y);
        this.laserChargesAvailable = inventory;
    }

    // method overloading, for default ammo
    public LaserBattery(int id, int x, int y) {
        super(id, x, y);
    }

    // setters and getters
    public int getLaserChargesAvailable() {
        return laserChargesAvailable;
    }

    public void setLaserChargesAvailable(int laserChargesAvailable) {
        this.laserChargesAvailable = laserChargesAvailable;
    }
    
    public void setCurrentAimAngle(double angle) {
        this.currentAimAngle = angle;
    }

    public double getCurrentAimAngle() {
        return this.currentAimAngle;
    }

    @Override
    public boolean checkHit(int px, int py) {
        double dx = px - getX();
        double dy = py - getY();
        return Math.hypot(dx, dy) <= 30; // Assuming same hit radius as InterceptorBattery
    }

    @Override
    public void tookHit() {
        this.isActive = false;
    }

    @Override
    public DefenseEntity attemptDefense(TargetingParams params) {
        // Check if the battery is active and has enough ammo
        if (!isActive || laserChargesAvailable == 0) {
            return null;
        }

        if (params instanceof LaserTargetingParams) {
            double angle = ((LaserTargetingParams) params).getAngle();
            // Create a LightShield at the battery's position
            LightShield laser = new LightShield(UUID.randomUUID().hashCode(), this, angle, LASER_RANGE, LASER_DURATION_SECONDS, this.getId());
            laserChargesAvailable--; // Lasers consume ammo
            return laser;
        }
        return null; // This battery only fires lasers
    }

    @Override
    public void repair() {
        this.isActive = true; // Reactivate the battery
        this.laserChargesAvailable = laserChargesAvailable + 30; // Repair adds 30 laser charges, can be adjusted as needed
    }

    @Override
    public int getInventory()
    {
        return laserChargesAvailable;
    }
}