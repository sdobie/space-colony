package spacecolony.playtest;

import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.save.SaveFile;
import spacecolony.save.SaveSlots;
import spacecolony.sim.*;
import spacecolony.sim.commands.*;
import spacecolony.sim.phases.CommandPhase;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/**
 * Continuous play-tester: runs the real Space Colony frame, makes one move per interval
 * (a command plus one tick, or just a tick) and validates every move.
 *
 * <p>Args (all optional): seed intervalSeconds runDir [maxMoves]; defaults are a random seed,
 * 60 seconds and build/continuous-playtest. Writes MOVE lines to stdout and VIOLATION lines to
 * runDir/violations.log. Starts a fresh seed after 30 moves with no population left. Each seed
 * plays one {@link Style}, picked from the seed (or forced with -Dpt.style=NAME), so the same
 * seed replays the same game while different seeds exercise different parts of the game. Run it
 * with {@code ./gradlew continuousPlayTest} (add {@code --args="SEED 0.02 DIR 1500"} for a
 * fast shake-out).
 */
public class ContinuousPlayTester {

    /** How the script plays a seed. */
    enum Style {
        /** Keep every colony fed, powered and growing; one colonizer at a time. */
        BALANCED,
        /** Ships before colony needs: two colonizers, three haulers, up to eight colonies. */
        EXPANDER,
        /** No colonizers; extra farms and power plants, and upgrades whenever it can. */
        INDUSTRIALIST,
        /** Research labs before anything else, up to three at the hub. */
        SCIENTIST,
        /** Never repairs or builds farms or habitats, so colonies run down and collapse. */
        NEGLECTFUL,
        /** Four moves in ten are random: any building anywhere, demolitions, cancels, research switches. */
        CHAOTIC
    }

    static final double EPS = 1e-6;
    static Engine engine;
    static SpaceColonyFrame frame;
    static Path runDir;
    static long seed;
    static Random rng;
    static Style style;
    static int move = 0;
    static int deadMoves = 0;
    static int shipSeq = 0;
    static int siteSeq = 0;
    static final List<String> uncaught = Collections.synchronizedList(new ArrayList<>());
    static final Set<String> reported = new HashSet<>();

    public static void main(String[] args) throws Exception {
        seed = args.length > 0 ? Long.parseLong(args[0]) : new Random().nextLong() & 0xffffffffL;
        double interval = args.length > 1 ? Double.parseDouble(args[1]) : 60.0;
        runDir = Path.of(args.length > 2 ? args[2] : "build/continuous-playtest");
        long maxMoves = args.length > 3 ? Long.parseLong(args[3]) : Long.MAX_VALUE;
        Files.createDirectories(runDir);
        Path saves = runDir.resolve("saves");
        Files.createDirectories(saves);
        System.setProperty(SaveSlots.DIR_PROPERTY, saves.toString());
        Files.writeString(runDir.resolve("violations.log"), "", StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> uncaught.add(t.getName() + ": " + stack(e)));
        rng = new Random(seed ^ 0x5eedL);
        style = pickStyle(seed);
        SwingUtilities.invokeAndWait(() -> {
            engine = new Engine(WorldGenerator.generate(seed));
            frame = new SpaceColonyFrame(engine);
            frame.setSize(1400, 900);
            frame.setVisible(true);
        });
        log("START seed=" + seed + " style=" + style + " interval=" + interval + "s main=" + System.getProperty("pt.rev", "?"));

        while (move < maxMoves) {
            long t0 = System.nanoTime();
            move++;
            try {
                SwingUtilities.invokeAndWait(ContinuousPlayTester::doMove);
            } catch (Exception e) {
                violation("move threw: " + stack(e.getCause() != null ? e.getCause() : e));
            }
            drainUncaught();
            long sleep = (long) (interval * 1000) - (System.nanoTime() - t0) / 1_000_000;
            if (sleep > 0) Thread.sleep(sleep);
        }
        log("DONE moves=" + move);
        System.exit(0);
    }

    // ---------------------------------------------------------------- one move

