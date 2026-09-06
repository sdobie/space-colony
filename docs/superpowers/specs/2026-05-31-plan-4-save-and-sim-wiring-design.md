# Plan 4 — Save/Load + Sim-Side Tech Wiring

**Date:** 2026-05-31
**Status:** Draft (post-brainstorm, pre-implementation-plan)
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–3 merged to `main` (sim core, render adaptation, engine + Swing UI shell).
**Successor:** Plan 5 = debug mode + UI polish (out of scope here).

## 1. Vision

Plans 1–3 produced a playable Swing shell, but two big sim-layer obligations from the original game design are still unfulfilled: research completes and does *nothing*, and HABITAT buildings are inert. There is also no way to save a game. Plan 4 closes those three gaps in one focused slice, so the game is genuinely playable session-to-session and tech feels meaningful.

Three deliverables:

1. **Save/load** — hand-rolled JSON (stdlib only), schema-versioned, named slot per file in `~/.space-colony/saves/`. File menu wired to `JFileChooser` / `JOptionPane`.
2. **Tech effects** — each of the 17 v1 techs from `TechCatalog` actually changes simulation behaviour via a new `spacecolony.sim.TechEffects` static class consulted by sim phases.
3. **HABITAT population cap** — buildings raise `populationCap` by `level × 100`, gated by colony-management techs.

Plus a small enforcement deliverable: **EDT assertion guards** on `Engine` and UI entry points, so layering violations crash loudly in development.

**Out of scope (Plan 5):** debug mode panel, tech tree / goals / events panel polish, autosave, multiple save slots in a single picker, save thumbnails.

## 2. Architecture overview

### 2.1 New packages

```
spacecolony/
├── sim/
│   └── TechEffects.java          (NEW — static multipliers, sim-only deps)
├── save/                          (NEW PACKAGE)
│   ├── SaveFile.java
│   ├── JsonWriter.java
│   ├── JsonReader.java
│   └── IncompatibleSaveException.java
├── engine/
│   └── EdtGuard.java             (NEW — single static helper)
└── ui/
    └── FileMenu.java             (NEW — JMenuBar with File menu)
```

### 2.2 Layering (extends Plan 3 rules)

- `world → sim` (existing)
- `engine → sim` (existing)
- `ui → engine` (existing)
- `save → sim`, `save → world` (new — save package needs both `World` and `WorldGenerator`)
- `ui → save` (new — `FileMenu` invokes `SaveFile`)
- **Never reverse.** `sim` stays Swing-free and IO-free.

### 2.3 Threading model (unchanged)

`SpaceColonyApp` runs everything on the EDT. `GameLoop`'s Swing `Timer` fires on EDT, so `Engine.tick`, listener fan-out, and command application all run on EDT. Save/load reads and writes from inside `JFileChooser` callbacks on the EDT but wraps the actual IO in a `SwingWorker` so disk latency doesn't freeze the frame.

Plan 4 adds `EdtGuard.assertEdt()` calls to `Engine.enqueue`, `Engine.tick`, `Engine.addListener`, and `FileMenu` action handlers, enforced via `-ea` in `test`/`run`/`play` Gradle tasks.

## 3. Save/load

### 3.1 File layout

- Directory: `~/.space-colony/saves/`. Created lazily on first save via `Files.createDirectories`.
- Filename: user picks via `JFileChooser`. `.json` appended if missing. No reserved names. No autosave.
- One envelope per file, pretty-printed JSON, UTF-8.

### 3.2 Schema v1 envelope

```json
{
  "schemaVersion": 1,
  "seed": 7777,
  "tick": 4321,
  "credits": 18500,
  "bodies": [
    {
      "id": "earth",
      "sites": [
        {
          "id": "site-earth-hub",
          "name": "Earth Hub",
          "lat": 0.5, "lon": -1.5,
          "siteBase": 200,
          "population": 250, "populationCap": 300, "morale": 0.92,
          "stockpile":   { "FOOD": 412.0, "WATER": 380.0, "...": "..." },
          "stockpileCap":{ "FOOD": 1000.0, "...": "..." },
          "buildings": [
            { "type": "HABITAT", "level": 2, "enabled": true },
            { "type": "FARM",    "level": 1, "enabled": true }
          ]
        }
      ]
    }
  ],
  "ships": [
    {
      "id": "ship-h1", "name": "H1", "class": "HAULER",
      "state": "IN_TRANSIT", "currentSiteId": null, "fuel": 92.0,
      "cargo": { "METAL": 50.0 },
      "transit": {
        "originSiteId": "site-earth-hub", "destSiteId": "site-mars-1",
        "departureTick": 4100, "arrivalTick": 4500,
        "cargoSnapshot": { "METAL": 50.0 }
      }
    }
  ],
  "tech":  { "researched": ["basic-mining"], "activeId": "ion-drives", "accumulatedPoints": 145.0 },
  "goals": { "achieved": ["first-mars-colony"] },
  "events":[
    { "tick": 4290, "severity": "INFO", "kind": "RESEARCH_COMPLETED",
      "message": "Researched Basic Mining",
      "bodyId": null, "siteId": null, "shipId": null }
  ]
}
```

