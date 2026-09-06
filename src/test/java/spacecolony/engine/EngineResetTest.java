package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import spacecolony.sim.World;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineResetTest {

    @Test
    void reset_swapsWorld_andFiresWorldReplaced() throws Exception {
        runOnEdt(() -> {
            World w1 = WorldGenerator.generate(1L);
            World w2 = WorldGenerator.generate(2L);
            Engine engine = new Engine(w1);
            List<EngineEvent> seen = new ArrayList<>();
            engine.addListener(seen::add);

            engine.reset(w2);

            assertSame(w2, engine.world());
            assertTrue(seen.stream().anyMatch(e -> e instanceof EngineEvent.WorldReplaced));
        });
    }

    @Test
    void reset_clearsPendingCommands() throws Exception {
        runOnEdt(() -> {
            World w1 = WorldGenerator.generate(1L);
            World w2 = WorldGenerator.generate(2L);
            Engine engine = new Engine(w1);
            engine.enqueue(new QueueResearchCommand("basic-mining"));
            engine.reset(w2);
            engine.tick();
            assertNull(w2.tech.activeId, "queued command should have been dropped on reset");
        });
    }

    @Test
    void reset_resetsSelection() throws Exception {
        runOnEdt(() -> {
            World w1 = WorldGenerator.generate(1L);
            Engine engine = new Engine(w1);
            engine.setSelection(Selection.body("mars"));
            engine.reset(WorldGenerator.generate(2L));
            assertEquals(Selection.NONE, engine.selection());
        });
    }

    private static void runOnEdt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeAndWait(r);
    }
}
