package spacecolony.debug;

import java.util.function.BiConsumer;
import spacecolony.save.IncompatibleSaveException;
import spacecolony.save.SaveFile;
import spacecolony.sim.Simulator;
import spacecolony.sim.World;

/**
 * Spec §8 determinism check: restore two copies from one snapshot, advance each by the
 * same number of ticks, and compare their save envelopes. The live world is never
 * advanced, so the string overload can run off the EDT.
 */
public final class DeterminismCheck {
    private DeterminismCheck() {}

    public record Result(boolean match, int ticks, long millis, String firstDiff) {}

    /** Convenience for tests: snapshots {@code live} (must be called on the thread that owns it). */
    public static Result run(World live, int ticks) throws IncompatibleSaveException {
        return run(SaveFile.toJson(live), ticks, (i, w) -> {});
    }

    /** Off-EDT entry point: works only on copies restored from {@code snapshot}. */
    public static Result run(String snapshot, int ticks) throws IncompatibleSaveException {
        return run(snapshot, ticks, (i, w) -> {});
    }

    /** Test seam: {@code perturb} sees each restored copy (index 0 or 1) before it advances. */
    static Result run(String s0, int ticks, BiConsumer<Integer, World> perturb) throws IncompatibleSaveException {
        long t0 = System.nanoTime();
        String[] out = new String[2];
        for (int i = 0; i < 2; i++) {
            World copy = SaveFile.fromJson(s0);
            perturb.accept(i, copy);
            Simulator sim = new Simulator();
            for (int t = 0; t < ticks; t++) sim.advance(copy);
            out[i] = SaveFile.toJson(copy);
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (out[0].equals(out[1])) return new Result(true, ticks, ms, null);
        return new Result(false, ticks, ms, firstDiff(out[0], out[1]));
    }

    /** "line N: <a> ≠ <b>" for the first differing line. */
    static String firstDiff(String a, String b) {
        String[] la = a.split("\n", -1);
        String[] lb = b.split("\n", -1);
        int n = Math.max(la.length, lb.length);
        for (int i = 0; i < n; i++) {
            String x = i < la.length ? la[i].trim() : "<end>";
            String y = i < lb.length ? lb[i].trim() : "<end>";
            if (!x.equals(y)) return "line " + (i + 1) + ": " + x + " ≠ " + y;
        }
        return null;
    }
}
