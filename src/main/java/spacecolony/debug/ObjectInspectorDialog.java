package spacecolony.debug;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.EngineListener;

/**
 * Debug object inspector (design §4.4): a reflective tree of one object. Read-only until
 * "Edit" is on; then, while paused, double-clicking an editable leaf asks for a new value
 * and commits it through {@link Engine#applyDebugEdit}. Refreshes on WorldChanged keeping
 * expanded rows, and closes on WorldReplaced because its object belongs to the old world.
 */
public final class ObjectInspectorDialog extends JDialog {
    private final Engine engine;
    private final ObjectTreeModel model;
    private final JTree tree;
    private final JToggleButton edit = new JToggleButton("Edit");
    private final EngineListener listener = this::onEngineEvent;

    ObjectInspectorDialog(Window owner, Engine engine, Object target, String title) {
        super(owner, "Inspect " + title, ModalityType.MODELESS);
        this.engine = engine;
        this.model = new ObjectTreeModel(target, title);
        this.tree = new JTree(model);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        tree.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                TreePath p = tree.getPathForLocation(e.getX(), e.getY());
                if (p != null) editNode((ObjectTreeModel.Node) p.getLastPathComponent());
            }
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(edit);
        top.add(new JLabel("Double-click a field to edit it (paused only)."));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        setPreferredSize(new Dimension(520, 640));
        pack();
        setLocationRelativeTo(owner);

        engine.addListener(listener);
        syncEditToggle();
        tree.expandRow(0);
    }

    public static ObjectInspectorDialog inspect(Component parent, Engine engine, Object target, String title) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        ObjectInspectorDialog d = new ObjectInspectorDialog(owner, engine, target, title);
        d.setVisible(true);
        return d;
    }

    @Override public void dispose() {
        engine.removeListener(listener);
        super.dispose();
    }

    private void onEngineEvent(EngineEvent e) {
        if (e instanceof EngineEvent.WorldReplaced) dispose();
        else if (e instanceof EngineEvent.WorldChanged) refreshKeepingExpansion();
        else if (e instanceof EngineEvent.SpeedChanged) syncEditToggle();
    }

    private void syncEditToggle() {
        boolean paused = engine.speed().isPaused();
        edit.setEnabled(paused);
        edit.setToolTipText(paused ? null : "Pause to edit");
    }

    /** Re-read live values, then re-expand rows and restore selection by label path. */
    void refreshKeepingExpansion() {
        List<List<String>> expanded = new ArrayList<>();
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath p = tree.getPathForRow(i);
            if (tree.isExpanded(p)) expanded.add(((ObjectTreeModel.Node) p.getLastPathComponent()).path());
        }
        TreePath sel = tree.getSelectionPath();
        List<String> selPath = sel == null ? null : ((ObjectTreeModel.Node) sel.getLastPathComponent()).path();
        model.refresh();
        for (List<String> path : expanded) {
            ObjectTreeModel.Node n = model.find(path);
            if (n != null) tree.expandPath(model.treePath(n));
        }
        if (selPath != null) {
            ObjectTreeModel.Node n = model.find(selPath);
            if (n != null) tree.setSelectionPath(model.treePath(n));
        }
    }

    private void editNode(ObjectTreeModel.Node n) {
        if (!edit.isSelected() || !engine.speed().isPaused() || !n.isEditableLeaf()) return;
        String text;
        Object[] constants = n.enumConstants();
        if (constants != null) {
            JComboBox<Object> combo = new JComboBox<>(constants);
            combo.setSelectedItem(n.value());
            int r = JOptionPane.showConfirmDialog(this, combo, "Set " + n.pathString(),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return;
            text = String.valueOf(combo.getSelectedItem());
        } else {
            text = (String) JOptionPane.showInputDialog(this, n.pathString() + " =", "Set value",
                JOptionPane.PLAIN_MESSAGE, null, null, String.valueOf(n.value()));
            if (text == null) return;
        }
        commit(n, text);
    }

    /** Apply an edit through the engine; a parse failure shows an error and changes nothing. */
    boolean commit(ObjectTreeModel.Node n, String text) {
        try {
            engine.applyDebugEdit("inspector: " + n.pathString() + " = " + text, w -> model.setValue(n, text));
            return true;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot set " + n.name(), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // Visible for tests.
    ObjectTreeModel model() { return model; }
    JTree tree() { return tree; }
    JToggleButton editToggle() { return edit; }
}
