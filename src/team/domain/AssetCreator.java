package team.domain;

public interface AssetCreator {
    // Receives an ID, startX, and groundY and returns a fully initialized Damageable object
    Damageable create(int id, int startX, int groundY);
}