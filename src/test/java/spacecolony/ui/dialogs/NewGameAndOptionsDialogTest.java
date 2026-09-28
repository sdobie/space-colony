package spacecolony.ui.dialogs;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.options.Options;
import spacecolony.testutil.Edt;
import static org.junit.jupiter.api.Assertions.*;

class NewGameAndOptionsDialogTest {
    @Test void seedValidation() {
        assertNull(NewGameDialog.validate("-5"));
        assertNull(NewGameDialog.validate(" 12345678901 "));
        assertEquals("Seed must be a whole number", NewGameDialog.validate(""));
        assertEquals("Seed must be a whole number", NewGameDialog.validate("12a"));
    }

    @Test void optionsDialog_buildsPaintsAndCollects() throws Exception {
        Edt.run(() -> {
            OptionsDialog d = new OptionsDialog(Options.DEFAULTS.withTutorialCompleted(true));
            assertEquals(4, d.tabs.getTabCount());
            assertEquals(Options.DEFAULTS.withTutorialCompleted(true), d.collect());
            d.autosaveCombo().setSelectedItem(5);
            assertEquals(5, d.collect().autosaveMinutes());
            assertTrue(d.collect().tutorialCompleted(), "tutorial progress survives the dialog");
            d.setSize(460, 320);
            d.doLayout();
            BufferedImage img = new BufferedImage(460, 320, BufferedImage.TYPE_INT_RGB);
            Graphics g = img.createGraphics();
            assertDoesNotThrow(() -> d.paint(g));
            g.dispose();
        });
    }

    @Test void controlsTab_listsTheRealBindings() {
        var rows = OptionsDialog.shortcutRows();
        assertTrue(rows.stream().anyMatch(r -> r[0].equals("Toggle debug mode") && r[1].equals("Ctrl+D")),
            () -> rows.stream().map(r -> r[0] + "=" + r[1]).toList().toString());
        assertTrue(rows.stream().anyMatch(r -> r[0].equals("Debug: step one tick (paused)") && r[1].equals("F10")));
    }
}
