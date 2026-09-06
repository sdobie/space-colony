package spacecolony.engine;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineEdtTest {

    @Test
    void enqueueOffEdt_throwsAssertionError() {
        // We're on a JUnit worker thread, not EDT. With -ea on, the guard must fire.
        assertFalse(SwingUtilities.isEventDispatchThread(),
            "precondition: test runs on a non-EDT thread");
        Engine engine = new Engine(WorldGenerator.generate(1L));
        assertThrows(AssertionError.class,
            () -> engine.enqueue(new QueueResearchCommand("basic-mining")));
    }

    @Test
    void enqueueOnEdt_succeeds() throws Exception {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        SwingUtilities.invokeAndWait(() ->
            engine.enqueue(new QueueResearchCommand("basic-mining")));
        // No assertion error means success.
    }
}
