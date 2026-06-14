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
    public Gift spawnGift() {
        // 1. Assign a unique ID
        int currentId = giftIdCounter++;

        // 2. Generate a random X-coordinate within screen bounds (leaving a 50px margin)
        double randomX = 50 + random.nextDouble() * (screenWidth - 100);

        // 3. Initialize the movement strategy (Parachute) for a controlled, swaying descent
        MovementStrategy parachute = new ParachuteStrategy();

        // 4. Determine the gift type (e.g., 10% chance for a new battery, 90% for ammo refill)
        GiftType type = (random.nextDouble() < 0.3) ? GiftType.NEW_BATTERY : GiftType.AMMO_REFILL;
        
        // 5. Instantiate and return the payload (Y starts at 0 - top of the screen)
        return new Gift(currentId, randomX, 0, parachute, type);
    }
}