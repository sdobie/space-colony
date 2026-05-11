package spacecolony;

import spacecolony.sim.*;
import spacecolony.world.WorldGenerator;

public class Main {
    public static void main(String[] args) {
        long seed = 42L;
        long ticks = 365L; // simulate one year
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[i + 1]);
            if (args[i].equals("--ticks") && i + 1 < args.length) ticks = Long.parseLong(args[i + 1]);
        }
        World w = WorldGenerator.generate(seed);
        Simulator sim = new Simulator();
        long start = System.currentTimeMillis();
        for (long t = 0; t < ticks; t++) sim.advance(w);
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Simulated %d ticks (seed=%d) in %d ms%n", ticks, seed, elapsed);
        System.out.printf("Tick: %d, credits: %d, ships: %d%n", w.tick, w.credits, w.ships.size());
        for (Body b : w.bodies) {
            for (Site s : b.sites) {
                System.out.printf("  %s @ (%.2f, %.2f): pop=%d morale=%.2f food=%.0f water=%.0f%n",
                    s.name, s.lat, s.lon, s.population, s.morale,
                    s.stockpile.get(Resource.FOOD), s.stockpile.get(Resource.WATER));
            }
        }
        System.out.println("Recent events:");
        for (Event e : w.recentEvents) {
            System.out.printf("  [t=%d %s] %s: %s%n", e.tick(), e.severity(), e.kind(), e.message());
        }
    }
}
