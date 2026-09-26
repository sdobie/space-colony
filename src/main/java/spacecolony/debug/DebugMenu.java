package spacecolony.debug;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;

/** "Debug" menu, mounted on the menu bar only while debug mode is on (design §4.9). */
public final class DebugMenu extends JMenu {
    static final Level[] LEVELS = { Level.FINE, Level.INFO, Level.WARNING };

    private final JMenu logLevel = new JMenu("Log level");

    DebugMenu(DebugController debug) {
        super("Debug");
        DebugActions a = debug.actions();
        add(item(a.step, DebugController.STEP_KEY));
        add(item(a.runN, KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK)));
        add(new JMenuItem(a.triggerEvent));
        addSeparator();
        add(new JMenuItem(a.dumpWorld));
        add(new JMenuItem(a.determinism));
        addSeparator();
        add(item(a.inspector, KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK)));
        add(item(a.logViewer, KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK)));

        ButtonGroup group = new ButtonGroup();
        DebugLogging.Installed inst = DebugLogging.current();
        Level current = inst == null || inst.level() == null ? Level.INFO : inst.level();
        for (Level l : LEVELS) {
            JRadioButtonMenuItem r = new JRadioButtonMenuItem(l.getName(), l.equals(current));
            r.addActionListener(e -> {
                DebugLogging.Installed i = DebugLogging.current();
                if (i != null) i.setLevel(l);
            });
            group.add(r);
            logLevel.add(r);
        }
        add(logLevel);

        JCheckBoxMenuItem overlays = new JCheckBoxMenuItem("Map overlays", debug.mapOverlaysToggle());
        overlays.addActionListener(e -> debug.setMapOverlays(overlays.isSelected()));
        add(overlays);
        addSeparator();
        add(new JMenuItem(a.throwTest));
    }

    JMenu logLevelMenu() { return logLevel; }

    private static JMenuItem item(javax.swing.Action a, KeyStroke key) {
        JMenuItem i = new JMenuItem(a);
        i.setAccelerator(key);
        return i;
    }
}