    record Plan(Command cmd, String desc, Site costSite, Map<Resource, Double> expectDelta) {}

    static void doMove() {
        World w = engine.world();
        long before = w.tick;
        String json0 = SaveFile.toJson(w);
        Plan plan = choose(w);
        int eventsBefore = w.recentEvents.size();
        List<Event> evBefore = new ArrayList<>(w.recentEvents);

        if (plan != null) engine.enqueue(plan.cmd);
        engine.tick();

        List<Event> fresh = newEvents(evBefore, w.recentEvents);
        String desc = plan == null ? "tick" : plan.desc;
        log("MOVE " + move + " tick " + before + "->" + w.tick + " " + desc);

        if (w.tick != before + 1) violation("tick advanced " + before + "->" + w.tick);
        if (plan != null) {
            for (Event e : fresh)
                if (e.kind() == EventKind.COMMAND_REJECTED)
                    violation("command the harness judged valid was rejected: " + desc + " -> " + e.message());
        }
        checkInvariants(w);
        String json1 = SaveFile.toJson(w);
        checkSaveRoundTrip(json1);
        checkShadow(json0, plan, json1);
        checkPaint();
        if (move % 50 == 0) {
            StringBuilder sb = new StringBuilder("STATUS move=" + move + " tick=" + w.tick + " ships=" + w.ships.size());
            for (Site s : allSites(w))
                sb.append(String.format(" | %s pop=%d/%d F=%.0f W=%.0f I=%.0f M=%.0f O=%.0f FU=%.0f b=%d", s.id, s.population, s.populationCap,
                    stock(s, Resource.FOOD), stock(s, Resource.WATER), stock(s, Resource.ICE), stock(s, Resource.METAL),
                    stock(s, Resource.ORE), stock(s, Resource.FUEL), s.buildings.size()));
            log(sb.toString());
        }
        checkCollapse(w);
    }

    // ---------------------------------------------------------------- strategy

    static Plan choose(World w) {
        List<Site> sites = allSites(w);
        if (sites.isEmpty()) return null;
        Site hub = w.findSite("site-earth-hub");
        if (hub == null) hub = sites.get(0);

        // 0. Repair broken buildings at populated sites.
        for (Site s : sites) {
            if (s.population <= 0 || style == Style.NEGLECTFUL) continue;
            for (Building b : s.buildings) {
                if (b.level > 0 && !b.enabled && Construction.whyNotRepair(s, b) == null)
                    return costed(new RepairBuildingCommand(s.id, b.id), "repair " + b.type + " #" + b.id + " at " + s.id,
                        s, Construction.repairCost(b.type).resources(), -1);
            }
        }
        if (style == Style.CHAOTIC && rng.nextDouble() < 0.4) {
            Plan p = chaos(w, sites);
            if (p != null) return p;
        }
        if (style == Style.SCIENTIST && count(hub, BuildingType.RESEARCH_LAB) < 3
                && underway(hub, BuildingType.RESEARCH_LAB) == 0) {
            Plan p = build(hub, BuildingType.RESEARCH_LAB, "scientist wants labs");
            if (p != null) return p;
        }
        // 1. Hub refinery first: METAL only comes from refining.
        if (count(hub, BuildingType.REFINERY) == 0) {
            Plan p = build(hub, BuildingType.REFINERY, "METAL needs a refinery");
            if (p != null) return p;
        }
        if (style == Style.EXPANDER) {
            Plan p = ships(w, hub, sites);
            if (p != null) return p;
        }
        // 2. Keep each colony fed, watered and powered.
        for (Site s : sites) {
            Plan p = needs(w, s, s == hub);
            if (p != null) return p;
        }
        // 3. Research.
        if (w.tech.activeId == null) {
            for (Tech t : sortedTechs()) {
                if (!w.tech.researched.contains(t.id()) && TechAvailability.prereqsMet(w.tech, t))
                    return plain(new QueueResearchCommand(t.id()), "research " + t.id());
            }
        }
        // 4. Ships.
        Plan sp = ships(w, hub, sites);
        if (sp != null) return sp;
        // 5. Occasional exploration of the rest of the command set.
        if (rng.nextDouble() < (style == Style.INDUSTRIALIST ? 0.4 : 0.06)) {
            Plan p = explore(w, sites);
            if (p != null) return p;
        }
        return null;
    }

