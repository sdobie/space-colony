package spacecolony.debug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DebugLoggingTest {
    @Test void parseLevel_acceptsSpecAliases() {
        assertEquals(Level.FINE,    DebugLogging.parseLevel("DEBUG"));
        assertEquals(Level.WARNING, DebugLogging.parseLevel("warn"));
        assertEquals(Level.INFO,    DebugLogging.parseLevel("INFO"));
        assertEquals(Level.FINE,    DebugLogging.parseLevel("FINE"));
        assertNull(DebugLogging.parseLevel("loud"));
        assertNull(DebugLogging.parseLevel(null));
    }

    @Test void install_createsLogFile(@TempDir Path dir) throws Exception {
        DebugLogging.Installed inst = DebugLogging.install(Level.INFO, dir);
        try {
            Logger.getLogger("spacecolony.test").info("hello");
            inst.flush();
            try (var files = Files.list(dir)) {
                assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("space-colony-")));
            }
            assertTrue(inst.ring().snapshot().stream().anyMatch(r -> "hello".equals(r.getMessage())));
            assertSame(inst, DebugLogging.current());
        } finally { inst.uninstall(); }
        assertNull(DebugLogging.current());
    }

    @Test void install_unwritableDir_stillHasRing(@TempDir Path dir) throws Exception {
        Path blocker = dir.resolve("file");
        Files.writeString(blocker, "not a dir");
        DebugLogging.Installed inst = DebugLogging.install(Level.INFO, blocker.resolve("logs"));
        try {
            assertNotNull(inst.ring());
            assertNull(inst.file());
            assertTrue(inst.ring().snapshot().stream().anyMatch(r -> r.getLevel() == Level.WARNING));
        } finally { inst.uninstall(); }
    }

    @Test void setLevel_changesWhatIsCaptured(@TempDir Path dir) {
        DebugLogging.Installed inst = DebugLogging.install(Level.WARNING, dir);
        try {
            Logger.getLogger("spacecolony.test").info("dropped");
            inst.setLevel(Level.FINE);
            Logger.getLogger("spacecolony.test").fine("kept");
            var msgs = inst.ring().snapshot().stream().map(r -> r.getMessage()).toList();
            assertFalse(msgs.contains("dropped"));
            assertTrue(msgs.contains("kept"));
        } finally { inst.uninstall(); }
    }
}
