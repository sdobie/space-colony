package spacecolony.engine;

/** Sealed event hierarchy emitted by the Engine to UI listeners. */
public sealed interface EngineEvent
    permits EngineEvent.WorldChanged,
            EngineEvent.SelectionChanged,
            EngineEvent.SpeedChanged,
            EngineEvent.ViewChanged {

    /** Emitted after each successful tick advance. */
    record WorldChanged(long tick) implements EngineEvent {}

    record SelectionChanged(Selection selection) implements EngineEvent {}

    record SpeedChanged(Speed speed) implements EngineEvent {}

    /** Center-pane view changed (e.g., system map ↔ body view). */
    record ViewChanged(View view) implements EngineEvent {
        public enum View { SYSTEM_MAP, BODY_VIEW }
    }
}
