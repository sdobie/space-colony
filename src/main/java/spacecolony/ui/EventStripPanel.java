package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;

public class EventStripPanel extends JPanel {
    private static final int VISIBLE_EVENTS = 20;
    private final Engine engine;
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));

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

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced) refresh();
        });
        refresh();
    }

    private void refresh() {
        list.removeAll();
        int shown = 0;
        var iter = engine.world().recentEvents.descendingIterator();
        while (iter.hasNext() && shown < VISIBLE_EVENTS) {
            Event ev = iter.next();
            JLabel label = new JLabel(format(ev));
            label.setForeground(colorFor(ev.severity()));
            label.setToolTipText(prettyKind(ev.kind()) + " at tick " + ev.tick());
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            list.add(label);
            shown++;
        }
        if (shown == 0) {
            JLabel empty = new JLabel("No events yet.");
            empty.setForeground(UiColors.FOREGROUND_DIM);
            empty.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            list.add(empty);
        }
        list.revalidate();
        list.repaint();
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
