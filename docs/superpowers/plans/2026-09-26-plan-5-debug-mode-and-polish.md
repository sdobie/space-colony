# Space Colony — Plan 5: Debug Mode, Save Slots + Autosave, Panel Polish

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the v1 scope of the game-design spec (§14). Add the debug mode from spec §8 (overlay, sim controls, object inspector, log viewer, map overlays, determinism check, logging, crash handling). Replace the raw file chooser with named save slots plus autosave on quit (spec §9). Finish the panel polish that PR #5 started: pop-cap breakdown, production rates, per-building tech multipliers, tech tiers with research rate and ETA, goal categories and progress bars, event filters and click-to-select. Also enforce tech prerequisites in the sim.

**Architecture:** One new package, `spacecolony.debug`, which depends on `engine`, `sim`, `save` and `world` and never on `ui`. A few small sim helpers (`TechAvailability`, `PopCapBreakdown`, `SimPhase` + `PhaseObserver`, `TransitPhase.fuelCost`, `ResearchPhase.pointsPerTick`) give the UI and debug tools one source of truth for numbers the phases already compute. `Engine` gains debug-mode state, stepping, multi-tick advance and a single audited `applyDebugEdit` mutation path. `save` gains `SaveSlots` and string-level `SaveFile.toJson/fromJson`. `ui` gains `GameSession` (current slot + quit/autosave) and `SaveSlotDialog`.

**Tech Stack:** Java 25, Gradle 9.0.0 via wrapper, JUnit 5, Swing, `java.util.logging`. No new dependencies.

**Spec reference:** `/Users/steve/projects/space-colony/docs/superpowers/specs/2026-09-26-plan-5-debug-mode-and-polish-design.md` (sections 1–9).

**Out of scope:** save thumbnails, timed autosave, graph-drawn tech tree, `distZip`, Java toolchain changes. See the design doc §1.

---

## Context

Plans 1–4 are on `main`: 8-phase sim, deterministic world-gen, Swing shell with 5 panels, `GameLoop` on a Swing `Timer`, save/load (schema v1), all 17 techs wired through `TechEffects`, HABITAT cap, and EDT guards. PR #5 (merged 2026-09-26) then did part of the panel polish: per-tech effect deltas (`TechEffects.deltas`), morale shown against its ceiling, locked techs blocked in the UI, a goals summary header, and game dates in the event strip. There are 183 tests, all passing.

Key facts about the existing code that this plan relies on:

- `Simulator.advance` calls `CommandPhase.drain`, `w.tick++`, `TransitPhase.advanceTransits`, `ProductionPhase.run`, `TransitPhase.loadingAndUnloading`, `EventPhase.run`, `ResearchPhase.run` and `GoalPhase.run`, in that order.
- `CommandPhase.applyQueueResearch` checks only "unknown" and "already researched". **It does not check prerequisites.** Since PR #5 the tech modal blocks locked techs, but the sim still accepts them. Task 1 fixes the sim and Task 25 makes the modal share the same rule.
- `ProductionPhase.recomputeCap(Site, TechState)` is private and holds the HABITAT cap formula.
- `TransitPhase.loadingAndUnloading` computes the departure fuel cost inline from the private `distanceBetweenSitesAtTicks`.
- `EventPhase.applyForTest(World, Body, EventKind, Random)` is public and used by `EventTechEffectsTest`.
- `SaveFile.save(World, Path)` / `load(Path)` build and parse a `JsonValue` tree via `JsonWriter.write` / `JsonReader.parse`.
- `Engine` has `tick()`, `reset(World)`, `setSpeed`, `setSelection`, `setView`. `EngineEvent` is sealed with `WorldChanged`, `WorldReplaced`, `SelectionChanged`, `SpeedChanged` and `ViewChanged`. Every listener uses `instanceof`, so there are no exhaustive switches to update when a variant is added.
- `SpaceColonyFrame` uses `EXIT_ON_CLOSE`. `FileMenu.QuitAction` calls `System.exit(0)`.
- `BodyViewPanel`'s sphere click opens `PlaceSiteDialog`. There are no clickable site markers.
- The game has no keyboard shortcuts yet.
- Starting world (`WorldGenerator.generate`): Earth Hub, pop 100, buildings HABITAT, FARM, MINE, POWER_PLANT, SHIPYARD (all L1), and no RESEARCH_LAB.
- Tests hop onto the EDT with `spacecolony.testutil.Edt.run(...)`.

---

## File Structure

### Create

```
src/main/java/spacecolony/
├── sim/
│   ├── TechAvailability.java
│   ├── GoalCategory.java
│   ├── PopCapBreakdown.java
│   ├── SimPhase.java
│   └── PhaseObserver.java
├── save/
│   ├── SaveSlots.java
│   └── SlotInfo.java
├── debug/                                   (NEW PACKAGE)
│   ├── DebugLogging.java
│   ├── RingBufferHandler.java
│   ├── ExceptionLog.java
│   ├── CrashHandler.java
│   ├── PhaseTimings.java
│   ├── DeterminismCheck.java
│   ├── ObjectTreeModel.java
│   ├── YieldSummary.java
│   ├── DebugActions.java
│   ├── DebugController.java
│   ├── DebugOverlayPanel.java
│   ├── DebugMenu.java
│   ├── TriggerEventDialog.java
│   ├── ObjectInspectorDialog.java
│   └── LogViewerDialog.java
└── ui/
    ├── GameSession.java
    └── SaveSlotDialog.java

src/test/java/spacecolony/
├── sim/
│   ├── TechAvailabilityTest.java
│   ├── GoalProgressTest.java
│   ├── PopCapBreakdownTest.java
│   ├── TransitFuelCostTest.java
│   └── SimulatorPhaseObserverTest.java
├── engine/
│   ├── EngineDebugTest.java
│   └── EngineLoggingTest.java
├── save/
│   ├── SaveFileJsonTest.java
│   └── SaveSlotsTest.java
├── debug/
│   ├── RingBufferHandlerTest.java
│   ├── DebugLoggingTest.java
│   ├── CrashHandlerTest.java
│   ├── PhaseTimingsTest.java
│   ├── DeterminismCheckTest.java
│   ├── ObjectTreeModelTest.java
│   └── YieldSummaryTest.java
└── ui/
    ├── DebugControllerTest.java      (builds SpaceColonyFrame, so it lives with ui tests)
    └── GameSessionTest.java
```

### Modify

- `sim/Goal.java`, `sim/GoalCatalog.java`: category + progress.
- `sim/phases/CommandPhase.java`: prereq check.
- `sim/phases/ProductionPhase.java`: `recomputeCap` delegates to `PopCapBreakdown`.
- `sim/phases/TransitPhase.java`: public `fuelCost`.
- `sim/phases/EventPhase.java`: `applyForTest` → `applyForced`.
- `sim/phases/ResearchPhase.java`: public `pointsPerTick`.
- `sim/Simulator.java`: phase observer, `queueDepth()`.
- `save/SaveFile.java`: `toJson` / `fromJson`.
- `engine/Engine.java`, `engine/EngineEvent.java`: debug flag, `step`, `advanceSilently`, `applyDebugEdit`, tick rate, logging.
- `SpaceColonyApp.java`: `--debug`, `--log-level`, logging and crash-handler install.
- `ui/SpaceColonyFrame.java`: `GameSession`, window close, `DebugController`, SOUTH stack.
- `ui/FileMenu.java`: slot-based menu.
- `ui/TopBar.java`: save toast.
- `ui/DetailPanel.java`, `ui/TechModal.java`, `ui/GoalsModal.java`, `ui/EventStripPanel.java`: the polish PR #5 didn't cover (Tasks 24–27 build on its code rather than replacing it).
- `test/.../ui/PanelFormattingTest.java`: new formatting cases.
- `ui/SystemMapPanel.java`, `ui/ColonyListPanel.java`, `ui/BodyViewPanel.java`: Shift+click inspector and map overlays.
- `test/.../sim/EventTechEffectsTest.java`: renamed seam.
- `test/.../ui/PanelSmokeTest.java`, `test/.../playtest/PlayTestDriver.java`: new menu and dialogs.

---

## Tasks

Tasks 1–8 are sim/engine/save groundwork (headless, test-first). Tasks 9–11 add logging and crash handling. Tasks 12–19 build debug mode. Tasks 20–23 add save slots and autosave. Tasks 24–27 are panel polish. Task 28 is verification and the PR.

Every task ends with `./gradlew test` green and a commit. Commit messages follow the existing `feat(<pkg>): …` / `test(<pkg>): …` / `fix(<pkg>): …` style.

### Task 0: Branch

- [ ] **Step 1: Create the branch from latest `main`**

```bash
cd /Users/steve/projects/space-colony
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
git checkout main && git pull
git checkout -b plan-5/debug-mode-and-polish
./gradlew test
```

Expected: 183 tests pass.

---

### Task 1: Research prerequisites (`TechAvailability` + `CommandPhase` fix)

**Files:**
- Create: `src/main/java/spacecolony/sim/TechAvailability.java`
- Create: `src/test/java/spacecolony/sim/TechAvailabilityTest.java`
- Modify: `src/main/java/spacecolony/sim/phases/CommandPhase.java`
- Modify: `src/test/java/spacecolony/sim/CommandTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package spacecolony.sim;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TechAvailabilityTest {
    @Test
    void rootTechs_areTierZero_andAvailable() {
        TechState s = new TechState();
        Tech ion = TechCatalog.get("ion-drives");
        assertEquals(0, TechAvailability.tier(ion));
        assertTrue(TechAvailability.prereqsMet(s, ion));
    }

    @Test
    void antimatter_isTierTwo() {
        assertEquals(1, TechAvailability.tier(TechCatalog.get("fusion-drives")));
        assertEquals(2, TechAvailability.tier(TechCatalog.get("antimatter")));
    }

    @Test
    void missingPrereqs_listsUnresearched() {
        TechState s = new TechState();
        Tech fusion = TechCatalog.get("fusion-drives");
        assertFalse(TechAvailability.prereqsMet(s, fusion));
        assertEquals(List.of("ion-drives"), TechAvailability.missingPrereqs(s, fusion));
        s.researched.add("ion-drives");
        assertTrue(TechAvailability.prereqsMet(s, fusion));
        assertEquals(List.of(), TechAvailability.missingPrereqs(s, fusion));
    }
}
```

Append to `CommandTest`:

```java
    @Test
    void queueResearch_missingPrereq_isRejected() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("fusion-drives"));
        sim.advance(w);
        assertNull(w.tech.activeId);
        assertTrue(w.recentEvents.stream().anyMatch(e ->
            e.kind() == EventKind.COMMAND_REJECTED && e.message().contains("Ion Drives")));
    }

    @Test
    void queueResearch_prereqMet_isAccepted() {
        World w = WorldGenerator.generate(1L);
        w.tech.researched.add("ion-drives");
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("fusion-drives"));
        sim.advance(w);
        assertEquals("fusion-drives", w.tech.activeId);
    }
```

- [ ] **Step 2: Run them and watch them fail**

`./gradlew test --tests 'spacecolony.sim.TechAvailabilityTest' --tests 'spacecolony.sim.CommandTest'`. Expected: the first fails to compile. After a stub, `queueResearch_missingPrereq_isRejected` fails.

- [ ] **Step 3: Implement `TechAvailability`**

```java
package spacecolony.sim;

import java.util.ArrayList;
import java.util.List;

/** Prerequisite checks shared by CommandPhase (validation) and the tech modal (display). */
public final class TechAvailability {
    private TechAvailability() {}

    public static boolean prereqsMet(TechState s, Tech t) {
        return s.researched.containsAll(t.prereqIds());
    }

    /** Unresearched prereq ids, in the order the tech declares them. */
    public static List<String> missingPrereqs(TechState s, Tech t) {
        List<String> out = new ArrayList<>();
        for (String id : t.prereqIds()) if (!s.researched.contains(id)) out.add(id);
        return out;
    }

    /** Longest prereq chain below {@code t}: 0 for techs with no prereqs. */
    public static int tier(Tech t) {
        int best = 0;
        for (String id : t.prereqIds()) best = Math.max(best, 1 + tier(TechCatalog.get(id)));
        return best;
    }
}
```

- [ ] **Step 4: Enforce in `CommandPhase.applyQueueResearch`**

Replace the method body's first line with a lookup and add the prereq check after the "already researched" check:

```java
    private static void applyQueueResearch(World w, QueueResearchCommand qr) {
        Tech t = TechCatalog.get(qr.techId());
        if (t == null) throw new CommandRejectedException("Unknown tech: " + qr.techId());
        if (w.tech.researched.contains(qr.techId())) throw new CommandRejectedException("Already researched: " + qr.techId());
        List<String> missing = TechAvailability.missingPrereqs(w.tech, t);
        if (!missing.isEmpty()) {
            List<String> names = missing.stream().map(id -> TechCatalog.get(id).name()).toList();
            throw new CommandRejectedException("Missing prerequisites for " + t.name() + ": " + String.join(", ", names));
        }
        // Preserve any accumulated points from goal rewards or a previously queued tech
        // (a player switching research mid-stream gets to carry their progress forward).
        w.tech.activeId = qr.techId();
    }
```

