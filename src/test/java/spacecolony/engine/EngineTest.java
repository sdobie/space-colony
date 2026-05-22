package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BuildingType;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    @Test
    void tick_advancesWorldAndFiresEvent() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        List<EngineEvent> received = new ArrayList<>();
        engine.addListener(received::add);
        long before = engine.world().tick;
        engine.tick();
        assertEquals(before + 1, engine.world().tick);
        assertTrue(received.stream().anyMatch(e -> e instanceof EngineEvent.WorldChanged));
    }

    @Test
    void enqueue_appliesCommandOnNextTick() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        int before = engine.world().findSite("site-earth-hub").buildings.size();
        engine.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
        engine.tick();
        assertEquals(before + 1, engine.world().findSite("site-earth-hub").buildings.size());
    }

    @Test
    void selectionChange_firesEvent() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        List<EngineEvent> received = new ArrayList<>();
        engine.addListener(received::add);
        engine.setSelection(Selection.body("mars"));
        assertEquals(Selection.body("mars"), engine.selection());
        assertTrue(received.stream().anyMatch(e ->
            e instanceof EngineEvent.SelectionChanged sc && sc.selection().equals(Selection.body("mars"))));
    }

    @Test
    void initialSelectionIsNone() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        assertEquals(Selection.NONE, engine.selection());
    }

    @Test
    void initialSpeedIsPaused() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        assertEquals(Speed.PAUSED, engine.speed());
    }
}
