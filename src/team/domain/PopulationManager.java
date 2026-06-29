package team.domain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PopulationManager {
    private final List<Civilian> civilians = new ArrayList<>();
    private int idCounter = 1;
    private double timeSinceLastSpawn = 0;

    private static final double SPAWN_INTERVAL = 2.0; // כל כמה שניות לנסות לייצר אזרח
    private static final int    MAX_CIVILIANS   = 20;  // מקסימום אזרחים בסך הכל
    private static final double WORLD_WIDTH     = 1200.0;

    public void update(double timeStep, int groundY, List<Damageable> damageables) {
        timeSinceLastSpawn += timeStep;

        // 1. עדכון מיקומים של כל האזרחים הקיימים ומחיקת אלו שברחו מהמסך
        Iterator<Civilian> it = civilians.iterator();
        while (it.hasNext()) {
            Civilian c = it.next();
            c.update(timeStep, groundY);
            if (c.isOutOfBounds(WORLD_WIDTH)) {
                it.remove();
            }
        }

        // 2. יצירת אזרחים חדשים מדי פעם
        if (timeSinceLastSpawn >= SPAWN_INTERVAL) {
            timeSinceLastSpawn = 0;
            // 30% סיכוי להיווצרות אם חסרים אזרחים
            if (civilians.size() < MAX_CIVILIANS && Math.random() < 0.3) {
                spawnCivilian(damageables);
            }
        }
    }

    private void spawnCivilian(List<Damageable> damageables) {
        List<GroundAsset> buildings = new ArrayList<>();
        for (Damageable d : damageables) {
            if (d instanceof GroundAsset) {
                buildings.add((GroundAsset) d);
            }
        }

        if (buildings.isEmpty()) return; // אין לאן לרוץ

        // הגרלת מבנה מתוך רשימת המבנים והגרלת צד הגעה (שמאל או ימין)
        GroundAsset target = buildings.get(ThreadLocalRandom.current().nextInt(buildings.size()));
        double startX = Math.random() > 0.5 ? -50 : WORLD_WIDTH + 50;

        civilians.add(new Civilian(idCounter++, startX, target));
    }

    // ה-Backend יקרא לפונקציה הזו כשהוא מזהה פגיעה במבנה!
    // מי שהיה בתוך המבנה בורח, מי שהיה על הגג — עף באוויר!
    public void notifyBuildingHit(GroundAsset building) {
        for (Civilian c : civilians) {
            if (c.getTargetBuilding() != null && c.getTargetBuilding().getId() == building.getId()) {
                c.catchFireAndFlee();
            }
        }
    }

    public boolean isBuildingPopulated(GroundAsset building) {
        for (Civilian c : civilians) {
            if ((c.isHidingOrOnRoof())
                    && c.getTargetBuilding() != null
                    && c.getTargetBuilding().getId() == building.getId()) {
                return true;
            }
        }
        return false;
    }

    public List<Civilian> getCivilians() {
        return civilians;
    }

    public void reset() {
        civilians.clear();
    }
}