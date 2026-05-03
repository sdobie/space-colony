# Space Colony Game — Design

**Date:** 2026-05-03
**Status:** Draft (post-brainstorm, pre-implementation-plan)
**Project root:** `/Users/steve/projects/space-colony/`

## 1. Vision

A solar-system-scale logistics tycoon. The player starts with one outpost on an Earth-analog and grows a network of colonies across a procedurally-flavoured but topologically fixed solar system. The interesting decisions are *between* bodies — when to launch, what to ship, which body to colonise next — not what to lay out *within* a colony.

The game is a sandbox. Optional achievement-style goals reward milestones but never end the game. A tech tree gates better propulsion, extraction, and production over time.

## 2. Core gameplay loops

- **Short loop (minutes):** dispatch a ship, watch it transit, unload, watch local production tick up.
- **Medium loop (tens of minutes):** establish a new site, supply it until self-sufficient, queue research that improves throughput.
- **Long loop (hours):** expand to outer-system bodies, unlock fusion drives, build redundant supply chains, hit milestone goals.

Pressure comes from supply needs (food, water, power) and occasional random events (meteor strikes, solar flares, equipment failures, disease outbreaks). No combat or external antagonists in v1.

## 3. World model

### 3.1 Time

- Tick = 1 game day. The simulator runs in discrete ticks.
- Speed controls: pause, 1×, 4×, 16× (ticks per real-second).
- Determinism contract: given `(seed, command history)`, the world state at any tick is reproducible.

### 3.2 Solar system layout (fixed across games)

Hand-tuned heliocentric layout. Same bodies, same orbits, every game:

| Body            | Type                     | Approx. semi-major axis      |
|-----------------|--------------------------|------------------------------|
| Sun             | Star                     | 0                            |
| Mercury-analog  | Rocky                    | 0.4 AU                       |
| Venus-analog    | Rocky                    | 0.7 AU                       |
| Earth-analog    | Rocky                    | 1.0 AU (player start)        |
| Mars-analog     | Rocky                    | 1.5 AU                       |
| Belt-A, B, C    | Asteroid                 | 2.4–2.9 AU                   |
| Jovian-analog   | Gas giant                | 5.2 AU                       |
| Io-analog       | Moon (rocky/volcanic)    | orbits Jovian-analog         |
| Europa-analog   | Moon (icy)               | orbits Jovian-analog         |

That's 10 playable bodies (everything but the Sun).

Players learn the layout across games. Variance comes from per-seed surface and resource rerolls.

### 3.3 Orbits

Circular-orbit approximation:
```
position(tick) = (semiMajorAxis · cos(phaseOffset + 2π·tick/period),
                  semiMajorAxis · sin(phaseOffset + 2π·tick/period))
```
Bodies are 2D heliocentric. Moons orbit their parent. This is enough to give "launch windows matter": when Earth and Mars are on the same side of the sun, transit is cheap; when opposed, expensive.

### 3.4 Per-game seed determines

- Each body's surface seed (passed to adapted `PlanetGenerator` from `planet-map`).
- Each body's resource yield map (low-resolution 2D grid of per-resource multipliers).
- Starting site lat/lon on Earth-analog.
- RNG stream for events (keyed by `(seed, tick, eventCounter)`).

### 3.5 Bodies, sites, buildings

A **Body** has a fixed orbit, type, mass, radius, surface seed, resource yield map, and a list of **Sites**.

A **Site** is a single colony located at a `(lat, lon)` on a body. It has aggregate stats — population, morale, stockpile, production rates — not a building grid. A body can host multiple sites at different locations; resource yields differ by location.

A site holds a list of **Buildings**. Each building has a type and a level. Building types in v1:
- HABITAT — population cap
- FARM — produces FOOD, consumes WATER + ENERGY
- MINE — produces ORE / SILICATE / ICE depending on body and yield
- REFINERY — converts ORE → METAL, ICE → WATER
- POWER_PLANT — produces ENERGY (solar; output scales with distance from sun)
- SHIPYARD — required to build/dock ships at this site
- RESEARCH_LAB — produces research points