### 3.3 What is persisted vs. regenerated

| Persisted (player state) | Regenerated from seed (world geometry) |
|---|---|
| `tick`, `credits`, `seed` | `Body.{type, orbit, mass, radius, surfaceSeed, resourceYields}` |
| `bodies[].sites[]` (sites + buildings + stockpiles) | `SystemLayout.BODIES` constants |
| `ships[]` (incl. cargo, fuel, transit) | `ShipClass` enum stats |
| `tech`, `goals`, recent `events` | `TechCatalog`, `GoalCatalog` entries |

`SaveFile.load` calls `WorldGenerator.generate(seed)` first, then reattaches sites/buildings/stockpiles/ships by id, then replaces tech/goals/events/credits/tick.

This keeps save files small and immune to drift in `SystemLayout` or `ResourceYieldMap` *within the same schema version*. Any change that breaks geometry semantics (renaming bodies, changing yield biases, etc.) must bump `schemaVersion`.

### 3.4 API

```java
package spacecolony.save;

public final class SaveFile {
    public static void  save(World w, Path file) throws IOException;
    public static World load(Path file) throws IOException, IncompatibleSaveException;
    private SaveFile() {}
}

public final class IncompatibleSaveException extends Exception {
    public final int fileSchemaVersion;
    public final int currentSchemaVersion;
}

// Package-private:
final class JsonWriter { String writeEnvelope(World w); }
final class JsonReader { JsonValue parse(String text); }   // recursive descent, throws JsonParseException
```

`save` writes atomically: `<name>.json.tmp` → `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. If write throws mid-flight, the original file is left intact and the `.tmp` is cleaned up by the catch block.

`load` flow:
1. Read file → `String`.
2. `JsonReader.parse` → `JsonValue` tree (or throws `JsonParseException` extends `RuntimeException`).
3. If `schemaVersion != 1` → throw `IncompatibleSaveException(fileVer, 1)`. No partial load, no autosave overwrite.
4. `WorldGenerator.generate(envelope.seed)` to rebuild geometry.
5. Walk tree, mutate the fresh `World`.
6. Return `World`.

### 3.5 UI hookup — `FileMenu`

`FileMenu extends JMenuBar`, mounted on `SpaceColonyApp`'s `JFrame`. Each action asserts EDT, pauses the game loop, performs the action, resumes.

- **File → New** — confirm dialog ("Discard current game?") → ask for seed (default = `System.currentTimeMillis()`) via `JOptionPane.showInputDialog` → `Engine.reset(WorldGenerator.generate(seed))`.
- **File → Save…** — `JFileChooser` in save mode, default dir = saves dir. Run `SaveFile.save` inside a `SwingWorker`. On error, `JOptionPane.ERROR_MESSAGE`. On success, no dialog (Plan 5 may add a status-bar toast).
- **File → Load…** — `JFileChooser` in open mode. Run `SaveFile.load` inside a `SwingWorker`. On success, hand the new `World` to `Engine.reset(...)`. On `IncompatibleSaveException`, show warning: *"This save was written with schema v{fileVer}; the current game uses v{currentVer}. Cannot load this save."* On `JsonParseException` or `IOException`, show error dialog. World unchanged in all failure cases.
- **File → Quit** — confirm dialog → `System.exit(0)`.

The engine needs one new method: `Engine.reset(World newWorld)` — replaces internal world reference, clears command queue, fans out a `WorldReplaced` event so all UI panels re-bind.

Touchups required to enable this:
- `Engine.java`: drop `final` from the `world` field; add `void reset(World)`.
- `Simulator.java`: add package-private `void clearCommands()` (the `commandQueue` field is currently private with no accessor).
- `EngineEvent.java`: add a new `WorldReplaced(long tick)` event variant. Existing panels listen for `WorldChanged`; they should treat `WorldReplaced` as a full rebind (clear local caches, re-read selection, etc.).

### 3.6 Hand-rolled JSON

Stdlib only — no Jackson, no Gson, no runtime deps. The whole save package fits in ~400 LOC.

- **Writer.** Recursive `appendValue(StringBuilder, Object, int indent)`. Strings escape `"`, `\`, `\b`, `\f`, `\n`, `\r`, `\t`, and ` ..`. Numbers use `Double.toString`/`Long.toString`. `null`, `true`/`false` direct. Indent = 2 spaces, sorted keys for object literals where stable iteration matters (`stockpile`, `cargo`) so round-trip diffs are clean.
- **Reader.** Recursive-descent over `char[]` with a position cursor. Returns a `JsonValue` sealed hierarchy: `JsonObject(Map<String,JsonValue>)`, `JsonArray(List<JsonValue>)`, `JsonString`, `JsonNumber`, `JsonBool`, `JsonNull`. Handles `\uXXXX`. Throws `JsonParseException` (extends `RuntimeException`) with byte offset on malformed input.
- **No reflection.** `SaveFile` walks the parsed tree manually and constructs `World`. Easy to review, easy to evolve schema.
- **Round-trip test:** `save → load → save` should be byte-identical (modulo iteration order). `EnumMap`s keyed on `Resource` make this stable if we write `Resource.values()` order. `HashSet` for `tech.researched` / `goals.achieved` requires explicit sorting on write.

