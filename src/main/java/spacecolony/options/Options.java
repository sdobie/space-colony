package spacecolony.options;

import java.util.List;
import java.util.logging.Level;
import spacecolony.engine.Speed;

/**
 * Player options (Plan 6 §4.1), persisted by {@link OptionsStore}. The allowed values are the
 * lists below, shared by the Options dialog's combos and the store's validation.
 *
 * @param uiScalePercent 0 means automatic (the toolkit's own scale)
 * @param autosaveMinutes 0 means timed autosave is off
 */
public record Options(Speed startSpeed, int autosaveMinutes, boolean confirmQuit,
                      boolean showSplash, boolean startMaximized, int uiScalePercent,
                      boolean startInDebug, Level logLevel, boolean tutorialCompleted) {
    public static final List<Speed> START_SPEEDS = List.of(Speed.PAUSED, Speed.X1);
    public static final List<Integer> AUTOSAVE_MINUTES = List.of(0, 5, 10, 15, 30);
    public static final List<Integer> UI_SCALES = List.of(0, 100, 125, 150, 200);
    public static final List<Level> LOG_LEVELS = List.of(Level.FINE, Level.INFO, Level.WARNING);

    public static final Options DEFAULTS =
        new Options(Speed.PAUSED, 10, true, true, false, 0, false, Level.INFO, false);

    public Options withTutorialCompleted(boolean done) {
        return new Options(startSpeed, autosaveMinutes, confirmQuit, showSplash, startMaximized,
            uiScalePercent, startInDebug, logLevel, done);
    }

    /** Every option back to its default except tutorial progress. */
    public Options restoredDefaults() {
        return DEFAULTS.withTutorialCompleted(tutorialCompleted);
    }
}
