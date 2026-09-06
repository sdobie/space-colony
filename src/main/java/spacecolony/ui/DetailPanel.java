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
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;

public class DetailPanel extends JPanel {
    private final Engine engine;
    private final JPanel content = new JPanel();
    private final SphereMiniRenderer miniRenderer;

    public DetailPanel(Engine engine) {
        this.engine = engine;
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
        addLabel("Body: " + s.bodyId + "  ·  pop " + s.population + "/" + s.populationCap, UiColors.FOREGROUND_DIM);
        addLabel(String.format("Morale: %.2f", s.morale), UiColors.FOREGROUND_DIM);
        content.add(Box.createVerticalStrut(6));
        addLabel("Stockpile:", UiColors.FOREGROUND_DIM);
        JPanel stockGrid = new JPanel(new GridLayout(0, 2, 6, 2));
        stockGrid.setOpaque(false);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            stockGrid.add(rowLabel(r.name(), UiColors.FOREGROUND_DIM));
            stockGrid.add(rowLabel(String.format("%.0f", s.stockpile.getOrDefault(r, 0.0)), UiColors.FOREGROUND));
        }
        content.add(stockGrid);
        content.add(Box.createVerticalStrut(6));
        addLabel("Buildings:", UiColors.FOREGROUND_DIM);
        for (Building b : s.buildings) {
            String enabled = b.enabled ? "" : "  (disabled)";
            addLabel("  " + b.type + " L" + b.level + enabled,
                b.enabled ? UiColors.FOREGROUND : UiColors.WARNING);
        }
        content.add(Box.createVerticalStrut(8));
        JButton build = new JButton("Build building...");
        build.addActionListener(e -> spacecolony.ui.dialogs.BuildBuildingDialog.show(this, engine, s.id));
        content.add(build);
        boolean hasShipyard = s.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.enabled);
        if (hasShipyard) {
            JButton ship = new JButton("Build ship...");
            ship.addActionListener(e -> spacecolony.ui.dialogs.BuildShipDialog.show(this, engine, s.id));
            content.add(ship);
        }
    }

    private void renderShip(Ship s) {
        if (s == null) { renderNone(); return; }
        addLabel(s.name + "  (" + s.shipClass + ")", UiColors.FOREGROUND);
        addLabel("State: " + s.state, UiColors.FOREGROUND_DIM);
        if (s.currentSiteId != null) addLabel("At: " + s.currentSiteId, UiColors.FOREGROUND_DIM);
        if (s.transit != null && s.state == ShipState.IN_TRANSIT) {
            addLabel("→ " + s.transit.destSiteId() + " (arrival t=" + s.transit.arrivalTick() + ")", UiColors.FOREGROUND_DIM);
        }
        addLabel(String.format("Fuel: %.1f", s.fuel), UiColors.FOREGROUND_DIM);
        if (s.cargoMass() > 0) {
            addLabel("Cargo:", UiColors.FOREGROUND_DIM);
            for (Resource r : Resource.values()) {
                double v = s.cargo.getOrDefault(r, 0.0);
                if (v > 1e-6) addLabel("  " + r + ": " + String.format("%.0f", v), UiColors.FOREGROUND);
            }
        }
        if (s.state == ShipState.IDLE) {
            JButton dispatch = new JButton("Dispatch...");
            dispatch.addActionListener(e -> spacecolony.ui.dialogs.DispatchShipDialog.show(this, engine, s.id));
            content.add(dispatch);
        }
    }

    private void addLabel(String text, java.awt.Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setAlignmentX(LEFT_ALIGNMENT);
        content.add(l);
    }

    private JLabel rowLabel(String text, java.awt.Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        return l;
    }
}
