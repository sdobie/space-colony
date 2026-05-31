package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.Tech;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.commands.QueueResearchCommand;

public class TechModal {
    public static void show(Component owner, Engine engine) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner), "Tech Tree", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        for (Tech t : TechCatalog.all()) {
            boolean done = engine.world().tech.researched.contains(t.id());
            boolean active = t.id().equals(engine.world().tech.activeId);
            JLabel row = new JLabel(String.format("%s — cost %d  %s", t.name(), t.researchCost(),
                done ? "✓" : active ? "(active, " + (int) engine.world().tech.accumulatedPoints + " pts)" : ""));
            row.setOpaque(true);
            Color bg = active ? UiColors.SELECTION : UiColors.PANEL_BACKGROUND;
            Color fg = done ? UiColors.FOREGROUND_DIM : UiColors.FOREGROUND;
            row.setBackground(bg);
            row.setForeground(fg);
            row.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            if (!done && !active) {
                row.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        engine.enqueue(new QueueResearchCommand(t.id()));
                        dlg.dispose();
                    }
                });
            }
            list.add(row);
        }
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel();
        south.add(close);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setSize(520, 520);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }
}
