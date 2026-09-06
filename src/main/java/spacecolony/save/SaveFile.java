package spacecolony.save;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.Event;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.Site;
import spacecolony.sim.World;

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

    // ===== LOAD (stub — implemented in Task 17) =====

    public static World load(Path file) throws IOException, IncompatibleSaveException {
        throw new UnsupportedOperationException("Implemented in Task 17");
    }

    // ===== helpers =====

    private static JsonValue str(String s) { return new JsonValue.JsonString(s); }
    private static JsonValue num(long n)   { return new JsonValue.JsonNumber(Long.toString(n)); }
    private static JsonValue num(int n)    { return new JsonValue.JsonNumber(Integer.toString(n)); }
    private static JsonValue num(String n) { return new JsonValue.JsonNumber(n); }
}
