package spacecolony.engine;

/** Subscriber for engine events. UI panels implement this. */
@FunctionalInterface
public interface EngineListener {
    void onEvent(EngineEvent event);
}
