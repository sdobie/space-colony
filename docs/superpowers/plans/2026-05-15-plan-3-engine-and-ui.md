# Space Colony Game — Plan 3: Engine + UI Shell

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the game playable in a Swing window. Wire the Plan 1 simulator and the Plan 2 renderer into a `SpaceColonyApp` (`JFrame`) with the C-layout dock (system map center, colony list left, detail right, top bar, event strip), a body view (full-screen sphere with click-to-place-site), and modal dialogs for the four player actions (build building, build ship, dispatch ship, place site). Plus basic modal Tech and Goals list panels. As a prerequisite, decompose the Plan 1 Simulator into per-phase classes so the UI doesn't pull on a god class.

**Architecture:** Single-threaded EDT. A `GameLoop` Swing `Timer` fires tick events at speed-controlled intervals; on each tick the `Engine` drains its command queue into the simulator and calls `Simulator.advance(world)`, then notifies UI listeners which repaint from world state. UI never mutates `World` directly — it builds `Command` records and calls `engine.enqueue(c)`. Each render-heavy panel caches the per-body flat map; the sphere itself re-renders per frame (fast enough at 200–512px output).

**Tech Stack:** Java 25, Gradle 9 via `./gradlew`, JUnit 5, AWT/Swing (JDK only). No new runtime dependencies.

**Spec reference:** `/Users/steve/projects/space-colony/docs/superpowers/specs/2026-05-03-space-colony-game-design.md` §4.3 (threading), §7 (UI components).

**Plans 1 + 2 status:** merged on `main` at `672d0ac`. Sim core (70 tests) + render package complete.

**Plan 4 (out of scope here):** Save/load (JSON), tech-effect multipliers wired into production, debug mode overlay, expanded tech/goals UI, sound.

---

## Context

This is the biggest plan because Swing UI is wide and shallow: many small panels and dialogs, none individually deep, but they all have to fit together. The architecture is:

```
SpaceColonyApp (main)
  └── SpaceColonyFrame (JFrame)
       ├── TopBar (NORTH)             — tick, date, speed, credits, menu
       ├── ColonyListPanel (WEST)     — sites + ships, click to select
       ├── MainViewPanel (CENTER)     — CardLayout: SystemMapPanel ↔ BodyViewPanel
       ├── DetailPanel (EAST)         — selection-dependent content
       └── EventStripPanel (SOUTH)    — recent events

      ↑ all panels subscribe to Engine events ↑

Engine
  ├── World (Plan 1 state)
  ├── Simulator (orchestrator now; Plan 1 phases extracted)
  ├── commandQueue (ArrayDeque)
  ├── listeners (EngineListener)
  └── GameLoop (Swing Timer with speed control)

UI builds Command records ──► engine.enqueue(c) ──► next tick drains
                                                  Simulator.advance(world)
                                                  fires WorldChanged
                                                  UI panels repaint
```

The first six tasks are an internal refactor of Plan 1's Simulator into per-phase classes (a follow-up flagged by the Plan 1 holistic review). The next twenty-two tasks build the engine and UI on top of that cleaner base.

### Manual verification rather than UI unit tests

Swing UI is hard to unit-test in a way that catches the bugs that actually matter (layout, sizing, mouse coordinates). For each panel we add a smoke test that constructs the panel and paints it to a `BufferedImage` — this catches NPE/init bugs cheaply. End-to-end UI correctness is verified manually via a checklist in Task 28.

---

## File Structure

```
src/main/java/spacecolony/
├── Main.java                              (Plan 1 headless driver — KEEP unchanged)
├── SpaceColonyApp.java                    (NEW — Swing main entry)
├── engine/
│   ├── Engine.java                        (NEW — wraps Simulator, owns command queue, fires events)
│   ├── GameLoop.java                      (NEW — Swing Timer + speed control)
│   ├── Speed.java                         (NEW — enum PAUSED / X1 / X4 / X16)
│   ├── Selection.java                     (NEW — record of selection type + id)
│   ├── EngineListener.java                (NEW — interface)
│   └── EngineEvent.java                   (NEW — sealed: WorldChanged, SelectionChanged, SpeedChanged, ViewChanged)
├── ui/
│   ├── SpaceColonyFrame.java              (NEW — JFrame + BorderLayout assembly)
│   ├── TopBar.java                        (NEW — NORTH)
│   ├── ColonyListPanel.java               (NEW — WEST)
│   ├── MainViewPanel.java                 (NEW — CENTER, CardLayout)
│   ├── SystemMapPanel.java                (NEW — solar system view)
│   ├── BodyViewPanel.java                 (NEW — full-screen sphere view)
│   ├── DetailPanel.java                   (NEW — EAST)
│   ├── SphereMiniRenderer.java            (NEW — small bounded sphere render for dock)
│   ├── EventStripPanel.java               (NEW — SOUTH)
│   ├── TechModal.java                     (NEW — modal tech list/queue)
│   ├── GoalsModal.java                    (NEW — modal goals list)
│   ├── UiColors.java                      (NEW — shared color constants)
│   └── dialogs/
│       ├── BuildBuildingDialog.java       (NEW)
│       ├── BuildShipDialog.java           (NEW)
│       ├── DispatchShipDialog.java        (NEW)
│       └── PlaceSiteDialog.java           (NEW)
├── sim/                                   (Plan 1, MODIFIED — decomposition)
│   ├── Simulator.java                     (slimmed orchestrator)
│   ├── OrbitalGeometry.java               (NEW — extracted helpers)
│   └── phases/                            (NEW package)
│       ├── CommandPhase.java
│       ├── TransitPhase.java
│       ├── ProductionPhase.java
│       ├── EventPhase.java
│       ├── ResearchPhase.java
│       └── GoalPhase.java
├── render/                                (Plan 2 unchanged)
└── world/                                 (Plan 1 unchanged)

src/test/java/spacecolony/
├── engine/
│   ├── EngineTest.java                    (NEW)
│   ├── GameLoopTest.java                  (NEW)
│   └── SelectionTest.java                 (NEW)
├── ui/
│   └── PanelSmokeTest.java                (NEW — constructs each panel + paints to image)
└── sim/                                   (existing tests, unchanged — they keep passing)
```

---

## Tasks

### Task 1: Extract `OrbitalGeometry` helper

**Files:**
- Create: `src/main/java/spacecolony/sim/OrbitalGeometry.java`
- Modify: `src/main/java/spacecolony/sim/Simulator.java`

The shared math helpers `bodyPosition` and `sunDistance` are used by both the production phase and the transit phase. Extract them to a package-private utility class.

- [ ] **Step 1: Write `OrbitalGeometry.java`**

```java
package spacecolony.sim;

/** Stateless heliocentric coordinate helpers shared by transit and production phases. */
public final class OrbitalGeometry {
    private OrbitalGeometry() {}

    /** Heliocentric (x, y) position of a body at the given tick, accounting for moon parents. */
    public static double[] bodyPosition(World w, String bodyId, long tick) {
        Body b = w.findBody(bodyId);
        if (b == null) return new double[] {0, 0};
        double[] p = b.orbit.position(tick);
        if (b.orbit.parentBodyId() != null) {
            double[] parent = bodyPosition(w, b.orbit.parentBodyId(), tick);
            return new double[] { parent[0] + p[0], parent[1] + p[1] };
        }
        return p;
    }

    /** Distance from a body to the sun (origin) at the world's current tick. */
    public static double sunDistance(World w, Body b) {
        double[] p = bodyPosition(w, b.id, w.tick);
        return Math.sqrt(p[0] * p[0] + p[1] * p[1]);
    }
}
```

- [ ] **Step 2: Update `Simulator.java` to use `OrbitalGeometry`**

Find the private static `bodyPosition` and `sunDistance` methods inside `Simulator.java` (currently at lines ~272 and ~398 per the survey). Delete both. Replace every internal call to `bodyPosition(...)` with `OrbitalGeometry.bodyPosition(...)` and `sunDistance(...)` with `OrbitalGeometry.sunDistance(...)`.

```bash
grep -n "bodyPosition\|sunDistance" /Users/steve/projects/space-colony/src/main/java/spacecolony/sim/Simulator.java
```

Each call site should be a simple rewrite: `bodyPosition(w, ...)` → `OrbitalGeometry.bodyPosition(w, ...)`.

- [ ] **Step 3: Compile + run all tests**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL with 70 tests passing. Determinism is preserved because the math is byte-identical.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/sim/OrbitalGeometry.java src/main/java/spacecolony/sim/Simulator.java
git commit -m "refactor(sim): extract OrbitalGeometry helpers from Simulator"
```

---

### Task 2: Extract `CommandPhase`

**Files:**
- Create: `src/main/java/spacecolony/sim/phases/CommandPhase.java`
- Modify: `src/main/java/spacecolony/sim/Simulator.java`

Move all command-handling logic (drainCommands, apply, applyBuildBuilding, applyBuildShip, applyRetireShip, applyQueueResearch, applyBuildSite, applyDispatchShip, currentBodyOf, CommandRejectedException) to a new package-private class. Simulator keeps the `commandQueue` field (so the public `enqueue()` API is unchanged) but delegates the drain.

- [ ] **Step 1: Create the package and class**

```bash
mkdir -p /Users/steve/projects/space-colony/src/main/java/spacecolony/sim/phases
```

```java
// src/main/java/spacecolony/sim/phases/CommandPhase.java
package spacecolony.sim.phases;

import java.util.Deque;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.Transit;
import spacecolony.sim.World;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.BuildSiteCommand;
import spacecolony.sim.commands.Command;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.sim.commands.RetireShipCommand;

/** Phase 1: drain the engine's command queue, applying each command or emitting a rejection event. */
public final class CommandPhase {
    private static final double FUEL_K = 0.5;

    private CommandPhase() {}

    public static void drain(World w, Deque<Command> queue) {
        while (!queue.isEmpty()) {
            Command c = queue.removeFirst();
            try {
                apply(w, c);
            } catch (CommandRejectedException ex) {
                w.emit(new Event(w.tick, EventSeverity.WARNING, EventKind.COMMAND_REJECTED,
                    ex.getMessage(), null, null, null));
            }
        }
    }

    private static void apply(World w, Command c) {
        switch (c) {
            case BuildBuildingCommand bb -> applyBuildBuilding(w, bb);
            case BuildShipCommand bs     -> applyBuildShip(w, bs);
            case RetireShipCommand rs    -> applyRetireShip(w, rs);
            case QueueResearchCommand qr -> applyQueueResearch(w, qr);
            case BuildSiteCommand bsc    -> applyBuildSite(w, bsc);
            case DispatchShipCommand ds  -> applyDispatchShip(w, ds);
        }
    }

    // — paste each apply* method body from Simulator.java unchanged —
    // (Open Simulator.java in your editor and copy the bodies of:
    //   applyBuildBuilding, applyBuildShip, applyRetireShip, applyQueueResearch,
    //   applyBuildSite, applyDispatchShip
    // Each becomes a private static method here.)

    private static String currentBodyOf(World w, Ship s) {
        if (s.currentSiteId == null) return null;
        Site site = w.findSite(s.currentSiteId);
        return site == null ? null : site.bodyId;
    }

