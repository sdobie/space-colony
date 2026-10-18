package spacecolony.engine;

/**
 * Tick rate for the game loop. millisPerTick determines the Swing Timer interval. One tick is
 * one game day, so 1× shows a day every three seconds and the faster speeds are multiples of it.
 */
public enum Speed {
    PAUSED(0),
    X1(3000),
    X4(750),
    X16(188);

    private final int millisPerTick;
    Speed(int millisPerTick) { this.millisPerTick = millisPerTick; }
    public int millisPerTick() { return millisPerTick; }
    public boolean isPaused() { return this == PAUSED; }
}