### 3.7 Failure modes

| Failure | User-visible behaviour |
|---|---|
| Save directory missing | created on save |
| File missing on load | error dialog "File not found", world unchanged |
| Malformed JSON | error dialog with parse-error location, world unchanged |
| Schema version mismatch | warning dialog ("save from schema vN, game uses vM"), world unchanged |
| IO error mid-save | error dialog, `.tmp` cleaned up, original file intact |
| Permissions denied | error dialog with OS message, world unchanged |

## 4. Tech effects (`TechEffects`)

### 4.1 Verbatim mapping for all 17 v1 techs

| Tech id | Description (from `TechCatalog`) | Effect | Applied in |
|---|---|---|---|
| `basic-mining`    | "Improves ore output by 10%." | `mineOre × 1.10` | ProductionPhase, MINE case |
| `basic-farming`   | "Improves food output by 10%." | `farmFood × 1.10` | ProductionPhase, FARM case |
| `solar-panels`    | "+25% output from POWER_PLANT." | `powerProduced × 1.25` per plant | ProductionPhase, power balance |
| `ion-drives`      | "Reduces fuel cost by 20%." | `fuelCost × 0.80` | TransitPhase, departure cost |
| `fusion-drives`   | "Reduces fuel cost by another 30%." | `fuelCost × 0.70` (stacks) | TransitPhase, departure cost |
| `atm-mining`      | "Enables FUEL extraction at gas giants." | gas-giant MINE emits FUEL via yield map | ProductionPhase, MINE case |
| `hydroponics`     | "+30% farm output, less water use." | `farmFood × 1.30`, `farmWaterDemand × 0.70` | ProductionPhase, FARM case |
| `smelting`        | "+20% refinery output." | `refineryOut × 1.20` (METAL and WATER) | ProductionPhase, REFINERY case |
| `medicine`        | "Reduces disease severity by 50%." | `diseasePopLoss × 0.50`, `diseaseMoraleHit × 0.50` | EventPhase, DISEASE_OUTBREAK |
| `colony-mgmt-i`   | "+20% population cap." | `popCap × 1.20` | ProductionPhase (HABITAT recompute) |
| `research-i`      | "+25% RESEARCH_LAB output." | `labOut × 1.25` | ResearchPhase |
| `research-ii`     | "+25% more RESEARCH_LAB output." | `labOut × 1.25` (stacks → ×1.5625) | ResearchPhase |
| `auto-mining`     | "+30% mine output." | `mineOre × 1.30`, `mineSilicate × 1.30` (stacks with basic-mining) | ProductionPhase, MINE case |
| `colony-mgmt-ii`  | "+30% population cap." | `popCap × 1.30` (stacks with mgmt-i) | ProductionPhase (HABITAT recompute) |
| `life-support-i`  | "+20% morale cap." | `moraleCeiling × 1.20` | ProductionPhase, `updateMorale` |
| `life-support-ii` | "+30% morale cap (stacks)." | `moraleCeiling × 1.30` (stacks → ×1.56) | ProductionPhase, `updateMorale` |
| `antimatter`      | "Halves fuel cost again." | `fuelCost × 0.50` (stacks) | TransitPhase, departure cost |

