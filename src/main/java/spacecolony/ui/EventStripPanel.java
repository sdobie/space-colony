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
            if (e instanceof EngineEvent.WorldChanged) refresh();
        });
        refresh();
    }

    private void refresh() {
        list.removeAll();
        int shown = 0;
        var iter = engine.world().recentEvents.descendingIterator();
        while (iter.hasNext() && shown < VISIBLE_EVENTS) {
            Event ev = iter.next();
            JLabel label = new JLabel(String.format("[t=%d] %s: %s", ev.tick(), ev.kind(), ev.message()));
            label.setForeground(colorFor(ev.severity()));
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            list.add(label);
            shown++;
        }
        list.revalidate();
        list.repaint();
    }

    private static Color colorFor(EventSeverity s) {
        return switch (s) {
            case INFO    -> UiColors.INFO;
            case WARNING -> UiColors.WARNING;
            case ERROR   -> UiColors.ERROR;
        };
    }
}