    static Plan needs(World w, Site s, boolean isHub) {
        double food = stock(s, Resource.FOOD), water = stock(s, Resource.WATER), metal = stock(s, Resource.METAL);
        var day = s.lastDay;
        double foodNet = day == null ? 0 : day.net(Resource.FOOD);
        double waterNet = day == null ? 0 : day.net(Resource.WATER);
        double metalNet = day == null ? 0 : day.net(Resource.METAL);
        boolean powerShort = day != null && day.powerUsed > day.powerMade + EPS;
        int free = Construction.SLOTS - s.buildings.size();
        if (powerShort && underway(s, BuildingType.POWER_PLANT) == 0 && count(s, BuildingType.POWER_PLANT) < (style == Style.INDUSTRIALIST ? 4 : 2)) {
            Plan p = build(s, BuildingType.POWER_PLANT, "power short");
            if (p != null) return p;
        }
        if (count(s, BuildingType.MINE) == 0) {
            Plan p = build(s, BuildingType.MINE, "no mine");
            if (p != null) return p;
        }
        if (count(s, BuildingType.REFINERY) == 0 && (waterNet < 0 || isHub)) {
            Plan p = build(s, BuildingType.REFINERY, "no refinery");
            if (p != null) return p;
        }
        // Can't afford a refinery: a furnace makes metal slowly without costing any.
        if (count(s, BuildingType.REFINERY) == 0 && count(s, BuildingType.FURNACE) == 0
                && Construction.whyNotBuild(s, BuildingType.REFINERY) != null) {
            Plan p = build(s, BuildingType.FURNACE, "no METAL for a refinery");
            if (p != null) return p;
        }
        boolean neglect = style == Style.NEGLECTFUL;
        if (!neglect && foodNet < 0 && food < 200 && underway(s, BuildingType.FARM) == 0
                && count(s, BuildingType.FARM) < (style == Style.INDUSTRIALIST ? 4 : 3)) {
            Plan p = build(s, BuildingType.FARM, "FOOD falling");
            if (p != null) return p;
        }
        if (waterNet < 0 && water < 200 && stock(s, Resource.ICE) < 50 && underway(s, BuildingType.MINE) == 0 && count(s, BuildingType.MINE) < 3) {
            Plan p = build(s, BuildingType.MINE, "WATER falling, little ICE");
            if (p != null) return p;
        }
        if (isHub && free > 2) {
            for (BuildingType t : List.of(BuildingType.RESEARCH_LAB, BuildingType.SHIPYARD, BuildingType.FACTORY)) {
                if (count(s, t) == 0 && metal > 60) {
                    Plan p = build(s, t, "hub needs a " + t);
                    if (p != null) return p;
                }
            }
        }
        if (!neglect && free > 1 && s.population >= s.populationCap * 0.9 && underway(s, BuildingType.HABITAT) == 0 && food > 100 && water > 100) {
            Plan p = build(s, BuildingType.HABITAT, "near pop cap");
            if (p != null) return p;
        }
        if (metalNet <= 0 && metal < 50 && count(s, BuildingType.MINE) < 2 && underway(s, BuildingType.MINE) == 0) {
            Plan p = build(s, BuildingType.MINE, "METAL low");
            if (p != null) return p;
        }
        return null;
    }

