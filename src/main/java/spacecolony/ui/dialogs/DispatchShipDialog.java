package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.commands.DispatchShipCommand;

public class DispatchShipDialog {
    public static void show(Component parent, Engine engine, String shipId) {
        // Collect all site IDs.
        List<String> siteIds = new ArrayList<>();
        for (var b : engine.world().bodies)
            for (Site s : b.sites)
                siteIds.add(s.id);
        if (siteIds.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No destination sites exist yet.");
            return;
        }
        JComboBox<String> dest = new JComboBox<>(siteIds.toArray(new String[0]));
        // One text field per resource (blank = 0).
        Map<Resource, JTextField> fields = new EnumMap<>(Resource.class);
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Destination:"));
        form.add(dest);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            JTextField f = new JTextField("0");
            fields.put(r, f);
            form.add(new JLabel(r.name() + ":"));
            form.add(f);
        }
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
        engine.enqueue(new DispatchShipCommand(shipId, (String) dest.getSelectedItem(), manifest));
    }
}
