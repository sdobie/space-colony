# Space Colony — Plan 4: Save/Load + Sim-Side Tech Wiring

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land save/load (hand-rolled JSON, schema-versioned), wire all 17 v1 techs into sim phases via a new `TechEffects` static class, give HABITAT buildings real effect on `populationCap`, and enforce EDT discipline on `Engine` via `-ea` assertions — turning the Plans 1–3 playable shell into a session-to-session game where research and habitats actually matter.

**Architecture:** Adds two new packages (`spacecolony.save` for JSON IO and `spacecolony.engine.EdtGuard`), one new sim helper (`spacecolony.sim.TechEffects`), one new UI menu (`spacecolony.ui.FileMenu`), and one new engine method (`Engine.reset(World)`). Existing phases (`ProductionPhase`, `TransitPhase`, `EventPhase`, `ResearchPhase`, `CommandPhase`) gain ~12 lines of multiplier wiring each at known call sites. Save format persists only player state (sites/buildings/stockpiles/ships/tech/goals/events/credits/tick/seed); world geometry regenerates via `WorldGenerator.generate(seed)` on load. The save package depends on `sim` + `world`, never the reverse.

**Tech Stack:** Java 25, Gradle 9.0.0 via wrapper, JUnit 5, Swing. Hand-rolled JSON (no Jackson/Gson). No new runtime dependencies.

**Spec reference:** `/Users/steve/projects/space-colony/docs/superpowers/specs/2026-05-31-plan-4-save-and-sim-wiring-design.md` (sections 1–8).

**Out of scope (Plan 5):** Debug Mode panel from game-design spec §8; tech tree / goals / events panel polish; autosave; multiple-slot picker UI; save thumbnails. UI surfacing of `moraleCeiling > 1.0` for life-support techs also waits until Plan 5.

---

## Context

Plans 1–3 (on `main`) shipped: a pure sim core with 8 phases, a deterministic world generator, a Swing UI shell with 5 panels, a `GameLoop` driving `Engine.tick` from a Swing `Timer`, and 86 tests. Research currently completes but does nothing; HABITAT buildings raise no caps; there's no save/load. Plan 4 closes those three gaps in a single bundled PR.

Key existing structure the engineer needs to know:

- `spacecolony.sim.Simulator` is a thin orchestrator that calls phase classes in `spacecolony.sim.phases.*` (`CommandPhase`, `TransitPhase`, `ProductionPhase`, `EventPhase`, `ResearchPhase`, `GoalPhase`). All sim mutations happen in those phase classes — `Simulator.java` itself is ~40 lines.
- `spacecolony.engine.Engine` wraps a `World` + `Simulator` and notifies UI via a sealed `EngineEvent` hierarchy (`WorldChanged`, `SelectionChanged`, `SpeedChanged`, `ViewChanged`).
- `spacecolony.engine.GameLoop` runs a Swing `Timer` that calls `Engine.tick` on the EDT.
- `Site` constructor today is `Site(String id, String name, String bodyId, double lat, double lon, int populationCap)`. Plan 4 replaces the trailing `populationCap` with `siteBase` (200 for Earth Hub, 100 for colonizer-planted).
- `Engine.world` is currently `final`. Plan 4 drops `final` so `Engine.reset(World)` can swap worlds on load.
- 7 panels implement `EngineListener` and currently respond to `WorldChanged` (and some to `SelectionChanged`): `ColonyListPanel`, `DetailPanel`, `SphereMiniRenderer`, `BodyViewPanel`, `TopBar`, `SystemMapPanel`, `EventStripPanel`. All must also treat the new `WorldReplaced` event as a full rebind trigger.
- `Body.resourceYields` is typed as `ResourceYieldSampler` (interface in `sim`), implemented by `spacecolony.world.ResourceYieldMap`. Gas-giant bodies already yield FUEL via the sampler; `atm-mining` tech is the gate that enables MINE-on-gas-giant.

---

## File Structure

### Create

```
src/main/java/spacecolony/
├── sim/
│   └── TechEffects.java                              (static multipliers, sim-only deps)
├── engine/
│   └── EdtGuard.java                                 (assertEdt / assertNotEdt helpers)
├── save/                                              (NEW PACKAGE)
│   ├── IncompatibleSaveException.java
│   ├── JsonValue.java                                (sealed hierarchy)
│   ├── JsonParseException.java
│   ├── JsonReader.java
│   ├── JsonWriter.java
│   └── SaveFile.java
└── ui/
    └── FileMenu.java                                 (JMenuBar with File menu)

src/test/java/spacecolony/
├── sim/
│   ├── TechEffectsTest.java
│   ├── ProductionTechEffectsTest.java
│   ├── TransitTechEffectsTest.java
│   ├── AtmMiningTest.java
│   ├── EventTechEffectsTest.java
│   ├── ResearchTechEffectsTest.java
│   ├── HabitatCapTest.java
│   └── SiteBaseTest.java
├── engine/
│   ├── EngineResetTest.java
│   └── EngineEdtTest.java
└── save/
    ├── JsonReaderTest.java
    ├── JsonWriterTest.java
    ├── SaveFileSaveTest.java
    └── SaveFileLoadTest.java
```

### Modify

- `build.gradle.kts` — add `jvmArgs("-ea")` to `test`, `run`, `play` tasks.
- `src/main/java/spacecolony/sim/Site.java` — replace `populationCap` constructor parameter with `siteBase`; add `public final int siteBase` field.
- `src/main/java/spacecolony/world/WorldGenerator.java` — pass `200` as `siteBase`.
- `src/main/java/spacecolony/sim/phases/CommandPhase.java` — pass `100` as `siteBase` in `applyBuildSite`; later, add fuel-cost multiplier in `applyDispatchShip`.
- `src/main/java/spacecolony/sim/Simulator.java` — add package-private `clearCommands()` method.
- `src/main/java/spacecolony/engine/EngineEvent.java` — add `WorldReplaced` sealed permitee.
- `src/main/java/spacecolony/engine/Engine.java` — drop `final` on `world`; add `reset(World)`; install EDT guards on `enqueue`, `tick`, `addListener`, `removeListener`, `reset`.
- `src/main/java/spacecolony/sim/phases/ProductionPhase.java` — TechEffects multipliers on power, mine, farm, refinery, morale ceiling, plus HABITAT cap recompute.
- `src/main/java/spacecolony/sim/phases/TransitPhase.java` — fuel-cost multiplier at departure.
- `src/main/java/spacecolony/sim/phases/EventPhase.java` — disease severity multiplier.
- `src/main/java/spacecolony/sim/phases/ResearchPhase.java` — research lab multiplier.
- `src/main/java/spacecolony/ui/SpaceColonyFrame.java` — mount `FileMenu` as `JMenuBar`.
- 7 panels — handle new `WorldReplaced` event in their `onEvent` switches: `ColonyListPanel`, `DetailPanel`, `SphereMiniRenderer`, `BodyViewPanel`, `TopBar`, `SystemMapPanel`, `EventStripPanel`.

---

## Tasks

### Task 0: Branch + Gradle assertions

Create the feature branch and enable JVM assertions on the `test`, `run`, and `play` tasks so EDT guards fire during development.

**Files:**
- Modify: `/Users/steve/projects/space-colony/build.gradle.kts`

- [x] **Step 1: Set JAVA_HOME and create branch**

```bash
cd /Users/steve/projects/space-colony
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
git checkout main
git pull
git checkout -b plan-4/save-and-sim-wiring
```

Expected: switched to `plan-4/save-and-sim-wiring` from latest `main`.

- [x] **Step 2: Enable assertions in Gradle tasks**

Edit `build.gradle.kts`. Replace the `tasks.named<Test>("test")` block and the `tasks.named<JavaExec>("run")` block, and the registered `play` task, so each has `jvmArgs("-ea")`. The final file should read:

```kotlin
plugins {
    application
    java
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "spacecolony.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    jvmArgs("-ea")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgs("-ea")
}

tasks.register<JavaExec>("render-demo") {
    group = "application"
    description = "Render one flat-map + sphere PNG per BodyType for visual inspection."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.render.RenderDemo"
}

tasks.register<JavaExec>("play") {
    group = "application"
    description = "Launch the Swing UI."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.SpaceColonyApp"
    standardInput = System.`in`
    jvmArgs("-ea")
}
```

- [x] **Step 3: Verify build still passes**

```bash
./gradlew test
```

Expected: all 86 existing tests PASS.

- [x] **Step 4: Commit**

```bash
git add build.gradle.kts
git commit -m "chore(build): enable -ea on test/run/play tasks for EDT assertions"
```

---

### Task 1: Add `Site.siteBase` field

`Site` needs a `siteBase` field (200 for Earth Hub, 100 for colonizer-planted) so the HABITAT cap formula in §5 of the spec can compute `siteBase + Σ(habitat.level × 100)`. This is a constructor signature change that ripples through `WorldGenerator` and `CommandPhase`.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/Site.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/world/WorldGenerator.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/CommandPhase.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/SiteBaseTest.java`

- [x] **Step 1: Write the failing test**

```java
// src/test/java/spacecolony/sim/SiteBaseTest.java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SiteBaseTest {
    @Test
    void earthHub_siteBase_is200() {
        World w = WorldGenerator.generate(1L);
        Site earthHub = w.findSite("site-earth-hub");
        assertEquals(200, earthHub.siteBase);
        assertEquals(200, earthHub.populationCap, "initial cap equals siteBase before HABITAT boost is computed");
    }

    @Test
    void colonizerPlantedSite_siteBase_is100() {
        World w = WorldGenerator.generate(2L);
        // Inject a colonizer at Mars manually so we can fire BuildSiteCommand without
        // running a multi-tick transit.
        Ship colonizer = new Ship("colo-1", "Colo", ShipClass.COLONIZER, "site-mars-stub");
        Site marsStub = new Site("site-mars-stub", "Mars Stub", "mars", 0.1, 0.1, 100);
        w.findBody("mars").sites.add(marsStub);
        w.ships.add(colonizer);

        Simulator sim = new Simulator();
        sim.enqueue(new BuildSiteCommand("site-mars-new", "Mars New", "mars", 0.2, 0.2, "colo-1"));
        sim.advance(w);

        Site planted = w.findSite("site-mars-new");
        assertNotNull(planted);
        assertEquals(100, planted.siteBase);
        assertEquals(100, planted.populationCap);
    }
}
```

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests SiteBaseTest
```

Expected: compilation failure (`siteBase` does not exist on `Site`).

- [x] **Step 3: Update `Site.java`**

Replace the file with:

```java
// src/main/java/spacecolony/sim/Site.java
package spacecolony.sim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One colony at a fixed (lat, lon) on a body. Aggregate stats only — no per-building grid.
 */
public class Site {
    public final String id;
    public String name;
    public final String bodyId;
    public final double lat;
    public final double lon;
    /** Baseline population cap before HABITAT boost. 200 for Earth Hub, 100 for colonizer-planted. */
    public final int siteBase;
    public int population;
    public int populationCap;
    public double morale; // 0.0 .. 1.0
    public final Map<Resource, Double> stockpile = new EnumMap<>(Resource.class);
    public final Map<Resource, Double> stockpileCap = new EnumMap<>(Resource.class);
    /** Last computed net production rate per day; refreshed each tick. */
    public final Map<Resource, Double> productionRateCache = new EnumMap<>(Resource.class);
    public final List<Building> buildings = new ArrayList<>();

    public Site(String id, String name, String bodyId, double lat, double lon, int siteBase) {
        this.id = id;
        this.name = name;
        this.bodyId = bodyId;
        this.lat = lat;
        this.lon = lon;
        this.siteBase = siteBase;
        this.populationCap = siteBase;   // initial cap before any HABITAT boost; recomputed each tick
        this.morale = 1.0;
        for (Resource r : Resource.values()) {
            stockpile.put(r, 0.0);
            stockpileCap.put(r, 1000.0);
            productionRateCache.put(r, 0.0);
        }
    }
}
```

- [x] **Step 4: Update `WorldGenerator.java`**

The Earth Hub already passes `200` as the trailing constructor arg — that argument is now `siteBase` rather than `populationCap`. No code change required, but verify by re-reading line 22 of `WorldGenerator.java` after the edit. The existing call site:

```java
Site start = new Site("site-earth-hub", "Earth Hub",
                      earth.id, SystemLayout.STARTING_SITE_LAT, SystemLayout.STARTING_SITE_LON, 200);
```

still compiles correctly — `200` now means `siteBase`. Leave as-is.

- [x] **Step 5: Update `CommandPhase.java`'s `applyBuildSite`**

Line 99 currently reads `new Site(bsc.siteId(), bsc.name(), bsc.bodyId(), bsc.lat(), bsc.lon(), 100);` — the `100` is now `siteBase` (was `populationCap`). Same value, different meaning. Leave as-is.

- [x] **Step 6: Update `SiteBaseTest` to use the new `Site` constructor for the stub**

Re-check that the test's `new Site("site-mars-stub", "Mars Stub", "mars", 0.1, 0.1, 100)` line works — yes, `100` is now `siteBase`. Same code.

- [x] **Step 7: Run all tests, confirm pass**

```bash
./gradlew test
```

Expected: 86 + 2 = 88 PASSED. Pay attention to any pre-existing test that constructs a `Site` and assumes the int argument is `populationCap` — semantically they're equivalent (cap initializes to `siteBase`), so existing tests should still pass.

- [x] **Step 8: Commit**

