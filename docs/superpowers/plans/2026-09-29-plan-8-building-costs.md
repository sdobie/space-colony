# Space Colony — Plan 8: Building Costs and Construction

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make building a decision. Buildings cost METAL and COMPONENTS from the colony's stockpile, take days to finish, can be upgraded to level 5 within a 10-slot colony, and can be repaired, cancelled or demolished. A new Factory makes COMPONENTS from METAL and SILICATE.

**Architecture:** Costs sit on `BuildingSpec` as a `BuildCost` record. The rules (slots, level cap, upgrade/repair/refund pricing, and "why not" messages) live in one headless class, `sim.Construction`, which `CommandPhase` enforces and the UI calls for its explanations. `Building` gains a stable per-site `id` and a `daysLeft` countdown that `ProductionPhase` advances as a new last step; a level-0 building is under construction. Four new commands address buildings by id. The build dialog shows cost, time and slots; each detail-panel building row gets a menu for Upgrade, Repair, Cancel and Demolish. Save schema goes to v3.

**Tech Stack:** Java 25, Gradle 9.0.0 via wrapper, JUnit 5, Swing. No new dependencies.

**Spec reference:** `docs/superpowers/specs/2026-09-29-plan-8-building-costs-design.md` (sections 1–10).

**Out of scope:** ship costs; COMPONENTS upkeep; construction techs; the other Plan 8 balance items (fuel outside gas giants, the farm's double brownout, cargo overfill). See the design doc §1 and §9.

---

## Context

Plans 1–7 are on `main` (last: PR #23, 2026-09-29). About 398 tests. GUI tests that build a frame need `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test` and skip themselves when headless. CI runs the full suite under xvfb on JDK 25.

Key facts about the existing code that this plan relies on:

- `BuildingCatalog` holds per-level rates and `BuildingSpec(type, displayName, summary, powerDrawPerLevel, rates)`; `all()` is in enum order, and the build dialog lists it in that order.
- `Building(type, level)` has public `type`, `level`, `enabled`; no id. `Site.buildings` is a plain `ArrayList` that main code and many tests `add` to directly.
- `CommandPhase.applyBuildBuilding` appends `new Building(type, 1)` with no checks beyond the site existing. `Command` is a sealed interface and `CommandPhase.apply` switches over it exhaustively.
- `SiteEconomy.run` scales every rate by `bd.level`; the power loop adds a `FlowLine` for every enabled building. Outcomes carry `Limit` (DISABLED, NEEDS_TECH, NO_INPUT, SHORT_INPUT, BROWNOUT, NO_YIELD, LOW_YIELD).
- `ProductionPhase.run` per site: cap → `SiteEconomy.run` → cache → clip → `lastDay` → morale → population → step 8 re-enables every power plant.
- `EventPhase`: METEOR_STRIKE and EQUIPMENT_FAILURE set `enabled = false` on a random building; nothing re-enables non-power buildings.
- Shipyard checks (`b.type == SHIPYARD && b.enabled`): `CommandPhase.applyBuildShip`, `DetailPanel.renderSite`, `BuildForecast.facts`.
- `ProductionParityTest` and `EconomyScenario` enqueue one `BuildBuildingCommand` per `BuildingType.values()` at tick 0; the fixture is `src/test/resources/parity/production-parity.txt`.
- `SaveFile.SCHEMA_VERSION = 2`, `MIN_READABLE_VERSION = 1`; buildings write `type`, `level`, `enabled`.
- Earth Hub starts with HABITAT, FARM, MINE, POWER_PLANT, SHIPYARD, 100 METAL, 50 COMPONENTS. Its ore yield is 0.19 / 0.45 / 0.30 on seeds 42 / 7777 / 12345.
- Tests that use `BuildBuildingCommand` today: `CommandTest`, `DeterminismTest`, `EngineTest`, `TutorialScriptTest`, `TutorialTargetsTest`, `BuildForecastTest`, `EconomyScenario`.

---

## File Structure

### Create

```
src/main/java/spacecolony/
├── sim/
│   ├── BuildCost.java
│   ├── Construction.java
│   └── commands/
│       ├── UpgradeBuildingCommand.java
│       ├── RepairBuildingCommand.java
│       ├── CancelConstructionCommand.java
│       └── DemolishBuildingCommand.java
└── ui/
    └── BuildingMenu.java

src/test/java/spacecolony/
├── sim/
│   ├── BuildCostTest.java
│   ├── ConstructionTest.java
│   ├── ConstructionCommandTest.java
│   └── ConstructionPacingTest.java
└── ui/
    └── BuildingMenuTest.java
```

### Modify

- `sim/BuildingType.java`, `sim/BuildingSpec.java`, `sim/BuildingCatalog.java`, `sim/Building.java`, `sim/Site.java`, `sim/EventKind.java`, `sim/commands/Command.java`
- `sim/economy/SiteEconomy.java`, `sim/economy/Limit.java`, `sim/economy/BuildForecast.java`
- `sim/phases/CommandPhase.java`, `sim/phases/ProductionPhase.java`
- `save/SaveFile.java`, `world/WorldGenerator.java`, `debug/DebugActions.java` (+ `DebugMenu.java`)
- `ui/BuildingInfoPanel.java`, `ui/dialogs/BuildBuildingDialog.java`, `ui/DetailPanel.java`, `ui/ResourceLedgerPanel.java`, `ui/dialogs/BuildShipDialog.java` (shipyard check)
- `tutorial/TutorialScript.java`
- Tests: `ProductionParityTest`, `EconomyScenario`, `CommandTest`, `DeterminismTest`, `EngineTest`, `BuildingCatalogTest`, `SiteEconomyTest`, `BuildForecastTest`, `SaveFileSchemaTest`, `SaveFileLoadTest`, `BuildBuildingDialogTest`, `PanelSmokeTest`, `TutorialScriptTest`, `TutorialTargetsTest`, `playtest/PlayTestDriver.java`, `playtest/StartupDriver.java`
- Docs: the design doc's status line; game-design spec §3.5 and §3.6 get a pointer to `Construction` and the Factory.

---

## Tasks

Tasks 1–9 are Part A: the sim (headless, test-first). Tasks 10–14 are Part B: the UI. Tasks 15–16 are Part C: drivers, docs, PR.

Every task ends with `./gradlew test` green (under `xvfb-run` for tasks that touch frames) and a commit in the existing `feat(<pkg>): …` / `test(<pkg>): …` / `refactor(<pkg>): …` style. **`production-parity.txt` must not change in any task.** If it does, the change moved a production number and is wrong.

### Task 0: Branch

- [ ] **Step 1: Create the branch from the latest `main`**

```bash
git checkout main && git pull
git checkout -b claude/plan-8-building-costs-<suffix>
```

---

## Part A — The sim

### Task 1: Building ids and direct placement in the parity scenarios

**Files:** Modify `sim/Building.java`, `sim/Site.java`, `world/WorldGenerator.java`, `sim/phases/CommandPhase.java` (colony founding and build only), `test/.../ProductionParityTest.java`, `test/.../EconomyScenario.java`.

This makes the parity scenarios independent of `BuildBuildingCommand` before that command starts charging, so the fixture keeps guarding the production math.

- [ ] **Step 1: Building fields.**

```java
public class Building {
    public final BuildingType type;
    public int level;
    public boolean enabled;
    /** Unique within its site; 0 until Site.addBuilding assigns one. */
    public int id;
    /** Days until the current construction step finishes; 0 when idle. */
    public int daysLeft;

    public Building(BuildingType type, int level) { ... enabled = true; }

    public boolean isOperational() { return enabled && level > 0; }
    public boolean isUnderConstruction() { return daysLeft > 0; }
}
```

- [ ] **Step 2: Site helpers.** `addBuilding(Building b)` sets `b.id = nextBuildingId()` when `b.id == 0` and appends; `nextBuildingId()` is max id + 1 (1 for an empty list); `findBuilding(int id)` returns the building or null. Tests may keep calling `buildings.add` directly; those buildings keep id 0 and aren't addressable by the new commands, which is fine for tests that don't use them.
- [ ] **Step 3:** `WorldGenerator` and `applyBuildSite` (the free Habitat) use `addBuilding`. `applyBuildBuilding` uses `addBuilding(new Building(type, 1))` (still free and instant here).
- [ ] **Step 4: Parity scenarios place buildings directly.** In `ProductionParityTest` and `EconomyScenario`, replace the tick-0 `for (BuildingType t : BuildingType.values()) enqueue(new BuildBuildingCommand(HUB, t))` with, before the first tick, `for (BuildingType t : List.of(HABITAT, FARM, MINE, REFINERY, POWER_PLANT, SHIPYARD, RESEARCH_LAB)) hub.addBuilding(new Building(t, 1));`. The commands used to apply in tick 1's command phase, before any production, and appended in this order, so the numbers are identical.
- [ ] **Step 5:** `./gradlew test` (parity must pass with the fixture untouched). Commit `refactor(sim): buildings get per-site ids; parity scenarios place buildings directly`.

### Task 2: BuildCost and catalog costs

**Files:** Create `sim/BuildCost.java`, `test/.../sim/BuildCostTest.java`. Modify `sim/BuildingSpec.java`, `sim/BuildingCatalog.java`, `test/.../sim/BuildingCatalogTest.java`.

- [ ] **Step 1: Failing tests.** `BuildCostTest`: `affordable` true at exactly the cost and false 0.5 short; `shortfall` lists only the missing resources with the missing amounts; `scaled(1.5)` of 15 METAL / 5 COMPONENTS is 23 / 8 (rounded up per resource) with days unchanged; `describe()` is "15 METAL, 5 COMPONENTS · 3 days". `BuildingCatalogTest`: every type has a cost with METAL > 0, COMPONENTS > 0 and days > 0; the farm costs 15 / 5 / 3.
- [ ] **Step 2: Implement.** `BuildCost(Map<Resource, Double> resources, int days)` with an unmodifiable `EnumMap` copy, and the methods in design doc §3.1. `BuildingSpec` gains a trailing `BuildCost cost`; `BuildingCatalog.add` takes METAL, COMPONENTS and days, with the design doc §3.1 table as constants (`FARM_COST_METAL = 15`, …) or inline in each `add` call; follow the catalog's existing style of named constants for anything the sim reads.
- [ ] **Step 3:** Commit `feat(sim): buildings have a METAL and COMPONENTS cost and a build time`.

### Task 3: The factory

**Files:** Modify `sim/BuildingType.java`, `sim/BuildingCatalog.java`, `sim/economy/SiteEconomy.java`, `ui/DetailPanel.java` (`buildingResult`'s switch, if it becomes non-exhaustive), `test/.../economy/SiteEconomyTest.java`, `test/.../sim/BuildingCatalogTest.java`.

- [ ] **Step 1: Failing tests.** A site with one factory, power to spare, 10 METAL and 10 SILICATE makes 0.5 COMPONENTS and uses 0.5 of each; with 0.25 SILICATE it makes 0.25 and reports `SHORT_INPUT(SILICATE)`; with no METAL it reports `NO_INPUT(METAL)` and makes nothing; at `powerFactor` 0.5 (one plant, enough consumers) it makes 0.25, not 0.125. Catalog: FACTORY draws 2.0 and has METAL 0.5 and SILICATE 0.5 inputs and COMPONENTS 0.5 output.
- [ ] **Step 2: Implement.** `FACTORY` after `REFINERY` in the enum. Catalog constants `FACTORY_METAL = 0.5`, `FACTORY_SILICATE = 0.5`, `FACTORY_COMPONENTS = 0.5`, summary per design doc §3.2, cost 30 / 5 / 5. `SiteEconomy` case:

```java
case FACTORY -> {
    double metalAsked = bd.level * BuildingCatalog.FACTORY_METAL * powerFactor;
    double siAsked = bd.level * BuildingCatalog.FACTORY_SILICATE * powerFactor;
    // Take only what the scarcer input can match, so nothing is wasted.
    double share = Math.min(1.0, Math.min(ratio(stock, Resource.METAL, metalAsked), ratio(stock, Resource.SILICATE, siAsked)));
    double metalUsed = consume(stock, report, src, Resource.METAL, metalAsked * share, bd.level * BuildingCatalog.FACTORY_METAL);
    consume(stock, report, src, Resource.SILICATE, siAsked * share, bd.level * BuildingCatalog.FACTORY_SILICATE);
    produce(stock, report, src, Resource.COMPONENTS,
        bd.level * BuildingCatalog.FACTORY_COMPONENTS * powerFactor * share,
        bd.level * BuildingCatalog.FACTORY_COMPONENTS);
    // limit: NO_INPUT / SHORT_INPUT on the scarcer input, else BROWNOUT
}
```

`ratio` is `asked <= 0 ? 1 : have / asked`. The limit uses the scarcer input's name.
- [ ] **Step 3:** Parity unchanged (no factories in the scenarios). Commit `feat(sim): factories assemble components from metal and silicate`.

### Task 4: Construction rules

**Files:** Create `sim/Construction.java`, `test/.../sim/ConstructionTest.java`.

- [ ] **Step 1: Failing tests** (design doc §8.1 `ConstructionTest`): `slotsUsed` counts level-0 buildings; farm upgrade costs are L2 15/5, L3 23/8, L4 30/10, L5 38/13; `invested` of a level-3 farm is 53/18 and `demolishRefund` 26/9; `cancelRefund` of a level-0 farm is 15/5 and of a farm upgrading to L3 is 23/8; `repairCost` of a research lab is 5/3. Messages: `whyNotBuild` with 10 buildings is "No free building slot (10 of 10)"; with 8 METAL for a mine is "Need 12 more METAL (have 8 of 20)"; with enough is null. `whyNotUpgrade` gives "Already level 5", "Under construction", "Damaged: repair it first". `whyNotRepair` on an enabled building is "Not damaged", on a power plant "Power plants come back on their own the next day". `whyNotDemolish` on a building with `daysLeft > 0` is "Under construction: cancel it instead".
- [ ] **Step 2: Implement** per design doc §3.7. Shortfall messages name the first missing resource in enum order; when several are short, append "and N more COMPONENTS". Refund maps omit zero entries.
- [ ] **Step 3:** Commit `feat(sim): construction rules for slots, upgrades, repairs and refunds`.

### Task 5: Paying for new buildings, and construction progress

**Files:** Modify `sim/phases/CommandPhase.java`, `sim/phases/ProductionPhase.java`, `sim/economy/SiteEconomy.java`, `sim/economy/Limit.java`, `sim/EventKind.java`, the shipyard checks; create `test/.../sim/ConstructionCommandTest.java`. Update `CommandTest`, `EngineTest`, `DeterminismTest`, `TutorialTargetsTest`, `BuildForecastTest` where they build via command and expect an instant level-1 building (give the site stock and tick past the build time, or place directly).

- [ ] **Step 1: Failing tests.** Build pays the exact cost and adds a level-0 building with `daysLeft == 3` for a farm; short stock is rejected with the `whyNotBuild` text as a `COMMAND_REJECTED` event and the stock is untouched; an 11th building is rejected. A farm ordered before tick 1 is level 1 after tick 3 (exactly one `BUILDING_COMPLETED` event, text "Farm finished at Earth Hub") and first adds FOOD on tick 4. A level-0 building draws no power (`report.powerUsed` unchanged) and its outcome is `CONSTRUCTING`. A shipyard under construction rejects `BuildShipCommand`.
- [ ] **Step 2: Implement.**
  - `applyBuildBuilding`: `whyNotBuild` → reject; else subtract `cost.resources()` from the stockpile, `addBuilding(new Building(type, 0))` with `daysLeft = cost.days()`.
  - `SiteEconomy`: in the power loop, `if (bd.level == 0) continue;` after the enabled check. In the production loop, after the disabled check, `if (bd.level == 0) { outcome(..., true, 0.0, Limit.CONSTRUCTING, null); continue; }`. `Limit.CONSTRUCTING` goes last in the enum.
  - `ProductionPhase` step 9, after step 8: for each building with `enabled && daysLeft > 0`: `daysLeft--`; at 0, `level++` and emit `BUILDING_COMPLETED` (INFO, site id) with "<Name> finished at <site>" when the new level is 1, else "<Name> upgraded to L<n> at <site>".
  - Replace the three shipyard checks with `isOperational()` (and `BuildShipDialog` if it checks too).
- [ ] **Step 3:** `./gradlew test`; parity still passes. Commit `feat(sim): new buildings cost resources and take days to finish`.

### Task 6: Upgrade, repair, cancel, demolish commands

**Files:** Create the four command records; modify `sim/commands/Command.java`, `sim/phases/CommandPhase.java`; extend `ConstructionCommandTest`.

- [ ] **Step 1: Failing tests** (design doc §8.1): upgrade pays, keeps L1 output while `daysLeft > 0`, then doubles; repair pays and the building produces in the same tick; cancel of a new building refunds and removes it, of an upgrade refunds and leaves `level` and clears `daysLeft`; demolish refunds half and frees the slot; a demolish refund at a full stockpile stops at the cap; two queued demolish commands on one colony remove the two intended buildings; unknown ids are rejected with "No such building"; a damaged building under construction doesn't count down.
- [ ] **Step 2: Implement** each `apply…` as "find site and building, `whyNot…` → reject, pay or refund, mutate". Refunds use a `refund(site, map)` helper that adds `min(amount, cap − stock)` per resource. Add the four records to `Command`'s `permits` and the `apply` switch.
- [ ] **Step 3:** Commit `feat(sim): upgrade, repair, cancel and demolish buildings`.

### Task 7: Forecasts know about costs and upgrades

**Files:** Modify `sim/economy/BuildForecast.java`, `test/.../economy/BuildForecastTest.java`.

- [ ] **Step 1: Failing tests.** `BuildForecast.of` carries `cost` (the catalog's) and `blocker` (`whyNotBuild`, or null). A factory forecast at Earth Hub shows a positive COMPONENTS delta and a negative METAL delta. `BuildForecast.ofUpgrade(w, s, id)` for a level-1 farm shows FOOD delta +1.5 × tech multiplier and `after.powerUsed − before.powerUsed == 2.0`. Neither mutates the world (reuse the existing JSON-equality check). A forecast at a colony with a level-0 building treats it as not yet working in both runs.
- [ ] **Step 2: Implement.** Add `BuildCost cost` and `String blocker` components to the record. `ofUpgrade` copies the building list, replacing the target with a fresh `Building(type, level + 1)` carrying the same `enabled`, and runs the same before/after. Facts: habitat upgrade "Population cap 300 → 400"; lab upgrade "Research 1.0 → 2.0 points a day". Warnings are derived exactly as for a new building.
- [ ] **Step 3:** Commit `feat(sim): forecasts show cost and preview upgrades`.

### Task 8: Save schema v3

**Files:** Modify `save/SaveFile.java`; tests `SaveFileSchemaTest`, `SaveFileLoadTest` (+ a small v2 fixture if the tests use resource files; otherwise a hand-written v2 JSON string).

- [ ] **Step 1: Failing tests.** v3 round-trips `id` and `daysLeft` for a level-0 building and an upgrading one; a v2 document loads with ids 1..n in list order and `daysLeft == 0`; `SCHEMA_VERSION == 3`.
- [ ] **Step 2: Implement.** Write `id` and `daysLeft`. On read, if absent, assign ids by position and `daysLeft = 0`. Bump `SCHEMA_VERSION` to 3.
- [ ] **Step 3:** `DeterminismTest`: extend its command script with a paid build, an upgrade, a repair (after a forced meteor via `EventPhase.applyForced`) and a demolish, so save/load/replay covers the new state. Commit `feat(save): schema v3 saves building ids and construction`.

### Task 9: Balance guard

**Files:** Create `test/.../sim/ConstructionPacingTest.java`.

- [ ] **Step 1:** For seeds 42, 7777, 12345, random events off: at tick 0 order a research lab, refinery, factory and power plant at Earth Hub via commands; every tick after, if the mine isn't level 2 or upgrading and `whyNotUpgrade` is null, order the upgrade. Advance up to 200 ticks. Assert all four are level 1 and the mine is level 2.
- [ ] **Step 2:** If a seed fails, tune the catalog costs (design doc §3.1 table) rather than the test, and update the design doc's table to match. Commit `test(sim): the opening build order finishes within 200 days`.

---

## Part B — The UI

### Task 10: Build dialog cost block

**Files:** Modify `ui/BuildingInfoPanel.java`, `ui/dialogs/BuildBuildingDialog.java`, `test/.../ui/BuildBuildingDialogTest.java`.

- [ ] **Step 1: Failing tests.** The list has 8 entries including "Factory". With Earth Hub's METAL set to 0, selecting Farm disables `build.ok` and `build.reason` reads "Need 15 more METAL (have 0 of 15)"; restoring stock enables it and *Build* enqueues `BuildBuildingCommand(site, FARM)`. `build.cost` contains "15 METAL" and "3 days".
- [ ] **Step 2: Implement** per design doc §4: cost lines coloured by affordability with "have N", build time "N days, ready in about N+1", slots "N of 10 used". A `JLabel build.reason` beside the buttons in `WARNING`. Unaffordable list entries render with `FOREGROUND_DIM`. The forecast table header becomes "Once built, per day".
- [ ] **Step 3:** Commit `feat(ui): build dialog shows cost, build time and slots`.

### Task 11: Building rows

**Files:** Modify `ui/DetailPanel.java`, `ui/ResourceLedgerPanel.java`, `test/.../ui/PanelSmokeTest.java`.

- [ ] **Step 1:** Header "Buildings  N of 10 slots". Row label per design doc §5.1: level-0 "Farm   building, 3 days left" (dim) or "building paused: damaged" (`WARNING`); upgrading "Mine L1 → L2   <output>  (upgrading, 3 d)"; damaged "damaged: repair 5 METAL, 3 COMPONENTS" (`WARNING`); a disabled power plant "offline today". `limitText`: `CONSTRUCTING → "under construction"`, `DISABLED → "damaged"`.
- [ ] **Step 2:** `PanelSmokeTest`: a site with a level-0 farm, an upgrading mine, a damaged lab and a factory paints; header text "6 of 10 slots" (adjust to the fixture's count).
- [ ] **Step 3:** Commit `feat(ui): building rows show construction, upgrades and damage`.

### Task 12: Building menu

**Files:** Create `ui/BuildingMenu.java`, `test/.../ui/BuildingMenuTest.java`; modify `ui/DetailPanel.java`.

- [ ] **Step 1: Failing tests** (design doc §8.2): items offered per state; disabled items carry the `whyNot…` tooltip; enabled Upgrade's tooltip contains the forecast's FOOD delta for a farm; activating Upgrade enqueues `UpgradeBuildingCommand(site, id)`; Cancel enqueues without a dialog. Demolish's confirmation is behind a package-private `Confirm` hook the test replaces.
- [ ] **Step 2: Implement.** `BuildingMenu.create(Engine, Site, Building)` returns a `JPopupMenu` with items named `building.upgrade`, `building.repair`, `building.cancel`, `building.demolish`. Each row in `DetailPanel` gets a trailing flat `⋯` `JButton` named `detail.building.<id>` that shows the menu under itself. Rows move from bare `JLabel`s to a small `BorderLayout` row panel (label centre, button east).
- [ ] **Step 3:** Commit `feat(ui): upgrade, repair, cancel and demolish from each building row`.

### Task 13: Debug "Finish construction"

**Files:** Modify `debug/DebugActions.java`, `debug/DebugMenu.java`; a test alongside the existing debug action tests.

- [ ] **Step 1:** Menu item enabled only while paused with a site selected; sets every `daysLeft > 0` at that site to 1. Test: after the action and one tick, the site has no building under construction.
- [ ] **Step 2:** Commit `feat(debug): finish construction at the selected colony`.

### Task 14: Tutorial text

**Files:** Modify `tutorial/TutorialScript.java`, `tutorial/TutorialScriptTest.java`.

- [ ] **Step 1:** `build-lab` text and the following research step per design doc §7. The `build-lab` predicate (`hasBuilding` at any level) stays.
- [ ] **Step 2:** `TutorialScriptTest` still finds "Build" in the dialog. Commit `feat(tutorial): explain building costs and build time`.

---

## Part C — Verification and PR

### Task 15: Play-test drivers

**Files:** Modify `playtest/PlayTestDriver.java`, `playtest/StartupDriver.java`.

- [ ] **Step 1:** `PlayTestDriver`: after the build dialog step, screenshot the cost block as `build/playtest/08-build-cost.png`; after a few ticks, open the first building's `⋯` menu and screenshot `build/playtest/08-building-menu.png`. Any step that waited for a building to *work* now advances past its build time first.
- [ ] **Step 2:** `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew startupPlayTest` passes. Commit `test(playtest): screenshot building costs and the building menu`.

### Task 16: Docs, full run, PR

- [ ] **Step 1:** Design doc status → "Implemented (Plan 8)" plus any deviations (tuned costs from Task 9 in particular). Game-design spec §3.5: "Costs, slots and upgrades: see `Construction` and `BuildingCatalog`"; §3.6: FACTORY makes COMPONENTS.
- [ ] **Step 2:** `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test` all green; test count about 444. `production-parity.txt` unchanged (`git diff main -- src/test/resources/parity` is empty).
- [ ] **Step 3:** Push, open the PR against `main` with screenshots of the build dialog's cost block and a building menu, drive CI green.

---

## Self-review notes

- **Parity is the tripwire.** Task 1 moves the scenarios off commands before anything charges; after that the fixture must never change. A factory or level-0 building in a parity scenario would be a scenario change, not a fix.
- **Level 0 is "not built yet".** Every place that multiplies by `level` already gives 0, but the power loop would add a zero-energy line and the outcome would read as a healthy building; Task 5 skips it explicitly. `PopCapBreakdown` and `ResearchPhase` need no change (0 × rate).
- **Ids, not indexes.** `FlowSource.Building` and `BuildingOutcome` keep list indexes (they describe one day's report); only commands use ids.
- **Refunds and the cap.** Refunds never exceed free room, so the production clip never sees command-phase overflow and `LedgerReconciliationTest` stays exact.
- **Power plants re-enable daily.** Don't offer Repair for them; step 8 already restores them.
- **Tests that built for free.** Several tests used `BuildBuildingCommand` as a shortcut; switch those to `addBuilding` unless the test is about the command.
- **The play-tester thread** will see rejections until its script checks `Construction.whyNotBuild`; tell it when this merges.
