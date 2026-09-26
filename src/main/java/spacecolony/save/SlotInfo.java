package spacecolony.save;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One listed save. {@code name} is the slot name ({@code _autosave} for the unnamed autosave);
 * {@code tick}, {@code seed} and {@code credits} are −1 unless {@code status} is {@code OK}.
 */
public record SlotInfo(String name, Path path, boolean autosave, Instant modified,
                       Status status, int schemaVersion, long tick, long seed, long credits) {
    public enum Status { OK, OTHER_SCHEMA, UNREADABLE }
}