```bash
git add src/main/java/spacecolony/sim/Site.java \
        src/test/java/spacecolony/sim/SiteBaseTest.java
git commit -m "feat(sim): add Site.siteBase field (200 for Earth Hub, 100 for colonizer-planted)"
```

---

### Task 2: Add `EngineEvent.WorldReplaced` and update panels

Add a new sealed permitee for the case where the engine swaps in a fresh `World` (on Load or New). Existing panels listen for `WorldChanged` and refresh; they need to also treat `WorldReplaced` as a refresh trigger.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/engine/EngineEvent.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/ColonyListPanel.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/DetailPanel.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/SphereMiniRenderer.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/BodyViewPanel.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/TopBar.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/SystemMapPanel.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/EventStripPanel.java`

- [x] **Step 1: Add `WorldReplaced` to `EngineEvent.java`**

Replace the file with:

```java
// src/main/java/spacecolony/engine/EngineEvent.java
package spacecolony.engine;

/** Sealed event hierarchy emitted by the Engine to UI listeners. */
public sealed interface EngineEvent
    permits EngineEvent.WorldChanged,
            EngineEvent.WorldReplaced,
            EngineEvent.SelectionChanged,
            EngineEvent.SpeedChanged,
            EngineEvent.ViewChanged {

    /** Emitted after each successful tick advance. */
    record WorldChanged(long tick) implements EngineEvent {}

    /**
     * Emitted when {@link Engine#reset(spacecolony.sim.World)} swaps in a fresh World
     * (Load or New). Listeners must do a full rebind — clear cached state derived
     * from the previous World and re-read everything from the new one.
     */
    record WorldReplaced(long tick) implements EngineEvent {}

    record SelectionChanged(Selection selection) implements EngineEvent {}

    record SpeedChanged(Speed speed) implements EngineEvent {}

    /** Center-pane view changed (e.g., system map ↔ body view). */
    record ViewChanged(View view) implements EngineEvent {
        public enum View { SYSTEM_MAP, BODY_VIEW }
    }
}
```

- [x] **Step 2: Update each panel's `onEvent` switch to include `WorldReplaced`**

For each of the 7 panels listed in **Files**, find the `instanceof EngineEvent.WorldChanged` check and add `WorldReplaced`. The current pattern in `ColonyListPanel.java:39` is:

```java
if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.SelectionChanged) refresh();
```

Change to:

```java
if (e instanceof EngineEvent.WorldChanged
    || e instanceof EngineEvent.WorldReplaced
    || e instanceof EngineEvent.SelectionChanged) refresh();
```

Apply the same pattern to each of the 7 panels — add `|| e instanceof EngineEvent.WorldReplaced` to whatever check already triggers `refresh()` / `repaint()`. The exact lines per file:

- `ColonyListPanel.java:39` — add to existing `||` chain.
- `DetailPanel.java:47` — add to existing `||` chain.
- `SphereMiniRenderer.java:32` — change `if (e instanceof EngineEvent.WorldChanged) repaint();` to `if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.WorldReplaced) repaint();`.
- `BodyViewPanel.java:47` — extend the existing condition (the line continues on multiple lines; add the new instanceof check alongside `WorldChanged`).
- `TopBar.java:52` — add to existing `||` chain.
- `SystemMapPanel.java:28` — add to existing `||` chain.
- `EventStripPanel.java:38` — change `if (e instanceof EngineEvent.WorldChanged) refresh();` to `if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.WorldReplaced) refresh();`.

- [x] **Step 3: Run all tests**

```bash
./gradlew test
```

Expected: all 88 tests still PASS. No new tests yet (these are wired in Task 4's `EngineResetTest`).

- [x] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/engine/EngineEvent.java \
        src/main/java/spacecolony/ui/
git commit -m "feat(engine): add WorldReplaced event and route through 7 panels"
```

---

### Task 3: Add `Simulator.clearCommands()`

`Engine.reset(World)` will need to clear any pending commands so a switch to a new world doesn't replay stale intent. `Simulator.commandQueue` is currently `private` with no clear method.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/Simulator.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/SimulatorClearCommandsTest.java`

- [x] **Step 1: Write the failing test**

```java
// src/test/java/spacecolony/sim/SimulatorClearCommandsTest.java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SimulatorClearCommandsTest {
    @Test
    void clearCommands_dropsPendingCommands() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        sim.clearCommands();
        sim.advance(w);
        assertNull(w.tech.activeId, "cleared command should not have set activeId");
    }
}
```

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests SimulatorClearCommandsTest
```

Expected: compilation failure (`clearCommands` does not exist).

- [x] **Step 3: Add the method to `Simulator.java`**

Insert immediately after the `enqueue` method (around line 35):

```java
    /** Drop all pending commands. Used by {@link spacecolony.engine.Engine#reset} on world swap. */
    public void clearCommands() { commandQueue.clear(); }
```

- [x] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests SimulatorClearCommandsTest
```

Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/Simulator.java \
        src/test/java/spacecolony/sim/SimulatorClearCommandsTest.java
git commit -m "feat(sim): add Simulator.clearCommands() for engine reset"
```

---

### Task 4: `Engine.reset(World)`

Drop `final` from `Engine.world` and add a `reset` method that swaps in a fresh world, clears the simulator's command queue, resets selection, and fires `WorldReplaced`. UI panels (Task 2) treat this as a full rebind.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/engine/Engine.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/engine/EngineResetTest.java`

- [x] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/engine/EngineResetTest.java
package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import spacecolony.sim.World;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineResetTest {

    @Test
    void reset_swapsWorld_andFiresWorldReplaced() throws Exception {
        runOnEdt(() -> {
            World w1 = WorldGenerator.generate(1L);
            World w2 = WorldGenerator.generate(2L);
            Engine engine = new Engine(w1);
            List<EngineEvent> seen = new ArrayList<>();
            engine.addListener(seen::add);

            engine.reset(w2);

            assertSame(w2, engine.world());
            assertTrue(seen.stream().anyMatch(e -> e instanceof EngineEvent.WorldReplaced));
        });
    }

    @Test
    void reset_clearsPendingCommands() throws Exception {
        runOnEdt(() -> {
            World w1 = WorldGenerator.generate(1L);
            World w2 = WorldGenerator.generate(2L);
            Engine engine = new Engine(w1);
            engine.enqueue(new QueueResearchCommand("basic-mining"));
            engine.reset(w2);
            engine.tick();
            assertNull(w2.tech.activeId, "queued command should have been dropped on reset");
        });
    }

    @Test
    void reset_resetsSelection() throws Exception {
        runOnEdt(() -> {
            World w1 = WorldGenerator.generate(1L);
            Engine engine = new Engine(w1);
            engine.setSelection(Selection.ofBody("mars"));
            engine.reset(WorldGenerator.generate(2L));
            assertEquals(Selection.NONE, engine.selection());
        });
    }

    private static void runOnEdt(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) r.run();
        else SwingUtilities.invokeAndWait(r);
    }
}
```

(Look at `Selection.java` for the actual factory method names. If `Selection.ofBody(String)` doesn't exist, use whatever the current API is — e.g., `new Selection.Body("mars")`. Read `src/main/java/spacecolony/engine/Selection.java` once and adjust the one line accordingly.)

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests EngineResetTest
```

Expected: compilation failure (`reset` does not exist; `world` field is final).

- [x] **Step 3: Update `Engine.java`**

Replace the file with:

```java
// src/main/java/spacecolony/engine/Engine.java
package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import spacecolony.sim.Simulator;
import spacecolony.sim.World;
import spacecolony.sim.commands.Command;

/**
 * Top-level engine. Wraps a World + Simulator and notifies UI listeners on changes.
 * UI calls {@link #enqueue} to submit player commands; the next {@link #tick} drains them.
 *
 * Not thread-safe — meant to be driven from the EDT.
 */
public class Engine {
    private World world;
    private final Simulator simulator = new Simulator();
    private final List<EngineListener> listeners = new ArrayList<>();
    private Selection selection = Selection.NONE;
    private Speed speed = Speed.PAUSED;
    private EngineEvent.ViewChanged.View view = EngineEvent.ViewChanged.View.SYSTEM_MAP;

    public Engine(World world) { this.world = world; }

    public World world() { return world; }
    public Selection selection() { return selection; }
    public Speed speed() { return speed; }
    public EngineEvent.ViewChanged.View view() { return view; }

    public void addListener(EngineListener l) { listeners.add(l); }
    public void removeListener(EngineListener l) { listeners.remove(l); }

    public void enqueue(Command c) { simulator.enqueue(c); }

    /** Advance the simulator one tick, then fire WorldChanged. */
    public void tick() {
        simulator.advance(world);
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    /**
     * Swap in a fresh World (used by save/load and New Game). Clears any pending commands,
     * resets selection to {@link Selection#NONE}, and notifies listeners via
     * {@link EngineEvent.WorldReplaced} so panels can rebind their cached state.
     */
    public void reset(World newWorld) {
        this.world = newWorld;
        simulator.clearCommands();
        if (!Selection.NONE.equals(selection)) {
            selection = Selection.NONE;
            fire(new EngineEvent.SelectionChanged(Selection.NONE));
        }
        fire(new EngineEvent.WorldReplaced(newWorld.tick));
    }

    public void setSelection(Selection s) {
        if (s.equals(selection)) return;
        selection = s;
        fire(new EngineEvent.SelectionChanged(s));
    }

    public void setSpeed(Speed s) {
        if (s == speed) return;
        speed = s;
        fire(new EngineEvent.SpeedChanged(s));
    }

    public void setView(EngineEvent.ViewChanged.View v) {
        if (v == view) return;
        view = v;
        fire(new EngineEvent.ViewChanged(v));
    }

    private void fire(EngineEvent e) {
        // Iterate over a snapshot so listeners that subscribe/unsubscribe during dispatch don't mutate the live list.
        for (EngineListener l : new ArrayList<>(listeners)) l.onEvent(e);
    }
}
```

- [x] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests EngineResetTest --tests EngineTest
```

Expected: all PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/engine/Engine.java \
        src/test/java/spacecolony/engine/EngineResetTest.java
git commit -m "feat(engine): add Engine.reset(World) and drop final from world field"
```

---

### Task 5: `EdtGuard` helper + install guards on `Engine`

Add a tiny static helper that asserts the caller is (or is not) on the EDT, and install assertions on every `Engine` public mutation. With `-ea` on (Task 0), violations crash loudly.

**Files:**
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/engine/EdtGuard.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/engine/Engine.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/engine/EngineEdtTest.java`

- [x] **Step 1: Write `EdtGuard`**

```java
// src/main/java/spacecolony/engine/EdtGuard.java
package spacecolony.engine;

import javax.swing.SwingUtilities;

/** EDT assertion helpers. Active only when assertions are enabled (-ea). */
public final class EdtGuard {
    private EdtGuard() {}

    public static void assertEdt() {
        assert SwingUtilities.isEventDispatchThread()
            : "Must be called on EDT, was on " + Thread.currentThread().getName();
    }

    public static void assertNotEdt() {
        assert !SwingUtilities.isEventDispatchThread() : "Must NOT be called on EDT";
    }
}
```

- [x] **Step 2: Write the failing EDT-guard test**

```java
// src/test/java/spacecolony/engine/EngineEdtTest.java
package spacecolony.engine;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineEdtTest {

    @Test
    void enqueueOffEdt_throwsAssertionError() {
        // We're on a JUnit worker thread, not EDT. With -ea on, the guard must fire.
        assertFalse(SwingUtilities.isEventDispatchThread(),
            "precondition: test runs on a non-EDT thread");
        Engine engine = new Engine(WorldGenerator.generate(1L));
        assertThrows(AssertionError.class,
            () -> engine.enqueue(new QueueResearchCommand("basic-mining")));
    }

    @Test
    void enqueueOnEdt_succeeds() throws Exception {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        SwingUtilities.invokeAndWait(() ->
            engine.enqueue(new QueueResearchCommand("basic-mining")));
        // No assertion error means success.
    }
}
```

- [x] **Step 3: Run, confirm fail**

```bash
./gradlew test --tests EngineEdtTest
```

Expected: both fail (guards not yet installed → `enqueueOffEdt_throwsAssertionError` fails because no `AssertionError` is thrown).

- [x] **Step 4: Install guards on `Engine.java`**

Add `import spacecolony.engine.EdtGuard;` (already in same package — so just `EdtGuard.assertEdt()` works without import). Insert `EdtGuard.assertEdt();` as the first line of each of:

- `addListener(EngineListener l)`
- `removeListener(EngineListener l)`
- `enqueue(Command c)`
- `tick()`
- `reset(World newWorld)`

Example for `enqueue`:

```java
public void enqueue(Command c) {
    EdtGuard.assertEdt();
    simulator.enqueue(c);
}
```

Example for `tick`:

```java
public void tick() {
    EdtGuard.assertEdt();
    simulator.advance(world);
    fire(new EngineEvent.WorldChanged(world.tick));
}
```

Apply the same one-line insertion to `addListener`, `removeListener`, and `reset`.

- [x] **Step 5: Run all tests**

```bash
./gradlew test
```

Expected: `EngineEdtTest` PASSES; previously-passing `EngineResetTest` and `EngineTest` still PASS because they already wrap calls in `SwingUtilities.invokeAndWait`. Existing `GameLoopTest` runs the timer fire on the EDT (Swing Timer fires on EDT). If any existing test fails with `AssertionError: Must be called on EDT`, that test was calling `engine.enqueue/tick/etc.` from a worker thread — fix that test by wrapping the call in `SwingUtilities.invokeAndWait`.

- [x] **Step 6: Commit**

```bash
git add src/main/java/spacecolony/engine/EdtGuard.java \
        src/main/java/spacecolony/engine/Engine.java \
        src/test/java/spacecolony/engine/EngineEdtTest.java
