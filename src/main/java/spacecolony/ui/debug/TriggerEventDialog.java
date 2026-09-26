package spacecolony.ui.debug;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import spacecolony.debug.DebugController;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.EventKind;
import spacecolony.sim.Site;
import spacecolony.sim.phases.EventPhase;

/** "Trigger event…" picker: choose a body and an event kind, then force-roll it now. */
public final class TriggerEventDialog {
    private TriggerEventDialog() {}

    public static void show(Component parent, DebugController debug) {
        Engine engine = debug.engine();
        JComboBox<String> bodies = new JComboBox<>();
        for (Body b : engine.world().bodies) bodies.addItem(b.id);
        String preselect = defaultBodyId(engine);
        if (preselect != null) bodies.setSelectedItem(preselect);
        JComboBox<EventKind> kinds = new JComboBox<>(EventPhase.randomKinds());

        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Body:"));  form.add(bodies);
        form.add(new JLabel("Event:")); form.add(kinds);
        int r = JOptionPane.showConfirmDialog(parent, form, "Trigger event",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        debug.triggerEvent((String) bodies.getSelectedItem(), (EventKind) kinds.getSelectedItem());
    }

    /** The selected body, or the body of the selected site; null otherwise. */
    static String defaultBodyId(Engine engine) {
        Selection sel = engine.selection();
        if (sel.kind() == Selection.Kind.BODY) return sel.id();
        if (sel.kind() == Selection.Kind.SITE) {
            Site s = engine.world().findSite(sel.id());
            return s == null ? null : s.bodyId;
        }
        return null;
    }
}
