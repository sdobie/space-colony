package spacecolony.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.Event;
import spacecolony.sim.Resource;
import spacecolony.sim.BuildingType;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.Transit;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;

/** Save/load entry points. Schema version 1. */
public final class SaveFile {
    public static final int SCHEMA_VERSION = 1;
    private SaveFile() {}

    // ===== SAVE =====

    public static void save(World w, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        String json = JsonWriter.write(buildEnvelope(w));
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, json);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) {}
            throw e;
        }
    }

    private static JsonValue buildEnvelope(World w) {
        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("schemaVersion", num(SCHEMA_VERSION));
        root.put("seed",          num(w.seed));
        root.put("tick",          num(w.tick));
        root.put("credits",       num(w.credits));
        root.put("bodies",        buildBodies(w));
        root.put("ships",         buildShips(w));
        root.put("tech",          buildTech(w));
        root.put("goals",         buildGoals(w));
        root.put("events",        buildEvents(w));
        return new JsonValue.JsonObject(root);
    }

    private static JsonValue buildBodies(World w) {
        List<JsonValue> out = new ArrayList<>();
        for (Body b : w.bodies) {
            if (b.sites.isEmpty()) continue;  // only persist bodies with player sites
            Map<String, JsonValue> body = new LinkedHashMap<>();
            body.put("id", str(b.id));
            List<JsonValue> sites = new ArrayList<>();
            for (Site s : b.sites) sites.add(buildSite(s));
            body.put("sites", new JsonValue.JsonArray(sites));
            out.add(new JsonValue.JsonObject(body));
        }
        return new JsonValue.JsonArray(out);
    }

    private static JsonValue buildSite(Site s) {
        Map<String, JsonValue> o = new LinkedHashMap<>();
        o.put("id", str(s.id));
        o.put("name", str(s.name));
        o.put("lat", num(Double.toString(s.lat)));
        o.put("lon", num(Double.toString(s.lon)));
        o.put("siteBase", num(s.siteBase));
        o.put("population", num(s.population));
        o.put("populationCap", num(s.populationCap));
        o.put("morale", num(Double.toString(s.morale)));
        o.put("stockpile",    resourceMap(s.stockpile));
        o.put("stockpileCap", resourceMap(s.stockpileCap));
        List<JsonValue> bldgs = new ArrayList<>();
        for (Building b : s.buildings) {
            Map<String, JsonValue> bo = new LinkedHashMap<>();
            bo.put("type", str(b.type.name()));
            bo.put("level", num(b.level));
            bo.put("enabled", new JsonValue.JsonBool(b.enabled));
            bldgs.add(new JsonValue.JsonObject(bo));
        }
        o.put("buildings", new JsonValue.JsonArray(bldgs));
        return new JsonValue.JsonObject(o);
    }

    private static JsonValue buildShips(World w) {
        List<JsonValue> out = new ArrayList<>();
        for (Ship s : w.ships) {
            Map<String, JsonValue> o = new LinkedHashMap<>();
            o.put("id", str(s.id));
            o.put("name", str(s.name));
            o.put("class", str(s.shipClass.name()));
            o.put("state", str(s.state.name()));
            o.put("currentSiteId", s.currentSiteId == null ? new JsonValue.JsonNull() : str(s.currentSiteId));
            o.put("fuel", num(Double.toString(s.fuel)));
            o.put("cargo", resourceMap(s.cargo));
            if (s.transit != null) {
                Map<String, JsonValue> t = new LinkedHashMap<>();
                t.put("originSiteId", str(s.transit.originSiteId()));
                t.put("destSiteId",   str(s.transit.destSiteId()));
                t.put("departureTick", num(s.transit.departureTick()));
                t.put("arrivalTick",   num(s.transit.arrivalTick()));
                t.put("cargoSnapshot", resourceMap(s.transit.cargoSnapshot()));
                o.put("transit", new JsonValue.JsonObject(t));
            } else {
                o.put("transit", new JsonValue.JsonNull());
            }
            out.add(new JsonValue.JsonObject(o));
        }
        return new JsonValue.JsonArray(out);
    }

    private static JsonValue buildTech(World w) {
        Map<String, JsonValue> o = new LinkedHashMap<>();
        List<JsonValue> rs = new ArrayList<>();
        // Sort for deterministic output.
        for (String id : new TreeSet<>(w.tech.researched)) rs.add(str(id));
        o.put("researched", new JsonValue.JsonArray(rs));
        o.put("activeId", w.tech.activeId == null ? new JsonValue.JsonNull() : str(w.tech.activeId));
        o.put("accumulatedPoints", num(Double.toString(w.tech.accumulatedPoints)));
        return new JsonValue.JsonObject(o);
    }

    private static JsonValue buildGoals(World w) {
        Map<String, JsonValue> o = new LinkedHashMap<>();
        List<JsonValue> as = new ArrayList<>();
        for (String id : new TreeSet<>(w.goals.achieved)) as.add(str(id));
        o.put("achieved", new JsonValue.JsonArray(as));
        return new JsonValue.JsonObject(o);
    }

    private static JsonValue buildEvents(World w) {
        List<JsonValue> out = new ArrayList<>();
        for (Event e : w.recentEvents) {
            Map<String, JsonValue> o = new LinkedHashMap<>();
            o.put("tick", num(e.tick()));
            o.put("severity", str(e.severity().name()));
            o.put("kind", str(e.kind().name()));
            o.put("message", str(e.message()));
            o.put("bodyId", e.bodyId() == null ? new JsonValue.JsonNull() : str(e.bodyId()));
            o.put("siteId", e.siteId() == null ? new JsonValue.JsonNull() : str(e.siteId()));
            o.put("shipId", e.shipId() == null ? new JsonValue.JsonNull() : str(e.shipId()));
            out.add(new JsonValue.JsonObject(o));
        }
        return new JsonValue.JsonArray(out);
    }

    private static JsonValue resourceMap(Map<Resource, Double> map) {
        Map<String, JsonValue> o = new LinkedHashMap<>();
        // Stable iteration via Resource enum ordinal.
        for (Resource r : Resource.values()) {
            Double v = map.get(r);
            if (v == null) continue;
            o.put(r.name(), num(Double.toString(v)));
        }
        return new JsonValue.JsonObject(o);
    }

    // ===== LOAD =====

    public static World load(Path file) throws IOException, IncompatibleSaveException {
        String text = Files.readString(file);
        JsonValue.JsonObject root = (JsonValue.JsonObject) JsonReader.parse(text);
        int version = (int) ((JsonValue.JsonNumber) root.values().get("schemaVersion")).asLong();
        if (version != SCHEMA_VERSION) throw new IncompatibleSaveException(version, SCHEMA_VERSION);

        long seed = ((JsonValue.JsonNumber) root.values().get("seed")).asLong();
        World w = WorldGenerator.generate(seed);
        w.tick = ((JsonValue.JsonNumber) root.values().get("tick")).asLong();
        w.credits = ((JsonValue.JsonNumber) root.values().get("credits")).asLong();

        // Geometry comes from the seed, but player-placed sites come from the file.
        // Wipe the generated sites (including the Earth Hub) so the file is authoritative.
        for (Body b : w.bodies) b.sites.clear();
        for (JsonValue bv : ((JsonValue.JsonArray) root.values().get("bodies")).values()) {
            JsonValue.JsonObject bo = (JsonValue.JsonObject) bv;
            String bodyId = ((JsonValue.JsonString) bo.values().get("id")).value();
            Body body = w.findBody(bodyId);
            if (body == null) continue; // body removed from layout — skip
            for (JsonValue sv : ((JsonValue.JsonArray) bo.values().get("sites")).values()) {
                body.sites.add(loadSite(bodyId, (JsonValue.JsonObject) sv));
            }
        }

        w.ships.clear();
        for (JsonValue sv : ((JsonValue.JsonArray) root.values().get("ships")).values()) {
            w.ships.add(loadShip((JsonValue.JsonObject) sv));
        }

        JsonValue.JsonObject tech = (JsonValue.JsonObject) root.values().get("tech");
        for (JsonValue v : ((JsonValue.JsonArray) tech.values().get("researched")).values()) {
            w.tech.researched.add(((JsonValue.JsonString) v).value());
        }
        JsonValue activeId = tech.values().get("activeId");
        w.tech.activeId = activeId instanceof JsonValue.JsonString jsa ? jsa.value() : null;
        w.tech.accumulatedPoints = ((JsonValue.JsonNumber) tech.values().get("accumulatedPoints")).asDouble();

        JsonValue.JsonObject goals = (JsonValue.JsonObject) root.values().get("goals");
        for (JsonValue v : ((JsonValue.JsonArray) goals.values().get("achieved")).values()) {
            w.goals.achieved.add(((JsonValue.JsonString) v).value());
        }

        for (JsonValue ev : ((JsonValue.JsonArray) root.values().get("events")).values()) {
            w.emit(loadEvent((JsonValue.JsonObject) ev));
        }

        return w;
    }

    private static Site loadSite(String bodyId, JsonValue.JsonObject o) {
        String id = ((JsonValue.JsonString) o.values().get("id")).value();
        String name = ((JsonValue.JsonString) o.values().get("name")).value();
        double lat = ((JsonValue.JsonNumber) o.values().get("lat")).asDouble();
        double lon = ((JsonValue.JsonNumber) o.values().get("lon")).asDouble();
        int siteBase = (int) ((JsonValue.JsonNumber) o.values().get("siteBase")).asLong();
        Site s = new Site(id, name, bodyId, lat, lon, siteBase);
        s.population = (int) ((JsonValue.JsonNumber) o.values().get("population")).asLong();
        s.populationCap = (int) ((JsonValue.JsonNumber) o.values().get("populationCap")).asLong();
        s.morale = ((JsonValue.JsonNumber) o.values().get("morale")).asDouble();
        loadResourceMap((JsonValue.JsonObject) o.values().get("stockpile"),    s.stockpile);
        loadResourceMap((JsonValue.JsonObject) o.values().get("stockpileCap"), s.stockpileCap);
        for (JsonValue bv : ((JsonValue.JsonArray) o.values().get("buildings")).values()) {
            JsonValue.JsonObject bo = (JsonValue.JsonObject) bv;
            BuildingType type = BuildingType.valueOf(
                ((JsonValue.JsonString) bo.values().get("type")).value());
            int level = (int) ((JsonValue.JsonNumber) bo.values().get("level")).asLong();
            Building b = new Building(type, level);
            b.enabled = ((JsonValue.JsonBool) bo.values().get("enabled")).value();
            s.buildings.add(b);
        }
        return s;
    }

    private static Ship loadShip(JsonValue.JsonObject o) {
        String id = ((JsonValue.JsonString) o.values().get("id")).value();
        String name = ((JsonValue.JsonString) o.values().get("name")).value();
        ShipClass cls = ShipClass.valueOf(((JsonValue.JsonString) o.values().get("class")).value());
        JsonValue currentSiteIdV = o.values().get("currentSiteId");
        String currentSiteId = currentSiteIdV instanceof JsonValue.JsonString jss ? jss.value() : null;
        Ship s = new Ship(id, name, cls, currentSiteId);
        s.state = ShipState.valueOf(((JsonValue.JsonString) o.values().get("state")).value());
        s.fuel = ((JsonValue.JsonNumber) o.values().get("fuel")).asDouble();
        loadResourceMap((JsonValue.JsonObject) o.values().get("cargo"), s.cargo);
        JsonValue tv = o.values().get("transit");
        if (tv instanceof JsonValue.JsonObject to) {
            Map<Resource, Double> snapshot = new EnumMap<>(Resource.class);
            loadResourceMap((JsonValue.JsonObject) to.values().get("cargoSnapshot"), snapshot);
            s.transit = new Transit(
                ((JsonValue.JsonString) to.values().get("originSiteId")).value(),
                ((JsonValue.JsonString) to.values().get("destSiteId")).value(),
                ((JsonValue.JsonNumber) to.values().get("departureTick")).asLong(),
                ((JsonValue.JsonNumber) to.values().get("arrivalTick")).asLong(),
                snapshot);
        }
        return s;
    }

    private static Event loadEvent(JsonValue.JsonObject o) {
        long tick = ((JsonValue.JsonNumber) o.values().get("tick")).asLong();
        EventSeverity sev = EventSeverity.valueOf(
            ((JsonValue.JsonString) o.values().get("severity")).value());
        EventKind kind = EventKind.valueOf(
            ((JsonValue.JsonString) o.values().get("kind")).value());
        String msg = ((JsonValue.JsonString) o.values().get("message")).value();
        return new Event(tick, sev, kind, msg,
            optString(o, "bodyId"), optString(o, "siteId"), optString(o, "shipId"));
    }

    private static String optString(JsonValue.JsonObject o, String key) {
        JsonValue v = o.values().get(key);
        return v instanceof JsonValue.JsonString js ? js.value() : null;
    }

    private static void loadResourceMap(JsonValue.JsonObject o, Map<Resource, Double> target) {
        for (Resource r : Resource.values()) {
            JsonValue v = o.values().get(r.name());
            if (v instanceof JsonValue.JsonNumber jn) target.put(r, jn.asDouble());
        }
    }

    // ===== helpers =====

    private static JsonValue str(String s) { return new JsonValue.JsonString(s); }
    private static JsonValue num(long n)   { return new JsonValue.JsonNumber(Long.toString(n)); }
    private static JsonValue num(int n)    { return new JsonValue.JsonNumber(Integer.toString(n)); }
    private static JsonValue num(String n) { return new JsonValue.JsonNumber(n); }
}
