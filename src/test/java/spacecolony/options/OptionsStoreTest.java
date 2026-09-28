package spacecolony.options;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.engine.Speed;
import static org.junit.jupiter.api.Assertions.*;

class OptionsStoreTest {
    @Test void missingFile_givesDefaults(@TempDir Path dir) {
        assertEquals(Options.DEFAULTS, new OptionsStore(dir.resolve("options.properties")).load());
    }

    @Test void roundTrip(@TempDir Path dir) throws Exception {
        OptionsStore store = new OptionsStore(dir.resolve("sub/options.properties"));
        Options o = new Options(Speed.X1, 5, false, false, true, 150, true, Level.FINE, true);
        store.save(o);
        assertEquals(o, store.load());
        assertFalse(Files.exists(dir.resolve("sub/options.properties.tmp")));
    }

    @Test void badValue_fallsBackPerKey_andUnknownKeysAreIgnored(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("options.properties");
        Files.writeString(f, """
            gameplay.autosaveMinutes=7
            display.showSplash=false
            developer.logLevel=LOUD
            future.option=42
            """);
        Options o = new OptionsStore(f).load();
        assertEquals(10, o.autosaveMinutes());
        assertFalse(o.showSplash());
        assertEquals(Level.INFO, o.logLevel());
        assertEquals(Options.DEFAULTS.startSpeed(), o.startSpeed());
    }

    @Test void restoredDefaults_keepsTutorialProgress() {
        Options o = new Options(Speed.X1, 5, false, false, true, 150, true, Level.FINE, true);
        assertEquals(Options.DEFAULTS.withTutorialCompleted(true), o.restoredDefaults());
    }

    @Test void systemPropertyOverridesTheFile(@TempDir Path dir) {
        Path f = dir.resolve("custom.properties");
        System.setProperty(OptionsStore.FILE_PROPERTY, f.toString());
        try {
            assertEquals(f, OptionsStore.defaultFile().file());
        } finally {
            System.clearProperty(OptionsStore.FILE_PROPERTY);
        }
    }
}
