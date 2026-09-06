package spacecolony.save;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveFileSaveTest {

    @Test
    void save_writesFileWithSchemaVersion1(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(7L);
        Path file = tmp.resolve("test.json");
        SaveFile.save(w, file);
        assertTrue(Files.exists(file));
        String content = Files.readString(file);
        JsonValue.JsonObject root = (JsonValue.JsonObject) JsonReader.parse(content);
        assertEquals(1L, ((JsonValue.JsonNumber) root.values().get("schemaVersion")).asLong());
        assertEquals(7L, ((JsonValue.JsonNumber) root.values().get("seed")).asLong());
    }

    @Test
    void save_createsParentDirIfMissing(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        Path nested = tmp.resolve("a").resolve("b").resolve("c.json");
        SaveFile.save(w, nested);
        assertTrue(Files.exists(nested));
    }

    @Test
    void save_atomicReplace_leavesNoTmpFile(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        Path file = tmp.resolve("test.json");
        SaveFile.save(w, file);
        SaveFile.save(w, file); // overwrite
        try (var stream = Files.list(tmp)) {
            long tmpCount = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0, tmpCount, "no .tmp files should linger after successful save");
        }
    }

    @Test
    void save_includesAllPlayerState(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(42L);
        w.credits = 12345;
        w.tick = 100;
        w.tech.researched.add("basic-mining");
        Path file = tmp.resolve("test.json");
        SaveFile.save(w, file);
        JsonValue.JsonObject root = (JsonValue.JsonObject) JsonReader.parse(Files.readString(file));
        assertEquals(100L, ((JsonValue.JsonNumber) root.values().get("tick")).asLong());
        assertEquals(12345L, ((JsonValue.JsonNumber) root.values().get("credits")).asLong());
        JsonValue.JsonObject tech = (JsonValue.JsonObject) root.values().get("tech");
        JsonValue.JsonArray researched = (JsonValue.JsonArray) tech.values().get("researched");
        assertEquals(1, researched.values().size());
        assertEquals("basic-mining", ((JsonValue.JsonString) researched.values().get(0)).value());
    }
}
