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
    static JLabel header(Engine engine) {
        int achieved = 0;
        long credits = 0, research = 0;
        for (Goal g : GoalCatalog.all()) {
            if (!engine.world().goals.achieved.contains(g.id())) continue;
            achieved++;
            credits += g.creditReward();
            research += g.researchReward();
        }
        JLabel l = new JLabel(String.format("%d / %d achieved  ·  earned +%d credits  +%d research",
            achieved, GoalCatalog.all().size(), credits, research));
        l.setOpaque(true);
        l.setBackground(UiColors.PANEL_BACKGROUND);
        l.setForeground(UiColors.FOREGROUND);
        l.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.PANEL_BORDER),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        return l;
    }

    public static void show(Component owner, Engine engine) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner), "Goals", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        dlg.add(header(engine), BorderLayout.NORTH);
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        for (Goal g : GoalCatalog.all()) {
            boolean done = engine.world().goals.achieved.contains(g.id());
            String text = String.format("%s %s  —  %s", done ? "✓" : "○", g.name(), rewardText(g));
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

    /** Only the rewards a goal actually pays, e.g. "+5000 credits" rather than "+5000 credits  +0 research". */
    static String rewardText(Goal g) {
        StringBuilder sb = new StringBuilder();
        if (g.creditReward() > 0) sb.append("+").append(g.creditReward()).append(" credits");
        if (g.researchReward() > 0) {
            if (sb.length() > 0) sb.append("  ");
            sb.append("+").append(g.researchReward()).append(" research");
        }
        return sb.length() == 0 ? "no reward" : sb.toString();
    }
}
