package team.domain;

public class InterceptorBattery extends AbstractDefenseSystem implements Damageable {
    private boolean active = true;
    private int missilesAvailable=20; // Default starting ammo
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
        return Math.hypot(dx, dy) <= 15.0;
    }

    @Override
    public void tookHit() {
        //System.out.println("InterceptorBattery " + getId() + " was hit and disabled.");
        active=false;
        missilesAvailable = 0;
    }


/**
 * Attempts to launch an interceptor missile based on target angle and power.
 * * @param missileId    Unique ID generated for the new missile
 * @param angleDegrees Launch angle in degrees (90 to 180)
 * @param power        Initial launch velocity/power
 * @return A configured InterceptorMissile instance, or null if launch fails
 */
public InterceptorMissile attemptDefense(double angleDegrees, double power) {
        // Check if the battery is active and has enough ammo
        if (!active || missilesAvailable == 0) {
            return null; 
        }

        // Convert angle to radians and calculate velocity components
        // vy is inverted (-) because the UI Y-axis goes downwards
        double rad = -Math.toRadians(angleDegrees-180);
        double vx = power * Math.cos(rad);
        double vy = -power * Math.sin(rad);

        // Create the missile at the battery's current ground position
        InterceptorMissile missile = new InterceptorMissile(this.getX(), this.getY(), vx, vy);
        
        // Assign the ballistic flight physics behavior
        missile.setMovementStrategy(new BallisticMovementStrategy());

        missilesAvailable--;

        return missile;
    }
}
