package spacecolony.ui.debug;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;

/** Modal window hosting {@link ObjectInspectorPanel} (Shift+click in debug mode). */
public final class ObjectInspectorDialog {
    private ObjectInspectorDialog() {}

    public static void show(Component parent, Engine engine, Selection target) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent),
            "Inspect " + target.kind().name().toLowerCase() + " " + target.id(),
            JDialog.ModalityType.APPLICATION_MODAL);
        d.setContentPane(new ObjectInspectorPanel(engine, target));
        d.setPreferredSize(new Dimension(520, 640));
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }
}
