package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.Tech;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechState;
import spacecolony.sim.commands.QueueResearchCommand;

public class TechModal {
    private static final Color DELTA_GOOD = new Color(120, 200, 130);

    public static void show(Component owner, Engine engine) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner), "Tech Tree", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        dlg.add(header(engine.world().tech), BorderLayout.NORTH);
        dlg.add(new JScrollPane(buildList(engine, dlg::dispose)), BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel();
        south.add(close);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setSize(560, 600);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    static JPanel header(TechState tech) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiColors.PANEL_BACKGROUND);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.PANEL_BORDER),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        String active = "No research active";
        if (tech.activeId != null && TechCatalog.get(tech.activeId) != null) {
            Tech t = TechCatalog.get(tech.activeId);
            active = String.format("Researching %s: %d / %d pts",
                t.name(), (int) tech.accumulatedPoints, t.researchCost());
        }
        JLabel left = new JLabel(active + "  ·  " + tech.researched.size() + "/" + TechCatalog.all().size() + " researched");
        left.setForeground(UiColors.FOREGROUND);
        JLabel right = new JLabel(String.format("Morale ceiling %.2f", TechEffects.moraleCeiling(tech)));
        right.setForeground(UiColors.FOREGROUND_DIM);
        p.add(left, BorderLayout.WEST);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    /** One block per tech: title line, description, then what it changes. */
    static JPanel buildList(Engine engine, Runnable onQueued) {
        TechState tech = engine.world().tech;
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        for (Tech t : TechCatalog.all()) {
            boolean done = tech.researched.contains(t.id());
            boolean active = t.id().equals(tech.activeId);
            List<String> missing = missingPrereqs(tech, t);
            boolean locked = !done && !missing.isEmpty();

            JPanel row = new JPanel();
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setOpaque(true);
            row.setBackground(active ? UiColors.SELECTION : UiColors.PANEL_BACKGROUND);
            row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.PANEL_BORDER),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            Color fg = done || locked ? UiColors.FOREGROUND_DIM : UiColors.FOREGROUND;
            row.add(label(titleLine(tech, t, done, active, missing), fg));
            row.add(label("  " + t.description(), UiColors.FOREGROUND_DIM));
            for (TechEffects.Delta d : TechEffects.deltas(tech, t.id())) {
                Color c = done ? UiColors.FOREGROUND_DIM : improves(d) ? DELTA_GOOD : UiColors.WARNING;
                row.add(label("  " + formatDelta(d, done), c));
            }

            if (!done && !active && !locked) {
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                row.setToolTipText("Click to research " + t.name());
                row.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        engine.enqueue(new QueueResearchCommand(t.id()));
                        onQueued.run();
                    }
                });
            }
            list.add(row);
        }
        return list;
    }

    private static String titleLine(TechState tech, Tech t, boolean done, boolean active, List<String> missing) {
        String status;
        if (done) status = "✓ researched";
        else if (active) status = String.format("active, %d / %d pts", (int) tech.accumulatedPoints, t.researchCost());
        else if (!missing.isEmpty()) status = "needs " + String.join(", ", missing);
        else status = "cost " + t.researchCost();
        return t.name() + "  —  " + status;
    }

    private static List<String> missingPrereqs(TechState tech, Tech t) {
        List<String> out = new ArrayList<>();
        for (String id : t.prereqIds()) {
            if (tech.researched.contains(id)) continue;
            Tech p = TechCatalog.get(id);
            out.add(p == null ? id : p.name());
        }
        return out;
    }

    /** Lower is better for costs and hazards; higher is better for everything else. */
    private static boolean improves(TechEffects.Delta d) {
        boolean lowerIsBetter = d.label().equals("Ship fuel cost")
            || d.label().equals("Farm water use")
            || d.label().equals("Disease severity");
        return lowerIsBetter ? d.after() < d.before() : d.after() > d.before();
    }

    /**
     * e.g. "Ship fuel cost ×0.80 → ×0.56 (−30%)", or for a researched tech
     * "Ship fuel cost ×0.80 → ×0.56 (−30%, applied)". Flags read "off → on".
     */
    static String formatDelta(TechEffects.Delta d, boolean applied) {
        String suffix = applied ? ", applied" : "";
        if (d.flag()) {
            return String.format("%s: %s → %s%s", d.label(),
                d.before() > 0 ? "on" : "off", d.after() > 0 ? "on" : "off", applied ? " (applied)" : "");
        }
        // The morale ceiling is an absolute value (base 1.0), not a multiplier on something.
        String prefix = d.label().equals("Morale ceiling") ? "" : "×";
        double pct = (d.after() / d.before() - 1.0) * 100.0;
        return String.format("%s %s%.2f → %s%.2f (%s%.0f%%%s)", d.label(),
            prefix, d.before(), prefix, d.after(), pct >= 0 ? "+" : "−", Math.abs(pct), suffix);
    }

    private static JLabel label(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
}
