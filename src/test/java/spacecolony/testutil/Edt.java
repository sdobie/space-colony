package spacecolony.testutil;

import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;

/**
 * Test helper for running code on the Event Dispatch Thread.
 *
 * <p>Engine mutations assert they are on the EDT (see {@code EdtGuard}), and JUnit runs
 * tests on a worker thread, so tests must hop onto the EDT explicitly.
 *
 * <p>Unlike a bare {@link SwingUtilities#invokeAndWait}, this rethrows whatever the body
 * threw — an {@code AssertionError} from a JUnit assertion stays an {@code AssertionError}
 * instead of arriving wrapped in an {@link InvocationTargetException}, so failure messages
 * stay readable.
 */
public final class Edt {
    private Edt() {}

    /** A body that may throw a checked exception. */
    @FunctionalInterface
    public interface Body {
        void run() throws Exception;
    }

    /** Run {@code body} on the EDT and wait for it, propagating any failure verbatim. */
    public static void run(Body body) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            body.run();
            return;
        }
        Throwable[] thrown = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                thrown[0] = t;
            }
        });
        switch (thrown[0]) {
            case null -> { }
            case Error e -> throw e;
            case Exception e -> throw e;
            default -> throw new IllegalStateException(thrown[0]);
        }
    }
}