    static class CommandRejectedException extends RuntimeException {
        CommandRejectedException(String m) { super(m); }
    }
}
```

**Important:** The `apply*` method bodies must be copied verbatim from `Simulator.java`. They reference `OrbitalGeometry.bodyPosition` (after Task 1) — this call still works because `OrbitalGeometry` is in the same `spacecolony.sim` package. Each method changes from `private void` to `private static void`. The `FUEL_K` constant moves with `applyDispatchShip` (it's referenced inside the dispatch-time fuel estimate).

- [ ] **Step 2: Update `Simulator.java` — drop the command code, delegate to CommandPhase**

Replace the entire `drainCommands`, `apply`, `applyBuildBuilding`, `applyBuildShip`, `applyRetireShip`, `applyQueueResearch`, `applyBuildSite`, `applyDispatchShip`, `currentBodyOf`, and `CommandRejectedException` block in Simulator.java (currently spanning roughly lines 43-153 + imports) with:

```java
private void drainCommands(World w) {
    spacecolony.sim.phases.CommandPhase.drain(w, commandQueue);
}
```

The `apply()` switch dispatch, all `apply*` helpers, `currentBodyOf`, and the inner exception class are deleted. Remove now-unused imports (the various `spacecolony.sim.commands.*` imports — keep only `Command`).

- [ ] **Step 3: Compile + run all tests**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL with 70 tests passing.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/CommandPhase.java src/main/java/spacecolony/sim/Simulator.java
git commit -m "refactor(sim): extract CommandPhase from Simulator"
```

---

### Task 3: Extract `TransitPhase`

**Files:**
- Create: `src/main/java/spacecolony/sim/phases/TransitPhase.java`
- Modify: `src/main/java/spacecolony/sim/Simulator.java`

Move `advanceTransits`, `loadingAndUnloading`, `computeArrivalTick`, `distanceBetweenSitesAtTicks`, and the `LOAD_RATE`/`FUEL_K` constants.

- [ ] **Step 1: Create the class**

```java
// src/main/java/spacecolony/sim/phases/TransitPhase.java
package spacecolony.sim.phases;

import java.util.EnumMap;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.Transit;
import spacecolony.sim.World;

public final class TransitPhase {
    private static final double LOAD_RATE = 25.0;
    private static final double FUEL_K = 0.5;

    private TransitPhase() {}

    // — paste the body of advanceTransits from Simulator unchanged —
    // — paste the body of loadingAndUnloading unchanged —
    // — paste computeArrivalTick + distanceBetweenSitesAtTicks unchanged —
    // (All methods become public static. Inner method bodies stay byte-identical.)

    // After copying: replace bare bodyPosition(...) calls with OrbitalGeometry.bodyPosition(...).
}
```

The copy is mechanical: take each method body, change `private void` → `public static void` (or `private static` for the helpers), and ensure the bodyPosition reference is `OrbitalGeometry.bodyPosition`.

- [ ] **Step 2: Update `Simulator.java`**

Replace the existing `advanceTransits`, `loadingAndUnloading`, `computeArrivalTick`, `distanceBetweenSitesAtTicks` methods with thin delegators:

```java
private void advanceTransits(World w) {
    spacecolony.sim.phases.TransitPhase.advanceTransits(w);
}

private void loadingAndUnloading(World w) {
    spacecolony.sim.phases.TransitPhase.loadingAndUnloading(w);
}
```

Delete `LOAD_RATE`, `FUEL_K`, `computeArrivalTick`, `distanceBetweenSitesAtTicks` from Simulator (they moved). Drop now-unused imports.

- [ ] **Step 3: Compile + run all tests**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: 70 PASSED.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/TransitPhase.java src/main/java/spacecolony/sim/Simulator.java
git commit -m "refactor(sim): extract TransitPhase from Simulator"
```

---

### Task 4: Extract `ProductionPhase`

**Files:**
- Create: `src/main/java/spacecolony/sim/phases/ProductionPhase.java`
- Modify: `src/main/java/spacecolony/sim/Simulator.java`

Move `productionAndConsumption`, `consume`, `produce`, `updateMorale`, `updatePopulation`, and the per-pop constants.

- [ ] **Step 1: Create the class**

```java
// src/main/java/spacecolony/sim/phases/ProductionPhase.java
package spacecolony.sim.phases;

import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceYieldSampler;
import spacecolony.sim.Site;
import spacecolony.sim.World;

public final class ProductionPhase {
    private static final double POP_FOOD_PER_DAY = 0.01;
    private static final double POP_WATER_PER_DAY = 0.005;

    private ProductionPhase() {}

    public static void run(World w) {
        // — paste body of productionAndConsumption verbatim —
        // (Replace bare sunDistance(...) with OrbitalGeometry.sunDistance(...).)
    }

    private static double consume(Site s, Resource r, double amount) {
        // — paste body unchanged —
    }
    /** Cache reflects gross output before stockpile cap clipping (which happens later). */
    private static void produce(Site s, Resource r, double amount) {
        // — paste body unchanged —
    }
    private static void updateMorale(Site s) {
        // — paste body unchanged —
    }
    private static void updatePopulation(Site s) {
        // — paste body unchanged —
    }
}
```

- [ ] **Step 2: Update `Simulator.java`**

Replace `productionAndConsumption` with a delegator and delete the rest:

```java
private void productionAndConsumption(World w) {
    spacecolony.sim.phases.ProductionPhase.run(w);
}
```

- [ ] **Step 3: Compile + test**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: 70 PASSED.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/ProductionPhase.java src/main/java/spacecolony/sim/Simulator.java
git commit -m "refactor(sim): extract ProductionPhase from Simulator"
```

---

### Task 5: Extract `EventPhase`, `ResearchPhase`, `GoalPhase`

**Files:**
- Create: `src/main/java/spacecolony/sim/phases/EventPhase.java`
- Create: `src/main/java/spacecolony/sim/phases/ResearchPhase.java`
- Create: `src/main/java/spacecolony/sim/phases/GoalPhase.java`
- Modify: `src/main/java/spacecolony/sim/Simulator.java`

Three small phases extracted together. Each is a single-method file.

- [ ] **Step 1: Create `EventPhase.java`**

```java
// src/main/java/spacecolony/sim/phases/EventPhase.java
package spacecolony.sim.phases;

import java.util.Random;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.DeterministicRng;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Site;
import spacecolony.sim.World;

public final class EventPhase {
    private static final double EVENT_BASE_RATE = 0.0008;
    private static final EventKind[] RANDOM_KINDS = {
        EventKind.METEOR_STRIKE, EventKind.SOLAR_FLARE,
        EventKind.EQUIPMENT_FAILURE, EventKind.DISEASE_OUTBREAK
    };

    private EventPhase() {}

    public static void run(World w) {
        // — paste body of randomEvents from Simulator verbatim —
        // (Reference RANDOM_KINDS instead of the inline kinds array.)
    }

    private static void applyEvent(World w, Body b, EventKind k, Random rng) {
        // — paste body of applyEvent unchanged —
    }
}
```

- [ ] **Step 2: Create `ResearchPhase.java`**

```java
// src/main/java/spacecolony/sim/phases/ResearchPhase.java
package spacecolony.sim.phases;

import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Site;
import spacecolony.sim.Tech;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.World;

public final class ResearchPhase {
    private ResearchPhase() {}

    public static void run(World w) {
        // — paste body of researchProgress from Simulator unchanged —
    }
}
```

- [ ] **Step 3: Create `GoalPhase.java`**

```java
// src/main/java/spacecolony/sim/phases/GoalPhase.java
package spacecolony.sim.phases;

import spacecolony.sim.Event;
import spacecolony.sim.EventKind;
import spacecolony.sim.EventSeverity;
import spacecolony.sim.Goal;
import spacecolony.sim.GoalCatalog;
import spacecolony.sim.World;

public final class GoalPhase {
    private GoalPhase() {}

    public static void run(World w) {
        // — paste body of goalCheck from Simulator unchanged —
    }
}
```

- [ ] **Step 4: Update `Simulator.java`**

Replace the three stubs with delegators:

```java
private void randomEvents(World w)     { spacecolony.sim.phases.EventPhase.run(w); }
private void researchProgress(World w) { spacecolony.sim.phases.ResearchPhase.run(w); }
private void goalCheck(World w)        { spacecolony.sim.phases.GoalPhase.run(w); }
```

Delete the `applyEvent` helper, the `EVENT_BASE_RATE` constant, and any now-unused imports.

- [ ] **Step 5: Compile + test**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: 70 PASSED.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/spacecolony/sim/phases/EventPhase.java \
        src/main/java/spacecolony/sim/phases/ResearchPhase.java \
        src/main/java/spacecolony/sim/phases/GoalPhase.java \
        src/main/java/spacecolony/sim/Simulator.java
git commit -m "refactor(sim): extract EventPhase, ResearchPhase, GoalPhase"
```

---

### Task 6: Slim `Simulator` to an orchestrator

**Files:**
- Modify: `src/main/java/spacecolony/sim/Simulator.java`

After Tasks 1-5, `Simulator.java` has thin delegator methods. Replace them with direct phase calls in `advance()`. The file should drop from ~486 LOC to ~50.

- [ ] **Step 1: Read the current `Simulator.java`**

```bash
wc -l /Users/steve/projects/space-colony/src/main/java/spacecolony/sim/Simulator.java
```

- [ ] **Step 2: Rewrite `Simulator.java` entirely**

Replace the file content with:

```java
package spacecolony.sim;

import java.util.ArrayDeque;
import java.util.Deque;
import spacecolony.sim.commands.Command;
import spacecolony.sim.phases.CommandPhase;
import spacecolony.sim.phases.EventPhase;
import spacecolony.sim.phases.GoalPhase;
import spacecolony.sim.phases.ProductionPhase;
import spacecolony.sim.phases.ResearchPhase;
import spacecolony.sim.phases.TransitPhase;

/**
 * Tick-by-tick simulation orchestrator. Each {@link #advance(World)} call runs the
 * 8 phases in order. Player intent enters via {@link #enqueue(Command)}; UI never
 * mutates World directly.
 *
 * Per-tick phases:
 *   1. Drain command queue                  (CommandPhase)
 *   2. Advance tick (here)
 *   3. Advance ships in transit (arrivals)  (TransitPhase.advanceTransits)
 *   4. Per-site production & consumption    (ProductionPhase)
 *   5. Loading/unloading ships (departures) (TransitPhase.loadingAndUnloading)
 *   6. Random events                        (EventPhase)
 *   7. Research progress                    (ResearchPhase)
 *   8. Goal check                           (GoalPhase)
 *
 * Single-threaded. {@code enqueue} is not thread-safe — call from the same thread
 * that calls {@link #advance}.
 */
public class Simulator {
    private final Deque<Command> commandQueue = new ArrayDeque<>();

    public void enqueue(Command c) { commandQueue.addLast(c); }

    public void advance(World w) {
        CommandPhase.drain(w, commandQueue);
        w.tick++;
        TransitPhase.advanceTransits(w);
        ProductionPhase.run(w);
        TransitPhase.loadingAndUnloading(w);
        EventPhase.run(w);
        ResearchPhase.run(w);
        GoalPhase.run(w);
    }
}
```

- [ ] **Step 3: Compile + test**

```bash
./gradlew test 2>&1 | tail -5
```

Expected: 70 PASSED. Byte-identical behavior, just reorganized.

- [ ] **Step 4: Verify file size dropped**

```bash
wc -l /Users/steve/projects/space-colony/src/main/java/spacecolony/sim/Simulator.java
```

Expected: ~50 LOC.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/sim/Simulator.java
git commit -m "refactor(sim): slim Simulator to an orchestrator (~50 LOC)"
```

---

