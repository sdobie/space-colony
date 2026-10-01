package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.BuildCost;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Construction;
import spacecolony.sim.PopCapBreakdown;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechState;
import spacecolony.sim.economy.BuildForecast;
import spacecolony.sim.economy.BuildingOutcome;
import spacecolony.sim.economy.DayReport;
import spacecolony.sim.economy.FlowLine;
import spacecolony.sim.economy.Limit;

public class DetailPanel extends JPanel {
    private final Engine engine;
    private final JPanel content = new WidthTrackingPanel();
    private final SphereMiniRenderer miniRenderer;
    /** Kept across refreshes so open rows stay open as the days tick. */
    private final ResourceLedgerPanel ledger = new ResourceLedgerPanel();
    private String ledgerSiteId;

    public DetailPanel(Engine engine) {
        this.engine = engine;
        setName(TARGET);
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UiColors.PANEL_BORDER));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(content,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        add(scroll, BorderLayout.CENTER);
        this.miniRenderer = new SphereMiniRenderer(engine);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.SelectionChanged
             || e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced) refresh();
        });
        refresh();
    }

    private void refresh() {
        content.removeAll();
        Selection sel = engine.selection();
        switch (sel.kind()) {
            case BODY -> renderBody(engine.world().findBody(sel.id()));
            case SITE -> renderSite(engine.world().findSite(sel.id()));
            case SHIP -> renderShip(engine.world().findShip(sel.id()));
            case NONE -> renderNone();
        }
        content.revalidate();
        content.repaint();
    }

    private void renderNone() {
        addLabel("(No selection)", UiColors.FOREGROUND_DIM);
    }

    private void renderBody(Body b) {
        if (b == null) { renderNone(); return; }
        addLabel(b.name, UiColors.FOREGROUND);
        addLabel(b.type.name() + "  ·  " + b.sites.size() + " site(s)", UiColors.FOREGROUND_DIM);
        miniRenderer.setBody(b);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrap.setOpaque(false);
        wrap.add(miniRenderer);
        content.add(wrap);
        JButton open = new JButton("Open body view");
        open.addActionListener(e -> {
            engine.setSelection(Selection.body(b.id));
            engine.setView(EngineEvent.ViewChanged.View.BODY_VIEW);
        });
        content.add(open);
    }

    private void renderSite(Site s) {
        if (s == null) { renderNone(); return; }
        addLabel(s.name, UiColors.FOREGROUND);
        TechState tech = engine.world().tech;
        // Use the breakdown's cap rather than s.populationCap so the line always adds up;
        // the two are equal after any tick.
        PopCapBreakdown cap = PopCapBreakdown.of(s, tech);
        addLabel("Body: " + s.bodyId + "  ·  pop " + s.population + "/" + cap.cap(), UiColors.FOREGROUND_DIM);
        addLabel(capBreakdown(cap), UiColors.FOREGROUND_DIM)
            .setToolTipText("Pop cap = (site base + 100 per enabled habitat level) × colony management techs");
        addLabel(moraleLine(s.morale, TechEffects.moraleCeiling(tech)), moraleColor(s.morale));
        content.add(Box.createVerticalStrut(6));
        if (!s.id.equals(ledgerSiteId)) {
            ledger.collapseAll();
            ledgerSiteId = s.id;
        }
        DayReport day = s.lastDay != null ? s.lastDay : BuildForecast.estimate(engine.world(), s);
        ledger.update(s, day);
        content.add(ledger);
        content.add(Box.createVerticalStrut(6));
        addLabel("Buildings  " + Construction.slotsUsed(s) + " of " + Construction.SLOTS + " slots",
            UiColors.FOREGROUND_DIM);
        for (int i = 0; i < s.buildings.size(); i++) {
            Building b = s.buildings.get(i);
            BuildingOutcome o = day.outcome(i);
            content.add(buildingRow(s, b, buildingText(b, o, day, tech), buildingColor(b, o), techNote(b.type, tech).trim()));
        }
        content.add(Box.createVerticalStrut(8));
        JButton build = new JButton("Build building...");
        build.setName(TARGET_BUILD_BUILDING);
        build.addActionListener(e -> spacecolony.ui.dialogs.BuildBuildingDialog.show(this, engine, s.id));
        content.add(build);
        boolean hasShipyard = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.isOperational());
        if (hasShipyard) {
            JButton ship = new JButton("Build ship...");
            ship.setName(TARGET_BUILD_SHIP);
            ship.addActionListener(e -> spacecolony.ui.dialogs.BuildShipDialog.show(this, engine, s.id));
            content.add(ship);
        }
    }

    static final String FOUND_COLONY_LABEL = "Found colony…";
    // Component names the tutorial highlights (Plan 6 §5.4).
    public static final String TARGET = "detail";
    public static final String TARGET_BUILD_BUILDING = "detail.buildBuilding";
    public static final String TARGET_BUILD_SHIP = "detail.buildShip";
    public static final String TARGET_DISPATCH = "detail.dispatch";
    public static final String TARGET_FOUND_COLONY = "detail.foundColony";
    /** Each building row's ⋯ button is named this plus the building id. */
    public static final String TARGET_BUILDING_PREFIX = "detail.building.";

    private void renderShip(Ship s) {
        if (s == null) { renderNone(); return; }
        addLabel(s.name + "  (" + s.shipClass + ")", UiColors.FOREGROUND);
        Body orbiting = s.orbitingBodyId == null ? null : engine.world().findBody(s.orbitingBodyId);
        addLabel(orbiting != null ? "Orbiting " + orbiting.name : "State: " + s.state, UiColors.FOREGROUND_DIM);
        if (s.currentSiteId != null) addLabel("At: " + s.currentSiteId, UiColors.FOREGROUND_DIM);
        if (s.transit != null && s.state == ShipState.IN_TRANSIT) {
            String dest = s.transit.destSiteId();
            if (dest == null) {
                Body b = engine.world().findBody(s.transit.destBodyId());
                dest = (b != null ? b.name : s.transit.destBodyId()) + " (unsettled)";
            }
            addLabel("→ " + dest + " (arrival t=" + s.transit.arrivalTick() + ")", UiColors.FOREGROUND_DIM);
        }
        // Ships fly on their origin site's FUEL, drawn at departure, so an empty tank is normal.
        String fuel = String.format("Fuel: %.1f", s.fuel);
        if (s.fuel < 1e-6 && s.currentSiteId != null) fuel += " (fills from site FUEL on departure)";
        addLabel(fuel, UiColors.FOREGROUND_DIM);
        if (s.cargoMass() > 0) {
            addLabel("Cargo:", UiColors.FOREGROUND_DIM);
            for (Resource r : Resource.values()) {
                double v = s.cargo.getOrDefault(r, 0.0);
                if (v > 1e-6) addLabel("  " + r + ": " + String.format("%.0f", v), UiColors.FOREGROUND);
            }
        }
        if (orbiting != null) {
            // An orbiting colonizer can only found a colony here (or be retired).
            JButton found = new JButton(FOUND_COLONY_LABEL);
            found.setName(TARGET_FOUND_COLONY);
            found.addActionListener(e -> {
                engine.setSelection(Selection.body(orbiting.id));
                engine.setView(EngineEvent.ViewChanged.View.BODY_VIEW);
            });
            content.add(found);
        } else if (s.state == ShipState.IDLE) {
            JButton dispatch = new JButton("Dispatch...");
            dispatch.setName(TARGET_DISPATCH);
            dispatch.addActionListener(e -> spacecolony.ui.dialogs.DispatchShipDialog.show(this, engine, s.id));
            content.add(dispatch);
        }
    }

    /** One building row: its label, and a ⋯ button that opens {@link BuildingMenu}. */
    private JPanel buildingRow(Site s, Building b, String text, java.awt.Color fg, String techs) {
        JPanel row = new JPanel(new BorderLayout()) {
            // The text wraps, so the row's height follows its width; never stretch taller than that.
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(wrappingText(text, fg, techs.isEmpty() ? null : "Techs: " + techs), BorderLayout.CENTER);
        JButton more = new JButton("⋯");
        more.setName(TARGET_BUILDING_PREFIX + b.id);
        more.setToolTipText("Upgrade, repair or demolish");
        more.setMargin(new java.awt.Insets(0, 4, 0, 4));
        more.setFocusable(false);
        more.addActionListener(e -> BuildingMenu.create(engine, s, b).show(more, 0, more.getHeight()));
        JPanel moreWrap = new JPanel(new BorderLayout());
        moreWrap.setOpaque(false);
        moreWrap.add(more, BorderLayout.NORTH); // stays on the first line when the text wraps
        row.add(moreWrap, BorderLayout.EAST);
        return row;
    }

    /** Label-styled text that wraps at word boundaries to the column's width instead of widening it. */
    static JTextArea wrappingText(String text, java.awt.Color fg, String tooltip) {
        // The indent is a border, not the text's leading spaces, so wrapped lines line up with the first.
        JTextArea area = new JTextArea(text.stripLeading());
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(0, 8 * (text.length() - text.stripLeading().length()) / 2, 0, 0));
        area.setFont(javax.swing.UIManager.getFont("Label.font"));
        area.setForeground(fg);
        area.setToolTipText(tooltip);
        return area;
    }

    /**
     * The scroll pane's view: always exactly as wide as the column, so a long line wraps (building
     * rows) or ends in "…" (labels) rather than pushing the column's right side out of the window.
     */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? r.height : r.width;
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }

    /**
     * A building row's text (Plan 8 §5.1): "  Farm L1   +1.5 FOOD", "  Mine L1 → L2   +0.9 ORE  (upgrading, 3 d)",
     * "  Refinery   building, 4 days left", or a damaged building's repair cost.
     */
    static String buildingText(Building b, BuildingOutcome o, DayReport day, TechState tech) {
        String name = BuildingCatalog.displayName(b.type);
        if (b.level == 0)
            return "  " + name + "   " + (b.enabled ? "building, " + days(b.daysLeft) + " left" : "building paused: damaged");
        String level = " L" + b.level + (b.isUnderConstruction() ? " → L" + (b.level + 1) : "");
        String upgrading = b.isUnderConstruction() && b.enabled ? "  (upgrading, " + b.daysLeft + " d)" : "";
        return "  " + name + level + "   " + buildingResult(b, o, day, tech) + upgrading;
    }

    private static String days(int n) { return n + (n == 1 ? " day" : " days"); }

    /**
     * What a building did yesterday: "+2.4 ORE · +0.9 SILICATE", "idle: no BIOMASS",
     * "60%: short of ORE", "+100 cap", "(disabled)".
     */
    static String buildingResult(Building b, BuildingOutcome o, DayReport day, TechState tech) {
        if (!b.enabled || (o != null && o.limit() == Limit.DISABLED)) {
            // Power plants come back by themselves each day (ProductionPhase step 8).
            if (b.type == BuildingType.POWER_PLANT) return "offline today";
            return "damaged: repair " + BuildCost.describe(Construction.repairCost(b.type).resources());
        }
        if (o == null) return "";
        String made = switch (b.type) {
            case HABITAT -> "+" + BuildingCatalog.HABITAT_CAP * b.level + " cap";
            case SHIPYARD -> "builds ships";
            case RESEARCH_LAB -> String.format("+%.1f research",
                b.level * BuildingCatalog.LAB_POINTS * TechEffects.researchLabMultiplier(tech));
            default -> {
                java.util.List<String> parts = new java.util.ArrayList<>();
                for (FlowLine l : day.linesOf(o.index()))
                    if (l.amount() > 1e-9) parts.add(String.format("+%.1f %s", l.amount(),
                        l.resource() == Resource.ENERGY ? "energy" : l.resource().name()));
                yield String.join(" · ", parts);
            }
        };
        if (o.limit() == null) return made;
        String why = ResourceLedgerPanel.limitText(o);
        if (o.efficiency() <= 1e-9) return "idle: " + why;
        if (o.efficiency() < 0.995) return String.format("%.0f%%: %s", o.efficiency() * 100, why);
        return made.isEmpty() ? why : made + " (" + why + ")";
    }

    private static java.awt.Color buildingColor(Building b, BuildingOutcome o) {
        if (!b.enabled) return UiColors.WARNING;
        if (b.level == 0) return UiColors.FOREGROUND_DIM;
        if (o == null || o.limit() == null) return UiColors.FOREGROUND;
        return switch (o.limit()) {
            case LOW_YIELD, NEEDS_TECH -> UiColors.FOREGROUND;
            default -> UiColors.WARNING;
        };
    }

    /** "Morale: 0.95 / 1.00", with a note once life-support techs lift the ceiling. */
    static String moraleLine(double morale, double ceiling) {
        String line = String.format("Morale: %.2f / %.2f", morale, ceiling);
        return ceiling > 1.0 + 1e-9 ? line + String.format("  (life support +%.0f%%)", (ceiling - 1.0) * 100.0) : line;
    }

    /** "  base 200 + habitats 100 × 1.56" (the colony-mgmt multiplier); omitted at 1.0. */
    static String capBreakdown(PopCapBreakdown c) {
        return "  base " + c.siteBase() + " + habitats " + c.habitatBoost()
            + (Math.abs(c.techMultiplier() - 1.0) > 1e-9 ? String.format(" × %.2f", c.techMultiplier()) : "");
    }

    /** Tech multipliers that currently apply to a building type, e.g. "  food ×1.30 · water ×0.70"; "" when none. */
    static String techNote(BuildingType type, TechState t) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        switch (type) {
            case MINE -> {
                addMultiplier(parts, "ore", TechEffects.mineOreMultiplier(t));
                addMultiplier(parts, "silicate", TechEffects.mineSilicateMultiplier(t));
            }
            case FARM -> {
                addMultiplier(parts, "food", TechEffects.farmFoodMultiplier(t));
                addMultiplier(parts, "water", TechEffects.farmWaterDemandMultiplier(t));
            }
            case POWER_PLANT  -> addMultiplier(parts, null, TechEffects.powerPlantMultiplier(t));
            case REFINERY     -> addMultiplier(parts, null, TechEffects.refineryMultiplier(t));
            case RESEARCH_LAB -> addMultiplier(parts, null, TechEffects.researchLabMultiplier(t));
            default -> {}
        }
        return parts.isEmpty() ? "" : "  " + String.join(" · ", parts);
    }

    private static void addMultiplier(java.util.List<String> parts, String what, double m) {
        if (Math.abs(m - 1.0) <= 1e-9) return;
        parts.add((what == null ? "" : what + " ") + String.format("×%.2f", m));
    }

    // Growth needs morale > 0.7 and decline starts below 0.3 (ProductionPhase).
    private static java.awt.Color moraleColor(double morale) {
        if (morale < 0.3) return UiColors.ERROR;
        if (morale <= 0.7) return UiColors.WARNING;
        return UiColors.FOREGROUND_DIM;
    }

    private JLabel addLabel(String text, java.awt.Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setAlignmentX(LEFT_ALIGNMENT);
        content.add(l);
        return l;
    }
}
