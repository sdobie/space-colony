package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.ShipClass;
import spacecolony.sim.commands.BuildShipCommand;

public class BuildShipDialog {
    public static void show(Component parent, Engine engine, String shipyardSiteId) {
        JTextField id = new JTextField("ship-" + (engine.world().ships.size() + 1));
        JTextField name = new JTextField("Hauler " + (engine.world().ships.size() + 1));
        JComboBox<ShipClass> combo = new JComboBox<>(ShipClass.values());
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Ship ID:"));   form.add(id);
        form.add(new JLabel("Name:"));      form.add(name);
        form.add(new JLabel("Class:"));     form.add(combo);
        int result = JOptionPane.showConfirmDialog(parent, form,
            "Build ship at " + shipyardSiteId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        engine.enqueue(new BuildShipCommand(id.getText(), name.getText(),
            (ShipClass) combo.getSelectedItem(), shipyardSiteId));
    }
}
