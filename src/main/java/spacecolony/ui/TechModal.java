package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
import spacecolony.sim.Tech;
import spacecolony.sim.TechAvailability;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechState;
import spacecolony.sim.World;
import spacecolony.sim.phases.ResearchPhase;
import spacecolony.sim.commands.QueueResearchCommand;

public class TechModal {
    private static final Color DELTA_GOOD = new Color(120, 200, 130);

    public static void show(Component owner, Engine engine) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner), "Tech Tree", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane();
        dlg.add(scroll, BorderLayout.CENTER);
        // The header and list are rebuilt on every world change so the progress bar moves
        // while the game keeps ticking underneath the modal.
        Component[] north = new Component[1];
        Runnable rebuild = () -> {
            if (north[0] != null) dlg.remove(north[0]);
            north[0] = header(engine.world());
            dlg.add(north[0], BorderLayout.NORTH);
            java.awt.Point pos = scroll.getViewport().getViewPosition();
            scroll.setViewportView(buildList(engine, dlg::dispose));
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
        dlg.setSize(560, 600);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    static JPanel header(World w) {
        TechState tech = w.tech;
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
        JLabel top = new JLabel(active);
        top.setForeground(UiColors.FOREGROUND);
        JLabel bottom = new JLabel(tech.researched.size() + "/" + TechCatalog.all().size() + " researched"
            + rateText(ResearchPhase.pointsPerTick(w))
            + String.format("  ·  Morale ceiling %.2f", TechEffects.moraleCeiling(tech)));
        bottom.setForeground(UiColors.FOREGROUND_DIM);
        p.add(top, BorderLayout.NORTH);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    /** "  ·  2.5 pts/day", or "  ·  no research labs" when nothing produces points. */
    static String rateText(double rate) {
        return rate > 1e-9 ? String.format("  ·  %.1f pts/day", rate) : "  ·  no research labs";
    }

    /** "250 / 800  ·  ETA 220 days", or "… ·  no labs" when the rate is 0. */
    static String progressText(double points, long cost, double rate) {
        String eta = rate > 1e-9
            ? "ETA " + (long) Math.ceil(Math.max(0.0, cost - points) / rate) + " days"
            : "no labs";
        return String.format("%d / %d  ·  %s", (int) points, cost, eta);
    }

    /** One block per tech: title line, description, then what it changes. */
    static JPanel buildList(Engine engine, Runnable onQueued) {
        TechState tech = engine.world().tech;
        double rate = ResearchPhase.pointsPerTick(engine.world());
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        // Group by tier (longest prereq chain), keeping catalog order within a tier.
        Map<Integer, List<Tech>> tiers = new TreeMap<>();
        for (Tech t : TechCatalog.all()) {
            tiers.computeIfAbsent(TechAvailability.tier(t), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<Integer, List<Tech>> tier : tiers.entrySet()) {
            JLabel tierHeader = label("Tier " + tier.getKey(), UiColors.INFO);
            tierHeader.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
            list.add(tierHeader);
            for (Tech t : tier.getValue()) list.add(techRow(engine, tech, t, rate, onQueued));
        }
        return list;
    }

    private static JPanel techRow(Engine engine, TechState tech, Tech t, double rate, Runnable onQueued) {
        boolean done = tech.researched.contains(t.id());
        boolean active = t.id().equals(tech.activeId);
        List<String> missing = TechAvailability.missingPrereqs(tech, t).stream()
            .map(id -> TechCatalog.get(id).name()).toList();
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
        if (active) {
            JProgressBar bar = new JProgressBar(0, (int) t.researchCost());
            bar.setValue((int) tech.accumulatedPoints);
            bar.setStringPainted(true);
            bar.setString(progressText(tech.accumulatedPoints, t.researchCost(), rate));
            bar.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.add(bar);
        }
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
        return row;
    }

    private static String titleLine(TechState tech, Tech t, boolean done, boolean active, List<String> missing) {
        String status;
        if (done) status = "✓ researched";
        else if (active) status = String.format("active, %d / %d pts", (int) tech.accumulatedPoints, t.researchCost());
        else if (!missing.isEmpty()) status = "needs " + String.join(", ", missing);
        else status = "cost " + t.researchCost();
        return t.name() + "  —  " + status;
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
