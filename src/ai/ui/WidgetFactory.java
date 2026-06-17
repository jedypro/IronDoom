package ai.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Font;

/**
 * Factory for consistently-styled Swing widgets.
 *
 * <p>Centralises button/widget construction so every panel uses the same
 * look without copy-pasting style code.</p>
 */
public final class WidgetFactory {

    private WidgetFactory() {}

    /**
     * Creates the standard "action" button used on intro, settings and
     * level-complete screens.
     *
     * @param text     label to display
     * @param fontSize point size for the button font
     */
    public static JButton createStyledButton(String text, int fontSize) {
        JButton button = new JButton(text);
        button.setFont(button.getFont().deriveFont(Font.BOLD, (float) fontSize));
        button.setPreferredSize(new java.awt.Dimension(170, 52));
        button.setBackground(UIConstants.COLOR_PRIMARY);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        button.setOpaque(true);
        return button;
    }

    /**
     * Creates a red "FIRE!" button.
     */
    public static JButton createFireButton() {
        JButton btn = new JButton("FIRE!");
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 16f));
        btn.setBackground(Color.RED);
        btn.setForeground(Color.WHITE);
        return btn;
    }
}