git commit -m "feat(engine): add EdtGuard and assert EDT on Engine mutations"
```

---

### Task 6: `TechEffects` static class + unit tests

Create the single source of truth for tech-driven multipliers. Each method is a one-liner over `TechState.researched`. No sim integration yet — that's Tasks 7–11.

**Files:**
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/TechEffects.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/TechEffectsTest.java`

- [x] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/sim/TechEffectsTest.java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechEffectsTest {
    private static final double EPS = 1e-9;

    private static TechState withTechs(String... ids) {
        TechState t = new TechState();
        for (String id : ids) t.researched.add(id);
        return t;
    }

    // === No techs: everything is 1.0 (or false for booleans). ===

    @Test
    void noTechs_allMultipliersAreOne() {
        TechState t = new TechState();
        assertEquals(1.0, TechEffects.mineOreMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.mineSilicateMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.farmFoodMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.farmWaterDemandMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.powerPlantMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.refineryMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.moraleCeiling(t), EPS);
        assertEquals(1.0, TechEffects.fuelCostMultiplier(t), EPS);
        assertFalse(TechEffects.gasGiantFuelEnabled(t));
        assertEquals(1.0, TechEffects.diseaseSeverityMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.researchLabMultiplier(t), EPS);
        assertEquals(1.0, TechEffects.popCapMultiplier(t), EPS);
    }

    // === Per-tech effects (verbatim from TechCatalog descriptions). ===

    @Test void basicMining()    { assertEquals(1.10, TechEffects.mineOreMultiplier(withTechs("basic-mining")), EPS); }
    @Test void basicFarming()   { assertEquals(1.10, TechEffects.farmFoodMultiplier(withTechs("basic-farming")), EPS); }
    @Test void solarPanels()    { assertEquals(1.25, TechEffects.powerPlantMultiplier(withTechs("solar-panels")), EPS); }
    @Test void ionDrives()      { assertEquals(0.80, TechEffects.fuelCostMultiplier(withTechs("ion-drives")), EPS); }
    @Test void fusionDrives()   { assertEquals(0.70, TechEffects.fuelCostMultiplier(withTechs("fusion-drives")), EPS); }
    @Test void atmMining()      { assertTrue(TechEffects.gasGiantFuelEnabled(withTechs("atm-mining"))); }
    @Test void hydroponicsFood()  { assertEquals(1.30, TechEffects.farmFoodMultiplier(withTechs("hydroponics")), EPS); }
    @Test void hydroponicsWater() { assertEquals(0.70, TechEffects.farmWaterDemandMultiplier(withTechs("hydroponics")), EPS); }
    @Test void smelting()       { assertEquals(1.20, TechEffects.refineryMultiplier(withTechs("smelting")), EPS); }
    @Test void medicine()       { assertEquals(0.50, TechEffects.diseaseSeverityMultiplier(withTechs("medicine")), EPS); }
    @Test void colonyMgmtI()    { assertEquals(1.20, TechEffects.popCapMultiplier(withTechs("colony-mgmt-i")), EPS); }
    @Test void researchI()      { assertEquals(1.25, TechEffects.researchLabMultiplier(withTechs("research-i")), EPS); }
    @Test void researchII()     { assertEquals(1.25, TechEffects.researchLabMultiplier(withTechs("research-ii")), EPS); }
    @Test void autoMiningOre()      { assertEquals(1.30, TechEffects.mineOreMultiplier(withTechs("auto-mining")), EPS); }
    @Test void autoMiningSilicate() { assertEquals(1.30, TechEffects.mineSilicateMultiplier(withTechs("auto-mining")), EPS); }
    @Test void colonyMgmtII()   { assertEquals(1.30, TechEffects.popCapMultiplier(withTechs("colony-mgmt-ii")), EPS); }
    @Test void lifeSupportI()   { assertEquals(1.20, TechEffects.moraleCeiling(withTechs("life-support-i")), EPS); }
    @Test void lifeSupportII()  { assertEquals(1.30, TechEffects.moraleCeiling(withTechs("life-support-ii")), EPS); }
    @Test void antimatter()     { assertEquals(0.50, TechEffects.fuelCostMultiplier(withTechs("antimatter")), EPS); }

    // === Stacking semantics: multiplicative composition. ===

    @Test
    void miningTechs_stackMultiplicatively() {
        assertEquals(1.10 * 1.30, TechEffects.mineOreMultiplier(withTechs("basic-mining", "auto-mining")), EPS);
        assertEquals(1.30, TechEffects.mineSilicateMultiplier(withTechs("basic-mining", "auto-mining")), EPS);
    }

    @Test
    void allDriveTechs_stackTo28pct() {
        assertEquals(0.80 * 0.70 * 0.50,
            TechEffects.fuelCostMultiplier(withTechs("ion-drives", "fusion-drives", "antimatter")), EPS);
    }

    @Test
    void bothColonyMgmt_stackTo156() {
        assertEquals(1.20 * 1.30,
            TechEffects.popCapMultiplier(withTechs("colony-mgmt-i", "colony-mgmt-ii")), EPS);
    }

    @Test
    void bothResearchMethods_stackTo15625() {
        assertEquals(1.25 * 1.25,
            TechEffects.researchLabMultiplier(withTechs("research-i", "research-ii")), EPS);
    }

    @Test
    void bothLifeSupports_stackTo156() {
        assertEquals(1.20 * 1.30,
            TechEffects.moraleCeiling(withTechs("life-support-i", "life-support-ii")), EPS);
    }
}
```

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests TechEffectsTest
```

Expected: compilation failure (`TechEffects` does not exist).

- [x] **Step 3: Write `TechEffects.java`**

```java
// src/main/java/spacecolony/sim/TechEffects.java
package spacecolony.sim;

/**
 * Static multipliers consulted by each sim phase to apply tech-tree effects.
 * Plan 4 wires all 17 v1 techs (see TechCatalog) into sim behaviour here.
 *
 * Each tech composes multiplicatively. With no techs researched, every multiplier
 * returns 1.0 (or false for booleans). Methods are intentionally explicit — no
 * reflection, no table — so a diff against TechCatalog is grep-friendly.
 */
public final class TechEffects {
    private TechEffects() {}

    // === ProductionPhase ===

    public static double mineOreMultiplier(TechState t) {
        return (t.researched.contains("basic-mining") ? 1.10 : 1.0)
             * (t.researched.contains("auto-mining")  ? 1.30 : 1.0);
    }

    public static double mineSilicateMultiplier(TechState t) {
        return (t.researched.contains("auto-mining") ? 1.30 : 1.0);
    }

    public static double farmFoodMultiplier(TechState t) {
        return (t.researched.contains("basic-farming") ? 1.10 : 1.0)
             * (t.researched.contains("hydroponics")   ? 1.30 : 1.0);
    }

    public static double farmWaterDemandMultiplier(TechState t) {
        return (t.researched.contains("hydroponics") ? 0.70 : 1.0);
    }

    public static double powerPlantMultiplier(TechState t) {
        return (t.researched.contains("solar-panels") ? 1.25 : 1.0);
    }

    public static double refineryMultiplier(TechState t) {
        return (t.researched.contains("smelting") ? 1.20 : 1.0);
    }

    public static double moraleCeiling(TechState t) {
        return (t.researched.contains("life-support-i")  ? 1.20 : 1.0)
             * (t.researched.contains("life-support-ii") ? 1.30 : 1.0);
    }

    // === TransitPhase ===

    public static double fuelCostMultiplier(TechState t) {
        return (t.researched.contains("ion-drives")    ? 0.80 : 1.0)
             * (t.researched.contains("fusion-drives") ? 0.70 : 1.0)
             * (t.researched.contains("antimatter")    ? 0.50 : 1.0);
    }

    public static boolean gasGiantFuelEnabled(TechState t) {
        return t.researched.contains("atm-mining");
    }

    // === EventPhase ===

    public static double diseaseSeverityMultiplier(TechState t) {
        return (t.researched.contains("medicine") ? 0.50 : 1.0);
    }

    // === ResearchPhase ===

    public static double researchLabMultiplier(TechState t) {
        return (t.researched.contains("research-i")  ? 1.25 : 1.0)
             * (t.researched.contains("research-ii") ? 1.25 : 1.0);
    }

    // === HABITAT cap (ProductionPhase) ===

    public static double popCapMultiplier(TechState t) {
        return (t.researched.contains("colony-mgmt-i")  ? 1.20 : 1.0)
             * (t.researched.contains("colony-mgmt-ii") ? 1.30 : 1.0);
    }
}
```

- [x] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests TechEffectsTest
```

Expected: all 27+ TechEffects tests PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/TechEffects.java \
        src/test/java/spacecolony/sim/TechEffectsTest.java
git commit -m "feat(sim): add TechEffects with all 17 v1 tech multipliers"
```

---

### Task 7: Wire `TechEffects` into `ProductionPhase`

Apply mine, farm, refinery, power, and morale-ceiling multipliers at their existing call sites. Note: HABITAT cap recompute is a separate Task 12; this task wires the production-side multipliers only.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/ProductionPhase.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/ProductionTechEffectsTest.java`

- [x] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/sim/ProductionTechEffectsTest.java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ProductionTechEffectsTest {

    @Test
    void basicMining_increasesOreOutputBy10pct() {
        double withoutTech = oreDeltaOneTick(false, false);
        double withTech    = oreDeltaOneTick(true, false);
        assertEquals(1.10, withTech / withoutTech, 1e-6);
    }

    @Test
    void autoMining_stacksWithBasicMining() {
        double base = oreDeltaOneTick(false, false);
        double both = oreDeltaOneTick(true, true);
        assertEquals(1.10 * 1.30, both / base, 1e-6);
    }

    @Test
    void hydroponics_increasesFoodAndDecreasesWaterDemand() {
        World wBase = WorldGenerator.generate(1L);
        World wTech = WorldGenerator.generate(1L);
        wTech.tech.researched.add("hydroponics");
        Simulator simBase = new Simulator(), simTech = new Simulator();

        Site sBase = wBase.findSite("site-earth-hub");
        Site sTech = wTech.findSite("site-earth-hub");
        double foodBase0 = sBase.stockpile.get(Resource.FOOD);
        double foodTech0 = sTech.stockpile.get(Resource.FOOD);
        double waterBase0 = sBase.stockpile.get(Resource.WATER);
        double waterTech0 = sTech.stockpile.get(Resource.WATER);

        simBase.advance(wBase);
        simTech.advance(wTech);

        double foodDeltaBase = sBase.stockpile.get(Resource.FOOD) - foodBase0;
        double foodDeltaTech = sTech.stockpile.get(Resource.FOOD) - foodTech0;
        double waterDeltaBase = waterBase0 - sBase.stockpile.get(Resource.WATER); // water consumed (positive)
        double waterDeltaTech = waterTech0 - sTech.stockpile.get(Resource.WATER);

        assertTrue(foodDeltaTech > foodDeltaBase * 1.25,
            "hydroponics should raise food output substantially (≥25% over baseline)");
        assertTrue(waterDeltaTech < waterDeltaBase,
            "hydroponics should reduce water consumption");
    }

    @Test
    void solarPanels_increasesPowerOutput() {
        // Make the site power-limited so multipliers are observable through the throttle.
        World wBase = WorldGenerator.generate(1L);
        World wTech = WorldGenerator.generate(1L);
        wTech.tech.researched.add("solar-panels");

        // Add lots of buildings so power demand exceeds supply at L1 plant.
        for (World w : new World[] { wBase, wTech }) {
            Site s = w.findSite("site-earth-hub");
            for (int i = 0; i < 10; i++) s.buildings.add(new Building(BuildingType.MINE, 1));
        }

        double oreBase0 = wBase.findSite("site-earth-hub").stockpile.get(Resource.ORE);
        double oreTech0 = wTech.findSite("site-earth-hub").stockpile.get(Resource.ORE);
        new Simulator().advance(wBase);
        new Simulator().advance(wTech);
        double dBase = wBase.findSite("site-earth-hub").stockpile.get(Resource.ORE) - oreBase0;
        double dTech = wTech.findSite("site-earth-hub").stockpile.get(Resource.ORE) - oreTech0;

        // More power → higher powerFactor → more ore from the same mines.
        assertTrue(dTech > dBase * 1.1, "solar-panels should noticeably raise power-throttled output");
    }

    @Test
    void smelting_increasesRefineryOutput() {
        World wBase = makeWorldWithRefinery(false);
        World wTech = makeWorldWithRefinery(true);
        double metalBase0 = wBase.findSite("site-earth-hub").stockpile.get(Resource.METAL);
        double metalTech0 = wTech.findSite("site-earth-hub").stockpile.get(Resource.METAL);
        new Simulator().advance(wBase);
        new Simulator().advance(wTech);
        double dBase = wBase.findSite("site-earth-hub").stockpile.get(Resource.METAL) - metalBase0;
        double dTech = wTech.findSite("site-earth-hub").stockpile.get(Resource.METAL) - metalTech0;
        assertEquals(1.20, dTech / dBase, 1e-3);
    }

    @Test
    void lifeSupportI_raisesMoraleCeilingTo120() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("life-support-i");
        Site s = w.findSite("site-earth-hub");
        s.morale = 1.0;
        // Advance several ticks under healthy supply — morale should drift up toward 1.20.
        Simulator sim = new Simulator();
        for (int i = 0; i < 100; i++) sim.advance(w);
        assertTrue(s.morale > 1.0, "morale should exceed legacy 1.0 ceiling with life-support-i");
        assertTrue(s.morale <= 1.20 + 1e-6, "morale should not exceed life-support-i ceiling 1.20");
    }

    // --- helpers ---

    private static double oreDeltaOneTick(boolean basicMining, boolean autoMining) {
        World w = WorldGenerator.generate(1L);
        if (basicMining) w.tech.researched.add("basic-mining");
        if (autoMining)  w.tech.researched.add("auto-mining");
        Site s = w.findSite("site-earth-hub");
        double before = s.stockpile.get(Resource.ORE);
        new Simulator().advance(w);
        return s.stockpile.get(Resource.ORE) - before;
    }

    private static World makeWorldWithRefinery(boolean smelting) {
        World w = WorldGenerator.generate(1L);
        if (smelting) w.tech.researched.add("smelting");
        Site s = w.findSite("site-earth-hub");
        // Seed enough ORE so refinery is input-limited the same way in both worlds.
        s.stockpile.put(Resource.ORE, 500.0);
        s.buildings.add(new Building(BuildingType.REFINERY, 1));
        return w;
    }
}
```

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests ProductionTechEffectsTest
```

Expected: all 6 FAIL.

- [x] **Step 3: Wire multipliers into `ProductionPhase.java`**

Modify `ProductionPhase.run(World w)` and `updateMorale`. The final shape (showing modified regions only — keep everything else unchanged):

In `run(World w)`, change the power-production line (currently `double output = 10.0 * bd.level / Math.max(0.05, r * r);`) to multiply by `TechEffects.powerPlantMultiplier(w.tech)`:

```java
double output = 10.0 * bd.level / Math.max(0.05, r * r)
              * TechEffects.powerPlantMultiplier(w.tech);
