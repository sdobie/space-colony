package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.phases.CommandPhase;

public class DispatchShipDialog {
    public static void show(Component parent, Engine engine, String shipId) {
        Ship ship = engine.world().findShip(shipId);
        if (ship == null) return;
        Map<String, Destination> destinations = destinations(engine.world(), ship);
        if (destinations.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No destination sites exist yet.");
            return;
        }
        JComboBox<String> dest = new JComboBox<>(destinations.keySet().toArray(new String[0]));
        // One text field per resource (blank = 0).
        Map<Resource, JTextField> fields = new EnumMap<>(Resource.class);
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Destination:"));
        form.add(dest);
        if (ship.shipClass.cargoCap() <= 0) {
            form.add(new JLabel(NO_CARGO_TEXT));
            form.add(new JLabel(""));
        }
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable() || ship.shipClass.cargoCap() <= 0) continue;
            JTextField f = new JTextField("0");
            fields.put(r, f);
            form.add(new JLabel(r.name() + ":"));
            form.add(f);
        }
        // Re-show the form (entries kept) until the cargo fits, the trip is affordable, or the
        // player cancels, so an overfull hold or a fuel shortfall is a popup right here rather
        // than a line in the event strip.
        while (true) {
            int result = JOptionPane.showConfirmDialog(parent, form,
                "Dispatch " + shipId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            Map<Resource, Double> manifest = new EnumMap<>(Resource.class);
            for (var entry : fields.entrySet()) {
                String raw = entry.getValue().getText().trim();
                if (raw.isEmpty()) continue;
                try {
                    double v = Double.parseDouble(raw);
                    if (v > 0) manifest.put(entry.getKey(), v);
                } catch (NumberFormatException ignored) { /* skip bad input */ }
            }
            String overflow = CommandPhase.cargoOverflow(ship, manifest);
            if (overflow != null) {
                JOptionPane.showMessageDialog(parent, overflow + ".",
                    "Too much cargo", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            Destination d = destinations.get((String) dest.getSelectedItem());
            String shortfall = fuelShortfall(engine.world(), ship, d, manifest);
            if (shortfall != null) {
                JOptionPane.showMessageDialog(parent,
                    shortfall + ".\nCarry less cargo, pick a closer destination, or bring more FUEL here.",
                    "Not enough fuel", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            engine.enqueue(d.siteId() != null
                ? new DispatchShipCommand(shipId, d.siteId(), manifest)
                : DispatchShipCommand.toBody(shipId, d.bodyId(), manifest));
            return;
        }
    }

    /** The fuel rejection the sim would give this dispatch, or null if the trip is affordable. */
    static String fuelShortfall(World w, Ship ship, Destination d, Map<Resource, Double> manifest) {
        String destBodyId = d.bodyId();
        if (d.siteId() != null) {
            Site site = w.findSite(d.siteId());
            if (site == null) return null; // the sim reports this one
            destBodyId = site.bodyId;
        }
        return CommandPhase.fuelShortfall(w, ship, destBodyId, manifest);
    }

    static final String NO_CARGO_TEXT = "Explorers carry no cargo";

    /** Where a combo label sends the ship: a site, or (colonizers and explorers) a body. */
    record Destination(String siteId, String bodyId) {}

    /**
     * Combo labels in order: every site id, then, for a colonizer, "&lt;Body&gt; (unsettled)" for
     * each body without a site, or for an explorer, "&lt;Body&gt; (surveyed)" or "(unsurveyed)"
     * for every body but the one it orbits.
     */
    static Map<String, Destination> destinations(World w, Ship ship) {
        Map<String, Destination> out = new LinkedHashMap<>();
        for (var b : w.bodies)
            for (Site s : b.sites)
                out.put(s.id, new Destination(s.id, null));
        if (ship.shipClass == ShipClass.COLONIZER) {
            for (var b : w.bodies)
                if (b.sites.isEmpty()) out.put(b.name + " (unsettled)", new Destination(null, b.id));
        } else if (ship.shipClass == ShipClass.EXPLORER) {
            for (var b : w.bodies) {
                if (b.id.equals(ship.orbitingBodyId)) continue;
                String tag = w.isSurveyed(b.id) ? " (surveyed)" : " (unsurveyed)";
                out.put(b.name + tag, new Destination(null, b.id));
            }
        }
        return out;
    }
}
