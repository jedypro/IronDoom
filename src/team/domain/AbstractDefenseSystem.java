package team.domain;

import base.IdentifiedObject;

public abstract class AbstractDefenseSystem extends IdentifiedObject {
    int x;
    int y;
    boolean isActive;
    // Constructor
    public AbstractDefenseSystem(int id, int x, int y) {
        super(id);
        this.x = x;
        this.y = y;
        this.isActive = true;
    }

    // setters and getters
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isActive() {
        return isActive;
    }
    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }
    public abstract void repair();
    //Defense method
    public abstract DefenseEntity attemptDefense(TargetingParams params);
}