```

Change the MINE case to apply ore/silicate multipliers:

```java
case MINE -> {
    if (yields != null) {
        double y = yields.sample(Resource.ORE, s.lat, s.lon);
        double produced = bd.level * 2.0 * y * powerFactor
                        * TechEffects.mineOreMultiplier(w.tech);
        produce(s, Resource.ORE, produced);
        // Mines also yield silicate, scaled.
        double si = bd.level * 1.0 * yields.sample(Resource.SILICATE, s.lat, s.lon) * powerFactor
                  * TechEffects.mineSilicateMultiplier(w.tech);
        produce(s, Resource.SILICATE, si);
    }
}
```

Change the FARM case to apply food multiplier and water-demand multiplier:

```java
case FARM -> {
    double waterDemandFactor = TechEffects.farmWaterDemandMultiplier(w.tech);
    double biomassConsumed = consume(s, Resource.BIOMASS, bd.level * 0.5 * powerFactor);
    consume(s, Resource.WATER, bd.level * 0.3 * powerFactor * waterDemandFactor);
    double foodProduced = bd.level * 1.5 * powerFactor *
                          Math.min(1.0, biomassConsumed / Math.max(1e-6, bd.level * 0.5))
                        * TechEffects.farmFoodMultiplier(w.tech);
    produce(s, Resource.FOOD, foodProduced);
}
```

Change the REFINERY case to apply refinery multiplier on outputs:

```java
case REFINERY -> {
    double mult = TechEffects.refineryMultiplier(w.tech);
    double oreUsed = consume(s, Resource.ORE, bd.level * 1.5 * powerFactor);
    produce(s, Resource.METAL, oreUsed * 0.8 * mult);
    double iceUsed = consume(s, Resource.ICE, bd.level * 1.0 * powerFactor);
    produce(s, Resource.WATER, iceUsed * 0.9 * mult);
}
```

Change `updateMorale` to take `TechState` and consult `moraleCeiling`:

```java
private static void updateMorale(Site s, TechState tech) {
    boolean shortFood = s.stockpile.getOrDefault(Resource.FOOD, 0.0) < 1e-6;
    boolean shortWater = s.stockpile.getOrDefault(Resource.WATER, 0.0) < 1e-6;
    double ceiling = TechEffects.moraleCeiling(tech);
    if (shortFood || shortWater) s.morale = Math.max(0.0, s.morale - 0.05);
    else s.morale = Math.min(ceiling, s.morale + 0.005);
}
```

And update the call site in `run` from `updateMorale(s);` to `updateMorale(s, w.tech);`.

Add `import spacecolony.sim.TechEffects;` and `import spacecolony.sim.TechState;` at the top of the file (TechState may already be reachable since same package, in which case skip).

- [x] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests ProductionTechEffectsTest --tests ProductionTest --tests SupplyTest --tests DeterminismTest
```

Expected: all PASS. Existing `ProductionTest` and `SupplyTest` should be unaffected because no techs are researched in those test worlds (multipliers = 1.0). `DeterminismTest` should still PASS because the multipliers are pure functions of `TechState`, which is part of the deterministic state.

- [x] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/ProductionPhase.java \
        src/test/java/spacecolony/sim/ProductionTechEffectsTest.java
git commit -m "feat(sim): wire mine/farm/refinery/power/morale tech multipliers into ProductionPhase"
```

---

### Task 8: Wire fuel-cost multiplier into transit + dispatch

Apply `TechEffects.fuelCostMultiplier` at the two places fuel is computed: `TransitPhase.loadingAndUnloading` at departure (the authoritative deduction) and `CommandPhase.applyDispatchShip` for the up-front affordability check.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/TransitPhase.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/CommandPhase.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/TransitTechEffectsTest.java`

- [x] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/sim/TransitTechEffectsTest.java
package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class TransitTechEffectsTest {

    @Test
    void ionDrives_cutsDepartureFuelCostBy20pct() {
        double baseCost = fuelConsumedOnDispatch(false);
        double techCost = fuelConsumedOnDispatch(true);
        assertEquals(0.80, techCost / baseCost, 1e-6);
    }

    @Test
    void fusionPlusIon_stacksTo56pct() {
        double base = fuelConsumedOnDispatch("ion-drives", "fusion-drives");
        double pure = fuelConsumedOnDispatch();
        assertEquals(0.80 * 0.70, base / pure, 1e-6);
    }

    @Test
    void allDriveTechs_reduceCostBy72pct() {
        double full = fuelConsumedOnDispatch("ion-drives", "fusion-drives", "antimatter");
        double pure = fuelConsumedOnDispatch();
        assertEquals(0.80 * 0.70 * 0.50, full / pure, 1e-6);
    }

    /** Drive the ship through LOADING → IN_TRANSIT and return fuel consumed at departure. */
    private static double fuelConsumedOnDispatch(String... techs) {
        World w = WorldGenerator.generate(1L);
        for (String id : techs) w.tech.researched.add(id);

        Site origin = w.findSite("site-earth-hub");
        origin.stockpile.put(Resource.METAL, 200.0);

        // Stub a destination site at Mars so transit math has a target.
        Site mars = new Site("site-mars-stub", "Mars Stub", "mars", 0.0, 0.0, 100);
        w.findBody("mars").sites.add(mars);

        Ship s = new Ship("ship-h1", "H1", ShipClass.HAULER, "site-earth-hub");
        s.fuel = 1_000_000.0;
        w.ships.add(s);

        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("ship-h1", "site-mars-stub", Map.of(Resource.METAL, 50.0)));
        double fuelBefore = s.fuel;
        for (int i = 0; i < 200 && s.state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, s.state);
        return fuelBefore - s.fuel;
    }

    private static double fuelConsumedOnDispatch(boolean ionDrives) {
        return ionDrives ? fuelConsumedOnDispatch("ion-drives") : fuelConsumedOnDispatch();
    }
}
```

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests TransitTechEffectsTest
```

Expected: all 3 FAIL.

- [x] **Step 3: Wire fuel multiplier in `TransitPhase.loadingAndUnloading`**

Find the line in `TransitPhase.java` that reads:

```java
double cost = FUEL_K * (s.shipClass.dryMass() + s.cargoMass()) * dist;
```

Change to:

```java
double cost = FUEL_K * (s.shipClass.dryMass() + s.cargoMass()) * dist
            * TechEffects.fuelCostMultiplier(w.tech);
```

Add `import spacecolony.sim.TechEffects;` at the top.

- [x] **Step 4: Wire fuel multiplier in `CommandPhase.applyDispatchShip`**

Find the line in `CommandPhase.java`:

```java
double estCost = FUEL_K * (s.shipClass.dryMass() + manifestMass) * dist;
```

Change to:

```java
double estCost = FUEL_K * (s.shipClass.dryMass() + manifestMass) * dist
               * TechEffects.fuelCostMultiplier(w.tech);
```

Add `import spacecolony.sim.TechEffects;` at the top.

- [x] **Step 5: Run, confirm pass**

```bash
./gradlew test --tests TransitTechEffectsTest --tests TransitMathTest --tests DeterminismTest
```

Expected: all PASS. Existing `TransitMathTest` should be unaffected (no techs in its world → multiplier = 1.0).

- [x] **Step 6: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/TransitPhase.java \
        src/main/java/spacecolony/sim/phases/CommandPhase.java \
        src/test/java/spacecolony/sim/TransitTechEffectsTest.java
git commit -m "feat(sim): wire fuel-cost multiplier into transit departure and dispatch check"
```

---

### Task 9: `atm-mining` enables FUEL extraction at gas giants

When `atm-mining` is researched and the MINE building is on a `GAS_GIANT` body, the mine produces FUEL via the same `ResourceYieldSampler`. (Gas giants already yield FUEL through the sampler; `atm-mining` gates whether MINE buildings sample it.)

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/ProductionPhase.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/AtmMiningTest.java`

- [x] **Step 1: Write the failing test**

```java
// src/test/java/spacecolony/sim/AtmMiningTest.java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class AtmMiningTest {

    @Test
    void withoutAtmMining_gasGiantMine_producesNoFuel() {
        World w = WorldGenerator.generate(1L);
        Site jovianMine = plantGasGiantMine(w);
        double fuelBefore = jovianMine.stockpile.get(Resource.FUEL);
        new Simulator().advance(w);
        assertEquals(fuelBefore, jovianMine.stockpile.get(Resource.FUEL), 1e-9,
            "gas-giant MINE should produce 0 FUEL without atm-mining");
    }

    @Test
    void withAtmMining_gasGiantMine_producesFuel() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("atm-mining");
        Site jovianMine = plantGasGiantMine(w);
        double fuelBefore = jovianMine.stockpile.get(Resource.FUEL);
        new Simulator().advance(w);
        assertTrue(jovianMine.stockpile.get(Resource.FUEL) > fuelBefore,
            "gas-giant MINE should produce FUEL with atm-mining researched");
    }

    @Test
    void withAtmMining_rockyBodyMine_doesNotChangeFuelBehavior() {
        // Rocky-body MINE should never produce FUEL regardless of tech (sampler returns 0).
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("atm-mining");
        Site earthHub = w.findSite("site-earth-hub");
        double fuelBefore = earthHub.stockpile.get(Resource.FUEL);
        new Simulator().advance(w);
        assertEquals(fuelBefore, earthHub.stockpile.get(Resource.FUEL), 1e-9);
    }

    private static Site plantGasGiantMine(World w) {
        Site jovianSite = new Site("site-jovian-1", "Jovian Cloud", "jovian", 0.0, 0.0, 100);
        jovianSite.buildings.add(new Building(BuildingType.MINE, 1));
        // Power plant so the mine isn't fully throttled.
        jovianSite.buildings.add(new Building(BuildingType.POWER_PLANT, 1));
        w.findBody("jovian").sites.add(jovianSite);
        return jovianSite;
    }
}
```

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests AtmMiningTest
```

Expected: the `with` test FAILS (MINE doesn't sample FUEL yet); the `without` and rocky tests PASS by default.

- [x] **Step 3: Update the MINE case in `ProductionPhase.run`**

The MINE case currently produces ORE and SILICATE. Add a conditional FUEL production for gas-giant bodies when `atm-mining` is researched. The complete updated MINE case:

```java
case MINE -> {
    if (yields != null) {
        double y = yields.sample(Resource.ORE, s.lat, s.lon);
        double produced = bd.level * 2.0 * y * powerFactor
                        * TechEffects.mineOreMultiplier(w.tech);
        produce(s, Resource.ORE, produced);
        // Mines also yield silicate, scaled.
        double si = bd.level * 1.0 * yields.sample(Resource.SILICATE, s.lat, s.lon) * powerFactor
                  * TechEffects.mineSilicateMultiplier(w.tech);
        produce(s, Resource.SILICATE, si);
        // Atmospheric mining: gas-giant MINE buildings extract FUEL when the tech is researched.
        if (b.type == BodyType.GAS_GIANT && TechEffects.gasGiantFuelEnabled(w.tech)) {
            double fy = yields.sample(Resource.FUEL, s.lat, s.lon);
            double fuel = bd.level * 2.0 * fy * powerFactor;
            produce(s, Resource.FUEL, fuel);
        }
    }
}
```

(`BodyType` is already imported in `ProductionPhase.java`.)

- [x] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests AtmMiningTest --tests ProductionTest
```

