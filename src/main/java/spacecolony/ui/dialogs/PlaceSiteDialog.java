package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.commands.BuildSiteCommand;

public class PlaceSiteDialog {
    /** lat and lon in radians. */
    public static void show(Component parent, Engine engine, String bodyId, double lat, double lon) {
        // Find a colonizer at this body, if any.
        Ship colonizer = null;
        for (Ship s : engine.world().ships) {
            if (s.shipClass == ShipClass.COLONIZER && s.currentSiteId != null) {
                var site = engine.world().findSite(s.currentSiteId);
                if (site != null && site.bodyId.equals(bodyId)) { colonizer = s; break; }
            }
        }
        if (colonizer == null) {
            JOptionPane.showMessageDialog(parent,
                "No COLONIZER ship at this body. Build one and dispatch it here first.",
                "Cannot place site", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextField id = new JTextField("site-" + bodyId + "-" + (engine.world().tick % 10000));
        JTextField name = new JTextField(bodyId + " outpost");
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Site ID:"));    form.add(id);
        form.add(new JLabel("Name:"));       form.add(name);
        form.add(new JLabel("Latitude:"));   form.add(new JLabel(String.format("%.3f rad", lat)));
        form.add(new JLabel("Longitude:"));  form.add(new JLabel(String.format("%.3f rad", lon)));
        int result = JOptionPane.showConfirmDialog(parent, form, "Place site on " + bodyId,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        engine.enqueue(new BuildSiteCommand(id.getText(), name.getText(), bodyId, lat, lon, colonizer.id));
    }
}
