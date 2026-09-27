package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.EngineListener;
import spacecolony.sim.Goal;
import spacecolony.sim.GoalCatalog;
import spacecolony.sim.GoalCategory;
import spacecolony.sim.World;

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
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane();
        dlg.add(scroll, BorderLayout.CENTER);
        // Rebuilt on every world change so the progress bars move while the game runs.
        Component[] north = new Component[1];
        Runnable rebuild = () -> {
            if (north[0] != null) dlg.remove(north[0]);
            north[0] = header(engine);
            dlg.add(north[0], BorderLayout.NORTH);
            java.awt.Point pos = scroll.getViewport().getViewPosition();
            scroll.setViewportView(buildList(engine.world()));
            scroll.getViewport().setViewPosition(pos);
            dlg.revalidate();
            dlg.repaint();
        };
        rebuild.run();
        EngineListener listener = e -> {
            if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.WorldReplaced) rebuild.run();
        };
        engine.addListener(listener);
        dlg.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { engine.removeListener(listener); }
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel();
        south.add(close);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setSize(500, 560);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    /** Goals grouped under a header per category, each with its reward, description and progress bar. */
    static JPanel buildList(World w) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        for (GoalCategory cat : GoalCategory.values()) {
            List<Goal> goals = GoalCatalog.all().stream().filter(g -> g.category() == cat).toList();
            if (goals.isEmpty()) continue;
            JLabel catHeader = label(categoryName(cat), UiColors.INFO);
            catHeader.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
            list.add(catHeader);
            for (Goal g : goals) list.add(goalRow(w, g));
        }
        return list;
    }

    private static JPanel goalRow(World w, Goal g) {
        boolean done = w.goals.achieved.contains(g.id());
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(UiColors.PANEL_BACKGROUND);
        row.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label(String.format("%s %s  —  %s", done ? "✓" : "○", g.name(), rewardText(g)),
            done ? new Color(120, 200, 130) : UiColors.FOREGROUND));
        row.add(label("    " + g.description(), UiColors.FOREGROUND_DIM));
        JProgressBar bar = new JProgressBar(0, 1000);
        bar.setValue((int) (g.displayProgress(w) * 1000));
        bar.setStringPainted(true);
        bar.setString(progressText(g, w));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(bar);
        return row;
    }

    private static String categoryName(GoalCategory c) {
        String n = c.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    /** The fraction behind a goal's bar: "100 / 1,000", "3 / 10 ships", "1 / 5 bodies", "not yet" or "done". */
    static String progressText(Goal g, World w) {
        if (w.goals.achieved.contains(g.id())) return "done";
        return switch (g.id()) {
            case "pop-1000"    -> String.format(Locale.ROOT, "%,d / %,d", GoalCatalog.totalPop(w), 1000);
            case "pop-10000"   -> String.format(Locale.ROOT, "%,d / %,d", GoalCatalog.totalPop(w), 10000);
            case "fleet-10"    -> w.ships.size() + " / 10 ships";
            case "five-bodies" -> GoalCatalog.settledBodies(w) + " / 5 bodies";
            case "first-mars-colony", "self-sufficient-mars", "belt-presence", "jovian-presence" -> "not yet";
            default -> String.format(Locale.ROOT, "%.0f%%", g.displayProgress(w) * 100.0);
        };
    }

    private static JLabel label(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
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
