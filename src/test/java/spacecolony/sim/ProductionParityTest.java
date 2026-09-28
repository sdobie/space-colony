package spacecolony.sim;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the sim's production numbers. The fixture was captured from the pre-Plan-7
 * ProductionPhase; any change to it means the economy refactor changed the game.
 * Regenerate only for a deliberate balance change: run {@link #main} from the repo root.
 */
class ProductionParityTest {
    static final long[] SEEDS = {42L, 7777L, 12345L};
    static final int TICKS = 2000;
    static final String FIXTURE = "/parity/production-parity.txt";

    static List<String> run() {
        List<String> out = new ArrayList<>();
        for (long seed : SEEDS) {
            EconomyScenario sc = new EconomyScenario(seed);
            for (int i = 1; i <= TICKS; i++) {
                sc.step();
                if (i % 100 == 0 || i == 25 || i == 50) snapshot(out, seed + "@" + i, sc.world);
            }
        }
        return out;
    }

    private static void snapshot(List<String> out, String key, World w) {
        for (Body b : w.bodies) for (Site s : b.sites) {
            for (Resource r : Resource.values())
                out.add(key + " " + s.id + " " + r + " " + String.format("%.12e", s.stockpile.get(r)));
            out.add(key + " " + s.id + " population " + s.population);
            out.add(key + " " + s.id + " morale " + String.format("%.12e", s.morale));
        }
        for (Ship sh : w.ships)
            out.add(key + " ship " + sh.id + " " + sh.state + " fuel " + String.format("%.12e", sh.fuel));
        out.add(key + " research " + String.format("%.12e", w.tech.accumulatedPoints) + " " + w.tech.researched.size());
    }

    @Test
    void productionNumbersMatchFixture() throws IOException {
        List<String> expected;
        try (InputStream in = ProductionParityTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "missing fixture " + FIXTURE);
            expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        List<String> actual = run();
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++)
            assertEquals(expected.get(i), actual.get(i), "first mismatch at line " + (i + 1));
        assertEquals(expected.size(), actual.size(), "line count");
    }

    public static void main(String[] args) throws IOException {
        Files.write(Path.of("src/test/resources" + FIXTURE), run(), StandardCharsets.UTF_8);
    }
}
