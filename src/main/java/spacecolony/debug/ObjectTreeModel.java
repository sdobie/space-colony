package spacecolony.debug;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import spacecolony.sim.ResourceYieldSampler;

/**
 * Reflective tree over an object graph for the debug inspector (spec §8). Covers private
 * and final fields, records, collections, maps and arrays. Children are computed lazily
 * when a node is first expanded; {@link #refresh()} drops them so live values re-read.
 */
public final class ObjectTreeModel implements TreeModel {
    public static final int MAX_DEPTH = 12;
    public static final int MAX_CHILDREN = 500;

    /** One row: a name, the value it holds, and (for fields) where to write it back. */
    public final class Node {
        private final String name;
        private final Object value;
        private final Field field;
        private final Object owner;
        private final List<String> path;
        private final Set<Object> ancestors;
        private final boolean cycle;
        private List<Node> children;

        Node(String name, Object value, Field field, Object owner,
             List<String> path, Set<Object> ancestors) {
            this.name = name;
            this.value = value;
                this.field = field;
            this.owner = owner;
            this.path = path;
            this.ancestors = ancestors;
            this.cycle = value != null && !isLeafValue(value) && ancestors.contains(value);
        }

        public String name() { return name; }
        public Object value() { return value; }
        /** Labels from the root down to this node; stable across refreshes. */
        public List<String> path() { return path; }
        public String pathString() { return String.join(".", path.subList(1, path.size())); }

        public boolean isEditableLeaf() {
            if (field == null || Modifier.isFinal(field.getModifiers())) return false;
            return isEditableType(field.getType());
        }

        /** Enum constants when this is an enum-typed field, else null. */
        public Object[] enumConstants() {
            return field != null && field.getType().isEnum() ? field.getType().getEnumConstants() : null;
        }

        public String valueText() {
            if (value == null) return "null";
            if (value instanceof ResourceYieldSampler) return "<sampler>";
            if (value instanceof String s) return "\"" + s + "\"";
            if (isLeafValue(value)) return String.valueOf(value);
            return typeName(value);
        }

        public String label() {
            if (cycle) return name + " ↺ " + typeName(value) + "@" + Integer.toHexString(System.identityHashCode(value));
            if (value != null && !isLeafValue(value)) return name + ": " + typeName(value) + sizeSuffix(value);
            return name + ": " + valueText();
        }

        boolean isLeaf() {
            return value == null || cycle || isLeafValue(value) || path.size() > MAX_DEPTH;
        }

        List<Node> children() {
            if (children == null) children = isLeaf() ? List.of() : computeChildren(this);
            return children;
        }

        @Override public String toString() { return label(); }
    }

    private final List<TreeModelListener> listeners = new ArrayList<>();
    private Node liveRoot;
    private final Object rootValue;
    private final String rootName;

    public ObjectTreeModel(Object target, String rootName) {
        this.rootValue = target;
        this.rootName = rootName;
        this.liveRoot = newRoot();
    }

