package team.domain;

/**
 * A generic contract for any defensive entity spawned into the simulation
 * (e.g., Interceptor Missiles, Laser Beams, Homing Interceptors).
 */
public interface DefenseEntity {

    /**
     * Gets the unique identifier of this entity.
     * Essential for tracking updates between the Backend and UI.
     */
    int getId();

    /**
     * Updates the physical state and position of the entity over time.
     * @param dt Delta time (elapsed time since the last frame)
     */
    void updatePosition(double dt);

    /**
     * Determines whether the entity is still active in the world.
     * If false, the game loop will automatically remove it.
     */
    boolean isActive();

    /**
     * Forces the entity to terminate, triggering explosions or deactivation.
     */
    void explode();

    /**
     * Gets the horizontal coordinate for UI rendering.
     */
    int getX();

    /**
     * Gets the vertical coordinate for UI rendering.
     */
    int getY();
}