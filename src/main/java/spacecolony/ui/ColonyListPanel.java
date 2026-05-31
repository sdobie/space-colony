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

public class ColonyListPanel extends JPanel {
    private final Engine engine;
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));

    public ColonyListPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.PANEL_BORDER));
        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        add(scroll, BorderLayout.CENTER);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.SelectionChanged) refresh();
        });
        refresh();
    }

    private void refresh() {
        list.removeAll();
        addHeader("Colonies");
        for (var body : engine.world().bodies) {
            for (Site s : body.sites) {
                boolean shortFood = s.stockpile.getOrDefault(Resource.FOOD, 0.0) < 1e-6;
                Color fg = shortFood ? UiColors.WARNING : UiColors.FOREGROUND;
                list.add(row(s.name + "  (" + body.name + ")", Selection.site(s.id), fg));
            }
        }
        addHeader("Ships (" + engine.world().ships.size() + ")");
        for (Ship s : engine.world().ships) {
            list.add(row(s.name + "  " + stateGlyph(s), Selection.ship(s.id), UiColors.FOREGROUND));
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
            @Override public void mousePressed(MouseEvent e) { engine.setSelection(sel); }
        });
        return l;
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