### Task 7: Engine primitives — `Speed`, `Selection`, `EngineEvent`, `EngineListener`

**Files:**
- Create: `src/main/java/spacecolony/engine/Speed.java`
- Create: `src/main/java/spacecolony/engine/Selection.java`
- Create: `src/main/java/spacecolony/engine/EngineEvent.java`
- Create: `src/main/java/spacecolony/engine/EngineListener.java`
- Create: `src/test/java/spacecolony/engine/SelectionTest.java`

- [ ] **Step 1: Write `Speed.java`**

```java
package spacecolony.engine;

/** Tick rate for the game loop. millisPerTick determines the Swing Timer interval. */
public enum Speed {
    PAUSED(0),
    X1(500),
    X4(125),
    X16(32);

    private final int millisPerTick;
    Speed(int millisPerTick) { this.millisPerTick = millisPerTick; }
    public int millisPerTick() { return millisPerTick; }
    public boolean isPaused() { return this == PAUSED; }
}
```

- [ ] **Step 2: Write `Selection.java`**

```java
package spacecolony.engine;

/**
 * Current player selection. {@code id} identifies the selected entity within {@code type}'s
 * domain; {@code id} is null when {@code type} is NONE.
 */
public record Selection(Kind kind, String id) {
    public enum Kind { NONE, BODY, SITE, SHIP }

    public static final Selection NONE = new Selection(Kind.NONE, null);

    public static Selection body(String bodyId) { return new Selection(Kind.BODY, bodyId); }
    public static Selection site(String siteId) { return new Selection(Kind.SITE, siteId); }
    public static Selection ship(String shipId) { return new Selection(Kind.SHIP, shipId); }
}
```

- [ ] **Step 3: Write `EngineEvent.java`**

```java
package spacecolony.engine;

/** Sealed event hierarchy emitted by the Engine to UI listeners. */
public sealed interface EngineEvent
    permits EngineEvent.WorldChanged,
            EngineEvent.SelectionChanged,
            EngineEvent.SpeedChanged,
            EngineEvent.ViewChanged {

    /** Emitted after each successful tick advance. */
    record WorldChanged(long tick) implements EngineEvent {}

    record SelectionChanged(Selection selection) implements EngineEvent {}

    record SpeedChanged(Speed speed) implements EngineEvent {}

    /** Center-pane view changed (e.g., system map ↔ body view). */
    record ViewChanged(View view) implements EngineEvent {
        public enum View { SYSTEM_MAP, BODY_VIEW }
    }
}
```

- [ ] **Step 4: Write `EngineListener.java`**

```java
package spacecolony.engine;

/** Subscriber for engine events. UI panels implement this. */
@FunctionalInterface
public interface EngineListener {
    void onEvent(EngineEvent event);
}
```

- [ ] **Step 5: Write `SelectionTest.java`**

```java
package spacecolony.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SelectionTest {
    @Test
    void none_hasNullId() {
        assertEquals(Selection.Kind.NONE, Selection.NONE.kind());
        assertNull(Selection.NONE.id());
    }

    @Test
    void factories_setKindAndId() {
        assertEquals(Selection.Kind.BODY, Selection.body("earth").kind());
        assertEquals("earth", Selection.body("earth").id());
        assertEquals(Selection.Kind.SITE, Selection.site("site-1").kind());
        assertEquals(Selection.Kind.SHIP, Selection.ship("ship-1").kind());
    }
}
```

- [ ] **Step 6: Compile + test**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew test 2>&1 | tail -5
```

Expected: 72 tests passing (70 prior + 2 SelectionTest).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/spacecolony/engine/ src/test/java/spacecolony/engine/SelectionTest.java
git commit -m "feat(engine): add Speed, Selection, EngineEvent, EngineListener"
```

---

### Task 8: `Engine` class

**Files:**
- Create: `src/main/java/spacecolony/engine/Engine.java`
- Create: `src/test/java/spacecolony/engine/EngineTest.java`

Top-level engine wrapping World + Simulator + listener list + current selection + current view.

- [ ] **Step 1: Write the test first**

```java
// src/test/java/spacecolony/engine/EngineTest.java
package spacecolony.engine;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BuildingType;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class EngineTest {
    @Test
    void tick_advancesWorldAndFiresEvent() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        List<EngineEvent> received = new ArrayList<>();
        engine.addListener(received::add);
        long before = engine.world().tick;
        engine.tick();
        assertEquals(before + 1, engine.world().tick);
        assertTrue(received.stream().anyMatch(e -> e instanceof EngineEvent.WorldChanged));
    }

    @Test
    void enqueue_appliesCommandOnNextTick() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        int before = engine.world().findSite("site-earth-hub").buildings.size();
        engine.enqueue(new BuildBuildingCommand("site-earth-hub", BuildingType.RESEARCH_LAB));
        engine.tick();
        assertEquals(before + 1, engine.world().findSite("site-earth-hub").buildings.size());
    }

    @Test
    void selectionChange_firesEvent() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        List<EngineEvent> received = new ArrayList<>();
        engine.addListener(received::add);
        engine.setSelection(Selection.body("mars"));
        assertEquals(Selection.body("mars"), engine.selection());
        assertTrue(received.stream().anyMatch(e ->
            e instanceof EngineEvent.SelectionChanged sc && sc.selection().equals(Selection.body("mars"))));
    }

    @Test
    void initialSelectionIsNone() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        assertEquals(Selection.NONE, engine.selection());
    }

    @Test
    void initialSpeedIsPaused() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        assertEquals(Speed.PAUSED, engine.speed());
    }
}
```

