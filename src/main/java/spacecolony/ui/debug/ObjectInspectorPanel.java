package spacecolony.ui.debug;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import spacecolony.debug.DebugController;
import spacecolony.debug.ObjectInspector;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;

/**
 * Content of the object inspector: a reflected field tree of the selected body, site or
 * ship. Read-only until "Edit" is ticked; then a selected primitive/String/enum leaf can be
 * set and the change is re-injected through {@link Engine#applyDebugMutation}.
 */
public class ObjectInspectorPanel extends JPanel {
    private final Engine engine;
    private final Selection target;
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final JCheckBox editToggle = new JCheckBox("Edit");
    private final JTextField valueField = new JTextField(16);
    private final JButton setBtn = new JButton("Set");
    private final JLabel status = new JLabel(" ");

    public ObjectInspectorPanel(Engine engine, Selection target) {
        super(new BorderLayout(4, 4));
        this.engine = engine;
        this.target = target;

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> rebuild());
        top.add(refresh);
        top.add(editToggle);
        top.add(valueField);
        top.add(setBtn);

        editToggle.addActionListener(e -> syncEditControls());
        tree.addTreeSelectionListener(e -> syncEditControls());
        setBtn.addActionListener(e -> applyEdit());
        valueField.addActionListener(e -> applyEdit());

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        rebuild();
    }

    /** Re-reflect the live object (it may have changed, or the world may have been replaced). */
    public void rebuild() {
        Object obj = DebugController.resolve(engine, target);
        String rootName = target.kind().name().toLowerCase() + " " + target.id();
        DefaultMutableTreeNode root = toTree(ObjectInspector.inspect(rootName, obj));
        tree.setModel(new DefaultTreeModel(root));
        tree.expandRow(0);
        if (obj == null) status.setText(target.id() + " no longer exists in this world");
        syncEditControls();
    }

    private static DefaultMutableTreeNode toTree(ObjectInspector.Node n) {
        DefaultMutableTreeNode t = new DefaultMutableTreeNode(n);
        for (ObjectInspector.Node c : n.children()) t.add(toTree(c));
        return t;
    }

    private ObjectInspector.Node selectedNode() {
        TreePath p = tree.getSelectionPath();
        if (p == null) return null;
        return (ObjectInspector.Node) ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
    }

    private void syncEditControls() {
        ObjectInspector.Node n = selectedNode();
        boolean editable = editToggle.isSelected() && n != null && n.isEditable();
        valueField.setEnabled(editable);
        setBtn.setEnabled(editable);
        if (editable) valueField.setText(String.valueOf(n.value()));
    }

    private void applyEdit() {
        ObjectInspector.Node n = selectedNode();
        if (n == null || !editToggle.isSelected() || !n.isEditable()) return;
        String text = valueField.getText();
        try {
            engine.applyDebugMutation(w -> ObjectInspector.set(n, text));
            status.setText("Set " + n.name() + " = " + text);
            TreePath path = tree.getSelectionPath();
            rebuild();
            reselect(path);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            status.setText(ex.getMessage());
        }
    }

    /** Re-select the same row (by name path) after a rebuild so repeated edits are easy. */
    private void reselect(TreePath old) {
        if (old == null) return;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getModel().getRoot();
        Object[] names = old.getPath();
        for (int i = 1; i < names.length && node != null; i++) {
            String want = ((ObjectInspector.Node) ((DefaultMutableTreeNode) names[i]).getUserObject()).name();
            DefaultMutableTreeNode next = null;
            for (int c = 0; c < node.getChildCount(); c++) {
                DefaultMutableTreeNode k = (DefaultMutableTreeNode) node.getChildAt(c);
                if (((ObjectInspector.Node) k.getUserObject()).name().equals(want)) { next = k; break; }
            }
            node = next;
        }
        if (node != null) {
            TreePath p = new TreePath(node.getPath());
            tree.expandPath(p.getParentPath());
            tree.setSelectionPath(p);
        }
    }

    // Visible for tests.
    JTree tree() { return tree; }
    JCheckBox editToggle() { return editToggle; }
    JTextField valueField() { return valueField; }
    JButton setButton() { return setBtn; }
    String statusText() { return status.getText(); }
}
