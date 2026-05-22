package spacecolony.engine;

import javax.swing.Timer;

/**
 * Drives {@link Engine#tick()} from a Swing Timer at the current {@link Speed}'s interval.
 * Speed changes reconfigure the timer; PAUSED stops it. Must run on the EDT.
 */
public class GameLoop {
    private final Engine engine;
    private final Timer timer;
    private final EngineListener listener = this::onEvent;

    public GameLoop(Engine engine) {
        this.engine = engine;
        // Start with a 1s placeholder; immediately reconfigured by applySpeed.
        this.timer = new Timer(1000, e -> engine.tick());
        timer.setRepeats(true);
        engine.addListener(listener);
        applySpeed(engine.speed());
    }

    private void onEvent(EngineEvent event) {
        if (event instanceof EngineEvent.SpeedChanged sc) applySpeed(sc.speed());
    }

    private void applySpeed(Speed s) {
        if (s.isPaused()) {
            timer.stop();
        } else {
            timer.setDelay(s.millisPerTick());
            timer.setInitialDelay(s.millisPerTick());
            if (!timer.isRunning()) timer.start();
        }
    }

    /** Stop the timer and detach from the engine. */
    public void dispose() {
        timer.stop();
        engine.removeListener(listener);
    }
}
