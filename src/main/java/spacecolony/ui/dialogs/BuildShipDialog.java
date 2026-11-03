package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.Building;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipYards;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.commands.BuildShipCommand;

/** Orders a ship at a colony's shipyard; shows each class's cost and days (Plan 10 §6). */
public class BuildShipDialog {
    public static void show(Component parent, Engine engine, String shipyardSiteId) {
        World w = engine.world();
        Site site = w.findSite(shipyardSiteId);
        if (site == null) return;
        JTextField id = new JTextField("ship-" + (w.ships.size() + 1));
        JTextField name = new JTextField("Hauler " + (w.ships.size() + 1));
        JComboBox<ShipClass> combo = new JComboBox<>(ShipClass.values());
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean selected, boolean focus) {
                String text = value instanceof ShipClass c ? classLabel(w, site, c) : String.valueOf(value);
                return super.getListCellRendererComponent(list, text, index, selected, focus);
            }
        });
        JLabel status = new JLabel();
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        Runnable refresh = () -> {
            ShipClass c = (ShipClass) combo.getSelectedItem();
            String why = ShipYards.whyNotBuild(w, site, c);
            status.setText(why != null ? why : statusLine(w, site, c));
            ok.setEnabled(why == null);
        };
        combo.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) return;
            ShipClass c = (ShipClass) e.getItem();
            // Follow the class in the default name, unless the player typed their own.
            String n = name.getText();
            for (ShipClass k : ShipClass.values())
                if (n.startsWith(title(k) + " ")) { name.setText(title(c) + n.substring(title(k).length())); break; }
            refresh.run();
        });
        refresh.run();

        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Ship ID:"));   form.add(id);
        form.add(new JLabel("Name:"));      form.add(name);
        form.add(new JLabel("Class:"));     form.add(combo);
        JPanel body = new JPanel(new java.awt.BorderLayout(0, 8));
        body.add(form, java.awt.BorderLayout.CENTER);
        body.add(status, java.awt.BorderLayout.SOUTH);

        JOptionPane pane = new JOptionPane(body, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
            null, new Object[] { ok, cancel }, ok);
        ok.addActionListener(e -> pane.setValue(ok));
        cancel.addActionListener(e -> pane.setValue(cancel));
        JDialog dialog = pane.createDialog(parent, "Build ship at " + site.name);
        dialog.setVisible(true);
        dialog.dispose();
        if (pane.getValue() != ok) return;
        engine.enqueue(new BuildShipCommand(id.getText(), name.getText(),
            (ShipClass) combo.getSelectedItem(), shipyardSiteId));
    }

    /** "HAULER · 30 METAL, 10 COMPONENTS · 20 days", the days at this colony's free (or best) shipyard. */
    public static String classLabel(World w, Site s, ShipClass c) {
        return c.name() + " · " + spacecolony.sim.BuildCost.describe(c.cost().resources())
            + " · " + days(ShipYards.days(c, yardLevel(w, s)));
    }

    /** "Built by Shipyard L1, ready in 20 days". */
    public static String statusLine(World w, Site s, ShipClass c) {
        int level = yardLevel(w, s);
        return "Built by Shipyard L" + level + ", ready in " + days(ShipYards.days(c, level));
    }

    private static int yardLevel(World w, Site s) {
        Building yard = ShipYards.freeYard(w, s);
        if (yard != null) return yard.level;
        int best = 1;
        for (Building b : s.buildings)
            if (b.type == spacecolony.sim.BuildingType.SHIPYARD && b.isOperational()) best = Math.max(best, b.level);
        return best;
    }

    private static String days(int n) { return n + (n == 1 ? " day" : " days"); }

    private static String title(ShipClass c) {
        String n = c.name().toLowerCase();
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
