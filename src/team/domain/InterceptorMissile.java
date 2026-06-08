package team.domain;

import base.IdentifiedObject;

public class InterceptorMissile extends IdentifiedObject implements DefenseEntity {
    
    private double x;
    private double y;
    private double vx;
    private double vy;
    private MovementStrategy movementStrategy;
    boolean active=true;


    // Constructor - initializes starting position and velocities
    public InterceptorMissile(double x, double y, double vx, double vy) {
        // Generates a globally unique hash number automatically
        super(java.util.UUID.randomUUID().hashCode()); 
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
}

    // Main update method called on every game tick
    public void updatePosition(double dt) {
        if (movementStrategy != null && active) {
            double [] nextPosition = movementStrategy.calculateNextPosition(this.getX(), this.getY(), this.getVx(), this.getVy(), dt);
            this.x = (int) nextPosition[0];
            this.y = (int) nextPosition[1];
            this.vx = (int) nextPosition[2];
            this.vy = (int) nextPosition[3];
        }
    }
   
    // Deactivates the missile upon impact or out of bounds
    public void explode() {
        this.active = false;
    }

    // Assigns the specific movement physics (Strategy Pattern)
    public void setMovementStrategy(MovementStrategy strategy) {
        this.movementStrategy = strategy;
    }

    // Getters & Setters
    public int getX() {
        return (int) x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public int getY() {
        return (int) y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getVx() {
        return vx;
    }

    public void setVx(double vx) {
        this.vx = vx;
    }

    public double getVy() {
        return vy;
    }

    public void setVy(double vy) {
        this.vy = vy;
    }

    public boolean isActive() {
        return active;
    }
}