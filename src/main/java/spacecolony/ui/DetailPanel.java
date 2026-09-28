package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.PopCapBreakdown;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.TechState;

public class DetailPanel extends JPanel {
    private final Engine engine;
    private final JPanel content = new JPanel();
    private final SphereMiniRenderer miniRenderer;

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
        addLabel("Stockpile:", UiColors.FOREGROUND_DIM);
        JPanel stockGrid = new JPanel(new GridLayout(0, 2, 6, 2));
        stockGrid.setOpaque(false);
        stockGrid.setAlignmentX(LEFT_ALIGNMENT);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            stockGrid.add(rowLabel(r.name(), UiColors.FOREGROUND_DIM));
            stockGrid.add(rowLabel(String.format("%.0f", s.stockpile.getOrDefault(r, 0.0)), UiColors.FOREGROUND));
        }
        stockGrid.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, stockGrid.getPreferredSize().height));
        content.add(stockGrid);
        content.add(Box.createVerticalStrut(6));
        addLabel("Net / day:", UiColors.FOREGROUND_DIM);
        JPanel rateGrid = new JPanel(new GridLayout(0, 2, 6, 2));
        rateGrid.setOpaque(false);
        rateGrid.setAlignmentX(LEFT_ALIGNMENT);
        for (Resource r : Resource.values()) {
            double rate = s.productionRateCache.getOrDefault(r, 0.0);
            if (Math.abs(rate) <= 1e-6) continue;
            rateGrid.add(rowLabel(r.name(), UiColors.FOREGROUND_DIM));
            rateGrid.add(rowLabel(String.format("%+.1f", rate), rate < 0 ? UiColors.ERROR : UiColors.FOREGROUND));
        }
        if (rateGrid.getComponentCount() == 0) addLabel("  (idle)", UiColors.FOREGROUND_DIM);
        else {
            rateGrid.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, rateGrid.getPreferredSize().height));
            content.add(rateGrid);
        }
        content.add(Box.createVerticalStrut(6));
        addLabel("Buildings:", UiColors.FOREGROUND_DIM);
        for (Building b : s.buildings) {
            String enabled = b.enabled ? "" : "  (disabled)";
            addLabel("  " + b.type + " L" + b.level + techNote(b.type, tech) + enabled,
                b.enabled ? UiColors.FOREGROUND : UiColors.WARNING);
        }
        content.add(Box.createVerticalStrut(8));
        JButton build = new JButton("Build building...");
        build.setName(TARGET_BUILD_BUILDING);
        build.addActionListener(e -> spacecolony.ui.dialogs.BuildBuildingDialog.show(this, engine, s.id));
        content.add(build);
        boolean hasShipyard = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.enabled);
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

    private JLabel rowLabel(String text, java.awt.Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        return l;
    }
}
