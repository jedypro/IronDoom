package team.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class AssetSpawner {
    
    private final List<AssetCreator> regularAssets = new ArrayList<>();
    private final List<AssetCreator> defenseSystems = new ArrayList<>();
    
    private final List<Integer> occupiedXPositions = new ArrayList<>();
    
    private int assetIdCounter = 1;
    private final int SAFE_MARGIN = 160;

    // פונקציות רישום
    public void registerRegularAsset(AssetCreator creator) {
        regularAssets.add(creator);
    }

    public void registerDefenseSystem(AssetCreator creator) {
        defenseSystems.add(creator);
    }

    // הגרלת נכסים רגילים - הכמות גדלה עם הרמה
    public List<Damageable> spawnRegularAssets(int level, int groundY) {
        List<Damageable> spawned = new ArrayList<>();
        if (regularAssets.isEmpty()) return spawned;

        int count = 1 + level; 
        spawnFromList(count, regularAssets, spawned, groundY);
        return spawned;
    }

    // הגרלת מערכות הגנה - הכמות גדלה עם הרמה
    public List<Damageable> spawnDefenseSystems(int level, int groundY) {
        List<Damageable> spawned = new ArrayList<>();
        if (defenseSystems.isEmpty()) return spawned;

        int count = 1 + (level / 2); 
        spawnFromList(count, defenseSystems, spawned, groundY);
        return spawned;
    }

    // פונקציית עזר פנימית המבצעת את ההגרלה והיצירה
    private void spawnFromList(int count, List<AssetCreator> creators, List<Damageable> spawned, int groundY) {
        for (int i = 0; i < count; i++) {
            int startX = findSafeX();
            if (startX != -1) {
                occupiedXPositions.add(startX); // סימון המיקום כתפוס
                int index = ThreadLocalRandom.current().nextInt(creators.size());
                spawned.add(creators.get(index).create(assetIdCounter++, startX, groundY));
            }
        }
    }

    // פונקציית עזר למציאת קואורדינטת X שאינה חופפת לנכסים קיימים
    private int findSafeX() {
        for (int attempts = 0; attempts < 50; attempts++) {
            int candidateX = ThreadLocalRandom.current().nextInt(50, 1450);
            boolean isSafe = true;

            for (int occupiedX : occupiedXPositions) {
                if (Math.abs(occupiedX - candidateX) < SAFE_MARGIN) {
                    isSafe = false;
                    break;
                }
            }

            if (isSafe) {
                return candidateX;
            }
        }
        return -1; // מחזיר -1 אם לא נמצא מיקום פנוי לאחר 50 ניסיונות
    }
}