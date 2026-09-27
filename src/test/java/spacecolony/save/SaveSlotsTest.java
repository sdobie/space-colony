package spacecolony.save;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveSlotsTest {

    @Test
    void validateName() {
        assertNull(SaveSlots.validateName("colony 1"));
        assertNull(SaveSlots.validateName("mars_run-2"));
        assertNull(SaveSlots.validateName("  padded  "));
        assertNull(SaveSlots.validateName("x".repeat(40)));
        for (String bad : List.of("", "   ", "x".repeat(41), "_x", "a/b", "a.json", "a.autosave", "..", "a\\b")) {
            assertNotNull(SaveSlots.validateName(bad), bad);
        }
        assertNotNull(SaveSlots.validateName(null));
    }

    @Test
    void paths(@TempDir Path dir) {
        SaveSlots s = new SaveSlots(dir);
        assertEquals(dir.resolve("colony.json"), s.slotPath("colony"));
        assertEquals(dir.resolve("colony.autosave.json"), s.autosavePath("colony"));
        assertEquals(dir.resolve("_autosave.json"), s.autosavePath(null));
        assertThrows(IllegalArgumentException.class, () -> s.slotPath("a/b"));
        assertThrows(IllegalArgumentException.class, () -> s.autosavePath("_x"));
    }

    @Test
    void slotOf_mapsFilesBackToSlots(@TempDir Path dir) {
        SaveSlots s = new SaveSlots(dir);
        assertEquals("belt", s.slotOf(s.slotPath("belt")));
        assertEquals("mars", s.slotOf(s.autosavePath("mars")));
        assertNull(s.slotOf(s.autosavePath(null)));
        assertNull(s.slotOf(dir.resolve("my save!.json")), "hand-named file");
        assertNull(s.slotOf(dir.resolveSibling("elsewhere").resolve("x.json")), "outside the slots dir");
    }

    @Test
    void list_newestFirst_withHeaders(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        World a = WorldGenerator.generate(1L); a.tick = 10; a.credits = 111;
        World b = WorldGenerator.generate(2L); b.tick = 20; b.credits = 222;
        SaveFile.save(a, s.slotPath("alpha"));
        SaveFile.save(b, s.slotPath("beta"));
        SaveFile.save(b, s.autosavePath("beta"));
        Files.setLastModifiedTime(s.slotPath("alpha"), FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(s.slotPath("beta"), FileTime.fromMillis(2_000));
        Files.setLastModifiedTime(s.autosavePath("beta"), FileTime.fromMillis(3_000));
        List<SlotInfo> l = s.list();
        assertEquals(List.of("beta", "beta", "alpha"), l.stream().map(SlotInfo::name).toList());
        assertTrue(l.get(0).autosave());
        assertFalse(l.get(1).autosave());
        assertEquals(20L, l.get(1).tick());
        assertEquals(2L, l.get(1).seed());
        assertEquals(111L, l.get(2).credits());
        assertEquals(SlotInfo.Status.OK, l.get(2).status());
    }

    @Test
    void list_unnamedAutosave_isAnAutosave(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        SaveFile.save(WorldGenerator.generate(3L), s.autosavePath(null));
        SlotInfo only = s.list().get(0);
        assertEquals(SaveSlots.UNNAMED_AUTOSAVE, only.name());
        assertTrue(only.autosave());
    }

    @Test
    void list_unreadableAndWrongSchema_stillListed(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        Files.writeString(dir.resolve("junk.json"), "{nope");
        Files.writeString(dir.resolve("empty.json"), "[]");
        SaveFile.save(WorldGenerator.generate(1L), s.slotPath("tmp"));
        String good = Files.readString(s.slotPath("tmp"));
        Files.delete(s.slotPath("tmp"));
        Files.writeString(dir.resolve("old.json"), good.replace("\"schemaVersion\": " + SaveFile.SCHEMA_VERSION, "\"schemaVersion\": 99"));
        Map<String, SlotInfo> byName = s.list().stream().collect(Collectors.toMap(SlotInfo::name, x -> x));
        assertEquals(SlotInfo.Status.UNREADABLE, byName.get("junk").status());
        assertEquals(SlotInfo.Status.UNREADABLE, byName.get("empty").status());
        assertEquals(SlotInfo.Status.OTHER_SCHEMA, byName.get("old").status());
        assertEquals(99, byName.get("old").schemaVersion());
        assertEquals(-1L, byName.get("old").tick());
    }

    @Test
    void list_skipsTmpAndNonJson(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        SaveFile.save(WorldGenerator.generate(1L), s.slotPath("real"));
        Files.writeString(dir.resolve("half.json.tmp"), "{");
        Files.writeString(dir.resolve("notes.txt"), "hi");
        Files.createDirectory(dir.resolve("folder.json"));
        assertEquals(List.of("real"), s.list().stream().map(SlotInfo::name).toList());
    }

    @Test
    void delete_removesOnlyTarget(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        World w = WorldGenerator.generate(1L);
        SaveFile.save(w, s.slotPath("c"));
        SaveFile.save(w, s.autosavePath("c"));
        s.delete("c", false);
        assertFalse(Files.exists(s.slotPath("c")));
        assertTrue(Files.exists(s.autosavePath("c")));
        s.delete("c", true);
        assertFalse(Files.exists(s.autosavePath("c")));
    }

    @Test
    void delete_byInfo_handlesHandNamedFiles(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        Files.writeString(dir.resolve("my save!.json"), "{nope");
        s.delete(s.list().get(0));
        assertEquals(List.of(), s.list());
    }

    @Test
    void list_missingDir_isEmpty(@TempDir Path dir) throws Exception {
        assertEquals(List.of(), new SaveSlots(dir.resolve("nope")).list());
    }

    @Test
    void v1Save_listsAsOk(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("legacy.json"), SaveFileSchemaTest.v1Sample());
        SlotInfo info = new SaveSlots(dir).list().get(0);
        assertEquals(SlotInfo.Status.OK, info.status());
        assertEquals(1, info.schemaVersion());
        assertTrue(info.tick() > 0);
    }
}
