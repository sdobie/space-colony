# Plan 7 — Resource Management

**Date:** 2026-09-28
**Status:** Design, awaiting approval.
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–6 merged to `main` (sim core, render adaptation, engine + Swing UI shell, save/load + tech wiring, debug mode + save slots + panel polish, startup + options + tutorial), plus PRs #16 and #18. About 350 tests.
**Source spec:** `2026-05-03-space-colony-game-design.md` §3.5 (buildings), §3.6 (resources and chains), §5.3 (site), §7 (detail panel).

## 1. Vision

The economy works, but the player can't see it. Two gaps:

1. **Building blind.** *Build building...* is a combo box of enum names (`MINE`, `REFINERY`, ...) and an OK button. Nothing says that a farm eats biomass and water, that a mine is only as good as the ground under the colony, that every non-power building draws 2 energy, or that a sixth building can brown out the whole colony. The only way to learn is to build it and watch the numbers.
2. **A colony is a total, not a story.** The detail panel shows a stockpile and one "Net / day" figure per resource. It can't say *what* is making or eating each resource, which building is starved, that the colony is in a brownout, or how many days of food are left. When FOOD goes red the player has no way to tell whether the farm is idle for lack of biomass, the power is short, or the population simply outgrew it.

Plan 7 makes the economy legible, without changing how it behaves:

1. **One economy model.** Building rates move out of `ProductionPhase` into a `BuildingCatalog`, and the per-site day's arithmetic moves into a pure `SiteEconomy` that returns an itemised **day report**: every flow, its source, what it wanted versus what it got, and why it fell short. `ProductionPhase` becomes a thin caller. The sim's numbers don't change (a parity test pins them).
2. **Build preview.** A new build dialog lists the buildings by name with a description, what one level makes and uses, and a **forecast at this colony**: each resource's net per day now and with the new building, the power balance before and after, and plain warnings ("No ore in the ground here", "Needs BIOMASS: nothing here makes it, stock lasts 40 days", "Causes a brownout: farms, mines and refineries drop to 86%").
3. **Colony ledger.** The detail panel's resource section becomes one table: stock and cap, net per day, and an outlook ("empty in 12 days", "full in 30 days"). Click a resource to expand what produces and consumes it, line by line. A power line shows supply against demand. Each building row says what it did yesterday, or why it didn't ("idle: no BIOMASS"). The colony list flags colonies that are running out.

Because the forecast and the ledger both come from `SiteEconomy`, what the build dialog promises is what the colony then shows.

**Out of scope:** building costs, upgrades and demolition (buildings are still free and level 1); economy balance (nothing makes BIOMASS, FUEL only at gas giants; the ledger will make these visible, and a balance pass is the natural Plan 8); an empire-wide resource summary across colonies; per-resource stockpile caps other than the existing flat 1000; charts or history beyond the last day.

## 2. Architecture overview

### 2.1 New and changed files

```
spacecolony/
├── sim/
│   ├── BuildingCatalog.java        (NEW: display name, summary, per-level rates, power draw per type)
│   ├── BuildingSpec.java           (NEW: record)
│   ├── economy/                    (NEW PACKAGE: pure, no Swing, no world mutation)
│   │   ├── SiteEconomy.java        (one site's day: power, consumption, production → DayReport)
│   │   ├── DayReport.java          (record: power balance, flow lines, building outcomes)
│   │   ├── FlowLine.java           (record: source, resource, amount, wanted)
│   │   ├── FlowSource.java         (sealed: Building(index,type,level), Population, Shipping(ship), StorageFull)
│   │   ├── BuildingOutcome.java    (record: index, type, level, status, limit)
│   │   ├── Limit.java              (enum: DISABLED, BROWNOUT, NO_INPUT, SHORT_INPUT, NO_YIELD, LOW_YIELD, NEEDS_TECH)
│   │   ├── BuildForecast.java      (before/after DayReports for "add one L1 building of type T here")
│   │   └── Outlook.java            (days until empty / full from stock, cap, net)
│   ├── Site.java                   (CHANGED: transient `lastDay` DayReport)
│   └── phases/
│       ├── ProductionPhase.java    (CHANGED: calls SiteEconomy; keeps clip, morale, population)
│       └── TransitPhase.java       (CHANGED: records shipping lines on the site's lastDay)
└── ui/
    ├── dialogs/BuildBuildingDialog.java  (REWRITTEN: list + info card + forecast)
    ├── BuildingInfoPanel.java      (NEW: the right-hand card of the build dialog)
    ├── ResourceLedgerPanel.java    (NEW: the detail panel's resource table with expandable rows)
    ├── DetailPanel.java            (CHANGED: uses ResourceLedgerPanel; building rows show outcomes)
    └── ColonyListPanel.java        (CHANGED: warning colour from the outlook)
```

