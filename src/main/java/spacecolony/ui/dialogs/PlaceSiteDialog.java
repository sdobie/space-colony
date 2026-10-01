package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.Body;
import spacecolony.sim.ResourceSurvey;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.World;
import spacecolony.sim.commands.BuildSiteCommand;

public class PlaceSiteDialog {
    static final String UNKNOWN_YIELDS = "unknown (unsurveyed)";

    /**
     * Label/value pairs for the ground at (lat, lon): "ORE:" / "Rich  .72" per surveyed resource
     * found there, or one "Yields here:" / "unknown (unsurveyed)" row on an unsurveyed body.
     */
    static List<String[]> yieldRows(World w, Body b, double lat, double lon) {
        List<String[]> rows = new ArrayList<>();
        if (b == null) return rows;
        if (!w.isSurveyed(b.id)) {
            rows.add(new String[] { "Yields here:", UNKNOWN_YIELDS });
            return rows;
        }
        List<ResourceSurvey.Entry> here = ResourceSurvey.at(b, lat, lon);
        if (here.isEmpty()) rows.add(new String[] { "Yields here:", "nothing to mine or farm" });
        for (ResourceSurvey.Entry e : here)
            rows.add(new String[] { e.resource() + ":", e.rating().label() + "  " + ResourceSurvey.fmt(e.best()) });
        return rows;
    }

    /** lat and lon in radians. */
    public static void show(Component parent, Engine engine, String bodyId, double lat, double lon) {
        // Find a colonizer at this body, if any.
        Ship colonizer = null;
        for (Ship s : engine.world().ships) {
            if (s.shipClass != ShipClass.COLONIZER) continue;
            if (bodyId.equals(s.orbitingBodyId)) { colonizer = s; break; }
            if (s.currentSiteId != null) {
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
        for (String[] row : yieldRows(engine.world(), engine.world().findBody(bodyId), lat, lon)) {
            form.add(new JLabel(row[0]));
            form.add(new JLabel(row[1]));
        }
        int result = JOptionPane.showConfirmDialog(parent, form, "Place site on " + bodyId,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        engine.enqueue(new BuildSiteCommand(id.getText(), name.getText(), bodyId, lat, lon, colonizer.id));
    }
}