Buildings are abstract: no on-surface placement, no adjacency rules. A site is one entity with a list of building entries.

### 3.6 Resources (~10)

Working list (final names locked in implementation plan):

- **ORE** — raw mineral, mined from rocky bodies and asteroids
- **METAL** — refined from ore
- **SILICATE** — raw mineral, used for components
- **ICE** — mined from cold bodies
- **WATER** — refined from ice; consumed by population and farms
- **FOOD** — grown by farms; consumed by population
- **BIOMASS** — input to farms (closes loop on repeat planting; can be supplemented from Earth-analog)
- **FUEL** — propellant for ships; produced from atmospheric mining (gas giant) or chemical synthesis
- **COMPONENTS** — assembled from metal + silicate; used to construct buildings, ships, maintenance
- **ENERGY** — flow-only (not stockpiled). Production must meet consumption per tick or brownout.

Production chains are intentionally light: ORE → METAL, ICE → WATER, METAL+SILICATE → COMPONENTS, FOOD made from BIOMASS+WATER+ENERGY. About 2–3 steps deep.

### 3.7 Ships

A discrete fleet. Each ship is its own entity with:

- `class` ∈ {HAULER, TANKER, COLONIZER}, defining `dryMass`, `cargoCap`, `thrust`
- `state` ∈ {IDLE, LOADING, IN_TRANSIT, UNLOADING}
- `currentSiteId` when not in transit
- `Transit` when in transit: `originSiteId`, `destSiteId`, `departureTick`, `arrivalTick`, `cargoSnapshot`
- `cargo: Map<Resource, Double>`
- `fuel: double`

**Transit math** (computed at the moment the ship physically departs — i.e., when LOADING completes, not when the dispatch command is enqueued):
- Compute origin position at departure tick.
- Predict destination position at future tick `t_dest`: solve fixed-point so that `straight_line_distance(originPos@departure, destPos@t_dest) / shipSpeed = t_dest − departure`. Converges in 2–3 iterations for circular orbits.
- `arrivalTick = ceil(t_dest)`.
- `fuelCost = k_fuel · (dryMass + cargoMass) · distance`. Fuel is deducted immediately at departure.
- The dispatch command itself does an estimated-fuel check at command time (using current positions) and rejects if the ship clearly can't afford the manifest, emitting a warning event ("Ship X has insufficient fuel for this manifest"). A second check at actual departure can still abort the trip if positions have drifted enough during loading to make the trip infeasible.

Ships are built at SHIPYARD sites; cost is a manifest of COMPONENTS + METAL paid from local stockpile and credits paid from world.

### 3.8 Tech tree

Roughly 15–20 techs in v1, each unlocking a multiplier, a new building type, a new ship class, or a new game mechanic (e.g. "Atmospheric mining" enables FUEL production at gas giants). Tech costs research points produced by RESEARCH_LAB buildings.

The tree is a DAG; some techs require prerequisites. Specific tech list and costs locked in the implementation plan.

### 3.9 Goals

A list of optional achievements, e.g. "Found a self-sufficient colony beyond Mars", "Establish a presence in the asteroid belt", "Reach population 10,000 system-wide". Predicates evaluated each tick. Goals never end the game; they award credits and/or research points and surface as events when achieved.

### 3.10 Events

Random events fire per body per tick against a small base rate, weighted by body type and current state:

- METEOR_STRIKE — damages a building (more likely on airless bodies)
- SOLAR_FLARE — temporary power loss across all sites
- EQUIPMENT_FAILURE — building disabled until repaired
- DISEASE_OUTBREAK — population loss + morale drop on populated sites

Effects are concrete state mutations and are emitted to the event log.

## 4. Architecture