Expected: all PASS.

- [x] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/ProductionPhase.java \
        src/test/java/spacecolony/sim/AtmMiningTest.java
git commit -m "feat(sim): atm-mining enables FUEL extraction at gas-giant MINE buildings"
```

---

### Task 10: Wire `medicine` into `EventPhase` (disease severity)

Apply `TechEffects.diseaseSeverityMultiplier` to the population-loss and morale-hit values in the DISEASE_OUTBREAK handler.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/EventPhase.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/EventTechEffectsTest.java`

- [x] **Step 1: Write the failing test**

The DISEASE_OUTBREAK case is randomly triggered. We can't easily wait for one in a deterministic test. Instead, we'll expose a package-private hook that directly invokes the event with a fixed RNG.

Add a package-private static method to `EventPhase.java`:

```java
/** Test hook: apply a specific event kind to a body. */
static void applyForTest(World w, Body b, EventKind kind, java.util.Random rng) {
    applyEvent(w, b, kind, rng);
}
```

(Add this method now as part of Step 1's preparatory edit — the test compiles against it.)

Then the test:

```java
// src/test/java/spacecolony/sim/EventTechEffectsTest.java
package spacecolony.sim;

import java.util.Random;
import org.junit.jupiter.api.Test;
import spacecolony.sim.phases.EventPhase;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EventTechEffectsTest {

    @Test
    void medicine_halvesDiseasePopulationLoss() {
        int lossBase = applyDisease(false);
        int lossTech = applyDisease(true);
        // With medicine, loss should be ~half of base. Rounding via Math.max(1, ...) can
        // wobble by 1; allow a tight slack.
        assertEquals(lossBase / 2, lossTech, 1,
            "medicine should halve disease pop loss (base=" + lossBase + ", tech=" + lossTech + ")");
    }

    @Test
    void medicine_halvesDiseaseMoraleHit() {
        double moraleHitBase = moraleHitFromDisease(false);
        double moraleHitTech = moraleHitFromDisease(true);
        assertEquals(0.50, moraleHitTech / moraleHitBase, 1e-6);
    }

    private static int applyDisease(boolean medicine) {
        World w = WorldGenerator.generate(42L);
        if (medicine) w.tech.researched.add("medicine");
        Site s = w.findSite("site-earth-hub");
        s.population = 1000; // large so /10 dominates the rounding
        int before = s.population;
        EventPhase.applyForTest(w, w.findBody("earth"), EventKind.DISEASE_OUTBREAK, new Random(7L));
        return before - s.population;
    }

    private static double moraleHitFromDisease(boolean medicine) {
        World w = WorldGenerator.generate(42L);
        if (medicine) w.tech.researched.add("medicine");
        Site s = w.findSite("site-earth-hub");
        s.population = 1000;
        s.morale = 1.0;
        EventPhase.applyForTest(w, w.findBody("earth"), EventKind.DISEASE_OUTBREAK, new Random(7L));
        return 1.0 - s.morale;
    }
}
```

- [x] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests EventTechEffectsTest
```

Expected: both FAIL.

- [x] **Step 3: Wire severity multiplier in `EventPhase.java`**

Change the DISEASE_OUTBREAK case in `applyEvent`:

```java
case DISEASE_OUTBREAK -> {
    double severity = TechEffects.diseaseSeverityMultiplier(w.tech);
    for (Site s : b.sites) if (s.population > 0) {
        int loss = Math.max(1, (int) Math.round(s.population / 10.0 * severity));
        s.population -= loss;
        s.morale = Math.max(0, s.morale - 0.2 * severity);
    }
    w.emit(new Event(w.tick, EventSeverity.WARNING, k,
        "Disease outbreak on " + b.name, b.id, null, null));
}
```

Add `import spacecolony.sim.TechEffects;` at the top.

- [x] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests EventTechEffectsTest --tests EventsTest --tests DeterminismTest
```

Expected: all PASS. `EventsTest`'s timeline equality should still hold (no techs researched in that test → severity = 1.0 → behaviour unchanged).

- [x] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/EventPhase.java \
        src/test/java/spacecolony/sim/EventTechEffectsTest.java
git commit -m "feat(sim): medicine halves disease pop loss and morale hit"
```

---

### Task 11: Wire `research-i/ii` into `ResearchPhase`

Apply `TechEffects.researchLabMultiplier` to the lab points accumulator.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/ResearchPhase.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/ResearchTechEffectsTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/spacecolony/sim/ResearchTechEffectsTest.java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class ResearchTechEffectsTest {

    @Test
    void researchI_raisesLabOutputBy25pct() {
        long ticksBase = ticksToComplete("basic-farming");
        long ticksTech = ticksToComplete("basic-farming", "research-i");
        // research-i gives ×1.25 → ticks should drop by ~20%. Allow slack since labs accumulate integer points.
        double ratio = (double) ticksTech / ticksBase;
        assertTrue(ratio < 0.85 && ratio > 0.78,
            "research-i should reduce ticks-to-complete by ~20% (ratio=" + ratio + ")");
    }

    @Test
    void researchIAndII_stackTo15625x() {
        long ticksBase = ticksToComplete("basic-farming");
        long ticksBoth = ticksToComplete("basic-farming", "research-i", "research-ii");
        double ratio = (double) ticksBoth / ticksBase;
        // 1 / (1.25 * 1.25) = 0.64
        assertTrue(ratio < 0.70 && ratio > 0.60,
            "research-i + research-ii should reduce ticks by ~36% (ratio=" + ratio + ")");
    }

    private static long ticksToComplete(String target, String... preResearched) {
        World w = WorldGenerator.generate(1L);
        for (String id : preResearched) w.tech.researched.add(id);
        // Drop a research lab on Earth Hub.
        w.findSite("site-earth-hub").buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand(target));
        long start = w.tick;
        for (int i = 0; i < 10_000; i++) {
            sim.advance(w);
            if (w.tech.researched.contains(target)) return w.tick - start;
        }
        throw new AssertionError("did not finish " + target);
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests ResearchTechEffectsTest
```

Expected: both FAIL.

- [ ] **Step 3: Wire multiplier in `ResearchPhase.java`**

Change the points accumulator line:

```java
if (bd.enabled && bd.type == BuildingType.RESEARCH_LAB) points += bd.level * 1.0;
```

to:

```java
if (bd.enabled && bd.type == BuildingType.RESEARCH_LAB)
    points += bd.level * 1.0 * TechEffects.researchLabMultiplier(w.tech);
```

Add `import spacecolony.sim.TechEffects;` at the top.

- [ ] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests ResearchTechEffectsTest --tests ResearchTest --tests DeterminismTest
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/ResearchPhase.java \
        src/test/java/spacecolony/sim/ResearchTechEffectsTest.java
git commit -m "feat(sim): wire research-i/ii multipliers into ResearchPhase"
```

---

### Task 12: HABITAT cap recompute in `ProductionPhase`

Each tick, recompute `populationCap` from `siteBase + Σ(habitat.level × 100)` and multiply by `TechEffects.popCapMultiplier`. This is the formula from spec §5.1.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases/ProductionPhase.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/sim/HabitatCapTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/sim/HabitatCapTest.java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class HabitatCapTest {

    @Test
    void earthHub_baseline_capIs300WithOneL1Habitat() {
        World w = WorldGenerator.generate(1L);
        new Simulator().advance(w);
        Site s = w.findSite("site-earth-hub");
        // siteBase=200, one L1 HABITAT (+100) → 300
        assertEquals(300, s.populationCap);
    }

    @Test
    void colonizerSite_baseline_capIs200WithOneL1Habitat() {
        World w = WorldGenerator.generate(1L);
        Site planted = new Site("site-mars-new", "Mars New", "mars", 0.0, 0.0, 100);
        planted.buildings.add(new Building(BuildingType.HABITAT, 1));
        w.findBody("mars").sites.add(planted);
        new Simulator().advance(w);
        assertEquals(200, planted.populationCap);
    }

    @Test
    void addingHabitat_raisesCapBy100PerLevel() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        s.buildings.add(new Building(BuildingType.HABITAT, 2));
        new Simulator().advance(w);
        // base 200 + L1 HABITAT (100) + new L2 HABITAT (200) = 500
        assertEquals(500, s.populationCap);
    }

    @Test
    void disabledHabitat_doesNotContribute() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        for (Building b : s.buildings) if (b.type == BuildingType.HABITAT) b.enabled = false;
        new Simulator().advance(w);
        assertEquals(200, s.populationCap);
    }

    @Test
    void colonyMgmtI_appliesMultiplier() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("colony-mgmt-i");
        new Simulator().advance(w);
        Site s = w.findSite("site-earth-hub");
        // (200 + 100) * 1.20 = 360
        assertEquals(360, s.populationCap);
    }

    @Test
    void bothMgmtTechs_stackMultiplicatively() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("colony-mgmt-i");
        w.tech.researched.add("colony-mgmt-ii");
        new Simulator().advance(w);
        Site s = w.findSite("site-earth-hub");
        // (200 + 100) * 1.20 * 1.30 = 468
        assertEquals(468, s.populationCap);
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests HabitatCapTest
```

Expected: most FAIL — current code doesn't recompute cap.

- [ ] **Step 3: Add `recomputeCap` to `ProductionPhase.java`**

Add a private static method:

```java
private static void recomputeCap(Site s, TechState tech) {
    int boost = 0;
    for (Building b : s.buildings) {
        if (b.enabled && b.type == BuildingType.HABITAT) boost += b.level * 100;
    }
    s.populationCap = (int) Math.round(
        (s.siteBase + boost) * TechEffects.popCapMultiplier(tech));
}
```

Call it at the very top of each site iteration in `run(World w)`, before the power-balance block:

```java
for (Body b : w.bodies) {
    for (Site s : b.sites) {
        recomputeCap(s, w.tech);

        // 1. Power balance.
        double powerProduced = 0;
        ...
    }
}
```

- [ ] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests HabitatCapTest --tests ProductionTest --tests SupplyTest --tests DeterminismTest
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/ProductionPhase.java \
        src/test/java/spacecolony/sim/HabitatCapTest.java
git commit -m "feat(sim): recompute HABITAT cap each tick (siteBase + boost × popCapMultiplier)"
```

---

### Task 13: `JsonValue` hierarchy + `JsonReader`

Build the hand-rolled JSON parser. Sealed hierarchy of value types; recursive-descent parser that returns the tree. No reflection.

**Files:**
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/save/JsonValue.java`
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/save/JsonParseException.java`
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/save/JsonReader.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/save/JsonReaderTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/save/JsonReaderTest.java
package spacecolony.save;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonReaderTest {

    @Test
    void parsesNull() {
        assertTrue(JsonReader.parse("null") instanceof JsonValue.JsonNull);
    }

    @Test
    void parsesBoolean() {
        assertEquals(true,  ((JsonValue.JsonBool) JsonReader.parse("true")).value());
        assertEquals(false, ((JsonValue.JsonBool) JsonReader.parse("false")).value());
    }

    @Test
    void parsesInteger() {
        JsonValue.JsonNumber n = (JsonValue.JsonNumber) JsonReader.parse("42");
        assertEquals(42L, n.asLong());
    }

    @Test
    void parsesNegativeAndDecimal() {
        assertEquals(-3.14, ((JsonValue.JsonNumber) JsonReader.parse("-3.14")).asDouble(), 1e-9);
        assertEquals(0L,    ((JsonValue.JsonNumber) JsonReader.parse("0")).asLong());
    }

    @Test
    void parsesString_withEscapes() {
        assertEquals("hello",       ((JsonValue.JsonString) JsonReader.parse("\"hello\"")).value());
        assertEquals("a\"b\\c",     ((JsonValue.JsonString) JsonReader.parse("\"a\\\"b\\\\c\"")).value());
        assertEquals("line\nbreak", ((JsonValue.JsonString) JsonReader.parse("\"line\\nbreak\"")).value());
        assertEquals("é",      ((JsonValue.JsonString) JsonReader.parse("\"\\u00e9\"")).value());
    }

    @Test
    void parsesArray() {
        JsonValue.JsonArray a = (JsonValue.JsonArray) JsonReader.parse("[1, 2, 3]");
        assertEquals(3, a.values().size());
        assertEquals(2L, ((JsonValue.JsonNumber) a.values().get(1)).asLong());
    }

    @Test
    void parsesObject() {
        JsonValue.JsonObject o = (JsonValue.JsonObject) JsonReader.parse(
            "{\"name\": \"Earth\", \"tick\": 42}");
        assertEquals("Earth", ((JsonValue.JsonString) o.values().get("name")).value());
        assertEquals(42L,     ((JsonValue.JsonNumber) o.values().get("tick")).asLong());
    }

    @Test
    void parsesNestedStructure() {
        String json = "{\"a\":[1,{\"b\":true}],\"c\":null}";
        JsonValue.JsonObject o = (JsonValue.JsonObject) JsonReader.parse(json);
        JsonValue.JsonArray a = (JsonValue.JsonArray) o.values().get("a");
        JsonValue.JsonObject inner = (JsonValue.JsonObject) a.values().get(1);
        assertEquals(true, ((JsonValue.JsonBool) inner.values().get("b")).value());
        assertTrue(o.values().get("c") instanceof JsonValue.JsonNull);
    }

    @Test
    void parsesEmptyArrayAndObject() {
        assertEquals(List.of(), ((JsonValue.JsonArray) JsonReader.parse("[]")).values());
        assertTrue(((JsonValue.JsonObject) JsonReader.parse("{}")).values().isEmpty());
    }

    @Test
    void tolerates_whitespace() {
        JsonValue.JsonObject o = (JsonValue.JsonObject) JsonReader.parse(
            "  {\n  \"a\" : 1 ,\n  \"b\" : 2\n}  ");
        assertEquals(2, o.values().size());
    }

    @Test
    void throwsOnMalformedInput() {
        assertThrows(JsonParseException.class, () -> JsonReader.parse("{"));
        assertThrows(JsonParseException.class, () -> JsonReader.parse("{\"a\":}"));
        assertThrows(JsonParseException.class, () -> JsonReader.parse("[1, 2,]"));
        assertThrows(JsonParseException.class, () -> JsonReader.parse("not json"));
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests JsonReaderTest
```

Expected: compilation failures (`JsonValue`, `JsonReader`, `JsonParseException` don't exist).

- [ ] **Step 3: Write `JsonValue.java`**

```java
// src/main/java/spacecolony/save/JsonValue.java
package spacecolony.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sealed tree of JSON values returned by {@link JsonReader#parse(String)}. */
public sealed interface JsonValue
    permits JsonValue.JsonObject,
            JsonValue.JsonArray,
            JsonValue.JsonString,
            JsonValue.JsonNumber,
            JsonValue.JsonBool,
            JsonValue.JsonNull {

    record JsonObject(Map<String, JsonValue> values) implements JsonValue {
        public JsonObject() { this(new LinkedHashMap<>()); }
    }
    record JsonArray(List<JsonValue> values) implements JsonValue {}
    record JsonString(String value) implements JsonValue {}
    /** Number is stored as a string for lossless representation; convert via {@link #asLong} or {@link #asDouble}. */
    record JsonNumber(String text) implements JsonValue {
        public long asLong() { return Long.parseLong(text); }
        public double asDouble() { return Double.parseDouble(text); }
    }
    record JsonBool(boolean value) implements JsonValue {}
    record JsonNull() implements JsonValue {}
}
```

- [ ] **Step 4: Write `JsonParseException.java`**

```java
// src/main/java/spacecolony/save/JsonParseException.java
package spacecolony.save;

/** Thrown by {@link JsonReader#parse(String)} on malformed input. Unchecked. */
public class JsonParseException extends RuntimeException {
    public final int position;
    public JsonParseException(String message, int position) {
        super(message + " at position " + position);
        this.position = position;
    }
}
```

- [ ] **Step 5: Write `JsonReader.java`**

```java
// src/main/java/spacecolony/save/JsonReader.java
package spacecolony.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Hand-rolled recursive-descent JSON parser. Returns a {@link JsonValue} tree. */
public final class JsonReader {
    private final String src;
    private int pos;

    private JsonReader(String src) { this.src = src; this.pos = 0; }

    public static JsonValue parse(String text) {
        JsonReader r = new JsonReader(text);
        r.skipWs();
        JsonValue v = r.parseValue();
        r.skipWs();
        if (r.pos != r.src.length()) throw r.err("trailing content");
        return v;
    }

    private JsonValue parseValue() {
        skipWs();
        if (pos >= src.length()) throw err("unexpected end of input");
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> new JsonValue.JsonString(parseString());
            case 't', 'f' -> parseBool();
            case 'n' -> parseNull();
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
            default -> throw err("unexpected character '" + c + "'");
        };
    }

    private JsonValue.JsonObject parseObject() {
        expect('{');
        Map<String, JsonValue> map = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { pos++; return new JsonValue.JsonObject(map); }
        while (true) {
            skipWs();
            if (peek() != '"') throw err("expected string key");
            String key = parseString();
            skipWs();
            expect(':');
            JsonValue v = parseValue();
            map.put(key, v);
            skipWs();
            char next = peek();
            if (next == ',') { pos++; continue; }
            if (next == '}') { pos++; return new JsonValue.JsonObject(map); }
            throw err("expected ',' or '}'");
        }
    }

    private JsonValue.JsonArray parseArray() {
        expect('[');
        List<JsonValue> values = new ArrayList<>();
        skipWs();
        if (peek() == ']') { pos++; return new JsonValue.JsonArray(values); }
        while (true) {
            JsonValue v = parseValue();
            values.add(v);
            skipWs();
            char next = peek();
            if (next == ',') { pos++; continue; }
            if (next == ']') { pos++; return new JsonValue.JsonArray(values); }
            throw err("expected ',' or ']'");
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= src.length()) throw err("dangling escape");
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > src.length()) throw err("bad unicode escape");
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw err("bad escape '\\" + esc + "'");
                }
            } else {
                sb.append(c);
            }
        }
        throw err("unterminated string");
    }

    private JsonValue.JsonNumber parseNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length() && "0123456789.eE+-".indexOf(src.charAt(pos)) >= 0) pos++;
        String text = src.substring(start, pos);
        try {
            Double.parseDouble(text);  // validate
        } catch (NumberFormatException e) {
            throw err("invalid number '" + text + "'");
        }
        return new JsonValue.JsonNumber(text);
    }

    private JsonValue parseBool() {
        if (src.startsWith("true", pos))  { pos += 4; return new JsonValue.JsonBool(true); }
        if (src.startsWith("false", pos)) { pos += 5; return new JsonValue.JsonBool(false); }
        throw err("expected true/false");
    }

    private JsonValue parseNull() {
        if (src.startsWith("null", pos)) { pos += 4; return new JsonValue.JsonNull(); }
        throw err("expected null");
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) throw err("expected '" + c + "'");
        pos++;
    }

    private char peek() {
        if (pos >= src.length()) throw err("unexpected end of input");
        return src.charAt(pos);
    }

    private JsonParseException err(String msg) { return new JsonParseException(msg, pos); }
}
```

- [ ] **Step 6: Run, confirm pass**

```bash
./gradlew test --tests JsonReaderTest
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/spacecolony/save/JsonValue.java \
        src/main/java/spacecolony/save/JsonParseException.java \
        src/main/java/spacecolony/save/JsonReader.java \
        src/test/java/spacecolony/save/JsonReaderTest.java
git commit -m "feat(save): hand-rolled JSON reader (recursive descent, no deps)"
```

---

### Task 14: `JsonWriter`

Pretty-printer matched to the reader. Sorts object keys alphabetically (so save-file diffs are stable) and indents with 2 spaces.

**Files:**
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/save/JsonWriter.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/save/JsonWriterTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/save/JsonWriterTest.java
package spacecolony.save;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonWriterTest {

    @Test
    void writesPrimitives() {
        assertEquals("null",   JsonWriter.write(new JsonValue.JsonNull()));
        assertEquals("true",   JsonWriter.write(new JsonValue.JsonBool(true)));
        assertEquals("false",  JsonWriter.write(new JsonValue.JsonBool(false)));
        assertEquals("42",     JsonWriter.write(new JsonValue.JsonNumber("42")));
        assertEquals("\"hi\"", JsonWriter.write(new JsonValue.JsonString("hi")));
    }

    @Test
    void escapesSpecialCharsInStrings() {
        String out = JsonWriter.write(new JsonValue.JsonString("a\"b\\c\nd"));
        assertEquals("\"a\\\"b\\\\c\\nd\"", out);
    }

    @Test
    void writesEmptyArrayAndObject() {
        assertEquals("[]", JsonWriter.write(new JsonValue.JsonArray(List.of())));
        assertEquals("{}", JsonWriter.write(new JsonValue.JsonObject(new LinkedHashMap<>())));
    }

    @Test
    void sortsObjectKeysAlphabetically() {
        Map<String, JsonValue> m = new LinkedHashMap<>();
        m.put("zebra", new JsonValue.JsonNumber("1"));
        m.put("apple", new JsonValue.JsonNumber("2"));
        String out = JsonWriter.write(new JsonValue.JsonObject(m));
        assertTrue(out.indexOf("\"apple\"") < out.indexOf("\"zebra\""),
            "apple must precede zebra in output: " + out);
    }

    @Test
    void prettyPrintsNestedStructure() {
        Map<String, JsonValue> m = new LinkedHashMap<>();
        m.put("a", new JsonValue.JsonArray(List.of(
            new JsonValue.JsonNumber("1"), new JsonValue.JsonNumber("2"))));
        m.put("b", new JsonValue.JsonString("hi"));
        String out = JsonWriter.write(new JsonValue.JsonObject(m));
        // Pretty-printed: contains newlines and 2-space indent.
        assertTrue(out.contains("\n"));
        assertTrue(out.contains("  \"a\""));
    }

    @Test
    void roundTrip_preservesStructure() {
        String original = "{\"a\":1,\"b\":[true,null,\"x\"],\"c\":{\"d\":-2.5}}";
        JsonValue tree = JsonReader.parse(original);
        String written = JsonWriter.write(tree);
        JsonValue reparsed = JsonReader.parse(written);
        assertEquals(JsonWriter.write(tree), JsonWriter.write(reparsed),
            "round-trip should be idempotent after first parse+write");
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests JsonWriterTest
```

Expected: compilation failure.

- [ ] **Step 3: Write `JsonWriter.java`**

```java
// src/main/java/spacecolony/save/JsonWriter.java
package spacecolony.save;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pretty-prints {@link JsonValue} trees with sorted keys and 2-space indent. */
public final class JsonWriter {
    private JsonWriter() {}

    public static String write(JsonValue v) {
        StringBuilder sb = new StringBuilder();
        append(sb, v, 0);
        return sb.toString();
    }

    private static void append(StringBuilder sb, JsonValue v, int indent) {
        switch (v) {
            case JsonValue.JsonNull n -> sb.append("null");
            case JsonValue.JsonBool b -> sb.append(b.value() ? "true" : "false");
            case JsonValue.JsonNumber n -> sb.append(n.text());
            case JsonValue.JsonString s -> appendString(sb, s.value());
            case JsonValue.JsonArray a -> appendArray(sb, a, indent);
            case JsonValue.JsonObject o -> appendObject(sb, o, indent);
        }
    }

    private static void appendString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    private static void appendArray(StringBuilder sb, JsonValue.JsonArray a, int indent) {
        if (a.values().isEmpty()) { sb.append("[]"); return; }
        sb.append('[');
        for (int i = 0; i < a.values().size(); i++) {
            sb.append('\n');
            indent(sb, indent + 1);
            append(sb, a.values().get(i), indent + 1);
            if (i < a.values().size() - 1) sb.append(',');
        }
        sb.append('\n');
        indent(sb, indent);
        sb.append(']');
    }

    private static void appendObject(StringBuilder sb, JsonValue.JsonObject o, int indent) {
        if (o.values().isEmpty()) { sb.append("{}"); return; }
        sb.append('{');
        List<String> keys = new ArrayList<>(o.values().keySet());
        keys.sort(String::compareTo);
        for (int i = 0; i < keys.size(); i++) {
            sb.append('\n');
            indent(sb, indent + 1);
            appendString(sb, keys.get(i));
            sb.append(": ");
            append(sb, o.values().get(keys.get(i)), indent + 1);
            if (i < keys.size() - 1) sb.append(',');
        }
        sb.append('\n');
        indent(sb, indent);
        sb.append('}');
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
    }
}
```

- [ ] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests JsonWriterTest --tests JsonReaderTest
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/save/JsonWriter.java \
        src/test/java/spacecolony/save/JsonWriterTest.java
git commit -m "feat(save): hand-rolled JSON writer (sorted keys, 2-space indent)"
```

---

### Task 15: `IncompatibleSaveException`

Trivial holder; needed so `SaveFile.load` can throw a typed exception that the UI can catch and surface as a warning dialog.

**Files:**
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/save/IncompatibleSaveException.java`

- [ ] **Step 1: Write the class**

```java
// src/main/java/spacecolony/save/IncompatibleSaveException.java
package spacecolony.save;

/**
 * Thrown by {@link SaveFile#load} when the file's schemaVersion does not match
 * the current code's schemaVersion. The world is left unchanged; the UI shows
 * a warning and the player keeps their current game.
 */
public class IncompatibleSaveException extends Exception {
    public final int fileSchemaVersion;
    public final int currentSchemaVersion;
    public IncompatibleSaveException(int fileSchemaVersion, int currentSchemaVersion) {
        super("Save file schema v" + fileSchemaVersion
            + " is incompatible with current schema v" + currentSchemaVersion);
        this.fileSchemaVersion = fileSchemaVersion;
        this.currentSchemaVersion = currentSchemaVersion;
    }
}
```

- [ ] **Step 2: Compile and commit**

```bash
./gradlew compileJava
git add src/main/java/spacecolony/save/IncompatibleSaveException.java
git commit -m "feat(save): add IncompatibleSaveException"
```

---

### Task 16: `SaveFile.save` with atomic write

Build the envelope `JsonValue` from a `World` and atomically write it to disk via a `.tmp` sibling. Schema version is `1`.

**Files:**
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/save/SaveFile.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/save/SaveFileSaveTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/spacecolony/save/SaveFileSaveTest.java
package spacecolony.save;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveFileSaveTest {

    @Test
    void save_writesFileWithSchemaVersion1(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(7L);
        Path file = tmp.resolve("test.json");
        SaveFile.save(w, file);
        assertTrue(Files.exists(file));
        String content = Files.readString(file);
        JsonValue.JsonObject root = (JsonValue.JsonObject) JsonReader.parse(content);
        assertEquals(1L, ((JsonValue.JsonNumber) root.values().get("schemaVersion")).asLong());
        assertEquals(7L, ((JsonValue.JsonNumber) root.values().get("seed")).asLong());
    }

    @Test
    void save_createsParentDirIfMissing(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        Path nested = tmp.resolve("a").resolve("b").resolve("c.json");
        SaveFile.save(w, nested);
        assertTrue(Files.exists(nested));
    }

    @Test
    void save_atomicReplace_leavesNoTmpFile(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        Path file = tmp.resolve("test.json");
        SaveFile.save(w, file);
        SaveFile.save(w, file); // overwrite
        try (var stream = Files.list(tmp)) {
            long tmpCount = stream.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
            assertEquals(0, tmpCount, "no .tmp files should linger after successful save");
        }
    }

    @Test
    void save_includesAllPlayerState(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(42L);
        w.credits = 12345;
        w.tick = 100;
        w.tech.researched.add("basic-mining");
        Path file = tmp.resolve("test.json");
        SaveFile.save(w, file);
        JsonValue.JsonObject root = (JsonValue.JsonObject) JsonReader.parse(Files.readString(file));
        assertEquals(100L, ((JsonValue.JsonNumber) root.values().get("tick")).asLong());
        assertEquals(12345L, ((JsonValue.JsonNumber) root.values().get("credits")).asLong());
        JsonValue.JsonObject tech = (JsonValue.JsonObject) root.values().get("tech");
        JsonValue.JsonArray researched = (JsonValue.JsonArray) tech.values().get("researched");
        assertEquals(1, researched.values().size());
        assertEquals("basic-mining", ((JsonValue.JsonString) researched.values().get(0)).value());
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests SaveFileSaveTest
```

Expected: compilation failure.

- [ ] **Step 3: Write `SaveFile.java` (save side only — `load` stub in Task 17)**

```java
// src/main/java/spacecolony/save/SaveFile.java
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
import spacecolony.sim.Transit;
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
```

- [ ] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests SaveFileSaveTest
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/save/SaveFile.java \
        src/test/java/spacecolony/save/SaveFileSaveTest.java
git commit -m "feat(save): SaveFile.save with atomic write and schema v1 envelope"
```

---

### Task 17: `SaveFile.load`

Parse the envelope; if schema mismatch, throw `IncompatibleSaveException`. Otherwise `WorldGenerator.generate(seed)` to rebuild geometry, then walk the tree and reattach sites/ships/tech/goals/events/credits/tick.

**Files:**
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/save/SaveFile.java`
- Create: `/Users/steve/projects/space-colony/src/test/java/spacecolony/save/SaveFileLoadTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/spacecolony/save/SaveFileLoadTest.java
package spacecolony.save;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Simulator;
import spacecolony.sim.Site;
import spacecolony.sim.World;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveFileLoadTest {

    @Test
    void roundTrip_freshWorld_preservesAllState(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(7L);
        Path f = tmp.resolve("a.json");
        SaveFile.save(w, f);
        World loaded = SaveFile.load(f);
        assertEquals(w.tick, loaded.tick);
        assertEquals(w.seed, loaded.seed);
        assertEquals(w.credits, loaded.credits);
        assertEquals(w.bodies.size(), loaded.bodies.size());
        assertNotNull(loaded.findSite("site-earth-hub"));
    }

    @Test
    void roundTrip_afterSimulation_isDeterministic(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        for (int i = 0; i < 500; i++) sim.advance(w);
        Path f = tmp.resolve("a.json");
        SaveFile.save(w, f);
        World loaded = SaveFile.load(f);

        // Advance both 100 more ticks; resulting state should match.
        Simulator s1 = new Simulator(), s2 = new Simulator();
        for (int i = 0; i < 100; i++) { s1.advance(w); s2.advance(loaded); }

        assertEquals(w.tick, loaded.tick);
        assertEquals(w.credits, loaded.credits);
        assertEquals(w.tech.researched, loaded.tech.researched);
        assertEquals(w.goals.achieved, loaded.goals.achieved);
        Site sa = w.findSite("site-earth-hub");
        Site sb = loaded.findSite("site-earth-hub");
        assertEquals(sa.population, sb.population);
        assertEquals(sa.morale, sb.morale, 1e-6);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            assertEquals(sa.stockpile.get(r), sb.stockpile.get(r), 1e-3, "stockpile " + r);
        }
    }

    @Test
    void roundTrip_shipInTransit_preservesArrivalTick(@TempDir Path tmp) throws Exception {
        World w = WorldGenerator.generate(1L);
        // Plant a Mars site and a hauler with fuel; dispatch and let it depart.
        Site mars = new Site("site-mars-1", "Mars 1", "mars", 0.0, 0.0, 100);
        w.findBody("mars").sites.add(mars);
        Ship h = new Ship("h1", "H1", ShipClass.HAULER, "site-earth-hub");
        h.fuel = 1_000_000.0;
        w.ships.add(h);
        w.findSite("site-earth-hub").stockpile.put(Resource.METAL, 200.0);
        Simulator sim = new Simulator();
        sim.enqueue(new DispatchShipCommand("h1", "site-mars-1", Map.of(Resource.METAL, 50.0)));
        for (int i = 0; i < 200 && h.state != ShipState.IN_TRANSIT; i++) sim.advance(w);
        assertEquals(ShipState.IN_TRANSIT, h.state);

        Path f = tmp.resolve("transit.json");
        SaveFile.save(w, f);
        World loaded = SaveFile.load(f);
        Ship lh = loaded.findShip("h1");
        assertEquals(ShipState.IN_TRANSIT, lh.state);
        assertEquals(h.transit.arrivalTick(), lh.transit.arrivalTick());
        assertEquals(h.transit.destSiteId(), lh.transit.destSiteId());
    }

    @Test
    void incompatibleVersion_throws(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("bad.json");
        Files.writeString(f, "{\"schemaVersion\": 99, \"seed\": 1, \"tick\": 0}");
        IncompatibleSaveException ex = assertThrows(IncompatibleSaveException.class,
            () -> SaveFile.load(f));
        assertEquals(99, ex.fileSchemaVersion);
        assertEquals(SaveFile.SCHEMA_VERSION, ex.currentSchemaVersion);
    }

    @Test
    void malformedJson_throws(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("bad.json");
        Files.writeString(f, "{this is not json");
        assertThrows(JsonParseException.class, () -> SaveFile.load(f));
    }

    @Test
    void missingFile_throws(@TempDir Path tmp) {
        Path f = tmp.resolve("nope.json");
        assertThrows(NoSuchFileException.class, () -> SaveFile.load(f));
    }
}
```

- [ ] **Step 2: Run, confirm fail**

```bash
./gradlew test --tests SaveFileLoadTest
```

Expected: tests that exercise `load` either fail or throw `UnsupportedOperationException` from the stub.

- [ ] **Step 3: Replace the `load` stub in `SaveFile.java`**

Replace the `load` method body and add private helpers:

```java
public static World load(Path file) throws IOException, IncompatibleSaveException {
    String text = Files.readString(file);
    JsonValue.JsonObject root = (JsonValue.JsonObject) JsonReader.parse(text);
    int version = (int) ((JsonValue.JsonNumber) root.values().get("schemaVersion")).asLong();
    if (version != SCHEMA_VERSION) throw new IncompatibleSaveException(version, SCHEMA_VERSION);

    long seed = ((JsonValue.JsonNumber) root.values().get("seed")).asLong();
    World w = spacecolony.world.WorldGenerator.generate(seed);
    w.tick = ((JsonValue.JsonNumber) root.values().get("tick")).asLong();
    w.credits = ((JsonValue.JsonNumber) root.values().get("credits")).asLong();

    // Sites: clear regenerated starting site if file overrides it, then reattach.
    // The starting site only exists on Earth Hub from WorldGenerator. Wipe sites on
    // every body so the file is authoritative.
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

    // Ships
    w.ships.clear();
    for (JsonValue sv : ((JsonValue.JsonArray) root.values().get("ships")).values()) {
        w.ships.add(loadShip((JsonValue.JsonObject) sv));
    }

    // Tech
    JsonValue.JsonObject tech = (JsonValue.JsonObject) root.values().get("tech");
    for (JsonValue v : ((JsonValue.JsonArray) tech.values().get("researched")).values()) {
        w.tech.researched.add(((JsonValue.JsonString) v).value());
    }
    JsonValue activeId = tech.values().get("activeId");
    w.tech.activeId = activeId instanceof JsonValue.JsonString jsa ? jsa.value() : null;
    w.tech.accumulatedPoints = ((JsonValue.JsonNumber) tech.values().get("accumulatedPoints")).asDouble();

    // Goals
    JsonValue.JsonObject goals = (JsonValue.JsonObject) root.values().get("goals");
    for (JsonValue v : ((JsonValue.JsonArray) goals.values().get("achieved")).values()) {
        w.goals.achieved.add(((JsonValue.JsonString) v).value());
    }

    // Events
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
        spacecolony.sim.BuildingType type = spacecolony.sim.BuildingType.valueOf(
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
        Map<Resource, Double> snapshot = new java.util.EnumMap<>(Resource.class);
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
    spacecolony.sim.EventSeverity sev = spacecolony.sim.EventSeverity.valueOf(
        ((JsonValue.JsonString) o.values().get("severity")).value());
    spacecolony.sim.EventKind kind = spacecolony.sim.EventKind.valueOf(
        ((JsonValue.JsonString) o.values().get("kind")).value());
    String msg = ((JsonValue.JsonString) o.values().get("message")).value();
    String bodyId = optString(o, "bodyId");
    String siteId = optString(o, "siteId");
    String shipId = optString(o, "shipId");
    return new Event(tick, sev, kind, msg, bodyId, siteId, shipId);
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
```

You'll need to add these imports at the top of `SaveFile.java`:

```java
import spacecolony.sim.Event;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
```

(Some are already imported. Just make sure all are present.)

- [ ] **Step 4: Run, confirm pass**

```bash
./gradlew test --tests SaveFileLoadTest --tests SaveFileSaveTest
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/save/SaveFile.java \
        src/test/java/spacecolony/save/SaveFileLoadTest.java
git commit -m "feat(save): SaveFile.load with WorldGenerator regen + player state reattach"
```

---

### Task 18: `FileMenu` + wire to `SpaceColonyFrame`

Add a JMenuBar with File menu (New / Save / Load / Quit) on `SpaceColonyFrame`. Each action asserts EDT, pauses the game loop, runs IO inside a `SwingWorker`, and resumes.

**Files:**
- Create: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/FileMenu.java`
- Modify: `/Users/steve/projects/space-colony/src/main/java/spacecolony/ui/SpaceColonyFrame.java`

- [ ] **Step 1: Write `FileMenu.java`**

```java
// src/main/java/spacecolony/ui/FileMenu.java
package spacecolony.ui;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import spacecolony.engine.EdtGuard;
import spacecolony.engine.Engine;
import spacecolony.engine.Speed;
import spacecolony.save.IncompatibleSaveException;
import spacecolony.save.JsonParseException;
import spacecolony.save.SaveFile;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;

/** File menu (New / Save / Load / Quit) mounted on the main frame. */
public final class FileMenu extends JMenuBar {
    private static final Path SAVES_DIR =
        Paths.get(System.getProperty("user.home"), ".space-colony", "saves");

    private final JFrame owner;
    private final Engine engine;

    public FileMenu(JFrame owner, Engine engine) {
        this.owner = owner;
        this.engine = engine;
        JMenu file = new JMenu("File");
        file.add(new JMenuItem(new NewAction()));
        file.add(new JMenuItem(new SaveAction()));
        file.add(new JMenuItem(new LoadAction()));
        file.addSeparator();
        file.add(new JMenuItem(new QuitAction()));
        add(file);
    }

    private JFileChooser chooser(String dialogTitle, boolean save) {
        JFileChooser ch = new JFileChooser(SAVES_DIR.toFile());
        ch.setDialogTitle(dialogTitle);
        ch.setFileFilter(new FileNameExtensionFilter("Space Colony saves (*.json)", "json"));
        if (save) ch.setDialogType(JFileChooser.SAVE_DIALOG);
        return ch;
    }

    private final class NewAction extends AbstractAction {
        NewAction() { super("New Game"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            int choice = JOptionPane.showConfirmDialog(owner,
                "Discard the current game?", "New Game", JOptionPane.OK_CANCEL_OPTION);
            if (choice != JOptionPane.OK_OPTION) { engine.setSpeed(prior); return; }
            String seedStr = JOptionPane.showInputDialog(owner,
                "Seed:", Long.toString(System.currentTimeMillis()));
            if (seedStr == null) { engine.setSpeed(prior); return; }
            try {
                long seed = Long.parseLong(seedStr.trim());
                engine.reset(WorldGenerator.generate(seed));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(owner, "Not a valid number: " + seedStr,
                    "New Game", JOptionPane.ERROR_MESSAGE);
            }
            // Leave the game paused after reset; player presses 1× to start.
        }
    }

    private final class SaveAction extends AbstractAction {
        SaveAction() { super("Save…"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            JFileChooser ch = chooser("Save Game", true);
            int r = ch.showSaveDialog(owner);
            if (r != JFileChooser.APPROVE_OPTION) { engine.setSpeed(prior); return; }
            Path file = ch.getSelectedFile().toPath();
            if (!file.getFileName().toString().endsWith(".json")) {
                file = file.resolveSibling(file.getFileName().toString() + ".json");
            }
            final Path target = file;
            final World world = engine.world();
            new SwingWorker<Void, Void>() {
                IOException ioErr;
                @Override protected Void doInBackground() {
                    try { SaveFile.save(world, target); }
                    catch (IOException ex) { ioErr = ex; }
                    return null;
                }
                @Override protected void done() {
                    EdtGuard.assertEdt();
                    if (ioErr != null) {
                        JOptionPane.showMessageDialog(owner,
                            "Could not save: " + ioErr.getMessage(),
                            "Save Error", JOptionPane.ERROR_MESSAGE);
                    }
                    engine.setSpeed(prior);
                }
            }.execute();
        }
    }

    private final class LoadAction extends AbstractAction {
        LoadAction() { super("Load…"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            Speed prior = engine.speed();
            engine.setSpeed(Speed.PAUSED);
            JFileChooser ch = chooser("Load Game", false);
            int r = ch.showOpenDialog(owner);
            if (r != JFileChooser.APPROVE_OPTION) { engine.setSpeed(prior); return; }
            Path file = ch.getSelectedFile().toPath();
            new SwingWorker<World, Void>() {
                Exception err;
                @Override protected World doInBackground() {
                    try { return SaveFile.load(file); }
                    catch (Exception ex) { err = ex; return null; }
                }
                @Override protected void done() {
                    EdtGuard.assertEdt();
                    if (err instanceof IncompatibleSaveException inc) {
                        JOptionPane.showMessageDialog(owner,
                            "This save was written with schema v" + inc.fileSchemaVersion
                                + "; the current game uses v" + inc.currentSchemaVersion
                                + ". Cannot load this save.",
                            "Incompatible Save", JOptionPane.WARNING_MESSAGE);
                    } else if (err instanceof JsonParseException jpe) {
                        JOptionPane.showMessageDialog(owner,
                            "Save file is not valid JSON: " + jpe.getMessage(),
                            "Load Error", JOptionPane.ERROR_MESSAGE);
                    } else if (err != null) {
                        JOptionPane.showMessageDialog(owner,
                            "Could not load: " + err.getMessage(),
                            "Load Error", JOptionPane.ERROR_MESSAGE);
                    } else {
                        try { engine.reset(get()); }
                        catch (Exception ignore) { /* covered by err branch above */ }
                    }
                    engine.setSpeed(prior);
                }
            }.execute();
        }
    }

    private final class QuitAction extends AbstractAction {
        QuitAction() { super("Quit"); }
        @Override public void actionPerformed(ActionEvent e) {
            EdtGuard.assertEdt();
            int r = JOptionPane.showConfirmDialog(owner, "Quit Space Colony?",
                "Quit", JOptionPane.OK_CANCEL_OPTION);
            if (r == JOptionPane.OK_OPTION) System.exit(0);
        }
    }
}
```

- [ ] **Step 2: Mount `FileMenu` on `SpaceColonyFrame`**

In `SpaceColonyFrame.java`'s constructor, add the menu bar after `super("Space Colony");` and before the `add(new TopBar(...))` line. The full constructor:

```java
public SpaceColonyFrame(Engine engine) {
    super("Space Colony");
    this.engine = engine;
    this.gameLoop = new GameLoop(engine);

    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setPreferredSize(new Dimension(1280, 800));
    setLayout(new BorderLayout());
    getContentPane().setBackground(UiColors.BACKGROUND);

    setJMenuBar(new FileMenu(this, engine));

    add(new TopBar(engine), BorderLayout.NORTH);
    ColonyListPanel colonyList = new ColonyListPanel(engine);
    colonyList.setPreferredSize(new Dimension(220, 0));
    add(colonyList, BorderLayout.WEST);
    add(new MainViewPanel(engine), BorderLayout.CENTER);
    DetailPanel detail = new DetailPanel(engine);
    detail.setPreferredSize(new Dimension(280, 0));
    add(detail, BorderLayout.EAST);
    add(new EventStripPanel(engine), BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(null);
}
```

- [ ] **Step 3: Run all tests + verify UI launches**

```bash
./gradlew test
./gradlew play --args="--seed 1"
```

Expected: all tests PASS; UI launches with a "File" menu in the menu bar showing New / Save / Load / Quit. Close the window manually.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/FileMenu.java \
        src/main/java/spacecolony/ui/SpaceColonyFrame.java
git commit -m "feat(ui): add File menu (New/Save/Load/Quit) backed by SwingWorker"
```

---

### Task 19: End-to-end determinism with techs + manual play-test

Final regression checks + manual UI test. No new code.

- [ ] **Step 1: Add a determinism test that exercises tech effects**

```java
// Append to src/test/java/spacecolony/sim/DeterminismTest.java
@Test
void techWiring_stillDeterministic() {
    World a = spacecolony.world.WorldGenerator.generate(7777L);
    World b = spacecolony.world.WorldGenerator.generate(7777L);
    Simulator s1 = new Simulator();
    Simulator s2 = new Simulator();
    s1.enqueue(new spacecolony.sim.commands.QueueResearchCommand("basic-mining"));
    s2.enqueue(new spacecolony.sim.commands.QueueResearchCommand("basic-mining"));
    for (int i = 0; i < 2000; i++) { s1.advance(a); s2.advance(b); }
    assertEquals(a.tech.researched, b.tech.researched);
    Site sa = a.findSite("site-earth-hub");
    Site sb = b.findSite("site-earth-hub");
    assertEquals(sa.population, sb.population);
    for (Resource r : Resource.values()) {
        if (!r.isStockpileable()) continue;
        assertEquals(sa.stockpile.get(r), sb.stockpile.get(r), 1e-6, "stockpile " + r);
    }
}
```

- [ ] **Step 2: Run full test suite**

```bash
./gradlew test
```

Expected: all ~125 tests PASS (86 carryover + ~39 new).

- [ ] **Step 3: Verify no Swing imports leaked into sim/world/save**

```bash
grep -r "import javax.swing\|import java.awt" \
  src/main/java/spacecolony/sim \
  src/main/java/spacecolony/world \
  src/main/java/spacecolony/save
```

Expected: no matches. (`spacecolony.save` is allowed to depend on `sim` + `world`, but never on Swing/AWT.)

- [ ] **Step 4: Manual play-test checklist**

Launch:

```bash
./gradlew play --args="--seed 1"
```

Run through this checklist, fixing anything that fails before opening the PR:

1. **Menu present.** File menu visible in menu bar; shows New / Save / Load / Quit.
2. **Habitat boost visible.** Select Earth Hub in the colony list. Note `populationCap` in the detail panel (should be 300). Build a HABITAT via the build dialog. Wait one tick. populationCap rises to 400.
3. **Mine tech works.** Note ore stockpile delta per tick on Earth Hub. Queue research `basic-mining`. Wait for completion. Ore delta per tick is ~10% higher.
4. **Save round-trip.** File → Save. Pick a filename like `test.json`. Confirm the file exists at `~/.space-colony/saves/test.json`. File → New → enter any seed → world resets. File → Load → choose `test.json` → game state restored (tick, credits, sites all match what was saved).
5. **Incompatible save warning.** Edit `~/.space-colony/saves/test.json` in a text editor; change `"schemaVersion": 1` to `"schemaVersion": 99`. File → Load → see warning dialog; world unchanged.
6. **Malformed save error.** Overwrite the file with `{not json`. File → Load → see error dialog; world unchanged.
7. **Mid-transit save.** Build a Hauler, dispatch it Earth → Mars (via build/dispatch dialogs). Save while it's IN_TRANSIT. Load. Ship should still be IN_TRANSIT with the same arrival tick.

- [ ] **Step 5: Push branch and open PR**

```bash
git push -u origin plan-4/save-and-sim-wiring
gh pr create --title "Plan 4: save/load + sim-side tech wiring + HABITAT cap" --body "$(cat <<'EOF'
## Summary
- Hand-rolled JSON save/load (`spacecolony.save.*`), schema v1, atomic writes to `~/.space-colony/saves/<name>.json`.
- New `spacecolony.sim.TechEffects` wires all 17 v1 techs into Production/Transit/Event/Research phases.
- HABITAT buildings now raise `populationCap` per spec: `(siteBase + Σ level·100) × popCapMultiplier`. New `Site.siteBase` field (200 for Earth Hub, 100 for colonizer-planted).
- `Engine.reset(World)` swaps worlds on Load/New; `EngineEvent.WorldReplaced` notifies the 7 listening panels.
- EDT discipline enforced via `EdtGuard.assertEdt()` and `-ea` on test/run/play tasks.

## Test plan
- [x] All ~125 unit tests pass (`./gradlew test`).
- [x] Manual play-test: HABITAT cap rises with build, tech effects visibly change rates, File menu save/load round-trips, schema-mismatch warning fires, mid-transit ships survive round-trip.
- [x] No Swing/AWT imports in `sim`, `world`, or `save` packages.

## Out of scope (Plan 5)
- Debug Mode panel; tech tree / goals / events panel polish; autosave; multi-slot picker; UI surfacing of `moraleCeiling > 1.0`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Return the PR URL.

- [ ] **Step 6: Commit the determinism test**

```bash
git add src/test/java/spacecolony/sim/DeterminismTest.java
git commit -m "test(sim): full-suite determinism check with tech wiring"
git push
```

---

## Verification

After all tasks complete:

1. **Full test suite passes:**

   ```bash
   cd /Users/steve/projects/space-colony
   export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
   ./gradlew test
   ```

   Expected: ~125 tests PASS.

2. **Headless main still runs:**

   ```bash
   ./gradlew run --args="--seed 1 --ticks 1000"
   ```

   Expected: completes in well under a second, prints state summary.

3. **No layering leaks:**

   ```bash
   grep -r "import javax.swing\|import java.awt" \
     src/main/java/spacecolony/sim \
     src/main/java/spacecolony/world \
     src/main/java/spacecolony/save
   ```

   Expected: no matches.

4. **`-ea` is on in test runs (verify guard fires):**

   `EngineEdtTest.enqueueOffEdt_throwsAssertionError` exercises this; if it passes, `-ea` is active.

5. **UI launches and File menu round-trips:** Manual checklist in Task 19 Step 4 passes end-to-end.

## What's Next

Plan 5: Debug Mode panel from game-design spec §8 (manual event injection, tick stepping, FPS overlay); tech-tree / goals / events panel polish (surface life-support morale ceiling, show tech effect deltas in dock); autosave + multi-slot picker; optional save thumbnails.

## Self-Review Notes

Issues found during the post-write self-review (fixed inline above):
- Initial draft assumed `Simulator` was a monolithic class with `productionAndConsumption(World)` etc. — actual code uses `spacecolony.sim.phases.*` with phase classes (`ProductionPhase.run`, `TransitPhase.loadingAndUnloading`, `EventPhase.applyEvent`, `ResearchPhase.run`). All call-site edits in Tasks 7–12 reference the actual phase methods.
- `CommandPhase.applyDispatchShip` also computes a fuel estimate up front — Task 8 wires the multiplier in both places (transit departure AND command-time estimate) so tech effects don't desync.
- `Engine.world` was `final`; Task 4 explicitly drops `final` and explains the rationale.
- `EngineEvent` is sealed with 4 permitees; Task 2 adds the new `WorldReplaced` permitee and updates each of the 7 listening panels.
- `Site` constructor signature change in Task 1: the trailing `int` argument was `populationCap`, now `siteBase`. The two existing call sites (`WorldGenerator` and `CommandPhase.applyBuildSite`) keep their literal values (`200` and `100`) — semantically equivalent, no fix-up needed.
- `Simulator.commandQueue` is `private`; Task 3 adds a `clearCommands()` accessor for `Engine.reset`.
- Plan 1 originally had the SOLAR_FLARE re-enable trick happen inside Simulator; the actual `ProductionPhase` already re-enables power plants at end-of-iteration (line 96), so the wiring task doesn't need to add it again.
- `EventPhase.applyEvent` is private; Task 10 adds a package-private `applyForTest` hook so the test can deterministically trigger DISEASE_OUTBREAK rather than waiting for randomness.

## Spec Coverage

| Spec section | Task |
|---|---|
| §2 Architecture overview — new packages, EDT guards | Tasks 0, 4, 5, 18 |
| §3.1–§3.2 Save file layout, schema v1 envelope | Tasks 13, 14, 16, 17 |
| §3.3 Persisted vs. regenerated state | Task 17 |
| §3.4 SaveFile API | Tasks 16, 17 |
| §3.5 UI hookup (File menu) | Task 18 |
| §3.6 Hand-rolled JSON | Tasks 13, 14 |
| §3.7 Failure modes | Tasks 16, 17, 18 |
| §4.1–§4.4 TechEffects table + API + call-site wiring | Tasks 6, 7, 8, 9, 10, 11 |
| §5.1–§5.5 HABITAT cap formula, `siteBase` field, recompute | Tasks 1, 12 |
| §6 EDT discipline | Tasks 0, 5 |
| §7.1 TechEffects unit tests | Task 6 |
| §7.2 Sim-wiring tests | Tasks 7, 8, 9, 10, 11 |
| §7.3 HABITAT cap tests | Task 12 |
| §7.4 Save/load tests | Tasks 16, 17 |
| §7.5 EDT guard test | Task 5 |
| §7.7 Manual play-test checklist | Task 19 |
