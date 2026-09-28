package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.Site;
import spacecolony.debug.ObjectInspectorDialog;
import spacecolony.sim.economy.BuildingOutcome;
import spacecolony.sim.economy.DayReport;
import spacecolony.sim.economy.Outlook;

public class ColonyListPanel extends JPanel {
    private final Engine engine;
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));

    public ColonyListPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.PANEL_BORDER));
        setName(TARGET);
        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        add(scroll, BorderLayout.CENTER);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced
             || e instanceof EngineEvent.SelectionChanged) refresh();
        });
        refresh();
    }

    /** Component name the tutorial highlights (Plan 6 §5.4). */
    public static final String TARGET = "colonylist";

    private void refresh() {
        list.removeAll();
        addHeader("Colonies");
        for (var body : engine.world().bodies) {
            for (Site s : body.sites) {
                list.add(row(s.name + "  (" + body.name + ")", Selection.site(s.id), rowColor(s)));
            }
        }
        addHeader("Ships (" + engine.world().ships.size() + ")");
        for (Ship s : engine.world().ships) {
            list.add(row(shipRowText(engine.world(), s), Selection.ship(s.id), UiColors.FOREGROUND));
        }
        list.revalidate();
        list.repaint();
    }

    private void addHeader(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(UiColors.FOREGROUND_DIM);
        l.setBorder(BorderFactory.createEmptyBorder(8, 8, 2, 8));
        list.add(l);
    }

    private Component row(String text, Selection sel, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setOpaque(true);
        l.setBackground(sel.equals(engine.selection()) ? UiColors.SELECTION : UiColors.PANEL_BACKGROUND);
        l.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isShiftDown() && engine.debugEnabled()) inspect(l, sel);
                else engine.setSelection(sel);
            }
        });
        return l;
    }

    /** Debug Shift+click: open the inspector on the row's Site or Ship. */
    private void inspect(Component from, Selection sel) {
        Object target = sel.kind() == Selection.Kind.SITE ? engine.world().findSite(sel.id())
                      : engine.world().findShip(sel.id());
        if (target == null) return;
        String kind = sel.kind() == Selection.Kind.SITE ? "Site " : "Ship ";
        ObjectInspectorDialog.inspect(from, engine, target, kind + sel.id());
    }

    /**
     * Red when FOOD or WATER is gone; amber when either runs out within 10 days or a
     * building sat short of an input yesterday.
     */
    static Color rowColor(Site s) {
        double food = s.stockpile.getOrDefault(Resource.FOOD, 0.0);
        double water = s.stockpile.getOrDefault(Resource.WATER, 0.0);
        if (food < 1e-6 || water < 1e-6) return UiColors.ERROR;
        DayReport d = s.lastDay;
        if (d == null) return UiColors.FOREGROUND;
        if (soon(s, Resource.FOOD, d) || soon(s, Resource.WATER, d)) return UiColors.WARNING;
        if (d.buildings().stream().anyMatch(BuildingOutcome::starved)) return UiColors.WARNING;
        return UiColors.FOREGROUND;
    }

    private static boolean soon(Site s, Resource r, DayReport d) {
        return Outlook.of(s.stockpile.getOrDefault(r, 0.0), s.stockpileCap.getOrDefault(r, 1000.0), d.net(r))
            instanceof Outlook.EmptyIn e && e.days() <= 10;
    }

    /** "Ark  ·", or "Ark  · orbiting Mars" for a colonizer waiting at an unsettled body. */
    static String shipRowText(spacecolony.sim.World w, Ship s) {
        String text = s.name + "  " + stateGlyph(s);
        if (s.orbitingBodyId != null) {
            var b = w.findBody(s.orbitingBodyId);
            text += " orbiting " + (b != null ? b.name : s.orbitingBodyId);
        }
        return text;
    }

    private static String stateGlyph(Ship s) {
        return switch (s.state) {
            case IDLE        -> "·";
            case LOADING     -> "▾";
            case IN_TRANSIT  -> "→";
            case UNLOADING   -> "▴";
        };
    }
}
