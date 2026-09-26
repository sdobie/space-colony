package spacecolony.ui.debug;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.EngineListener;
import spacecolony.engine.Selection;

/** Modal window hosting {@link ObjectInspectorPanel} (Shift+click in debug mode). */
public final class ObjectInspectorDialog {
    private ObjectInspectorDialog() {}

    public static void show(Component parent, Engine engine, Selection target) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent),
            "Inspect " + target.kind().name().toLowerCase() + " " + target.id(),
            JDialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        d.setContentPane(new ObjectInspectorPanel(engine, target));
        // The inspected object belongs to the old world after Load/New, so close with it.
        EngineListener closeOnReplace = e -> { if (e instanceof EngineEvent.WorldReplaced) d.dispose(); };
        engine.addListener(closeOnReplace);
        d.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { engine.removeListener(closeOnReplace); }
        });
        d.setPreferredSize(new Dimension(520, 640));
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }
}
