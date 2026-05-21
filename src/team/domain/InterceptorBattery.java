package team.domain;

public class InterceptorBattery extends AbstractDefenseSystem implements Damageable {
    int missilesAvailable;
    // Constructor
    public InterceptorBattery(int id, int x, int y, int missilesAvailable) {
        super(id, x, y);
        this.missilesAvailable = missilesAvailable;
    }
    // setters and getters
    public int getMissilesAvailable() {
        return missilesAvailable;
    }
    public void setMissilesAvailable(int missilesAvailable) {
        this.missilesAvailable = missilesAvailable;
    }

    public boolean canFire() {
        return missilesAvailable > 0 && isActive();
    }
    
    @Override
    public boolean checkHit(int px, int py) {
        double dx = px - getX();
        double dy = py - getY();
        return Math.hypot(dx, dy) <= 15.0;
    }

    @Override
    public void tookHit() {
        System.out.println("InterceptorBattery " + getId() + " was hit and disabled.");
        setActive(false);
        missilesAvailable = 0;
    }

    @Override
    public void attemptDefense() {
        // Implementation for attempting defense
    }
}
