package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.World;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineLoggingTest {
    private static List<LogRecord> capture(String loggerName, Edt.Body body) throws Exception {
        Logger logger = Logger.getLogger(loggerName);
        Level prior = logger.getLevel();
        logger.setLevel(Level.ALL);
        List<LogRecord> got = new ArrayList<>();
        Handler h = new Handler() {
            @Override public void publish(LogRecord r) { got.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(h);
        try {
            Edt.run(body);
        } finally {
            logger.removeHandler(h);
            logger.setLevel(prior);
        }
        return got;
    }

    @Test void tickEvents_areLoggedToSimEventsLogger() throws Exception {
        List<LogRecord> got = capture("spacecolony.sim.events", () -> {
            World w = WorldGenerator.generate(1L);
            w.findSite("site-earth-hub").buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
            w.tech.activeId = "basic-mining";
            w.tech.accumulatedPoints = 99.5;
            Engine e = new Engine(w);
            e.tick();   // lab adds ≥1 point → RESEARCH_COMPLETED this tick
        });
        assertTrue(got.stream().anyMatch(r -> r.getLevel() == Level.INFO
            && r.getMessage().contains("RESEARCH_COMPLETED")), got.toString());
    }

    @Test void eachEvent_isLoggedOnceAcrossTicks() throws Exception {
        List<LogRecord> got = capture("spacecolony.sim.events", () -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.advanceSilently(2000);
            e.tick();
            e.tick();
        });
        long distinct = got.stream().map(LogRecord::getMessage).distinct().count();
        assertTrue(got.size() > 0);
        assertEquals(got.size(), distinct, "no event is logged twice");
    }

    @Test void debugEdit_isLoggedWithDescription() throws Exception {
        List<LogRecord> got = capture("spacecolony.debug", () -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.applyDebugEdit("set credits", w -> w.credits = 7);
        });
        assertTrue(got.stream().anyMatch(r -> r.getLevel() == Level.INFO && r.getMessage().contains("set credits")));
    }

    @Test void reset_isLoggedWithSeed() throws Exception {
        List<LogRecord> got = capture("spacecolony.engine", () -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.reset(WorldGenerator.generate(77L));
        });
        assertTrue(got.stream().anyMatch(r -> r.getMessage().contains("seed=77")));
    }
}
