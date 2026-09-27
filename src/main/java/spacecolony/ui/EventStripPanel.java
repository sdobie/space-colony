package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.World;

public class EventStripPanel extends JPanel {
    private static final int VISIBLE_EVENTS = 20;
    private final Engine engine;
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));
    // Session-only severity filters, all on by default.
    private final Map<EventSeverity, JToggleButton> filters = new EnumMap<>(EventSeverity.class);

    public EventStripPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.PANEL_BORDER));
        setPreferredSize(new Dimension(0, 110));

        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        add(scroll, BorderLayout.CENTER);

        JPanel toggles = new JPanel(new GridLayout(0, 1, 0, 2));
        toggles.setOpaque(false);
        toggles.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 8));
        for (EventSeverity sev : EventSeverity.values()) {
            JToggleButton b = new JToggleButton(sev == EventSeverity.WARNING ? "WARN" : sev.name(), true);
            // Flat buttons drawn in the strip's colours: lit in the severity colour when on, dim when off.
            b.setContentAreaFilled(false);
            b.setFocusable(false);
            b.setMargin(new Insets(0, 6, 0, 6));
            b.setToolTipText("Show or hide " + sev.name().toLowerCase(java.util.Locale.ROOT) + " events");
            Runnable style = () -> {
                Color c = b.isSelected() ? colorFor(sev) : UiColors.FOREGROUND_DIM;
                b.setForeground(c);
                b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(b.isSelected() ? c : UiColors.PANEL_BORDER),
                    BorderFactory.createEmptyBorder(1, 8, 1, 8)));
            };
            style.run();
            b.addActionListener(e -> { style.run(); refresh(); });
            filters.put(sev, b);
            toggles.add(b);
        }
        add(toggles, BorderLayout.EAST);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced) refresh();
        });
        refresh();
    }

    private void refresh() {
        list.removeAll();
        World w = engine.world();
        int shown = 0;
        var iter = w.recentEvents.descendingIterator();
        while (iter.hasNext() && shown < VISIBLE_EVENTS) {
            Event ev = iter.next();
            if (!filters.get(ev.severity()).isSelected()) continue;
            JLabel label = new JLabel(format(ev));
            label.setForeground(colorFor(ev.severity()));
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            Selection target = targetOf(w, ev);
            if (target != null) {
                label.setToolTipText(prettyKind(ev.kind()) + " at tick " + ev.tick() + " · click to select");
                label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                label.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { engine.setSelection(target); }
                });
            } else {
                label.setToolTipText(prettyKind(ev.kind()) + " at tick " + ev.tick());
            }
            list.add(label);
            shown++;
        }
        if (shown == 0) {
            boolean anyEvents = !w.recentEvents.isEmpty();
            JLabel empty = new JLabel(anyEvents ? "No matching events." : "No events yet.");
            empty.setForeground(UiColors.FOREGROUND_DIM);
            empty.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            list.add(empty);
        }
        list.revalidate();
        list.repaint();
    }

    /** The entity an event is about, preferring ship, then site, then body; null if none still exists. */
    static Selection targetOf(World w, Event ev) {
        if (ev.shipId() != null && w.findShip(ev.shipId()) != null) return Selection.ship(ev.shipId());
        if (ev.siteId() != null && w.findSite(ev.siteId()) != null) return Selection.site(ev.siteId());
        if (ev.bodyId() != null && w.findBody(ev.bodyId()) != null) return Selection.body(ev.bodyId());
        return null;
    }

    /** The filter toggle for a severity; package-private for tests. */
    JToggleButton filter(EventSeverity s) { return filters.get(s); }

    /** The row labels currently shown; package-private for tests. */
    List<JLabel> rows() {
        List<JLabel> out = new ArrayList<>();
        for (Component c : list.getComponents()) if (c instanceof JLabel l) out.add(l);
        return out;
    }

    /**
     * e.g. "Y0 D12  Meteor strike on Mars", on the same calendar as the top bar. Sim messages
     * already name what happened, so the kind is only prefixed for rejected commands, whose
     * message is the bare rejection reason.
     */
    static String format(Event ev) {
        String msg = ev.kind() == EventKind.COMMAND_REJECTED
            ? prettyKind(ev.kind()) + ": " + ev.message() : ev.message();
        return String.format("Y%d D%d  %s", ev.tick() / 365, ev.tick() % 365 + 1, msg);
    }

    /** SHIP_OUT_OF_FUEL -> "Ship out of fuel". */
    static String prettyKind(EventKind k) {
        String s = k.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static Color colorFor(EventSeverity s) {
        return switch (s) {
            case INFO    -> UiColors.INFO;
            case WARNING -> UiColors.WARNING;
            case ERROR   -> UiColors.ERROR;
        };
    }
}