    static Plan ships(World w, Site hub, List<Site> sites) {
        boolean yard = hub.buildings.stream().anyMatch(b -> b.type == BuildingType.SHIPYARD && b.isOperational());
        long explorers = w.ships.stream().filter(s -> s.shipClass == ShipClass.EXPLORER).count();
        long haulers = w.ships.stream().filter(s -> s.shipClass == ShipClass.HAULER).count();
        long colonizers = w.ships.stream().filter(s -> s.shipClass == ShipClass.COLONIZER).count();
        if (yard) {
            if (explorers == 0) return newShip(w, hub, ShipClass.EXPLORER);
            boolean expand = style == Style.EXPANDER;
            int maxColonizers = expand ? 2 : style == Style.INDUSTRIALIST ? 0 : 1;
            if (colonizers < maxColonizers && sites.size() < (expand ? 8 : 5)
                    && stock(hub, Resource.FUEL) > (expand ? 30 : 60)) return newShip(w, hub, ShipClass.COLONIZER);
            if (haulers < (expand ? 3 : 2) && sites.size() > 1) return newShip(w, hub, ShipClass.HAULER);
        }
        for (Ship s : w.ships) {
            if (s.state != ShipState.IDLE) continue;
            Plan p = switch (s.shipClass) {
                case COLONIZER -> colonizer(w, s, hub);
                case EXPLORER -> explorer(w, s, sites);
                case HAULER, TANKER -> hauler(w, s, sites, hub);
            };
            if (p != null) return p;
        }
        return null;
    }

    static Plan colonizer(World w, Ship s, Site hub) {
        if (s.orbitingBodyId != null) {
            Body b = w.findBody(s.orbitingBodyId);
            String id = "pt-site-" + (++siteSeq) + "-" + seed % 1000;
            return plain(new BuildSiteCommand(id, b.name + " " + siteSeq, b.id, rng.nextDouble() * 120 - 60,
                rng.nextDouble() * 360 - 180, s.id), "found " + id + " on " + b.id + " with " + s.id);
        }
        Site at = w.findSite(s.currentSiteId);
        if (at == null) return null;
        Body target = null;
        double best = Double.MAX_VALUE;
        double[] here = OrbitalGeometry.bodyPosition(w, at.bodyId, w.tick);
        for (Body b : w.bodies) {
            if (!b.sites.isEmpty() || b.type == BodyType.GAS_GIANT) continue;
            double[] p = OrbitalGeometry.bodyPosition(w, b.id, w.tick);
            double d = Math.hypot(p[0] - here[0], p[1] - here[1]);
            if (d < best) { best = d; target = b; }
        }
        if (target == null) return null;
        Map<Resource, Double> m = new EnumMap<>(Resource.class);
        put(m, at, Resource.METAL, 40);
        put(m, at, Resource.WATER, 20);
        put(m, at, Resource.FOOD, 30);
        if (CommandPhase.fuelShortfall(w, s, target.id, m) != null) return null;
        return plain(DispatchShipCommand.toBody(s.id, target.id, m), "send colonizer " + s.id + " to " + target.id + " with " + m);
    }

    static Plan explorer(World w, Ship s, List<Site> sites) {
        String fromBody = s.orbitingBodyId != null ? s.orbitingBodyId : bodyOf(w, s.currentSiteId);
        if (fromBody == null) return null;
        double[] here = OrbitalGeometry.bodyPosition(w, fromBody, w.tick);
        Body target = null;
        double best = Double.MAX_VALUE;
        for (Body b : w.bodies) {
            if (w.isSurveyed(b.id) || b.id.equals(fromBody)) continue;
            double[] p = OrbitalGeometry.bodyPosition(w, b.id, w.tick);
            double d = Math.hypot(p[0] - here[0], p[1] - here[1]);
            if (d < best) { best = d; target = b; }
        }
        Map<Resource, Double> none = Map.of();
        if (target != null && CommandPhase.fuelShortfall(w, s, target.id, none) == null) {
            // From orbit keep enough in the tank to reach some colony afterwards.
            if (s.orbitingBodyId == null || s.fuel > s.shipClass.tankCap() * 0.6)
                return plain(DispatchShipCommand.toBody(s.id, target.id, none), "send explorer " + s.id + " to survey " + target.id);
        }
        if (s.orbitingBodyId != null) {
            for (Site site : sites) {
                if (CommandPhase.fuelShortfall(w, s, site.bodyId, none) == null)
                    return plain(new DispatchShipCommand(s.id, site.id, none), "send explorer " + s.id + " home to " + site.id);
            }
        }
        return null;
    }

