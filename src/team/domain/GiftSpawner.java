package team.domain;

import java.util.Random;

public class GiftSpawner {
    private final Random random = new Random();
    private int giftIdCounter = 20000; // Distinct ID range to separate from threats
    private final double screenWidth;

    // constructor
    public GiftSpawner(double screenWidth) {
        this.screenWidth = screenWidth;
    }

    /**
     * Generates a new Gift entity with a randomized drop location and reward type.
     * * @return A newly instantiated Gift object.
     */
    public Gift spawnGift(boolean includeRepairType) {
        // 1. Assign a unique ID
        int currentId = giftIdCounter++;

        // 2. Generate a random X-coordinate within screen bounds (leaving a 50px margin)
        double randomX = 50 + random.nextDouble() * (screenWidth - 100);

        // 3. Initialize the movement strategy (Parachute) for a controlled, swaying descent
        MovementStrategy parachute = new ParachuteStrategy();
        double rand = random.nextDouble();
        if(includeRepairType) {
            // 4. Determine the gift type with a 20% chance for a new battery, 50% for ammo refill, and 30% for repair
            GiftType type = (rand < 0.3) ? GiftType.NEW_BATTERY : 
                            (rand < 0.7) ? GiftType.AMMO_REFILL : GiftType.BATTERY_REPAIR;
            return new Gift(currentId, randomX, 0, parachute, type);
        }
        else {
        // 4. Determine the gift type
        GiftType type = (rand< 0.3) ? GiftType.NEW_BATTERY :  GiftType.AMMO_REFILL;
        return new Gift(currentId, randomX, 0, parachute, type);
    
    }
        
    }
}