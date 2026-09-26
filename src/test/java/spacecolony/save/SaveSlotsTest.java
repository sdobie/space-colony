package spacecolony.save;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveSlotsTest {

    @Test
    void validateName_trimsAndDropsJsonExtension() {
        assertEquals("Colony One", SaveSlots.validateName("  Colony One  "));
        assertEquals("mars-run", SaveSlots.validateName("mars-run.json"));
        assertEquals("v1.2 (backup)", SaveSlots.validateName("v1.2 (backup)"));
    }

    @Test
    void validateName_rejectsUnusableNames() {
        for (String bad : new String[] {null, "", "   ", ".json", "../escape", "a/b", "a\\b", ".hidden",
                "_autosave", "trailing.", "mine.autosave", "Mine.AUTOSAVE", "x".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> SaveSlots.validateName(bad), "accepted: " + bad);
        }
    }

    @Test
    void files_followSpecNaming(@TempDir Path tmp) {
        SaveSlots slots = new SaveSlots(tmp);
        assertEquals(tmp.resolve("alpha.json"), slots.fileFor("alpha"));
        assertEquals(tmp.resolve("alpha.autosave.json"), slots.autosaveFileFor("alpha"));
        assertEquals(tmp.resolve("_autosave.json"), slots.autosaveFileFor(null));
    }

    @Test
    void list_isEmptyWhenDirectoryMissing(@TempDir Path tmp) throws Exception {
        assertEquals(List.of(), new SaveSlots(tmp.resolve("nope")).list());
    }

    @Test
    void list_newestFirst_skipsTmpAndOtherFiles(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp);
        World w = WorldGenerator.generate(3L);
        slots.save(w, "old");
        slots.save(w, "new");
        slots.autosave(w, "old");
        Files.setLastModifiedTime(slots.fileFor("old"), FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        Files.setLastModifiedTime(slots.autosaveFileFor("old"), FileTime.from(Instant.parse("2026-02-01T00:00:00Z")));
        Files.setLastModifiedTime(slots.fileFor("new"), FileTime.from(Instant.parse("2026-03-01T00:00:00Z")));
        Files.writeString(tmp.resolve("half.json.tmp"), "{");
        Files.writeString(tmp.resolve("notes.txt"), "hi");
        Files.createDirectory(tmp.resolve("dir.json"));

        List<String> names = slots.list().stream().map(SaveSlots.Slot::name).toList();
        assertEquals(List.of("new", "old.autosave", "old"), names);
    }

    @Test
    void saveThenLoad_roundTripsThroughSlot(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp.resolve("saves"));
        World w = WorldGenerator.generate(11L);
        w.tick = 250;
        w.credits = 777;
        slots.save(w, "first colony");
        assertTrue(slots.exists("first colony"));

        SaveSlots.Slot slot = slots.list().get(0);
        World loaded = slots.load(slot);
        assertEquals(11L, loaded.seed);
        assertEquals(250L, loaded.tick);
        assertEquals(777L, loaded.credits);
    }

    @Test
    void autosave_leavesNamedSlotUntouched(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp);
        World w = WorldGenerator.generate(5L);
        w.tick = 10;
        slots.save(w, "main");
        String before = Files.readString(slots.fileFor("main"));

        w.tick = 99;
        slots.autosave(w, "main");
        assertEquals(before, Files.readString(slots.fileFor("main")));
        assertEquals(99L, SaveFile.load(slots.autosaveFileFor("main")).tick);
    }

    @Test
    void autosave_withoutSlot_writesUnnamedAutosave(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp);
        slots.autosave(WorldGenerator.generate(5L), null);
        assertTrue(Files.exists(tmp.resolve("_autosave.json")));
        SaveSlots.Slot only = slots.list().get(0);
        assertTrue(only.isAutosave());
        assertNull(only.owningSlot());
    }

    @Test
    void slot_owningSlot_mapsAutosaveBackToItsSlot(@TempDir Path tmp) {
        assertEquals("main", slot(tmp, "main").owningSlot());
        assertEquals("main", slot(tmp, "main.autosave").owningSlot());
        assertTrue(slot(tmp, "main.autosave").isAutosave());
        assertFalse(slot(tmp, "main").isAutosave());
        // Hand-copied files with names the picker would refuse fall back to the unnamed autosave.
        assertNull(slot(tmp, "my save!").owningSlot());
        assertNull(slot(tmp, "my save!.autosave").owningSlot());
    }

    @Test
    void delete_removesFile(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp);
        slots.save(WorldGenerator.generate(1L), "gone");
        slots.delete(slots.list().get(0));
        assertFalse(slots.exists("gone"));
        assertEquals(List.of(), slots.list());
    }

    private static SaveSlots.Slot slot(Path dir, String name) {
        return new SaveSlots.Slot(name, dir.resolve(name + ".json"), Instant.EPOCH);
    }
}
