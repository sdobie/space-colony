# Space Colony — Plan 10: Ship Build Times and Fuel Types

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ships take time to build, by class, at a shipyard that builds one at a time. FUEL becomes HYDROGEN (gas giants, the best fuel), and a new Fuel plant makes SYNFUEL from BIOMASS anywhere, worth half a unit of hydrogen.

**Architecture:** `ShipState.BUILDING` with `Ship.buildDaysLeft` and `Ship.yardBuildingId`, advanced in ProductionPhase step 9. `sim.Fuels` holds the fuel resources and their value per unit; trip costs are fuel value, so hydrogen-only numbers don't change. `Ship.fuel` becomes a per-fuel `tank`. Save schema v5.

**Tech Stack:** Java 25, Gradle 9 via wrapper, JUnit 5, Swing. No new dependencies.

**Spec reference:** `docs/superpowers/specs/2026-11-01-plan-10-ship-build-times-and-fuels-design.md` (sections 1–10).

**Out of scope:** ship costs, a build queue, choosing a fuel per trip, fuel mass. See the design doc §1 and §9.

---

## Context

Plans 1–9 are on `main` (last: PR #40, 2026-11-01). GUI tests that build a frame need `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test` and skip themselves when headless. CI runs the full suite under xvfb on JDK 25.

Key facts about the existing code:

- `Resource` is an enum whose order the parity fixture follows (`ProductionParityTest` iterates `Resource.values()`). FUEL sits between BIOMASS and COMPONENTS; SYNFUEL goes right after it.
- FUEL is produced only in `SiteEconomy` (gas-giant MINE with `TechEffects.gasGiantFuelEnabled`). It is consumed only by ships: `TransitPhase.loadingAndUnloading` (draw at departure, explorer top-up), `TransitPhase.departFromOrbit`, `CommandPhase.fuelShortfall`, and drained back in `applyRetireShip` and `applyBuildSite`.
- `Ship.fuel` is one double; `ShipClass.tankCap()` is 100 for EXPLORER.
- `CommandPhase.applyBuildShip` adds a ready IDLE ship if the site has an operational SHIPYARD.
- `ProductionPhase.advanceConstruction` (step 9) counts down `Building.daysLeft`, skipping damaged buildings.
- `SaveFile.SCHEMA_VERSION = 4`; resource maps are written by enum name.
- The tutorial's build step completes on `hasColonizer`; `PlayTestDriver`, `StartupDriver`, `SurveyDriver`, `ShipGraphicsDriver` and `ContinuousPlayTester` all build ships and expect them ready.

---

## File Structure

### Create

```
src/main/java/spacecolony/sim/Fuels.java
src/main/java/spacecolony/sim/ShipYards.java
src/test/java/spacecolony/sim/FuelsTest.java
src/test/java/spacecolony/sim/FuelPlantTest.java
src/test/java/spacecolony/sim/ShipBuildTest.java
```

### Modify

- `sim/Resource.java`, `ShipClass.java`, `ShipState.java`, `Ship.java`, `BuildingType.java`, `BuildingCatalog.java`, `EventKind.java`, `TechCatalog.java`, `GoalCatalog.java`
- `sim/economy/SiteEconomy.java`, `sim/economy/BuildForecast.java`
- `sim/phases/CommandPhase.java`, `ProductionPhase.java`, `TransitPhase.java`
- `save/SaveFile.java`, `world/WorldGenerator.java`, `tutorial/TutorialScenario.java`, `tutorial/TutorialScript.java`
- `debug/DebugActions.java`
- `ui/ColonyListPanel.java`, `ui/DetailPanel.java`, `ui/SystemMapPanel.java`, `ui/BuildingInfoPanel.java`, `ui/dialogs/BuildShipDialog.java`, `ui/dialogs/DispatchShipDialog.java`
- Tests mentioning FUEL; `production-parity.txt`; play-test drivers.
- Docs: the design doc's status line; game-design spec §3.4 (build days) and §3.5 (fuels).

---

## Tasks

Part A (tasks 1–6) is the sim, headless and test-first. Part B (7–9) is the UI. Part C (10) is drivers, docs and the PR. Every task ends with `./gradlew test` green and a commit in the `feat(<pkg>): …` style.

### Task 0: Branch

- [ ] Work on the thread branch from the latest `main` (restart it from `main` after the design PR merges).

## Part A — The sim

### Task 1: Rename FUEL to HYDROGEN and add SYNFUEL

- [ ] **Step 1:** Rename the enum constant and every reference in main and test code (IDE-style rename, then grep for `"FUEL"` strings and player text: tech description, ledger labels, survey, popups, tutorial). Add `SYNFUEL` after `HYDROGEN`.
- [ ] **Step 2:** `SaveFile` maps a `"FUEL"` key to HYDROGEN when reading any resource map (one helper, `resourceNamed(String)`). The v1 fixture test keeps passing.
- [ ] **Step 3:** Regenerate `production-parity.txt` and verify it mechanically: `sed 's/ FUEL / HYDROGEN /'` on the old fixture plus an inserted `SYNFUEL 0.000000000000e+00` line after each HYDROGEN line must equal the new file byte for byte. Commit `refactor(sim): FUEL is now HYDROGEN; add SYNFUEL`.

### Task 2: Fuels and the per-fuel tank

**Files:** create `sim/Fuels.java`, `FuelsTest`; modify `Ship`, `TransitPhase`, `CommandPhase`, `SaveFile` (tank only).

- [ ] **Step 1: Failing tests** (design §8.1 `FuelsTest`).
- [ ] **Step 2: Implement.**

```java
/** The resources ships burn, best first, and what a unit of each is worth in fuel value. */
public final class Fuels {
    public static final List<Resource> ALL = List.of(Resource.HYDROGEN, Resource.SYNFUEL);
    public static final double SYNFUEL_VALUE = 0.5;
    public static double value(Resource r);                          // 1.0, 0.5, else 0
    public static double valueOf(Map<Resource, Double> amounts);     // Σ amount × value over ALL
    /** Moves up to {@code value} of fuel from {@code from} into {@code to}, in ALL order; returns the value moved. */
    public static double transfer(Map<Resource, Double> from, Map<Resource, Double> to, double value);
    /** Removes {@code value} of fuel from {@code tank}, in ALL order. */
    public static void burn(Map<Resource, Double> tank, double value);
}
```

`Ship.fuel` → `public final Map<Resource, Double> tank` (an `EnumMap` with both fuels at 0) and `fuelValue()`. Departure, top-up, orbit departure, retire and found use `Fuels`; each transfer logs one `Shipping.Kind.FUEL` ledger line per fuel moved. `fuelShortfall` counts `Fuels.valueOf(tank) + Fuels.valueOf(origin stock − manifest)` and names both fuels in its message. SaveFile writes `"tank"` and reads a legacy `"fuel"` as hydrogen.
- [ ] **Step 3:** `RefuelTest`, `TransitFuelCostTest`, `ExplorerTransitTest` pass with only the rename. Commit `feat(sim): ships burn fuel by value, hydrogen first`.

### Task 3: The Fuel plant

**Files:** `BuildingType`, `BuildingCatalog`, `SiteEconomy`, `BuildForecast`, `FuelPlantTest`.

- [ ] **Step 1: Failing tests** (design §8.1 `FuelPlantTest`).
- [ ] **Step 2: Implement.** `FUEL_PLANT_BIOMASS = 1.0`, `FUEL_PLANT_SYNFUEL = 1.0`, cost `BuildCost.of(20, 10, 4)`. In `SiteEconomy` handle FUEL_PLANT in a second pass after the main building loop, so farms have taken their biomass; it scales with `powerFactor` like other producers and reports `Limit` "Needs BIOMASS" when short. `BuildForecast` and `BuildingInfoPanel` get their cases. The parity fixture must not change (no scenario builds a fuel plant).
- [ ] **Step 3:** Commit `feat(sim): Fuel plant makes SYNFUEL from BIOMASS`.

### Task 4: Ships take time to build

**Files:** `ShipClass` (+ `buildDays`), `ShipState` (+ BUILDING), `Ship` (+ `buildDaysLeft`, `yardBuildingId`), create `ShipYards`, `EventKind` (+ SHIP_COMPLETED), `CommandPhase`, `ProductionPhase`, `GoalCatalog`, `ShipBuildTest`.

- [ ] **Step 1: Failing tests** (design §8.1 `ShipBuildTest`).
- [ ] **Step 2: Implement.**

```java
public final class ShipYards {
    /** The operational shipyard at {@code s} with no ship being built, highest level first; null if none. */
    public static Building freeYard(World w, Site s);
    /** Calendar days a {@code c} takes at a level-{@code level} shipyard: ceil(buildDays / level). */
    public static int days(ShipClass c, int level);
    /** "Every shipyard at Earth Hub is busy (Hauler 3, 12 days left)", or null. */
    public static String whyNotBuild(World w, Site s);
    /** The ship {@code yard} at {@code s} is building, or null. */
    public static Ship building(World w, Site s, Building yard);
}
```

`applyBuildShip` takes `freeYard`, creates the ship with `state = BUILDING`, `buildDaysLeft = shipClass.buildDays()`, `yardBuildingId = yard.id`. `applyDispatchShip` rejects BUILDING ships; `applyRetireShip` on one just removes it; `applyDemolish` rejects a shipyard with a ship on it. `ProductionPhase` step 9 calls `advanceShipBuilds(w, site)` after building construction: for each BUILDING ship docked at the site, find its yard; skip if missing or damaged; `buildDaysLeft -= yard.level`; at ≤ 0 set IDLE, `buildDaysLeft = 0` and emit SHIP_COMPLETED. *Fleet of 10* counts ships whose state isn't BUILDING. Check every `switch` on `ShipState` (the UI and tutorial use `==` today, so only new code paths need care).
- [ ] **Step 3:** Fix tests and helpers that build a ship and dispatch it right away: add a `testutil` helper `Ships.buildNow(World, …)` that creates a ready ship, and use it where a test isn't about building. Commit `feat(sim): ships take days to build at a shipyard`.

### Task 5: Save schema v5

- [ ] **Step 1: Failing tests.** v5 round-trips a BUILDING ship (days left and yard id) and a mixed tank; a v4 save string with `"fuel": 30` and a FUEL stockpile loads as 30 hydrogen and a ready ship.
- [ ] **Step 2: Implement** `SCHEMA_VERSION = 5`, `optInt` for the two ship fields. Commit `feat(save): schema v5 saves ship builds and fuel tanks`.

### Task 6: Debug, tutorial, world start

- [ ] `WorldGenerator` and `TutorialScenario` start with 400 HYDROGEN (done by Task 1's rename; check the comment text).
- [ ] Debug *Finish construction* also sets every BUILDING ship to 0 days left and completes it.
- [ ] Tutorial: "Build a colonizer" completes when a colonizer exists in any state; its text adds the build time; new auto step `wait-colonizer` "Wait for your colonizer" completes when a COLONIZER is not BUILDING (or later progress already happened). `TutorialScriptTest` updated.
- [ ] Commit `feat(tutorial): wait for the colonizer to be built`.

## Part B — The UI

### Task 7: Build ship dialog

- [ ] Extract `BuildShipDialog.classLabel(World, Site, ShipClass)` ("HAULER · 20 days") and `statusLine(World, Site)` for tests. Disable OK when `ShipYards.whyNotBuild` is non-null. Commit `feat(ui): build ship dialog shows build days and busy shipyards`.

### Task 8: Ship cards, detail panel, system map

- [ ] `ColonyListPanel` ship card: dimmed sprite, progress bar, "Building · N days left" (N in calendar days at the yard's level). Empire strip FUEL = `Fuels.valueOf` over all stockpiles, tooltip with the split.
- [ ] `DetailPanel.renderShip`: under-construction text and *Cancel build* only; the tank line lists each non-zero fuel and the value; explorer "Tank: 64 / 100 (40 H₂ + 48 SYN)".
- [ ] `SystemMapPanel` skips BUILDING ships in docked-ship drawing and hit-testing.
- [ ] All new right-column text wraps within 330 px (PR #28). Commit `feat(ui): ships under construction and two-fuel tanks`.

### Task 9: Dispatch popup

- [ ] `DispatchShipDialog` popup uses the new shortfall text; "bring more FUEL here" becomes "bring hydrogen or synfuel here, or build a Fuel plant". Commit `feat(ui): fuel popup names both fuels`.

## Part C — Drivers, docs, PR

### Task 10

- [ ] `PlayTestDriver`, `StartupDriver`, `SurveyDriver`, `ShipGraphicsDriver`: after ordering a ship, run the clock (or use the debug finish) until it's ready. `ContinuousPlayTester`: wait on BUILDING ships; industrialist and expander styles build a fuel plant at Earth Hub once they have a second farm.
- [ ] Screenshots: a ship card mid-build, the build dialog, the Fuel plant preview, a two-fuel tank. Save them under `/mnt/project-files/plan-10-ships-fuel/`.
- [ ] Update the design doc status line and the game-design spec §3.4 and §3.5.
- [ ] Full `xvfb-run … ./gradlew test`, open the implementation PR, drive CI green.
