package spacecolony.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import spacecolony.sim.World;

/**
 * Named save slots in one directory (spec §9). Slot {@code foo} lives in {@code foo.json};
 * autosave on quit writes {@code foo.autosave.json}, or {@code _autosave.json} for a game
 * that has never been saved to a slot.
 *
 * <p>Not EDT-guarded, like {@link SaveFile}: {@link #save}, {@link #autosave} and
 * {@link #load} should run in a {@code SwingWorker}.
 */
public final class SaveSlots {
    /** System property that overrides the saves directory (used by the play-test harness). */
    public static final String DIR_PROPERTY = "spacecolony.savesDir";
    public static final String AUTOSAVE_SUFFIX = ".autosave";
    public static final String UNNAMED_AUTOSAVE = "_autosave";
    private static final String EXT = ".json";
    private static final int MAX_NAME_LENGTH = 64;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _.'()-]*");

    /** One save file in the directory. {@code name} is the filename without {@code .json}. */
    public record Slot(String name, Path file, Instant modified) {
        public boolean isAutosave() {
            return name.equals(UNNAMED_AUTOSAVE) || name.endsWith(AUTOSAVE_SUFFIX);
        }

        /**
         * The named slot a game loaded from this file belongs to, so its next autosave lands
         * beside it. Null for the unnamed autosave, or for a file whose name isn't a valid slot
         * name (e.g. copied in by hand); those games autosave to {@code _autosave.json}.
         */
        public String owningSlot() {
            if (name.equals(UNNAMED_AUTOSAVE)) return null;
            String base = name.endsWith(AUTOSAVE_SUFFIX)
                ? name.substring(0, name.length() - AUTOSAVE_SUFFIX.length())
                : name;
            try {
                return validateName(base).equals(base) ? base : null;
            } catch (IllegalArgumentException invalid) {
                return null;
            }
        }
    }

    private final Path dir;

    public SaveSlots(Path dir) { this.dir = dir; }

    /** {@code ~/.space-colony/saves}, or the directory named by {@value #DIR_PROPERTY}. */
    public static SaveSlots atDefaultLocation() {
        String override = System.getProperty(DIR_PROPERTY);
        if (override != null && !override.isBlank()) return new SaveSlots(Paths.get(override));
        return new SaveSlots(Paths.get(System.getProperty("user.home"), ".space-colony", "saves"));
    }

    public Path dir() { return dir; }

    /**
     * Normalizes a player-typed slot name: trims it and drops a trailing {@code .json}.
     *
     * @throws IllegalArgumentException with a message fit for a dialog if the name is unusable
     */
    public static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(EXT)) name = name.substring(0, name.length() - EXT.length()).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Enter a name for the save.");
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Save names can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (!NAME.matcher(name).matches() || name.endsWith(".")) {
            throw new IllegalArgumentException(
                "Save names must start with a letter or digit and use only letters, digits, "
                    + "spaces and _ . ' ( ) -");
        }
        if (name.toLowerCase(Locale.ROOT).endsWith(AUTOSAVE_SUFFIX)) {
            throw new IllegalArgumentException("Names ending in \"" + AUTOSAVE_SUFFIX + "\" are reserved for autosaves.");
        }
        return name;
    }

    /** Every save in the directory, autosaves included, newest first. Empty if the directory is missing. */
    public List<Slot> list() throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        List<Slot> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(EXT) || fn.length() == EXT.length() || !Files.isRegularFile(p)) continue;
                out.add(new Slot(fn.substring(0, fn.length() - EXT.length()), p,
                    Files.getLastModifiedTime(p).toInstant()));
            }
        }
        out.sort(Comparator.comparing(Slot::modified).reversed().thenComparing(Slot::name));
        return out;
    }

    /** File for a named slot. */
    public Path fileFor(String slotName) {
        return dir.resolve(validateName(slotName) + EXT);
    }

    /** Autosave file for a named slot, or {@code _autosave.json} when {@code slotName} is null. */
    public Path autosaveFileFor(String slotName) {
        if (slotName == null) return dir.resolve(UNNAMED_AUTOSAVE + EXT);
        return dir.resolve(validateName(slotName) + AUTOSAVE_SUFFIX + EXT);
    }

    public boolean exists(String slotName) {
        return Files.exists(fileFor(slotName));
    }

    public void save(World w, String slotName) throws IOException {
        SaveFile.save(w, fileFor(slotName));
    }

    /** Writes the autosave for {@code slotName} (null = the game has no slot yet). */
    public void autosave(World w, String slotName) throws IOException {
        SaveFile.save(w, autosaveFileFor(slotName));
    }

    public World load(Slot slot) throws IOException, IncompatibleSaveException {
        return SaveFile.load(slot.file());
    }

    public void delete(Slot slot) throws IOException {
        Files.deleteIfExists(slot.file());
    }
}
