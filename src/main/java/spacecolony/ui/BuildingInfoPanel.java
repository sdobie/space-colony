package spacecolony.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingSpec;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.TechState;
import spacecolony.sim.economy.BuildForecast;

/** The build dialog's right-hand card: what a building does, and what it would do here. */
public class BuildingInfoPanel extends JPanel implements Scrollable {
    public static final String NAME = "build.info";
    private static final int TEXT_WIDTH = 370;

    public BuildingInfoPanel() {
        setName(NAME);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
    }

    public void show(BuildForecast f, Site site, TechState tech) {
        removeAll();
        BuildingSpec spec = BuildingCatalog.get(f.type());
        JLabel title = label(spec.displayName(), UiColors.FOREGROUND);
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));
        add(title);
        add(wrapped(spec.summary(), UiColors.FOREGROUND));
        add(Box.createVerticalStrut(8));

        add(label("Per level, per day", UiColors.FOREGROUND_DIM));
        for (String line : perLevel(spec, tech)) add(label("  " + line, UiColors.FOREGROUND));
        add(Box.createVerticalStrut(8));

        add(label("At " + site.name + ", per day", UiColors.FOREGROUND_DIM));
        add(forecastTable(f));
        for (String fact : f.facts()) add(wrapped(fact, UiColors.FOREGROUND_DIM));
        if (!f.warnings().isEmpty()) add(Box.createVerticalStrut(6));
        for (String w : f.warnings()) add(wrapped("⚠ " + w, UiColors.WARNING));
        add(Box.createVerticalGlue());
        revalidate();
        repaint();
    }

    /** "uses 0.5 BIOMASS, 0.3 WATER, 2 energy" / "makes 1.5 FOOD" lines, with tech multipliers named. */
    static List<String> perLevel(BuildingSpec spec, TechState tech) {
        List<String> uses = new ArrayList<>();
        List<String> makes = new ArrayList<>();
        boolean yields = false;
        for (BuildingSpec.Rate r : spec.rates()) {
            String amount = fmt(r.perLevel()) + " " + r.resource();
            switch (r.kind()) {
                case INPUT -> uses.add(amount);
                case OUTPUT -> makes.add(amount);
                case YIELD_OUTPUT -> { makes.add("up to " + amount); yields = true; }
            }
        }
        if (spec.powerDrawPerLevel() > 0) uses.add(fmt(spec.powerDrawPerLevel()) + " energy");
        List<String> out = new ArrayList<>();
        if (!uses.isEmpty()) out.add("uses " + String.join(", ", uses));
        switch (spec.type()) {
            case POWER_PLANT -> makes.add(fmt(BuildingCatalog.POWER_PLANT_OUTPUT) + " energy at 1 AU, ÷ distance²");
            case HABITAT -> makes.add("+" + BuildingCatalog.HABITAT_CAP + " population cap");
            case RESEARCH_LAB -> makes.add(fmt(BuildingCatalog.LAB_POINTS) + " research point");
            case SHIPYARD -> makes.add("ships, when you order them");
            default -> {}
        }
        if (!makes.isEmpty()) out.add("makes " + String.join(", ", makes) + (yields ? " (× ground yield)" : ""));
        if (spec.type() == BuildingType.MINE) out.add("at a gas giant, with Atmospheric Mining: "
            + fmt(BuildingCatalog.MINE_GAS_FUEL) + " FUEL × yield");
        String tn = DetailPanel.techNote(spec.type(), tech).trim();
        if (!tn.isEmpty()) out.add("techs: " + tn);
        return out;
    }

    private JPanel forecastTable(BuildForecast f) {
        JPanel grid = new JPanel(new GridLayout(0, 4, 10, 1));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.add(label("", UiColors.FOREGROUND_DIM));
        grid.add(label("now", UiColors.FOREGROUND_DIM));
        grid.add(label("with it", UiColors.FOREGROUND_DIM));
        grid.add(label("change", UiColors.FOREGROUND_DIM));
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            double before = f.before().net(r), after = f.after().net(r);
            if (Math.abs(before) < 0.005 && Math.abs(after) < 0.005) continue;
            double d = after - before;
            grid.add(label("  " + r, UiColors.FOREGROUND_DIM));
            grid.add(label(String.format("%+.1f", before), UiColors.FOREGROUND));
            grid.add(label(String.format("%+.1f", after), UiColors.FOREGROUND));
            grid.add(label(Math.abs(d) < 0.05 ? "—" : String.format("%+.1f", d), changeColor(d)));
        }
        grid.add(label("  Power", UiColors.FOREGROUND_DIM));
        grid.add(label(power(f.before().powerMade, f.before().powerUsed), UiColors.FOREGROUND));
        grid.add(label(power(f.after().powerMade, f.after().powerUsed),
            f.after().powerFactor < 1.0 - 1e-9 ? UiColors.ERROR : UiColors.FOREGROUND));
        grid.add(label("made / used", UiColors.FOREGROUND_DIM));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
        return grid;
    }

    private static String power(double made, double used) {
        return String.format("%.1f / %.1f", made, used);
    }

    private static Color changeColor(double d) {
        if (Math.abs(d) < 0.05) return UiColors.FOREGROUND_DIM;
        return d > 0 ? UiColors.FOREGROUND : UiColors.ERROR;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.format("%.0f", v) : String.format("%.2f", v).replaceAll("0$", "");
    }

    private static JLabel label(String text, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JLabel wrapped(String text, Color fg) {
        String html = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return label("<html><body style='width:" + TEXT_WIDTH + "px'>" + html + "</body></html>", fg);
    }

    /** All the card's text, for tests. */
    public String text() {
        StringBuilder sb = new StringBuilder();
        collect(this, sb);
        return sb.toString();
    }

    private static void collect(Component c, StringBuilder sb) {
        if (c instanceof JLabel l && l.getText() != null) sb.append(l.getText().replaceAll("<[^>]*>", "")).append('\n');
        if (c instanceof java.awt.Container k) for (Component child : k.getComponents()) collect(child, sb);
    }

    // Scrollable: fit the dialog's width and scroll only vertically.
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
