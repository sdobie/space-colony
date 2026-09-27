# Space Colony — Plan 6: Startup Sequence, Options, Tutorial

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the game a front door. Add a splash screen and a title screen (Continue, New Game, Tutorial, Load, Options, Quit), a persisted Options dialog, timed autosave, and a guided tutorial for new players. First fix the two sim gaps that make expansion unplayable today: ships never get fuel, and colonizers can't reach a body that has no site yet.

**Architecture:** Two new headless packages, `spacecolony.options` (an `Options` record and `OptionsStore` over a properties file) and `spacecolony.tutorial` (script, progress rules, scenario world). Two new Swing packages, `spacecolony.ui.startup` (`AppController`, splash, title, New Game and Options dialogs) and `spacecolony.ui.tutorial` (coach card, highlight layer, controller). The existing `ui` panels only gain component names and a `Hooks` record on `SpaceColonyFrame`, and never import the new UI packages. In the sim, `Transit` and `Ship` learn about body-bound trips, departures draw fuel from the origin, `World` gains a random-events switch set by a command, and the save schema goes to v2 while still loading v1.

**Tech Stack:** Java 25, Gradle 9.0.0 via wrapper, JUnit 5, Swing, `java.util.logging`, `java.util.Properties`. No new dependencies.

**Spec reference:** `docs/superpowers/specs/2026-09-27-plan-6-startup-options-tutorial-design.md` (sections 1–9).

**Out of scope:** difficulty or scenario options, hints outside the tutorial, sound, saving a tutorial in progress, key rebinding, colonists on colonizers, economy balance beyond the tutorial world, `distZip`. See the design doc §1.

---

## Context

