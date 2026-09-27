package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.debug.TriggerEventDialog;
import spacecolony.sim.EventKind;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineDebugTest {
    @Test void debugToggle_firesOncePerChange() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            List<EngineEvent> seen = new ArrayList<>();
            e.addListener(seen::add);
            e.setDebugEnabled(true); e.setDebugEnabled(true); e.setDebugEnabled(false);
            assertEquals(List.of(new EngineEvent.DebugModeChanged(true), new EngineEvent.DebugModeChanged(false)), seen);
        });
    }

    @Test void step_whilePaused_advancesOneTick() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.step();
            assertEquals(1L, e.world().tick);
        });
    }

    @Test void step_whileRunning_assertionFires() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.setSpeed(Speed.X1);
            assertThrows(AssertionError.class, e::step);
        });
    }

    @Test void advanceSilently_firesOneWorldChanged() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            List<EngineEvent> seen = new ArrayList<>();
            e.addListener(seen::add);
            e.advanceSilently(250);
            assertEquals(250L, e.world().tick);
            assertEquals(1, seen.stream().filter(x -> x instanceof EngineEvent.WorldChanged).count());
        });
    }

    @Test void advanceSilently_rejectsOutOfRange() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            assertThrows(IllegalArgumentException.class, () -> e.advanceSilently(0));
            assertThrows(IllegalArgumentException.class, () -> e.advanceSilently(10_001));
        });
    }

    @Test void applyDebugEdit_mutatesAndNotifies_onlyWhenPaused() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            List<EngineEvent> seen = new ArrayList<>();
            e.addListener(seen::add);
            e.applyDebugEdit("credits", w -> w.credits = 42);
            assertEquals(42, e.world().credits);
            assertTrue(seen.stream().anyMatch(x -> x instanceof EngineEvent.WorldChanged));
            e.setSpeed(Speed.X1);
            assertThrows(AssertionError.class, () -> e.applyDebugEdit("x", w -> {}));
        });
    }

    @Test void commandQueueDepth_tracksEnqueue() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.enqueue(new QueueResearchCommand("basic-mining"));
            assertEquals(1, e.commandQueueDepth());
            e.tick();
            assertEquals(0, e.commandQueueDepth());
        });
    }

    @Test void ticksPerSecond_zeroUntilTwoTicks() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            assertEquals(0.0, e.ticksPerSecond());
            e.tick();
            assertEquals(0.0, e.ticksPerSecond());
            Thread.sleep(5);
            e.tick();
            assertTrue(e.ticksPerSecond() > 0);
        });
    }

    @Test void triggerEvent_whilePaused_appliesForcedEventNow() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            int before = e.world().findSite("site-earth-hub").population;
            TriggerEventDialog.trigger(e, "earth", EventKind.DISEASE_OUTBREAK);
            assertTrue(e.world().findSite("site-earth-hub").population < before);
            assertEquals(EventKind.DISEASE_OUTBREAK, e.world().recentEvents.peekLast().kind());
            assertEquals(0L, e.world().tick, "forcing an event must not advance time");
        });
    }
}