    static Plan hauler(World w, Ship s, List<Site> sites, Site hub) {
        Site at = w.findSite(s.currentSiteId);
        if (at == null || sites.size() < 2) return null;
        for (Resource r : List.of(Resource.ICE, Resource.WATER, Resource.FOOD, Resource.METAL, Resource.FUEL)) {
            double have = stock(at, r);
            if (have < 150) continue;
            for (Site d : sites) {
                if (d == at || stock(d, r) > 60) continue;
                if (r == Resource.ICE && count(d, BuildingType.REFINERY) == 0) continue;
                Map<Resource, Double> m = new EnumMap<>(Resource.class);
                m.put(r, Math.floor(Math.min(s.shipClass.cargoCap(), have - 100)));
                if (CommandPhase.fuelShortfall(w, s, d.bodyId, m) != null) continue;
                return plain(new DispatchShipCommand(s.id, d.id, m), "haul " + m + " " + at.id + " -> " + d.id + " with " + s.id);
            }
        }
        if (at != hub && rng.nextDouble() < 0.2 && CommandPhase.fuelShortfall(w, s, hub.bodyId, Map.of()) == null)
            return plain(new DispatchShipCommand(s.id, hub.id, Map.of()), "send hauler " + s.id + " back to " + hub.id);
        return null;
    }

    static Plan explore(World w, List<Site> sites) {
        Site s = sites.get(rng.nextInt(sites.size()));
        if (s.buildings.isEmpty()) return null;
        Building b = s.buildings.get(rng.nextInt(s.buildings.size()));
        int pick = style == Style.INDUSTRIALIST ? 0 : rng.nextInt(10);
        if (pick < 5 && Construction.whyNotUpgrade(s, b) == null)
            return costed(new UpgradeBuildingCommand(s.id, b.id), "explore: upgrade " + b.type + " #" + b.id + " at " + s.id + " to L" + (b.level + 1),
                s, Construction.upgradeCost(b.type, b.level + 1).resources(), -1);
        if (pick < 8 && b.isUnderConstruction() && Construction.whyNotCancel(b) == null)
            return costed(new CancelConstructionCommand(s.id, b.id), "explore: cancel " + b.type + " #" + b.id + " at " + s.id,
                s, Construction.cancelRefund(b), +1);
        if (pick == 8 && b.type == BuildingType.HABITAT && count(s, BuildingType.HABITAT) > 2
                && s.population < s.populationCap * 0.5 && Construction.whyNotDemolish(b) == null)
            return costed(new DemolishBuildingCommand(s.id, b.id), "explore: demolish spare HABITAT #" + b.id + " at " + s.id,
                s, Construction.demolishRefund(b), +1);
        if (pick == 9) {
            List<Tech> avail = new ArrayList<>();
            for (Tech t : sortedTechs())
                if (!w.tech.researched.contains(t.id()) && TechAvailability.prereqsMet(w.tech, t) && !t.id().equals(w.tech.activeId)) avail.add(t);
            if (!avail.isEmpty()) {
                Tech t = avail.get(rng.nextInt(avail.size()));
                return plain(new QueueResearchCommand(t.id()), "explore: switch research to " + t.id());
            }
        }
        return null;
    }

    /** A random command: any building at any colony, or demolishing any building, else an explore move. */
    static Plan chaos(World w, List<Site> sites) {
        Site s = sites.get(rng.nextInt(sites.size()));
        int pick = rng.nextInt(3);
        if (pick == 0) {
            BuildingType t = BuildingType.values()[rng.nextInt(BuildingType.values().length)];
            Plan p = build(s, t, "chaos");
            if (p != null) return p;
        }
        if (pick == 1 && !s.buildings.isEmpty()) {
            Building b = s.buildings.get(rng.nextInt(s.buildings.size()));
            if (Construction.whyNotDemolish(b) == null)
                return costed(new DemolishBuildingCommand(s.id, b.id), "chaos: demolish " + b.type + " #" + b.id + " at " + s.id,
                    s, Construction.demolishRefund(b), +1);
        }
        return explore(w, sites);
    }

