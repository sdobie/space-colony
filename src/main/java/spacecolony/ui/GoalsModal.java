package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.Goal;
import spacecolony.sim.GoalCatalog;

public class GoalsModal {
    public static void show(Component owner, Engine engine) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner), "Goals", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        for (Goal g : GoalCatalog.all()) {
            boolean done = engine.world().goals.achieved.contains(g.id());
            String text = String.format("%s %s  —  +%d credits  +%d research",
                done ? "✓" : "○", g.name(), g.creditReward(), g.researchReward());
            JLabel row = new JLabel(text);
            row.setOpaque(true);
            row.setBackground(UiColors.PANEL_BACKGROUND);
            Color fg = done ? new Color(120, 200, 130) : UiColors.FOREGROUND;
            row.setForeground(fg);
            row.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            list.add(row);
            JLabel desc = new JLabel("    " + g.description());
            desc.setForeground(UiColors.FOREGROUND_DIM);
            desc.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
            list.add(desc);
        }
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel();
        south.add(close);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setSize(500, 480);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }
}
