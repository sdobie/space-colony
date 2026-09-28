package spacecolony.ui;

import javax.swing.Timer;

/**
 * Timed autosave (Plan 6 §4.4): every N minutes of wall-clock time, asks the session to
 * autosave in the background. The session skips tutorials and games that haven't advanced.
 */
public final class AutosaveTimer {
    private final Timer timer;

    public AutosaveTimer(GameSession session) {
        this.timer = new Timer(60_000, e -> session.autosaveInBackground());
        timer.setRepeats(true);
    }

    /** 0 stops the timer; any other value restarts it at that interval. */
    public void setMinutes(int minutes) {
        timer.stop();
        if (minutes <= 0) return;
        int ms = minutes * 60_000;
        timer.setDelay(ms);
        timer.setInitialDelay(ms);
        timer.start();
    }

    public void stop() { timer.stop(); }

    boolean running() { return timer.isRunning(); }
    int delayMillis() { return timer.getDelay(); }
}