    static Style pickStyle(long seed) {
        String forced = System.getProperty("pt.style");
        if (forced != null) return Style.valueOf(forced.toUpperCase(Locale.ROOT));
        return Style.values()[(int) Long.remainderUnsigned(seed, Style.values().length)];
    }

    static Plan newShip(World w, Site hub, ShipClass c) {
        String id = "pt-ship-" + (++shipSeq);
        while (w.findShip(id) != null) id = "pt-ship-" + (++shipSeq);
        return plain(new BuildShipCommand(id, c.name().charAt(0) + c.name().substring(1).toLowerCase() + " " + shipSeq, c, hub.id),
            "build " + c + " " + id + " at " + hub.id);
    }

    static Plan build(Site s, BuildingType t, String why) {
        if (Construction.whyNotBuild(s, t) != null) return null;
        return costed(new BuildBuildingCommand(s.id, t), "build " + t + " at " + s.id + " (" + why + ")",
            s, Construction.buildCost(t).resources(), -1);
    }

    static Plan plain(Command c, String desc) { return new Plan(c, desc, null, null); }

    static Plan costed(Command c, String desc, Site s, Map<Resource, Double> amounts, int sign) {
        Map<Resource, Double> d = new EnumMap<>(Resource.class);
        amounts.forEach((r, v) -> d.put(r, sign * v));
        return new Plan(c, desc, s, d);
    }

    // ---------------------------------------------------------------- checks

    static final Map<String, Integer> lastPop = new HashMap<>();

    static void checkInvariants(World w) {
        if (!Double.isFinite(w.credits)) violation("credits not finite: " + w.credits);
        Set<String> siteIds = new HashSet<>();
        for (Body b : w.bodies) {
            for (Site s : b.sites) {
                String at = s.id + "@t" + w.tick;
                if (!siteIds.add(s.id)) violation("duplicate site id " + s.id);
                if (!s.bodyId.equals(b.id)) violation(at + " bodyId " + s.bodyId + " but listed under " + b.id);
                if (s.population < 0) violation(at + " population " + s.population);
                // Losing housing (a damaged or demolished habitat) can leave population over the cap; the game
                // only stops growth there, so the bug is population growing while already at or over the cap.
                Integer prevPop = lastPop.put(s.id, s.population);
                if (s.population > s.populationCap && prevPop != null && s.population > prevPop)
                    violation(at + " population grew " + prevPop + "->" + s.population + " over cap " + s.populationCap);
                double ceil = TechEffects.moraleCeiling(w.tech);
                if (!(s.morale >= -EPS && s.morale <= ceil + EPS)) violation(at + " morale " + s.morale + " outside 0.." + ceil);
                for (var e : s.stockpile.entrySet()) {
                    double v = e.getValue(), cap = s.stockpileCap.getOrDefault(e.getKey(), 1000.0);
                    if (!Double.isFinite(v) || v < -EPS) violation(at + " " + e.getKey() + " = " + v);
                    if (v > cap + 1e-3) violation(at + " " + e.getKey() + " " + v + " over cap " + cap);
                }
                if (s.buildings.size() > Construction.SLOTS) violation(at + " " + s.buildings.size() + " buildings > " + Construction.SLOTS + " slots");
                Set<Integer> ids = new HashSet<>();
                for (Building bl : s.buildings) {
                    if (!ids.add(bl.id)) violation(at + " duplicate building id " + bl.id);
                    if (bl.level < 0 || bl.level > Construction.MAX_LEVEL) violation(at + " building #" + bl.id + " level " + bl.level);
                    if (bl.daysLeft < 0) violation(at + " building #" + bl.id + " daysLeft " + bl.daysLeft);
                    if (bl.level == 0 && !bl.isUnderConstruction()) violation(at + " level-0 " + bl.type + " #" + bl.id + " not under construction");
                }
            }
        }
        Set<String> shipIds = new HashSet<>();
        for (Ship s : w.ships) {
            String at = s.id + "@t" + w.tick;
            if (!shipIds.add(s.id)) violation("duplicate ship id " + s.id);
            if (!Double.isFinite(s.fuel) || s.fuel < -EPS) violation(at + " fuel " + s.fuel);
            if (s.shipClass.tankCap() > 0 && s.fuel > s.shipClass.tankCap() + EPS && s.state == ShipState.IN_TRANSIT)
                violation(at + " fuel " + s.fuel + " over tank " + s.shipClass.tankCap());
            for (var e : s.cargo.entrySet())
                if (!Double.isFinite(e.getValue()) || e.getValue() < -EPS) violation(at + " cargo " + e.getKey() + " = " + e.getValue());
            if (s.cargoMass() > s.shipClass.cargoCap() + 1e-3) violation(at + " cargo " + s.cargoMass() + " over cap " + s.shipClass.cargoCap());
            switch (s.state) {
                case IN_TRANSIT -> {
                    if (s.transit == null) violation(at + " IN_TRANSIT with no transit");
                    else if (s.transit.arrivalTick() != Transit.PENDING_ARRIVAL_TICK && s.transit.arrivalTick() < w.tick)
                        violation(at + " still in transit past arrival tick " + s.transit.arrivalTick());
                }
                case IDLE, LOADING, UNLOADING -> {
                    boolean docked = s.currentSiteId != null && w.findSite(s.currentSiteId) != null;
                    boolean orbit = s.orbitingBodyId != null && w.findBody(s.orbitingBodyId) != null;
                    if (!docked && !orbit) violation(at + " " + s.state + " but neither docked nor in orbit (site=" + s.currentSiteId + ", orbit=" + s.orbitingBodyId + ")");
                    if (s.state == ShipState.LOADING && s.transit == null) violation(at + " LOADING with no transit");
                }
            }
        }
        if (w.tech.activeId != null && w.tech.researched.contains(w.tech.activeId))
            violation("active research " + w.tech.activeId + " already researched");
        if (w.tech.accumulatedPoints < -EPS || !Double.isFinite(w.tech.accumulatedPoints))
            violation("research points " + w.tech.accumulatedPoints);
        for (Body b : w.bodies)
            if (!b.sites.isEmpty() && !w.isSurveyed(b.id)) violation("settled body " + b.id + " not surveyed");
    }

