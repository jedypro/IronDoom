package team.domain;

public class Gift {
    private int id;
    private double x;
    private double y;
    private int width = 30;
    private int height = 30;
    private MovementStrategy movementStrategy;
    private GiftType giftType;

    public Gift(int id, double x, double y, MovementStrategy strategy, GiftType type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.movementStrategy = strategy;
        this.giftType = type;
    }

    public void updatePosition(double timeStep) {
        //updating position based on movement strategy
        double[] newPos = movementStrategy.calculateNextPosition(x, y, 0, 0, timeStep);
        this.x = newPos[0];
        this.y = newPos[1];
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getGiftType() { return giftType.name(); }
}