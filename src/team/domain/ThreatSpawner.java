package team.domain;

import java.util.ArrayList;
import java.util.Random;
import java.util.List;

public class ThreatSpawner {
    private final List<ThreatCreator> availableThreats = new ArrayList<>();
    private double spawnInterval;
    private double timeSinceLastSpawn;
    private GameState gameState;
    private final Random random = new Random();
    private int threatIdCounter = 10000; 

    //constructor
    public ThreatSpawner(GameState gameState) {
        this.gameState = gameState;
        double level = this.gameState.getLevel();
        this.spawnInterval = 1/(1+0.25*(level-1)); // for the desired spawn rate
        this.timeSinceLastSpawn = 0;
    }

    //getters and setters
    public double getSpawnInterval() {
        return spawnInterval;
    }
    public double getTimeSinceLastSpawn() {
        return timeSinceLastSpawn;
    }
    public List<ThreatCreator> getAvailableThreats() {
        return availableThreats;
    }
    public void setSpawnInterval(double spawnInterval) {
        this.spawnInterval = spawnInterval;
    }
    public void setTimeSinceLastSpawn(double timeSinceLastSpawn) {
        this.timeSinceLastSpawn = timeSinceLastSpawn;
    }

    //add new threat creator to the list of available threats
    public void registerThreatType(ThreatCreator creator) {
        this.availableThreats.add(creator);
    }

    // generate a random threat of a registered type
    public AbstractThreat createRandomThreat(){
        if (availableThreats.isEmpty()) {
            return null; 
        }

        // 1. מחלקים תעודת זהות
        int currentId = threatIdCounter++;

        // 2. מגרילים סוג איום מתוך רשימת הרשומים
        int index = random.nextInt(availableThreats.size());
    
        // 3. מפעילים את המתכון שנבחר (המתכון כבר יעשה את כל ההגרלות בפנים!)
        return availableThreats.get(index).create(currentId);
    }

    public AbstractThreat spawnThreat(double timeStep) {
        timeSinceLastSpawn += timeStep;
        if (timeSinceLastSpawn >= spawnInterval) {
            AbstractThreat newThreat = createRandomThreat();
            timeSinceLastSpawn = 0; // Reset the timer after spawning
            return newThreat;
        }
        return null; // Not time to spawn yet
    }
    
}
