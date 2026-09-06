package spacecolony.engine;

import javax.swing.SwingUtilities;

/** EDT assertion helpers. Active only when assertions are enabled (-ea). */
public final class EdtGuard {
    private EdtGuard() {}

    public static void assertEdt() {
        assert SwingUtilities.isEventDispatchThread()
            : "Must be called on EDT, was on " + Thread.currentThread().getName();
    }

    public static void assertNotEdt() {
        assert !SwingUtilities.isEventDispatchThread() : "Must NOT be called on EDT";
    }
}