Add imports for `Tech`, `TechAvailability` and `java.util.List`.

- [ ] **Step 5: Run the full suite, then commit**

Some existing tests may queue a tech with prereqs on a fresh world. Grep `QueueResearchCommand(` in `src/test`. Any test that queues `fusion-drives`, `hydroponics`, `research-ii`, `auto-mining`, `colony-mgmt-ii`, `life-support-ii` or `antimatter` must first add the prereq to `w.tech.researched`. That's a test fixture fix, not a behaviour change.

```bash
./gradlew test
git add -A && git commit -m "fix(sim): reject research whose prerequisites are not researched"
```

---

### Task 2: Goal categories and progress

**Files:**
- Create: `src/main/java/spacecolony/sim/GoalCategory.java`
- Modify: `src/main/java/spacecolony/sim/Goal.java`, `src/main/java/spacecolony/sim/GoalCatalog.java`
- Create: `src/test/java/spacecolony/sim/GoalProgressTest.java`

- [ ] **Step 1: Failing test**

```java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class GoalProgressTest {
    private static double p(World w, String id) { return GoalCatalog.get(id).displayProgress(w); }

    @Test
    void freshWorld_progressValues() {
        World w = WorldGenerator.generate(1L);          // Earth Hub, pop 100, no ships
        assertEquals(0.10, p(w, "pop-1000"), 1e-9);
        assertEquals(0.01, p(w, "pop-10000"), 1e-9);
        assertEquals(0.0,  p(w, "fleet-10"), 1e-9);
        assertEquals(0.2,  p(w, "five-bodies"), 1e-9);
        assertEquals(0.0,  p(w, "first-mars-colony"), 1e-9);
    }

    @Test
    void achievedGoal_displaysFull_evenIfMeasureDrops() {
        World w = WorldGenerator.generate(1L);
        w.goals.achieved.add("pop-1000");
        assertEquals(1.0, p(w, "pop-1000"), 1e-9);
    }

    @Test
    void progress_isClamped() {
        World w = WorldGenerator.generate(1L);
        w.findSite("site-earth-hub").population = 50_000;
        assertEquals(1.0, p(w, "pop-10000"), 1e-9);
    }

    @Test
    void everyGoal_hasCategory() {
        for (Goal g : GoalCatalog.all()) assertNotNull(g.category(), g.id());
    }
}
```

- [ ] **Step 2: Implement**

```java
package spacecolony.sim;
public enum GoalCategory { EXPANSION, POPULATION, FLEET }
```

`Goal` gets two new components after `description`, plus a display helper:

```java
public record Goal(
    String id,
    String name,
    String description,
    GoalCategory category,
    long creditReward,
    long researchReward,
    Predicate<World> predicate,
    /** Raw measure toward the goal in [0, 1] (clamped by displayProgress). Display only. */
    ToDoubleFunction<World> progress
) {
    /** 1.0 once achieved; otherwise the live measure clamped to [0, 1]. */
    public double displayProgress(World w) {
        if (w.goals.achieved.contains(id)) return 1.0;
        return Math.max(0.0, Math.min(1.0, progress.applyAsDouble(w)));
    }
}
```

In `GoalCatalog`, factor the repeated stream expressions into private static helpers so predicate and progress share them:

```java
    private static int totalPop(World w) {
        return w.bodies.stream().flatMap(b -> b.sites.stream()).mapToInt(s -> s.population).sum();
    }
    private static long settledBodies(World w) {
        return w.bodies.stream().filter(b -> !b.sites.isEmpty()).count();
    }
    private static ToDoubleFunction<World> binary(Predicate<World> p) {
        return w -> p.test(w) ? 1.0 : 0.0;
    }
```

Each `add(new Goal(...))` passes its category and progress, following design §3.2:

- EXPANSION goals use `binary(pred)`, except `five-bodies`, which uses `w -> settledBodies(w) / 5.0`.
- `pop-1000` uses `w -> totalPop(w) / 1000.0` and `pop-10000` uses `/ 10000.0`.
- `fleet-10` uses `w -> w.ships.size() / 10.0`.

For the binary goals, declare the predicate once in a local variable so it isn't duplicated.

- [ ] **Step 3: Fix call sites**

`GoalsModal` doesn't construct `Goal`s, so no change is needed there (Task 26 uses the new fields). Grep `new Goal(` in `src/test` and update any fixtures.

- [ ] **Step 4: Test and commit**

```bash
./gradlew test
git add -A && git commit -m "feat(sim): goal categories and display progress"
```

---

### Task 3: `PopCapBreakdown`

**Files:**
- Create: `src/main/java/spacecolony/sim/PopCapBreakdown.java`
- Modify: `src/main/java/spacecolony/sim/phases/ProductionPhase.java`
- Create: `src/test/java/spacecolony/sim/PopCapBreakdownTest.java`

- [ ] **Step 1: Failing test (Plan 4 design §5.2 sample table)**

```java
package spacecolony.sim;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PopCapBreakdownTest {
    private static Site site(int base, int... habitatLevels) {
        Site s = new Site("s", "S", "earth", 0, 0, base);
        for (int l : habitatLevels) s.buildings.add(new Building(BuildingType.HABITAT, l));
        return s;
    }
    private static TechState tech(String... ids) {
        TechState t = new TechState();
        for (String id : ids) t.researched.add(id);
        return t;
    }

    @Test void l1_noTech()        { assertEquals(300,  PopCapBreakdown.of(site(200, 1), tech()).cap()); }
    @Test void l1l1_noTech()      { assertEquals(400,  PopCapBreakdown.of(site(200, 1, 1), tech()).cap()); }
    @Test void l1_mgmtI()         { assertEquals(360,  PopCapBreakdown.of(site(200, 1), tech("colony-mgmt-i")).cap()); }
    @Test void l1_bothMgmt()      { assertEquals(468,  PopCapBreakdown.of(site(200, 1), tech("colony-mgmt-i", "colony-mgmt-ii")).cap()); }
    @Test void l1l2l1_bothMgmt()  { assertEquals(1248, PopCapBreakdown.of(site(200, 1, 2, 1), tech("colony-mgmt-i", "colony-mgmt-ii")).cap()); }
    @Test void colonizer_l1l2l1() { assertEquals(936,  PopCapBreakdown.of(site(100, 1, 2, 1), tech("colony-mgmt-i", "colony-mgmt-ii")).cap()); }

    @Test
    void components_areExposed() {
        Site s = site(200, 2);
        s.buildings.add(new Building(BuildingType.HABITAT, 1));
        s.buildings.get(1).enabled = false;
        PopCapBreakdown b = PopCapBreakdown.of(s, tech("colony-mgmt-i"));
        assertEquals(200, b.siteBase());
        assertEquals(200, b.habitatBoost());   // disabled L1 excluded
        assertEquals(1.2, b.techMultiplier(), 1e-12);
    }
}
```

- [ ] **Step 2: Implement and delegate**

```java
package spacecolony.sim;

/** populationCap = round((siteBase + Σ enabled HABITAT level × 100) × popCapMultiplier). */
public record PopCapBreakdown(int siteBase, int habitatBoost, double techMultiplier, int cap) {
    public static PopCapBreakdown of(Site s, TechState t) {
        int boost = 0;
        for (Building b : s.buildings) {
            if (b.enabled && b.type == BuildingType.HABITAT) boost += b.level * 100;
        }
        double mult = TechEffects.popCapMultiplier(t);
        return new PopCapBreakdown(s.siteBase, boost, mult, (int) Math.round((s.siteBase + boost) * mult));
    }
}
```

In `ProductionPhase`, replace the body of `recomputeCap` with `s.populationCap = PopCapBreakdown.of(s, tech).cap();` and keep its doc comment pointing at `PopCapBreakdown`.

- [ ] **Step 3: Test and commit.** `HabitatCapTest` must pass unchanged.

```bash
./gradlew test
git add -A && git commit -m "refactor(sim): extract PopCapBreakdown as the single cap formula"
```

---

### Task 4: `TransitPhase.fuelCost` and `ResearchPhase.pointsPerTick`

**Files:**
- Modify: `src/main/java/spacecolony/sim/phases/TransitPhase.java`, `src/main/java/spacecolony/sim/phases/ResearchPhase.java`
- Create: `src/test/java/spacecolony/sim/TransitFuelCostTest.java`
- Modify: `src/test/java/spacecolony/sim/ResearchTest.java`

- [ ] **Step 1: Failing tests**

`TransitFuelCostTest`: build a HAULER at Earth Hub, give it 100 fuel, and plant a Mars site the same way `TransitMathTest` does (copy its fixture helper). Dispatch with a small METAL manifest, then advance until `state == IN_TRANSIT`, recording `fuelBefore` just before the departure tick. Assert:

```java
double expected = TransitPhase.fuelCost(w, ship.shipClass,
    ship.transit.cargoSnapshot().values().stream().mapToDouble(Double::doubleValue).sum(),
    ship.transit.originSiteId(), ship.transit.destSiteId(),
    ship.transit.departureTick(), ship.transit.arrivalTick());
assertEquals(fuelBefore - ship.fuel, expected, 1e-9);
```

Add a second case with `ion-drives` researched: the deducted fuel is 0.8× the no-tech value for an identical dispatch.

Append to `ResearchTest`:

```java
    @Test
    void pointsPerTick_sumsEnabledLabsWithMultiplier() {
        World w = WorldGenerator.generate(1L);
        Site s = w.findSite("site-earth-hub");
        s.buildings.add(new Building(BuildingType.RESEARCH_LAB, 2));
        s.buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
        s.buildings.get(s.buildings.size() - 1).enabled = false;
        assertEquals(2.0, ResearchPhase.pointsPerTick(w), 1e-12);
        w.tech.researched.add("research-i");
        assertEquals(2.5, ResearchPhase.pointsPerTick(w), 1e-12);
    }
```

- [ ] **Step 2: Implement `fuelCost`**

In `TransitPhase`, add:

```java
    /**
     * Fuel a ship of class {@code c} carrying {@code cargoMass} burns flying from the origin
     * site's body at {@code t0} to the destination site's body at {@code t1}, under the
     * world's current tech. Used at departure and (as an estimate) by the debug map overlay.
     */
    public static double fuelCost(World w, ShipClass c, double cargoMass,
                                  String originSiteId, String destSiteId, long t0, long t1) {
        double dist = distanceBetweenSitesAtTicks(w, originSiteId, destSiteId, t0, t1);
        return FUEL_K * (c.dryMass() + cargoMass) * dist * TechEffects.fuelCostMultiplier(w.tech);
    }
```

Replace the inline `dist` + `cost` lines in `loadingAndUnloading` with:

```java
                    double cost = fuelCost(w, s.shipClass, s.cargoMass(),
                        s.transit.originSiteId(), s.transit.destSiteId(), depart, arrival);
```

`FUEL_K` (0.5) is already a private constant in `TransitPhase`. `CommandPhase` has its own copy for the dispatch-time estimate; leave that one alone.

- [ ] **Step 3: Implement `pointsPerTick`**

Move the lab loop out of `ResearchPhase.run` into:

```java
    /** Research points generated per tick by all enabled labs, including tech multipliers. */
    public static double pointsPerTick(World w) {
        double points = 0;
        double mult = TechEffects.researchLabMultiplier(w.tech);
        for (Body b : w.bodies) for (Site s : b.sites)
            for (Building bd : s.buildings)
                if (bd.enabled && bd.type == BuildingType.RESEARCH_LAB) points += bd.level * mult;
        return points;
    }
```

`run` then does `w.tech.accumulatedPoints += pointsPerTick(w);`. Hoisting the multiplier out of the loop changes floating-point results only if the sum is reassociated. `bd.level * 1.0 * m` summed over labs equals `Σ(level × m)` term for term, so keep the per-term multiplication as shown and `DeterminismTest` stays byte-identical.

- [ ] **Step 4: Test and commit**

```bash
./gradlew test
git add -A && git commit -m "refactor(sim): expose TransitPhase.fuelCost and ResearchPhase.pointsPerTick"
```

---

### Task 5: Phase observer and queue depth

**Files:**
- Create: `src/main/java/spacecolony/sim/SimPhase.java`, `src/main/java/spacecolony/sim/PhaseObserver.java`
- Modify: `src/main/java/spacecolony/sim/Simulator.java`
- Create: `src/test/java/spacecolony/sim/SimulatorPhaseObserverTest.java`

- [ ] **Step 1: Failing test**