### 4.1 High-level structure

```
┌──────────────────────────────────────────────────────────┐
│                     UI (Swing)                           │
│  SystemMapPanel · ColonyListPanel · DetailPanel ·        │
│  BodyViewPanel (uses SphereRenderer) · TechPanel · ...   │
│         │                              ▲                 │
│         │ enqueues Commands            │ reads World     │
│         ▼                              │                 │
│  ┌──────────────────────────────────────────────────┐    │
│  │              Game Engine                         │    │
│  │  GameLoop (Swing Timer) → Simulator.advance()    │    │
│  └──────────────────────────────────────────────────┘    │
│         │                              ▲                 │
│         ▼                              │                 │
│  ┌─────────────────┐          ┌──────────────────┐       │
│  │   Sim (pure)    │ ◀────────│  Save / Load     │       │
│  └─────────────────┘          └──────────────────┘       │
│  ┌─────────────────────────────────────────────────┐     │
│  │  World-gen (seeded, adapted from planet-map)    │     │
│  └─────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────┘
```

### 4.2 Packages

- `spacecolony.sim` — pure data + simulation rules. Zero Swing/IO imports. Heavily unit-tested.
- `spacecolony.world` — world generation: orbital layout (constants), per-body resource maps, surface seeds.
- `spacecolony.engine` — `GameLoop`, `CommandQueue`, `EventBus`. Bridges UI ↔ sim.
- `spacecolony.save` — JSON serialization (hand-rolled, stdlib only).
- `spacecolony.ui` — Swing panels, top-level `SpaceColonyApp`.
- `spacecolony.render` — adapted `SphereRenderer`, `SimplexNoise`, `PlanetGenerator` from `planet-map`.
- `spacecolony.debug` — debug overlay, object inspector, log viewer (only loaded when debug mode is on).

### 4.3 Threading

Single-threaded: game loop on a Swing `Timer` on the EDT. Simulator is fast enough at v1 scale (handful of bodies, dozens of ships) that a worker thread is unnecessary and would introduce concurrency bugs around saves and rendering.

## 5. Sim data model (`spacecolony.sim`)

### 5.1 World

The entire game state. Plain data, JSON-serializable.

- `long tick` — ticks since game start
- `long seed` — for replay / debugging
- `Star sun`
- `List<Body> bodies`
- `List<Ship> ships`
- `TechState tech` — researched + in-progress techs
- `GoalState goals` — achieved goal IDs + progress
- `List<Event> recentEvents` — ring buffer for the UI alert strip
- `long credits` — abstract currency for ship purchases / tech investments

### 5.2 Body

- `String id, name`
- `BodyType type` ∈ {ROCKY, GAS_GIANT, ICE, ASTEROID, MOON}
- `Orbit orbit` — `semiMajorAxis`, `period`, `phaseOffset`, optional `parentId` for moons
- `double mass, radius`
- `long surfaceSeed`
- `ResourceMap resourceYields` — 2D grid of per-resource multipliers, sampled at site creation
- `List<Site> sites`

### 5.3 Site

- `String id, name`
- `String bodyId; double lat, lon`
- `int population, populationCap`
- `Map<Resource, Double> stockpile`
- `Map<Resource, Double> productionRateCache` — recomputed each tick from buildings + workers
- `List<Building> buildings`
- `double morale ∈ [0, 1]`

### 5.4 Ship, Building, Resource, Orbit, Event

As described in §3.5–§3.7 and §3.10. All plain data.

### 5.5 Determinism

No `Math.random()` anywhere in `sim` or `world`. All randomness goes through a `Random` keyed off `(seed, tick, stepId)` so any sequence is reproducible from a saved seed and tick count.

## 6. Simulation rules (per tick)

`Simulator.advance(World)` runs the following phases in order. Each phase is a pure function of state at the start of the tick.

