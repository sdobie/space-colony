package spacecolony.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JPanel;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.economy.BuildingOutcome;
import spacecolony.sim.economy.DayReport;
import spacecolony.sim.economy.FlowLine;
import spacecolony.sim.economy.FlowSource;
import spacecolony.sim.economy.Outlook;

/**
 * A colony's resources: stock, net per day and outlook, one row each. Clicking a row lists
 * what made and used that resource yesterday. The Power row does the same for energy.
 */
public class ResourceLedgerPanel extends JPanel {
    public static final String ROW_PREFIX = "ledger.row.";
    public static final String POWER_ROW = "ledger.row.POWER";

    private final Set<Resource> expanded = EnumSet.noneOf(Resource.class);
    private boolean powerExpanded;
    private Site site;
    private DayReport report;
    private int row;

    public ResourceLedgerPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
    }

    /** Forget which rows are open (the selection moved to another colony). */
    public void collapseAll() {
        expanded.clear();
        powerExpanded = false;
    }

    public boolean isExpanded(Resource r) { return expanded.contains(r); }

    public void toggle(Resource r) {
        if (!expanded.remove(r)) expanded.add(r);
        rebuild();
    }

    public void togglePower() {
        powerExpanded = !powerExpanded;
        rebuild();
    }

    public void update(Site s, DayReport d) {
        this.site = s;
        this.report = d;
        rebuild();
    }

    private void rebuild() {
        removeAll();
        row = 0;
        if (site == null || report == null) return;
        cell(label("Resources" + (report.estimate ? " (estimate)" : ""), UiColors.FOREGROUND_DIM), 0, 1, null);
        cell(label("stock", UiColors.FOREGROUND_DIM), 1, 1, null);
        cell(label("/ day", UiColors.FOREGROUND_DIM), 2, 1, null);
        cell(label("outlook", UiColors.FOREGROUND_DIM), 3, 1, null);
        row++;
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            double stock = site.stockpile.getOrDefault(r, 0.0);
            if (stock <= 1e-6 && !report.touches(r)) continue;
            resourceRow(r, stock);
        }
        powerRow();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        revalidate();
        repaint();
    }

    private void resourceRow(Resource r, double stock) {
        double cap = site.stockpileCap.getOrDefault(r, 1000.0);
        double net = report.net(r);
        Outlook o = Outlook.of(stock, cap, net);
        boolean open = expanded.contains(r);
        Runnable click = () -> toggle(r);
        JLabel name = label((open ? "▾ " : "▸ ") + r, UiColors.FOREGROUND);
        name.setName(ROW_PREFIX + r.name());
        clickable(cell(name, 0, 1, null), click);
        JLabel stockLabel = label(String.format("%.0f", stock), UiColors.FOREGROUND);
        stockLabel.setToolTipText(String.format("%.0f of %.0f storage", stock, cap));
        clickable(cell(stockLabel, 1, 1, null), click);
        clickable(cell(label(Math.abs(net) < 0.05 ? "0.0" : String.format("%+.1f", net),
            net < -0.05 ? UiColors.ERROR : UiColors.FOREGROUND), 2, 1, null), click);
        clickable(cell(label(outlookText(o), outlookColor(r, o, stock)), 3, 1, null), click);
        row++;
        if (!open) return;
        var lines = report.linesFor(r);
        if (lines.isEmpty()) childLine("nothing made or used", UiColors.FOREGROUND_DIM);
        for (FlowLine l : lines) childLine(lineText(l, report), l.shortfall() ? UiColors.WARNING : UiColors.FOREGROUND_DIM);
    }

    private void powerRow() {
        if (report.powerMade <= 1e-9 && report.powerUsed <= 1e-9) return;
        boolean brownout = report.powerFactor < 1.0 - 1e-9;
        Runnable click = this::togglePower;
        JLabel name = label((powerExpanded ? "▾ " : "▸ ") + "Power", UiColors.FOREGROUND);
        name.setName(POWER_ROW);
        clickable(cell(name, 0, 1, null), click);
        JLabel balance = label(String.format("%.1f / %.1f", report.powerMade, report.powerUsed),
            brownout ? UiColors.ERROR : UiColors.FOREGROUND);
        balance.setToolTipText("made / used");
        clickable(cell(balance, 1, 2, null), click);
        JLabel state = label(brownout ? String.format("brownout %.0f%%", report.powerFactor * 100) : "ok",
            brownout ? UiColors.ERROR : UiColors.FOREGROUND_DIM);
        state.setToolTipText(brownout
            ? String.format("Brownout: %.1f made, %.1f used. Farms, mines and refineries run at %.0f%%.",
                report.powerMade, report.powerUsed, report.powerFactor * 100)
            : "Enough power for every building");
        clickable(cell(state, 3, 1, null), click);
        row++;
        if (!powerExpanded) return;
        for (FlowLine l : report.linesFor(Resource.ENERGY)) childLine(lineText(l, report), UiColors.FOREGROUND_DIM);
    }

    private void childLine(String text, Color fg) {
        cell(label(text, fg), 0, 4, new Insets(0, 16, 0, 0));
        row++;
    }

    /** "+1.5  Farm L1", "−0.0  Farm L1 (wants 0.5; no BIOMASS)", "+25.0  Mule unloading". */
    static String lineText(FlowLine l, DayReport d) {
        String text = String.format("%+.1f  %s", l.amount(), sourceName(l.source()));
        if (l.shortfall()) text += String.format(" (wants %.1f%s)", Math.abs(l.wanted()), reason(l, d));
        return text;
    }

    static String sourceName(FlowSource src) {
        return switch (src) {
            case FlowSource.Building b -> BuildingCatalog.displayName(b.type()) + " L" + b.level();
            case FlowSource.Population p -> "Population (" + p.people() + ")";
            case FlowSource.Shipping s -> s.shipName() + " " + switch (s.kind()) {
                case LOADING -> "loading";
                case UNLOADING -> "unloading";
                case FUEL -> "fuel";
                case RETURNED -> "returned cargo";
            };
            case FlowSource.StorageFull f -> "lost: storage full";
        };
    }

    private static String reason(FlowLine l, DayReport d) {
        if (l.source() instanceof FlowSource.Population) return "; ran out";
        if (!(l.source() instanceof FlowSource.Building b)) return "";
        BuildingOutcome o = d.outcome(b.index());
        if (o == null || o.limit() == null) return "; ran out";
        return "; " + limitText(o);
    }

    /** "no BIOMASS", "short of ORE", "brownout", "poor ORE here", "disabled". */
    static String limitText(BuildingOutcome o) {
        return switch (o.limit()) {
            case DISABLED -> "disabled";
            case NO_INPUT -> "no " + o.limitResource();
            case SHORT_INPUT -> "short of " + o.limitResource();
            case BROWNOUT -> "brownout";
            case NEEDS_TECH -> "needs Atmospheric Mining";
            case NO_YIELD -> "no " + o.limitResource() + " here";
            case LOW_YIELD -> "poor " + o.limitResource() + " here";
        };
    }

    static String outlookText(Outlook o) {
        return switch (o) {
            case Outlook.Empty e -> "empty";
            case Outlook.Full f -> "full";
            case Outlook.Steady s -> "steady";
            case Outlook.EmptyIn e -> "empty " + days(e.days());
            case Outlook.FullIn f -> "full " + days(f.days());
        };
    }

    private static String days(int d) {
        return (d >= Outlook.MAX_DAYS ? Outlook.MAX_DAYS + "+" : String.valueOf(d)) + " d";
    }

    private Color outlookColor(Resource r, Outlook o, double stock) {
        boolean vital = r == Resource.FOOD || r == Resource.WATER;
        boolean wanted = report.linesFor(r).stream().anyMatch(l -> l.wanted() < 0);
        return switch (o) {
            case Outlook.Empty e -> wanted ? UiColors.ERROR : UiColors.FOREGROUND_DIM;
            case Outlook.EmptyIn e when vital && e.days() <= 10 -> UiColors.ERROR;
            case Outlook.EmptyIn e when e.days() <= 30 -> UiColors.WARNING;
            case Outlook.Full f when report.linesFor(r).stream()
                .anyMatch(l -> l.source() instanceof FlowSource.StorageFull) -> UiColors.WARNING;
            default -> UiColors.FOREGROUND_DIM;
        };
    }

    private JLabel cell(JLabel l, int col, int span, Insets insets) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = col;
        c.gridy = row;
        c.gridwidth = span;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = col == 0 ? 1.0 : 0.0;
        c.insets = insets != null ? insets : new Insets(1, 0, 1, col == 3 ? 0 : 8);
        add(l, c);
        return l;
    }

    private static void clickable(Component c, Runnable onClick) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { onClick.run(); }
        });
    }

    private static JLabel label(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        return l;
    }

    /** All visible text, for tests. */
    String text() {
        StringBuilder sb = new StringBuilder();
        for (Component c : getComponents()) if (c instanceof JLabel l) sb.append(l.getText()).append('\n');
        return sb.toString();
    }

    /** The label for a row's name cell, for tests. */
    JLabel rowLabel(String name) {
        for (Component c : getComponents()) if (name.equals(c.getName())) return (JLabel) c;
        return null;
    }
}
