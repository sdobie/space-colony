package spacecolony.debug;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflects an object graph into a tree for the debug object inspector (spec §8). Every
 * declared instance field is included, private ones too. Collections, maps and arrays are
 * expanded element by element; JDK value types (and anything else under {@code java.}) are
 * shown as leaves via {@code toString}.
 *
 * <p>Leaves holding a primitive, boxed primitive, String or enum can be edited in place via
 * {@link #set(Node, String)} when they sit in a non-final field, a List slot or a Map value.
 */
public final class ObjectInspector {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_CHILDREN = 500;

    private ObjectInspector() {}

    /** Applies a parsed value to wherever a node's value lives. */
    @FunctionalInterface
    private interface Setter {
        void set(Object value) throws ReflectiveOperationException;
    }

    /** One row of the inspector tree. Immutable snapshot; re-inspect after edits. */
    public static final class Node {
        private final String name;
        private final Class<?> type;
        private final Object value;
        private final String display;
        private final List<Node> children;
        private final Setter setter;

        Node(String name, Class<?> type, Object value, String display, List<Node> children, Setter setter) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.display = display;
            this.children = children;
            this.setter = setter;
        }

        public String name() { return name; }
        public Class<?> type() { return type; }
        public Object value() { return value; }
        public List<Node> children() { return children; }
        public boolean isEditable() { return setter != null; }

        /** Finds a direct child by name, or null. */
        public Node child(String childName) {
            for (Node c : children) if (c.name.equals(childName)) return c;
            return null;
        }

        @Override public String toString() { return display; }
    }

    public static Node inspect(String rootName, Object root) {
        Class<?> t = root == null ? Object.class : root.getClass();
        return build(rootName, t, root, null, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /**
     * Parse {@code text} as the node's type and write it back into the live object.
     *
     * @throws IllegalStateException if the node is not editable
     * @throws IllegalArgumentException if {@code text} does not parse as the node's type
     */
    public static void set(Node node, String text) {
        if (!node.isEditable()) throw new IllegalStateException(node.name + " is not editable");
        Object parsed = parse(node.type, text);
        try {
            node.setter.set(parsed);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set " + node.name + ": " + e.getMessage(), e);
        }
    }

    private static Node build(String name, Class<?> declared, Object v, Setter setter,
                              int depth, Set<Object> ancestors) {
        if (v == null) {
            return new Node(name, declared, null, label(name, declared) + " = null", List.of(), null);
        }
        Class<?> c = v.getClass();
        if (isLeaf(c)) {
            Class<?> type = declared.isPrimitive() ? declared : c;
            Setter s = isEditableType(type) ? setter : null;
            return new Node(name, type, v, label(name, type) + " = " + quote(v), List.of(), s);
        }
        if (ancestors.contains(v)) {
            return new Node(name, c, v, label(name, c) + " (cycle)", List.of(), null);
        }
        if (depth >= MAX_DEPTH) {
            return new Node(name, c, v, label(name, c) + " …", List.of(), null);
        }
        ancestors.add(v);
        try {
            List<Node> kids = new ArrayList<>();
            String suffix = "";
            if (c.isArray()) {
                int n = Array.getLength(v);
                suffix = " [" + n + "]";
                Class<?> ct = c.getComponentType();
                for (int i = 0; i < Math.min(n, MAX_CHILDREN); i++) {
                    final int idx = i;
                    kids.add(build("[" + i + "]", ct, Array.get(v, i),
                        x -> Array.set(v, idx, x), depth + 1, ancestors));
                }
            } else if (v instanceof List<?> list) {
                suffix = " [" + list.size() + "]";
                @SuppressWarnings("unchecked") List<Object> ml = (List<Object>) list;
                for (int i = 0; i < Math.min(list.size(), MAX_CHILDREN); i++) {
                    final int idx = i;
                    kids.add(build("[" + i + "]", Object.class, list.get(i),
                        x -> ml.set(idx, x), depth + 1, ancestors));
                }
            } else if (v instanceof Collection<?> coll) {
                suffix = " [" + coll.size() + "]";
                int i = 0;
                for (Object o : coll) {
                    if (i >= MAX_CHILDREN) break;
                    kids.add(build("[" + i++ + "]", Object.class, o, null, depth + 1, ancestors));
                }
            } else if (v instanceof Map<?, ?> map) {
                suffix = " {" + map.size() + "}";
                @SuppressWarnings("unchecked") Map<Object, Object> mm = (Map<Object, Object>) map;
                int i = 0;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (i++ >= MAX_CHILDREN) break;
                    Object key = e.getKey();
                    kids.add(build(String.valueOf(key), Object.class, e.getValue(),
                        x -> mm.put(key, x), depth + 1, ancestors));
                }
            } else {
                for (Field f : instanceFields(c)) {
                    Object fv;
                    try {
                        f.setAccessible(true);
                        fv = f.get(v);
                    } catch (ReflectiveOperationException | RuntimeException ex) {
                        kids.add(new Node(f.getName(), f.getType(), null,
                            label(f.getName(), f.getType()) + " <inaccessible>", List.of(), null));
                        continue;
                    }
                    Setter fs = Modifier.isFinal(f.getModifiers()) ? null : x -> f.set(v, x);
                    kids.add(build(f.getName(), f.getType(), fv, fs, depth + 1, ancestors));
                }
            }
            return new Node(name, c, v, label(name, c) + suffix, List.copyOf(kids), null);
        } finally {
            ancestors.remove(v);
        }
    }

    /** Declared instance fields, superclass fields first, stopping at JDK classes. */
    private static List<Field> instanceFields(Class<?> c) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> k = c; k != null && !isJdk(k); k = k.getSuperclass()) chain.add(0, k);
        List<Field> out = new ArrayList<>();
        for (Class<?> k : chain) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                out.add(f);
            }
        }
        return out;
    }

    private static boolean isLeaf(Class<?> c) {
        if (c.isArray()) return false;
        if (c.isEnum() || (c.getSuperclass() != null && c.getSuperclass().isEnum())) return true;
        if (Collection.class.isAssignableFrom(c) || Map.class.isAssignableFrom(c)) return false;
        return isJdk(c);
    }

    private static boolean isJdk(Class<?> c) {
        String n = c.getName();
        return c.isPrimitive() || n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jdk.");
    }

    static boolean isEditableType(Class<?> t) {
        return t.isPrimitive() || t == String.class || t.isEnum()
            || t == Integer.class || t == Long.class || t == Double.class || t == Float.class
            || t == Short.class || t == Byte.class || t == Boolean.class || t == Character.class;
    }

    static Object parse(Class<?> t, String text) {
        String s = text.trim();
        try {
            if (t == int.class || t == Integer.class) return Integer.parseInt(s);
            if (t == long.class || t == Long.class) return Long.parseLong(s);
            if (t == double.class || t == Double.class) return Double.parseDouble(s);
            if (t == float.class || t == Float.class) return Float.parseFloat(s);
            if (t == short.class || t == Short.class) return Short.parseShort(s);
            if (t == byte.class || t == Byte.class) return Byte.parseByte(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a valid " + t.getSimpleName() + ": " + text, e);
        }
        if (t == boolean.class || t == Boolean.class) {
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
            throw new IllegalArgumentException("Not a valid boolean: " + text);
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
        throw new IllegalArgumentException("Cannot edit values of type " + t.getName());
    }

    private static String label(String name, Class<?> type) {
        return name + ": " + type.getSimpleName();
    }

    private static String quote(Object v) {
        return v instanceof String s ? "\"" + s + "\"" : String.valueOf(v);
    }
}
