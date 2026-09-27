package spacecolony.debug;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.Body;
import spacecolony.sim.DeterministicRng;
import spacecolony.sim.EventKind;
import spacecolony.sim.phases.EventPhase;

/** Debug "Trigger event…": pick a body and a random-event kind, then force it now (paused only). */
public final class TriggerEventDialog {
    private TriggerEventDialog() {}

    /** Combo entry for a body, labelled {@code name (id)}. */
    record BodyChoice(Body body) {
        @Override public String toString() { return body.name + " (" + body.id + ")"; }
    }

    /** Bodies with sites first, then the rest, each group in world order. */
    static List<BodyChoice> bodyChoices(Engine engine) {
        List<BodyChoice> withSites = new ArrayList<>();
        List<BodyChoice> rest = new ArrayList<>();
        for (Body b : engine.world().bodies) (b.sites.isEmpty() ? rest : withSites).add(new BodyChoice(b));
        withSites.addAll(rest);
        return withSites;
    }

    /** The edit the dialog's OK performs; also used headless by tests. */
    public static void trigger(Engine engine, String bodyId, EventKind kind) {
        engine.applyDebugEdit("trigger " + kind + " on " + bodyId,
            w -> EventPhase.applyForced(w, w.findBody(bodyId), kind,
                DeterministicRng.forStep(w.seed, w.tick, EventPhase.DEBUG_STEP_ID)));
    }

    public static void show(Component parent, Engine engine) {
        JComboBox<BodyChoice> bodies = new JComboBox<>(bodyChoices(engine).toArray(new BodyChoice[0]));
        JComboBox<EventKind> kinds = new JComboBox<>(EventPhase.randomKinds().toArray(new EventKind[0]));
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Body:"));  form.add(bodies);
        form.add(new JLabel("Event:")); form.add(kinds);

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        boolean paused = engine.speed().isPaused();
        ok.setEnabled(paused);
        if (!paused) ok.setToolTipText("Pause the game to trigger events");
        JOptionPane pane = new JOptionPane(form, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
            null, new Object[] { ok, cancel }, ok);
        JDialog d = pane.createDialog(SwingUtilities.getWindowAncestor(parent) == null ? parent
            : SwingUtilities.getWindowAncestor(parent), "Trigger event");
        ok.addActionListener(e -> {
            BodyChoice bc = (BodyChoice) bodies.getSelectedItem();
            trigger(engine, bc.body().id, (EventKind) kinds.getSelectedItem());
            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());
        d.setVisible(true);
    }
}