    static void checkSaveRoundTrip(String json) {
        try {
            String again = SaveFile.toJson(SaveFile.fromJson(json));
            if (!again.equals(json)) violation("save round trip changed the world: " + firstDiff(json, again));
        } catch (Exception e) {
            violation("save round trip threw: " + stack(e));
        }
    }

    /** Replays the move headlessly from the pre-move save; the UI must not change the outcome. */
    static void checkShadow(String json0, Plan plan, String json1) {
        try {
            World with = SaveFile.fromJson(json0);
            Simulator sim = new Simulator();
            if (plan != null) sim.enqueue(plan.cmd);
            sim.advance(with);
            String shadow = SaveFile.toJson(with);
            if (!shadow.equals(json1)) violation("headless replay differs from the live game: " + firstDiff(shadow, json1));
            if (plan != null && plan.costSite != null) {
                // Measure right after the command phase: once production runs, the building's own flows
                // (a demolished refinery's metal) and stock-limited consumers (a factory fed by a refund) blur it.
                World before = SaveFile.fromJson(json0), after = SaveFile.fromJson(json0);
                CommandPhase.drain(after, new ArrayDeque<>(List.of(plan.cmd)));
                Site a = after.findSite(plan.costSite.id), b = before.findSite(plan.costSite.id);
                for (var e : plan.expectDelta.entrySet()) {
                    Resource r = e.getKey();
                    double va = a.stockpile.getOrDefault(r, 0.0), vb = b.stockpile.getOrDefault(r, 0.0);
                    double cap = a.stockpileCap.getOrDefault(r, 1000.0);
                    if (va > cap - 1 || vb > cap - 1) continue; // clipped at the cap, can't compare
                    double got = va - vb;
                    if (Math.abs(got - e.getValue()) > 1e-6)
                        violation("conservation: " + plan.desc + " changed " + r + " by " + fmt(got) + ", expected " + fmt(e.getValue()));
                }
            }
        } catch (Exception e) {
            violation("headless replay threw: " + stack(e));
        }
    }

