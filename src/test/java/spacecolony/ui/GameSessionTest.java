package spacecolony.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.save.SaveFile;
import spacecolony.save.SaveSlots;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {
    record Calls(List<Integer> exits, List<String> prompts) {
        Calls() { this(new ArrayList<>(), new ArrayList<>()); }
    }

    private static GameSession session(Engine e, SaveSlots slots, Calls c, boolean confirmQuit, boolean quitAnyway) {
        return new GameSession(e, slots, new GameSession.Ui() {
            public boolean confirmQuit() { return confirmQuit; }
            public boolean quitAnyway(String msg) { c.prompts().add(msg); return quitAnyway; }
            public void savedToast(String label) {}
        }, c.exits()::add);
    }

    @Test
    void slotName_transitions(@TempDir Path dir) throws Exception {
        Edt.run(() -> {
            SaveSlots slots = new SaveSlots(dir);
            GameSession s = session(new Engine(WorldGenerator.generate(1L)), slots, new Calls(), true, true);
            assertNull(s.currentSlot());
            s.onSavedAs("colony");                    assertEquals("colony", s.currentSlot());
            s.onNewGame();                            assertNull(s.currentSlot());
            s.onLoaded(slots.autosavePath("mars"));   assertEquals("mars", s.currentSlot());
            s.onLoaded(slots.autosavePath(null));     assertNull(s.currentSlot());
            s.onLoaded(slots.slotPath("belt"));       assertEquals("belt", s.currentSlot());
            s.onLoaded(dir.resolveSibling("elsewhere").resolve("x.json")); assertNull(s.currentSlot());
        });
    }

    @Test
    void quit_writesAutosave_thenExits(@TempDir Path dir) throws Exception {
        Calls c = new Calls();
        SaveSlots slots = new SaveSlots(dir);
        SaveFile.save(WorldGenerator.generate(1L), slots.slotPath("colony"));
        String named = Files.readString(slots.slotPath("colony"));
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            engine.world().tick = 55;
            GameSession s = session(engine, slots, c, true, true);
            s.onSavedAs("colony");
            s.quit();
        });
        assertEquals(55L, SaveFile.load(dir.resolve("colony.autosave.json")).tick);
        assertEquals(named, Files.readString(slots.slotPath("colony")), "named slot untouched");
        assertEquals(List.of(0), c.exits());
    }

    @Test
    void quit_unnamedGame_writesUnnamedAutosave(@TempDir Path dir) throws Exception {
        Calls c = new Calls();
        Edt.run(() -> session(new Engine(WorldGenerator.generate(1L)), new SaveSlots(dir), c, true, true).quit());
        assertTrue(Files.exists(dir.resolve("_autosave.json")));
        assertEquals(List.of(0), c.exits());
    }

    @Test
    void quit_cancelled_doesNothing_andStaysPaused(@TempDir Path dir) throws Exception {
        Calls c = new Calls();
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            engine.setSpeed(Speed.X4);
            session(engine, new SaveSlots(dir), c, false, true).quit();
            assertEquals(Speed.PAUSED, engine.speed());
        });
        try (var files = Files.list(dir)) { assertEquals(0, files.count()); }
        assertTrue(c.exits().isEmpty());
    }

    @Test
    void quit_autosaveFails_asksBeforeExit(@TempDir Path dir) throws Exception {
        Path blocker = dir.resolve("f");
        Files.writeString(blocker, "x");
        Calls declined = new Calls();
        Edt.run(() -> session(new Engine(WorldGenerator.generate(1L)),
            new SaveSlots(blocker.resolve("saves")), declined, true, false).quit());
        assertEquals(1, declined.prompts().size());
        assertTrue(declined.exits().isEmpty(), "declined 'quit anyway'");

        Calls accepted = new Calls();
        Edt.run(() -> session(new Engine(WorldGenerator.generate(1L)),
            new SaveSlots(blocker.resolve("saves")), accepted, true, true).quit());
        assertEquals(List.of(0), accepted.exits());
    }
}