### 4.2 Stacking semantics

All multipliers compose **multiplicatively**. With every drive tech researched: `0.80 × 0.70 × 0.50 = 0.28` (72% reduction). With both mining techs: `1.10 × 1.30 = 1.43`. With both colony-mgmt: `1.20 × 1.30 = 1.56`. Per-tech values match the descriptions exactly.

### 4.3 `TechEffects` API

```java
package spacecolony.sim;

public final class TechEffects {
    private TechEffects() {}

    // ProductionPhase
    public static double mineOreMultiplier(TechState t);          // basic-mining × auto-mining
    public static double mineSilicateMultiplier(TechState t);     // auto-mining
    public static double farmFoodMultiplier(TechState t);         // basic-farming × hydroponics
    public static double farmWaterDemandMultiplier(TechState t);  // hydroponics
    public static double powerPlantMultiplier(TechState t);       // solar-panels
    public static double refineryMultiplier(TechState t);         // smelting
    public static double moraleCeiling(TechState t);              // life-support-i × life-support-ii

    // TransitPhase
    public static double fuelCostMultiplier(TechState t);         // ion × fusion × antimatter
    public static boolean gasGiantFuelEnabled(TechState t);       // atm-mining

    // EventPhase
    public static double diseaseSeverityMultiplier(TechState t);  // medicine

    // ResearchPhase
    public static double researchLabMultiplier(TechState t);      // research-i × research-ii

    // HABITAT cap (called from ProductionPhase, see §5)
    public static double popCapMultiplier(TechState t);           // colony-mgmt-i × colony-mgmt-ii
}
```

Every method is a one-liner:
```java
return (t.researched.contains("X") ? a : 1.0)
     * (t.researched.contains("Y") ? b : 1.0);
```
No reflection, no table. Explicit so a diff against `TechCatalog` is grep-friendly.

### 4.4 Call-site changes in `Simulator`

`productionAndConsumption`:
- Power: `double output = 10.0 * bd.level / Math.max(0.05, r*r) * TechEffects.powerPlantMultiplier(w.tech);`
- MINE: `bd.level * 2.0` → `× TechEffects.mineOreMultiplier(w.tech)`; same for silicate. Gas-giant special case: if `TechEffects.gasGiantFuelEnabled(w.tech)` and `body.type == GAS_GIANT`, sample `ResourceYieldMap` for `FUEL` and produce.
- FARM: food output `× TechEffects.farmFoodMultiplier(w.tech)`; water consume `× TechEffects.farmWaterDemandMultiplier(w.tech)`.
- REFINERY: METAL and WATER output `× TechEffects.refineryMultiplier(w.tech)`.
- `updateMorale`: clamp upper to `TechEffects.moraleCeiling(w.tech)` instead of hard `1.0`. Note: morale stays a `[0, ceiling]` value; UI surfacing of the new ceiling is Plan 5.

`loadingAndUnloading` (departure cost):
```java
double cost = FUEL_K * (s.shipClass.dryMass() + s.cargoMass()) * dist
            * TechEffects.fuelCostMultiplier(w.tech);
```

`applyEvent` DISEASE_OUTBREAK case:
```java
double sev = TechEffects.diseaseSeverityMultiplier(w.tech);
int loss = (int) Math.max(1, (s.population / 10.0) * sev);
s.population -= loss;
s.morale = Math.max(0, s.morale - 0.2 * sev);
```

`researchProgress`:
```java
points += bd.level * 1.0 * TechEffects.researchLabMultiplier(w.tech);
```

Total: ~12 lines changed in `Simulator.java`, plus the new `TechEffects` class (~60 lines).

## 5. HABITAT population cap

### 5.1 Formula

```
siteBase     = (site is Earth Hub) ? 200 : 100        // persisted on Site
habitatBoost = Σ habitat.level × 100  (enabled HABITATs only)
rawCap       = siteBase + habitatBoost
finalCap     = round(rawCap × TechEffects.popCapMultiplier(world.tech))
```

`popCapMultiplier` = `(mgmt-i ? 1.20 : 1.0) × (mgmt-ii ? 1.30 : 1.0)` → max ×1.56.

### 5.2 Sample table

