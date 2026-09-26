package spacecolony.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.save.SaveFile;
import spacecolony.save.SaveSlots;
import spacecolony.sim.World;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveSlotDialogTest {

    private static SaveSlots populated(Path dir) throws Exception {
        SaveSlots slots = new SaveSlots(dir);
        World w = WorldGenerator.generate(1L);
        w.tick = 400;
        w.credits = 1234;
        SaveFile.save(w, slots.slotPath("alpha"));
        SaveFile.save(w, slots.autosavePath("alpha"));
        SaveFile.save(w, slots.autosavePath(null));
        Files.writeString(dir.resolve("junk.json"), "{nope");
        return slots;
    }

    /** Waits for the table's background load to land. */
    private static SaveSlotDialog.Content loaded(SaveSlots slots, SaveSlotDialog.Mode mode) throws Exception {
        SaveSlotDialog.Content[] c = new SaveSlotDialog.Content[1];
        Edt.run(() -> c[0] = SaveSlotDialog.contentForTest(slots, mode));
        long deadline = System.currentTimeMillis() + 10_000;
        String[] status = {"Loading…"};
        while ("Loading…".equals(status[0]) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            Edt.run(() -> status[0] = c[0].status.getText());
        }
        assertNotEquals("Loading…", status[0], "slot list never loaded");
        return c[0];
    }

    private static List<List<Object>> rows(SaveSlotDialog.Content c) {
        List<List<Object>> out = new ArrayList<>();
        for (int r = 0; r < c.model.getRowCount(); r++) {
            List<Object> row = new ArrayList<>();
            for (int col = 0; col < c.model.getColumnCount(); col++) row.add(c.model.getValueAt(r, col));
            out.add(row);
        }
        return out;
    }

    @Test
    void load_listsEverything_andOnlyEnablesLoadForReadableRows(@TempDir Path dir) throws Exception {
        SaveSlotDialog.Content c = loaded(populated(dir), SaveSlotDialog.Mode.LOAD);
        Edt.run(() -> {
            List<List<Object>> rows = rows(c);
            assertEquals(4, rows.size());
            List<Object> names = rows.stream().map(r -> r.get(0)).toList();
            assertTrue(names.containsAll(List.of("alpha", "(unnamed game)", "junk")), names.toString());
            List<Object> alphaSlot = rows.stream()
                .filter(r -> r.get(0).equals("alpha") && r.get(1).equals("slot")).findFirst().orElseThrow();
            assertEquals("Y1 D36", alphaSlot.get(3));
            assertEquals("1234", alphaSlot.get(4));
            assertFalse(c.primary.isEnabled());
            assertFalse(c.delete.isEnabled());

            int junk = names.indexOf("junk");
            c.table.setRowSelectionInterval(junk, junk);
            assertEquals("(unreadable)", rows.get(junk).get(3));
            assertFalse(c.primary.isEnabled(), "can't load an unreadable file");
            assertTrue(c.delete.isEnabled(), "but can delete it");

            int alpha = rows.indexOf(alphaSlot);
            c.table.setRowSelectionInterval(alpha, alpha);
            assertTrue(c.primary.isEnabled());
            c.onPrimary();
            assertEquals("alpha", ((spacecolony.save.SlotInfo) c.result).name());
        });
    }

    @Test
    void saveAs_showsOnlySlots_andValidatesName(@TempDir Path dir) throws Exception {
        SaveSlotDialog.Content c = loaded(populated(dir), SaveSlotDialog.Mode.SAVE_AS);
        Edt.run(() -> {
            assertEquals(List.of("alpha"), rows(c).stream().map(r -> r.get(0)).toList());
            assertFalse(c.primary.isEnabled(), "empty name");
            assertEquals(" ", c.error.getText(), "no nagging before typing");

            c.nameField.setText("bad/name");
            assertFalse(c.primary.isEnabled());
            assertEquals("Letters, digits, space, _ and - only", c.error.getText());

            c.nameField.setText("  fresh ");
            assertTrue(c.primary.isEnabled());
            c.onPrimary();
            assertEquals("fresh", c.result);

            c.table.setRowSelectionInterval(0, 0);
            assertEquals("alpha", c.nameField.getText());
        });
    }

    @Test
    void emptyDirectory_saysSo_andPaints(@TempDir Path dir) throws Exception {
        SaveSlotDialog.Content c = loaded(new SaveSlots(dir.resolve("none")), SaveSlotDialog.Mode.LOAD);
        Edt.run(() -> {
            assertEquals("No saves yet.", c.status.getText());
            c.setSize(600, 360);
            c.doLayout();
            BufferedImage img = new BufferedImage(600, 360, BufferedImage.TYPE_INT_RGB);
            Graphics g = img.getGraphics();
            c.paint(g);
            g.dispose();
        });
    }
}
