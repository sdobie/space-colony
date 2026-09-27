package spacecolony.tutorial;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialProgressTest {
    private static boolean a, b;

    private static TutorialProgress toy() {
        a = b = false;
        return new TutorialProgress(List.of(
            new TutorialStep("m1", "M1", "", List.of(), null, null),
            new TutorialStep("a", "A", "", List.of(), null, c -> a),
            new TutorialStep("b", "B", "", List.of(), null, c -> b),
            new TutorialStep("m2", "M2", "", List.of(), null, null)));
    }

    private static final TutorialContext CTX = new TutorialContext(null, null, null, null);

    @Test void manualSteps_waitForNext() {
        TutorialProgress p = toy();
        a = b = true;
        assertFalse(p.update(CTX));
        assertEquals("m1", p.current().id());
        p.next();
        assertEquals("a", p.current().id());
    }

    @Test void satisfiedAutoSteps_areSkippedInOneUpdate() {
        TutorialProgress p = toy();
        p.next();
        assertFalse(p.update(CTX));
        a = true;
        b = true;
        assertTrue(p.update(CTX));
        assertEquals("m2", p.current().id());
        assertTrue(p.onLastStep());
    }

    @Test void skip_alwaysAdvances_butNotPastTheEnd() {
        TutorialProgress p = toy();
        p.skip(); p.skip(); p.skip();
        assertEquals("m2", p.current().id());
        p.skip();
        assertEquals(3, p.index());
    }

    @Test void next_onAnAutoStep_isAnError() {
        TutorialProgress p = toy();
        p.next();
        assertThrows(IllegalStateException.class, p::next);
    }
}