```java
package spacecolony.sim;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import spacecolony.save.SaveFile;
import spacecolony.sim.commands.QueueResearchCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SimulatorPhaseObserverTest {
    @Test
    void observer_seesEightPhasesInOrder() {
        World w = WorldGenerator.generate(1L);
        Simulator sim = new Simulator();
        List<SimPhase> seen = new ArrayList<>();
        List<Long> ticks = new ArrayList<>();
        sim.setPhaseObserver((tick, phase, nanos) -> { seen.add(phase); ticks.add(tick); assertTrue(nanos >= 0); });
        sim.advance(w);
        assertEquals(List.of(SimPhase.values()), seen);
        assertTrue(ticks.stream().allMatch(t -> t == 1L), "observer reports post-increment tick");
    }

    @Test
    void observer_doesNotChangeOutcome() {
        World a = WorldGenerator.generate(9L), b = WorldGenerator.generate(9L);
        Simulator sa = new Simulator(), sb = new Simulator();
        sb.setPhaseObserver((t, p, n) -> {});
        for (int i = 0; i < 500; i++) { sa.advance(a); sb.advance(b); }
        assertEquals(SaveFile.toJson(a), SaveFile.toJson(b));
    }

    @Test
    void queueDepth_reportsPendingCommands() {
        Simulator sim = new Simulator();
        sim.enqueue(new QueueResearchCommand("basic-mining"));
        sim.enqueue(new QueueResearchCommand("basic-farming"));
        assertEquals(2, sim.queueDepth());
        sim.clearCommands();
        assertEquals(0, sim.queueDepth());
    }
}
```

(`observer_doesNotChangeOutcome` uses `SaveFile.toJson` from Task 6. Implement Task 6 before this task.)

- [ ] **Step 2: Implement**

```java
package spacecolony.sim;
/** The 8 per-tick phases of {@link Simulator#advance}, in execution order. */
public enum SimPhase { COMMANDS, TICK, ARRIVALS, PRODUCTION, DEPARTURES, EVENTS, RESEARCH, GOALS }
```

```java
package spacecolony.sim;
/** Optional timing hook for {@link Simulator}. {@code tick} is the tick being produced (post-increment). */
@FunctionalInterface
public interface PhaseObserver { void phaseDone(long tick, SimPhase phase, long nanos); }
```

`Simulator`:

```java
    private PhaseObserver observer; // null = no timing

    public void setPhaseObserver(PhaseObserver o) { this.observer = o; }
    public int queueDepth() { return commandQueue.size(); }

    public void advance(World w) {
        if (observer == null) {
            CommandPhase.drain(w, commandQueue);
            w.tick++;
            TransitPhase.advanceTransits(w);
            ProductionPhase.run(w);
            TransitPhase.loadingAndUnloading(w);
            EventPhase.run(w);
            ResearchPhase.run(w);
            GoalPhase.run(w);
            return;
        }
        long next = w.tick + 1;
        timed(next, SimPhase.COMMANDS,   () -> CommandPhase.drain(w, commandQueue));
        timed(next, SimPhase.TICK,       () -> w.tick++);
        timed(next, SimPhase.ARRIVALS,   () -> TransitPhase.advanceTransits(w));
        timed(next, SimPhase.PRODUCTION, () -> ProductionPhase.run(w));
        timed(next, SimPhase.DEPARTURES, () -> TransitPhase.loadingAndUnloading(w));
        timed(next, SimPhase.EVENTS,     () -> EventPhase.run(w));
        timed(next, SimPhase.RESEARCH,   () -> ResearchPhase.run(w));
        timed(next, SimPhase.GOALS,      () -> GoalPhase.run(w));
    }

    private void timed(long tick, SimPhase p, Runnable r) {
        long t0 = System.nanoTime();
        r.run();
        observer.phaseDone(tick, p, System.nanoTime() - t0);
    }
```

The un-instrumented branch is kept verbatim so the no-debug path doesn't allocate a lambda per phase per tick. Update the class Javadoc's phase list to name `SimPhase`.

- [ ] **Step 3: Test and commit**

```bash
./gradlew test
git add -A && git commit -m "feat(sim): optional PhaseObserver timing hook and queueDepth"
```

---

### Task 6: `SaveFile.toJson` / `fromJson`

**Files:**
- Modify: `src/main/java/spacecolony/save/SaveFile.java`
- Create: `src/test/java/spacecolony/save/SaveFileJsonTest.java`

- [ ] **Step 1: Failing test**

```java
package spacecolony.save;

import org.junit.jupiter.api.Test;
import spacecolony.sim.Simulator;
import spacecolony.sim.World;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class SaveFileJsonTest {
    @Test
    void toJson_fromJson_isStable() throws Exception {
        World w = WorldGenerator.generate(3L);
        Simulator sim = new Simulator();
        for (int i = 0; i < 300; i++) sim.advance(w);
        String a = SaveFile.toJson(w);
        String b = SaveFile.toJson(SaveFile.fromJson(a));
        assertEquals(a, b);
    }

    @Test
    void fromJson_wrongVersion_throws() {
        String bad = SaveFile.toJson(WorldGenerator.generate(1L))
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99");
        IncompatibleSaveException e = assertThrows(IncompatibleSaveException.class, () -> SaveFile.fromJson(bad));
        assertEquals(99, e.fileSchemaVersion);
    }
}
```

Check the writer's exact spacing (`"schemaVersion": 1`) against `JsonWriterTest` before relying on the `replace`.

- [ ] **Step 2: Implement.** In `SaveFile`:

```java
    /** Serialise {@code w} to the schema-v1 envelope (same text {@link #save} writes). */
    public static String toJson(World w) { return JsonWriter.write(buildEnvelope(w)); }

    /** Parse a schema-v1 envelope. Throws {@link JsonParseException} on malformed text. */
    public static World fromJson(String text) throws IncompatibleSaveException { … body of load after readString … }
```

`save` becomes `String json = toJson(w);` followed by the existing atomic write. `load` becomes `return fromJson(Files.readString(file));`.

- [ ] **Step 3: Test and commit**

```bash
./gradlew test
git add -A && git commit -m "refactor(save): string-level toJson/fromJson for snapshots"
```

---

### Task 7: `EventPhase.applyForced`

**Files:** `src/main/java/spacecolony/sim/phases/EventPhase.java`, `src/test/java/spacecolony/sim/EventTechEffectsTest.java`

- [ ] **Step 1:** Rename `applyForTest` → `applyForced`. New Javadoc:

```java
    /**
     * Apply a specific event kind to a body, bypassing the random draw in {@link #run}.
     * Used by tests and by the debug "Trigger event…" control, which passes
     * {@code DeterministicRng.forStep(seed, tick, DEBUG_STEP_ID)} so forced events are reproducible.
     */
    public static void applyForced(World w, Body b, EventKind kind, Random rng) { applyEvent(w, b, kind, rng); }

    /** RNG step id reserved for debug-forced events; never used by a sim phase. */
    public static final long DEBUG_STEP_ID = 99L;
```

- [ ] **Step 2:** Update both call sites in `EventTechEffectsTest`. `./gradlew test`. Commit: `refactor(sim): rename EventPhase test seam to applyForced for debug use`.

---

### Task 8: Engine debug API

**Files:**
- Modify: `src/main/java/spacecolony/engine/Engine.java`, `src/main/java/spacecolony/engine/EngineEvent.java`
- Create: `src/test/java/spacecolony/engine/EngineDebugTest.java`

- [ ] **Step 1: Failing tests** (all inside `Edt.run`)

```java
    @Test void debugToggle_firesOncePerChange() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            List<EngineEvent> seen = new ArrayList<>();
            e.addListener(seen::add);
            e.setDebugEnabled(true); e.setDebugEnabled(true); e.setDebugEnabled(false);
            assertEquals(List.of(new EngineEvent.DebugModeChanged(true), new EngineEvent.DebugModeChanged(false)), seen);
        });
    }

    @Test void step_whilePaused_advancesOneTick() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.step();
            assertEquals(1L, e.world().tick);
        });
    }

    @Test void step_whileRunning_assertionFires() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.setSpeed(Speed.X1);
            assertThrows(AssertionError.class, e::step);
        });
    }

    @Test void advanceSilently_firesOneWorldChanged() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            List<EngineEvent> seen = new ArrayList<>();
            e.addListener(seen::add);
            e.advanceSilently(250);
            assertEquals(250L, e.world().tick);
            assertEquals(1, seen.stream().filter(x -> x instanceof EngineEvent.WorldChanged).count());
        });
    }

    @Test void advanceSilently_rejectsOutOfRange() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            assertThrows(IllegalArgumentException.class, () -> e.advanceSilently(0));
            assertThrows(IllegalArgumentException.class, () -> e.advanceSilently(10_001));
        });
    }

    @Test void applyDebugEdit_mutatesAndNotifies_onlyWhenPaused() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            List<EngineEvent> seen = new ArrayList<>();
            e.addListener(seen::add);
            e.applyDebugEdit("credits", w -> w.credits = 42);
            assertEquals(42, e.world().credits);
            assertTrue(seen.stream().anyMatch(x -> x instanceof EngineEvent.WorldChanged));
            e.setSpeed(Speed.X1);
            assertThrows(AssertionError.class, () -> e.applyDebugEdit("x", w -> {}));
        });
    }

    @Test void commandQueueDepth_tracksEnqueue() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            e.enqueue(new QueueResearchCommand("basic-mining"));
            assertEquals(1, e.commandQueueDepth());
            e.tick();
            assertEquals(0, e.commandQueueDepth());
        });
    }
```

- [ ] **Step 2: Implement.** In `EngineEvent`, add `EngineEvent.DebugModeChanged` to `permits` and:

```java
    /** Debug mode toggled (Ctrl+D, --debug, or Debug menu). */
    record DebugModeChanged(boolean enabled) implements EngineEvent {}
```

In `Engine`:

```java
    private boolean debugEnabled;
    private final long[] tickTimes = new long[64];   // ring of System.nanoTime() at each tick
    private int tickCount;

    public boolean debugEnabled() { return debugEnabled; }
    public void setDebugEnabled(boolean on) {
        EdtGuard.assertEdt();
        if (on == debugEnabled) return;
        debugEnabled = on;
        fire(new EngineEvent.DebugModeChanged(on));
    }

    /** Advance exactly one tick while paused (debug "Step"). */
    public void step() {
        EdtGuard.assertEdt();
        assert speed.isPaused() : "step() requires PAUSED";
        tick();
    }

    /** Advance {@code n} ticks, firing a single WorldChanged at the end (debug "Run N"). */
    public void advanceSilently(int n) {
        EdtGuard.assertEdt();
        if (n < 1 || n > 10_000) throw new IllegalArgumentException("n must be 1..10000, was " + n);
        for (int i = 0; i < n; i++) simulator.advance(world);
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    /**
     * The only path by which anything outside the Simulator mutates World. Debug-only;
     * requires PAUSED so edits never interleave with ticks.
     */
    public void applyDebugEdit(String description, java.util.function.Consumer<World> fn) {
        EdtGuard.assertEdt();
        assert speed.isPaused() : "debug edits require PAUSED";
        fn.accept(world);
        fire(new EngineEvent.WorldChanged(world.tick));
    }

    public int commandQueueDepth() { return simulator.queueDepth(); }
    public void setPhaseObserver(PhaseObserver o) { simulator.setPhaseObserver(o); }

    /** Measured ticks per real second over the recorded ring (0 when fewer than 2 ticks). */
    public double ticksPerSecond() {
        int n = Math.min(tickCount, tickTimes.length);
        if (n < 2) return 0.0;
        long newest = tickTimes[(tickCount - 1) % tickTimes.length];
        long oldest = tickTimes[(tickCount - n) % tickTimes.length];
        return (n - 1) / ((newest - oldest) / 1e9);
    }
```

`tick()` records `tickTimes[tickCount++ % tickTimes.length] = System.nanoTime();` before firing. Logging for these methods arrives in Task 10.

- [ ] **Step 3: Test and commit**

```bash
./gradlew test
git add -A && git commit -m "feat(engine): debug mode flag, step, advanceSilently, applyDebugEdit"
```

---

### Task 9: `RingBufferHandler` + `DebugLogging`

**Files:**
- Create: `src/main/java/spacecolony/debug/RingBufferHandler.java`, `src/main/java/spacecolony/debug/DebugLogging.java`
- Create: `src/test/java/spacecolony/debug/RingBufferHandlerTest.java`, `src/test/java/spacecolony/debug/DebugLoggingTest.java`

- [ ] **Step 1: Failing tests**

```java
class RingBufferHandlerTest {
    @Test void keepsNewestUpToCapacity() {
        RingBufferHandler h = new RingBufferHandler(3);
        for (int i = 0; i < 5; i++) h.publish(new LogRecord(Level.INFO, "m" + i));
        assertEquals(List.of("m2", "m3", "m4"), h.snapshot().stream().map(LogRecord::getMessage).toList());
    }

    @Test void concurrentPublish_losesNothingBelowCapacity() throws Exception {
        RingBufferHandler h = new RingBufferHandler(10_000);
        ExecutorService ex = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) ex.submit(() -> { for (int i = 0; i < 1000; i++) h.publish(new LogRecord(Level.INFO, "x")); });
        ex.shutdown();
        assertTrue(ex.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(4000, h.snapshot().size());
    }

    @Test void version_increasesOnPublish() {
        RingBufferHandler h = new RingBufferHandler(2);
        long v0 = h.version();
        h.publish(new LogRecord(Level.INFO, "a"));
        assertTrue(h.version() > v0);
    }
}
```

