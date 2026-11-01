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
import spacecolony.sim.MeteorSize;
import spacecolony.sim.Site;
import spacecolony.sim.phases.EventPhase;

/**
 * Debug "Trigger event…": pick a colony, an event kind and (for a meteor) its size, then force it
 * now (paused only). A solar flare is system-wide and needs no colony.
 */
public final class TriggerEventDialog {
    private TriggerEventDialog() {}

    /** Combo entry for a colony, labelled {@code Earth Hub (Earth)}. */
    record SiteChoice(Site site, Body body) {
        @Override public String toString() { return site.name + " (" + body.name + ")"; }
    }

    /** "Random" or a fixed meteor size. */
    record SizeChoice(MeteorSize size) {
        @Override public String toString() { return size == null ? "Random" : size.title(); }
    }

    /** Every colony, in world order. */
    static List<SiteChoice> siteChoices(Engine engine) {
        List<SiteChoice> out = new ArrayList<>();
        for (Body b : engine.world().bodies) for (Site s : b.sites) out.add(new SiteChoice(s, b));
        return out;
    }

    /** Force {@code kind} on the first colony of body {@code bodyId}; used headless by tests and drivers. */
    public static void trigger(Engine engine, String bodyId, EventKind kind) {
        engine.applyDebugEdit("trigger " + kind + " on " + bodyId,
            w -> EventPhase.applyForced(w, w.findBody(bodyId), kind,
                DeterministicRng.forStep(w.seed, w.tick, EventPhase.DEBUG_STEP_ID)));
    }

    /** The edit the dialog's OK performs: {@code kind} at colony {@code siteId}, a meteor of {@code size} (null: random). */
    public static void triggerAt(Engine engine, String siteId, EventKind kind, MeteorSize size) {
        engine.applyDebugEdit("trigger " + kind + (size == null ? "" : " " + size) + " at " + siteId,
            w -> EventPhase.applyForced(w, siteId == null ? null : w.findSite(siteId), kind, size,
                DeterministicRng.forStep(w.seed, w.tick, EventPhase.DEBUG_STEP_ID)));
    }

    public static void show(Component parent, Engine engine) {
        JComboBox<SiteChoice> sites = new JComboBox<>(siteChoices(engine).toArray(new SiteChoice[0]));
        JComboBox<EventKind> kinds = new JComboBox<>(EventPhase.randomKinds().toArray(new EventKind[0]));
        List<SizeChoice> sizeList = new ArrayList<>();
        sizeList.add(new SizeChoice(null));
        for (MeteorSize m : MeteorSize.values()) sizeList.add(new SizeChoice(m));
        JComboBox<SizeChoice> sizes = new JComboBox<>(sizeList.toArray(new SizeChoice[0]));
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Event:"));  form.add(kinds);
        form.add(new JLabel("Colony:")); form.add(sites);
        form.add(new JLabel("Size:"));   form.add(sizes);
        Runnable sync = () -> {
            EventKind k = (EventKind) kinds.getSelectedItem();
            sites.setEnabled(k != EventKind.SOLAR_FLARE);
            sizes.setEnabled(k == EventKind.METEOR_STRIKE);
        };
        kinds.addActionListener(e -> sync.run());
        sync.run();

        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        boolean paused = engine.speed().isPaused();
        boolean anyColony = sites.getItemCount() > 0;
        ok.setEnabled(paused);
        if (!paused) ok.setToolTipText("Pause the game to trigger events");
        JOptionPane pane = new JOptionPane(form, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION,
            null, new Object[] { ok, cancel }, ok);
        JDialog d = pane.createDialog(SwingUtilities.getWindowAncestor(parent) == null ? parent
            : SwingUtilities.getWindowAncestor(parent), "Trigger event");
        ok.addActionListener(e -> {
            EventKind k = (EventKind) kinds.getSelectedItem();
            SiteChoice sc = (SiteChoice) sites.getSelectedItem();
            if (k != EventKind.SOLAR_FLARE && (!anyColony || sc == null)) { d.dispose(); return; }
            SizeChoice size = (SizeChoice) sizes.getSelectedItem();
            triggerAt(engine, k == EventKind.SOLAR_FLARE ? null : sc.site().id, k,
                k == EventKind.METEOR_STRIKE && size != null ? size.size() : null);
            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());
        d.setVisible(true);
    }
}
