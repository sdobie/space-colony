package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.sim.BuildingType;
import spacecolony.sim.commands.BuildBuildingCommand;

public class BuildBuildingDialog {
    public static void show(Component parent, Engine engine, String siteId) {
        JComboBox<BuildingType> combo = new JComboBox<>(BuildingType.values());
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Building:"));
        form.add(combo);
        int result = JOptionPane.showConfirmDialog(parent, form,
            "Build at " + siteId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        BuildingType picked = (BuildingType) combo.getSelectedItem();
        engine.enqueue(new BuildBuildingCommand(siteId, picked));
    }
}
