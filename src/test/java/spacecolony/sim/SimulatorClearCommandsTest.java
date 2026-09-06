package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SimulatorClearCommandsTest {
    @Test
    void clearCommands_dropsPendingCommands() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        sim.clearCommands();
        sim.advance(w);
        assertNull(w.tech.activeId, "cleared command should not have set activeId");
    }
}
