package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.util.List;
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
import spacecolony.sim.Fuels;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceSurvey;
import spacecolony.sim.ShipClass;
import spacecolony.sim.World;
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
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT)) {
            // Never taller than the globe, so the Resources rows sit right under it.
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        wrap.setOpaque(false);
        wrap.setAlignmentX(LEFT_ALIGNMENT);
        wrap.add(miniRenderer);
        content.add(wrap);
        JButton open = new JButton("Open body view");
        open.addActionListener(e -> {
            engine.setSelection(Selection.body(b.id));
            engine.setView(EngineEvent.ViewChanged.View.BODY_VIEW);
        });
        content.add(open);
        content.add(Box.createVerticalStrut(6));
        World w = engine.world();
        if (!w.isSurveyed(b.id)) {
            addLabel(UNSURVEYED_TEXT, UiColors.FOREGROUND_DIM);
            addLabel("Send an explorer to survey it.", UiColors.FOREGROUND_DIM);
            return;
        }
        addLabel("Resources (surveyed)", UiColors.FOREGROUND_DIM);
        List<ResourceSurvey.Entry> entries = ResourceSurvey.of(b);
        if (entries.isEmpty()) addLabel("  No useful resources.", UiColors.FOREGROUND_DIM);
        for (ResourceSurvey.Entry e : entries) {
            java.awt.Color fg = e.rating() == ResourceSurvey.Rating.RICH ? UiColors.FOREGROUND : UiColors.FOREGROUND_DIM;
            // Short enough for the 330 px column on one line.
            JLabel row = addLabel(resourceRow(e), fg);
            row.setIcon(GameIcons.resource(e.resource(), GameIcons.SMALL));
            row.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 0));
            row.setToolTipText("Best " + e.resource() + " at "
                + degrees(e.bestLat(), e.bestLon()) + ". The body view's overlay shows where.");
        }
    }

    static final String UNSURVEYED_TEXT = "Resources unknown.";

    /** {@code ORE  Rich  avg .51  best .85}. */
    static String resourceRow(ResourceSurvey.Entry e) {
        return e.resource() + "  " + e.rating().label() + "  avg " + ResourceSurvey.fmt(e.average())
            + "  best " + ResourceSurvey.fmt(e.best());
    }

    /** {@code 23°N 104°W} for a latitude and longitude in radians. */
    static String degrees(double lat, double lon) {
        long la = Math.round(Math.toDegrees(lat));
        long lo = Math.round(Math.toDegrees(lon));
        return Math.abs(la) + "°" + (la < 0 ? "S" : "N") + " " + Math.abs(lo) + "°" + (lo < 0 ? "W" : "E");
    }

    /** {@code Ground: ORE .19 · SIL .45 · ICE .10 · BIO .21}: the yields at a colony's spot. */
    static String groundLine(Body b, Site s) {
        StringBuilder sb = new StringBuilder("Ground:");
        List<ResourceSurvey.Entry> here = ResourceSurvey.at(b, s.lat, s.lon);
        if (here.isEmpty()) return "Ground: nothing to mine or farm";
        for (int i = 0; i < here.size(); i++) {
            sb.append(i == 0 ? " " : " · ").append(ResourceSurvey.abbrev(here.get(i).resource()))
              .append(' ').append(ResourceSurvey.fmt(here.get(i).best()));
        }
        return sb.toString();
    }

    private void renderSite(Site s) {
        if (s == null) { renderNone(); return; }
        addLabel(s.name, UiColors.FOREGROUND);
        TechState tech = engine.world().tech;
        // Use the breakdown's cap rather than s.populationCap so the line always adds up;
        // the two are equal after any tick.
        PopCapBreakdown cap = PopCapBreakdown.of(s, tech);
        addLabel("Body: " + s.bodyId + "  ·  pop " + s.population + "/" + cap.cap(), UiColors.FOREGROUND_DIM);
        Body body = engine.world().findBody(s.bodyId);
        if (body != null) {
            addLabel(groundLine(body, s), UiColors.FOREGROUND_DIM)
                .setToolTipText("Yield at this colony's spot (0 to 1). Mines and farms scale with it.");
        }
        addLabel(capBreakdown(cap), UiColors.FOREGROUND_DIM)
            .setToolTipText("Pop cap = (site base + 100 per enabled habitat level) × colony management techs");
        addLabel(moraleLine(s.morale, TechEffects.moraleCeiling(tech)), moraleColor(s.morale));
        String dust = dustLine(s);
        if (dust != null) addLabel(dust, UiColors.WARNING)
            .setToolTipText("Dust from a meteor strike dims the sky: farms plant and drink as usual but grow less FOOD");
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
            World w = engine.world();
            content.add(buildingRow(s, b, rowText(w, s, b, buildingText(b, o, day, tech)), buildingColor(b, o),
                techNote(b.type, tech).trim(), progressFraction(w, s, b), progressColor(b)));
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
    /** A building row's progress bar is named this plus the building id. */
    public static final String TARGET_PROGRESS_PREFIX = "detail.progress.";
    /** Each building row's ⋯ button is named this plus the building id. */
    public static final String TARGET_BUILDING_PREFIX = "detail.building.";
    /** Each building row's tile icon is named this plus the building id. */
    public static final String TARGET_BUILDING_ICON_PREFIX = "detail.buildingIcon.";

    private void renderShip(Ship s) {
        if (s == null) { renderNone(); return; }
        addLabel(s.name + "  (" + s.shipClass + ")", UiColors.FOREGROUND);
        Body orbiting = s.orbitingBodyId == null ? null : engine.world().findBody(s.orbitingBodyId);
        if (s.state == ShipState.BUILDING) {
            renderShipUnderConstruction(s);
            return;
        }
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
        addLabel(tankText(s), UiColors.FOREGROUND_DIM);
        if (s.cargoMass() > 0) {
            addLabel("Cargo:", UiColors.FOREGROUND_DIM);
            for (Resource r : Resource.values()) {
                double v = s.cargo.getOrDefault(r, 0.0);
                if (v <= 1e-6) continue;
                JLabel line = addLabel(r + ": " + String.format("%.0f", v), UiColors.FOREGROUND);
                line.setIcon(GameIcons.resource(r, GameIcons.SMALL));
                line.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 0));
            }
        }
        if (orbiting != null && s.shipClass == ShipClass.EXPLORER) {
            // An explorer flies on from orbit on its own tank.
            JButton dispatch = new JButton("Dispatch...");
            dispatch.setName(TARGET_DISPATCH);
            dispatch.addActionListener(e -> spacecolony.ui.dialogs.DispatchShipDialog.show(this, engine, s.id));
            content.add(dispatch);
        } else if (orbiting != null) {
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

    private void renderShipUnderConstruction(Ship s) {
        World w = engine.world();
        content.add(wrappingText(underConstructionText(w, s), UiColors.FOREGROUND_DIM, null));
        ProgressBar bar = new ProgressBar(spacecolony.sim.ShipYards.progress(s), UiColors.WARNING);
        bar.setName(TARGET_PROGRESS_PREFIX + "ship");
        content.add(bar);
        addLabel("Cost: " + s.shipClass.cost().describe(), UiColors.FOREGROUND_DIM);
        content.add(Box.createVerticalStrut(6));
        JButton cancel = new JButton("Cancel build");
        cancel.setToolTipText("Stop building " + s.name + " and get its METAL and COMPONENTS back");
        cancel.addActionListener(e -> engine.enqueue(new spacecolony.sim.commands.RetireShipCommand(s.id)));
        content.add(cancel);
    }

    /** "Under construction at Earth Hub (Shipyard L1): 12 days left", or paused when the yard is damaged. */
    static String underConstructionText(World w, Ship s) {
        Site at = w.findSite(s.currentSiteId);
        Building yard = spacecolony.sim.ShipYards.yardOf(w, s);
        String where = (at != null ? at.name : s.currentSiteId) + (yard != null ? " (Shipyard L" + yard.level + ")" : "");
        if (yard == null || !yard.isOperational())
            return "Under construction at " + where + ": paused, the shipyard is damaged";
        return "Under construction at " + where + ": " + spacecolony.sim.ShipYards.daysLeftText(w, s);
    }

    /**
     * A building row's text with its progress note (Plan 10 §6.1): a lab adds the tech under
     * research, a shipyard says what it's building.
     */
    static String rowText(World w, Site s, Building b, String base) {
        if (b.level == 0 || !b.enabled && b.type != BuildingType.SHIPYARD) return base;
        return switch (b.type) {
            case RESEARCH_LAB -> base + " · " + researchNote(w);
            case SHIPYARD -> {
                Ship ship = spacecolony.sim.ShipYards.building(w, s, b);
                String name = BuildingCatalog.displayName(b.type) + " L" + b.level;
                String upgrading = b.isUnderConstruction() && b.enabled ? "  (upgrading, " + b.daysLeft + " d)" : "";
                if (ship == null) yield b.enabled ? "  " + name + "   idle" + upgrading : base;
                if (!b.enabled) yield "  " + name + "   paused: damaged (" + ship.name + ")";
                yield "  " + name + "   building " + ship.name + ", " + spacecolony.sim.ShipYards.daysLeftText(w, ship) + upgrading;
            }
            default -> base;
        };
    }

    /** "Ion Drives 120 / 300", or "no research queued". */
    static String researchNote(World w) {
        if (w.tech.activeId == null) return "no research queued";
        spacecolony.sim.Tech t = spacecolony.sim.TechCatalog.get(w.tech.activeId);
        if (t == null) return "no research queued";
        return String.format("%s %.0f / %d", t.name(), Math.floor(w.tech.accumulatedPoints), t.researchCost());
    }

    /** How far along a building's current job is, 0..1, or null when it has none to show. */
    static Double progressFraction(World w, Site s, Building b) {
        if (b.isUnderConstruction()) {
            int total = (b.level == 0 ? Construction.buildCost(b.type) : Construction.upgradeCost(b.type, b.level + 1)).days();
            return total <= 0 ? null : Math.max(0.0, Math.min(1.0, 1.0 - b.daysLeft / (double) total));
        }
        if (b.level == 0) return null;
        return switch (b.type) {
            case SHIPYARD -> {
                Ship ship = spacecolony.sim.ShipYards.building(w, s, b);
                yield ship == null ? null : spacecolony.sim.ShipYards.progress(ship);
            }
            case RESEARCH_LAB -> researchFraction(w);
            default -> null;
        };
    }

    /** The active tech's share done, 0..1, or null when nothing is queued. */
    static Double researchFraction(World w) {
        if (w.tech.activeId == null) return null;
        spacecolony.sim.Tech t = spacecolony.sim.TechCatalog.get(w.tech.activeId);
        if (t == null || t.researchCost() <= 0) return null;
        return Math.max(0.0, Math.min(1.0, w.tech.accumulatedPoints / t.researchCost()));
    }

    private static java.awt.Color progressColor(Building b) {
        if (!b.enabled) return UiColors.FOREGROUND_DIM;
        if (b.isUnderConstruction()) return UiColors.FOREGROUND_DIM;
        return b.type == BuildingType.RESEARCH_LAB ? UiColors.INFO : UiColors.WARNING;
    }

    /** One building row: its label, a progress bar when it has one, and a ⋯ button that opens {@link BuildingMenu}. */
    private JPanel buildingRow(Site s, Building b, String text, java.awt.Color fg, String techs,
                               Double progress, java.awt.Color barColor) {
        JPanel row = new JPanel(new BorderLayout()) {
            // The text wraps, so the row's height follows its width; never stretch taller than that.
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        // The tile stands in for the text's indent.
        row.add(wrappingText(text.stripLeading(), fg, techs.isEmpty() ? null : "Techs: " + techs), BorderLayout.CENTER);
        // The building's tile, pinned to the first line like the ⋯ button.
        JLabel icon = new JLabel(GameIcons.building(b.type, GameIcons.MEDIUM));
        icon.setName(TARGET_BUILDING_ICON_PREFIX + b.id);
        icon.setToolTipText(BuildingCatalog.displayName(b.type));
        icon.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        JPanel iconWrap = new JPanel(new BorderLayout());
        iconWrap.setOpaque(false);
        iconWrap.add(icon, BorderLayout.NORTH);
        row.add(iconWrap, BorderLayout.WEST);
        row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        if (progress != null) {
            // Under the text, across the row (not nested, so the wrapped text keeps its layout).
            ProgressBar bar = new ProgressBar(progress, barColor);
            bar.setName(TARGET_PROGRESS_PREFIX + b.id);
            bar.setToolTipText(String.format("%.0f%% done", progress * 100));
            row.add(bar, BorderLayout.SOUTH);
        }
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
     * "Tank: 64 / 100 (40 hydrogen, 48 synfuel)" for an explorer, "Fuel: 40 hydrogen, 48 synfuel
     * (64 value)" otherwise; ships fly on their origin site's fuel, drawn at departure, so an
     * empty tank is normal.
     */
    static String tankText(Ship s) {
        double value = s.fuelValue();
        boolean mixed = s.tank.getOrDefault(Resource.SYNFUEL, 0.0) >= 0.5;
        String fuel;
        if (s.shipClass.tankCap() > 0)
            fuel = String.format("Tank: %.0f / %.0f (%s)", value, s.shipClass.tankCap(), Fuels.describe(s.tank));
        else if (value < 1e-6)
            fuel = "Fuel: none" + (s.currentSiteId != null ? " (fills from the colony's fuel on departure)" : "");
        else fuel = "Fuel: " + Fuels.describe(s.tank) + (mixed ? String.format(" (%.0f value)", value) : "");
        return fuel;
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

    /** "Impact dust: farms at 75% for 14 more days", or null with clear skies. */
    static String dustLine(Site s) {
        if (s.dustDays <= 0) return null;
        return String.format("Impact dust: farms at %.0f%% for %d more %s",
            s.dustFactor * 100, s.dustDays, s.dustDays == 1 ? "day" : "days");
    }

    /**
     * What a building did yesterday: "+2.4 ORE · +0.9 SILICATE", "idle: no BIOMASS",
     * "60%: short of ORE", "+100 cap", "(disabled)".
     */
    static String buildingResult(Building b, BuildingOutcome o, DayReport day, TechState tech) {
        if (b.enabled && b.offlineDays > 0)
            return "offline after solar flare, back in " + days(b.offlineDays);
        if (!b.enabled || (o != null && o.limit() == Limit.DISABLED)) {
            String repair = "repair " + BuildCost.describe(Construction.repairCost(b.type).resources());
            if (b.damagedBy == null) return "damaged: " + repair;
            return "damaged by " + EventStripPanel.prettyKind(b.damagedBy).toLowerCase(java.util.Locale.ROOT)
                + ", " + EventStripPanel.date(b.damagedTick) + ": " + repair;
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
        // Dust's share is on the colony's dust line; the efficiency here would mix in the inputs.
        if (o.limit() == Limit.DUST) return made.isEmpty() ? why : made + " (" + why + ")";
        if (o.efficiency() <= 1e-9) return "idle: " + why;
        if (o.efficiency() < 0.995) return String.format("%.0f%%: %s", o.efficiency() * 100, why);
        return made.isEmpty() ? why : made + " (" + why + ")";
    }

    private static java.awt.Color buildingColor(Building b, BuildingOutcome o) {
        if (!b.enabled || b.offlineDays > 0) return UiColors.WARNING;
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
