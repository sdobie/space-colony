package spacecolony;

import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LaunchArgsTest {
    @Test void noArgs() {
        LaunchArgs a = LaunchArgs.parse(new String[0]);
        assertNull(a.seed());
        assertFalse(a.skipTitle());
        assertFalse(a.debug());
        assertFalse(a.skipIntro());
        assertNull(a.logLevel());
        assertNull(a.badLevel());
    }

    @Test void seed_skipsTheTitle() {
        LaunchArgs a = LaunchArgs.parse(new String[] {"--seed", "7"});
        assertEquals(7L, a.seed());
        assertTrue(a.skipTitle());
    }

    @Test void flags() {
        LaunchArgs a = LaunchArgs.parse(new String[] {"--debug", "--skip-intro", "--log-level=DEBUG"});
        assertTrue(a.debug());
        assertTrue(a.skipIntro());
        assertEquals(Level.FINE, a.logLevel());
    }

    @Test void badLogLevel_isKept() {
        LaunchArgs a = LaunchArgs.parse(new String[] {"--log-level=LOUD"});
        assertNull(a.logLevel());
        assertEquals("--log-level=LOUD", a.badLevel());
    }

    @Test void version_comesFromTheBuild() {
        assertEquals("0.6.0", LaunchArgs.version());
    }
}
