package spacecolony.sim;

import java.util.EnumSet;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import spacecolony.save.SaveFile;
import spacecolony.sim.commands.SetRandomEventsCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class RandomEventsSwitchTest {
    private static final EnumSet<EventKind> RANDOM = EnumSet.of(EventKind.METEOR_STRIKE,
        EventKind.SOLAR_FLARE, EventKind.EQUIPMENT_FAILURE, EventKind.DISEASE_OUTBREAK);

    /** Random events emitted while advancing {@code w} by {@code ticks}. */
    private static long randomEvents(World w, int ticks) {
        Simulator sim = new Simulator();
        long n = 0;
        for (int i = 0; i < ticks; i++) {
            Event last = w.recentEvents.peekLast();
            sim.advance(w);
            for (Iterator<Event> it = w.recentEvents.descendingIterator(); it.hasNext(); ) {
                Event e = it.next();
                if (e == last) break;
                if (RANDOM.contains(e.kind())) n++;
            }
        }
        return n;
    }

    @Test void on_byDefault_rollsEvents() {
        assertTrue(randomEvents(WorldGenerator.generate(1L), 5_000) > 0);
    }

    @Test void off_rollsNone() {
        World w = WorldGenerator.generate(1L);
        w.randomEventsEnabled = false;
        assertEquals(0, randomEvents(w, 5_000));
    }

    @Test void command_turnsThemBackOn() {
        World w = WorldGenerator.generate(1L);
        w.randomEventsEnabled = false;
        Simulator sim = new Simulator();
        sim.enqueue(new SetRandomEventsCommand(true));
        sim.advance(w);
        assertTrue(w.randomEventsEnabled);
    }

    @Test void off_isDeterministic() {
        World a = WorldGenerator.generate(3L), b = WorldGenerator.generate(3L);
        a.randomEventsEnabled = false;
        b.randomEventsEnabled = false;
        Simulator sa = new Simulator(), sb = new Simulator();
        for (int i = 0; i < 500; i++) { sa.advance(a); sb.advance(b); }
        assertEquals(SaveFile.toJson(a), SaveFile.toJson(b));
    }
}