`tutorial/TutorialScript.java` changes one step's text (§6). No save schema change (§3.6).

### 2.2 Layering

- `sim.economy → sim`. Nothing in `sim` outside `phases` imports `sim.economy`, except `Site` for the `lastDay` field type.
- `ui → sim.economy` for display. The UI never calls `SiteEconomy` with the live stockpile map; it always goes through `BuildForecast` or reads `Site.lastDay`.
- `SiteEconomy` never touches `World` state. It takes the stockpile as a map argument: `ProductionPhase` passes the site's real map, previews pass a copy.

### 2.3 Threading

Unchanged: everything on the EDT. A forecast is a few dozen multiplications per building and runs on the EDT when the dialog's selection changes.

## 3. The economy model

### 3.1 BuildingCatalog

Today the rates are literals inside `ProductionPhase`'s switch. They move to one table so the sim, the build dialog and the ledger read the same numbers:

| Type | Display name | Per level per day | Power |
|---|---|---|---|
| HABITAT | Habitat | +100 population cap | −2 |
| FARM | Farm | uses 0.5 BIOMASS, 0.3 WATER → makes 1.5 FOOD | −2 |
| MINE | Mine | makes 2.0 ORE × ore yield, 1.0 SILICATE × silicate yield; at a gas giant with Atmospheric Mining, 2.0 FUEL × fuel yield | −2 |
| REFINERY | Refinery | uses 1.5 ORE → 0.8 METAL per ORE; uses 1.0 ICE → 0.9 WATER per ICE | −2 |
| POWER_PLANT | Power plant | makes 10 energy ÷ (distance from sun in AU)² | 0 |
| SHIPYARD | Shipyard | lets this colony build ships | −2 |
| RESEARCH_LAB | Research lab | makes 1.0 research point | −2 |

```java
public record BuildingSpec(BuildingType type, String displayName, String summary,
                           double powerDrawPerLevel, List<Rate> rates) {
    /** One input or output per level per day, before yield, power and tech. */
    public record Rate(Resource resource, double perLevel, Kind kind) {
        public enum Kind { INPUT, OUTPUT, YIELD_OUTPUT }  // YIELD_OUTPUT is scaled by the ground
    }
}
```

`BuildingCatalog.get(type)` returns the spec; `BuildingCatalog.all()` lists them in enum order. The summary is one sentence written for the player, for example Farm: "Grows food from biomass and water. Without biomass it sits idle." The refinery's two chains are two independent input/output pairs; the spec carries a `pairs` note so the dialog can print "1.5 ORE → 1.2 METAL" rather than two disconnected lines.

Research labs and habitats keep working where they do today (`ResearchPhase.pointsPerTick`, `PopCapBreakdown`), but read their per-level numbers from the catalog.

### 3.2 SiteEconomy and the day report

```java
public final class SiteEconomy {
    /** One day at one site. Mutates only {@code stock}. Same order of operations as Plan 6's ProductionPhase. */
    public static DayReport run(World w, Body b, Site s, List<Building> buildings, Map<Resource, Double> stock);
}

public record DayReport(double powerMade, double powerUsed, double powerFactor,
                        List<FlowLine> lines, List<BuildingOutcome> buildings) {
    public double net(Resource r);                 // sum of lines for r
    public List<FlowLine> linesFor(Resource r);    // producers first (largest first), then consumers
    public DayReport withLine(FlowLine l);         // used by TransitPhase and the clip step
}

public record FlowLine(FlowSource source, Resource resource, double amount, double wanted) {}
// amount: signed (+ made, − used). wanted: what the source would have moved with full power and full inputs.
```

