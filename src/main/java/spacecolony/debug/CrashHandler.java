package spacecolony.debug;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spec §8.2: log every uncaught exception; outside debug mode, show one "Something went
 * wrong" dialog at a time (the overlay banner covers debug mode).
 */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    /** Shows the crash to the player; blocks until they dismiss it. */
    @FunctionalInterface
    public interface Reporter {
        void report(Throwable t) throws Exception;
    }

    private static final Logger LOG = Logger.getLogger("spacecolony.crash");

    private final ExceptionLog log;
    private final BooleanSupplier debugOn;
    private final Reporter reporter;
    private final AtomicBoolean reporting = new AtomicBoolean();

    public CrashHandler(ExceptionLog log, BooleanSupplier debugOn, Reporter reporter) {
        this.log = log;
        this.debugOn = debugOn;
        this.reporter = reporter;
    }

    @Override public void uncaughtException(Thread t, Throwable e) {
        LOG.log(Level.SEVERE, "Uncaught on " + t.getName(), e);
        log.add(t, e);
        if (debugOn.getAsBoolean()) return;
        // While a crash dialog is open, further exceptions are only logged, so a failing
        // paintComponent can't stack dialogs.
        if (!reporting.compareAndSet(false, true)) return;
        try {
            reporter.report(e);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Crash reporter failed", ex);
        } finally {
            reporting.set(false);
        }
    }

    /** For SwingWorker.done() bodies that catch an unexpected ExecutionException. */
    public void report(Throwable e) { uncaughtException(Thread.currentThread(), e); }

    /** Process-wide instance installed by SpaceColonyApp; null in tests that don't install one. */
    private static volatile CrashHandler installed;

    public static void install(CrashHandler h) {
        installed = h;
        Thread.setDefaultUncaughtExceptionHandler(h);
    }

    public static void reportIfInstalled(Throwable e) {
        CrashHandler h = installed;
        if (h != null) h.report(e);
        else LOG.log(Level.SEVERE, "Unhandled", e);
    }
}
