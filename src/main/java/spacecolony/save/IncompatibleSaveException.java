package spacecolony.save;

/**
 * Thrown by {@link SaveFile#load} when the file's schemaVersion does not match
 * the current code's schemaVersion. The world is left unchanged; the UI shows
 * a warning and the player keeps their current game.
 */
public class IncompatibleSaveException extends Exception {
    public final int fileSchemaVersion;
    public final int currentSchemaVersion;
    public IncompatibleSaveException(int fileSchemaVersion, int currentSchemaVersion) {
        super("Save file schema v" + fileSchemaVersion
            + " is incompatible with current schema v" + currentSchemaVersion);
        this.fileSchemaVersion = fileSchemaVersion;
        this.currentSchemaVersion = currentSchemaVersion;
    }
}