```java
class DebugLoggingTest {
    @Test void parseLevel_acceptsSpecAliases() {
        assertEquals(Level.FINE,    DebugLogging.parseLevel("DEBUG"));
        assertEquals(Level.WARNING, DebugLogging.parseLevel("warn"));
        assertEquals(Level.INFO,    DebugLogging.parseLevel("INFO"));
        assertEquals(Level.FINE,    DebugLogging.parseLevel("FINE"));
        assertNull(DebugLogging.parseLevel("loud"));
    }

    @Test void install_createsLogFile(@TempDir Path dir) throws Exception {
        DebugLogging.Installed inst = DebugLogging.install(Level.INFO, dir);
        try {
            Logger.getLogger("spacecolony.test").info("hello");
            inst.flush();
            try (var files = Files.list(dir)) {
                assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("space-colony-")));
            }
            assertTrue(inst.ring().snapshot().stream().anyMatch(r -> "hello".equals(r.getMessage())));
        } finally { inst.uninstall(); }
    }

    @Test void install_unwritableDir_stillHasRing(@TempDir Path dir) throws Exception {
        Path blocker = dir.resolve("file");
        Files.writeString(blocker, "not a dir");
        DebugLogging.Installed inst = DebugLogging.install(Level.INFO, blocker.resolve("logs"));
        try {
            assertNotNull(inst.ring());
            assertTrue(inst.ring().snapshot().stream().anyMatch(r -> r.getLevel() == Level.WARNING));
        } finally { inst.uninstall(); }
    }
}
```

- [ ] **Step 2: Implement `RingBufferHandler`**

```java
package spacecolony.debug;

/** Keeps the newest {@code capacity} LogRecords in memory for the log viewer. Thread-safe. */
public final class RingBufferHandler extends Handler {
    private final ArrayDeque<LogRecord> records = new ArrayDeque<>();
    private final int capacity;
    private long version;

    public RingBufferHandler(int capacity) { this.capacity = capacity; setLevel(Level.ALL); }

    @Override public synchronized void publish(LogRecord r) {
        if (r == null || !isLoggable(r)) return;
        records.addLast(r);
        while (records.size() > capacity) records.removeFirst();
        version++;
    }
    public synchronized List<LogRecord> snapshot() { return List.copyOf(records); }
    /** Monotonic counter; the viewer polls it to skip redundant refreshes. */
    public synchronized long version() { return version; }
    public synchronized void clear() { records.clear(); version++; }
    @Override public void flush() {}
    @Override public void close() {}
}
```

- [ ] **Step 3: Implement `DebugLogging`**

```java
package spacecolony.debug;

/** Installs the spacecolony logger tree: in-memory ring (always) + rotating daily file (best effort). */
public final class DebugLogging {
    public static final String ROOT = "spacecolony";
    public static final int RING_CAPACITY = 2000;
    private static final Logger LOG = Logger.getLogger("spacecolony.debug");

    public record Installed(Logger root, RingBufferHandler ring, FileHandler file) {
        public void flush() { if (file != null) file.flush(); }
        public void setLevel(Level l) { root.setLevel(l); }
        public void uninstall() {
            root.removeHandler(ring);
            if (file != null) { root.removeHandler(file); file.close(); }
        }
    }

    private static volatile Installed current;
    public static Installed current() { return current; }

    /** "DEBUG"→FINE, "WARN"→WARNING, any JUL level name (case-insensitive); null when unrecognised. */
    public static Level parseLevel(String s) {
        if (s == null) return null;
        String u = s.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (u) {
            case "DEBUG" -> Level.FINE;
            case "WARN"  -> Level.WARNING;
            default -> { try { yield Level.parse(u); } catch (IllegalArgumentException e) { yield null; } }
        };
    }

    public static Path defaultLogDir() {
        return Paths.get(System.getProperty("user.home"), ".space-colony", "logs");
    }

    public static Installed install(Level level, Path logDir) {
        Logger root = Logger.getLogger(ROOT);
        root.setUseParentHandlers(false);
        root.setLevel(level);
        RingBufferHandler ring = new RingBufferHandler(RING_CAPACITY);
        root.addHandler(ring);
        FileHandler file = null;
        try {
            Files.createDirectories(logDir);
            String pattern = logDir.resolve("space-colony-" + LocalDate.now() + ".%g.log").toString();
            file = new FileHandler(pattern, 1_000_000, 5, true);
            file.setFormatter(new SimpleFormatter());
            file.setLevel(Level.ALL);
            root.addHandler(file);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "File logging disabled: " + e.getMessage(), e);
        }
        Installed inst = new Installed(root, ring, file);
        current = inst;
        return inst;
    }
    private DebugLogging() {}
}
```

Set the one-line format in `SpaceColonyApp` (Task 12) via `System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %3$s: %5$s%6$s%n")` **before** the first `SimpleFormatter` is created.

- [ ] **Step 4: Test and commit**

```bash
./gradlew test
git add -A && git commit -m "feat(debug): JUL ring buffer + rotating file logging"
```

---

### Task 10: Engine logging bridge

**Files:**
- Modify: `src/main/java/spacecolony/engine/Engine.java`
- Create: `src/test/java/spacecolony/engine/EngineLoggingTest.java`

- [ ] **Step 1: Failing test**

```java
class EngineLoggingTest {
    @Test void tickEvents_areLoggedToSimEventsLogger() throws Exception {
        Logger events = Logger.getLogger("spacecolony.sim.events");
        List<LogRecord> got = new ArrayList<>();
        Handler h = new Handler() {
            @Override public void publish(LogRecord r) { got.add(r); }
            @Override public void flush() {} @Override public void close() {}
        };
        events.addHandler(h);
        try {
            Edt.run(() -> {
                World w = WorldGenerator.generate(1L);
                w.findSite("site-earth-hub").buildings.add(new Building(BuildingType.RESEARCH_LAB, 1));
                w.tech.activeId = "basic-mining";
                w.tech.accumulatedPoints = 99.5;
                Engine e = new Engine(w);
                e.tick();   // lab adds ≥1 point → RESEARCH_COMPLETED this tick
            });
            assertTrue(got.stream().anyMatch(r -> r.getLevel() == Level.INFO && r.getMessage().contains("Researched Basic Mining")));
        } finally { events.removeHandler(h); }
    }

    @Test void debugEdit_isLoggedWithDescription() throws Exception {
        // same handler pattern on "spacecolony.debug"; applyDebugEdit("set credits", …) → INFO containing "set credits"
    }
}
```

- [ ] **Step 2: Implement.** Add loggers to `Engine`:

```java
    private static final Logger LOG = Logger.getLogger("spacecolony.engine");
    private static final Logger EVENTS = Logger.getLogger("spacecolony.sim.events");
    private static final Logger DEBUG = Logger.getLogger("spacecolony.debug");
```

- `tick()` and each iteration of `advanceSilently`: after `simulator.advance(world)`, call `logNewEvents()`, which logs each new event as `[Y%d D%d] KIND: message` at INFO (INFO severity) or WARNING (WARNING/ERROR). Don't key this on `tick`: `CommandPhase` emits rejections with the pre-increment tick. Instead keep a reference to the newest `Event` already logged (`lastLogged`) and log every event after it in `recentEvents`, oldest first. If `lastLogged` has been evicted from the ring (200 cap, e.g. during `advanceSilently`), log the whole ring.
- `reset`: `LOG.info("World replaced: seed=" + newWorld.seed + " tick=" + newWorld.tick)`. Also reset the "newest logged" marker.
- `setSpeed`: `LOG.fine("Speed " + s)`.
- `enqueue`: `LOG.fine("Enqueue " + c)`. Commands are records, so `toString` is readable.
- `applyDebugEdit`: `DEBUG.info("Debug edit at tick " + world.tick + ": " + description)`.

- [ ] **Step 3: Test and commit.** `feat(engine): bridge sim events and engine actions to java.util.logging`.

---

### Task 11: `ExceptionLog` + `CrashHandler`

**Files:**
- Create: `src/main/java/spacecolony/debug/ExceptionLog.java`, `src/main/java/spacecolony/debug/CrashHandler.java`
- Create: `src/test/java/spacecolony/debug/CrashHandlerTest.java`

- [ ] **Step 1: Failing test**

```java
class CrashHandlerTest {
    @Test void uncaught_isLoggedRecordedAndReportedOnce() throws Exception {
        ExceptionLog log = new ExceptionLog(20);
        List<Throwable> reported = new ArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        // Reporter that "holds the dialog open" until released.
        CrashHandler.Reporter r = t -> { reported.add(t); release.await(5, TimeUnit.SECONDS); };
        CrashHandler h = new CrashHandler(log, () -> false /* debug off */, r);

        Thread a = new Thread(() -> { throw new IllegalStateException("boom-1"); });
        a.setUncaughtExceptionHandler(h);
        a.start();
        waitUntil(() -> reported.size() == 1);
        h.uncaughtException(Thread.currentThread(), new RuntimeException("boom-2")); // while dialog open
        release.countDown();
        a.join();

        assertEquals(2, log.snapshot().size());
        assertEquals(1, reported.size(), "second exception must not open another dialog");
    }

    @Test void debugOn_neverReports() {
        ExceptionLog log = new ExceptionLog(20);
        List<Throwable> reported = new ArrayList<>();
        CrashHandler h = new CrashHandler(log, () -> true, reported::add);
        h.uncaughtException(Thread.currentThread(), new RuntimeException("x"));
        assertEquals(1, log.snapshot().size());
        assertTrue(reported.isEmpty());
    }
}
```

(`waitUntil` is a tiny polling helper in the test class with a 5 s deadline.)

- [ ] **Step 2: Implement**

```java
package spacecolony.debug;

/** Bounded, thread-safe list of recent uncaught exceptions for the overlay banner. */
public final class ExceptionLog {
    public record Entry(Instant at, String thread, Throwable error) {}
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private final int capacity;
    private long total;
    public ExceptionLog(int capacity) { this.capacity = capacity; }
    public synchronized void add(Thread t, Throwable e) {
        entries.addLast(new Entry(Instant.now(), t.getName(), e));
        while (entries.size() > capacity) entries.removeFirst();
        total++;
    }
    public synchronized List<Entry> snapshot() { return List.copyOf(entries); }
    public synchronized long total() { return total; }
    public synchronized void clear() { entries.clear(); }
}
```

```java
package spacecolony.debug;

/** Spec §8.2: log every uncaught exception; outside debug mode, show one "Something went wrong" dialog at a time. */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {
    @FunctionalInterface public interface Reporter { void report(Throwable t) throws Exception; }

    private static final Logger LOG = Logger.getLogger("spacecolony.crash");
    private final ExceptionLog log;
    private final BooleanSupplier debugOn;
    private final Reporter reporter;
    private final AtomicBoolean reporting = new AtomicBoolean();

    public CrashHandler(ExceptionLog log, BooleanSupplier debugOn, Reporter reporter) { … }

    @Override public void uncaughtException(Thread t, Throwable e) {
        LOG.log(Level.SEVERE, "Uncaught on " + t.getName(), e);
        log.add(t, e);
        if (debugOn.getAsBoolean()) return;
        if (!reporting.compareAndSet(false, true)) return;
        try { reporter.report(e); }
        catch (Exception ex) { LOG.log(Level.SEVERE, "Crash reporter failed", ex); }
        finally { reporting.set(false); }
    }

    /** For SwingWorker.done() bodies that catch an unexpected ExecutionException. */
    public void report(Throwable e) { uncaughtException(Thread.currentThread(), e); }

    /** Process-wide instance installed by SpaceColonyApp; null in tests that don't install one. */
    private static volatile CrashHandler installed;
    public static void install(CrashHandler h) { installed = h; Thread.setDefaultUncaughtExceptionHandler(h); }
    public static void reportIfInstalled(Throwable e) {
        CrashHandler h = installed;
        if (h != null) h.report(e); else LOG.log(Level.SEVERE, "Unhandled", e);
    }
}
```

The production `Reporter` is built in Task 12: an `invokeAndWait`-safe dialog on the EDT with Continue/Quit, where Quit calls `GameSession.quit()`.

- [ ] **Step 3: Test and commit.** `feat(debug): uncaught-exception handler with single-dialog reporting`.

---

### Task 12: Launch flags, logging/crash install, `DebugController` skeleton, Ctrl+D

**Files:**
- Modify: `src/main/java/spacecolony/SpaceColonyApp.java`, `src/main/java/spacecolony/ui/SpaceColonyFrame.java`
- Create: `src/main/java/spacecolony/debug/DebugController.java`, `src/main/java/spacecolony/debug/PhaseTimings.java`
- Create: `src/test/java/spacecolony/debug/PhaseTimingsTest.java`, `src/test/java/spacecolony/ui/DebugControllerTest.java`

- [ ] **Step 1: `PhaseTimings` (test first)**

```java
class PhaseTimingsTest {
    @Test void keepsLast50PerPhase_meanLeMax() {
        PhaseTimings pt = new PhaseTimings(50);
        for (long t = 1; t <= 60; t++) for (SimPhase p : SimPhase.values()) pt.phaseDone(t, p, t * 1000);
        for (SimPhase p : SimPhase.values()) {
            PhaseTimings.Stat s = pt.stat(p);
            assertEquals(50, s.samples());
            assertEquals(60_000, s.maxNanos());
            assertEquals((11 + 60) / 2.0 * 1000, s.meanNanos(), 1e-6);
        }
    }
}
```