- [ ] **Step 2: Run, expect compilation failure** (Engine doesn't exist)

```bash
./gradlew test --tests EngineTest 2>&1 | tail -5
```

- [ ] **Step 3: Implement `Engine.java`**

```java
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
    private final World world;
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

- [ ] **Step 4: Run, expect 5 PASSED**

```bash
./gradlew test --tests EngineTest 2>&1 | tail -10
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/engine/Engine.java src/test/java/spacecolony/engine/EngineTest.java
git commit -m "feat(engine): Engine wraps World+Simulator with listener and selection"
```

---

### Task 9: `GameLoop` (Swing Timer + speed control)

**Files:**
- Create: `src/main/java/spacecolony/engine/GameLoop.java`
- Create: `src/test/java/spacecolony/engine/GameLoopTest.java`

The GameLoop holds a `javax.swing.Timer` that fires tick events at the speed-controlled interval. The Engine wires `setSpeed` calls to reconfigure the timer.

- [ ] **Step 1: Write the test first**

```java
package spacecolony.engine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class GameLoopTest {

    @Test
    void paused_doesNotTick() throws Exception {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        GameLoop loop = new GameLoop(engine);
        engine.setSpeed(Speed.PAUSED);
        long before = engine.world().tick;
        Thread.sleep(150);
        assertEquals(before, engine.world().tick);
        loop.dispose();
    }

    @Test
    void x16_ticksWithinReasonableTime() throws Exception {
        // X16 = 32ms/tick; running 3 ticks should take ~96ms, certainly <500ms.
        Engine engine = new Engine(WorldGenerator.generate(1L));
        GameLoop loop = new GameLoop(engine);
        CountDownLatch threeTicks = new CountDownLatch(3);
        engine.addListener(e -> { if (e instanceof EngineEvent.WorldChanged) threeTicks.countDown(); });
        SwingUtilities.invokeAndWait(() -> engine.setSpeed(Speed.X16));
        assertTrue(threeTicks.await(2, TimeUnit.SECONDS), "Expected 3 ticks within 2s on X16");
        loop.dispose();
    }
}
```

- [ ] **Step 2: Implement `GameLoop.java`**

```java
package spacecolony.engine;

import javax.swing.Timer;

/**
 * Drives {@link Engine#tick()} from a Swing Timer at the current {@link Speed}'s interval.
 * Speed changes reconfigure the timer; PAUSED stops it. Must run on the EDT.
 */
public class GameLoop {
    private final Engine engine;
    private final Timer timer;

    public GameLoop(Engine engine) {
        this.engine = engine;
        // Start with a 1s placeholder; immediately reconfigured by applySpeed.
        this.timer = new Timer(1000, e -> engine.tick());
        timer.setRepeats(true);
        engine.addListener(this::onEvent);
        applySpeed(engine.speed());
    }

    private void onEvent(EngineEvent event) {
        if (event instanceof EngineEvent.SpeedChanged sc) applySpeed(sc.speed());
    }

    private void applySpeed(Speed s) {
        if (s.isPaused()) {
            timer.stop();
        } else {
            timer.setDelay(s.millisPerTick());
            timer.setInitialDelay(s.millisPerTick());
            if (!timer.isRunning()) timer.start();
        }
    }

    /** Stop the timer and detach from the engine. */
    public void dispose() {
        timer.stop();
        engine.removeListener(this::onEvent);
    }
}
```

Note: `engine.removeListener(this::onEvent)` won't match because each method reference creates a new instance. Replace the field-method-reference with an explicit field:

```java
private final EngineListener listener = this::onEvent;
```

then `engine.addListener(listener)` in the constructor and `engine.removeListener(listener)` in `dispose()`.

- [ ] **Step 3: Adjust Engine to lazy-deliver SpeedChanged events**

The GameLoop's constructor calls `applySpeed(engine.speed())` before the listener is registered, so it picks up the initial PAUSED state directly. No engine changes needed. But subsequent setSpeed calls must fire SpeedChanged for GameLoop to react — verify this is already the case in `Engine.setSpeed`. (It is — see Task 8.)

- [ ] **Step 4: Run, expect 2 PASSED**

```bash
./gradlew test --tests GameLoopTest 2>&1 | tail -10
```

If `x16_ticksWithinReasonableTime` is flaky on a slow CI box, bump the timeout to 5s. The test relies on Swing's EDT being responsive.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/engine/GameLoop.java src/test/java/spacecolony/engine/GameLoopTest.java
git commit -m "feat(engine): GameLoop drives Engine.tick via Swing Timer at speed-controlled intervals"
```

---

### Task 10: `UiColors` constants

**Files:**
- Create: `src/main/java/spacecolony/ui/UiColors.java`

Shared color constants so panels stay visually consistent.

- [ ] **Step 1: Write the file**

```java
package spacecolony.ui;

import java.awt.Color;

/** Shared UI palette. Plan 3 uses a dark theme; Plan 4 may add light alternates. */
public final class UiColors {
    private UiColors() {}

    public static final Color BACKGROUND        = new Color(10, 15, 28);
    public static final Color PANEL_BACKGROUND  = new Color(13, 20, 33);
    public static final Color PANEL_BORDER      = new Color(36, 48, 67);
    public static final Color FOREGROUND        = new Color(220, 226, 240);
    public static final Color FOREGROUND_DIM    = new Color(140, 150, 170);
    public static final Color SELECTION         = new Color(26, 46, 74);
    public static final Color WARNING           = new Color(240, 180, 90);
    public static final Color ERROR             = new Color(232, 90, 90);
    public static final Color INFO              = new Color(120, 180, 240);
    public static final Color ORBIT_LINE        = new Color(70, 90, 130, 80);
    public static final Color SHIP_DOT          = new Color(255, 230, 150);
    public static final Color SUN               = new Color(255, 220, 100);
    public static final Color STARFIELD_BG      = new Color(8, 10, 18);
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew compileJava 2>&1 | tail -3
git add src/main/java/spacecolony/ui/UiColors.java
git commit -m "feat(ui): add shared UiColors palette"
```

---

### Task 11: `SpaceColonyFrame` skeleton + `SpaceColonyApp` entry

**Files:**
- Create: `src/main/java/spacecolony/ui/SpaceColonyFrame.java`
- Create: `src/main/java/spacecolony/SpaceColonyApp.java`
- Modify: `build.gradle.kts` (add a `play` Gradle task that runs `SpaceColonyApp`)

Empty placeholder panels with labels so we can see the layout before wiring real content.

- [ ] **Step 1: Write `SpaceColonyFrame.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.GameLoop;

/** Top-level Swing window. Owns the engine + game loop and wires the 5-region layout. */
public class SpaceColonyFrame extends JFrame {
    private final Engine engine;
    private final GameLoop gameLoop;

    public SpaceColonyFrame(Engine engine) {
        super("Space Colony");
        this.engine = engine;
        this.gameLoop = new GameLoop(engine);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1280, 800));
        setLayout(new BorderLayout());
        getContentPane().setBackground(UiColors.BACKGROUND);

        add(placeholder("TopBar (Task 12)",        new Dimension(0, 40)),   BorderLayout.NORTH);
        add(placeholder("ColonyListPanel (Task 17)", new Dimension(220, 0)), BorderLayout.WEST);
        add(placeholder("MainViewPanel (Task 14)",  new Dimension(0, 0)),    BorderLayout.CENTER);
        add(placeholder("DetailPanel (Task 18)",   new Dimension(280, 0)),  BorderLayout.EAST);
        add(placeholder("EventStripPanel (Task 13)", new Dimension(0, 70)), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private static JPanel placeholder(String text, Dimension preferred) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiColors.PANEL_BACKGROUND);
        p.setBorder(BorderFactory.createLineBorder(UiColors.PANEL_BORDER));
        p.setPreferredSize(preferred);
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(UiColors.FOREGROUND_DIM);
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    public Engine engine() { return engine; }
    public GameLoop gameLoop() { return gameLoop; }
}
```

- [ ] **Step 2: Write `SpaceColonyApp.java`**

```java
package spacecolony;

import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.ui.SpaceColonyFrame;
import spacecolony.world.WorldGenerator;

/** Swing entry point. Use `./gradlew play --args="--seed N"` to launch. */
public class SpaceColonyApp {
    public static void main(String[] args) {
        long seed = 42L;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[i + 1]);
        }
        final long finalSeed = seed;
        SwingUtilities.invokeLater(() -> {
            Engine engine = new Engine(WorldGenerator.generate(finalSeed));
            SpaceColonyFrame frame = new SpaceColonyFrame(engine);
            frame.setVisible(true);
        });
    }
}
```

- [ ] **Step 3: Add the `play` Gradle task**

In `/Users/steve/projects/space-colony/build.gradle.kts`, after the `render-demo` task block, add:

```kotlin
tasks.register<JavaExec>("play") {
    group = "application"
    description = "Launch the Swing UI."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.SpaceColonyApp"
    standardInput = System.`in`
}
```

- [ ] **Step 4: Launch and visually verify the layout**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew play --args="--seed 42" 2>&1 | head -10
```

A 1280×800 dark window opens with 5 labeled regions ("TopBar", "ColonyListPanel", etc.). Close the window. If it doesn't appear, check `./gradlew --info play` for errors.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/ui/SpaceColonyFrame.java \
        src/main/java/spacecolony/SpaceColonyApp.java \
        build.gradle.kts
git commit -m "feat(ui): SpaceColonyFrame skeleton + SpaceColonyApp entry + play task"
```

---

### Task 12: `TopBar`

**Files:**
- Create: `src/main/java/spacecolony/ui/TopBar.java`
- Modify: `src/main/java/spacecolony/ui/SpaceColonyFrame.java`

Top bar shows: tick + in-game date, credits, speed buttons (⏸ 1× 4× 16×), and a "Tech" + "Goals" menu (wired in later tasks).

- [ ] **Step 1: Write `TopBar.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Speed;

public class TopBar extends JPanel {
    private final Engine engine;
    private final JLabel tickLabel = new JLabel();
    private final JLabel creditsLabel = new JLabel();
    private final JButton pauseBtn = speedButton("⏸", Speed.PAUSED);
    private final JButton x1Btn = speedButton("1×", Speed.X1);
    private final JButton x4Btn = speedButton("4×", Speed.X4);
    private final JButton x16Btn = speedButton("16×", Speed.X16);

    public TopBar(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.PANEL_BORDER));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        left.setOpaque(false);
        tickLabel.setForeground(UiColors.FOREGROUND);
        creditsLabel.setForeground(UiColors.FOREGROUND);
        left.add(tickLabel);
        left.add(creditsLabel);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        center.setOpaque(false);
        center.add(pauseBtn);
        center.add(x1Btn);
        center.add(x4Btn);
        center.add(x16Btn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        right.setOpaque(false);
        right.add(menuButton("Tech",  () -> { /* Task 26 wires this */ }));
        right.add(menuButton("Goals", () -> { /* Task 27 wires this */ }));

        add(left,   BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right,  BorderLayout.EAST);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.SpeedChanged) {
                refresh();
            }
        });
        refresh();
    }

    private JButton speedButton(String label, Speed s) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.addActionListener(e -> engine.setSpeed(s));
        return b;
    }

    private JButton menuButton(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void refresh() {
        long tick = engine.world().tick;
        // 365-day year: day Y of D
        long year = tick / 365;
        long day = tick % 365 + 1;
        tickLabel.setText(String.format("Tick %d  ·  Y%d D%d", tick, year, day));
        creditsLabel.setText("Credits: " + engine.world().credits);
        Speed sp = engine.speed();
        pauseBtn.setEnabled(sp != Speed.PAUSED);
        x1Btn.setEnabled(sp != Speed.X1);
        x4Btn.setEnabled(sp != Speed.X4);
        x16Btn.setEnabled(sp != Speed.X16);
    }
}
```

- [ ] **Step 2: Wire into `SpaceColonyFrame`**

In `SpaceColonyFrame.java`, replace the NORTH placeholder with the real TopBar:

```java
add(new TopBar(engine), BorderLayout.NORTH);
```

Remove the corresponding `placeholder("TopBar (Task 12)", ...)` line.

- [ ] **Step 3: Launch and verify**

```bash
./gradlew play --args="--seed 42"
```

- The top bar shows "Tick 0 · Y0 D1" and "Credits: 10000".
- Click `1×`. The tick counter starts increasing every ~500ms.
- Click `4×`. Tick rate visibly speeds up.
- Click `⏸`. Tick stops incrementing.

Close the window.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/TopBar.java src/main/java/spacecolony/ui/SpaceColonyFrame.java
git commit -m "feat(ui): TopBar with speed controls and tick/date/credits display"
```

---

### Task 13: `EventStripPanel`

**Files:**
- Create: `src/main/java/spacecolony/ui/EventStripPanel.java`
- Modify: `src/main/java/spacecolony/ui/SpaceColonyFrame.java`

Bottom strip showing the last ~10 events, color-coded by severity, scrolling as new events arrive.

- [ ] **Step 1: Write `EventStripPanel.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.sim.Event;
import spacecolony.sim.EventSeverity;

public class EventStripPanel extends JPanel {
    private static final int VISIBLE_EVENTS = 20;
    private final Engine engine;
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));

    public EventStripPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiColors.PANEL_BORDER));
        setPreferredSize(new Dimension(0, 110));

        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        add(scroll, BorderLayout.CENTER);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged) refresh();
        });
        refresh();
    }

    private void refresh() {
        list.removeAll();
        int shown = 0;
        // recentEvents is a Deque; iterate from newest backwards.
        var iter = engine.world().recentEvents.descendingIterator();
        while (iter.hasNext() && shown < VISIBLE_EVENTS) {
            Event ev = iter.next();
            JLabel label = new JLabel(String.format("[t=%d] %s: %s", ev.tick(), ev.kind(), ev.message()));
            label.setForeground(colorFor(ev.severity()));
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            list.add(label);
            shown++;
        }
        list.revalidate();
        list.repaint();
    }

    private static Color colorFor(EventSeverity s) {
        return switch (s) {
            case INFO    -> UiColors.INFO;
            case WARNING -> UiColors.WARNING;
            case ERROR   -> UiColors.ERROR;
        };
    }
}
```

- [ ] **Step 2: Wire into `SpaceColonyFrame`** — replace the SOUTH placeholder with `new EventStripPanel(engine)`.

- [ ] **Step 3: Launch and verify**

```bash
./gradlew play --args="--seed 42"
```

Click `16×` and let it run for a few seconds. Random events fire periodically; they appear in the bottom strip, color-coded (warnings in orange).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/EventStripPanel.java src/main/java/spacecolony/ui/SpaceColonyFrame.java
git commit -m "feat(ui): EventStripPanel shows recent events color-coded by severity"
```

---

### Task 14: `MainViewPanel` (CardLayout) + `SystemMapPanel` skeleton

**Files:**
- Create: `src/main/java/spacecolony/ui/MainViewPanel.java`
- Create: `src/main/java/spacecolony/ui/SystemMapPanel.java`
- Modify: `src/main/java/spacecolony/ui/SpaceColonyFrame.java`

Center pane is a `CardLayout` switching between system map and body view. Task 14 stands up just the system map painted with sun + orbits + bodies; ships and selection come in Task 15.

- [ ] **Step 1: Write `MainViewPanel.java`**

```java
package spacecolony.ui;

import java.awt.CardLayout;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;

/** CardLayout host that swaps between SystemMapPanel and BodyViewPanel. */
public class MainViewPanel extends JPanel {
    private static final String SYSTEM = "system";
    private static final String BODY   = "body";
    private final CardLayout cards = new CardLayout();
    private final SystemMapPanel systemMap;
    private final BodyViewPanel bodyView;

    public MainViewPanel(Engine engine) {
        setLayout(cards);
        setBackground(UiColors.BACKGROUND);
        this.systemMap = new SystemMapPanel(engine);
        this.bodyView = new BodyViewPanel(engine);
        add(systemMap, SYSTEM);
        add(bodyView, BODY);
        engine.addListener(e -> {
            if (e instanceof EngineEvent.ViewChanged vc) {
                cards.show(this, vc.view() == EngineEvent.ViewChanged.View.BODY_VIEW ? BODY : SYSTEM);
            }
        });
    }
}
```

(BodyViewPanel is a stub here — Task 20 fills it in. For Task 14 we only need it to compile; create a one-line stub.)

```java
// src/main/java/spacecolony/ui/BodyViewPanel.java — STUB, filled in Task 20
package spacecolony.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import spacecolony.engine.Engine;

public class BodyViewPanel extends JPanel {
    public BodyViewPanel(Engine engine) {
        add(new JLabel("BodyViewPanel — implemented in Task 20"));
    }
}
```

- [ ] **Step 2: Write `SystemMapPanel.java`** (skeleton — bodies only, no ships or selection yet)

