package spacecolony.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.engine.Engine;
import spacecolony.save.SaveFile;
import spacecolony.save.SaveSlots;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class FileMenuTest {

    @Test
    void quit_newGame_autosavesToUnnamedSlotThenExits(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp);
        CountDownLatch exited = new CountDownLatch(1);
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(8L));
            engine.world().tick = 42;
            FileMenu menu = new FileMenu(null, engine, slots, exited::countDown);
            assertNull(menu.currentSlot());
            menu.quit(false);
        });
        assertTrue(exited.await(10, TimeUnit.SECONDS), "exit was not called");
        Path auto = tmp.resolve("_autosave.json");
        assertTrue(Files.exists(auto));
        assertEquals(42L, SaveFile.load(auto).tick);
    }

    @Test
    void quit_afterLoadingSlot_autosavesBesideThatSlot(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp);
        slots.save(WorldGenerator.generate(9L), "base");
        String named = Files.readString(slots.fileFor("base"));
        CountDownLatch exited = new CountDownLatch(1);
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            FileMenu menu = new FileMenu(null, engine, slots, exited::countDown);
            SaveSlots.Slot slot = slots.list().get(0);
            menu.startGame(slots.load(slot), slot.owningSlot());
            assertEquals("base", menu.currentSlot());
            engine.world().tick = 77;
            menu.quit(false);
        });
        assertTrue(exited.await(10, TimeUnit.SECONDS), "exit was not called");
        assertEquals(77L, SaveFile.load(slots.autosaveFileFor("base")).tick);
        assertEquals(named, Files.readString(slots.fileFor("base")), "named slot must not be overwritten");
        assertFalse(Files.exists(tmp.resolve("_autosave.json")));
    }

    @Test
    void quit_afterLoadingAutosave_keepsWritingThatAutosave(@TempDir Path tmp) throws Exception {
        SaveSlots slots = new SaveSlots(tmp);
        slots.autosave(WorldGenerator.generate(4L), "base");
        CountDownLatch exited = new CountDownLatch(1);
        Edt.run(() -> {
            FileMenu menu = new FileMenu(null, new Engine(WorldGenerator.generate(1L)), slots, exited::countDown);
            SaveSlots.Slot slot = slots.list().get(0);
            menu.startGame(slots.load(slot), slot.owningSlot());
            assertEquals("base", menu.currentSlot());
            menu.startGame(WorldGenerator.generate(2L), null);  // File -> New forgets the slot
            assertNull(menu.currentSlot());
            menu.startGame(slots.load(slot), slot.owningSlot());
            menu.quit(false);
        });
        assertTrue(exited.await(10, TimeUnit.SECONDS), "exit was not called");
        assertEquals(4L, SaveFile.load(slots.autosaveFileFor("base")).seed);
        assertFalse(Files.exists(slots.fileFor("base")));
    }
}