`PhaseTimings implements PhaseObserver` holds a `long[50]` ring per phase (an `EnumMap<SimPhase, long[]>` plus counts) and has `record Stat(int samples, double meanNanos, long maxNanos)`. It's EDT-only, like the engine.

- [ ] **Step 2: `SpaceColonyApp` argument parsing**

```java
        long seed = 42L;
        boolean debug = false;
        Level level = Level.INFO;
        String badLevel = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[++i]);
            else if (a.equals("--debug")) debug = true;
            else if (a.startsWith("--log-level=")) {
                Level l = DebugLogging.parseLevel(a.substring("--log-level=".length()));
                if (l != null) level = l; else badLevel = a;
            }
        }
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT %4$s %3$s: %5$s%6$s%n");
        DebugLogging.install(level, DebugLogging.defaultLogDir());
        if (badLevel != null) Logger.getLogger("spacecolony").warning("Ignoring " + badLevel + "; using INFO");
        ExceptionLog exceptions = new ExceptionLog(20);
```

Then, inside `invokeLater`: build `Engine`, then `SpaceColonyFrame(engine, exceptions)`. Next install `CrashHandler.install(new CrashHandler(exceptions, engine::debugEnabled, frame::showCrashDialog))`, then `engine.setDebugEnabled(debug)` and show the frame.

`frame::showCrashDialog` runs the dialog on the EDT and blocks the caller until it closes. On the EDT it calls the dialog directly (modal, so it pumps events). Off the EDT it uses `invokeAndWait`. Its options are Continue (return) and Quit. Until Task 21 lands, Quit calls `System.exit(0)`; Task 21 switches it to `session.quit()` so it autosaves.

- [ ] **Step 3: `DebugController`**

```java
package spacecolony.debug;

/**
 * Owns debug-mode UI lifecycle. Constructed once by SpaceColonyFrame; listens for
 * DebugModeChanged and mounts/unmounts the overlay, Debug menu, phase timings, and map overlays.
 * Ctrl+D is bound on the frame's root pane.
 */
public final class DebugController {
    public DebugController(JFrame frame, JMenuBar menuBar, JPanel southStack, Engine engine,
                           ExceptionLog exceptions, Runnable repaintMap);
    public boolean mapOverlaysOn();          // debug on && overlay toggle on (default on)
    public PhaseTimings timings();
    public ExceptionLog exceptions();
    public Engine engine();
}
```

- Constructor: registers Ctrl+D in `frame.getRootPane()`'s `WHEN_IN_FOCUSED_WINDOW` input map (`KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)`, action `engine.setDebugEnabled(!engine.debugEnabled())`), adds an engine listener, and applies the current state.
- On enable: `timings = new PhaseTimings(50); engine.setPhaseObserver(timings);`. Then it adds the overlay (Task 13) at index 0 of `southStack` and the Debug menu (Task 14) to `menuBar`, calls `revalidate`/`repaint` on both, and `repaintMap.run()`.
- On disable: `engine.setPhaseObserver(null)`, removes both, revalidates, and repaints the map.

For now the overlay and menu are empty placeholders (`new JPanel()`, `new JMenu("Debug")`). Tasks 13–14 fill them.

- [ ] **Step 4: Frame wiring.** `SpaceColonyFrame(Engine, ExceptionLog)`:
  - SOUTH becomes a `southStack` panel (`BoxLayout.Y_AXIS`) holding the `EventStripPanel`.
  - Construct `DebugController` after the menu bar and panels, passing `mainView::repaint` (the center `MainViewPanel`).
  - Expose `debugController()` for tests and the play-test driver.
  - Keep the one-argument constructor delegating with a fresh `ExceptionLog(20)`, so existing tests compile.

- [ ] **Step 5: `DebugControllerTest`** (EDT, no `setVisible`)

```java
    @Test void toggle_mountsAndUnmountsOverlayAndMenu() throws Exception {
        Edt.run(() -> {
            Engine e = new Engine(WorldGenerator.generate(1L));
            SpaceColonyFrame f = new SpaceColonyFrame(e);
            int menus = f.getJMenuBar().getMenuCount();
            e.setDebugEnabled(true);
            assertEquals(menus + 1, f.getJMenuBar().getMenuCount());
            assertEquals("Debug", f.getJMenuBar().getMenu(menus).getText());
            e.setDebugEnabled(false);
            assertEquals(menus, f.getJMenuBar().getMenuCount());
            f.dispose();
        });
    }
```

This test lives in `src/test/java/spacecolony/ui/` because it builds `SpaceColonyFrame`, and `debug` must not import `ui`, in tests too.

- [ ] **Step 6: Test and commit.** `feat(debug): --debug/--log-level flags, Ctrl+D toggle, DebugController lifecycle`.

---

### Task 13: Debug overlay panel

**Files:**
- Create: `src/main/java/spacecolony/debug/DebugOverlayPanel.java`, `src/main/java/spacecolony/debug/DebugActions.java`

- [ ] **Step 1: `DebugActions`.** One `javax.swing.Action` per control, shared by the overlay buttons and the Debug menu (Task 14). Each asserts EDT in `actionPerformed`.

| Action | Enabled when | Does |
|---|---|---|
| `step` | paused | `engine.step()` |
| `runN` | always | pause; `JOptionPane.showInputDialog("Ticks to run (1–10000):", "1000")`; validate; set wait cursor on the frame; `engine.advanceSilently(n)`; restore cursor; restore prior speed. Log `"Ran n ticks in x ms"`. |
| `triggerEvent` | paused | `TriggerEventDialog.show(frame, engine)` (Task 15) |
| `dumpWorld` | always | `SwingWorker`: `SaveFile.save(world, debugDir.resolve("world-" + ts + "-t" + tick + ".json"))`; `done()` logs the path and calls `overlay.setStatus("Dumped to …")`, or logs SEVERE and shows the error in the status |
| `determinism` | always | Task 16 |
| `inspector` | always | Task 17: inspect the current selection, or the World |
| `logViewer` | always | Task 18 |
| `toggleMapOverlays` | always | flip the controller flag, `repaintMap.run()` (a `JCheckBoxMenuItem` in the menu) |
| `throwTest` | always | `throw new IllegalStateException("Debug test exception")`, for play-test step 9 |

The speed-dependent `enabled` states update from an engine listener on `SpeedChanged`. Debug dir: `~/.space-colony/debug/`. Timestamp format `yyyyMMdd-HHmmss`.

- [ ] **Step 2: `DebugOverlayPanel` layout** (`BorderLayout`, monospaced 11pt, preferred height 150)
  - NORTH: status line `JLabel`, formatted as `tick 1234 · 15.9 t/s · X16 · queue 0 · rng(seed=42, tick=1234) · <status message>`.
  - CENTER: `JTable` (non-editable `AbstractTableModel`) with columns Phase, Mean µs, Max µs, and 8 rows from `PhaseTimings`.
  - EAST: vertical button column for Step, Run N…, Trigger…, Dump, Determinism, Inspector, Logs.
  - SOUTH: exceptions banner (`JPanel` with a red label and a Details… button). It's visible only when `exceptions.total() > 0`. The label reads `3 exceptions · latest: IllegalStateException: boom` and Details opens the log viewer with level SEVERE.
- Refresh on `WorldChanged`/`WorldReplaced`, and on a 500 ms `javax.swing.Timer` that runs only while the overlay is displayable (start in `addNotify`, stop in `removeNotify`), so the paused state and new exceptions show without ticks.
- `setStatus(String)` sets the trailing status message. It is cleared after 10 s.

- [ ] **Step 3: Smoke test** in `PanelSmokeTest`: construct `DebugController` via the frame with debug on, then find the `DebugOverlayPanel` in the south stack, size it 900×150, and `paintToImage` it.

- [ ] **Step 4: Test and commit.** `feat(debug): overlay with status, phase timings, exceptions banner, sim controls`.

---

### Task 14: Debug menu

**Files:** Create `src/main/java/spacecolony/debug/DebugMenu.java`; modify `DebugController`.

- [ ] **Step 1:** `DebugMenu extends JMenu("Debug")` built from `DebugActions`:
  - Step (`F10`), Run N… (`Ctrl+R`), Trigger event…, then a separator.
  - Dump world, Determinism check, then a separator.
  - Inspector (`Ctrl+I`), Log viewer (`Ctrl+L`).
  - "Log level" submenu: radio items FINE/INFO/WARNING calling `DebugLogging.current().setLevel(...)`.
  - A "Map overlays" `JCheckBoxMenuItem`, a separator, and "Throw test exception".

  Accelerators exist only while the menu is mounted, so they're only live in debug mode. None clash, because the game has no other shortcuts; `Ctrl+S` is taken by Save in Task 22.
- [ ] **Step 2:** `DebugController` mounts this in place of the placeholder. Extend `DebugControllerTest` to assert the menu has the Log level submenu.
- [ ] **Step 3:** Test and commit. `feat(debug): Debug menu with accelerators and log-level control`.

---

### Task 15: Trigger event dialog

**Files:** Create `src/main/java/spacecolony/debug/TriggerEventDialog.java`.

- [ ] **Step 1:** Modal dialog with two combos:
  - Body: bodies with sites first, then the rest, labelled `name (id)`.
  - Kind: `METEOR_STRIKE`, `SOLAR_FLARE`, `EQUIPMENT_FAILURE`, `DISEASE_OUTBREAK`.

  OK is enabled only when `engine.speed().isPaused()`. Otherwise it's disabled with the tooltip "Pause the game to trigger events". On OK:

```java
String bodyId = selectedBody.id;
engine.applyDebugEdit("trigger " + kind + " on " + bodyId,
    w -> EventPhase.applyForced(w, w.findBody(bodyId), kind,
             DeterministicRng.forStep(w.seed, w.tick, EventPhase.DEBUG_STEP_ID)));
```

- [ ] **Step 2: Headless test** in `EngineDebugTest`. This exercises the same lambda the dialog uses without showing the dialog: while paused, apply a `DISEASE_OUTBREAK` on Earth via `applyDebugEdit`; Earth Hub population drops and the newest event is `DISEASE_OUTBREAK`.
- [ ] **Step 3:** Commit. `feat(debug): trigger-event dialog`.

---

### Task 16: Determinism check + dump

**Files:**
- Create: `src/main/java/spacecolony/debug/DeterminismCheck.java`
- Create: `src/test/java/spacecolony/debug/DeterminismCheckTest.java`

- [ ] **Step 1: Failing tests**

```java
class DeterminismCheckTest {
    @Test void freshWorld_isDeterministic() throws Exception {
        World w = WorldGenerator.generate(5L);
        DeterminismCheck.Result r = DeterminismCheck.run(w, 1000);
        assertTrue(r.match(), r.firstDiff());
        assertEquals(0L, w.tick, "live world must not be advanced");
    }

    @Test void injectedNondeterminism_isDetected() throws Exception {
        World w = WorldGenerator.generate(5L);
        // Perturb only the second restored copy; the check must notice.
        DeterminismCheck.Result r = DeterminismCheck.run(SaveFile.toJson(w), 200,
            (copyIndex, copy) -> { if (copyIndex == 1) copy.credits += 1; });
        assertFalse(r.match());
        assertTrue(r.firstDiff().contains("credits"), r.firstDiff());
    }
}
```

The test seam is a `BiConsumer<Integer, World>` "perturb" hook, called on each copy right after it is restored. Final signature:

```java
/** Convenience for tests: snapshots {@code live} (must be called on the thread that owns it). */
public static Result run(World live, int ticks) throws IncompatibleSaveException;
/** Off-EDT entry point: works only on copies restored from {@code snapshot}. */
public static Result run(String snapshot, int ticks) throws IncompatibleSaveException;
static Result run(String snapshot, int ticks, BiConsumer<Integer, World> perturb) throws IncompatibleSaveException; // test seam
public record Result(boolean match, int ticks, long millis, String firstDiff) {}
```

- [ ] **Step 2: Implement**

```java
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
    static String firstDiff(String a, String b) { … split on '\n', walk both … }
```

`run(World, int)` is `run(SaveFile.toJson(live), ticks, (i, w) -> {})`. The live world must only be read on the EDT, so the `determinism` action takes the snapshot on the EDT, then runs the string overload in the worker, and `done()` reports `"Determinism OK (1000 ticks, 312 ms)"` or `"Determinism MISMATCH: line 212: …"` to the overlay status and the log (INFO/SEVERE).

- [ ] **Step 3:** Wire the `determinism` action. Test and commit. `feat(debug): determinism check on restored copies`.

---

### Task 17: Object inspector

**Files:**
- Create: `src/main/java/spacecolony/debug/ObjectTreeModel.java`, `src/main/java/spacecolony/debug/ObjectInspectorDialog.java`
- Create: `src/test/java/spacecolony/debug/ObjectTreeModelTest.java`
- Modify: `src/main/java/spacecolony/ui/SystemMapPanel.java`, `ColonyListPanel.java`, `BodyViewPanel.java`

- [ ] **Step 1: Failing model tests**

