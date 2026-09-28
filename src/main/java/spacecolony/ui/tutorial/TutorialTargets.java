package spacecolony.ui.tutorial;

import java.awt.Component;
import java.awt.Container;
import java.util.List;

/** Finds the component a tutorial step points at, by the name the panel gave it (Plan 6 §5.4). */
public final class TutorialTargets {
    private TutorialTargets() {}

    /** The first showing component under {@code root} named {@code key}, or null. */
    public static Component find(Container root, String key) {
        for (Component c : root.getComponents()) {
            if (key.equals(c.getName()) && c.isShowing()) return c;
            if (c instanceof Container inner) {
                Component r = find(inner, key);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** The first of {@code keys} that resolves, or null. */
    public static Component findFirst(Container root, List<String> keys) {
        for (String k : keys) {
            Component c = find(root, k);
            if (c != null) return c;
        }
        return null;
    }
}
