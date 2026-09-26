package spacecolony.ui.dialogs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.save.SaveSlots;
import spacecolony.sim.World;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveSlotDialogTest {

    private static SaveSlots populated(Path dir) throws Exception {
        SaveSlots slots = new SaveSlots(dir);
        World w = WorldGenerator.generate(1L);
        slots.save(w, "alpha");
        slots.autosave(w, "alpha");
        slots.autosave(w, null);
        return slots;
    }

    private static List<String> firstColumn(SaveSlotDialog.Picker p) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < p.model.getRowCount(); i++) out.add((String) p.model.getValueAt(i, 0));
        return out;
    }

    @Test
    void loadPicker_listsSlotsAndAutosaves(@TempDir Path tmp) throws Exception {
        SaveSlots slots = populated(tmp);
        Edt.run(() -> {
            SaveSlotDialog.Picker p = new SaveSlotDialog.Picker(slots, false, null);
            List<String> shown = firstColumn(p);
            assertEquals(3, shown.size());
            assertTrue(shown.containsAll(List.of("alpha", "alpha (autosave)", "Autosave (unsaved game)")), shown.toString());
            assertNull(p.selected());
            assertFalse(p.delete.isEnabled());
            p.table.setRowSelectionInterval(0, 0);
            assertNotNull(p.selected());
            assertTrue(p.delete.isEnabled());
        });
    }

    @Test
    void savePicker_hidesAutosaves_andSelectionFillsName(@TempDir Path tmp) throws Exception {
        SaveSlots slots = populated(tmp);
        Edt.run(() -> {
            SaveSlotDialog.Picker p = new SaveSlotDialog.Picker(slots, true, "draft");
            assertEquals(List.of("alpha"), firstColumn(p));
            assertEquals("draft", p.nameField.getText());
            p.table.setRowSelectionInterval(0, 0);
            assertEquals("alpha", p.nameField.getText());
        });
    }

    @Test
    void loadPicker_emptyDirectory_saysSo(@TempDir Path tmp) throws Exception {
        Edt.run(() -> {
            SaveSlotDialog.Picker p = new SaveSlotDialog.Picker(new SaveSlots(tmp.resolve("none")), false, null);
            assertEquals(0, p.model.getRowCount());
            assertEquals("No saves yet.", p.status.getText());
        });
    }
}