```java
class ObjectTreeModelTest {
    static final class Loop { Loop self; int n = 3; }

    @Test void site_exposesFields_withEditability() {
        Site s = new Site("s", "S", "earth", 0, 0, 200);
        ObjectTreeModel m = new ObjectTreeModel(s, "site");
        ObjectTreeModel.Node pop = m.child(m.root(), "population");
        ObjectTreeModel.Node base = m.child(m.root(), "siteBase");
        assertTrue(pop.isEditableLeaf());
        assertFalse(base.isEditableLeaf(), "final field");
        assertEquals("200", base.valueText());
    }

    @Test void map_childrenAreKeys() {
        Site s = new Site("s", "S", "earth", 0, 0, 200);
        ObjectTreeModel m = new ObjectTreeModel(s, "site");
        ObjectTreeModel.Node stock = m.child(m.root(), "stockpile");
        assertNotNull(m.child(stock, "[FOOD]"));
    }

    @Test void cycle_rendersAsBackReference() {
        Loop l = new Loop(); l.self = l;
        ObjectTreeModel m = new ObjectTreeModel(l, "loop");
        ObjectTreeModel.Node self = m.child(m.root(), "self");
        assertTrue(self.label().startsWith("self ↺"));
        assertEquals(0, m.getChildCount(self));
    }

    @Test void setValue_parsesAndWrites() throws Exception {
        Site s = new Site("s", "S", "earth", 0, 0, 200);
        ObjectTreeModel m = new ObjectTreeModel(s, "site");
        m.setValue(m.child(m.root(), "population"), "321");
        assertEquals(321, s.population);
        m.setValue(m.child(m.root(), "morale"), "0.5");
        assertEquals(0.5, s.morale);
        assertThrows(IllegalArgumentException.class, () -> m.setValue(m.child(m.root(), "siteBase"), "1"));
        assertThrows(NumberFormatException.class, () -> m.setValue(m.child(m.root(), "population"), "lots"));
    }

    @Test void enumField_listsConstants() {
        Building b = new Building(BuildingType.MINE, 1);
        ObjectTreeModel m = new ObjectTreeModel(b, "b");
        ObjectTreeModel.Node type = m.child(m.root(), "type");
        assertFalse(type.isEditableLeaf(), "final");
    }
}
```

- [ ] **Step 2: Implement `ObjectTreeModel implements TreeModel`**

`Node` holds a label, the value, an optional `Field` plus owner (for field nodes), a path from the root (a list of labels, used to keep expansion across refreshes) and lazily computed children.

- **Leaf types:** `null`, primitives and boxes, `String`, `Enum`, and `ResourceYieldSampler` (rendered `<sampler>`).
- **Children:**
  - Records: `getRecordComponents()` via the accessor.
  - `Collection`: `[i]`, capped at 500 with a `… N more` leaf.
  - `Map`: `[key]`.
  - Arrays: `[i]` via `java.lang.reflect.Array`.
  - Other objects: declared fields up the superclass chain, skipping static and synthetic fields, with `trySetAccessible()`. A field that can't be made accessible renders `<inaccessible>`.
- **Cycle guard:** an `IdentityHashMap`-backed set of ancestors on the node's path. A repeat renders `label ↺ Type@hash` with no children.
- **Depth cap:** 12.
- `isEditableLeaf()`: field node, not final, type primitive, box, `String` or enum.
- `setValue(Node, String text)`: parses by field type (`Integer.parseInt`, `Double.parseDouble`, `Long.parseLong`, `Boolean.parseBoolean`; enums via `Enum.valueOf`; `String` as-is) and calls `field.set(owner, v)`. It throws `IllegalArgumentException` when the node isn't editable.
- `refresh()` drops cached children and fires `treeStructureChanged` on the root.

- [ ] **Step 3: `ObjectInspectorDialog`** (non-modal `JDialog`, 520×640)
  - `JTree` over the model, with a custom renderer showing `label: value` in monospace. Expand the root on open.
  - An "Edit" `JToggleButton` in the toolbar. When it's on and the engine is paused, double-clicking an editable leaf opens an inline editor: a `JComboBox` of constants for enums, otherwise a `JTextField`. On commit it runs `engine.applyDebugEdit("inspector: " + node.pathString() + " = " + text, w -> model.setValue(node, text))`. A parse failure shows a `JOptionPane` error and nothing changes. While running, the toggle is disabled with the tooltip "Pause to edit".
  - A listener on `WorldChanged` calls `model.refresh()` and re-expands the saved expanded paths (matched by label paths). A `WorldReplaced` listener disposes the dialog. Remove the listener in `dispose()`.
  - `static void inspect(Component parent, Engine engine, Object target, String title)`.

- [ ] **Step 4: Shift+click wiring (ui)** — each branch runs only when `engine.debugEnabled() && e.isShiftDown()`:
  - `SystemMapPanel.mousePressed`: after hit-testing, if a body was hit, `ObjectInspectorDialog.inspect(this, engine, best, "Body " + best.id)` and return without changing the selection.
  - `ColonyListPanel` row listener: inspect the `Site` or `Ship` the row refers to.
  - `BodyViewPanel.SpherePanel.mousePressed`: inspect `currentBody()` instead of opening `PlaceSiteDialog`.
  - The `inspector` action inspects the current selection (`findBody`/`findSite`/`findShip`), or `engine.world()` when there's no selection.

