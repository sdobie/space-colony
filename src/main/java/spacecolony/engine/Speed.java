package spacecolony.engine;

/** Tick rate for the game loop. millisPerTick determines the Swing Timer interval. */
public enum Speed {
    PAUSED(0),
    X1(500),
    X4(125),
    X16(32);

    private final int millisPerTick;
    Speed(int millisPerTick) { this.millisPerTick = millisPerTick; }
    public int millisPerTick() { return millisPerTick; }
    public boolean isPaused() { return this == PAUSED; }
}
