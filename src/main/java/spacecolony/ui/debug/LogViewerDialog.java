package spacecolony.ui.debug;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import spacecolony.debug.RingBufferHandler;

/** Non-modal window hosting {@link LogViewerPanel}, so it can tail while the game runs. */
public final class LogViewerDialog {
    private LogViewerDialog() {}

    public static void show(Component parent, RingBufferHandler buffer) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), "Log viewer",
            JDialog.ModalityType.MODELESS);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        d.setContentPane(new LogViewerPanel(buffer));
        d.setPreferredSize(new Dimension(900, 500));
        d.pack();
        d.setLocationRelativeTo(parent);
        d.setVisible(true);
    }
}
