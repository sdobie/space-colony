package spacecolony.engine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class GameLoopTest {

    @Test
    void paused_doesNotTick() throws Exception {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        GameLoop[] loop = new GameLoop[1];
        // Constructing the loop registers an engine listener, so it must happen on the EDT.
        Edt.run(() -> {
            loop[0] = new GameLoop(engine);
            engine.setSpeed(Speed.PAUSED);
        });

        long before = engine.world().tick;
        // Deliberately sleep OFF the EDT: blocking the EDT here would stop the Swing Timer
        // from firing on its own and the test would pass even if pausing were broken.
        Thread.sleep(150);
        assertEquals(before, engine.world().tick);

        Edt.run(() -> loop[0].dispose());
    }

    @Test
    void x16_ticksWithinReasonableTime() throws Exception {
        // X16 = 32ms/tick; running 3 ticks should take ~96ms, certainly <2s.
        Engine engine = new Engine(WorldGenerator.generate(1L));
        GameLoop[] loop = new GameLoop[1];
        CountDownLatch threeTicks = new CountDownLatch(3);
        Edt.run(() -> {
            loop[0] = new GameLoop(engine);
            engine.addListener(e -> { if (e instanceof EngineEvent.WorldChanged) threeTicks.countDown(); });
            engine.setSpeed(Speed.X16);
        });

        // Await OFF the EDT: ticks are delivered on the EDT, so blocking it would deadlock.
        assertTrue(threeTicks.await(2, TimeUnit.SECONDS), "Expected 3 ticks within 2s on X16");

        Edt.run(() -> loop[0].dispose());
    }
}
