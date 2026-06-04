package team.domain;

public interface AssetCreator {
    // Receives an ID and returns a fully initialized Damageable object
    Damageable create(int id);
}