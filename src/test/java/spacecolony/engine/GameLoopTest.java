package spacecolony.engine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class GameLoopTest {

    @Test
    void paused_doesNotTick() throws Exception {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        GameLoop loop = new GameLoop(engine);
        engine.setSpeed(Speed.PAUSED);
        long before = engine.world().tick;
        Thread.sleep(150);
        assertEquals(before, engine.world().tick);
        loop.dispose();
    }

    @Test
    void x16_ticksWithinReasonableTime() throws Exception {
        // X16 = 32ms/tick; running 3 ticks should take ~96ms, certainly <2s.
        Engine engine = new Engine(WorldGenerator.generate(1L));
        GameLoop loop = new GameLoop(engine);
        CountDownLatch threeTicks = new CountDownLatch(3);
        engine.addListener(e -> { if (e instanceof EngineEvent.WorldChanged) threeTicks.countDown(); });
        SwingUtilities.invokeAndWait(() -> engine.setSpeed(Speed.X16));
        assertTrue(threeTicks.await(2, TimeUnit.SECONDS), "Expected 3 ticks within 2s on X16");
        loop.dispose();
    }
}