1. **Drain command queue.** Apply queued player commands (build site, buy ship, dispatch ship, queue research, retire ship). Validate; reject invalid commands with a warning event. This is the only place outside-the-sim mutations enter.
2. **Advance tick & orbits.** `tick++`. Body positions are computed on demand from the new tick value, not stored.
3. **Advance ships in transit.** For each ship with `state == IN_TRANSIT`, if `tick >= arrivalTick`, transition to UNLOADING and dock at destination. (LOADING/UNLOADING take a small fixed number of ticks so ship turnover is visible.)
4. **Per-site production & consumption.** For each site:
   - Compute power balance. Negative balance → proportionally throttle non-essential consumers (brownout).
   - Compute resource production: `output = baseRate · workers · resourceYieldMultiplier · techMultiplier · powerFactor`.
   - Compute consumption: population eats FOOD, draws WATER, uses COMPONENTS for maintenance.
   - Update stockpiles (clipped at storage cap).
   - Update morale: drops if essentials below threshold, recovers slowly otherwise.
   - Update population: grows under high morale + housing; declines under shortages.
5. **Loading / unloading ships.** LOADING ships transfer site → ship cargo (rate-limited). When manifest filled, run the transit math (§3.7), deduct fuel, transition IN_TRANSIT, and store the resulting `arrivalTick` and `cargoSnapshot` on the ship. UNLOADING ships transfer ship → site cargo; when empty, transition IDLE.
6. **Random events.** Roll once per body per tick against a base rate. If triggered, weighted random event type by body context. Apply effect; append to `recentEvents`.
7. **Research progress.** Active research accumulates points = `Σ research_lab_output · techMultiplier`. When points ≥ tech cost, mark complete; effects apply immediately.
8. **Goal check.** Iterate goals, evaluate predicates, mark newly-achieved goals and emit events. Goals never end the game.

## 7. UI components & data flow

Top-level frame `SpaceColonyApp extends JFrame` with `BorderLayout`:

- **NORTH** — `TopBar`: tick counter, in-game date, speed buttons (⏸ 1× 4× 16×), credits, menu (Save / Load / New / Tech / Goals / Debug).
- **WEST** — `ColonyListPanel`: scrollable list of all sites and ships, color-coded for warnings. Click selects.
- **CENTER** — `MainViewPanel` (CardLayout): `SystemMapPanel` (default) ↔ `BodyViewPanel`.
- **EAST** — `DetailPanel`: contextual detail for current selection. Includes `SphereMiniRenderer` for body selections.
- **SOUTH** — `EventStripPanel`: recent events, color-coded by severity.

### 7.1 SystemMapPanel

Custom `paintComponent` renders: starfield, sun, faint orbit circles, bodies at current positions, in-transit ships moving along straight lines, site markers on bodies. Mouse: click body to select, right-click for context menu, scroll to zoom (1×–8×), drag to pan. Repaints on tick + interaction (interaction throttled, mirroring `planet-map`).

### 7.2 BodyViewPanel

Adapted `SphereRenderer` from `planet-map` shows the rotating body full-screen. Site markers projected to lat/lon. Click marker to select. "Place site here" mode (when player has a colonizer ship at the body) lets the player click a lat/lon to plant a new site. "Back to system map" button.

### 7.3 DetailPanel

Content depends on selection:

- **Site:** name, body, lat/lon, pop / cap, morale bar, stockpile bars, production rates, building list with upgrade buttons, warning indicators, action buttons (build building, demolish, dispatch ship from here).
- **Ship:** class, state, location/transit progress bar, cargo, fuel, dispatch dialog (pick destination + manifest), recall, retire.
- **Body:** name, type, mass/radius, list of sites, mini sphere render via `SphereMiniRenderer`, "Open body view" button.

### 7.4 TechPanel & GoalsPanel

Modal/overlay panels: tech tree as a graph (available highlighted, locked greyed); goals list grouped by category with progress bars.

### 7.5 Per-frame data flow

