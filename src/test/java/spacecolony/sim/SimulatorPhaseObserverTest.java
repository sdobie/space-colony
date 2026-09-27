package spacecolony.sim;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.save.SaveFile;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SimulatorPhaseObserverTest {
    @Test
    void observer_seesEightPhasesInOrder() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        List<SimPhase> seen = new ArrayList<>();
        List<Long> ticks = new ArrayList<>();
        sim.setPhaseObserver((tick, phase, nanos) -> { seen.add(phase); ticks.add(tick); assertTrue(nanos >= 0); });
        sim.advance(w);
        assertEquals(List.of(SimPhase.values()), seen);
        assertTrue(ticks.stream().allMatch(t -> t == 1L), "observer reports post-increment tick");
    }

    @Test
    void observer_doesNotChangeOutcome() {
        World a = WorldGenerator.generate(9L), b = WorldGenerator.generate(9L);
        Simulator sa = new Simulator(), sb = new Simulator();
        sb.setPhaseObserver((t, p, n) -> {});
        for (int i = 0; i < 500; i++) { sa.advance(a); sb.advance(b); }
        assertEquals(SaveFile.toJson(a), SaveFile.toJson(b));
    }

    @Test
    void queueDepth_reportsPendingCommands() {
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        sim.enqueue(new QueueResearchCommand("basic-farming"));
        assertEquals(2, sim.queueDepth());
        sim.clearCommands();
        assertEquals(0, sim.queueDepth());
    }
}