```java
package spacecolony.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.sim.Body;
import spacecolony.sim.OrbitalGeometry;

public class SystemMapPanel extends JPanel {
    private final Engine engine;
    private double scale = 70.0; // pixels per AU at zoom = 1
    private double zoom = 1.0;
    private double offsetX = 0, offsetY = 0;

    public SystemMapPanel(Engine engine) {
        this.engine = engine;
        setBackground(UiColors.STARFIELD_BG);
        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged) repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                // Task 15 implements selection here.
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        // Sun
        g2.setColor(UiColors.SUN);
        g2.fillOval(cx - 6, cy - 6, 12, 12);

        // Orbits (faint circles for top-level bodies)
        g2.setColor(UiColors.ORBIT_LINE);
        for (Body b : engine.world().bodies) {
            if (b.orbit.parentBodyId() != null) continue; // skip moons here
            int r = (int) (b.orbit.semiMajorAxis() * scale * zoom);
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        }

        // Bodies
        for (Body b : engine.world().bodies) {
            double[] p = OrbitalGeometry.bodyPosition(engine.world(), b.id, engine.world().tick);
            int x = cx + (int) (p[0] * scale * zoom + offsetX);
            int y = cy + (int) (p[1] * scale * zoom + offsetY);
            g2.setColor(colorForBody(b));
            int radius = b.orbit.parentBodyId() == null ? 5 : 3;
            g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);

            // Label
            g2.setColor(UiColors.FOREGROUND_DIM);
            g2.drawString(b.name, x + radius + 3, y + 4);
        }

        g2.dispose();
    }

    private static Color colorForBody(Body b) {
        return switch (b.type) {
            case ROCKY     -> new Color(180, 130, 100);
            case GAS_GIANT -> new Color(220, 180, 130);
            case ICE_BODY  -> new Color(180, 220, 240);
            case ASTEROID  -> new Color(130, 120, 110);
            case MOON      -> new Color(170, 165, 160);
        };
    }
}
```

- [ ] **Step 3: Wire into `SpaceColonyFrame`** — replace the CENTER placeholder with `new MainViewPanel(engine)`.

- [ ] **Step 4: Launch and verify**

```bash
./gradlew play --args="--seed 42"
```

The center panel shows a sun at the middle, orbital circles, and 10 body markers labeled by name. Set speed to `16×` — over a few seconds, the inner planets visibly orbit.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/ui/MainViewPanel.java \
        src/main/java/spacecolony/ui/SystemMapPanel.java \
        src/main/java/spacecolony/ui/BodyViewPanel.java \
        src/main/java/spacecolony/ui/SpaceColonyFrame.java
git commit -m "feat(ui): SystemMapPanel renders sun + orbits + bodies"
```

---

### Task 15: `SystemMapPanel` — ships in transit + click-to-select

**Files:**
- Modify: `src/main/java/spacecolony/ui/SystemMapPanel.java`

Paint in-transit ships interpolated along their straight-line trajectory, and handle click selection.

- [ ] **Step 1: Add ship rendering to `paintComponent`**

Inside the existing `paintComponent`, after the bodies loop, add:

```java
// In-transit ships: interpolate along straight line origin → destination.
for (var ship : engine.world().ships) {
    if (ship.state != spacecolony.sim.ShipState.IN_TRANSIT) continue;
    var t = ship.transit;
    double[] op = OrbitalGeometry.bodyPosition(engine.world(),
        engine.world().findSite(t.originSiteId()).bodyId, t.departureTick());
    double[] dp = OrbitalGeometry.bodyPosition(engine.world(),
        engine.world().findSite(t.destSiteId()).bodyId, t.arrivalTick());
    long now = engine.world().tick;
    double progress = (double)(now - t.departureTick()) / Math.max(1, t.arrivalTick() - t.departureTick());
    progress = Math.max(0, Math.min(1, progress));
    double sx = op[0] + (dp[0] - op[0]) * progress;
    double sy = op[1] + (dp[1] - op[1]) * progress;
    int x = cx + (int) (sx * scale * zoom + offsetX);
    int y = cy + (int) (sy * scale * zoom + offsetY);
    g2.setColor(UiColors.SHIP_DOT);
    g2.fillRect(x - 2, y - 2, 4, 4);
}
```

- [ ] **Step 2: Implement click-to-select**

Replace the placeholder `mousePressed` handler with:

```java
@Override public void mousePressed(MouseEvent e) {
    int cxLocal = getWidth() / 2;
    int cyLocal = getHeight() / 2;
    // Find the nearest body within 12 pixels of the click.
    Body best = null;
    double bestDist = 12.0;
    for (Body b : engine.world().bodies) {
        double[] p = OrbitalGeometry.bodyPosition(engine.world(), b.id, engine.world().tick);
        int x = cxLocal + (int) (p[0] * scale * zoom + offsetX);
        int y = cyLocal + (int) (p[1] * scale * zoom + offsetY);
        double d = Math.hypot(x - e.getX(), y - e.getY());
        if (d < bestDist) { bestDist = d; best = b; }
    }
    if (best != null) {
        engine.setSelection(spacecolony.engine.Selection.body(best.id));
    } else {
        engine.setSelection(spacecolony.engine.Selection.NONE);
    }
}
```

- [ ] **Step 3: Highlight the selected body**

In the bodies loop in `paintComponent`, before drawing each body dot, check whether it's selected and, if so, draw a yellow outline:

```java
var sel = engine.selection();
boolean selected = sel.kind() == spacecolony.engine.Selection.Kind.BODY && b.id.equals(sel.id());
if (selected) {
    g2.setColor(new Color(255, 240, 120));
    g2.drawOval(x - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6);
}
```

Also add a listener for SelectionChanged so the panel repaints on selection:

```java
engine.addListener(e -> {
    if (e instanceof EngineEvent.SelectionChanged || e instanceof EngineEvent.WorldChanged) {
        repaint();
    }
});
```

(Replace the existing WorldChanged-only listener with this combined one.)

- [ ] **Step 4: Launch and verify**

```bash
./gradlew play --args="--seed 42"
```

- Click on Earth's dot: a yellow ring appears around it.
- Click empty space: ring disappears (selection becomes NONE).
- Click Mars while paused: selection moves to Mars.

Ships won't be visible yet (no dispatch UI), but the rendering code is in place for Task 24's first dispatch.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/ui/SystemMapPanel.java
git commit -m "feat(ui): SystemMapPanel renders in-transit ships and handles click selection"
```

---

### Task 16: `ColonyListPanel`

**Files:**
- Create: `src/main/java/spacecolony/ui/ColonyListPanel.java`
- Modify: `src/main/java/spacecolony/ui/SpaceColonyFrame.java`

Left dock: scrollable list of all sites and ships, color-coded for warnings. Clicking an entry selects it.

- [ ] **Step 1: Write `ColonyListPanel.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.Site;

public class ColonyListPanel extends JPanel {
    private final Engine engine;
    private final JPanel list = new JPanel(new GridLayout(0, 1, 0, 1));

    public ColonyListPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.PANEL_BORDER));
        list.setOpaque(false);
        JScrollPane scroll = new JScrollPane(list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        add(scroll, BorderLayout.CENTER);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.SelectionChanged) refresh();
        });
        refresh();
    }

    private void refresh() {
        list.removeAll();
        addHeader("Colonies");
        for (var body : engine.world().bodies) {
            for (Site s : body.sites) {
                boolean shortFood = s.stockpile.getOrDefault(Resource.FOOD, 0.0) < 1e-6;
                Color fg = shortFood ? UiColors.WARNING : UiColors.FOREGROUND;
                list.add(row(s.name + "  (" + body.name + ")", Selection.site(s.id), fg));
            }
        }
        addHeader("Ships (" + engine.world().ships.size() + ")");
        for (Ship s : engine.world().ships) {
            list.add(row(s.name + "  " + stateGlyph(s), Selection.ship(s.id), UiColors.FOREGROUND));
        }
        list.revalidate();
        list.repaint();
    }

    private void addHeader(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(UiColors.FOREGROUND_DIM);
        l.setBorder(BorderFactory.createEmptyBorder(8, 8, 2, 8));
        list.add(l);
    }

    private Component row(String text, Selection sel, Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setOpaque(true);
        l.setBackground(sel.equals(engine.selection()) ? UiColors.SELECTION : UiColors.PANEL_BACKGROUND);
        l.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { engine.setSelection(sel); }
        });
        return l;
    }

    private static String stateGlyph(Ship s) {
        return switch (s.state) {
            case IDLE        -> "·";
            case LOADING     -> "▾";
            case IN_TRANSIT  -> "→";
            case UNLOADING   -> "▴";
        };
    }
}
```

- [ ] **Step 2: Wire into `SpaceColonyFrame`** — replace the WEST placeholder with `new ColonyListPanel(engine)`.

- [ ] **Step 3: Launch and verify**

- The left dock shows "Colonies" with "Earth Hub (Earth)" and "Ships (0)".
- Click "Earth Hub": the row highlights and the system map highlights Earth.
- (Site detail will populate in Task 18.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/ColonyListPanel.java src/main/java/spacecolony/ui/SpaceColonyFrame.java
git commit -m "feat(ui): ColonyListPanel lists sites + ships with click selection"
```

---

### Task 17: `SphereMiniRenderer` (cached small render for the dock)

**Files:**
- Create: `src/main/java/spacecolony/ui/SphereMiniRenderer.java`
- Create: `src/test/java/spacecolony/ui/PanelSmokeTest.java`

A small `JPanel` (~200×200) that shows a rotating sphere for a given Body. Caches the flat map per body (generating one takes ~270ms so we cannot regenerate per repaint). The sphere itself is re-rendered each tick using the cached flat map.

- [ ] **Step 1: Write `SphereMiniRenderer.java`**

```java
package spacecolony.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.render.BodyAppearance;
import spacecolony.render.BodyAppearances;
import spacecolony.render.PlanetGenerator;
import spacecolony.render.SphereRenderer;
import spacecolony.sim.Body;

/** Small (~200px) sphere render for the right dock. Flat maps are cached per body id. */
public class SphereMiniRenderer extends JPanel {
    private static final int SIZE = 200;
    private static final int FLAT_W = 512;
    private static final int FLAT_H = 256;

    private final Engine engine;
    private final Map<String, BufferedImage> flatCache = new HashMap<>();
    private Body body;

    public SphereMiniRenderer(Engine engine) {
        this.engine = engine;
        setOpaque(false);
        setPreferredSize(new Dimension(SIZE, SIZE));
        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged) repaint();
        });
    }

    /** Switch which body this renderer shows. Pass null to clear. */
    public void setBody(Body body) {
        this.body = body;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (body == null) return;
        BufferedImage flat = flatCache.computeIfAbsent(body.id, id -> {
            BodyAppearance app = BodyAppearances.defaultFor(body.type);
            return new PlanetGenerator(FLAT_W, FLAT_H).generate(body.surfaceSeed, app);
        });
        BodyAppearance app = BodyAppearances.defaultFor(body.type);
        // Rotation advances slowly with tick: 1 full rotation per 360 ticks.
        double rotation = (engine.world().tick % 360) * 1.0;
        BufferedImage sphere = SphereRenderer.render(flat, SIZE, rotation, 12.0, 1.0,
            body.surfaceSeed, app.atmosphereColor());
        g.drawImage(sphere, 0, 0, null);
    }
}
```

- [ ] **Step 2: Write a smoke test that paints each UI panel to verify no NPE**

```java
// src/test/java/spacecolony/ui/PanelSmokeTest.java
package spacecolony.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class PanelSmokeTest {
    @Test
    void sphereMiniRenderer_paintsWithoutCrashing() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        SphereMiniRenderer panel = new SphereMiniRenderer(engine);
        panel.setBody(engine.world().findBody("earth"));
        panel.setSize(200, 200);
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        assertDoesNotThrow(() -> panel.paint(g));
        g.dispose();
    }
}
```

- [ ] **Step 3: Run, expect 1 PASSED**

```bash
./gradlew test --tests PanelSmokeTest 2>&1 | tail -8
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/SphereMiniRenderer.java src/test/java/spacecolony/ui/PanelSmokeTest.java
git commit -m "feat(ui): SphereMiniRenderer with per-body flat-map cache + smoke test"
```

---

### Task 18: `DetailPanel`

**Files:**
- Create: `src/main/java/spacecolony/ui/DetailPanel.java`
- Modify: `src/main/java/spacecolony/ui/SpaceColonyFrame.java`

Right dock that shows context-appropriate content based on `engine.selection()`. Body selection shows the SphereMiniRenderer + body stats + "Open body view" button; site selection shows stockpile/buildings/morale + action buttons; ship selection shows class/state/cargo + dispatch button.

- [ ] **Step 1: Write `DetailPanel.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.Building;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.Site;

