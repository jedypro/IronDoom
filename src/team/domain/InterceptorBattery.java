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
    public void tookHit() {
        // Implementation for when the interceptor battery is hit
    }

    @Override
    public void attemptDefense() {
        // Implementation for attempting defense
    }
}
