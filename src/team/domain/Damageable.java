package team.domain;

public interface Damageable {
    public void tookHit();
    public boolean checkHit(int x, int y);
    public int getId();
    public int getX();
    public int getY();
}
