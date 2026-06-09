package team.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AssetSpawner {
    
    private final List<AssetCreator> regularAssets = new ArrayList<>();
    
    private static class RegisteredCreator {
        AssetCreator creator;
        int maxSpawns;
        int currentSpawns;
        
        RegisteredCreator(AssetCreator creator, int maxSpawns) {
            this.creator = creator;
            this.maxSpawns = maxSpawns;
            this.currentSpawns = 0;
        }
    }
    
    private final List<RegisteredCreator> defenseSystems = new ArrayList<>();
    
    private final List<Integer> occupiedXPositions = new ArrayList<>();
    
    private int assetIdCounter = 1;
    private final int SAFE_MARGIN = 200;

    // פונקציות רישום
    public void registerRegularAsset(AssetCreator creator) {
        regularAssets.add(creator);
    }

    public void registerDefenseSystem(AssetCreator creator) {
        defenseSystems.add(new RegisteredCreator(creator, Integer.MAX_VALUE));
    }
    
    public void registerDefenseSystem(AssetCreator creator, int maxSpawns) {
        defenseSystems.add(new RegisteredCreator(creator, maxSpawns));
    }

    // הגרלת נכסים רגילים - הכמות גדלה עם הרמה
    public List<Damageable> spawnRegularAssets(int level, int groundY) {
        List<Damageable> spawned = new ArrayList<>();
        if (regularAssets.isEmpty()) return spawned;

        int count = 1 + level; 
        for (int i = 0; i < count; i++) {
            int startX = findSafeX();
            if (startX != -1) {
                occupiedXPositions.add(startX); // Mark the position as occupied
                int index = ThreadLocalRandom.current().nextInt(regularAssets.size());
                spawned.add(regularAssets.get(index).create(assetIdCounter++, startX, groundY));
            }
        }
        return spawned;
    }

    // הגרלת מערכות הגנה - הכמות גדלה עם הרמה
    public List<Damageable> spawnDefenseSystems(int level, int groundY) {
        List<Damageable> spawned = new ArrayList<>();
        if (defenseSystems.isEmpty()) return spawned;

        int count = 1 + (level / 2); 
        
        // Guarantee 1 LaserBattery if level >= 3 (assuming it's registered)
        if (level >= 3) {
            for (RegisteredCreator rc : defenseSystems) {
                if (rc.creator.create(0, 0, 0) instanceof LaserBattery) {
                    if (rc.currentSpawns < rc.maxSpawns) {
                        int startX = findSafeX();
                        if (startX != -1) {
                            occupiedXPositions.add(startX);
                            rc.currentSpawns++;
                            spawned.add(rc.creator.create(assetIdCounter++, startX, groundY));
                            count--; // Decrement remaining count
                        }
                    }
                    break;
                }
            }
        }
        
        for (int i = 0; i < count; i++) {
            int startX = findSafeX();
            if (startX != -1) {
                occupiedXPositions.add(startX); // Mark the position as occupied
                
                // Get available creators
                List<RegisteredCreator> available = new ArrayList<>();
                for (RegisteredCreator rc : defenseSystems) {
                    if (rc.currentSpawns < rc.maxSpawns) {
                        available.add(rc);
                    }
                }
                
                if (!available.isEmpty()) {
                    int index = ThreadLocalRandom.current().nextInt(available.size());
                    RegisteredCreator chosen = available.get(index);
                    chosen.currentSpawns++;
                    spawned.add(chosen.creator.create(assetIdCounter++, startX, groundY));
                }
            }
        }
        return spawned;
    }

    // פונקציית עזר למציאת קואורדינטת X שאינה חופפת לנכסים קיימים
    private int findSafeX() {
        for (int attempts = 0; attempts < 50; attempts++) {
            int candidateX = ThreadLocalRandom.current().nextInt(150, 1300);
            boolean isSafe = true;

            for (int occupiedX : occupiedXPositions) {
                if (Math.abs(occupiedX - candidateX) <  SAFE_MARGIN) {
                    isSafe = false;
                    break;
                }
            }

            if (isSafe) {
                return candidateX;
            }
        }
        return -1; // Returns -1 if no free position is found after 50 attempts
    }
}