public class DetailPanel extends JPanel {
    private final Engine engine;
    private final JPanel content = new JPanel();
    private final SphereMiniRenderer miniRenderer;

    public DetailPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UiColors.PANEL_BORDER));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane scroll = new JScrollPane(content,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        add(scroll, BorderLayout.CENTER);
        this.miniRenderer = new SphereMiniRenderer(engine);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.SelectionChanged || e instanceof EngineEvent.WorldChanged) refresh();
        });
        refresh();
    }

    private void refresh() {
        content.removeAll();
        Selection sel = engine.selection();
        switch (sel.kind()) {
            case BODY -> renderBody(engine.world().findBody(sel.id()));
            case SITE -> renderSite(engine.world().findSite(sel.id()));
            case SHIP -> renderShip(engine.world().findShip(sel.id()));
            case NONE -> renderNone();
        }
        content.revalidate();
        content.repaint();
    }

    private void renderNone() {
        addLabel("(No selection)", UiColors.FOREGROUND_DIM);
    }

    private void renderBody(Body b) {
        if (b == null) { renderNone(); return; }
        addLabel(b.name, UiColors.FOREGROUND);
        addLabel(b.type.name() + "  ·  " + b.sites.size() + " site(s)", UiColors.FOREGROUND_DIM);
        miniRenderer.setBody(b);
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        wrap.setOpaque(false);
        wrap.add(miniRenderer);
        content.add(wrap);
        JButton open = new JButton("Open body view");
        open.addActionListener(e -> {
            engine.setSelection(Selection.body(b.id));
            engine.setView(EngineEvent.ViewChanged.View.BODY_VIEW);
        });
        content.add(open);
        // Plan-2 BodyAppearances.mars() is wired here for the Mars body — left to Plan 3+ polish.
    }

    private void renderSite(Site s) {
        if (s == null) { renderNone(); return; }
        addLabel(s.name, UiColors.FOREGROUND);
        addLabel("Body: " + s.bodyId + "  ·  pop " + s.population + "/" + s.populationCap, UiColors.FOREGROUND_DIM);
        addLabel(String.format("Morale: %.2f", s.morale), UiColors.FOREGROUND_DIM);
        content.add(Box.createVerticalStrut(6));
        addLabel("Stockpile:", UiColors.FOREGROUND_DIM);
        JPanel stockGrid = new JPanel(new GridLayout(0, 2, 6, 2));
        stockGrid.setOpaque(false);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            stockGrid.add(rowLabel(r.name(), UiColors.FOREGROUND_DIM));
            stockGrid.add(rowLabel(String.format("%.0f", s.stockpile.getOrDefault(r, 0.0)), UiColors.FOREGROUND));
        }
        content.add(stockGrid);
        content.add(Box.createVerticalStrut(6));
        addLabel("Buildings:", UiColors.FOREGROUND_DIM);
        for (Building b : s.buildings) {
            String enabled = b.enabled ? "" : "  (disabled)";
            addLabel("  " + b.type + " L" + b.level + enabled,
                b.enabled ? UiColors.FOREGROUND : UiColors.WARNING);
        }
        content.add(Box.createVerticalStrut(8));
        JButton build = new JButton("Build building...");
        build.addActionListener(e -> spacecolony.ui.dialogs.BuildBuildingDialog.show(this, engine, s.id));
        content.add(build);
        boolean hasShipyard = s.buildings.stream().anyMatch(b -> b.type == spacecolony.sim.BuildingType.SHIPYARD && b.enabled);
        if (hasShipyard) {
            JButton ship = new JButton("Build ship...");
            ship.addActionListener(e -> spacecolony.ui.dialogs.BuildShipDialog.show(this, engine, s.id));
            content.add(ship);
        }
    }

    private void renderShip(Ship s) {
        if (s == null) { renderNone(); return; }
        addLabel(s.name + "  (" + s.shipClass + ")", UiColors.FOREGROUND);
        addLabel("State: " + s.state, UiColors.FOREGROUND_DIM);
        if (s.currentSiteId != null) addLabel("At: " + s.currentSiteId, UiColors.FOREGROUND_DIM);
        if (s.transit != null && s.state == spacecolony.sim.ShipState.IN_TRANSIT) {
            addLabel("→ " + s.transit.destSiteId() + " (arrival t=" + s.transit.arrivalTick() + ")", UiColors.FOREGROUND_DIM);
        }
        addLabel(String.format("Fuel: %.1f", s.fuel), UiColors.FOREGROUND_DIM);
        if (s.cargoMass() > 0) {
            addLabel("Cargo:", UiColors.FOREGROUND_DIM);
            for (Resource r : Resource.values()) {
                double v = s.cargo.getOrDefault(r, 0.0);
                if (v > 1e-6) addLabel("  " + r + ": " + String.format("%.0f", v), UiColors.FOREGROUND);
            }
        }
        if (s.state == spacecolony.sim.ShipState.IDLE) {
            JButton dispatch = new JButton("Dispatch...");
            dispatch.addActionListener(e -> spacecolony.ui.dialogs.DispatchShipDialog.show(this, engine, s.id));
            content.add(dispatch);
        }
    }

    private void addLabel(String text, java.awt.Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        l.setAlignmentX(LEFT_ALIGNMENT);
        content.add(l);
    }

    private JLabel rowLabel(String text, java.awt.Color fg) {
        JLabel l = new JLabel(text);
        l.setForeground(fg);
        return l;
    }
}
```

Note: `DetailPanel` references `BuildBuildingDialog`, `BuildShipDialog`, and `DispatchShipDialog`. Create stub classes now so the panel compiles; they get filled in Tasks 22-24.

- [ ] **Step 2: Create stub dialog classes**

```java
// src/main/java/spacecolony/ui/dialogs/BuildBuildingDialog.java — STUB, filled in Task 22
package spacecolony.ui.dialogs;

import java.awt.Component;
import javax.swing.JOptionPane;
import spacecolony.engine.Engine;

public class BuildBuildingDialog {
    public static void show(Component parent, Engine engine, String siteId) {
        JOptionPane.showMessageDialog(parent, "BuildBuildingDialog — Task 22");
    }
}
```

```java
// src/main/java/spacecolony/ui/dialogs/BuildShipDialog.java — STUB, filled in Task 23
package spacecolony.ui.dialogs;

import java.awt.Component;
import javax.swing.JOptionPane;
import spacecolony.engine.Engine;

public class BuildShipDialog {
    public static void show(Component parent, Engine engine, String siteId) {
        JOptionPane.showMessageDialog(parent, "BuildShipDialog — Task 23");
    }
}
```

```java
// src/main/java/spacecolony/ui/dialogs/DispatchShipDialog.java — STUB, filled in Task 24
package spacecolony.ui.dialogs;

import java.awt.Component;
import javax.swing.JOptionPane;
import spacecolony.engine.Engine;

public class DispatchShipDialog {
    public static void show(Component parent, Engine engine, String shipId) {
        JOptionPane.showMessageDialog(parent, "DispatchShipDialog — Task 24");
    }
}
```

- [ ] **Step 3: Wire into `SpaceColonyFrame`** — replace the EAST placeholder with `new DetailPanel(engine)`.

- [ ] **Step 4: Launch and verify**

- Click Earth in the system map: right dock shows "Earth", body type, site count, and a small rotating sphere.
- Click "Earth Hub" in the colony list: right dock shows population, morale, stockpile bars, building list, and "Build building..." button.
- Click "Build building...": stub dialog box appears.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/ui/DetailPanel.java \
        src/main/java/spacecolony/ui/dialogs/BuildBuildingDialog.java \
        src/main/java/spacecolony/ui/dialogs/BuildShipDialog.java \
        src/main/java/spacecolony/ui/dialogs/DispatchShipDialog.java \
        src/main/java/spacecolony/ui/SpaceColonyFrame.java
git commit -m "feat(ui): DetailPanel with selection-driven content + dialog stubs"
```

---

### Task 19: Smoke tests for the major panels

**Files:**
- Modify: `src/test/java/spacecolony/ui/PanelSmokeTest.java`

Add construct-and-paint smoke tests for each panel built so far. These catch NPE/init bugs without requiring a real display.

- [ ] **Step 1: Extend `PanelSmokeTest`**

```java
// Add these methods to the existing PanelSmokeTest class.

@Test
void topBar_paintsWithoutCrashing() {
    Engine engine = new Engine(WorldGenerator.generate(1L));
    TopBar p = new TopBar(engine);
    p.setSize(800, 40);
    paintToImage(p, 800, 40);
}

@Test
void colonyListPanel_paintsWithoutCrashing() {
    Engine engine = new Engine(WorldGenerator.generate(1L));
    ColonyListPanel p = new ColonyListPanel(engine);
    p.setSize(220, 600);
    paintToImage(p, 220, 600);
}

@Test
void systemMapPanel_paintsWithoutCrashing() {
    Engine engine = new Engine(WorldGenerator.generate(1L));
    SystemMapPanel p = new SystemMapPanel(engine);
    p.setSize(800, 600);
    paintToImage(p, 800, 600);
}

@Test
void detailPanel_paintsWithoutCrashing() {
    Engine engine = new Engine(WorldGenerator.generate(1L));
    DetailPanel p = new DetailPanel(engine);
    p.setSize(280, 600);
    paintToImage(p, 280, 600);
    engine.setSelection(spacecolony.engine.Selection.body("earth"));
    paintToImage(p, 280, 600);
    engine.setSelection(spacecolony.engine.Selection.site("site-earth-hub"));
    paintToImage(p, 280, 600);
}

@Test
void eventStripPanel_paintsWithoutCrashing() {
    Engine engine = new Engine(WorldGenerator.generate(1L));
    EventStripPanel p = new EventStripPanel(engine);
    p.setSize(800, 110);
    paintToImage(p, 800, 110);
}

private static void paintToImage(javax.swing.JPanel panel, int w, int h) {
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics g = img.createGraphics();
    try {
        panel.paint(g);
    } finally {
        g.dispose();
    }
}
```

- [ ] **Step 2: Run, expect all PASSED**

```bash
./gradlew test --tests PanelSmokeTest 2>&1 | tail -10
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/spacecolony/ui/PanelSmokeTest.java
git commit -m "test(ui): smoke tests for all major panels via paint-to-image"
```

---

### Task 20: `BodyViewPanel` — full-screen sphere

**Files:**
- Modify: `src/main/java/spacecolony/ui/BodyViewPanel.java`

Replace the Task 14 stub with the real BodyViewPanel: renders the selected body as a large sphere (~500×500) with a "Back to system map" button.

