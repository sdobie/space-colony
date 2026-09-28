# Space Colony — Plan 7: Resource Management

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the economy legible. Show players what a building does, and what it would do at this colony, before they build it; and show, inside a colony, what is producing and consuming each resource, which buildings are starved, whether power is short, and how long each stock lasts.

**Architecture:** Building rates move from `ProductionPhase` literals into a `BuildingCatalog`. A new headless package `spacecolony.sim.economy` holds `SiteEconomy` (one site's day as a pure function over a stockpile map, returning an itemised `DayReport`), `BuildForecast` (the same function run on copies, with and without a new building) and `Outlook` (days to empty or full). `ProductionPhase` calls `SiteEconomy` on the live stockpile and stores the report on `Site.lastDay`; `TransitPhase` appends shipping lines to it. The UI gets a rewritten build dialog with an info card and forecast, and a `ResourceLedgerPanel` in the detail panel with expandable per-resource breakdowns.

**Tech Stack:** Java 25, Gradle 9.0.0 via wrapper, JUnit 5, Swing. No new dependencies.

**Spec reference:** `docs/superpowers/specs/2026-09-28-plan-7-resource-management-design.md` (sections 1–9).

**Out of scope:** building costs, upgrades, demolition; any balance change; an empire-wide summary; history beyond the last day. See the design doc §1.

---

## Context

Plans 1–6 are on `main` (last: PR #18, 2026-09-28). About 353 tests. GUI tests that build a frame need `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test` and skip themselves when headless. CI runs the full suite under xvfb on JDK 25.

Key facts about the existing code that this plan relies on:

- `ProductionPhase.run(World)` loops bodies → sites and, per site: `recomputeCap`; power (plants make `10.0 * level / max(0.05, r*r) * powerPlantMultiplier`, every other enabled building demands `2.0 * level`); resets `productionRateCache`; population eats `pop * 0.01` FOOD and `pop * 0.005` WATER; then each enabled building in list order (MINE, FARM, REFINERY; the others are no-ops); clip to `stockpileCap` and 0; morale; population; re-enable power plants.
- `consume(s, r, amt)` takes `min(have, amt)` and merges `-taken` into the cache; `produce` adds to stock and cache. The cache is gross of clipping.
- Readers of `productionRateCache`: `DetailPanel` (Net / day), `GoalCatalog` (food goal), `ProductionTest`, `CommandTest` (exact arithmetic on METAL and ORE).
- `ResearchPhase.pointsPerTick` sums `level * 1.0 * researchLabMultiplier`. `PopCapBreakdown` adds `level * 100` per enabled HABITAT.
- `TransitPhase.loadingAndUnloading` (phase 5, after production) moves stock for LOADING (manifest, then fuel shortfall draw, or on abort returns cargo) and UNLOADING (capped at room since PR #16).
- `BuildBuildingCommand(siteId, type)` appends `new Building(type, 1)`; buildings are free.
- `BuildBuildingDialog.show(parent, engine, siteId)` is a `JOptionPane` with a `JComboBox<BuildingType>`. `PlayTestDriver` steps 2–3 and `StartupDriver`'s tutorial walk drive it by combo + "OK".
- `TutorialScriptTest.LABELS` includes "Build building..." and "RESEARCH_LAB"; step `build-lab` says "pick **RESEARCH_LAB** and press OK".
- `SaveFile` writes named fields, so a new `Site` field is not saved unless added there.
- Earth Hub starts with HABITAT, FARM, MINE, POWER_PLANT, SHIPYARD (power 10 made / 8 used at 1 AU), population 100, FOOD 200, WATER 200, METAL 100, COMPONENTS 50, FUEL 400, BIOMASS 100.

---

## File Structure

### Create

```
src/main/java/spacecolony/
├── sim/
│   ├── BuildingSpec.java
│   ├── BuildingCatalog.java
│   └── economy/
│       ├── FlowSource.java
│       ├── FlowLine.java
│       ├── Limit.java
│       ├── BuildingOutcome.java
│       ├── DayReport.java
│       ├── SiteEconomy.java
│       ├── Outlook.java
│       └── BuildForecast.java
└── ui/
    ├── BuildingInfoPanel.java
    └── ResourceLedgerPanel.java

src/test/java/spacecolony/
├── sim/
│   ├── ProductionParityTest.java
│   ├── BuildingCatalogTest.java
│   └── economy/
│       ├── SiteEconomyTest.java
│       ├── OutlookTest.java
│       ├── LedgerReconciliationTest.java
│       └── BuildForecastTest.java
└── ui/
    ├── BuildBuildingDialogTest.java
    ├── ResourceLedgerPanelTest.java
    └── ColonyListPanelTest.java   (or additions to an existing panel test)

src/test/resources/parity/production-parity.txt
```

### Modify

- `sim/Site.java` (`lastDay`), `sim/PopCapBreakdown.java`, `sim/phases/ProductionPhase.java`, `sim/phases/ResearchPhase.java`, `sim/phases/TransitPhase.java`
- `ui/dialogs/BuildBuildingDialog.java` (rewrite), `ui/DetailPanel.java`, `ui/ColonyListPanel.java`
- `tutorial/TutorialScript.java`
- Tests: `tutorial/TutorialScriptTest.java`, `ui/PanelSmokeTest.java`, `playtest/PlayTestDriver.java`, `playtest/StartupDriver.java`
- Docs: the design doc's status line; game-design spec §3.5 gets a pointer to `BuildingCatalog` as the source of building rates.

---

## Tasks

Tasks 1–7 are Part A: the economy model (headless, test-first). Tasks 8–13 are Part B: the UI. Tasks 14–15 are Part C: drivers, docs, PR.

Every task ends with `./gradlew test` green (under `xvfb-run` for tasks that touch frames) and a commit in the existing `feat(<pkg>): …` / `test(<pkg>): …` / `refactor(<pkg>): …` style.

### Task 0: Branch

- [ ] **Step 1: Create the branch from the latest `main`**

```bash
git checkout main && git pull
git checkout -b claude/plan-7-resource-management-<suffix>
```

---

## Part A — The economy model

### Task 1: Production parity fixture (before any refactor)

**Files:** Create `src/test/java/spacecolony/sim/ProductionParityTest.java`, `src/test/resources/parity/production-parity.txt`.

This pins today's numbers so Tasks 2–6 can prove they changed nothing.

- [ ] **Step 1: Write the scenario.** For each seed in `{42, 7777, 12345}`: `WorldGenerator.generate(seed)`, `randomEventsEnabled = true`, then enqueue at tick 0: one `BuildBuildingCommand` per `BuildingType` at `site-earth-hub` (all seven), `QueueResearchCommand("basic-mining")`; at tick 300 `QueueResearchCommand("basic-farming")` if not active; at tick 50 build a HAULER `h1` and at tick 60 dispatch it to Earth Hub's own body with a 40-METAL manifest (reuse `SupplyTest`'s dispatch shape). Advance 2,000 ticks.
- [ ] **Step 2: Serialize the result** as sorted lines `seed site resource %.12e` for every stockpile entry, plus `seed site population N` and `seed site morale %.12e`, and `seed research %.12e`.
- [ ] **Step 3: Capture the fixture on unmodified `main` code.** A `main`-guarded helper in the test (`ProductionParityTest.main` writes the file) produces `production-parity.txt`; commit it. The `@Test` compares line by line and reports the first mismatch.
- [ ] **Step 4: Run** `./gradlew test --tests '*ProductionParityTest*'` → PASS. Commit `test(sim): pin production numbers before the economy refactor`.

### Task 2: BuildingCatalog

**Files:** Create `sim/BuildingSpec.java`, `sim/BuildingCatalog.java`, `test/.../sim/BuildingCatalogTest.java`. Modify `ProductionPhase`, `ResearchPhase`, `PopCapBreakdown`.

- [ ] **Step 1: Failing test.** Every `BuildingType` has a spec with a non-blank display name and summary; `POWER_PLANT.powerDrawPerLevel() == 0` and every other type `== 2.0`; `FARM` rates are BIOMASS 0.5 in, WATER 0.3 in, FOOD 1.5 out; `MINE` has ORE 2.0 and SILICATE 1.0 as `YIELD_OUTPUT`.
- [ ] **Step 2: Implement.**

```java
public record BuildingSpec(BuildingType type, String displayName, String summary,
                           double powerDrawPerLevel, List<Rate> rates) {
    public record Rate(Resource resource, double perLevel, Kind kind) {
        public enum Kind { INPUT, OUTPUT, YIELD_OUTPUT }
    }
}

public final class BuildingCatalog {
    // Rates are the numbers ProductionPhase used through Plan 6; ProductionParityTest pins them.
    public static final double POWER_DRAW = 2.0;
    public static final double POWER_PLANT_OUTPUT = 10.0;      // at 1 AU, ÷ r²
    public static final double MINE_ORE = 2.0, MINE_SILICATE = 1.0, MINE_GAS_FUEL = 2.0;
    public static final double FARM_BIOMASS = 0.5, FARM_WATER = 0.3, FARM_FOOD = 1.5;
    public static final double REFINERY_ORE = 1.5, REFINERY_METAL_PER_ORE = 0.8;
    public static final double REFINERY_ICE = 1.0, REFINERY_WATER_PER_ICE = 0.9;
    public static final double LAB_POINTS = 1.0;
    public static final int HABITAT_CAP = 100;

    public static BuildingSpec get(BuildingType t) { ... }
    public static List<BuildingSpec> all() { ... }   // enum order
}
```

Summaries (player-facing, one or two sentences):

| Type | Summary |
|---|---|
| HABITAT | "Housing. Each level raises the population cap by 100." |
| FARM | "Grows food from biomass and water. Without biomass it sits idle." |
| MINE | "Digs ore and silicate; output depends on what's in the ground here. At a gas giant, with Atmospheric Mining, it also pulls fuel from the air." |
| REFINERY | "Turns ore into metal and ice into water." |
| POWER_PLANT | "Solar power for every other building. Output falls with distance from the sun; if demand outruns it, farms, mines and refineries slow down." |
| SHIPYARD | "Lets this colony build ships." |
| RESEARCH_LAB | "Produces research points for the active tech." |

- [ ] **Step 3: Replace the literals** in `ProductionPhase` (`10.0`, `2.0 * bd.level`, `2.0`, `1.0`, `0.5`, `0.3`, `1.5`, `0.8`, `0.9`), `ResearchPhase.pointsPerTick` (`1.0`) and `PopCapBreakdown` (`100`) with the catalog constants. Keep each expression's operand order exactly (e.g. `bd.level * BuildingCatalog.MINE_ORE * y * powerFactor * ...`), so the doubles are bit-identical.
- [ ] **Step 4:** `./gradlew test` (parity must pass). Commit `refactor(sim): building rates live in BuildingCatalog`.

### Task 3: Economy records and Outlook

**Files:** Create `sim/economy/{FlowSource,FlowLine,Limit,BuildingOutcome,DayReport,Outlook}.java`, `test/.../economy/OutlookTest.java`.

- [ ] **Step 1: Records.**

```java
public sealed interface FlowSource {
    record Building(int index, BuildingType type, int level) implements FlowSource {}
    record Population(int people) implements FlowSource {}
    record Shipping(String shipId, String shipName, Kind kind) implements FlowSource {
        public enum Kind { LOADING, UNLOADING, FUEL, RETURNED }
    }
    record StorageFull() implements FlowSource {}
}

/** amount: signed, + made / − used. wanted: the same sign, at full power and full inputs. */
public record FlowLine(FlowSource source, Resource resource, double amount, double wanted) {
    public boolean shortfall() { return Math.abs(wanted) - Math.abs(amount) > 1e-9; }
}

public enum Limit { DISABLED, NEEDS_TECH, NO_INPUT, SHORT_INPUT, BROWNOUT, NO_YIELD, LOW_YIELD }

/** efficiency in [0,1]; limit null when running at full rate; limitResource set for input/yield limits. */
public record BuildingOutcome(int index, BuildingType type, int level, boolean enabled,
                              double efficiency, Limit limit, Resource limitResource) {}

public final class DayReport {
    // Mutable while SiteEconomy / ProductionPhase / TransitPhase build it; read-only to the UI.
    public double powerMade, powerUsed, powerFactor = 1.0;
    public boolean estimate;                 // true for a dry run shown after load
    final List<FlowLine> lines = new ArrayList<>();
    final List<BuildingOutcome> buildings = new ArrayList<>();
    public void add(FlowLine l);
    public List<FlowLine> lines();                        // unmodifiable view, insertion order
    public List<BuildingOutcome> buildings();
    public double net(Resource r);                        // sum of all lines for r, insertion order
    public double productionNet(Resource r);              // Building + Population lines only
    public List<FlowLine> linesFor(Resource r);           // producers (desc), then consumers (by magnitude desc)
}
```

(A mutable `DayReport` rather than a record: `TransitPhase` appends to the same report after production. The UI only calls the read methods.)

- [ ] **Step 2: Outlook, test-first.**

```java
public sealed interface Outlook {
    record Steady() implements Outlook {}
    record Empty() implements Outlook {}
    record Full() implements Outlook {}
    record EmptyIn(int days) implements Outlook {}
    record FullIn(int days) implements Outlook {}
    static Outlook of(double stock, double cap, double net) { ... }
    int MAX_DAYS = 999;
}
```

Tests: `of(0, 1000, -1)` → Empty; `of(1000, 1000, +1)` → Full; `of(100, 1000, -1)` → EmptyIn(100); `of(100, 1000, -0.3)` → EmptyIn(334) (ceil); `of(900, 1000, +2)` → FullIn(50); `of(500, 1000, 0.004)` → Steady; `of(500, 1000, -0.0001)` → Steady; `of(999_999, 10_000_000, -0.01)` → EmptyIn(999).

- [ ] **Step 3:** Commit `feat(sim): economy report records and Outlook`.

### Task 4: SiteEconomy (flows)

**Files:** Create `sim/economy/SiteEconomy.java`, `test/.../economy/SiteEconomyTest.java`. Modify `ProductionPhase`, `Site`.

- [ ] **Step 1: Move the day's arithmetic.** `SiteEconomy.run(World w, Body b, Site s, List<Building> buildings, Map<Resource, Double> stock)` contains `ProductionPhase` steps 1, 3, 4 and 5 moved verbatim, with these substitutions only:
  - `s.stockpile` → `stock`; `s.buildings` → `buildings`.
  - The power loop also calls `report.add(new FlowLine(new Building(i, type, level), ENERGY, ±x, ±x))`, and sets `powerMade`, `powerUsed`, `powerFactor` on the report.
  - `consume(s, r, amt)` → `consume(stock, report, source, r, amt, wanted)`, which does the same `min(have, amt)` and `stock.put(r, have - taken)` and adds `FlowLine(source, r, -taken, -wanted)`. `produce` likewise with `+amount, +wanted`.
  - `wanted` is the same expression without `powerFactor` and without input limitation: mine ORE `level * MINE_ORE * y * mult`; farm BIOMASS `level * FARM_BIOMASS`; farm WATER `level * FARM_WATER * waterDemandFactor`; farm FOOD `level * FARM_FOOD * mult`; refinery ORE `level * REFINERY_ORE`, METAL `level * REFINERY_ORE * REFINERY_METAL_PER_ORE * mult`, and the ICE/WATER pair likewise; population FOOD/WATER wanted = need.
  - Labs and habitats: add informational lines? **No.** They don't move resources. Their outcome (Task 5) carries their effect; the UI reads research and cap from the catalog.
- [ ] **Step 2: ProductionPhase calls it.**

```java
recomputeCap(s, w.tech);
DayReport day = SiteEconomy.run(w, b, s, s.buildings, s.stockpile);
for (Resource r : Resource.values()) s.productionRateCache.put(r, day.productionNet(r));
// 6. Stockpile clipping, now recorded.
for (Resource r : Resource.values()) {
    if (!r.isStockpileable()) continue;
    double cap = s.stockpileCap.getOrDefault(r, 1000.0);
    double cur = s.stockpile.getOrDefault(r, 0.0);
    if (cur > cap) { s.stockpile.put(r, cap); day.add(new FlowLine(new StorageFull(), r, cap - cur, 0.0)); }
    if (cur < 0) s.stockpile.put(r, 0.0);
}
s.lastDay = day;
updateMorale(...); updatePopulation(...); /* re-enable plants */
```

`productionNet` sums in insertion order, which is the order the old `merge` calls ran, so `productionRateCache` is bit-identical. `Site` gains `public DayReport lastDay; // not saved; null until the first tick after load`.

- [ ] **Step 3: Tests (SiteEconomyTest)** on a hand-built world (`World`, one body with a constant `ResourceYieldSampler`, one site): farm + plant with BIOMASS 0 → FOOD line `+0.0` wanted `+1.5`, BIOMASS line `0.0` wanted `-0.5`; plant at r = 2 AU with four consumers → `powerFactor == 2.5 / 8`; a disabled mine yields no lines; `run` on a copy leaves `s.stockpile` untouched; sum of lines per resource equals the change in the passed map.
- [ ] **Step 4:** `./gradlew test` (parity, `ProductionTest`, `CommandTest` unchanged). Commit `refactor(sim): SiteEconomy computes a site's day as an itemised report`.

### Task 5: Building outcomes and limits

**Files:** Modify `SiteEconomy`, `SiteEconomyTest`.

- [ ] **Step 1: Tests first.** Farm with no BIOMASS → `NO_INPUT, BIOMASS, efficiency 0`. Two farms, BIOMASS 0.7 → first full (`limit null`), second `SHORT_INPUT, BIOMASS, efficiency 0.4`. Brownout (factor 0.8) → every farm, mine and refinery `BROWNOUT, 0.8`. Mine where ORE yield is 0 → `NO_YIELD, ORE`; yield 0.2 → `LOW_YIELD, ORE, efficiency 1.0` (low yield isn't a shortfall against wanted, so efficiency stays 1, but the limit still explains the small number). Disabled → `DISABLED, enabled false, efficiency 0`. Gas-giant mine without `atm-mining` → `NEEDS_TECH, FUEL` only if no other limit applies. Habitat, shipyard, lab: `limit null` unless disabled or browned out (labs: brownout doesn't reduce research today, so labs only get `DISABLED`; keep sim behaviour, and the UI must not claim a slowdown the sim doesn't apply).
- [ ] **Step 2: Implement** `outcome(index, building, linesOfThisBuilding, powerFactor, yields...)` using the priority order in the design doc §3.2. Main output per type: FOOD for farms, ORE for mines, METAL for refineries (or WATER if the site has ICE but no ORE flow), ENERGY for plants. Efficiency = `actual / wanted` of the main output, 1.0 when wanted is 0.
- [ ] **Step 3:** Commit `feat(sim): each building reports how it did and what limited it`.

### Task 6: Shipping lines and the reconciliation invariant

**Files:** Modify `TransitPhase`. Create `test/.../economy/LedgerReconciliationTest.java`.

- [ ] **Step 1: Failing test.** Seed 7777, events on, the Task 1 command script plus a second hauler shuttling ICE from Earth Hub to a Mars colony (found it via the Plan 6 colonizer path) every time it's idle, for 500 ticks. Around each `advance`, snapshot every site's stockpile after phase 3 (use `Simulator.setPhaseObserver`: capture on `ARRIVALS` done) and compare with the stockpile after `DEPARTURES`: `after - before == lastDay.net(r)` within 1e-9 for every stockpileable resource. Fails now (no shipping lines).
- [ ] **Step 2: Implement.** In `loadingAndUnloading`, every `origin.stockpile.merge` / `dest.stockpile.merge` also adds a line on that site's `lastDay` (guard `lastDay != null`): manifest load `LOADING, -move`; fuel draw `FUEL, -draw`; abort return `RETURNED, +amount`; unload `UNLOADING, +move`. Wanted equals amount.
- [ ] **Step 3:** Test passes. Commit `feat(sim): shipping moves appear in the colony's day report`.

### Task 7: BuildForecast

**Files:** Create `sim/economy/BuildForecast.java`, `test/.../economy/BuildForecastTest.java`.

- [ ] **Step 1: Tests first** (world from `WorldGenerator.generate(42)`, Earth Hub):
  - Forecasting any type leaves `SaveFile.toJson(world)` unchanged.
  - FARM: `delta(FOOD) == 1.5` (no tech), `delta(BIOMASS) == -0.5`, a warning starting "Needs BIOMASS" containing "about 100 days".
  - RESEARCH_LAB, then a second one: the first forecast has no power warning (10 / 10), the second says "Power short: 10.0 made, 12.0 needed" and "83%".
  - POWER_PLANT when already browned out: `after.powerFactor == 1`, no warning, a fact line "Power 10.0 → 20.0 made".
  - HABITAT: fact "Population cap 300 → 400".
  - MINE on a body whose sampler returns 0 for ORE: warning starts "No ORE in the ground here".
  - MINE at a gas giant without the tech: "Research Atmospheric Mining".
  - Build what was forecast (events off, no ships), advance one tick: `lastDay.net(r) == after.net(r)` for every r except ones clipped by storage.
- [ ] **Step 2: Implement.**

```java
public record BuildForecast(BuildingType type, DayReport before, DayReport after,
                            List<String> facts, List<String> warnings) {
    public static BuildForecast of(World w, Site s, BuildingType type) {
        Body b = w.findBody(s.bodyId);
        DayReport before = SiteEconomy.run(w, b, s, s.buildings, new EnumMap<>(s.stockpile));
        List<Building> plus = new ArrayList<>(s.buildings);
        plus.add(new Building(type, 1));
        DayReport after = SiteEconomy.run(w, b, s, plus, new EnumMap<>(s.stockpile));
        return new BuildForecast(type, before, after, facts(w, s, type, before, after), warnings(...));
    }
    public double delta(Resource r) { return after.net(r) - before.net(r); }
    /** The next day at this site as it stands; shown when Site.lastDay is null. */
    public static DayReport estimate(World w, Site s) { ... estimate = true ... }
}
```

`Building` constructs enabled, so disabled buildings in `s.buildings` stay disabled because the list holds the same objects (don't copy them). Warnings follow the design doc §3.4, generated from the new building's `BuildingOutcome` and from the before/after nets, in this order: yield, input, tech, power, knock-on. Days use `Outlook.of(stock, cap, after.net(r))`.

- [ ] **Step 3:** Commit `feat(sim): BuildForecast previews a building at a colony`.

---

## Part B — UI

### Task 8: BuildingInfoPanel

**Files:** Create `ui/BuildingInfoPanel.java`.

- [ ] **Step 1: Implement** a `JPanel` (`BoxLayout.Y_AXIS`, `UiColors.PANEL_BACKGROUND`) with `show(BuildingSpec spec, BuildForecast f, Site s, TechState tech)`:
  - Title (display name, bold), summary in a wrapping `JTextArea`-styled label (`<html><body style='width:380px'>`).
  - "Per level, per day": inputs and outputs from `spec.rates()` with the tech multipliers from `DetailPanel.techNote`'s logic (move that helper to a package-private `TechNotes` so both use it), plus "2 energy" for consumers, "10 energy at 1 AU (÷ distance²)" for plants.
  - "At <site>, per day": a `GridLayout(0, 4)` of resource / now / with it / change for every resource with a non-zero before or after net, then a Power row `made / used`. Change colours: `FOREGROUND` for gains, `ERROR` for losses, dim for zero.
  - Facts in `FOREGROUND_DIM`, warnings with a "⚠ " prefix in `WARNING`.
- [ ] **Step 2:** Commit `feat(ui): building info card with per-level rates and a colony forecast`.

### Task 9: New build dialog

**Files:** Rewrite `ui/dialogs/BuildBuildingDialog.java`. Create `test/.../ui/BuildBuildingDialogTest.java`.

- [ ] **Step 1: Failing test** (skips when headless): `BuildBuildingDialog.create(frame, engine, "site-earth-hub")` returns an unshown `JDialog`; `build.list` has the 7 display names; selecting "Farm" puts "Needs BIOMASS" in the `build.info` panel's text; clicking `build.ok` enqueues `BuildBuildingCommand("site-earth-hub", FARM)` (check with `engine.world()` after one `advanceSilently`, or a queue-depth check) and disposes; `build.cancel` enqueues nothing.
- [ ] **Step 2: Implement.** `JDialog` (modal, title "Build at " + site name), `BorderLayout`: WEST `JScrollPane(JList<BuildingSpec>)` with a renderer showing `displayName` and " ⚠" when that type's forecast has warnings (forecasts for all 7 computed on open: cheap); CENTER `BuildingInfoPanel`; SOUTH right-aligned Cancel / Build. Build is the default button; Escape cancels; double-click in the list builds. Selection change → recompute that forecast from `engine.world()` and `show(...)`. `show(...)` = `create(...).setVisible(true)`.
- [ ] **Step 3:** Commit `feat(ui): build dialog shows what each building does before you build it`.

### Task 10: ResourceLedgerPanel

**Files:** Create `ui/ResourceLedgerPanel.java`, `test/.../ui/ResourceLedgerPanelTest.java`.

- [ ] **Step 1: Failing tests:** given a site and a `DayReport`, `update(site, report)` makes one row per resource with stock > 0 or any line; clicking the FOOD row (`ledger.row.FOOD`) shows child lines whose text includes "Population (100)" and "Farm L1"; a second `update` keeps FOOD expanded; a report with `powerFactor 0.83` shows the power row text "Brownout" in `ERROR`; `report.estimate` puts "(estimate)" in the header.
- [ ] **Step 2: Implement.** `GridBagLayout` rows: arrow + resource | stock "/ cap" | `%+.1f` net | outlook text. Row click toggles `expanded` (`EnumSet<Resource>`, plus a flag for Power). Child lines: `%+.1f  <label>` with the shortfall suffix "(wants X; no BIOMASS)" / "(wants X; brownout)" in dim. Labels: Building → `displayName + " L" + level`; Population → "Population (N)"; Shipping → `shipName + " " + kind.lower()`; StorageFull → "lost: storage full". Outlook text: "empty", "full", "empty in N d", "full in N d", "999+ d", "steady"; colours per design doc §5.1.
- [ ] **Step 3:** Commit `feat(ui): resource ledger with per-resource producers and consumers`.

### Task 11: Detail panel wiring and building rows

**Files:** Modify `ui/DetailPanel.java`, `ui/PanelSmokeTest.java`.

- [ ] **Step 1:** In `renderSite`, replace the Stockpile and Net / day blocks with one long-lived `ResourceLedgerPanel` field (so expansion state persists across `refresh()`), updated with `s.lastDay != null ? s.lastDay : BuildForecast.estimate(world, s)`. Reset its expansion only when the selected site id changes.
- [ ] **Step 2: Building rows** from `report.buildings()` by index: `displayName L<level>` then the outcome text per design doc §5.2 (outputs from that building's lines, e.g. "+2.4 ORE · +0.9 SILICATE"; lab "+1.0 research" from `ResearchPhase` math; habitat "+100 cap"; shipyard "builds ships"; limit text "idle: no BIOMASS", "60%: short of ORE", "83%: brownout", "poor ore here", "(disabled)"). Colour `WARNING` when a limit other than `LOW_YIELD`/`NEEDS_TECH` applies. Tooltip = old `techNote`.
- [ ] **Step 3:** `PanelSmokeTest`: a site with one of every type plus a disabled mine renders and paints. Commit `feat(ui): detail panel shows the colony ledger and what each building did`.

### Task 12: Colony list warnings

**Files:** Modify `ui/ColonyListPanel.java`; tests.

- [ ] **Step 1:** A static `rowColor(Site s)` using `lastDay` (or nothing when null): `ERROR` if FOOD or WATER stock < 1e-6; `WARNING` if either's `Outlook` is `EmptyIn(d ≤ 10)` or any `BuildingOutcome` has `NO_INPUT`/`SHORT_INPUT`; else `FOREGROUND`. Unit-test the three cases on hand-built reports.
- [ ] **Step 2:** Commit `feat(ui): colony list flags colonies running short`.

### Task 13: Tutorial text

**Files:** Modify `tutorial/TutorialScript.java`, `tutorial/TutorialScriptTest.java`.

- [ ] **Step 1:** `build-lab`: "... click **Build building...**, pick **Research lab** and press **Build**." `read-dock`: add "<p>Click a resource to see what makes and uses it.</p>".
- [ ] **Step 2:** `LABELS`: replace "RESEARCH_LAB" with "Research lab" and add "Build". The labels test must find both in the new dialog (build it with `create(...)`).
- [ ] **Step 3:** Commit `feat(tutorial): build step matches the new build dialog`.

---

## Part C — Verification and PR

### Task 14: Play-test drivers

**Files:** Modify `playtest/PlayTestDriver.java`, `playtest/StartupDriver.java`.

- [ ] **Step 1:** Replace the combo-and-OK code with: find `JList` named `build.list`, `setSelectedValue(BuildingCatalog.get(TYPE), true)`, click the "Build" button. Screenshot the dialog with Farm selected as `02-build-dialog.png`.
- [ ] **Step 2:** `PlayTestDriver` new step after step 3: select Earth Hub, click the FOOD row, screenshot `build/playtest/07-ledger.png`, check the ledger shows a "Farm L1" line.
- [ ] **Step 3:** `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew startupPlayTest` passes. (`./gradlew playTest` hangs at the Tech modal on `main` too; not this plan's to fix, but its steps 2–3 must reach that point.) Commit `test(playtest): drive the new build dialog and screenshot the ledger`.

### Task 15: Docs, full run, PR

- [ ] **Step 1:** Design doc status → "Implemented (Plan 7)" plus any deviations. Game-design spec §3.5: "Rates: see `BuildingCatalog`."
- [ ] **Step 2:** `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test` all green; test count about 389.
- [ ] **Step 3:** Push, open the PR against `main` with before/after screenshots of the build dialog and the detail panel, drive CI green.

---

## Self-review notes

- **Parity before refactor.** Task 1 must be committed before Task 2 touches `ProductionPhase`, and captured from unmodified code. If Task 2 or 4 changes a number, the fix is in the refactor, never in the fixture.
- **Floating point.** Keep expression order identical when moving code; `productionNet` sums in the same order the old `merge` calls did. `CommandTest` does exact arithmetic on `productionRateCache`.
- **Labs and brownout.** Today power shortage doesn't slow research. The UI mustn't claim it does (Task 5 step 1). Changing that is balance, out of scope.
- **Shared building objects.** `BuildForecast` copies the list, not the buildings, so `enabled` flags carry over; `SiteEconomy` never writes to a `Building`.
- **`lastDay` on new sites.** A site founded this tick gets its report in the same tick's production phase; `TransitPhase` still null-guards.
- **No schema change.** `lastDay` isn't written by `SaveFile`; loading gives null, and the UI shows the estimate.
