package spacecolony.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.concurrent.ThreadLocalRandom;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import spacecolony.ui.UiColors;

/** New Game: pick a seed (Plan 6 §3.6). Used by the title screen and File → New Game. */
public final class NewGameDialog {
    private NewGameDialog() {}

    /** Returns the chosen seed, or null when cancelled. */
    public static Long show(Component owner) {
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        if (w == null && owner instanceof Window ow) w = ow;
        JDialog d = new JDialog(w, "New Game", JDialog.ModalityType.APPLICATION_MODAL);
        Long[] result = { null };

        JTextField seed = new JTextField(Long.toString(randomSeed()), 20);
        JButton randomize = new JButton("Randomize");
        randomize.addActionListener(e -> seed.setText(Long.toString(randomSeed())));
        JLabel error = new JLabel(" ");
        error.setForeground(UiColors.ERROR);
        JButton start = new JButton("Start");
        JButton cancel = new JButton("Cancel");
        Runnable validate = () -> {
            String msg = validate(seed.getText());
            error.setText(msg == null ? " " : msg);
            start.setEnabled(msg == null);
        };
        seed.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { validate.run(); }
            public void removeUpdate(DocumentEvent e) { validate.run(); }
            public void changedUpdate(DocumentEvent e) { validate.run(); }
        });
        start.addActionListener(e -> { result[0] = Long.parseLong(seed.getText().trim()); d.dispose(); });
        cancel.addActionListener(e -> d.dispose());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel("Seed:"), c);
        c.gridx = 1; form.add(seed, c);
        c.gridx = 2; form.add(randomize, c);
        c.gridx = 1; c.gridy = 1; c.gridwidth = 2; form.add(error, c);
        form.add(new JLabel("The seed shapes every planet's surface and resources."), gbc(1, 2));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(start);
        d.getContentPane().add(form, BorderLayout.CENTER);
        d.getContentPane().add(buttons, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(start);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result[0];
    }

    private static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y; c.gridwidth = 2;
        c.insets = new Insets(0, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    /** Why {@code raw} can't be a seed, or null when it can. */
    static String validate(String raw) {
        try {
            Long.parseLong(raw == null ? "" : raw.trim());
            return null;
        } catch (NumberFormatException e) {
            return "Seed must be a whole number";
        }
    }

    private static long randomSeed() {
        return ThreadLocalRandom.current().nextLong(1, 1_000_000_000_000L);
    }
}
