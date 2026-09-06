package spacecolony.engine;

/** Sealed event hierarchy emitted by the Engine to UI listeners. */
public sealed interface EngineEvent
    permits EngineEvent.WorldChanged,
            EngineEvent.WorldReplaced,
            EngineEvent.SelectionChanged,
            EngineEvent.SpeedChanged,
            EngineEvent.ViewChanged {

    /** Emitted after each successful tick advance. */
    record WorldChanged(long tick) implements EngineEvent {}

    /**
     * Emitted when {@link Engine#reset(spacecolony.sim.World)} swaps in a fresh World
     * (Load or New). Listeners must do a full rebind — clear cached state derived
     * from the previous World and re-read everything from the new one.
     */
    record WorldReplaced(long tick) implements EngineEvent {}

    record SelectionChanged(Selection selection) implements EngineEvent {}

    record SpeedChanged(Speed speed) implements EngineEvent {}

    /** Center-pane view changed (e.g., system map ↔ body view). */
    record ViewChanged(View view) implements EngineEvent {
        public enum View { SYSTEM_MAP, BODY_VIEW }
    }
}
