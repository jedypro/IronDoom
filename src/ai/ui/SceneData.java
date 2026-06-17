package ai.ui;

import team.domain.AbstractThreat;
import team.domain.Civilian;
import team.domain.Damageable;
import team.domain.DefenseEntity;
import team.domain.Gift;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe snapshot of the game world handed to the renderer each frame.
 *
 * <p>All lists are replaced atomically so the canvas never reads a
 * half-updated scene.</p>
 */
public class SceneData {

    private volatile List<AbstractThreat>  threats      = Collections.emptyList();
    private volatile List<Damageable>      damageables  = Collections.emptyList();
    private volatile List<DefenseEntity>   interceptors = Collections.emptyList();
    private volatile List<Gift>            gifts        = Collections.emptyList();
    private volatile List<Civilian>        civilians    = Collections.emptyList();

    // ── Update (called from game-update thread, not EDT) ─────────────────────

    public synchronized void update(
            List<AbstractThreat>  threats,
            List<Damageable>      damageables,
            List<DefenseEntity>   interceptors,
            List<Gift>            gifts) {

        this.threats      = new ArrayList<>(threats);
        this.damageables  = new ArrayList<>(damageables);
        this.interceptors = new ArrayList<>(interceptors);
        this.gifts        = new ArrayList<>(gifts);
    }

    public synchronized void setCivilians(List<Civilian> civilians) {
        this.civilians = new ArrayList<>(civilians);
    }

    // ── Read (called from EDT / canvas) ──────────────────────────────────────

    public List<AbstractThreat> getThreats()      { return threats; }
    public List<Damageable>     getDamageables()  { return damageables; }
    public List<DefenseEntity>  getInterceptors() { return interceptors; }
    public List<Gift>           getGifts()        { return gifts; }
    public List<Civilian>       getCivilians()    { return civilians; }
}