| Buildings | Mgmt I | Mgmt II | Earth Hub cap | Colonizer-site cap |
|---|---|---|---|---|
| HABITAT L1 | – | – | 300 | 200 |
| HABITAT L1 + L1 | – | – | 400 | 300 |
| HABITAT L2 | – | – | 400 | 300 |
| HABITAT L1 | ✓ | – | 360 | 240 |
| HABITAT L1 | ✓ | ✓ | 468 | 312 |
| HABITAT L1 + L2 + L1 | ✓ | ✓ | 1248 | 936 |

### 5.3 `siteBase` field on `Site`

To avoid encoding "is this the starting site?" anywhere, store the base on the `Site` itself:

```java
public final int siteBase;   // 200 for Earth Hub, 100 for colonizer-planted

public Site(String id, String name, String bodyId, double lat, double lon, int siteBase) {
    this.siteBase = siteBase;
    this.populationCap = siteBase;   // initial cap before any habitats
}
```

This **replaces the trailing `populationCap` parameter** of the current constructor (signature `Site(String id, String name, String bodyId, double lat, double lon, int populationCap)`). Both call sites are updated:
- `WorldGenerator.generate(seed)` → Earth Hub built with `siteBase = 200`.
- `Simulator.apply` for `BuildSiteCommand` → colonizer-planted with `siteBase = 100`.
- Save file persists `siteBase`. (Schema v1's first release; no default fallback path needed.)

### 5.4 Recompute timing

Cap is recomputed every tick at the start of each site's pass in `productionAndConsumption`, before population growth:

```java
private void recomputeCap(Site s, TechState tech) {
    int boost = 0;
    for (Building b : s.buildings) {
        if (b.enabled && b.type == BuildingType.HABITAT) boost += b.level * 100;
    }
    s.populationCap = (int) Math.round(
        (s.siteBase + boost) * TechEffects.popCapMultiplier(tech));
}
```

O(buildings) per site per tick — ~10 ops. Cheap.

### 5.5 Edge cases

- **Disabled HABITAT** (event knockout): boost drops on next tick; cap can fall below current population. `updatePopulation` already gates growth on `population < populationCap`. Cap is a growth ceiling, not a hard limit — population doesn't shrink when cap falls (matches spec §3.5).
- **Tech researched mid-game**: effect kicks in next tick automatically — `popCapMultiplier` reads `TechState` live.
- **Save/load**: cap reconstructs deterministically from buildings + tech, but we persist `populationCap` anyway for save-file readability. `recomputeCap` overwrites on the next tick.

## 6. EDT discipline

### 6.1 `EdtGuard` helper

```java
package spacecolony.engine;

public final class EdtGuard {
    private EdtGuard() {}

    public static void assertEdt() {
        assert javax.swing.SwingUtilities.isEventDispatchThread()
            : "Must be called on EDT, was on " + Thread.currentThread().getName();
    }
    public static void assertNotEdt() {
        assert !javax.swing.SwingUtilities.isEventDispatchThread()
            : "Must NOT be called on EDT";
    }
}
```

### 6.2 Guard insertions

- `Engine.enqueue(Command)` — top of method.
- `Engine.tick()` — top of method.
- `Engine.addListener` / `Engine.removeListener` — top of method.
- `Engine.reset(World)` — top of method.
- `FileMenu` action handlers — top of each.

`SaveFile.save` and `SaveFile.load` are **not** guarded — they should run off-EDT via `SwingWorker`. Their callers in `FileMenu` assert EDT before dispatching the worker.

`sim` and `world` packages stay assertion-free — they're EDT-agnostic by design (tests drive them from arbitrary threads).

### 6.3 Gradle: enable assertions

```kotlin
tasks.named<Test>("test")    { jvmArgs("-ea") }
tasks.named<JavaExec>("run") { jvmArgs("-ea") }
tasks.named<JavaExec>("play") { jvmArgs("-ea") }   // play task added in Plan 3
```

With `-ea` on, violations crash loudly; production runs without `-ea` skip the assertion (no-op).

## 7. Testing strategy

### 7.1 TechEffects unit tests — `TechEffectsTest`

~17 tiny tests over a freshly-constructed `TechState`:
- No researched techs → every multiplier returns `1.0`, `gasGiantFuelEnabled == false`.
- Each tech alone → matches table in §4.1 verbatim.
- Stacking pairs: basic-mining + auto-mining → 1.43; ion + fusion + antimatter → 0.28; mgmt-i + mgmt-ii → 1.56; research-i + research-ii → 1.5625; both life-supports → 1.56.

Independent of `Simulator`, no `World` required. Fast.

### 7.2 Sim-wiring tests — verify multipliers are actually consulted

- `ProductionTest.basicMiningResearched_oreOutputUp10pct` — pre/post tech delta ratio ≈ 1.10 ± ε.
- `ProductionTest.hydroponicsResearched_farmFoodUp30pct_waterDown30pct`.
- `ProductionTest.solarPanelsResearched_powerProducedUp25pct` — observed via brownout boundary (force a power-limited setup, check the throttle factor moves).
- `TransitMathTest.ionDrivesResearched_fuelCostDown20pct` — identical dispatches, compare fuel deltas.
- `TransitMathTest.atmMiningResearched_gasGiantYieldsFuel` — MINE on Jovian-analog produces FUEL only when tech is set.
- `EventsTest.medicineResearched_diseaseLossHalved` — call `applyEvent` directly with a fixed RNG to deterministically trigger DISEASE_OUTBREAK.
- `ResearchTest.researchIResearched_progressRateUp25pct` — one lab, count ticks to complete `basic-farming`.

### 7.3 HABITAT cap tests — `HabitatCapTest`

- `earthHubStartingCap_isBase200PlusOneHabitat` — WorldGenerator output cap=300 after first tick.
- `colonizerSiteBase_is100` — plant via `BuildSiteCommand`, no extra habitats → cap=100 after first tick.
- `addingHabitat_raisesCap` — build L1 → cap grows by 100.
- `disabledHabitat_doesNotContribute` — flip `enabled=false` → cap drops next tick.
- `colonyMgmtI_appliesMultiplier` — research mgmt-i → cap × 1.20.
- `bothMgmtTechs_stackMultiplicatively` — research both → cap × 1.56.

### 7.4 Save/load tests — `SaveFileTest`

- `roundTrip_emptyDefaultWorld` — generate → save → load → structural equality (tick, credits, body count, ship count 0, tech.researched empty).
- `roundTrip_afterSimulation` — generate → advance 500 ticks with commands → save → load → advance both originals and loaded 100 more ticks → byte-identical state (population, stockpiles, tech, goals, recent events).
- `roundTrip_withShipInTransit` — dispatch ship → save mid-transit → load → ship still IN_TRANSIT with same arrival tick and cargo snapshot.
- `roundTrip_withResearchedTech` — research basic-mining + ion-drives → save → load → tech.researched restored, and tech effects active (one-tick production delta matches pre-save).
- `incompatibleVersion_throws` — handcraft JSON with `schemaVersion: 99` → `IncompatibleSaveException` with both version fields populated.
- `malformedJson_throws` — write `"{not json"` → `JsonParseException`.
- `missingFile_throws` — load non-existent path → `NoSuchFileException`.
- `atomicWrite_leavesOriginalOnFailure` — simulate mid-write IO error → assert original file intact, `.tmp` cleaned up.

Equality helpers live in a `WorldAssertions` test util — compare envelope-equivalent state across two worlds without object identity.

### 7.5 EDT guard test

- `EngineEdtTest.enqueueOffEdt_assertionFires` — `assertThrows(AssertionError.class, () -> engine.enqueue(...))` from a non-EDT thread with `-ea` on. Demonstrates the guard fires; no need to test every method.

### 7.6 Test count estimate

| Bucket | New tests |
|---|---|
| TechEffects unit | ~17 |
| Sim wiring | ~7 |
| HABITAT cap | 6 |
| Save round-trip | 8 |
| EDT guard | 1 |
| **Plan 4 new** | **~39** |
| Plans 1–3 carry-over | 86 |
| **Total after Plan 4** | **~125** |

### 7.7 Manual play-test checklist (end-of-plan verification)

1. Launch `./gradlew play --args="--seed 1"`.
2. Build a HABITAT → `populationCap` jumps by 100 in dock panel.
3. Research `basic-mining` → wait for completion → ore production visibly climbs.
4. **File → Save…** → choose name → file exists at `~/.space-colony/saves/`.
5. **File → New** → world resets.
6. **File → Load…** → reload the save → tick, credits, sites all restored.
7. Hand-edit save to `"schemaVersion": 99` → Load → warning dialog, world unchanged.
8. Hand-edit save to invalid JSON → Load → error dialog, world unchanged.
9. Dispatch a ship → save mid-transit → load → ship still in transit with correct arrival tick.

## 8. What's next

Plan 5 will tackle UI polish: surface the new pop-cap, tech effects, and goal progress in dedicated panels; add the Debug Mode panel from spec §8 (manual event injection, tick stepping, FPS overlay); polish event log filtering; optionally add autosave + multiple-slot picker.
