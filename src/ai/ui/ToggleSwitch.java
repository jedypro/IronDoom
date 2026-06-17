package ai.ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A simple iOS-style toggle-switch Swing component.
 *
 * <p>Fires {@link ActionListener} events when the state changes,
 * making it a drop-in replacement for a check-box in settings panels.</p>
 */
public class ToggleSwitch extends JComponent {

    private static final int COMPONENT_WIDTH  = 50;
    private static final int COMPONENT_HEIGHT = 25;

    private static final Color ENABLED_COLOR  = new Color(70, 150, 230);
    private static final Color DISABLED_COLOR = new Color(120, 120, 120);
    private static final Color KNOB_COLOR     = Color.WHITE;

    private boolean selected;

    public ToggleSwitch(boolean initialState) {
        this.selected = initialState;
        setPreferredSize(new Dimension(COMPONENT_WIDTH, COMPONENT_HEIGHT));
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggle();
            }
        });
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            repaint();
            fireActionPerformed();
        }
    }

    public void toggle() {
        setSelected(!selected);
    }

    public void addActionListener(ActionListener listener) {
        listenerList.add(ActionListener.class, listener);
    }

    public ActionListener[] getActionListeners() {
        return listenerList.getListeners(ActionListener.class);
    }

    // ── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int arc         = COMPONENT_HEIGHT;
        int knobDiam    = COMPONENT_HEIGHT - 4;
        int knobX       = selected ? COMPONENT_WIDTH - knobDiam - 2 : 2;

        g2d.setColor(selected ? ENABLED_COLOR : DISABLED_COLOR);
        g2d.fillRoundRect(0, 0, COMPONENT_WIDTH, COMPONENT_HEIGHT, arc, arc);

        g2d.setColor(KNOB_COLOR);
        g2d.fillOval(knobX, 2, knobDiam, knobDiam);

        g2d.dispose();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void fireActionPerformed() {
        ActionEvent evt = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle");
        for (ActionListener listener : getActionListeners()) {
            listener.actionPerformed(evt);
        }
    }
}