- [ ] **Step 1: Rewrite `BodyViewPanel.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.render.BodyAppearance;
import spacecolony.render.BodyAppearances;
import spacecolony.render.PlanetGenerator;
import spacecolony.render.SphereRenderer;
import spacecolony.sim.Body;

public class BodyViewPanel extends JPanel {
    private static final int FLAT_W = 1024;
    private static final int FLAT_H = 512;

    private final Engine engine;
    private final SpherePanel sphere;
    private final Map<String, BufferedImage> flatCache = new HashMap<>();

    public BodyViewPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.STARFIELD_BG);
        this.sphere = new SpherePanel();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        JButton back = new JButton("← Back to system map");
        back.addActionListener(e -> engine.setView(EngineEvent.ViewChanged.View.SYSTEM_MAP));
        top.add(back);

        add(top, BorderLayout.NORTH);
        add(sphere, BorderLayout.CENTER);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.SelectionChanged
             || e instanceof EngineEvent.ViewChanged) sphere.repaint();
        });
    }

    private Body currentBody() {
        Selection sel = engine.selection();
        if (sel.kind() != Selection.Kind.BODY) return null;
        return engine.world().findBody(sel.id());
    }

    private BufferedImage flatMap(Body b) {
        return flatCache.computeIfAbsent(b.id, id ->
            new PlanetGenerator(FLAT_W, FLAT_H).generate(b.surfaceSeed, BodyAppearances.defaultFor(b.type)));
    }

    private class SpherePanel extends JPanel {
        SpherePanel() { setOpaque(false); setPreferredSize(new Dimension(600, 600)); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Body b = currentBody();
            if (b == null) return;
            int size = Math.min(getWidth(), getHeight()) - 40;
            if (size < 64) return;
            BodyAppearance app = BodyAppearances.defaultFor(b.type);
            double rotation = (engine.world().tick % 360) * 1.0;
            BufferedImage sphereImg = SphereRenderer.render(flatMap(b), size, rotation, 12.0, 1.0,
                b.surfaceSeed, app.atmosphereColor());
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g.drawImage(sphereImg, x, y, null);
        }
    }
}
```

- [ ] **Step 2: Launch and verify**

- Select a body in the system map.
- Click "Open body view" in the right dock.
- A large rotating sphere of that body fills the center.
- Click "← Back to system map" — back to the system map.
- Try this for each body type — gas giants look banded, asteroids look gray, ice bodies look icy-blue.

- [ ] **Step 3: Add a smoke test**

```java
// Add to PanelSmokeTest:
@Test
void bodyViewPanel_paintsWithoutCrashing() {
    Engine engine = new Engine(WorldGenerator.generate(1L));
    BodyViewPanel p = new BodyViewPanel(engine);
    p.setSize(600, 600);
    paintToImage(p, 600, 600);
    engine.setSelection(spacecolony.engine.Selection.body("earth"));
    paintToImage(p, 600, 600);
}
```

```bash
./gradlew test --tests PanelSmokeTest 2>&1 | tail -8
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/BodyViewPanel.java src/test/java/spacecolony/ui/PanelSmokeTest.java
git commit -m "feat(ui): BodyViewPanel renders selected body as full-screen rotating sphere"
```

---

### Task 21: `BodyViewPanel` — click-to-place-site via unproject

**Files:**
- Modify: `src/main/java/spacecolony/ui/BodyViewPanel.java`
- Create: `src/main/java/spacecolony/ui/dialogs/PlaceSiteDialog.java`

Clicking on the sphere's surface (when a COLONIZER ship is present at the body) opens a PlaceSiteDialog with the resolved lat/lon prefilled.

- [ ] **Step 1: Write `PlaceSiteDialog.java`**

```java
package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipClass;
import spacecolony.sim.commands.BuildSiteCommand;

public class PlaceSiteDialog {
    /** lat and lon in radians. */
    public static void show(Component parent, Engine engine, String bodyId, double lat, double lon) {
        // Find a colonizer at this body, if any.
        Ship colonizer = null;
        for (Ship s : engine.world().ships) {
            if (s.shipClass == ShipClass.COLONIZER && s.currentSiteId != null) {
                var site = engine.world().findSite(s.currentSiteId);
                if (site != null && site.bodyId.equals(bodyId)) { colonizer = s; break; }
            }
        }
        if (colonizer == null) {
            JOptionPane.showMessageDialog(parent,
                "No COLONIZER ship at this body. Build one and dispatch it here first.",
                "Cannot place site", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextField id = new JTextField("site-" + bodyId + "-" + (engine.world().tick % 10000));
        JTextField name = new JTextField(bodyId + " outpost");
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Site ID:"));    form.add(id);
        form.add(new JLabel("Name:"));       form.add(name);
        form.add(new JLabel("Latitude:"));   form.add(new JLabel(String.format("%.3f rad", lat)));
        form.add(new JLabel("Longitude:"));  form.add(new JLabel(String.format("%.3f rad", lon)));
        int result = JOptionPane.showConfirmDialog(parent, form, "Place site on " + bodyId,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        engine.enqueue(new BuildSiteCommand(id.getText(), name.getText(), bodyId, lat, lon, colonizer.id));
    }
}
```

- [ ] **Step 2: Add the mouse handler in `BodyViewPanel`'s `SpherePanel`**

In the constructor of the inner `SpherePanel`, add a mouse listener:

```java
addMouseListener(new java.awt.event.MouseAdapter() {
    @Override public void mousePressed(java.awt.event.MouseEvent e) {
        Body b = currentBody();
        if (b == null) return;
        int size = Math.min(getWidth(), getHeight()) - 40;
        int x0 = (getWidth() - size) / 2;
        int y0 = (getHeight() - size) / 2;
        int px = e.getX() - x0;
        int py = e.getY() - y0;
        if (px < 0 || py < 0 || px >= size || py >= size) return;
        double rotation = (engine.world().tick % 360) * 1.0;
        double[] latLon = spacecolony.render.SphereRenderer.unproject(px, py, size, rotation, 12.0, 1.0);
        if (latLon == null) return;
        spacecolony.ui.dialogs.PlaceSiteDialog.show(BodyViewPanel.this, engine, b.id, latLon[0], latLon[1]);
    }
});
```

- [ ] **Step 3: Launch and verify**

- Set seed `--seed 1`, open the Earth body view, click on the sphere — a warning appears ("No COLONIZER ship at this body") because the default world has no colonizers.
- For end-to-end Earth verification: in the next task we add BuildShipDialog. Or: use a temporary debug hook to seed a colonizer.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/BodyViewPanel.java \
        src/main/java/spacecolony/ui/dialogs/PlaceSiteDialog.java
git commit -m "feat(ui): BodyViewPanel click → unproject → PlaceSiteDialog"
```

---

### Task 22: `BuildBuildingDialog`

**Files:**
- Modify: `src/main/java/spacecolony/ui/dialogs/BuildBuildingDialog.java`

Replace the stub with a real dialog that lets the player pick a BuildingType for the selected site.

- [ ] **Step 1: Rewrite `BuildBuildingDialog.java`**

```java
package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.sim.BuildingType;
import spacecolony.sim.commands.BuildBuildingCommand;

public class BuildBuildingDialog {
    public static void show(Component parent, Engine engine, String siteId) {
        JComboBox<BuildingType> combo = new JComboBox<>(BuildingType.values());
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Building:"));
        form.add(combo);
        int result = JOptionPane.showConfirmDialog(parent, form,
            "Build at " + siteId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        BuildingType picked = (BuildingType) combo.getSelectedItem();
        engine.enqueue(new BuildBuildingCommand(siteId, picked));
    }
}
```

- [ ] **Step 2: Launch and verify**

- Select Earth Hub site.
- Click "Build building...".
- Pick RESEARCH_LAB, click OK.
- Within ~1 tick, the building list in the detail panel adds "RESEARCH_LAB L1".

- [ ] **Step 3: Commit**

```bash
git add src/main/java/spacecolony/ui/dialogs/BuildBuildingDialog.java
git commit -m "feat(ui): BuildBuildingDialog enqueues BuildBuildingCommand for the selected site"
```

---

### Task 23: `BuildShipDialog`

**Files:**
- Modify: `src/main/java/spacecolony/ui/dialogs/BuildShipDialog.java`

- [ ] **Step 1: Rewrite `BuildShipDialog.java`**

```java
package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.ShipClass;
import spacecolony.sim.commands.BuildShipCommand;

public class BuildShipDialog {
    public static void show(Component parent, Engine engine, String shipyardSiteId) {
        JTextField id = new JTextField("ship-" + (engine.world().ships.size() + 1));
        JTextField name = new JTextField("Hauler " + (engine.world().ships.size() + 1));
        JComboBox<ShipClass> combo = new JComboBox<>(ShipClass.values());
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Ship ID:"));   form.add(id);
        form.add(new JLabel("Name:"));      form.add(name);
        form.add(new JLabel("Class:"));     form.add(combo);
        int result = JOptionPane.showConfirmDialog(parent, form,
            "Build ship at " + shipyardSiteId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        engine.enqueue(new BuildShipCommand(id.getText(), name.getText(),
            (ShipClass) combo.getSelectedItem(), shipyardSiteId));
    }
}
```

- [ ] **Step 2: Verify** — select Earth Hub, click "Build ship...", create a HAULER. After the next tick the ship appears in the colony list with state `·` (IDLE).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/spacecolony/ui/dialogs/BuildShipDialog.java
git commit -m "feat(ui): BuildShipDialog enqueues BuildShipCommand at the chosen shipyard"
```

---

### Task 24: `DispatchShipDialog`

**Files:**
- Modify: `src/main/java/spacecolony/ui/dialogs/DispatchShipDialog.java`

The most complex action dialog: pick a destination site + manifest (resource → amount).

- [ ] **Step 1: Rewrite `DispatchShipDialog.java`**

```java
package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import spacecolony.engine.Engine;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.commands.DispatchShipCommand;

public class DispatchShipDialog {
    public static void show(Component parent, Engine engine, String shipId) {
        // Collect all site IDs.
        List<String> siteIds = new ArrayList<>();
        for (var b : engine.world().bodies)
            for (Site s : b.sites)
                siteIds.add(s.id);
        if (siteIds.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No destination sites exist yet.");
            return;
        }
        JComboBox<String> dest = new JComboBox<>(siteIds.toArray(new String[0]));
        // One text field per resource (blank = 0).
        Map<Resource, JTextField> fields = new EnumMap<>(Resource.class);
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 4));
        form.add(new JLabel("Destination:"));
        form.add(dest);
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            JTextField f = new JTextField("0");
            fields.put(r, f);
            form.add(new JLabel(r.name() + ":"));
            form.add(f);
        }
        int result = JOptionPane.showConfirmDialog(parent, form,
            "Dispatch " + shipId, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        Map<Resource, Double> manifest = new EnumMap<>(Resource.class);
        for (var entry : fields.entrySet()) {
            String raw = entry.getValue().getText().trim();
            if (raw.isEmpty()) continue;
            try {
                double v = Double.parseDouble(raw);
                if (v > 0) manifest.put(entry.getKey(), v);
            } catch (NumberFormatException ignored) { /* skip bad input */ }
        }
        engine.enqueue(new DispatchShipCommand(shipId, (String) dest.getSelectedItem(), manifest));
    }
}
```

- [ ] **Step 2: Launch and verify**

- Build a hauler at Earth Hub (Task 23).
- Once it's IDLE, select it in the colony list.
- Click "Dispatch..." in the right dock.
- Pick Earth Hub itself as the destination (trivial round-trip), enter "10" for METAL, click OK.
- After a tick, the ship's state shows LOADING; after manifest fills, it shows IN_TRANSIT and a yellow dot appears on the system map moving back to Earth.
- (For a real long-distance dispatch you need a second site — Task 21's PlaceSiteDialog gives you that path.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/spacecolony/ui/dialogs/DispatchShipDialog.java
git commit -m "feat(ui): DispatchShipDialog with destination picker and per-resource manifest"
```

---

### Task 25: `TechModal`

**Files:**
- Create: `src/main/java/spacecolony/ui/TechModal.java`
- Modify: `src/main/java/spacecolony/ui/TopBar.java`

Simple modal listing all techs from `TechCatalog`. Researched ones are dimmed; the active one is highlighted; clicking an unresearched one queues it.

- [ ] **Step 1: Write `TechModal.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.Tech;
import spacecolony.sim.TechCatalog;
import spacecolony.sim.commands.QueueResearchCommand;

public class TechModal {
    public static void show(Component owner, Engine engine) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner), "Tech Tree", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        for (Tech t : TechCatalog.all()) {
            boolean done = engine.world().tech.researched.contains(t.id());
            boolean active = t.id().equals(engine.world().tech.activeId);
            JLabel row = new JLabel(String.format("%s — cost %d  %s", t.name(), t.researchCost(),
                done ? "✓" : active ? "(active, " + (int) engine.world().tech.accumulatedPoints + " pts)" : ""));
            row.setOpaque(true);
            Color bg = active ? UiColors.SELECTION : UiColors.PANEL_BACKGROUND;
            Color fg = done ? UiColors.FOREGROUND_DIM : UiColors.FOREGROUND;
            row.setBackground(bg);
            row.setForeground(fg);
            row.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            if (!done && !active) {
                row.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        engine.enqueue(new QueueResearchCommand(t.id()));
                        dlg.dispose();
                    }
                });
            }
            list.add(row);
        }
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel();
        south.add(close);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setSize(520, 520);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }
}
```

- [ ] **Step 2: Wire the TopBar "Tech" button**

In `TopBar.java`, replace the empty Runnable with:

```java
right.add(menuButton("Tech",  () -> spacecolony.ui.TechModal.show(this, engine)));
```

- [ ] **Step 3: Launch and verify**

- Click "Tech" in the top bar. The modal opens listing 17 techs.
- Click `basic-mining` — the dialog closes and the tech becomes active.
- Wait for the (slow) research to complete (you'll see RESEARCH_COMPLETED in the event strip eventually if you have research labs).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/TechModal.java src/main/java/spacecolony/ui/TopBar.java
git commit -m "feat(ui): TechModal lists catalog + queues research via QueueResearchCommand"
```

