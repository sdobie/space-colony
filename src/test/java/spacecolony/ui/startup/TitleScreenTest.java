package spacecolony.ui.startup;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.options.Options;
import spacecolony.save.SlotInfo;
import static org.junit.jupiter.api.Assertions.*;
import static spacecolony.ui.startup.TitleScreen.Action.*;

class TitleScreenTest {
    static final Instant NOW = Instant.parse("2026-09-27T20:00:00Z");

    static SlotInfo slot(String name, SlotInfo.Status status, long tick, Duration age) {
        return new SlotInfo(name, Path.of(name + ".json"), false, NOW.minus(age), status, 2, tick, 1, 100);
    }

    @Test void firstRun_defaultsToTutorial_withBanner() {
        assertEquals(TUTORIAL, TitleScreen.defaultAction(Options.DEFAULTS, List.of()));
        assertTrue(TitleScreen.showNewPlayerBanner(Options.DEFAULTS, List.of()));
    }

    @Test void withSaves_defaultsToContinue() {
        var slots = List.of(slot("colony", SlotInfo.Status.OK, 10, Duration.ofHours(1)));
        assertEquals(CONTINUE, TitleScreen.defaultAction(Options.DEFAULTS, slots));
        assertFalse(TitleScreen.showNewPlayerBanner(Options.DEFAULTS, slots));
    }

    @Test void onlyUnreadableSaves_defaultToNewGame() {
        var slots = List.of(slot("junk", SlotInfo.Status.UNREADABLE, -1, Duration.ZERO));
        assertEquals(NEW_GAME, TitleScreen.defaultAction(Options.DEFAULTS, slots));
        assertNull(TitleScreen.continueTarget(slots));
    }

    @Test void tutorialDone_noSaves_defaultsToNewGame_noBanner() {
        Options done = Options.DEFAULTS.withTutorialCompleted(true);
        assertEquals(NEW_GAME, TitleScreen.defaultAction(done, List.of()));
        assertFalse(TitleScreen.showNewPlayerBanner(done, List.of()));
    }

    @Test void continueTarget_skipsUnreadableNewerFiles() {
        var slots = List.of(slot("junk", SlotInfo.Status.OTHER_SCHEMA, -1, Duration.ZERO),
                            slot("colony", SlotInfo.Status.OK, 10, Duration.ofHours(1)));
        assertEquals("colony", TitleScreen.continueTarget(slots).name());
    }

    @Test void continueLabel() {
        assertEquals("colony · Y3 D121 · 2 hours ago",
            TitleScreen.continueLabel(slot("colony", SlotInfo.Status.OK, 1215, Duration.ofMinutes(130)), NOW));
        assertEquals("Unnamed game · Y0 D1 · just now",
            TitleScreen.continueLabel(slot("_autosave", SlotInfo.Status.OK, 0, Duration.ofSeconds(5)), NOW));
        assertEquals("3 days ago", TitleScreen.ago(NOW.minus(Duration.ofDays(3)), NOW));
        assertEquals("1 minute ago", TitleScreen.ago(NOW.minus(Duration.ofSeconds(61)), NOW));
    }
}
