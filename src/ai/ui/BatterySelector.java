package ai.ui;

import team.domain.AbstractDefenseSystem;
import team.domain.Damageable;
import team.domain.InterceptorBattery;
import team.domain.LaserBattery;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates all logic related to selecting a defense battery from the UI.
 *
 * <p>Manages:
 * <ul>
 *   <li>The {@link JComboBox} showing active battery IDs.</li>
 *   <li>The info label showing status and ammo.</li>
 *   <li>Cycling selection with arrow keys.</li>
 * </ul>
 */
public class BatterySelector {

    private final JComboBox<Integer> comboBox;
    private final JLabel             infoLabel;
    private final UiState            uiState;

    public BatterySelector(UiState uiState) {
        this.uiState = uiState;

        comboBox  = new JComboBox<>();
        infoLabel = new JLabel("Status: No Battery Available | Ammo: 0");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.BOLD, 14f));

        comboBox.addActionListener(e -> {
            Integer selected = (Integer) comboBox.getSelectedItem();
            if (selected != null) {
                uiState.setSelectedBatteryId(selected);
                refreshInfoLabel(null); // will look up from uiState + scene externally
            }
        });
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public JComboBox<Integer> getComboBox()  { return comboBox; }
    public JLabel             getInfoLabel() { return infoLabel; }

    // ── Update combo items from current scene ─────────────────────────────────

    public void updateItems(List<Damageable> damageables) {
        List<Integer> activeIds = collectActiveIds(damageables);

        if (!needsRefresh(activeIds)) {
            autoSelectFirst(activeIds);
            return;
        }

        Object previousSelection = comboBox.getSelectedItem();
        comboBox.removeAllItems();
        for (int id : activeIds) comboBox.addItem(id);

        if (previousSelection != null && activeIds.contains(previousSelection)) {
            comboBox.setSelectedItem(previousSelection);
            uiState.setSelectedBatteryId((Integer) previousSelection);
        } else if (!activeIds.isEmpty()) {
            comboBox.setSelectedIndex(0);
            uiState.setSelectedBatteryId(activeIds.get(0));
        }
    }

    /** Cycle selection by {@code delta} positions (±1). */
public void cycleSelection(int delta, List<Damageable> damageables) {
    if (comboBox.getItemCount() == 0) return;
    
    try {
        int current = Math.max(0, comboBox.getSelectedIndex());
        int next = (current + delta + comboBox.getItemCount()) % comboBox.getItemCount();
        boolean isNextActive = canIdShoot((Integer) comboBox.getItemAt(next), damageables);
        System.out.println("[INFO] Attempting to cycle battery selection from index " + current + " to index " + next + " which is " + (isNextActive ? "active" : "inactive") + ".");
        int safetyCounter = 0;
        int maxItems = comboBox.getItemCount();
        
        // Loop until an active battery is found, or we checked all items (prevent deadlock)
        while (!isNextActive && safetyCounter < maxItems) {
            System.out.println("[INFO] Skipping inactive battery ID: " + comboBox.getItemAt(next));
            next = (next + delta + maxItems) % maxItems;
            safetyCounter++;
            
            if (safetyCounter >= maxItems) {
                System.err.println("[WARNING] No active batteries available to select.");
                return; // Exit to prevent infinite UI thread freeze
            }
            isNextActive = canIdShoot((Integer) comboBox.getItemAt(next), damageables);
        }
        
        comboBox.setSelectedIndex(next);
        Integer selected = (Integer) comboBox.getSelectedItem();
        if (selected != null && uiState != null) {
            uiState.setSelectedBatteryId(selected);
            System.out.println("[INFO] Battery selection successfully cycled to active ID: " + selected);
        }
        
    } catch (Exception e) {
        System.err.println("[ERROR] Failed to cycle battery selection: " + e.getMessage());
    }
}
    public boolean canIdShoot(int id, List<Damageable> damageables) {
         AbstractDefenseSystem system = findSystem(id, damageables);
         return system.isActive() && system.getInventory()>0;
    }   

    /** Update the info label given the current scene. */
    public void refreshInfoLabel(List<Damageable> damageables) {
        int selectedId = uiState.getSelectedBatteryId();

        if (selectedId == -1) {
            infoLabel.setText("Status: No Defense System Selected | Ammo: 0");
            infoLabel.setForeground(Color.BLACK);
            return;
        }

        AbstractDefenseSystem system = findSystem(selectedId, damageables);

        if (system == null) {
            infoLabel.setText("Status: No Defense System Available | Ammo: 0");
            infoLabel.setForeground(Color.BLACK);
            return;
        }

        String status   = system.isActive() ? "ACTIVE" : "DAMAGED";
        String ammoInfo = buildAmmoInfo(system);

        infoLabel.setText(String.format("System %d SELECTED | %s | %s", selectedId, status, ammoInfo));
        infoLabel.setForeground(system.isActive() ? new Color(20, 90, 180) : new Color(200, 40, 40));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Integer> collectActiveIds(List<Damageable> damageables) {
        List<Integer> ids = new ArrayList<>();
        if (damageables == null) return ids;
        for (Damageable d : damageables) {
            if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) d).isActive()) {
                ids.add(((AbstractDefenseSystem) d).getId());
            }
        }
        ids.sort(Integer::compare);
        return ids;
    }

    private boolean needsRefresh(List<Integer> activeIds) {
        if (comboBox.getItemCount() != activeIds.size()) return true;
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            if (!activeIds.contains(comboBox.getItemAt(i))) return true;
        }
        return false;
    }

    private void autoSelectFirst(List<Integer> activeIds) {
        if (uiState.getSelectedBatteryId() == -1 && !activeIds.isEmpty()) {
            comboBox.setSelectedIndex(0);
            uiState.setSelectedBatteryId(activeIds.get(0));
        }
    }

    private AbstractDefenseSystem findSystem(int id, List<Damageable> damageables) {
        if (damageables == null) return null;
        for (Damageable d : damageables) {
            if (d instanceof AbstractDefenseSystem && ((AbstractDefenseSystem) d).getId() == id) {
                return (AbstractDefenseSystem) d;
            }
        }
        return null;
    }

    private String buildAmmoInfo(AbstractDefenseSystem system) {
        if (system instanceof InterceptorBattery) {
            return String.format("Interceptors: %d", ((InterceptorBattery) system).getMissilesAvailable());
        }
        if (system instanceof LaserBattery) {
            return String.format("Laser Charges: %d", ((LaserBattery) system).getLaserChargesAvailable());
        }
        return "Ammo: N/A";
    }
}