Plans 1–5 are on `main` (PR #11 merged 2026-09-27): 8-phase sim, Swing shell, save slots with autosave on quit, debug mode, panel polish. There are 281 tests. GUI tests that build a frame need `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test`, and skip themselves when headless.

Key facts about the existing code that this plan relies on:

- `Ship(id, name, class, homeSiteId)` starts with `fuel = 0.0`. Nothing in `sim` ever increases `fuel`.
- `CommandPhase.applyDispatchShip` estimates `FUEL_K * (dryMass + manifestMass) * dist * fuelCostMultiplier` from current body positions and rejects when `s.fuel < estCost`. `TransitPhase.loadingAndUnloading` recomputes the cost with `fuelCost(...)` at departure and aborts with `SHIP_OUT_OF_FUEL` when short.
- `Transit(originSiteId, destSiteId, departureTick, arrivalTick, cargoSnapshot)` is site to site. `computeArrivalTick` and `distanceBetweenSitesAtTicks` resolve sites to bodies and use `OrbitalGeometry.bodyPosition`.
- On arrival, `advanceTransits` sets `UNLOADING` and `currentSiteId = destSiteId`. Unloading moves `LOAD_RATE = 25` per resource per tick with no cap.
- `CommandPhase.applyBuildSite` requires a COLONIZER whose `currentSiteId` is a site on the target body, then creates `Site(..., siteBase 100)` with one L1 HABITAT and removes the ship. The new site's population starts at 0.
- `DispatchShipDialog` lists site **ids** in a `JComboBox<String>`, and `PlayTestDriver` step 7 selects `"site-mars-1"` by id.
- `EventPhase.run(World)` draws `DeterministicRng.forStep(seed, tick, 6L)` and rolls per body. `applyForced` bypasses it.
- `SaveFile.SCHEMA_VERSION = 1`. `fromJson` throws `IncompatibleSaveException` for any other version. `SaveFile.toJson(World)` exists. `save` writes `<file>.tmp` and moves it atomically.
- `SaveSlots.list()` marks any non-current schema as `Status.OTHER_SCHEMA`.
- `GameSession(engine, slots, Ui, IntConsumer exit)` has `quit()`, which pauses, confirms, autosaves synchronously and exits.
- `SpaceColonyFrame` builds its own `GameSession` with `System::exit` and a `FileMenu` with 7 components.
- `SpaceColonyApp.main` parses `--seed`, `--debug` and `--log-level=`, installs logging and the crash handler, and builds one `Engine` and frame.
- `DebugLogging.current()` returns `Installed`, which has `setLevel(Level)`.
- `CrashHandler(ExceptionLog, BooleanSupplier debugOn, Reporter)` is installed once with `CrashHandler.install`.
- `build.gradle.kts` has no `version`.
- Starting stock at Earth Hub: FOOD 200, WATER 200, METAL 100, COMPONENTS 50, FUEL 100, BIOMASS 100. An Earth→Mars colonizer trip is 2–8 ticks, and costs 42–202 fuel with 80 cargo, depending on orbital phase.

---

## File Structure

### Create

```
src/main/java/spacecolony/
├── LaunchArgs.java
├── sim/commands/SetRandomEventsCommand.java
├── options/
│   ├── Options.java
│   └── OptionsStore.java
├── tutorial/
│   ├── TutorialContext.java
│   ├── TutorialStep.java
│   ├── TutorialProgress.java
│   ├── TutorialScript.java
│   └── TutorialScenario.java
└── ui/
    ├── AutosaveTimer.java
    ├── SaveLoading.java
    ├── startup/
    │   ├── AppController.java
    │   ├── Starfield.java
    │   ├── SplashWindow.java
    │   ├── TitleScreen.java
    │   ├── NewGameDialog.java
    │   └── OptionsDialog.java
    └── tutorial/
        ├── TutorialController.java
        ├── CoachPanel.java
        ├── HighlightLayer.java
        └── TutorialTargets.java

src/main/resources/spacecolony/version.properties

src/test/java/spacecolony/
├── LaunchArgsTest.java
├── sim/
│   ├── RefuelTest.java
│   ├── ColonizeTest.java
│   └── RandomEventsSwitchTest.java
├── save/SaveFileSchemaTest.java
├── options/OptionsStoreTest.java
├── tutorial/
│   ├── TutorialProgressTest.java
│   └── TutorialScriptTest.java
├── ui/
│   ├── TitleScreenTest.java
│   ├── StartupSmokeTest.java
│   └── TutorialTargetsTest.java
└── playtest/StartupDriver.java

src/test/resources/saves/v1-sample.json
```

### Modify

- `sim/World.java`, `sim/Ship.java`, `sim/Transit.java`, `sim/commands/Command.java`, `sim/commands/DispatchShipCommand.java`
- `sim/phases/CommandPhase.java`, `sim/phases/TransitPhase.java`, `sim/phases/EventPhase.java`
- `save/SaveFile.java` (schema v2, `writeJson`), `save/SaveSlots.java` (v1 is OK)
- `SpaceColonyApp.java`, `build.gradle.kts` (`version`, resource filtering, `startupPlayTest`)
- `ui/GameSession.java`, `ui/FileMenu.java`, `ui/SpaceColonyFrame.java`, `ui/TopBar.java`, `ui/DetailPanel.java`, `ui/ColonyListPanel.java`, `ui/BodyViewPanel.java`, `ui/SystemMapPanel.java`, `ui/dialogs/DispatchShipDialog.java`, `ui/dialogs/PlaceSiteDialog.java`
- Tests: `ui/GameSessionTest.java`, `ui/PanelSmokeTest.java`, `save/SaveSlotsTest.java`, `playtest/PlayTestDriver.java`
- Docs: the design doc's status line, and game-design spec §15 (the options note).

---

## Tasks

Tasks 1–7 are Part A: the sim fixes and their UI (headless, test-first). Tasks 8–14 cover options, session and File menu. Tasks 15–17 are the startup sequence. Tasks 18–24 are the tutorial. Tasks 25–26 are the play-test driver, docs and the PR.

Every task ends with `./gradlew test` green (under `xvfb-run` for tasks that touch frames) and a commit. Commit messages follow the existing `feat(<pkg>): …` / `test(<pkg>): …` / `fix(<pkg>): …` style.

### Task 0: Branch

- [ ] **Step 1: Create the branch from the latest `main`**

```bash
git checkout main && git pull
git checkout -b plan-6/startup-options-tutorial
xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test
```

Expected: 281 tests pass, 0 skipped.

---

## Part A — Making expansion playable

### Task 1: Random-events switch

**Files:** `sim/World.java`, `sim/commands/SetRandomEventsCommand.java`, `sim/commands/Command.java`, `sim/phases/CommandPhase.java`, `sim/phases/EventPhase.java`, test `sim/RandomEventsSwitchTest.java`

- [ ] **Step 1: Write the failing test**

```java
package spacecolony.sim;

class RandomEventsSwitchTest {
    private static final EnumSet<EventKind> RANDOM = EnumSet.of(EventKind.METEOR_STRIKE,
        EventKind.SOLAR_FLARE, EventKind.EQUIPMENT_FAILURE, EventKind.DISEASE_OUTBREAK);

    private static long randomEvents(World w, int ticks) {
        Simulator sim = new Simulator();
        long n = 0;
        for (int i = 0; i < ticks; i++) {
            Event last = w.recentEvents.peekLast();
            sim.advance(w);
            // count only events emitted this tick
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
        a.randomEventsEnabled = b.randomEventsEnabled = false;
        Simulator sa = new Simulator(), sb = new Simulator();
        for (int i = 0; i < 500; i++) { sa.advance(a); sb.advance(b); }
        assertEquals(SaveFile.toJson(a), SaveFile.toJson(b));
    }
}
```

If seed 1 happens to roll no random event in 5,000 ticks (the base rate is 0.0008 per body per tick over 10 bodies, so about 40 are expected), pick the first seed that does and note it in a comment.

- [ ] **Step 2: Implement**

- `World`: `public boolean randomEventsEnabled = true;` with a Javadoc line saying it's set at creation (the tutorial) or by `SetRandomEventsCommand`.
- `SetRandomEventsCommand(boolean enabled) implements Command`. Add it to `Command`'s `permits`, and add `case SetRandomEventsCommand re -> w.randomEventsEnabled = re.enabled();` to `CommandPhase.apply`.
- `EventPhase.run`: create the RNG exactly as today, then `if (!w.randomEventsEnabled) return;` before the body loop. `applyForced` is unchanged.

- [ ] **Step 3: Run and commit.** `./gradlew test` → `feat(sim): random-events switch and SetRandomEventsCommand`

### Task 2: Ships draw fuel from their origin

**Files:** `sim/phases/TransitPhase.java`, `sim/phases/CommandPhase.java`, test `sim/RefuelTest.java`, `playtest/PlayTestDriver.java`

- [ ] **Step 1: Write the failing tests**

```java
class RefuelTest {
    /** Earth Hub, a fresh hauler, and a Mars site to fly to. */
    private static World world() {
        World w = WorldGenerator.generate(1L);
        w.findBody("mars").sites.add(new Site("site-mars-1", "Mars 1", "mars", 0, 0, 100));
        return w;
    }

    @Test void newShip_departs_drawingExactlyTheCostFromOrigin() {
        World w = world();
        Site hub = w.findSite("site-earth-hub");
        hub.stockpile.put(Resource.FUEL, 1_000.0);
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("h1", "H1", ShipClass.HAULER, "site-earth-hub"));
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.METAL, 10.0)));
        for (int i = 0; i < 5 && w.findShip("h1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        Ship h = w.findShip("h1");
        assertEquals(ShipState.IN_TRANSIT, h.state);
        double cost = TransitPhase.fuelCost(w, ShipClass.HAULER, h.cargoMass(), "site-earth-hub",
            "site-mars-1", h.transit.departureTick(), h.transit.arrivalTick());
        assertEquals(1_000.0 - cost, hub.stockpile.get(Resource.FUEL), 1e-6);
        assertEquals(0.0, h.fuel, 1e-6);   // burned exactly what it drew
    }

    @Test void shipWithEnoughFuel_drawsNothing() { /* h.fuel = 10_000 before dispatch; hub FUEL unchanged */ }

    @Test void originShort_rejectedAtCommandTime() {
        // hub FUEL 1.0 → COMMAND_REJECTED whose message starts "Not enough fuel at Earth Hub"
    }

    @Test void manifestFuel_isCargo_loadedBeforeTheDraw() {
        // hub FUEL = 100 + cost; manifest FUEL 100 → departs; hub FUEL ends at ~0; cargo FUEL 100
    }
}
```

- [ ] **Step 2: Implement the departure draw** in `loadingAndUnloading`, where the manifest is filled, before the existing `s.fuel < cost` abort:

```java
if (s.fuel < cost) {
    double have = origin.stockpile.getOrDefault(Resource.FUEL, 0.0);
    double draw = Math.min(cost - s.fuel, have);
    if (draw > 0) {
        origin.stockpile.merge(Resource.FUEL, -draw, Double::sum);
        s.fuel += draw;
    }
}
```

- [ ] **Step 3: Update the command-time estimate** in `applyDispatchShip`:

```java
double originFuel = originSite.stockpile.getOrDefault(Resource.FUEL, 0.0);
double manifestFuel = ds.manifest().getOrDefault(Resource.FUEL, 0.0);
double available = s.fuel + Math.max(0.0, originFuel - manifestFuel);
if (available < estCost)
    throw new CommandRejectedException(String.format(
        "Not enough fuel at %s for this trip: need ≈%.0f, have %.0f", originSite.name, estCost, available));
```

Update any existing test that asserts the old "Insufficient fuel for dispatch" text.

- [ ] **Step 4: Drop the workaround.** In `PlayTestDriver.step7_midTransit`, remove `h.fuel = 1_000_000.0;` and top up `site-earth-hub` FUEL to 1,000 instead, so the step exercises the real draw.

- [ ] **Step 5: Run and commit.** `fix(sim): ships draw trip fuel from their origin's stockpile`

### Task 3: Body-bound transits

**Files:** `sim/Transit.java`, `sim/commands/DispatchShipCommand.java`, `sim/phases/CommandPhase.java`, `sim/phases/TransitPhase.java`, test `sim/ColonizeTest.java`

- [ ] **Step 1: Write the failing tests** (first half of `ColonizeTest`)

```java
class ColonizeTest {
    static World withColonizer() {
        World w = WorldGenerator.generate(1L);
        w.findSite("site-earth-hub").stockpile.put(Resource.FUEL, 1_000.0);
        new Simulator().advance(w);   // settle tick 0 → 1
        Simulator sim = new Simulator();
        sim.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, "site-earth-hub"));
        sim.advance(w);
        return w;
    }

    @Test void colonizer_toBody_departsForMars() {
        World w = withColonizer();
        Simulator sim = new Simulator();
        sim.enqueue(DispatchShipCommand.toBody("c1", "mars", Map.of(Resource.FOOD, 40.0)));
        for (int i = 0; i < 5 && w.findShip("c1").state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        Ship c = w.findShip("c1");
        assertEquals(ShipState.IN_TRANSIT, c.state);
        assertNull(c.transit.destSiteId());
        assertEquals("mars", c.transit.destBodyId());
        assertTrue(c.transit.arrivalTick() > c.transit.departureTick());
    }

    @Test void hauler_toBody_rejected() { /* message "Only colonizers can travel to a body without a site" */ }
    @Test void unknownBody_rejected() { /* "No such body: pluto" */ }
    @Test void dispatchCommand_requiresExactlyOneDestination() {
        assertThrows(IllegalArgumentException.class,
            () -> new DispatchShipCommand("c1", "site-earth-hub", "mars", Map.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new DispatchShipCommand("c1", null, null, Map.of()));
    }
}
```

- [ ] **Step 2: Extend the records**

```java
public record Transit(String originSiteId, String destSiteId, String destBodyId,
                      long departureTick, long arrivalTick, Map<Resource, Double> cargoSnapshot) {
    /** Site-bound trip (the Plan 1–5 shape). */
    public Transit(String originSiteId, String destSiteId, long departureTick, long arrivalTick,
                   Map<Resource, Double> cargoSnapshot) {
        this(originSiteId, destSiteId, null, departureTick, arrivalTick, cargoSnapshot);
    }
    /** The body this trip ends at: the dest site's body, or {@link #destBodyId}. */
    public String destBody(World w) {
        if (destBodyId != null) return destBodyId;
        Site s = w.findSite(destSiteId);
        return s == null ? null : s.bodyId;
    }
    ...
}

public record DispatchShipCommand(String shipId, String destSiteId, String destBodyId,
                                  Map<Resource, Double> manifest) implements Command {
    public DispatchShipCommand {
        if ((destSiteId == null) == (destBodyId == null))
            throw new IllegalArgumentException("exactly one of destSiteId, destBodyId");
    }
    public DispatchShipCommand(String shipId, String destSiteId, Map<Resource, Double> manifest) {
        this(shipId, destSiteId, null, manifest);
    }
    public static DispatchShipCommand toBody(String shipId, String bodyId, Map<Resource, Double> manifest) {
        return new DispatchShipCommand(shipId, null, bodyId, manifest);
    }
}
```

Every existing `new Transit(...)` with five arguments keeps compiling. The two places in `TransitPhase` that rebuild a `Transit` at departure must carry `destBodyId` through (use the six-argument form).

- [ ] **Step 3: Command phase.** In `applyDispatchShip`, resolve the destination body first: `destSiteId != null` → site (as today) → its body. Otherwise, the body must exist ("No such body: …") and the ship must be a COLONIZER ("Only colonizers can travel to a body without a site"). Use that body id for the fuel estimate. Build the LOADING transit with `new Transit(s.currentSiteId, ds.destSiteId(), ds.destBodyId(), w.tick, PENDING_ARRIVAL_TICK, snapshot)`.

- [ ] **Step 4: Transit math.** Replace `distanceBetweenSitesAtTicks(w, originSiteId, destSiteId, …)` with `distanceBetweenBodiesAtTicks(w, originBodyId, destBodyId, …)`, and have `computeArrivalTick` use `s.transit.destBody(w)`. Keep the public `fuelCost(w, class, mass, originSiteId, destSiteId, t0, t1)` signature for the debug overlay and `TransitFuelCostTest`, and add an overload `fuelCostToBody(w, class, mass, originSiteId, destBodyId, t0, t1)`. Both delegate to one private body-to-body method. Update the debug map overlay (`SystemMapPanel` transit prediction) to call `fuelCostToBody` when `destBodyId != null`.

- [ ] **Step 5: Run and commit.** `feat(sim): colonizers can fly to a body with no site`

### Task 4: Arriving in orbit

**Files:** `sim/Ship.java`, `sim/phases/TransitPhase.java`, `sim/phases/CommandPhase.java`, test `sim/ColonizeTest.java`

- [ ] **Step 1: Tests**

```java
@Test void colonizer_arrives_orbitingMars_withCargoAboard() {
    World w = withColonizer();
    Simulator sim = new Simulator();
    sim.enqueue(DispatchShipCommand.toBody("c1", "mars", Map.of(Resource.FOOD, 40.0)));
    for (int i = 0; i < 50 && w.findShip("c1").orbitingBodyId == null; i++) sim.advance(w);
    Ship c = w.findShip("c1");
    assertEquals(ShipState.IDLE, c.state);
    assertEquals("mars", c.orbitingBodyId);
    assertNull(c.currentSiteId);
    assertNull(c.transit);
    assertEquals(40.0, c.cargo.get(Resource.FOOD), 1e-6);
    assertTrue(w.recentEvents.stream().anyMatch(e -> e.message().contains("is orbiting Mars")));
}
@Test void orbitingColonizer_cannotBeDispatched() { /* "Ship is orbiting Mars; found a colony or retire it" */ }
@Test void orbitingColonizer_canBeRetired() { ... }
```

- [ ] **Step 2: Implement**

- `Ship`: `public String orbitingBodyId;`. Update the `currentSiteId` Javadoc: "null when IN_TRANSIT, or when IDLE in orbit of {@link #orbitingBodyId}".
- `advanceTransits`: when `arrivalTick` is reached and `transit.destBodyId() != null`, set `state = IDLE`, `orbitingBodyId = destBodyId`, `transit = null`, keep the cargo, and emit an INFO `SHIP_ARRIVED` event "Colonizer <name> is orbiting <body name>" with `bodyId` and `shipId` set. The site-bound branch is unchanged.
- `applyDispatchShip`: `if (s.orbitingBodyId != null) reject(...)` before the idle check.
- `currentBodyOf(w, ship)`: return `orbitingBodyId` when it's set.

- [ ] **Step 3: Run and commit.** `feat(sim): colonizers wait in orbit at an unsettled body`

### Task 5: Founding a colony from orbit

**Files:** `sim/phases/CommandPhase.java`, test `sim/ColonizeTest.java`

- [ ] **Step 1: Tests.** From the orbiting state of Task 4: `new BuildSiteCommand("site-mars-a", "Ares", "mars", 0.3, 1.2, "c1")`. After one tick, the site exists on Mars with one L1 HABITAT, its stockpile FOOD is 40, `c1` is gone, and the "Settle Mars" goal is achieved within one more tick. Also: the docked case in `SiteBaseTest` stays green unchanged.

- [ ] **Step 2: Implement.** `applyBuildSite` already goes through `currentBodyOf`, so orbiting colonizers now pass validation. After adding the site and before `w.ships.remove(colonizer)`, move all cargo:

```java
for (var e : colonizer.cargo.entrySet()) {
    if (e.getValue() > 1e-9) s.stockpile.merge(e.getKey(), e.getValue(), Double::sum);
}
```

- [ ] **Step 3: Run and commit.** `feat(sim): a new colony starts with its colonizer's cargo`

### Task 6: Save schema v2

**Files:** `save/SaveFile.java`, `save/SaveSlots.java`, test `save/SaveFileSchemaTest.java`, `save/SaveSlotsTest.java`, fixture `src/test/resources/saves/v1-sample.json`

- [ ] **Step 1: Capture the v1 fixture before changing anything:** on `main`'s code, write `SaveFile.toJson` of seed 1 after 200 ticks, with one hauler in transit, to `src/test/resources/saves/v1-sample.json`. (A throwaway `main` in the test tree does this; delete it afterwards.)

- [ ] **Step 2: Tests**

- v2 round-trip: a world with `randomEventsEnabled = false`, an orbiting colonizer and a body-bound LOADING transit survives `toJson` → `fromJson` → `toJson` unchanged.
- v1 loads: the fixture loads, `randomEventsEnabled` is true, every ship's `orbitingBodyId` is null, every transit's `destBodyId` is null, and `toJson` of the result has `"schemaVersion": 2`.
- v3 is rejected: a string with `"schemaVersion": 3` throws `IncompatibleSaveException(3, 2)`.
- `SaveSlotsTest`: a copy of the v1 fixture in the slots dir lists as `Status.OK` with its tick.

- [ ] **Step 3: Implement**

- `SCHEMA_VERSION = 2`, and `public static final int MIN_READABLE_VERSION = 1;`.
- `fromJson` accepts `MIN_READABLE_VERSION ≤ v ≤ SCHEMA_VERSION`.
- Write `randomEventsEnabled`, ship `orbitingBodyId` (null → `JsonNull`) and transit `destBodyId` (null → `JsonNull`). Read them as optional: an absent key gives the default. That's all the v1 migration needs.
- `SaveSlots.info`: OK when the version is in the readable range.

- [ ] **Step 4: Run and commit.** `feat(save): schema v2 for orbiting ships, body-bound transits and the events switch; v1 still loads`

### Task 7: Colonizing in the UI

**Files:** `ui/dialogs/DispatchShipDialog.java`, `ui/dialogs/PlaceSiteDialog.java`, `ui/DetailPanel.java`, `ui/ColonyListPanel.java`, `ui/SystemMapPanel.java`, test `ui/PanelSmokeTest.java`

- [ ] **Step 1: DispatchShipDialog.** Build the combo items as strings: every site id (so `PlayTestDriver` keeps selecting by id), then, when the ship is a COLONIZER, `"<Body name> (unsettled)"` for each body with no sites, mapped to the body id in a `LinkedHashMap<String, String>`. On OK: a site id gives `new DispatchShipCommand(id, siteId, manifest)`, and an unsettled label gives `DispatchShipCommand.toBody(id, bodyId, manifest)`. Extract the item building into a package-private static `destinations(World, Ship)` for the test.

- [ ] **Step 2: PlaceSiteDialog.** The colonizer search also matches `s.orbitingBodyId != null && s.orbitingBodyId.equals(bodyId)`.

- [ ] **Step 3: DetailPanel (ship).** When `orbitingBodyId != null`, the state line reads "Orbiting <Body name>", Dispatch… is hidden, and a **Found colony…** button does `engine.setSelection(Selection.body(bodyId)); engine.setView(BODY_VIEW);`. The body view already opens on the selected body.

- [ ] **Step 4: ColonyListPanel** shows "orbiting <Body name>" in the ship row's location slot. **SystemMapPanel** draws an orbiting ship as the normal ship dot, offset 6 px up and to the right of its body's centre.

- [ ] **Step 5: Tests** (`PanelSmokeTest`): `destinations` includes "Mars (unsettled)" for a colonizer and not for a hauler. `DetailPanel` with an orbiting colonizer selected paints, contains "Orbiting Mars", and has a "Found colony…" button and no "Dispatch…" button.

- [ ] **Step 6: Run under Xvfb and commit.** `feat(ui): dispatch colonizers to unsettled bodies and found colonies from orbit`

---

## Part B — Options, session, File menu

### Task 8: Options and OptionsStore

**Files:** `options/Options.java`, `options/OptionsStore.java`, test `options/OptionsStoreTest.java`

- [ ] **Step 1: Tests** (temp dir via `@TempDir`)

- `load()` with no file returns `Options.DEFAULTS`.
- Save then load returns an equal record.
- A file with `gameplay.autosaveMinutes=7` and `display.showSplash=false` loads `autosaveMinutes == 10` (the default; 7 isn't allowed) and `showSplash == false`.
- Unknown keys are ignored.
- After `save`, no `options.properties.tmp` remains.
- `OptionsStore.defaultFile()` honours `-Dspacecolony.optionsFile` (set and clear it in the test).

- [ ] **Step 2: Implement**

```java
package spacecolony.options;

public record Options(Speed startSpeed, int autosaveMinutes, boolean confirmQuit,
                      boolean showSplash, boolean startMaximized, int uiScalePercent,
                      boolean startInDebug, Level logLevel, boolean tutorialCompleted) {
    public static final List<Speed> START_SPEEDS = List.of(Speed.PAUSED, Speed.X1);
    public static final List<Integer> AUTOSAVE_MINUTES = List.of(0, 5, 10, 15, 30);
    public static final List<Integer> UI_SCALES = List.of(0, 100, 125, 150, 200);
    public static final List<Level> LOG_LEVELS = List.of(Level.FINE, Level.INFO, Level.WARNING);
    public static final Options DEFAULTS =
        new Options(Speed.PAUSED, 10, true, true, false, 0, false, Level.INFO, false);
    public Options withTutorialCompleted(boolean done) { ... }
}
```

`OptionsStore(Path file)`, `static OptionsStore defaultFile()` (`~/.space-colony/options.properties`, or the `spacecolony.optionsFile` property), `Options load()` (never throws; logs `WARNING` per bad key to `spacecolony.options`), and `void save(Options) throws IOException` (`Properties.store` to `.tmp`, then `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`; create parent dirs). Parse each key with a small helper `pick(Properties, key, List<T> allowed, Function<String,T> parse, T fallback)`.

- [ ] **Step 3: Run and commit.** `feat(options): persisted options file`

### Task 9: Launch arguments and version

**Files:** `LaunchArgs.java`, test `LaunchArgsTest.java`, `build.gradle.kts`, `src/main/resources/spacecolony/version.properties`

- [ ] **Step 1: Tests** as in design §7.2.

- [ ] **Step 2: Implement** `record LaunchArgs(Long seed, boolean debug, Level logLevel, String badLevel, boolean skipIntro)` with `static LaunchArgs parse(String[] args)`, lifting the loop from `SpaceColonyApp.main`. `logLevel` is null when not given, so options can fill it. `boolean skipTitle() { return seed != null; }`.

- [ ] **Step 3: Version.** In `build.gradle.kts`, add `version = "0.6.0"` and:

```kotlin
tasks.named<ProcessResources>("processResources") {
    inputs.property("version", project.version)
    filesMatching("spacecolony/version.properties") { expand("version" to project.version) }
}
```

`version.properties` contains `version=${version}`. Add a static `LaunchArgs.version()` (or a tiny `spacecolony.Version`) that reads it, falling back to `"dev"`.

- [ ] **Step 4: Run and commit.** `feat(app): launch-argument parsing and a build version`

### Task 10: Shared load path and `SaveFile.writeJson`

**Files:** `ui/SaveLoading.java`, `ui/FileMenu.java`, `save/SaveFile.java`

- [ ] **Step 1:** Move `FileMenu.loadFrom`'s worker and three error dialogs into `SaveLoading.load(Component owner, Path file, Consumer<World> onLoaded, Runnable onFinally)`. `FileMenu` calls it with `world -> { engine.reset(world); session.onLoaded(file); }` and restores the prior speed in `onFinally`. There's no behaviour change, and the existing load tests and `PlayTestDriver` steps 5–8 stay green.

- [ ] **Step 2:** Split `SaveFile.save` into `toJson` plus a new `public static void writeJson(String json, Path file) throws IOException` (the atomic tmp-and-move half). `save` becomes `writeJson(toJson(w), file)`.

- [ ] **Step 3: Run and commit.** `refactor(ui): one load path for the File menu and title screen`

### Task 11: GameSession modes, leave, background autosave

**Files:** `ui/GameSession.java`, test `ui/GameSessionTest.java`

- [ ] **Step 1: Tests** (existing style, with injected `Ui` and exit)

- `mode()` defaults to NORMAL.
- TUTORIAL `quit()` confirms, doesn't write any autosave, and exits.
- `leave()` autosaves to the current slot's autosave path, then runs the main-menu hook and doesn't call exit. Cancelling the confirm does neither.
- `confirmQuit = false` (via a `BooleanSupplier`) skips `ui.confirmQuit()` but still autosaves.
- `autosaveInBackground()` writes `_autosave.json` for an unnamed game (wait for the worker with a latch in the injected toast callback), toasts "Autosaved" through the injected `Ui`, and does nothing on a second call at the same tick.

- [ ] **Step 2: Implement**

```java
public enum Mode { NORMAL, TUTORIAL }

public GameSession(Engine engine, SaveSlots slots, Ui ui, IntConsumer exit,
                   Runnable mainMenu, BooleanSupplier confirmQuit) { ... }
// existing 4-arg constructor delegates with (null, () -> true)

public Mode mode();
public void setMode(Mode m);          // fires a listener list used by FileMenu and the frame title
public boolean canLeaveToMenu() { return mainMenu != null; }
public void quit()  { endSession(() -> exit.accept(0)); }
public void leave() { endSession(mainMenu); }

private void endSession(Runnable then) {
    EdtGuard.assertEdt();
    engine.setSpeed(Speed.PAUSED);
    if (confirmQuit.getAsBoolean() && !ui.confirmQuit(mode)) return;
    if (mode == Mode.NORMAL && !autosaveNow()) return;   // autosaveNow = today's try/catch + quitAnyway
    then.run();
}

/** Timed autosave: snapshot on the EDT, write off it. Skips when the tick hasn't moved. */
public void autosaveInBackground() { ... SaveFile.toJson(world) ... SwingWorker → SaveFile.writeJson ... }
```

`Ui.confirmQuit()` becomes `confirmQuit(Mode)` so the frame can word it: "Quit Space Colony?", "Return to the main menu? Your game will be autosaved." and "Leave the tutorial?". Add `Ui.toast(String)` and route `savedToast` through it. Update the existing `GameSessionTest` fakes.

- [ ] **Step 3: Run and commit.** `feat(ui): session modes, return to menu, background autosave`

### Task 12: Frame hooks, File menu, New Game dialog

**Files:** `ui/SpaceColonyFrame.java`, `ui/FileMenu.java`, `ui/startup/NewGameDialog.java`, test `ui/PanelSmokeTest.java`

- [ ] **Step 1: Frame hooks**

```java
public record Hooks(IntConsumer exit, Runnable mainMenu, Supplier<Options> options,
                    Consumer<Options> saveOptions) {
    public static Hooks standalone() {
        Options[] held = { Options.DEFAULTS };
        return new Hooks(System::exit, null, () -> held[0], o -> held[0] = o);
    }
}
public SpaceColonyFrame(Engine engine, ExceptionLog exceptions, Hooks hooks) { ... }
```

The two existing constructors delegate with `Hooks.standalone()`. The frame builds `GameSession` from the hooks (`confirmQuit` reads `hooks.options().get().confirmQuit()`) and listens to session mode changes to set its title ("Space Colony" or "Space Colony — Tutorial"). It exposes `session()` (already there) and `topBar()`.

- [ ] **Step 2: FileMenu.** Add **Options…** (pause, `OptionsDialog.show`, `hooks.saveOptions`, restore speed) and **Main Menu** (`session.leave()`, omitted when `!session.canLeaveToMenu()`), with separators as in design §3.5. Save and Save As… disable with the tooltip "Not available during the tutorial" while the mode is TUTORIAL. New Game shows `NewGameDialog.show(owner)` after the existing discard confirm. `OptionsDialog` arrives in Task 14: until then, Options… is added but its action shows a placeholder message, and Task 14 replaces it.

- [ ] **Step 3: NewGameDialog.** `static Long show(Component owner)`: a modal `JDialog` with the seed field, Randomize, validation label, and Start/Cancel as in design §3.6. The seed check is a package-private `static String validate(String)` (null when OK).

- [ ] **Step 4: Tests.** The File menu has 9 components with `Hooks.standalone()` and 10 with a `mainMenu` hook. `NewGameDialog.validate` accepts `-5` and `12345678901` and rejects an empty value and `12a`. In TUTORIAL mode, Save and Save As… are disabled.

- [ ] **Step 5: Run under Xvfb and commit.** `feat(ui): frame hooks, Options and Main Menu items, New Game dialog`

### Task 13: Timed autosave

**Files:** `ui/AutosaveTimer.java`, `ui/SpaceColonyFrame.java`, `ui/TopBar.java`

- [ ] **Step 1: Implement** `AutosaveTimer(GameSession session)`, which wraps a Swing `Timer` with `setMinutes(int)` (0 stops it) and `stop()`. Each fire calls `session.autosaveInBackground()`, which already skips TUTORIAL and unchanged ticks. The frame owns one, sets it from `hooks.options().get().autosaveMinutes()`, re-applies it after Options… is accepted, and stops it in `dispose()`. `TopBar.toast` is reused for "Autosaved" and "Autosave failed".

- [ ] **Step 2: Test** (`GameSessionTest` covers the save itself): `AutosaveTimer.setMinutes(5)` gives a running timer with a 300,000 ms delay, and `setMinutes(0)` stops it.

- [ ] **Step 3: Run and commit.** `feat(ui): timed autosave`

### Task 14: Options dialog

**Files:** `ui/startup/OptionsDialog.java`, test `ui/StartupSmokeTest.java`

- [ ] **Step 1: Implement** `static Options show(Component owner, Options current)`: a modal `JDialog` with a `JTabbedPane` of Gameplay, Display, Controls and Developer, per design §4.3. Combos are built from the `Options` value lists with display names ("Off", "Every 5 minutes", "Automatic", "125%", …). Restore defaults resets the controls from `Options.DEFAULTS` but keeps `current.tutorialCompleted()`. On macOS (`os.name` starts with "Mac"), the scale combo is disabled with the note "macOS applies display scaling itself".

- [ ] **Step 2: Controls tab.** Build a `JTable` from a static list of `(action, KeyStroke)` rows. It reads `DebugController.TOGGLE_KEY`, `DebugController.STEP_KEY`, the Debug menu's run/inspector/log `KeyStroke`s (promote them to public constants on `DebugMenu`) and the File menu's accelerators (promote `FileMenu.withKey`'s keys to public constants). Render with `KeyEvent.getModifiersExText` + `KeyEvent.getKeyText`.

- [ ] **Step 3:** Wire `FileMenu`'s Options… to it (replacing the Task 12 placeholder), and have `SpaceColonyFrame` apply the result: save via hooks, reset the autosave timer, and `DebugLogging.current().setLevel(...)` when installed.

- [ ] **Step 4: Tests.** The dialog's content pane builds and paints offscreen, with 4 tabs. The Controls table has a row for "Toggle debug mode" with "Ctrl+D". Collecting from the controls after changing the autosave combo to "Every 5 minutes" gives `autosaveMinutes == 5` (factor the collect step into a package-private method).

- [ ] **Step 5: Run and commit.** `feat(ui): Options dialog`

---

## Part C — Startup sequence

### Task 15: Starfield and splash

**Files:** `ui/startup/Starfield.java`, `ui/startup/SplashWindow.java`, test `ui/StartupSmokeTest.java`

- [ ] **Step 1: Starfield.** `final class Starfield { Starfield(long seed); void paint(Graphics2D g, int w, int h, double t); }`. It fills with `UiColors.STARFIELD_BG` and draws 300 stars from a seeded `Random` (positions as fractions of the size, so they survive resizes) with 1–2 px dots in three brightnesses. The sun is a radial-gradient disc at (−0.1w, 0.75h) with radius 0.35h. Three orbit arcs use `UiColors.ORBIT_LINE`, and small planet dots sit on the arcs at angle `phase + t * speed` (`t` in seconds; `t = 0` is static). Antialiasing is on.

- [ ] **Step 2: SplashWindow.** `JWindow`, 640 × 360, centred. It paints `Starfield` at `t = 0`, then "SPACE COLONY" (bold 44 pt, `UiColors.FOREGROUND`), `"v" + version` (dim, 13 pt), the status text, and a 200 px progress bar (`UiColors.INFO` on `PANEL_BORDER`). `setStatus(String text, double fraction)`. `onSkip(Runnable)` registers a mouse and key listener.

- [ ] **Step 3: Tests.** `Starfield.paint` onto a 320 × 180 `BufferedImage` leaves at least 50 non-background pixels, and is identical for the same seed. `SplashWindow`'s content paints into a `BufferedImage` without throwing (build it only when not headless; otherwise test its content `JComponent` directly).

- [ ] **Step 4: Run and commit.** `feat(ui): starfield backdrop and splash window`

### Task 16: Title screen

**Files:** `ui/startup/TitleScreen.java`, test `ui/TitleScreenTest.java`

- [ ] **Step 1: Tests** (pure):

```java
enum Action { CONTINUE, NEW_GAME, TUTORIAL, LOAD, OPTIONS, QUIT }

@Test void firstRun_defaultsToTutorial() {
    assertEquals(TUTORIAL, TitleScreen.defaultAction(Options.DEFAULTS, List.of()));
    assertTrue(TitleScreen.showNewPlayerBanner(Options.DEFAULTS, List.of()));
}
@Test void withSaves_defaultsToContinue() { ... one OK SlotInfo → CONTINUE, no banner }
@Test void onlyUnreadableSaves_defaultsToNewGame() { ... }
@Test void tutorialDone_noSaves_defaultsToNewGame_noBanner() { ... }
@Test void continueLabel() {
    // SlotInfo name "colony", tick 1215, modified 2 h before `now` → "colony · Y3 D121 · 2 hours ago"
}
```

`continueTarget(List<SlotInfo>)` returns the first `Status.OK` entry (the list is newest first). An unnamed autosave is labelled "Unnamed game".

- [ ] **Step 2: Implement** `TitleScreen extends JFrame`. The content is a `JComponent` that paints the animated `Starfield` (30 fps `Timer`, started on `windowOpened`/`componentShown` and stopped when hidden), with a centred `BoxLayout` column: title, subtitle "A solar-system logistics game", the optional banner, and six 280 px buttons styled with the game palette (Continue has a second dim line via HTML). The footer holds the version and "Debug mode on". Up/Down/Enter/Escape go through the root pane's `InputMap`. It takes `TitleScreen.Listener` (one method per action) and `update(Options, List<SlotInfo>, boolean debugNext)` to refresh the enablement, labels, banner and default button.

- [ ] **Step 3: Run and commit.** `feat(ui): title screen`

### Task 17: AppController and new `main`

**Files:** `ui/startup/AppController.java`, `SpaceColonyApp.java`

- [ ] **Step 1: AppController**

```java
public final class AppController implements TitleScreen.Listener {
    public AppController(Options options, OptionsStore store, SaveSlots slots,
                         ExceptionLog exceptions, LaunchArgs args) { ... }

    /** Splash (unless skipped) → title, or straight into a game for --seed. */
    public void start();

    // TitleScreen.Listener
    void onContinue();  void onNewGame();  void onTutorial();
    void onLoad();      void onOptions();  void onQuit();

    void startGame(World world, String slotOrNull, GameSession.Mode mode);
    void returnToTitle();

    /** For CrashHandler: route to the game frame when one is showing. */
    public boolean debugOn();
    public void report(Throwable t) throws Exception;
}
```

- `start()`: if `args.skipTitle()` → `startGame(WorldGenerator.generate(seed), null, NORMAL)`. Otherwise show the splash (unless skipped or headless), list slots in a `SwingWorker`, and wait for both the worker and a 1,500 ms one-shot `Timer` (or a skip click after the worker). Then dispose the splash and show the title with the list.
- `startGame`: `new Engine(world)`, `new SpaceColonyFrame(engine, exceptions, hooks)` with `hooks.mainMenu = this::returnToTitle`, `exit = System::exit`, and options get/save through `store`. Then `engine.setDebugEnabled(args.debug() || options.startInDebug())`, `engine.setSpeed(options.startSpeed())` for NORMAL (PAUSED for TUTORIAL; the tutorial's step 2 starts the clock), maximize if asked, `session.setMode(mode)`, set the slot name, hide the title, and show the frame. TUTORIAL mode also creates the `TutorialController` (Task 23; until then this branch is left out and `onTutorial` starts a NORMAL game on `TutorialScenario.world()` so the flow can be tried).
- `returnToTitle`: `frame.gameLoop().dispose()`, dispose the tutorial controller, `frame.dispose()`, re-list slots (worker), and `title.update(...)` + show.
- Continue/Load go through `SaveLoading.load(title, path, world -> startGame(world, slots.slotOf(path), NORMAL), () -> {})`.
- Options from the title: `OptionsDialog.show`, save, and `title.update`.
- `report`: with a frame showing, `frame.showCrashDialog(t)`. Otherwise, a two-button `JOptionPane` on the title (Continue / Quit → `System.exit(0)`).

- [ ] **Step 2: SpaceColonyApp.main**

```java
LaunchArgs args = LaunchArgs.parse(argv);
OptionsStore store = OptionsStore.defaultFile();
Options options = store.load();
if (options.uiScalePercent() != 0 && System.getProperty("sun.java2d.uiScale") == null)
    System.setProperty("sun.java2d.uiScale", options.uiScalePercent() / 100.0 + "");
Level level = args.logLevel() != null ? args.logLevel() : options.logLevel();
// logging install, bad-level warning, ExceptionLog: as today
SwingUtilities.invokeLater(() -> {
    AppController app = new AppController(options, store, SaveSlots.defaultDir(), exceptions, args);
    CrashHandler.install(new CrashHandler(exceptions, app::debugOn, app::report));
    app.start();
});
```

Update the class Javadoc to mention `--skip-intro` and that `--seed` skips the title.

- [ ] **Step 3: Check by hand under Xvfb.** `./gradlew play` shows the splash, then the title (take a screenshot with `import -window root`). `./gradlew play --args="--seed 7"` opens the game directly. Existing drivers (`debugPlayTest`, `cacheCheck`) still pass, because they build frames directly.

- [ ] **Step 4: Run and commit.** `feat(app): splash → title → game startup sequence`

---

## Part D — Tutorial

### Task 18: Target keys on the panels

**Files:** `ui/TopBar.java`, `ui/ColonyListPanel.java`, `ui/DetailPanel.java`, `ui/BodyViewPanel.java`

- [ ] **Step 1:** Add `setName(...)` calls as in design §5.4: `topbar.speed.x1`, `topbar.speed.x16`, `topbar.tech`, `topbar.goals`, `colonylist` (the list component itself), `detail` (the panel), `detail.buildBuilding`, `detail.buildShip`, `detail.dispatch`, `detail.foundColony`, and `bodyview.sphere` (the sphere component, or the panel if the sphere is painted directly). Define the keys as `public static final String` constants on each panel, for example `TopBar.TARGET_X1`, so the script refers to constants rather than string literals. `tutorial` can't import `ui`, so the script holds its own string copies, and `TutorialTargetsTest` (Task 24) checks that the two sets match.

- [ ] **Step 2: Run and commit.** `feat(ui): name the controls the tutorial points at`

### Task 19: Tutorial core types

**Files:** `tutorial/TutorialContext.java`, `tutorial/TutorialStep.java`, `tutorial/TutorialProgress.java`, test `tutorial/TutorialProgressTest.java`

- [ ] **Step 1: Types**

```java
public record TutorialContext(World world, Speed speed, Selection selection, EngineEvent.ViewChanged.View view) {}

public record TutorialStep(String id, String title, String html, List<String> targets, String hint,
                           Predicate<TutorialContext> done) {
    /** Targets in priority order; the first one showing gets the highlight. Empty for none. */
    /** Manual steps have no completion test and show a Next button. */
    public boolean manual() { return done == null; }
}

public final class TutorialProgress {
    public TutorialProgress(List<TutorialStep> steps);
    public int index();  public TutorialStep current();  public int size();
    public boolean finished();                  // index == size - 1 and it's the last (manual) step
    /** Advance past every consecutive automatic step whose test passes. True if the index moved. */
    public boolean update(TutorialContext c);
    public void next();                         // manual steps only (assert)
    public void skip();                         // any step; no-op on the last
}
```

- [ ] **Step 2: Tests** with a four-step toy script (manual, auto A, auto B, manual): `update` doesn't pass a manual step, `next` then `update` with A and B both true lands on the last step in one call, `skip` always advances, and the last step can't be skipped past.

- [ ] **Step 3: Run and commit.** `feat(tutorial): step, context and progress rules`

### Task 20: Scenario and script, with a headless walk-through

**Files:** `tutorial/TutorialScenario.java`, `tutorial/TutorialScript.java`, test `tutorial/TutorialScriptTest.java`

- [ ] **Step 1: Scenario.** `TUTORIAL_SEED = 20260927L`. `world()` generates it, sets `randomEventsEnabled = false`, and sets Earth Hub FUEL 400, FOOD 300 and WATER 300.

- [ ] **Step 2: Script.** `static List<TutorialStep> steps()` returns the 12 steps of design §5.3. The text is short HTML paragraphs with the UI labels in `<b>`. Completion tests use `GoalCatalog`/`World` lookups only. Hints: step 7 "Select Earth Hub first.", step 8 "Select your colonizer in the colony list.", step 10 "Select the colonizer, or open Mars's body view." Target keys are string constants mirrored from Task 18.

- [ ] **Step 3: Walk-through test.** Build an `Engine` on `TutorialScenario.world()` inside `Edt.run`. Mirror what the UI does at each step and call `progress.update(ctx(engine))` after each action (plus `engine.tick()` where the sim must run). Assert `progress.current().id()` after each action:

```
welcome → next() → start-clock
setSpeed(X1) → select-hub
setSelection(site earth hub) → read-dock → next() → build-lab
enqueue(BuildBuilding RESEARCH_LAB) + tick → research
enqueue(QueueResearch "basic-mining") + tick → build-colonizer
enqueue(BuildShip COLONIZER "c1") + tick → dispatch
enqueue(toBody("c1","mars", FOOD 40, WATER 40, METAL 20)) + tick → fast-forward
tick until orbiting (≤ 30 ticks) → found-colony
enqueue(BuildSite on mars from "c1") + tick → goals   (and "first-mars-colony" achieved)
next() → finish
```

Add a second test for working ahead: building the colonizer while on `build-lab` goes to `research` when the lab is done, then skips `build-colonizer` after research is queued. A third test checks labels: every `<b>…</b>` in every step's HTML is in a fixed set of real labels (`"1×"`, `"16×"`, `"Earth Hub"`, `"Build building..."`, `"RESEARCH_LAB"`, `"Tech"`, `"Build ship..."`, `"COLONIZER"`, `"Dispatch..."`, `"Mars (unsettled)"`, `"Found colony…"`, `"Goals"`, `"Keep playing"`, `"Main menu"`). Task 24 checks that this set matches the real components.

- [ ] **Step 4: Run and commit.** `feat(tutorial): tutorial world and the 12-step script`

### Task 21: Targets and highlight

**Files:** `ui/tutorial/TutorialTargets.java`, `ui/tutorial/HighlightLayer.java`

- [ ] **Step 1:** `TutorialTargets.find(Container root, String key)`: depth-first over `getComponents()` (and a `JRootPane`'s content and layered panes), returning the first component with `key.equals(getName()) && isShowing()`.

- [ ] **Step 2:** `HighlightLayer extends JComponent`: non-opaque, `contains(int, int)` returns false, and `setTarget(Component c)` (null hides it). It paints a 2 px rounded rectangle, inflated by 4 px, around `SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), this)`. The alpha pulses 90–255 on a 50 ms `Timer` (running only while there's a target). A `ComponentListener` on the target and its ancestors triggers repaints.

- [ ] **Step 3: Tests** (`TutorialTargetsTest`, Xvfb): `find` returns the Tech button in a built frame, and null for a hidden component. `HighlightLayer.contains` is false at its centre. With the layer covering the frame and targeting the 1× button, a `Robot` click at the button's centre sets the speed to X1.

- [ ] **Step 4: Run and commit.** `feat(ui): tutorial target lookup and highlight layer`

### Task 22: Coach card

**Files:** `ui/tutorial/CoachPanel.java`

- [ ] **Step 1: Implement** a `JPanel` with rounded `PANEL_BACKGROUND` and an `INFO` 1 px border, 340 px wide. It has a header ("Step 5 of 12", dim), a title (bold 15 pt), a body (`JEditorPane` "text/html", non-editable, transparent, with a stylesheet in the game palette), a hint label (`WARNING` colour, hidden when empty), and a button row: Next (visible for manual steps), Skip step, Exit tutorial, and a ▾/▸ collapse toggle. The last step replaces the row with **Keep playing** and **Main menu**. The API is `show(TutorialStep, int index, int size, boolean targetVisible)` plus a `Listener { next(); skip(); exit(); keepPlaying(); mainMenu(); }`.

- [ ] **Step 2: Test** (`StartupSmokeTest`): paint the card for step 1 and step 12 offscreen. Step 12 has Keep playing and Main menu and no Skip step.

- [ ] **Step 3: Run and commit.** `feat(ui): tutorial coach card`

### Task 23: Tutorial controller and session wiring

**Files:** `ui/tutorial/TutorialController.java`, `ui/startup/AppController.java`

- [ ] **Step 1: Implement**

```java
public final class TutorialController implements CoachPanel.Listener {
    public TutorialController(SpaceColonyFrame frame, Engine engine, Runnable toMainMenu,
                              Runnable markCompleted) { ... }
    public void dispose();
}
```

- The constructor adds `HighlightLayer` and `CoachPanel` to `frame.getLayeredPane()` at `PALETTE_LAYER`, lays them out on `componentResized` (the card at the lower left of the centre region, above the south stack), registers an `EngineListener`, and renders step 0.
- On `WorldChanged`/`SelectionChanged`/`SpeedChanged`/`ViewChanged`: `progress.update(ctx)`. On a change, render and log. Always re-resolve the target, because selection changes swap the detail panel's buttons.
- On `WorldReplaced`: `endTutorial(false)`, and switch the session to NORMAL without enabling events (the new world has its own flag).
- `keepPlaying()`: `engine.enqueue(new SetRandomEventsCommand(true))`, `session.setMode(NORMAL)`, `markCompleted`, then remove the UI.
- `mainMenu()`: `markCompleted`, then `session.leave()` (TUTORIAL mode, so no autosave).
- `exit()`: a three-option dialog (Keep playing / Main menu / Cancel) that routes to the two methods above.
- Reaching `finish` also calls `markCompleted`.

- [ ] **Step 2: AppController:** `onTutorial()` → `startGame(TutorialScenario.world(), null, TUTORIAL)`. `startGame` creates the controller for TUTORIAL with `markCompleted = () -> saveOptions(options.withTutorialCompleted(true))`. `returnToTitle` disposes it.

- [ ] **Step 3: Run and commit.** `feat(tutorial): run the tutorial in a real game`

### Task 24: Target and label checks against the real UI

**Files:** test `ui/TutorialTargetsTest.java`

- [ ] **Step 1: Tests** (Xvfb): build a `SpaceColonyFrame` on `TutorialScenario.world()`, show it offscreen-sized, and for each automatic step, put the UI into the state its step expects (select the hub; build a colonizer via commands and select it; make it orbit via ticks and select it; switch to Mars body view). Assert that at least one of `step.targets()` resolves with `TutorialTargets.find(frame, key)`. Also assert that every panel `TARGET_*` constant equals its string copy in `TutorialScript`, and that each bold label from Task 20's set appears as the text of some button, list cell, combo item or menu item in the corresponding state.

- [ ] **Step 2: Run under Xvfb and commit.** `test(ui): tutorial targets and labels match the real UI`

---

## Part E — Verification and PR

### Task 25: Startup play-test driver

**Files:** `playtest/StartupDriver.java`, `build.gradle.kts`

- [ ] **Step 1:** Register `startupPlayTest` like `debugPlayTest`, with `systemProperty("spacecolony.savesDir", …)` and `systemProperty("spacecolony.optionsFile", …)` pointing under `build/playtest/startup`, which is cleaned at the start.

- [ ] **Step 2: Driver.** Call `SpaceColonyApp.main(new String[0])`, then script design §7.4 with `Robot` clicks found through `TutorialTargets` and button text. It prints `PASS`/`FAIL` per step like the other drivers, saves screenshots to `build/playtest/tutorial-NN.png`, and exits non-zero on any failure. For step 9 of the tutorial, it calls `engine.advanceSilently(1)` in a loop on the EDT until the colonizer orbits, instead of waiting in real time. It calls `frame.toFront(); frame.requestFocus()` before key presses (Xvfb has no window manager).

- [ ] **Step 3:** `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew startupPlayTest` passes. Commit `test(playtest): startup, options and tutorial driver`.

### Task 26: Docs, full run, PR

- [ ] **Step 1:** Mark the design doc's status line "Implemented (Plan 6)". In the game-design spec §15, strike the options/tutorial-related items if any and add a line recording that options live in `~/.space-colony/options.properties`. Update the `SpaceColonyApp` Javadoc and the `play` task description.
- [ ] **Step 2:** `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test debugPlayTest startupPlayTest`. Expected: about 332 tests, 0 skipped, both drivers PASS.
- [ ] **Step 3:** Run the design §7.6 manual checklist on a desktop (the parts Xvfb can't show: real splash timing, UI scale, macOS behaviour).
- [ ] **Step 4:** Push and open the PR. Its body lists what a player sees before and after, and links this plan.

---

## Self-review notes

- **Order matters for Part A.** Task 2 (refuel) comes before Task 3 because `ColonizeTest` relies on a new colonizer being able to depart at all.
- **Keeping the old shapes compiling.** The five-argument `Transit` constructor and the three-argument `DispatchShipCommand` constructor keep every existing caller and test unchanged. Only `TransitPhase`'s departure rebuild has to switch to the six-argument form, or it would drop `destBodyId`.
- **The package graph stays acyclic.** `ui` never imports `ui.startup` or `ui.tutorial`, and `tutorial` never imports `ui`. The target-key strings are the one duplicated piece, and Task 24 checks them.
- **Determinism.** The events switch is persisted and command-driven, and refuel is pure arithmetic on existing state. `DeterminismCheck` and `DeterminismTest` still apply unchanged.
