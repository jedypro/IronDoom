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
    private final int SAFE_MARGIN = 40; // מרווח ביטחון מסביב למבנה

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
            int startX = findSafeX(240); // רוחב מקסימלי אפשרי של מבנה כדי להבטיח מציאת שטח פנוי
            if (startX != -1) {
                int index = ThreadLocalRandom.current().nextInt(regularAssets.size());
                Damageable newAsset = regularAssets.get(index).create(assetIdCounter++, startX, groundY);
                spawned.add(newAsset);
                
                if (newAsset instanceof GroundAsset) {
                    int width = ((GroundAsset) newAsset).getWidth();
                    occupiedXPositions.add(startX); // שמירת נקודת התחלה
                    occupiedXPositions.add(startX + width); // שמירת נקודת סיום
                }
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
                        int startX = findSafeX(60); // 60 רוחב משוער של סוללת לייזר
                        if (startX != -1) {
                            rc.currentSpawns++;
                            // ל-UI נקודת ה-X של סוללה היא האמצע שלה, לכן נעביר startX + 30
                            spawned.add(rc.creator.create(assetIdCounter++, startX + 30, groundY));
                            occupiedXPositions.add(startX); // נקודת התחלה
                            occupiedXPositions.add(startX + 60); // נקודת סיום
                            count--; // Decrement remaining count
                        }
                    }
                    break;
                }
            }
        }
        
        for (int i = 0; i < count; i++) {
            int startX = findSafeX(60); // 60 רוחב משוער של סוללה רגילה
            if (startX != -1) {
                
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
                    // כיוון מדויק לאמצע הסוללה
                    Damageable newDefense = chosen.creator.create(assetIdCounter++, startX + 30, groundY);
                    spawned.add(newDefense);
                    occupiedXPositions.add(startX); // נקודת התחלה
                    occupiedXPositions.add(startX + 60); // נקודת סיום
                }
            }
        }
        return spawned;
    }

    // פונקציית עזר למציאת קואורדינטת X שאינה חופפת לנכסים קיימים
    private int findSafeX(int expectedWidth) {
        for (int attempts = 0; attempts < 50; attempts++) {
            int candidateX = ThreadLocalRandom.current().nextInt(40, 1160 - expectedWidth);
            boolean isSafe = true;

            for (int i = 0; i < occupiedXPositions.size(); i += 2) {
                int startX = occupiedXPositions.get(i);
                int endX = occupiedXPositions.get(i + 1);
                // מונע חפיפה - האם ההתחלה של החדש נוגעת בסוף של הקיים (כולל הריווח) ולהיפך
                if (candidateX < endX + SAFE_MARGIN && candidateX + expectedWidth > startX - SAFE_MARGIN) {
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