1. Tick fires (Swing `Timer`, interval based on speed).
2. Engine drains command queue, calls `Simulator.advance(world)`.
3. Engine fires `WorldChanged`.
4. UI panels listening repaint and refresh from `world` (read-only).

### 7.6 Command flow

User action → handler builds a `Command` → enqueues on `engine.commandQueue` → next tick simulator drains and applies. Failures emit events the user sees.

The UI never mutates `World` directly. Same property gives testable sim and clean save/load.

## 8. Debug mode

Toggle via `--debug` flag at launch or `Ctrl+D` at runtime. Available to power users / developers; off by default.

When enabled:

- **Debug overlay** — toggleable bottom panel: current tick, real-time tick rate, last 50 simulator phase timings, command queue depth, RNG state, recent exceptions.
- **Object inspector** — Shift+click any body, site, or ship → modal `ObjectInspectorDialog` reflects every field as a tree (private fields included). Read-only by default; an "Edit" toggle lets you mutate primitive fields and re-inject.
- **Log viewer** — `LogViewerDialog` shows the rolling event log + engine `java.util.logging` stream. Filter by level + source package. Tail-follow. Copy / save buttons.
- **Sim controls** — buttons: "Step 1 tick" (paused only), "Run N ticks" (advance without rendering, for stress tests), "Trigger event…" (force-roll an event), "Dump world to JSON" (timestamped file in `~/.space-colony/debug/`).
- **Render overlays** on `SystemMapPanel` — orbital paths labelled with period, ship transits show predicted arrival point + fuel cost line, body labels show resource yield summary.
- **Determinism check** — snapshot world, advance 1000 ticks, snapshot, restore, advance 1000 ticks again, assert snapshots match.

### 8.1 Logging architecture

- Sim, engine, world-gen log via `java.util.logging` (stdlib).
- `RingBufferHandler` keeps last N records in memory for the log viewer.
- `FileHandler` writes to `~/.space-colony/logs/<date>.log`, size-rotated.
- Level configurable via `--log-level=DEBUG|INFO|WARN` and at runtime through the debug menu.

### 8.2 Uncaught exceptions

Global handler on the EDT and any worker thread captures uncaught exceptions, logs the full stack, surfaces a banner in the debug overlay; in non-debug mode shows a polite dialog with continue/quit options.

## 9. Save / load

- One JSON file per save in `~/.space-colony/saves/<slot-name>.json`.
- Auto-save on quit writes `<slot-name>.autosave.json` (or `_autosave.json` for unnamed/new games).
- Multiple named save slots; menu lists all slots with timestamps and lets the player save / load / delete.
- Format: `World` serialized via a small hand-rolled JSON writer (no external dep). Plain-data classes with primitive collections; cycles avoided by using IDs (e.g., `bodyId` instead of `Body` reference inside `Site`).
- Schema version field at the top of every save. Loader rejects unknown versions with a clear error dialog and does not auto-overwrite a corrupt file.
- Load reconstructs `World` exactly. Transient caches (e.g. `productionRateCache`) are recomputed on first tick after load.

## 10. Error handling

- **In sim:** invalid commands rejected with `Event(severity=warning, message=...)`. Sim never throws on player input.
- **Programmer errors:** assertions throw `IllegalStateException` (e.g., "ship in transit has no Transit"). These crash — they're bugs.
- **Save load failures:** dialog with the error, fall back to "New Game" menu. No auto-overwrite of corrupt files.
- **World-gen failures:** impossible by construction (deterministic). Any exception is a bug.
- **UI errors:** EDT exception handler logs and shows a "Something went wrong" dialog with a continue button; doesn't kill the JVM.

## 11. Testing

Framework: JUnit 5 via Gradle. Source layout: `src/main/java/...`, `src/test/java/...`.

### 11.1 Sim unit tests