    static void checkPaint() {
        try {
            int wd = Math.max(1, frame.getRootPane().getWidth()), ht = Math.max(1, frame.getRootPane().getHeight());
            BufferedImage img = new BufferedImage(wd, ht, BufferedImage.TYPE_INT_ARGB);
            var g = img.createGraphics();
            frame.getRootPane().paint(g);
            g.dispose();
        } catch (Exception e) {
            violation("painting the frame threw: " + stack(e));
        }
    }

    static void checkCollapse(World w) {
        int pop = allSites(w).stream().mapToInt(s -> s.population).sum();
        deadMoves = pop == 0 ? deadMoves + 1 : 0;
        if (deadMoves >= 30) {
            log("COLLAPSE seed=" + seed + " at tick " + w.tick + "; starting a new seed");
            seed = new Random().nextLong() & 0xffffffffL;
            rng = new Random(seed ^ 0x5eedL);
            style = pickStyle(seed);
            deadMoves = 0;
            engine.reset(WorldGenerator.generate(seed));
            log("START seed=" + seed + " style=" + style + " (restart)");
        }
    }

    // ---------------------------------------------------------------- helpers

    static List<Event> newEvents(List<Event> before, Collection<Event> after) {
        List<Event> out = new ArrayList<>(after);
        // recentEvents is a bounded deque: drop the prefix we had already seen.
        int overlap = 0;
        for (int k = Math.min(before.size(), out.size()); k > 0; k--) {
            if (before.subList(before.size() - k, before.size()).equals(out.subList(0, k))) { overlap = k; break; }
        }
        return out.subList(overlap, out.size());
    }

    static List<Site> allSites(World w) {
        List<Site> out = new ArrayList<>();
        for (Body b : w.bodies) out.addAll(b.sites);
        return out;
    }

    static List<Tech> sortedTechs() {
        List<Tech> ts = new ArrayList<>(TechCatalog.all());
        ts.sort(Comparator.comparingInt(TechAvailability::tier).thenComparingLong(Tech::researchCost).thenComparing(Tech::id));
        return ts;
    }

    static int count(Site s, BuildingType t) { return (int) s.buildings.stream().filter(b -> b.type == t).count(); }
    static int underway(Site s, BuildingType t) { return (int) s.buildings.stream().filter(b -> b.type == t && b.isUnderConstruction()).count(); }
    static double stock(Site s, Resource r) { return s.stockpile.getOrDefault(r, 0.0); }
    static String bodyOf(World w, String siteId) { Site s = siteId == null ? null : w.findSite(siteId); return s == null ? null : s.bodyId; }

    static void put(Map<Resource, Double> m, Site s, Resource r, double want) {
        double v = Math.floor(Math.min(want, stock(s, r) * 0.3));
        if (v >= 1) m.put(r, v);
    }

    static String firstDiff(String a, String b) {
        int i = 0;
        while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++;
        int from = Math.max(0, i - 80);
        return "at char " + i + ": ..." + a.substring(from, Math.min(a.length(), i + 80)) + " ||| ..." + b.substring(from, Math.min(b.length(), i + 80));
    }

    static String fmt(double d) { return String.format("%.4f", d); }

    static String stack(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = sw.toString().replace('\n', ' ').replace('\t', ' ');
        return s.length() > 1500 ? s.substring(0, 1500) : s;
    }

    static void drainUncaught() {
        synchronized (uncaught) {
            for (String u : uncaught) violation("uncaught exception " + u);
            uncaught.clear();
        }
    }

    static void violation(String msg) {
        String line = Instant.now() + " VIOLATION seed=" + seed + " move=" + move + " " + msg;
        System.out.println(line);
        System.out.flush();
        try {
            Files.writeString(runDir.resolve("violations.log"), line + "\n", StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    static void log(String msg) {
        System.out.println(Instant.now() + " " + msg);
        System.out.flush();
    }
}
