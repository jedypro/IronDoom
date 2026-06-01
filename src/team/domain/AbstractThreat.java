package team.domain;

import base.IdentifiedObject;

public abstract class AbstractThreat extends IdentifiedObject{
    int x;
    int y;
    int vx;
    int vy;
    int length;
    int height;
    int threatLevel;
    MovementStrategy movementStrategy;
    // Constructor
    public AbstractThreat(int id, int x, int y, int vx, int vy, int length, int height, int threatLevel, MovementStrategy movementStrategy) {
        super(id);
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.length = length;
        this.height = height;
        this.threatLevel = threatLevel;
        this.movementStrategy = movementStrategy;
    }
    // setters and getters
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getVx() {
        return vx;
    }

    public int getVy() {
        return vy;
    }

    public int getLength() {
        return length;
    }

    public int getHeight() {
        return height;
    }
    public int getThreatLevel() {
        return threatLevel;
    }
    public MovementStrategy getMovementStrategy() {
        return movementStrategy;
    }
    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setVx(int vx) {
        this.vx = vx;
    }

    public void setVy(int vy) {
        this.vy = vy;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setHeight(int height) {
        this.height = height;
    }
    public void setThreatLevel(int threatLevel) {
        this.threatLevel = threatLevel;
    }
    public void setMovementStrategy(MovementStrategy movementStrategy) {
        this.movementStrategy = movementStrategy;
    }

    //update the trajectory
    public void updateTrajectory(double timeStep) {
        double[] nextPosition = movementStrategy.calculateNextPosition(x, y, vx, vy, timeStep);
        this.x = (int) nextPosition[0];
        this.y = (int) nextPosition[1];
        this.vx = (int) nextPosition[2];
        this.vy = (int) nextPosition[3];
    }



}
