package team.domain;

import base.IdentifiedObject;

public class GroundAsset extends IdentifiedObject implements Damageable {
    private final String name;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final GameState gameState;

    public GroundAsset(int id, String name, int x, int y, int width, int height, GameState gameState) {
        super(id);
        this.name = name;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.gameState = gameState;
    }

    public String getName() {
        return name;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public boolean checkHit(int px, int py) {
        return px >= x && px <= x + width && py >= y && py <= y + height;
    }

    @Override
    public void tookHit() {
        //System.out.println("GroundAsset " + name + " took a hit.");
        gameState.updateScore(-50);
        //System.out.println("Game score is now " + gameState.getScore());
    }
}