`run` does steps 1, 3, 4 and 5 of today's `ProductionPhase` in the same order with the same arithmetic:

1. Power: each enabled POWER_PLANT adds a `FlowLine(Building, ENERGY, +output)`; every other enabled building adds `(Building, ENERGY, −draw)`. `powerFactor = min(1, made / used)`.
2. Population eats: `(Population, FOOD, −taken, −need)` and the same for WATER.
3. Buildings in list order: each consume and produce call becomes a line with its `wanted` alongside. Consumption still draws from `stock` in order, so a second farm still finds less biomass than the first.
4. Each building gets a `BuildingOutcome` with a status and, when it fell short, the first `Limit` that explains it, in this priority: `DISABLED` (an event knocked it out), `NEEDS_TECH` (gas-giant fuel without Atmospheric Mining; informational only), `NO_INPUT(r)` (got 0 of an input), `SHORT_INPUT(r)` (got under 99% of an input), `BROWNOUT` (powerFactor < 1; farms, mines and refineries only, since power has never slowed labs, habitats or shipyards and this plan doesn't change that), `NO_YIELD(r)` (ground yield under 0.01), `LOW_YIELD(r)` (under 0.25). Efficiency is `actual main output / wanted main output`.

`ProductionPhase.run` becomes: recompute cap → `DayReport r = SiteEconomy.run(w, b, s, s.buildings, s.stockpile)` → clip (adding a `StorageFull` line for anything clipped, with the amount lost) → `s.lastDay = r` → `productionRateCache` filled, for existing readers (`GoalCatalog`, tests), from the building and population lines only, so it keeps today's meaning (gross of clipping and shipping) → morale → population → re-enable power plants. The clip step is the only new arithmetic, and it only records what the clip already throws away.

ENERGY lines never reach `productionRateCache` (it has only ever held 0 for ENERGY); the power balance lives on `powerMade` / `powerUsed`.

### 3.3 Shipping lines

The colony's stock also moves when ships load, unload and draw fuel (`TransitPhase.loadingAndUnloading`, phase 5, right after production). Those moves are appended to `lastDay` as `(Shipping(shipId, shipName, kind), r, ±amount)` with kind `LOADING`, `UNLOADING`, `FUEL` or `RETURNED` (aborted departure). With them, the ledger's net per day is exactly how the stockpile changed over phases 4–5. One-off moves in the command phase (retiring a docked ship, founding a colony) aren't daily flows and aren't recorded.

### 3.4 BuildForecast

```java
public record BuildForecast(BuildingType type, DayReport before, DayReport after, List<String> warnings) {
    public static BuildForecast of(World w, Site s, BuildingType type);
    public double delta(Resource r);   // after.net(r) − before.net(r)
}
```

`of` runs `SiteEconomy` twice on copies of the site's stockpile: once with the current building list, once with an extra enabled L1 building of `type` at the end (where `BuildBuildingCommand` puts it). It never touches the world. Yesterday's events are respected: disabled buildings stay disabled in both runs, so the preview matches what tomorrow will look like.

Warnings are derived from the `after` report and the catalog, not special-cased per type:

- **No yield.** The new building's outcome is `NO_YIELD`: "No ORE in the ground here. Mines elsewhere on this body may do better (see the body view's yield overlay)." `LOW_YIELD` gives "Poor ore here: 18% of a rich site."
- **Missing input.** `NO_INPUT(r)` or `SHORT_INPUT(r)`, plus whether anything at the colony makes `r`: "Needs BIOMASS. Nothing here makes it; the 40 in stock lasts about 80 days." (days from the `after` net).
- **Brownout.** `after.powerFactor < 1`: "Power short: 12.0 made, 14.0 needed. Farms, mines and refineries here run at 86%." If `before` was already short, "Makes the brownout worse: 86% → 75%."
- **Needs tech.** A mine at a gas giant without Atmospheric Mining: "Research Atmospheric Mining to pull fuel from this atmosphere."
- **Knock-on.** Any resource whose `after` net turns negative when `before` was ≥ 0, or whose outlook drops below 30 days: "WATER goes from +0.4 to −0.5 a day."
- **Type-specific facts** from the catalog, not the sim: Habitat "Population cap 300 → 400"; Research lab "Research 1.0 → 2.0 points a day" (uses `ResearchPhase.pointsPerTick` plus the lab's own output); Shipyard "Lets Earth Hub build ships" or "Earth Hub already has a shipyard; a second one adds nothing yet."

### 3.5 Outlook

`Outlook.of(stock, cap, net)` returns `EMPTY_IN(days)`, `FULL_IN(days)`, `STEADY`, `EMPTY` (0 stock and net ≤ 0 with demand) or `FULL` (at cap and net ≥ 0). Days are `ceil(stock / −net)` or `ceil((cap − stock) / net)`, capped at 999 ("999+ days"). A net within ±0.005 per day is `STEADY`.

### 3.6 Saving

`Site.lastDay` is `transient` in spirit: `SaveFile` writes named fields, so simply not writing it keeps schema v2 unchanged. After a load (or at tick 0) `lastDay` is null. The UI then shows an estimate from `BuildForecast.of(...).before()` (a dry run of the next day), labelled "(estimate)". The next tick replaces it with the real report. `DeterminismCheck` compares saved state, so it is unaffected.

## 4. Build dialog

### 4.1 Layout

A modal `JDialog` titled "Build at Earth Hub", about 720 × 440:

```
┌──────────────────┬──────────────────────────────────────────────────────┐
│ Habitat          │ Farm                                                 │
│ Farm          ⚠  │ Grows food from biomass and water. Without biomass   │
│ Mine          ⚠  │ it sits idle.                                        │
│ Refinery         │                                                      │
│ Power plant      │ Per level, per day                                   │
│ Shipyard         │   uses 0.5 BIOMASS, 0.3 WATER, 2 energy              │
│ Research lab     │   makes 1.5 FOOD                                     │
│                  │                                                      │
│                  │ At Earth Hub, per day      now    with it   change   │
│                  │   FOOD                    +0.5     +2.0     +1.5     │
│                  │   BIOMASS                 −0.5     −1.0     −0.5     │
│                  │   WATER                   −0.8     −1.1     −0.3     │
│                  │   Power (made / used)   10 / 8   10 / 10             │
│                  │                                                      │
│                  │ ⚠ Needs BIOMASS. Nothing here makes it; the 100 in   │
│                  │   stock lasts about 100 days.                        │
├──────────────────┴──────────────────────────────────────────────────────┤
│                                                     [ Cancel ] [ Build ] │
└─────────────────────────────────────────────────────────────────────────┘
```

- Left: a `JList` of catalog display names. A ⚠ marks types whose forecast has a warning. The first entry is selected on open. Double-click or Enter builds.
- Right (`BuildingInfoPanel`): name, summary, the per-level rates with the current tech multipliers applied and named, then the forecast table for resources whose before or after net is non-zero, the power row, then warnings in `UiColors.WARNING`. Changes are coloured (`FOREGROUND` for gains, `ERROR` for losses).
- *Build* enqueues `BuildBuildingCommand(siteId, type)` exactly as today and closes. *Cancel* and Escape close.
- The forecast is computed when the dialog opens and when the selection changes, from the world at that moment. The game keeps running behind the modal as it does today.

`BuildBuildingDialog.show(Component parent, Engine engine, String siteId)` keeps its signature. For tests, a package-private `BuildBuildingDialog.create(...)` returns the unshown dialog, and components are named `build.list`, `build.info`, `build.ok` (label "Build") and `build.cancel`.

### 4.2 Names in the UI

The catalog's display names replace raw enum names wherever Plan 7 touches the UI: the build dialog, the detail panel's building rows and ledger lines. Other places (event text, debug inspector, the ship dialog) keep enum names; a later pass can move them.

## 5. Colony ledger

### 5.1 Resource table

`ResourceLedgerPanel` replaces the "Stockpile" and "Net / day" grids in `DetailPanel.renderSite`. One row per resource that has stock or any flow:

```
Resources                     stock    / day   outlook
▸ FOOD                     182 / 1000   +0.5   999+ d to full
▾ WATER                    190 / 1000   −0.8   empty in 238 d
     −0.5  Population (100)
     −0.3  Farm L1
▸ ORE                       40 / 1000   +2.4   full in 400 d
▸ BIOMASS                   98 / 1000   −0.5   empty in 196 d
  Power                  10.0 made / 8.0 used          ok
```

- Clicking a row toggles its breakdown: one line per `FlowLine`, producers first, largest first. A line that fell short shows its wanted amount and reason in dim text: "+0.0 Farm L1 (wants 1.5; no BIOMASS)". Shipping lines read "+25 Ark unloading". Storage-full lines read "−3.1 lost: storage full".
- The expanded set is a `Set<Resource>` on the panel, kept across refreshes and selection changes (so FOOD stays open as the days tick).
- Outlook colours: `ERROR` when empty, or FOOD / WATER empty within 10 days; `WARNING` when any resource empties within 30 days or is full and losing output; dim otherwise.
- The Power row expands to per-building energy lines. When `powerFactor < 1` it reads "Brownout: 10.0 made / 12.0 used, production at 83%" in `ERROR`.
- When `lastDay` is null (§3.6), the header reads "Resources (estimate)".

### 5.2 Building rows

Each building row in the detail panel gets its outcome from `lastDay.buildings()` (matched by index):

- `Mine L1   +2.4 ORE · +0.9 SILICATE`
- `Farm L1   idle: no BIOMASS` (`WARNING`)
- `Refinery L1   60%: short of ORE` (`WARNING`)
- `Power plant L1   +10.0 energy`
- `Research lab L1   +1.0 research`
- `Habitat L1   +100 cap`
- `Mine L1   (disabled)` (`WARNING`; equipment failure or meteor)

The existing tech multiplier note moves into the row's tooltip.

### 5.3 Colony list

`ColonyListPanel` colours a colony row `ERROR` when FOOD or WATER is empty, `WARNING` when either empties within 10 days or any building is idle for lack of input, and normal otherwise. Today it only checks FOOD == 0.

## 6. Tutorial

- Step `build-lab` text becomes "... click **Build building...**, pick **Research lab** and press **Build**." `TutorialScriptTest.labels` checks "Build" against the dialog's button.
- Step `read-dock` gains one sentence: "Click a resource to see what makes and uses it."
- Target keys are unchanged (`detail.buildBuilding` still names the button that opens the dialog).

## 7. Testing strategy

### 7.1 Sim (JUnit, headless)

- `ProductionParityTest` (written first, against the unrefactored code): seeds 42, 7777 and 12345, a fixed command script (build every building type at Earth Hub, research Basic Mining then Basic Farming, dispatch a hauler), 2,000 ticks; assert every site's stockpile, population and morale to 1e-9 against values captured from `main` before the refactor and checked in as a fixture. This is what "the numbers don't change" means.
- `BuildingCatalogTest`: every `BuildingType` has a spec, a display name and a non-empty summary; power plants draw 0 and everything else 2.
- `SiteEconomyTest`: a farm with no biomass reports `NO_INPUT(BIOMASS)` and 0 food; a brownout sets `BROWNOUT` on every consumer and the right `powerFactor`; a mine at zero ORE yield reports `NO_YIELD`; a disabled building reports `DISABLED` and no lines; two farms sharing too little biomass: the first `SHORT_INPUT`/full, the second starved; `run` doesn't touch anything but `stock`.
- `LedgerReconciliationTest`: over 500 ticks of a busy world with ships loading and unloading, for every site and resource, `stock after phase 5 − stock before phase 4 == lastDay.net(r)` to 1e-9. This is the invariant that makes the ledger trustworthy.
- `BuildForecastTest`: forecasting does not change the world (save JSON before == after); a farm at Earth Hub warns about BIOMASS with a days figure; a mine on a zero-ore site warns "No ORE"; the seventh building at a colony with one power plant warns about a brownout; a power plant forecast clears an existing brownout; habitat says cap 300 → 400; a gas-giant mine without the tech warns about Atmospheric Mining. Building exactly what was forecast and ticking once gives a `lastDay` whose nets equal `after`'s nets (same stock, no events).
- `OutlookTest`: empty, full, steady, both directions, the 999 cap.
- Existing `ProductionTest`, `ProductionTechEffectsTest`, `AtmMiningTest`, `SupplyTest`, `DeterminismTest` and `GoalsTest` pass unchanged.

### 7.2 UI (JUnit; frames need Xvfb, like `DebugControllerTest`)

- `BuildBuildingDialogTest`: the list has 7 display names; selecting Farm fills the info panel with the summary and a BIOMASS warning; *Build* enqueues `BuildBuildingCommand(site, FARM)`; *Cancel* enqueues nothing.
- `ResourceLedgerPanelTest`: rows for resources with stock or flow; clicking FOOD expands its lines and the expansion survives a refresh; a brownout shows the Power row in `ERROR`; a null `lastDay` shows "(estimate)".
- `PanelSmokeTest`: the detail panel paints a site with buildings of every type, including a disabled one.
- `ColonyListPanel`: FOOD empty → `ERROR`; FOOD empties in 5 days → `WARNING`.

### 7.3 Play-test drivers

`PlayTestDriver` steps 2–3 and `StartupDriver`'s tutorial step select from the new list and click *Build* instead of the combo box and OK. `PlayTestDriver` adds one step: open Earth Hub's FOOD row and screenshot the ledger to `build/playtest/07-ledger.png`, and screenshot the build dialog with Farm selected.

### 7.4 Test count estimate

| Bucket | New tests |
|---|---|
| Parity + reconciliation | ~5 |
| Catalog, economy, outlook | ~14 |
| Forecast | ~8 |
| UI (dialog, ledger, list) | ~9 |
| **Plan 7 new** | **~36** |
| Carried over (current `main`) | ~353 |
| **Total after Plan 7** | **~389** |

### 7.5 Manual play-test checklist

1. New game, select Earth Hub. The Resources table shows FOOD, WATER, METAL, COMPONENTS, FUEL and BIOMASS with outlooks. Click FOOD: population is the only consumer.
2. Build building...: Farm shows the BIOMASS warning with a days figure; Mine shows Earth Hub's actual ore yield; Power plant shows the power going up.
3. Build a farm. Next day, the FOOD row has a "+1.5 Farm L1" line and BIOMASS shows "empty in N days" matching what the dialog said.
4. Build buildings until the dialog warns about a brownout, build one more, and see the Power row go red and the farm and mine rows show their reduced output.
5. Dispatch a hauler with cargo from Earth Hub: that day's ledger shows "Hauler loading" lines, and the destination shows "unloading" lines on arrival.
6. Let BIOMASS run out: the farm row reads "idle: no BIOMASS" and the colony list turns amber.
7. Save and load: the table shows "(estimate)" until the next day, then the real report.
8. Tutorial: the Build lab step's text matches the new dialog, and the highlight still lands on *Build building...*.

## 8. Risks and decisions

- **Refactoring the sim's heart.** Moving `ProductionPhase` into `SiteEconomy` could quietly change numbers. The parity fixture is captured before any refactor, and the refactor is a move, not a rewrite: same order, same expressions. Any rebalancing waits for a separate plan.
- **The forecast is one day, from today's stock.** It shows what tomorrow looks like, not the long run: a farm with 100 biomass shows +1.5 food even though it will stop in 200 days. The warning line and days figure carry the long view. A multi-day simulation was considered and rejected: it would need ships, events and population growth to be meaningful.
- **Warnings from outcomes, not per-type rules.** Deriving warnings from `Limit`s means a new building type gets sensible warnings for free, at the cost of slightly generic wording.
- **No building costs yet.** The dialog has room for a cost line, but buildings stay free in Plan 7; adding costs changes the game's balance and belongs with the balance pass.
- **Enum names remain in some places** (§4.2). Changing every surface at once would touch event text and saves' human-readable fields for little gain.

## 9. What's next

Plan 8 candidates: a balance pass using the ledger as its instrument (a BIOMASS source, fuel synthesis outside gas giants, COMPONENTS production from METAL + SILICATE as spec §3.6 intended, building costs, early-game water); an empire resource summary across colonies; building upgrades and demolition from the building rows; contextual hints driven by the outcome `Limit`s.