- [ ] **Step 5: Smoke test.** Construct `ObjectInspectorDialog` on the Earth Hub (don't `setVisible`) and paint its tree to an image. Test and commit. `feat(debug): reflective object inspector with paused-only field edits`.

---

### Task 18: Log viewer

**Files:** Create `src/main/java/spacecolony/debug/LogViewerDialog.java`.

- [ ] **Step 1:** Non-modal `JDialog` (900×520) with a `JTabbedPane`:
  - **Log tab.** `JTable` over `ring.snapshot()` with columns Time (`HH:mm:ss.SSS`), Level, Logger (drop the `spacecolony.` prefix), and Message (`record.getMessage()` formatted with `new SimpleFormatter().formatMessage(r)`).
    - Toolbar: min-level combo (ALL, FINE, INFO, WARNING, SEVERE), logger-prefix text field, "Follow" checkbox (default on), Copy, Save….
    - A `JTextArea` in a split pane below shows the selected record's thrown stack (`StringWriter` + `printStackTrace`).
    - A 500 ms `Timer` refreshes when `ring.version()` changed. With Follow on, it scrolls to the last row.
  - **Events tab.** `JTable` over `engine.world().recentEvents` with columns Date (`Y%d D%d`), Severity, Kind and Message, a severity combo and a kind combo. It refreshes on `WorldChanged`/`WorldReplaced`.
  - **Copy:** the visible rows of the active tab as tab-separated text go to the system clipboard.
  - **Save…:** `JFileChooser`, then write the same text with `Files.writeString`, off-EDT in a `SwingWorker`.
- [ ] **Step 2:** `static void show(Component parent, Engine engine, Level initialMinLevel)`. The exceptions banner's Details button passes `Level.SEVERE`.
- [ ] **Step 3:** Smoke test: build with a ring holding 3 records (one with a thrown exception), select row 3, and paint. Commit. `feat(debug): log viewer with level/logger filters and event tab`.

---

### Task 19: Map overlays + `YieldSummary`

**Files:**
- Create: `src/main/java/spacecolony/debug/YieldSummary.java`, `src/test/java/spacecolony/debug/YieldSummaryTest.java`
- Modify: `src/main/java/spacecolony/ui/SystemMapPanel.java`

- [ ] **Step 1: Failing test**

```java
class YieldSummaryTest {
    @Test void topThree_sortedDescending_andCached() {
        World w = WorldGenerator.generate(1L);
        YieldSummary ys = new YieldSummary();
        Body mars = w.findBody("mars");
        List<YieldSummary.Entry> top = ys.top(mars, 3);
        assertEquals(3, top.size());
        assertTrue(top.get(0).mean() >= top.get(1).mean() && top.get(1).mean() >= top.get(2).mean());
        assertSame(top, ys.top(mars, 3));
        ys.clear();
        assertNotSame(top, ys.top(mars, 3));
    }
}
```

- [ ] **Step 2: Implement.** `YieldSummary` caches by body id. `top(Body, k)` samples `b.resourceYields.sample(r, lat, lon)` on an 8×16 grid (lat from −π/2+π/16 in π/8 steps, lon from −π+π/16 in π/8 steps) for each stockpileable resource. It sorts by mean descending, keeps `k`, and returns an unmodifiable list, which it caches. If `resourceYields` is null, it returns an empty list. `Entry(Resource resource, double mean)`. `format(List<Entry>)` gives `ORE .62 · ICE .41 · SIL .30`, using three-letter abbreviations from `resource.name().substring(0, 3)`.

- [ ] **Step 3: `SystemMapPanel` overlays.** The panel gets a `DebugController` reference through a setter (`setDebug(DebugController)`, called by the frame after construction), because the controller is built after the panels. In `paintComponent`, after the existing layers, if `debug != null && debug.mapOverlaysOn()`:
  - **Orbit labels:** for top-level bodies, draw `b.orbit.period() + " d"` at `(cx + r·cos45°, cy − r·sin45°)` in `ORBIT_LINE` brightened. For moons, draw `period d` at `(x + 6, y + 14)` next to the moon.
  - **Transit prediction:** for each `IN_TRANSIT` ship, compute its current point as today, then `dp` at `arrivalTick` (already computed). Draw a dashed line (`BasicStroke` with dash `{4, 4}`) from the ship to `dp`, and a hollow 8 px circle at `dp`. Label it at `dp + (6, −6)` with `String.format("t=%d  ≈%.1f fuel", arrival, TransitPhase.fuelCost(world, ship.shipClass, snapshotMass, origin, dest, departure, arrival))`.
  - **Yields:** under each body label (`y + 16`), draw `YieldSummary.format(yields.top(b, 3))` in `FOREGROUND_DIM` at 10 pt. The panel owns one `YieldSummary`, cleared on `WorldReplaced`.

- [ ] **Step 4:** Smoke test: debug on, a ship forced `IN_TRANSIT` (copy the fixture from `TransitMathTest`), and paint 900×700. Commit. `feat(ui): debug map overlays for orbits, transit predictions, yield summaries`.

---

### Task 20: `SaveSlots` + `SlotInfo`

**Files:**
- Create: `src/main/java/spacecolony/save/SaveSlots.java`, `src/main/java/spacecolony/save/SlotInfo.java`
- Create: `src/test/java/spacecolony/save/SaveSlotsTest.java`

- [ ] **Step 1: Failing tests**

```java
class SaveSlotsTest {
    @Test void validateName() {
        assertNull(SaveSlots.validateName("colony 1"));
        assertNull(SaveSlots.validateName("mars_run-2"));
        for (String bad : List.of("", "   ", "x".repeat(41), "_x", "a/b", "a.json", "a.autosave"))
            assertNotNull(SaveSlots.validateName(bad), bad);
    }

    @Test void paths(@TempDir Path dir) {
        SaveSlots s = new SaveSlots(dir);
        assertEquals(dir.resolve("colony.json"), s.slotPath("colony"));
        assertEquals(dir.resolve("colony.autosave.json"), s.autosavePath("colony"));
        assertEquals(dir.resolve("_autosave.json"), s.autosavePath(null));
        assertThrows(IllegalArgumentException.class, () -> s.slotPath("a/b"));
    }

    @Test void list_newestFirst_withHeaders(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        World a = WorldGenerator.generate(1L); a.tick = 10; a.credits = 111;
        World b = WorldGenerator.generate(2L); b.tick = 20; b.credits = 222;
        SaveFile.save(a, s.slotPath("alpha"));
        SaveFile.save(b, s.slotPath("beta"));
        SaveFile.save(b, s.autosavePath("beta"));
        Files.setLastModifiedTime(s.slotPath("alpha"), FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(s.slotPath("beta"), FileTime.fromMillis(2_000));
        Files.setLastModifiedTime(s.autosavePath("beta"), FileTime.fromMillis(3_000));
        List<SlotInfo> l = s.list();
        assertEquals(List.of("beta", "beta", "alpha"), l.stream().map(SlotInfo::name).toList());
        assertTrue(l.get(0).autosave());
        assertEquals(20L, l.get(1).tick());
        assertEquals(111L, l.get(2).credits());
    }

    @Test void list_unreadableAndWrongSchema_stillListed(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        Files.writeString(dir.resolve("junk.json"), "{nope");
        Files.writeString(dir.resolve("old.json"), SaveFile.toJson(WorldGenerator.generate(1L))
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99"));
        Map<String, SlotInfo> byName = s.list().stream().collect(Collectors.toMap(SlotInfo::name, x -> x));
        assertEquals(SlotInfo.Status.UNREADABLE, byName.get("junk").status());
        assertEquals(SlotInfo.Status.OTHER_SCHEMA, byName.get("old").status());
        assertEquals(99, byName.get("old").schemaVersion());
    }

    @Test void delete_removesOnlyTarget(@TempDir Path dir) throws Exception {
        SaveSlots s = new SaveSlots(dir);
        World w = WorldGenerator.generate(1L);
        SaveFile.save(w, s.slotPath("c"));
        SaveFile.save(w, s.autosavePath("c"));
        s.delete("c", false);
        assertFalse(Files.exists(s.slotPath("c")));
        assertTrue(Files.exists(s.autosavePath("c")));
    }

    @Test void list_missingDir_isEmpty(@TempDir Path dir) throws Exception {
        assertEquals(List.of(), new SaveSlots(dir.resolve("nope")).list());
    }
}
```

- [ ] **Step 2: Implement**

```java
package spacecolony.save;

public record SlotInfo(String name, Path path, boolean autosave, Instant modified,
                       Status status, int schemaVersion, long tick, long seed, long credits) {
    public enum Status { OK, OTHER_SCHEMA, UNREADABLE }
}
```

`SaveSlots`:
- `NAME = Pattern.compile("[A-Za-z0-9 _-]{1,40}")`.
- `validateName(raw)`: trims, then returns `"Name is required"`, `"At most 40 characters"`, `"Letters, digits, space, _ and - only"` or `"Names starting with _ are reserved"`, or `null` when the name is valid. The `.json`/`.autosave` cases fail the character rule because `.` isn't allowed.
- `list()` returns `List.of()` if the directory is missing.
  - For each `*.json`, it derives the name: strip `.json`, then strip `.autosave` (setting `autosave = true`). `_autosave.json` gets the name `_autosave` with `autosave = true`.
  - Then `JsonReader.parse(Files.readString(p))`. `JsonParseException`, `IOException` or `ClassCastException` gives `UNREADABLE`. A version other than `SaveFile.SCHEMA_VERSION` gives `OTHER_SCHEMA` (tick etc. = −1). Otherwise it reads `tick`, `seed` and `credits` for `OK`.
  - Sort by `modified` descending, then name.
  - Unreadable files are logged at FINE to `spacecolony.save`.
- `delete(name, autosave)`: `Files.deleteIfExists` on the resolved path. It only resolves names that pass validation, or the literal `_autosave`.

- [ ] **Step 3:** Test and commit. `feat(save): named save slots with listing, validation, delete`.

---

### Task 21: `GameSession` (current slot, quit + autosave)

**Files:**
- Create: `src/main/java/spacecolony/ui/GameSession.java`, `src/test/java/spacecolony/ui/GameSessionTest.java`
- Modify: `src/main/java/spacecolony/ui/SpaceColonyFrame.java`

- [ ] **Step 1: Failing tests**

```java
class GameSessionTest {
    record Calls(List<Integer> exits, List<String> prompts) {}

    private static GameSession session(Engine e, SaveSlots slots, Calls c, boolean confirmQuit, boolean quitAnyway) {
        return new GameSession(e, slots, new GameSession.Ui() {
            public boolean confirmQuit() { return confirmQuit; }
            public boolean quitAnyway(String msg) { c.prompts().add(msg); return quitAnyway; }
            public void savedToast(String label) {}
        }, c.exits()::add);
    }

    @Test void slotName_transitions(@TempDir Path dir) throws Exception {
        Edt.run(() -> {
            GameSession s = session(new Engine(WorldGenerator.generate(1L)), new SaveSlots(dir),
                                    new Calls(new ArrayList<>(), new ArrayList<>()), true, true);
            assertNull(s.currentSlot());
            s.onSavedAs("colony");                         assertEquals("colony", s.currentSlot());
            s.onNewGame();                                 assertNull(s.currentSlot());
            s.onLoaded(new SaveSlots(dir).autosavePath("mars"));    assertEquals("mars", s.currentSlot());
            s.onLoaded(new SaveSlots(dir).autosavePath(null));      assertNull(s.currentSlot());
            s.onLoaded(new SaveSlots(dir).slotPath("belt"));        assertEquals("belt", s.currentSlot());
            s.onLoaded(Path.of("/tmp/elsewhere/x.json"));           assertNull(s.currentSlot());
        });
    }

    @Test void quit_writesAutosave_thenExits(@TempDir Path dir) throws Exception {
        Calls c = new Calls(new ArrayList<>(), new ArrayList<>());
        Edt.run(() -> {
            GameSession s = session(new Engine(WorldGenerator.generate(1L)), new SaveSlots(dir), c, true, true);
            s.onSavedAs("colony");
            s.quit();
        });
        assertTrue(Files.exists(dir.resolve("colony.autosave.json")));
        assertEquals(List.of(0), c.exits());
    }

    @Test void quit_cancelled_doesNothing(@TempDir Path dir) throws Exception { /* confirmQuit=false → no file, no exit */ }

    @Test void quit_autosaveFails_asksBeforeExit(@TempDir Path dir) throws Exception {
        Path blocker = dir.resolve("f"); Files.writeString(blocker, "x");
        Calls c = new Calls(new ArrayList<>(), new ArrayList<>());
        Edt.run(() -> session(new Engine(WorldGenerator.generate(1L)), new SaveSlots(blocker.resolve("saves")), c, true, false).quit());
        assertEquals(1, c.prompts().size());
        assertTrue(c.exits().isEmpty(), "declined 'quit anyway'");
    }
}
```

- [ ] **Step 2: Implement**

```java
package spacecolony.ui;

/** Current save slot + the one quit path (menu, window close, crash dialog). */
public final class GameSession {
    public interface Ui {
        boolean confirmQuit();
        boolean quitAnyway(String autosaveError);
        void savedToast(String label);
    }
    private static final Logger LOG = Logger.getLogger("spacecolony.save");
    private final Engine engine; private final SaveSlots slots; private final Ui ui; private final IntConsumer exit;
    private String currentSlot;

    public GameSession(Engine engine, SaveSlots slots, Ui ui, IntConsumer exit) { … }

    public String currentSlot() { return currentSlot; }
    public SaveSlots slots() { return slots; }
    public void onSavedAs(String name) { currentSlot = name; }
    public void onNewGame() { currentSlot = null; }
    /** Derive the slot from a loaded path: <n>.json / <n>.autosave.json inside the slots dir → n; else null. */
    public void onLoaded(Path file) { … }
    public void savedToast(String label) { ui.savedToast(label); }

    public void quit() {
        EdtGuard.assertEdt();
        engine.setSpeed(Speed.PAUSED);
        if (!ui.confirmQuit()) return;
        Path target = slots.autosavePath(currentSlot);
        try {
            SaveFile.save(engine.world(), target);
            LOG.info("Autosaved to " + target);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.SEVERE, "Autosave failed", e);
            if (!ui.quitAnyway(e.getMessage())) return;
        }
        exit.accept(0);
    }
}
```

`quit()` leaves the game paused when cancelled. That matches Plan 4's New Game ("leave paused; player presses 1×").

- [ ] **Step 3: Frame wiring**
  - `setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)`.
  - `addWindowListener(new WindowAdapter() { windowClosing → session.quit() })`.
  - `session = new GameSession(engine, SaveSlots.defaultDir(), swingUi, System::exit)`. `swingUi` uses `JOptionPane` for `confirmQuit`/`quitAnyway` and forwards `savedToast` to `TopBar` (Task 23).
  - Expose `session()`. The crash dialog's Quit calls `session.quit()`.
  - Tests construct the frame but never close it, so `System::exit` is never reached in tests.
- [ ] **Step 4:** Test and commit. `feat(ui): GameSession tracks current slot and autosaves on quit`.

---

### Task 22: Slot-based File menu + `SaveSlotDialog`

**Files:**
- Create: `src/main/java/spacecolony/ui/SaveSlotDialog.java`
- Modify: `src/main/java/spacecolony/ui/FileMenu.java`, `src/test/java/spacecolony/ui/PanelSmokeTest.java`, `src/test/java/spacecolony/playtest/PlayTestDriver.java`

- [ ] **Step 1: `FileMenu(JFrame, Engine, GameSession)`.** Replace the two-argument constructor. `PanelSmokeTest` passes a session built on a `@TempDir` `SaveSlots` with a no-op `Ui` and exit. Items:

| Item | Accelerator | Behaviour |
|---|---|---|
| New Game | – | Plan 4 flow; on success `session.onNewGame()` |
| Save | Ctrl+S | if `currentSlot != null`: save to `slotPath(currentSlot)` in a worker, then toast; else Save As |
| Save As… | Ctrl+Shift+S | `SaveSlotDialog.saveAs(owner, slots)`, which returns a name or null; save, `session.onSavedAs(name)`, toast |
| Load… | Ctrl+O | `SaveSlotDialog.load(owner, slots)`, which returns a `SlotInfo` or null; load via the Plan 4 worker + error dialogs; on success `engine.reset(w)`, `session.onLoaded(info.path())` |
| Load from file… | – | the Plan 4 `JFileChooser` flow verbatim; on success `session.onLoaded(file)` |
| Quit | Ctrl+Q | `session.quit()` |

Every action keeps Plan 4's pause/restore-speed and `EdtGuard.assertEdt()`. Worker `done()` bodies route an unexpected `ExecutionException` to `CrashHandler.reportIfInstalled`. Expected exceptions keep their dialogs.

- [ ] **Step 2: `SaveSlotDialog`** (modal)
  - Shared: a `JTable` with columns Name, Kind ("slot"/"autosave"), Saved (`yyyy-MM-dd HH:mm`), Game date (`Y%d D%d` from tick, or "(unreadable)"/"(schema N)"), Credits. It's loaded via `SwingWorker<List<SlotInfo>>` from `slots.list()` and shows "Loading…" until then.
  - `saveAs`: the table shows only `OK` non-autosave slots, plus a name field below. Row selection copies the name into the field. `validateName` runs on each keystroke (`DocumentListener`) and the message shows in red under the field, with Save disabled while invalid. If the name exists, a "Overwrite “name”?" confirm appears. Returns the trimmed name.
  - `load`: all slots. Load is enabled only for `OK` rows, and double-click on an `OK` row loads. Delete (enabled for any row) asks "Delete “name” (autosave)?", then calls `slots.delete`, then reloads the table. Returns the `SlotInfo`.
  - Both have Cancel, which returns null.
- [ ] **Step 3: Tests**
  - `PanelSmokeTest.fileMenu_buildsWithoutCrashing` now expects 7 components: New/Save/Save As/Load/Load from file/separator/Quit.
  - Add `saveSlotDialog_buildsWithoutCrashing`: construct the dialog's content panel via a package-private factory `SaveSlotDialog.contentForTest(slots, Mode.LOAD)` over a temp dir with one save, wait for the worker (`Edt.run` twice), and paint it.
- [ ] **Step 4: `PlayTestDriver`**
  - The menu-items assertion becomes `List.of("New Game", "Save", "Save As…", "Load…", "Load from file…", "<separator>", "Quit")`.
  - Steps that drove the Save/Load `JFileChooser`: the save-and-reload round-trip (step 4) and mid-transit round-trip (step 9) now drive `SaveSlotDialog`. Find the dialog, set the name field, and click Save. For Load, select the row by name and click Load.
  - Steps 7–8 (hand-edited schema/garbage files) keep the chooser via "Load from file…".
- [ ] **Step 5:** Test and commit. `feat(ui): save slots dialog and slot-aware File menu`.

---

### Task 23: Save toast in `TopBar`

**Files:** `src/main/java/spacecolony/ui/TopBar.java`, `src/main/java/spacecolony/ui/SpaceColonyFrame.java`

- [ ] **Step 1:** Add a `statusLabel` to the left flow (after credits) in `UiColors.INFO`. Add `public void toast(String text)`: set the text and (re)start a 3 s one-shot `Timer` that clears it.
- [ ] **Step 2:** `GameSession.Ui.savedToast(label)` in the frame calls `topBar.toast("Saved “" + label + "”")`. `FileMenu` calls `session.savedToast(name)` on successful saves. `quit()` doesn't toast, because the app is exiting.
- [ ] **Step 3:** Smoke: `topBar.toast("x")` then paint. Commit. `feat(ui): transient save confirmation in top bar`.

---

### Task 24: Detail panel polish

PR #5 already added the morale line (`DetailPanel.moraleLine`, e.g. `Morale: 1.10 / 1.56  (life support +56%)`) and its colours. **Keep both.** This task adds only what's still missing from design §6.1.

**Files:** `src/main/java/spacecolony/ui/DetailPanel.java`, `src/test/java/spacecolony/ui/PanelSmokeTest.java`, `src/test/java/spacecolony/ui/PanelFormattingTest.java`

- [ ] **Step 1: Failing formatting tests** (append to `PanelFormattingTest`, which already covers `moraleLine`)

```java
    @Test void capBreakdown_omitsMultiplierAtOne() {
        assertEquals("  base 200 + habitats 100", DetailPanel.capBreakdown(new PopCapBreakdown(200, 100, 1.0, 300)));
        assertEquals("  base 200 + habitats 100 × 1.56 (colony mgmt)",
            DetailPanel.capBreakdown(new PopCapBreakdown(200, 100, 1.56, 468)));
    }

    @Test void techNote_listsOnlyChangedMultipliers() {
        TechState t = new TechState();
        assertEquals("", DetailPanel.techNote(BuildingType.MINE, t));
        t.researched.add("basic-mining");
        t.researched.add("hydroponics");
        assertEquals("  ore ×1.10", DetailPanel.techNote(BuildingType.MINE, t));
        assertEquals("  food ×1.30 · water ×0.70", DetailPanel.techNote(BuildingType.FARM, t));
        assertEquals("", DetailPanel.techNote(BuildingType.SHIPYARD, t));
    }
```

- [ ] **Step 2: Population + breakdown.** Change the pop line to use `PopCapBreakdown.of(s, w.tech).cap()` and add a dim line under it with the package-private `capBreakdown`:

```java
    static String capBreakdown(PopCapBreakdown c) {
        return "  base " + c.siteBase() + " + habitats " + c.habitatBoost()
            + (Math.abs(c.techMultiplier() - 1.0) > 1e-9 ? String.format(" × %.2f (colony mgmt)", c.techMultiplier()) : "");
    }
```

Use `cap.cap()` rather than `s.populationCap` so the breakdown always adds up. They're equal after any tick.

- [ ] **Step 3: Net rate grid.** After the stockpile grid, add a "Net / day:" header, then a 2-column grid for each resource with `|rate| > 1e-6` from `s.productionRateCache`, formatted `%+.1f`, in `UiColors.ERROR` when negative. If none, show a dim `(idle)`.

- [ ] **Step 4: Building multipliers.** Add package-private `static String techNote(BuildingType, TechState)`:
  - MINE: ore via `mineOreMultiplier`, silicate via `mineSilicateMultiplier`.
  - FARM: food via `farmFoodMultiplier`, water via `farmWaterDemandMultiplier`.
  - POWER_PLANT, REFINERY, RESEARCH_LAB: the bare multiplier.

  Each part is formatted `what ×%.2f` (bare `×%.2f` when there's no label), skipping values within 1e-9 of 1.0. Parts are joined with ` · ` and prefixed with two spaces, or the result is `""` when nothing changed. Building rows become `"  " + b.type + " L" + b.level + techNote(b.type, w.tech) + enabled`.

- [ ] **Step 5: Smoke test.** Select the Earth Hub with `colony-mgmt-i` researched, paint, and assert the collected label text contains `× 1.20 (colony mgmt)` and `Net / day:`. `allText(Component)` is a small recursive helper in the test.

- [ ] **Step 6:** Commit. `feat(ui): detail panel shows pop-cap breakdown, net rates, per-building tech multipliers`.

---

### Task 25: Tech modal polish

PR #5 rebuilt `TechModal` as `header(TechState)` + `buildList(Engine, Runnable)`, with per-tech effect deltas (`TechEffects.deltas`, `formatDelta`), "needs …" for locked techs (which can't be clicked), and an active-research line. **Keep all of that**, including the `PanelFormattingTest` coverage. This task adds the rest of design §6.2.

**Files:** `src/main/java/spacecolony/ui/TechModal.java`, `src/test/java/spacecolony/ui/PanelSmokeTest.java`

- [ ] **Step 1: Shared prereq logic.** Replace `TechModal`'s private `missingPrereqs` with `TechAvailability.missingPrereqs` (Task 1) mapped to names, so the UI and `CommandPhase` use one rule.
- [ ] **Step 2: Research rate in the header.** `header` takes the `World` (or the `TechState` plus a rate) and appends `String.format("  ·  %.1f pts/day", ResearchPhase.pointsPerTick(w))`, or "  ·  no research labs" when 0. Update any `PanelFormattingTest`/smoke call sites of `header(...)`.
- [ ] **Step 3: Tiers.** In `buildList`, group `TechCatalog.all()` by `TechAvailability.tier` (catalog order within a tier) and insert a "Tier N" header label before each group.
- [ ] **Step 4: Progress bar + ETA on the active row.** Under the title line of the active tech, add a `JProgressBar(0, (int) cost)` at `(int) accumulatedPoints`, labelled `"%d / %d  ·  ETA %d days"`. ETA is `ceil((cost − pts) / rate)`, or `"no labs"` when the rate is 0.
- [ ] **Step 5: Live refresh.** `show` builds its content through a `rebuild()` that replaces the NORTH header and the scroll view. An engine listener (`WorldChanged`/`WorldReplaced` → `rebuild()`) is added on show and removed in `windowClosed` (`setDefaultCloseOperation(DISPOSE_ON_CLOSE)`). The dialog stays modal. The Swing timer keeps ticking underneath, so the bar moves.
- [ ] **Step 6: Smoke test.** With `ion-drives` researched, `fusion-drives` active and a RESEARCH_LAB on the Earth Hub, build `buildList` and assert the collected text contains "Tier 2", "ETA" and "needs Fusion Drives" (antimatter). Commit. `feat(ui): tech modal tiers, research rate, progress bar with ETA, live refresh`.

---

### Task 26: Goals modal polish

PR #5 added the `header(Engine)` summary ("n / 8 achieved · earned …") and `rewardText(Goal)`. **Keep both.** This task adds categories, progress bars and live refresh (design §6.3).

**Files:** `src/main/java/spacecolony/ui/GoalsModal.java`

- [ ] **Step 1:** Same live-refresh structure as Task 25 (a `rebuild()` that replaces the header and list). Group by `GoalCategory` in enum order, with a header per category.
- [ ] **Step 2:** Each row keeps its current line (`✓/○ name  —  rewardText(g)`) and dim description, and adds a `JProgressBar(0, 1000)` at `(int) (g.displayProgress(w) * 1000)` labelled by a package-private `progressText(Goal, World)`. Achieved goals read "done". `pop-*` goals read `"%,d / %,d"` total pop against the target, `fleet-10` reads `n / 10 ships`, `five-bodies` reads `n / 5 bodies`, and the other goals read `not yet`. An unknown goal id falls back to a percentage.
- [ ] **Step 3:** Add `progressText` cases to `PanelFormattingTest`. On a fresh world, `pop-1000` gives `100 / 1,000` and `five-bodies` gives `1 / 5 bodies`. Commit. `feat(ui): goals grouped by category with progress bars and live refresh`.

---

### Task 27: Event strip polish

PR #5 added game dates (`EventStripPanel.format`: `Y0 D12  Meteor strike on Mars`), a kind tooltip and an empty state. **Keep them.** This task adds filters and click-to-select (design §6.4).

**Files:** `src/main/java/spacecolony/ui/EventStripPanel.java`

- [ ] **Step 1: Filters.** Add an EAST panel of three `JToggleButton`s (INFO, WARN, ERROR), all selected. Toggling calls `refresh()`, which skips events whose severity toggle is off and still shows up to `VISIBLE_EVENTS` matching rows. The empty-state text becomes "No matching events." when filters hide everything.
- [ ] **Step 2: Click to select.** Resolve a target, preferring ship, then site, then body:

```java
Selection target = ev.shipId() != null && w.findShip(ev.shipId()) != null ? Selection.ship(ev.shipId())
                 : ev.siteId() != null && w.findSite(ev.siteId()) != null ? Selection.site(ev.siteId())
                 : ev.bodyId() != null && w.findBody(ev.bodyId()) != null ? Selection.body(ev.bodyId())
                 : null;
```

If there is a target, set a hand cursor and add a `MouseAdapter` that calls `engine.setSelection(target)`. The tooltip becomes `prettyKind(kind) + " at tick " + tick + " · click to select"`.
- [ ] **Step 3: Smoke test.** Emit one WARNING with a `bodyId` and one INFO, then turn off INFO and assert one row is shown. Dispatch a `MOUSE_PRESSED` on the row and assert `engine.selection()` is the body. Commit. `feat(ui): event strip severity filters and click-to-select`.

---

### Task 28: Verification, play-test, PR

- [ ] **Step 1: Full suite.** `./gradlew test`. Expected: about 235 tests pass (183 carried over plus about 52 new).
- [ ] **Step 2: Layering checks**

```bash
# sim/world/save stay Swing-free
grep -rl "import javax.swing\|import java.awt" src/main/java/spacecolony/sim src/main/java/spacecolony/world src/main/java/spacecolony/save
# sim stays logging-free (engine bridges)
grep -rl "java.util.logging" src/main/java/spacecolony/sim
# debug never imports ui
grep -rl "import spacecolony.ui" src/main/java/spacecolony/debug
```

Expected: no output from any of the three.

- [ ] **Step 3: Headless main.** `./gradlew run --args="--seed 1 --ticks 1000"` still prints the summary.
- [ ] **Step 4: Scripted play-test.** Extend `PlayTestDriver` with the debug steps from design §7.5:
  - Ctrl+D shows the overlay.
  - Step → +1 tick.
  - Run N 500 → +500.
  - Trigger a METEOR_STRIKE on Earth → the strip's first row contains `METEOR_STRIKE`.
  - The determinism status contains "OK".
  - `GameSession.quit()` with an injected exit → the autosave exists and the Load dialog lists it.

  Run `./gradlew playTest` on a desktop session and check the screenshots in `build/playtest`.
- [ ] **Step 5: Manual checklist.** Walk design §7.7 steps 1–15 by hand and tick them in the PR description.
- [ ] **Step 6: Docs.** Mark this plan's steps `[x]`. In the game-design spec, change §9's "auto-save on quit" bullet to note the `_autosave.json` naming as built, and resolve §15's open question on debug shortcuts: Ctrl+D; F10, Ctrl+R, Ctrl+I and Ctrl+L in debug only; Ctrl+S/Ctrl+Shift+S/Ctrl+O/Ctrl+Q for File.
- [ ] **Step 7: PR**

```bash
git push -u origin plan-5/debug-mode-and-polish
gh pr create --title "Plan 5: debug mode, save slots + autosave, panel polish" --body "…"
```

Use the same body structure as Plan 4's PR: Summary, Test plan (checkboxes for the suite, layering greps, playTest and the manual checklist), and Out of scope (thumbnails, timed autosave, graph tech tree, distZip).

---

## Verification

1. `./gradlew test`: about 235 tests pass.
2. The layering greps in Task 28 Step 2 print nothing.
3. `./gradlew run --args="--seed 1 --ticks 1000"` completes and prints the summary.
4. `./gradlew play --args="--seed 1 --debug"` opens with the overlay. Ctrl+D toggles it.
5. `-ea` is still on for `test`/`run`/`play`. `EngineDebugTest.step_whileRunning_assertionFires` passing proves it.
6. Design §7.7 manual checklist passes end to end.

## What's Next

With Plan 5, every "In v1" bullet of the game-design spec §14 is built. Candidate Plan 6:
- A balance pass using the new tooling (phase timings, Run N, determinism check).
- Save thumbnails and timed autosave.
- The Mars appearance hook from Plan 3's review (`BodyAppearances.forBodyId`) and true gas-giant banding from Plan 2's notes.
- `distZip` packaging.
- Then the post-v1 list: sound, Kuiper/comets, Hohmann transfers.

## Spec Coverage

| Design section | Task |
|---|---|
| §3.1 Research prerequisites | 1, 25 |
| §3.2 Goal categories + progress | 2, 26 |
| §3.3 Pop-cap breakdown | 3, 24 |
| §3.4 Fuel-cost estimate | 4, 19 |
| §3.5 Phase observer | 5, 12, 13 |
| §3.6 Forced events | 7, 15 |
| §3.7 World snapshots | 6, 16 |
| §3.8 Engine additions | 8, 10 |
| §4.1 Turning debug on | 12 |
| §4.2 Overlay | 13 |
| §4.3 Trigger event | 15 |
| §4.4 Object inspector | 17 |
| §4.5 Determinism check | 16 |
| §4.6 Map overlays | 19 |
| §4.7 Logging | 9, 10 |
| §4.8 Uncaught exceptions | 11, 12 |
| §4.9 Debug menu | 14 |
| §4.10 Log viewer | 18 |
| §5.1 Slots | 20 |
| §5.2 GameSession | 21 |
| §5.3 Menu + dialog | 22 |
| §5.4 Window close | 21 |
| §5.5 Save confirmation | 23 |
| §6.1 Detail panel | 24 |
| §6.2 Tech modal | 25 |
| §6.3 Goals modal | 26 |
| §6.4 Event strip | 27 |
| §7 Testing | every task; 28 |

## Self-Review Notes

Checked against the code on `main` while writing:
- `TransitPhase.FUEL_K` already exists (0.5); `CommandPhase` keeps its own copy for the dispatch estimate and is untouched.
- `JsonWriter` emits `"key": value` with sorted keys and two-space indent, so the schema-99 `replace` in Tasks 6 and 20 matches.
- `BodyViewPanel` has no site markers; Shift+click there inspects the body (design §4.4 says so explicitly).
- `SwingWorker` exceptions never reach the default uncaught handler, so worker `done()` bodies route unexpected failures to `CrashHandler.reportIfInstalled` (Tasks 11, 22).
- PR #5 merged while this plan was being written. Tasks 1 and 24–27 were revised against it: they keep `TechEffects.deltas`, `DetailPanel.moraleLine`, `TechModal.header/buildList/formatDelta`, `GoalsModal.header/rewardText` and `EventStripPanel.format`, and add only what's still missing.
- Starting world has no RESEARCH_LAB; tests that need research points add one (Tasks 4, 10).
- `EngineEvent` listeners all use `instanceof`; adding `DebugModeChanged` needs no listener edits.
- `DebugControllerTest` lives under `src/test/java/spacecolony/ui/` because it builds `SpaceColonyFrame`; `debug` must not depend on `ui` even in tests.
