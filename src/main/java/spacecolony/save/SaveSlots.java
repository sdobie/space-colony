package spacecolony.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Named save slots in one directory (spec §9, Plan 5 §5.1). Slot {@code foo} lives in
 * {@code foo.json} and autosaves to {@code foo.autosave.json}; a game never saved to a slot
 * autosaves to {@code _autosave.json}.
 *
 * <p>Not EDT-guarded, like {@link SaveFile}: {@link #list} parses every file, so callers run
 * it in a {@code SwingWorker}.
 */
public final class SaveSlots {
    /** System property that overrides the saves directory (used by the play-test harness). */
    public static final String DIR_PROPERTY = "spacecolony.savesDir";
    public static final String UNNAMED_AUTOSAVE = "_autosave";
    private static final String EXT = ".json";
    private static final String AUTOSAVE_EXT = ".autosave.json";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _-]{1,40}");
    private static final Logger LOG = Logger.getLogger("spacecolony.save");

    private final Path dir;

    public SaveSlots(Path dir) { this.dir = dir; }

    /** {@code ~/.space-colony/saves}, or the directory named by {@value #DIR_PROPERTY}. */
    public static SaveSlots defaultDir() {
        String override = System.getProperty(DIR_PROPERTY);
        if (override != null && !override.isBlank()) return new SaveSlots(Paths.get(override));
        return new SaveSlots(Paths.get(System.getProperty("user.home"), ".space-colony", "saves"));
    }

    public Path dir() { return dir; }

    /** Trims {@code raw} and returns why it can't be a slot name, or null when it can. */
    public static String validateName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) return "Name is required";
        if (name.length() > 40) return "At most 40 characters";
        if (!NAME.matcher(name).matches()) return "Letters, digits, space, _ and - only";
        if (name.startsWith("_")) return "Names starting with _ are reserved";
        return null;
    }

    /** Path of a named slot. */
    public Path slotPath(String name) {
        return dir.resolve(checked(name) + EXT);
    }

    /** Autosave path for a named slot, or {@code _autosave.json} when {@code nameOrNull} is null. */
    public Path autosavePath(String nameOrNull) {
        if (nameOrNull == null) return dir.resolve(UNNAMED_AUTOSAVE + EXT);
        return dir.resolve(checked(nameOrNull) + AUTOSAVE_EXT);
    }

    /**
     * The slot a file in this directory belongs to: {@code <n>.json} or {@code <n>.autosave.json}
     * with a valid {@code n} gives {@code n}. Anything else (the unnamed autosave, a file
     * elsewhere, a hand-named file) gives null.
     */
    public String slotOf(Path file) {
        Path abs = file.toAbsolutePath().normalize();
        if (!dir.toAbsolutePath().normalize().equals(abs.getParent())) return null;
        String fn = abs.getFileName().toString();
        String base;
        if (fn.endsWith(AUTOSAVE_EXT)) base = fn.substring(0, fn.length() - AUTOSAVE_EXT.length());
        else if (fn.endsWith(EXT)) base = fn.substring(0, fn.length() - EXT.length());
        else return null;
        return validateName(base) == null && base.equals(base.trim()) ? base : null;
    }

    /**
     * Every save in the directory, newest first. Files that don't parse or have another schema
     * version are still listed (so the player can delete them) with tick, seed and credits −1.
     */
    public List<SlotInfo> list() throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        List<SlotInfo> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String fn = p.getFileName().toString();
                if (!fn.endsWith(EXT) || fn.length() == EXT.length() || !Files.isRegularFile(p)) continue;
                out.add(info(p, fn));
            }
        }
        out.sort(Comparator.comparing(SlotInfo::modified).reversed().thenComparing(SlotInfo::name));
        return out;
    }

    private static SlotInfo info(Path p, String fn) throws IOException {
        boolean autosave = fn.endsWith(AUTOSAVE_EXT) || fn.equals(UNNAMED_AUTOSAVE + EXT);
        String name = fn.endsWith(AUTOSAVE_EXT)
            ? fn.substring(0, fn.length() - AUTOSAVE_EXT.length())
            : fn.substring(0, fn.length() - EXT.length());
        var modified = Files.getLastModifiedTime(p).toInstant();
        try {
            var root = ((JsonValue.JsonObject) JsonReader.parse(Files.readString(p))).values();
            int version = (int) ((JsonValue.JsonNumber) root.get("schemaVersion")).asLong();
            if (version != SaveFile.SCHEMA_VERSION) {
                return new SlotInfo(name, p, autosave, modified, SlotInfo.Status.OTHER_SCHEMA, version, -1, -1, -1);
            }
            return new SlotInfo(name, p, autosave, modified, SlotInfo.Status.OK, version,
                ((JsonValue.JsonNumber) root.get("tick")).asLong(),
                ((JsonValue.JsonNumber) root.get("seed")).asLong(),
                ((JsonValue.JsonNumber) root.get("credits")).asLong());
        } catch (JsonParseException | IOException | ClassCastException | NullPointerException e) {
            LOG.log(Level.FINE, "Unreadable save " + p, e);
            return new SlotInfo(name, p, autosave, modified, SlotInfo.Status.UNREADABLE, -1, -1, -1, -1);
        }
    }

    /** Deletes a slot or its autosave. {@code name} must be valid, or {@code _autosave}. */
    public void delete(String name, boolean autosave) throws IOException {
        Path target = UNNAMED_AUTOSAVE.equals(name) ? autosavePath(null)
            : autosave ? autosavePath(name) : slotPath(name);
        Files.deleteIfExists(target);
    }

    /** Deletes a listed file, including hand-named ones that {@link #delete(String, boolean)} can't name. */
    public void delete(SlotInfo info) throws IOException {
        Files.deleteIfExists(info.path());
    }

    private static String checked(String name) {
        String err = validateName(name);
        if (err != null) throw new IllegalArgumentException(err + ": \"" + name + "\"");
        return name.trim();
    }
}