    private Node newRoot() {
        return new Node(rootName, rootValue, null, null, List.of(rootName), Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    public Node root() { return liveRoot; }

    /** Finds a direct child by name, or null. */
    public Node child(Node parent, String name) {
        for (Node c : parent.children()) if (c.name.equals(name)) return c;
        return null;
    }

    /** Finds the node at {@code labelPath} (from {@link Node#path()}) in the current tree, or null. */
    public Node find(List<String> labelPath) {
        Node n = liveRoot;
        if (labelPath.isEmpty() || !labelPath.get(0).equals(n.name)) return null;
        for (int i = 1; i < labelPath.size() && n != null; i++) n = child(n, labelPath.get(i));
        return n;
    }

    /** TreePath of {@code n} in the current tree (for selection/expansion). */
    public TreePath treePath(Node n) {
        List<Object> nodes = new ArrayList<>();
        Node cur = liveRoot;
        nodes.add(cur);
        for (int i = 1; i < n.path.size(); i++) {
            cur = child(cur, n.path.get(i));
            if (cur == null) return null;
            nodes.add(cur);
        }
        return new TreePath(nodes.toArray());
    }

    /**
     * Parse {@code text} by the field's type and write it into the live object.
     *
     * @throws IllegalArgumentException when the node isn't an editable field, or {@code text}
     *         doesn't parse ({@link NumberFormatException} for numbers)
     */
    public void setValue(Node node, String text) {
        if (!node.isEditableLeaf()) throw new IllegalArgumentException(node.name + " is not editable");
        Object v = parse(node.field.getType(), text);
        try {
            node.field.set(node.owner, v);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot write " + node.name + ": " + e.getMessage(), e);
        }
    }

    /** Drop cached children so values re-read from the live objects. */
    public void refresh() {
        liveRoot = newRoot();
        TreeModelEvent ev = new TreeModelEvent(this, new Object[] { liveRoot });
        for (TreeModelListener l : new ArrayList<>(listeners)) l.treeStructureChanged(ev);
    }

    // ----- TreeModel -----

    @Override public Object getRoot() { return liveRoot; }
    @Override public Object getChild(Object parent, int index) { return ((Node) parent).children().get(index); }
    @Override public int getChildCount(Object parent) { return ((Node) parent).children().size(); }
    @Override public boolean isLeaf(Object node) { return ((Node) node).isLeaf(); }
    @Override public void valueForPathChanged(TreePath path, Object newValue) {}
    @Override public int getIndexOfChild(Object parent, Object child) { return ((Node) parent).children().indexOf(child); }
    @Override public void addTreeModelListener(TreeModelListener l) { listeners.add(l); }
    @Override public void removeTreeModelListener(TreeModelListener l) { listeners.remove(l); }

    // ----- reflection -----

    private List<Node> computeChildren(Node parent) {
        Object v = parent.value;
        Set<Object> anc = Collections.newSetFromMap(new IdentityHashMap<>());
        anc.addAll(parent.ancestors);
        anc.add(v);
        List<Node> out = new ArrayList<>();
        Class<?> c = v.getClass();
        if (c.isArray()) {
            int n = Array.getLength(v);
            for (int i = 0; i < Math.min(n, MAX_CHILDREN); i++) {
                out.add(node(parent, "[" + i + "]", Array.get(v, i), null, null, anc));
            }
            more(parent, out, n, anc);
        } else if (v instanceof Collection<?> coll) {
            int i = 0;
            for (Object o : coll) {
                if (i >= MAX_CHILDREN) break;
                out.add(node(parent, "[" + i++ + "]", o, null, null, anc));
            }
            more(parent, out, coll.size(), anc);
        } else if (v instanceof Map<?, ?> map) {
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (i++ >= MAX_CHILDREN) break;
                out.add(node(parent, "[" + e.getKey() + "]", e.getValue(), null, null, anc));
            }
            more(parent, out, map.size(), anc);
        } else if (c.isRecord()) {
            for (RecordComponent rc : c.getRecordComponents()) {
                Object cv;
                try {
                    rc.getAccessor().trySetAccessible();
                    cv = rc.getAccessor().invoke(v);
                } catch (ReflectiveOperationException | RuntimeException ex) {
                    out.add(node(parent, rc.getName(), "<inaccessible>", null, null, anc));
                    continue;
                }
                out.add(node(parent, rc.getName(), cv, null, null, anc));
            }
        } else {
            for (Field f : instanceFields(c)) {
                if (!f.trySetAccessible()) {
                    out.add(node(parent, f.getName(), "<inaccessible>", null, null, anc));
                    continue;
                }
                Object fv;
                try { fv = f.get(v); }
                catch (IllegalAccessException ex) { fv = "<inaccessible>"; }
                out.add(node(parent, f.getName(), fv, f, v, anc));
            }
        }
        return List.copyOf(out);
    }

    private Node node(Node parent, String name, Object value, Field f, Object owner, Set<Object> anc) {
        List<String> p = new ArrayList<>(parent.path);
        p.add(name);
        return new Node(name, value, f, owner, List.copyOf(p), anc);
    }

    private void more(Node parent, List<Node> out, int total, Set<Object> anc) {
        if (total > MAX_CHILDREN) out.add(node(parent, "… " + (total - MAX_CHILDREN) + " more", null, null, null, anc));
    }

    private static List<Field> instanceFields(Class<?> c) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) chain.add(0, k);
        List<Field> out = new ArrayList<>();
        for (Class<?> k : chain) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                out.add(f);
            }
        }
        return out;
    }

    private static boolean isLeafValue(Object v) {
        return v instanceof Number || v instanceof Boolean || v instanceof Character
            || v instanceof String || v instanceof Enum<?> || v instanceof ResourceYieldSampler;
    }

    private static boolean isEditableType(Class<?> t) {
        return t.isPrimitive() || t == String.class || t.isEnum()
            || t == Integer.class || t == Long.class || t == Double.class || t == Float.class
            || t == Short.class || t == Byte.class || t == Boolean.class || t == Character.class;
    }

    private static Object parse(Class<?> t, String text) {
        String s = text.trim();
        if (t == int.class || t == Integer.class) return Integer.parseInt(s);
        if (t == long.class || t == Long.class) return Long.parseLong(s);
        if (t == double.class || t == Double.class) return Double.parseDouble(s);
        if (t == float.class || t == Float.class) return Float.parseFloat(s);
        if (t == short.class || t == Short.class) return Short.parseShort(s);
        if (t == byte.class || t == Byte.class) return Byte.parseByte(s);
        if (t == boolean.class || t == Boolean.class) {
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
            throw new IllegalArgumentException("Not a boolean: " + text);
        }
        if (t == char.class || t == Character.class) {
            if (text.length() != 1) throw new IllegalArgumentException("Not a single character: " + text);
            return text.charAt(0);
        }
        if (t == String.class) return text;
        if (t.isEnum()) {
            for (Object c : t.getEnumConstants()) if (((Enum<?>) c).name().equals(s)) return c;
            throw new IllegalArgumentException("Not a " + t.getSimpleName() + " constant: " + text);
        }
        throw new IllegalArgumentException("Cannot edit " + t.getName());
    }

    private static String typeName(Object v) { return v.getClass().getSimpleName(); }

    private static String sizeSuffix(Object v) {
        if (v.getClass().isArray()) return " [" + Array.getLength(v) + "]";
        if (v instanceof Collection<?> c) return " [" + c.size() + "]";
        if (v instanceof Map<?, ?> m) return " {" + m.size() + "}";
        return "";
    }
}
