package spacecolony.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.engine.Speed;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class DebugControllerTest {
    @Test
    void stepOne_advancesOneTickOnlyWhilePaused() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugController dc = new DebugController(engine);
            dc.stepOne();
            assertEquals(1, engine.world().tick);
            assertEquals(1, dc.timings().sampleCount());
            engine.setSpeed(Speed.X1);
            assertThrows(IllegalStateException.class, dc::stepOne);
            assertEquals(1, engine.world().tick);
            dc.dispose();
        });
    }

    @Test
    void runTicks_advancesNAndFiresOneWorldChanged() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugController dc = new DebugController(engine);
            List<EngineEvent> events = new ArrayList<>();
            engine.addListener(events::add);
            dc.runTicks(250);
            assertEquals(250, engine.world().tick);
            assertEquals(1, events.stream().filter(e -> e instanceof EngineEvent.WorldChanged).count());
            assertEquals(PhaseTimings.WINDOW, dc.timings().sampleCount());
            assertThrows(IllegalArgumentException.class, () -> dc.runTicks(0));
            assertThrows(IllegalArgumentException.class, () -> dc.runTicks(DebugController.MAX_RUN_TICKS + 1));
            dc.dispose();
        });
    }

    @Test
    void triggerEvent_emitsTheChosenKindOnTheChosenBodyNow() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugController dc = new DebugController(engine);
            List<EngineEvent> events = new ArrayList<>();
            engine.addListener(events::add);
            int popBefore = engine.world().findSite("site-earth-hub").population;
            dc.triggerEvent("earth", EventKind.DISEASE_OUTBREAK);
            Event last = engine.world().recentEvents.peekLast();
            assertEquals(EventKind.DISEASE_OUTBREAK, last.kind());
            assertEquals("earth", last.bodyId());
            assertEquals(0, last.tick());
            assertEquals(0, engine.world().tick, "forcing an event must not advance time");
            assertTrue(engine.world().findSite("site-earth-hub").population < popBefore);
            assertTrue(events.stream().anyMatch(e -> e instanceof EngineEvent.WorldChanged));
            assertThrows(IllegalArgumentException.class, () -> dc.triggerEvent("pluto-x", EventKind.SOLAR_FLARE));
            dc.dispose();
        });
    }

    @Test
    void worldEditsAreRefusedWhileRunning() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugController dc = new DebugController(engine);
            engine.setSpeed(Speed.X1);
            int before = engine.world().recentEvents.size();
            assertThrows(IllegalStateException.class, () -> dc.triggerEvent("earth", EventKind.SOLAR_FLARE));
            assertThrows(IllegalStateException.class, () -> engine.applyDebugEdit("x", w -> w.credits = 1));
            assertEquals(before, engine.world().recentEvents.size());
            assertEquals(10_000, engine.world().credits);
            dc.dispose();
        });
    }

    @Test
    void debugEditsAreLogged() throws Exception {
        RingBufferHandler buf = DebugLogging.install(Level.INFO);
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            engine.applyDebugEdit("set credits to 5", w -> w.credits = 5);
            assertEquals(5, engine.world().credits);
            assertTrue(buf.snapshot().stream().anyMatch(r -> r.getMessage().equals("Debug edit: set credits to 5")));
        });
    }

    @Test
    void worldEventsAreMirroredIntoTheLogOnce() throws Exception {
        RingBufferHandler buf = DebugLogging.install(Level.INFO);
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugController dc = new DebugController(engine);
            buf.clear();
            dc.triggerEvent("mars", EventKind.METEOR_STRIKE);
            engine.tick(); // no new world events expected to re-log the meteor strike
            long mirrored = buf.snapshot().stream()
                .filter(r -> r.getLoggerName().equals("spacecolony.sim.events"))
                .map(LogRecord::getMessage)
                .filter(m -> m.contains("METEOR_STRIKE"))
                .count();
            assertEquals(1, mirrored);
            dc.dispose();
        });
    }

    @Test
    void resolve_mapsSelectionsToLiveObjects() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            assertSame(engine.world().findBody("earth"), DebugController.resolve(engine, Selection.body("earth")));
            assertSame(engine.world().findSite("site-earth-hub"),
                DebugController.resolve(engine, Selection.site("site-earth-hub")));
            assertNull(DebugController.resolve(engine, Selection.NONE));
            assertNull(DebugController.resolve(engine, Selection.ship("nope")));
        });
    }

    @Test
    void pendingCommandCount_tracksTheQueue() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            assertEquals(0, engine.pendingCommandCount());
            engine.enqueue(new QueueResearchCommand("basic-mining"));
            assertEquals(1, engine.pendingCommandCount());
            engine.tick();
            assertEquals(0, engine.pendingCommandCount());
        });
    }

    @Test
    void toggleDebug_firesDebugModeChanged() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DebugController dc = new DebugController(engine);
            List<EngineEvent> events = new ArrayList<>();
            engine.addListener(events::add);
            dc.toggleDebug();
            assertTrue(engine.debugEnabled());
            dc.setDebugEnabled(true); // no-op, no duplicate event
            dc.toggleDebug();
            assertFalse(engine.debugEnabled());
            assertEquals(List.of(new EngineEvent.DebugModeChanged(true), new EngineEvent.DebugModeChanged(false)),
                events);
            dc.dispose();
        });
    }
}