- **Determinism:** `advance(w, 100)` from same seed yields same `World`.
- **Production:** site with fixed buildings produces expected resources per tick.
- **Transit:** dispatch with known orbital params yields expected `arrivalTick` and `fuelCost`.
- **Supply pressure:** site morale drops when food stockpile hits zero; recovers when restocked.
- **Events:** with fixed RNG seed, events fire at expected ticks and apply expected effects.
- **Goals:** predicate evaluation correct for representative goals (e.g., "establish 3 self-sufficient colonies").

### 11.2 Save round-trip tests

- `serialize(world); deserialize; advance(50)` matches `advance(originalWorld, 50)`.
- Reject unsupported schema version.

### 11.3 World-gen tests

- Same seed → same `World` (regression on accidental nondeterminism).
- Resource yield maps correlate correctly with body type.

### 11.4 Light UI tests

- Construct each panel, set sample selections, assert no exceptions thrown during paint.
- We do not pixel-test rendering. Visual issues are caught by manual play.

### 11.5 Coverage target

Sim coverage > 80%. UI coverage informal — verified manually.

## 12. Build & run

Gradle, Kotlin DSL.

- `./gradlew run` — launch the game.
- `./gradlew test` — run JUnit tests.
- `./gradlew distZip` — packaged distribution (post-v1).
- Java 21 (or latest LTS available locally).
- Single test dependency: `org.junit.jupiter:junit-jupiter`. No runtime dependencies.

## 13. World-gen — adapted from planet-map

The new project copies and adapts these classes from `/Users/steve/projects/planet-map/src/main/java/planetmap/`:

- `SimplexNoise.java` — verbatim or near-verbatim, used for surface textures and resource maps.
- `PlanetGenerator.java` — adapted; the equirectangular height/biome/moisture/temperature pipeline is reused, but generation is parameterised by body type (gas giant rendering differs from rocky).
- `SphereRenderer.java` — adapted for two contexts: full-screen `BodyViewPanel` and small-bounded `SphereMiniRenderer` for the right dock panel.

Adapted classes live in `spacecolony.render`. We do not depend on `planet-map` as a library; copying is cleaner for v1 and avoids cross-project coupling. A shared library can be extracted later if a third project needs the renderer.

## 14. v1 scope

**In v1:**

- Fixed orbital layout (10 playable bodies including moons + asteroids, plus the Sun); procedural surface + resources per seed.
- Tick-based sim with pause / 1× / 4× / 16× speed controls.
- Map + dock panels UI (system map center, colony list left, detail right).
- Sites (multiple per body) with aggregate stats, no surface building grid.
- ~10 resources, light production chains (2–3 steps).
- Discrete ship fleet: build, dispatch, cargo manifests; straight-line travel to future position; fuel ∝ mass × distance.
- Survival pressures (food / water / power) and a small set of random events.
- Tech tree (~15–20 techs).
- Optional achievement-style goals (don't end the game).
- Save / load with named slots + auto-save on quit.
- Inline body preview in dock + full sphere body view on demand.
- Debug mode: overlay, object inspector, log viewer, sim controls, render overlays, determinism check.
- JUnit tests covering sim math (tick advance, transit, production, supply consumption, save round-trip, world-gen determinism).

**Deferred to post-v1:**

- Sound / music.
- Additional bodies (Kuiper belt, comets).
- Hohmann-style proper orbital mechanics with delta-v.
- Pirates / antagonists / combat.
- Multi-system / interstellar.
- Buildings-on-surface base building.
- Mod / scenario support.
- Multiplayer.

## 15. Open questions for the implementation plan

These are best resolved when writing the plan, not here:

- Final list of Resource enum values and exact numeric balance.
- Final tech tree contents, prerequisites, and costs.
- Specific goal definitions and reward values.
- JSON save schema details (field naming convention).
- Debug-mode keyboard shortcut conflicts with main game shortcuts.
- Whether `ResourceMap` is a flat array or a `double[][]` and the resolution.
