package team.domain;

public class InterceptorBattery extends AbstractDefenseSystem implements Damageable {
    private int missilesAvailable = 20; // Default starting ammo
    // Constructor
    public InterceptorBattery(int id, int x, int y, int inventory) {
        super(id, x, y);
        this.missilesAvailable = inventory;
    }

    //method overloading, for default ammo
    public InterceptorBattery(int id, int x, int y) {
        super(id, x, y);
    }

    // setters and getters
    public int getMissilesAvailable() {
        return missilesAvailable;
    }
    public void setMissilesAvailable(int missilesAvailable) {
        this.missilesAvailable = missilesAvailable;
    }

    @Override
    public boolean checkHit(int px, int py) {
        double dx = px - getX();
        double dy = py - getY();
        return Math.hypot(dx, dy) <= 30;
    }

    @Override
    public void tookHit() {
        //System.out.println("InterceptorBattery " + getId() + " was hit and disabled.");
        this.isActive = false;
    }


/**
 * Attempts to launch an interceptor missile based on target angle and power.
 * * @param missileId    Unique ID generated for the new missile
 * @param angleDegrees Launch angle in degrees (0 to 90), where 0 is horizontal left and 90 is vertical up
 * @param power        Initial launch velocity/power
 * @return A configured InterceptorMissile instance, or null if launch fails
 */
public InterceptorMissile attemptDefense(double angleDegrees, double power) {
        // Check if the battery is active and has enough ammo
        if (!isActive || missilesAvailable == 0) {
            return null; 
        }

        // Convert angle to velocity components.
        // 0° = left along the ground, 90° = straight up.
        double rad = Math.toRadians(angleDegrees);
        double vx = -power * Math.cos(rad);
        double vy = -power * Math.sin(rad);

        // Create the missile at the battery's current ground position
        InterceptorMissile missile = new InterceptorMissile(this.getX(), this.getY(), vx, vy);
        
        // Use the standard ballistic flight path for now.
        missile.setMovementStrategy(new BallisticMovementStrategy());

        missilesAvailable--;

        return missile;
    }
}