---

### Task 26: `GoalsModal`

**Files:**
- Create: `src/main/java/spacecolony/ui/GoalsModal.java`
- Modify: `src/main/java/spacecolony/ui/TopBar.java`

Modal listing all goals from `GoalCatalog`, marking achieved ones.

- [ ] **Step 1: Write `GoalsModal.java`**

```java
package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.Goal;
import spacecolony.sim.GoalCatalog;

public class GoalsModal {
    public static void show(Component owner, Engine engine) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(owner), "Goals", JDialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout());
        JPanel list = new JPanel(new GridLayout(0, 1, 0, 2));
        list.setBackground(UiColors.PANEL_BACKGROUND);
        for (Goal g : GoalCatalog.all()) {
            boolean done = engine.world().goals.achieved.contains(g.id());
            String text = String.format("%s %s  —  +%d credits  +%d research",
                done ? "✓" : "○", g.name(), g.creditReward(), g.researchReward());
            JLabel row = new JLabel(text);
            row.setOpaque(true);
            row.setBackground(UiColors.PANEL_BACKGROUND);
            Color fg = done ? new Color(120, 200, 130) : UiColors.FOREGROUND;
            row.setForeground(fg);
            row.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            list.add(row);
            JLabel desc = new JLabel("    " + g.description());
            desc.setForeground(UiColors.FOREGROUND_DIM);
            desc.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
            list.add(desc);
        }
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        JPanel south = new JPanel();
        south.add(close);
        dlg.add(south, BorderLayout.SOUTH);
        dlg.setSize(500, 480);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }
}
```

- [ ] **Step 2: Wire the TopBar "Goals" button** — replace the empty Runnable with:

```java
right.add(menuButton("Goals", () -> spacecolony.ui.GoalsModal.show(this, engine)));
```

- [ ] **Step 3: Launch and verify**

- Click "Goals" in the top bar. 8 goals listed, all with `○` (unachieved). Descriptions visible.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/ui/GoalsModal.java src/main/java/spacecolony/ui/TopBar.java
git commit -m "feat(ui): GoalsModal lists catalog with achieved-state and rewards"
```

---

### Task 27: Run the full test suite + smoke tests

**Files:** None (verification only).

- [ ] **Step 1: Run all tests**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew test 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL. Test count = 70 (Plan 1-2) + 2 (SelectionTest) + 5 (EngineTest) + 2 (GameLoopTest) + 7 (PanelSmokeTest) = ~86 tests. Allow for ±1 if a test got combined.

- [ ] **Step 2: Run the headless sim driver** (regression for Plan 1)

```bash
./gradlew run --args="--seed 1 --ticks 1000" 2>&1 | tail -10
```

Expected: prints sim summary. No errors. Determinism intact.

- [ ] **Step 3: Run the renderer demo** (regression for Plan 2)

```bash
./gradlew render-demo --args="--seed 7 --out /tmp/sc-render" 2>&1 | tail -3
```

Expected: 10 PNGs written.

- [ ] **Step 4: Commit a marker (if anything changed)**

No commit usually. If any test required adjustment, commit those changes.

---

### Task 28: Manual play-test checklist

**Files:** None (manual UI verification).

- [ ] **Launch the app** with `./gradlew play --args="--seed 1"`.

- [ ] **Smoke**: window opens. 5 regions visible. Top bar shows Tick 0, Y0 D1, Credits 10000.

- [ ] **Speed controls**: pause / 1× / 4× / 16× cycle through; tick counter visibly accelerates.

- [ ] **System map**: sun at center, 10 bodies labeled, orbits drawn. Bodies move when unpaused.

- [ ] **Selection**: click any body → ring appears around it; right dock shows body name + small rotating sphere + "Open body view" button.

- [ ] **Colony list**: "Earth Hub (Earth)" visible. Click it → right dock shows pop/morale/stockpile/buildings/Build building button.

- [ ] **Build building**: click "Build building...", pick RESEARCH_LAB, OK. Within one tick, building list adds RESEARCH_LAB L1.

- [ ] **Build ship**: in Earth Hub detail, click "Build ship...", build a HAULER. Within one tick, ship appears in colony list as `Hauler 1 ·`.

- [ ] **Body view**: select Earth, click "Open body view". Full sphere fills the center, slowly rotating. Click "Back to system map" returns.

- [ ] **Click-to-place-site (rejection path)**: in Earth body view, click on the sphere surface → "No COLONIZER ship at this body" warning appears.

- [ ] **Dispatch**: select the Hauler, click "Dispatch...". Pick "site-earth-hub" as destination (self), enter METAL=10, OK. Ship state cycles LOADING → IN_TRANSIT → UNLOADING → IDLE; a yellow dot is visible on the system map during transit.

- [ ] **Random events**: at 16× speed, after a minute or two, events appear in the bottom strip (METEOR_STRIKE / SOLAR_FLARE / EQUIPMENT_FAILURE / DISEASE_OUTBREAK), color-coded as warnings.

- [ ] **Tech modal**: click "Tech" in top bar. 17 techs listed. Click `basic-mining`. The modal closes and `(active, 0 pts)` appears next to it the next time you open the modal.

- [ ] **Goals modal**: click "Goals" in top bar. 8 goals listed, all `○`. Close.

- [ ] **Close the window** with the X button — no exceptions logged.

If every box checks: Plan 3 is complete. If any item fails, capture the symptom and either fix in a follow-up commit or open an issue.

---

## Verification

End-state for Plan 3:

1. `./gradlew test` is BUILD SUCCESSFUL with ~86 tests passing.
2. `./gradlew play --args="--seed 1"` launches a playable game window.
3. `./gradlew run` (headless sim) still works — no regression.
4. `./gradlew render-demo` still works — no regression.
5. `Simulator.java` is ≤ ~60 LOC; per-phase logic lives in `spacecolony.sim.phases.*`.
6. Every UI panel has a smoke test that paints it to a BufferedImage without throwing.

## What's Next

Plan 4 adds:
- Save/load (JSON) with named slots + auto-save on quit
- Debug mode (overlay, object inspector, log viewer, sim controls)
- Tech-effect multipliers wired into production
- Richer Tech/Goals panels (graph layout, progress bars)
- Surface yield overlays in BodyView (debug-mode-only)

## Self-Review Notes

Fixes applied during the post-write self-review:

1. **`Engine.removeListener` with method-reference bug.** `engine.removeListener(this::onEvent)` doesn't work because each method-reference call creates a new instance — they don't `equals` each other. Task 9 was updated to stash the listener in a final field so removal works.

2. **CardLayout swap on ViewChanged.** `MainViewPanel` subscribes to `ViewChanged` events; Task 14 added the listener inside the panel rather than wiring through frame-level state.

3. **DetailPanel references three dialog classes that don't exist when Task 18 lands.** Stubs are created in Task 18 so the panel compiles; Tasks 22-24 replace them with real dialogs. Stubs use `JOptionPane.showMessageDialog` with the task number so manual testers know what's missing.

4. **Body view sphere size depends on panel size; minimum 64 px guarded** so a tiny window doesn't pass a zero-size to the renderer.

5. **Random events visible from launch**: the spec phase ordering means events emit during `EventPhase` (phase 6), so the bottom strip shows them as they happen. No need for separate event surfacing logic.

6. **`BodyAppearances.mars()` is still not auto-applied** (Plan 2 holistic-review follow-up). The DetailPanel comment in Task 18 marks it; a small follow-up could add `BodyAppearances.forBodyId(String id, BodyType t)` that branches on `id.equals("mars")` to call `mars()`. Deferred to Plan 4 since Plan 3 visuals are still distinguishable.

7. **No engine thread-safety guards.** Single-threaded EDT is the documented contract; the Engine class Javadoc says so. Plan 4 may add an `assertEDT()` check inside `enqueue`/`tick` for sanity.

## Spec Coverage

Plan 3 implements the following spec sections:

- §4.3 Threading (single-threaded EDT) — Task 8, 9 (Engine + GameLoop both EDT-only)
- §7.1 SystemMapPanel — Tasks 14-15
- §7.2 BodyViewPanel + unproject usage — Tasks 20-21
- §7.3 DetailPanel — Tasks 17-18
- §7.4 TechPanel + GoalsPanel (basic versions) — Tasks 25-26
- §7.5 Per-frame data flow (Engine event → listener repaints) — Tasks 8, 12-16
- §7.6 Command flow (UI → enqueue → drain → emit events) — Tasks 22-24
- §11.4 Light UI tests (smoke tests via paint-to-image) — Tasks 17, 19, 20
- §12 Build & run (`./gradlew play`) — Task 11

Deferred per the brainstorm:

- §8 Debug mode → Plan 4
- §9 Save / load → Plan 4
- §11.2 Save round-trip tests → Plan 4
- Tech-effect multipliers wired into production (§6 step 7) → Plan 4
