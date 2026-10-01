# Plan 8 — Building Costs and Construction

**Date:** 2026-09-29
**Status:** Implemented (Plan 8). Deviations: `ConstructionPacingTest` orders the factory last (lab, refinery, power plant, mine L2, then factory), because on a metal-poor Earth (seed 42, ore yield 0.19) a factory built first eats every unit of METAL the refinery makes and the mine upgrade never becomes affordable; costs are unchanged. The tutorial's dispatch step packs FOOD 40, WATER 30, METAL 20 and COMPONENTS 10 so the new colony can pay for a building. `Construction` also has `whyNotCancel`.
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–7 merged to `main` (last: Plan 7 resource management, PRs #19 and #20), plus play-tester and balance PRs #16, #18, #21, #22 and #23. About 400 tests.
**Source spec:** `2026-05-03-space-colony-game-design.md` §3.5 (buildings), §3.6 (COMPONENTS "used to construct buildings, ships, maintenance"; METAL + SILICATE → COMPONENTS), §7 (detail panel: "building list with upgrade buttons ... build building, demolish").

## 1. Vision

Buildings are free and instant. *Build building...* adds a level-1 building the next day at no cost, as many times as the player likes, and nothing ever changes a building after that. Four consequences:

1. **No economy pressure.** METAL and COMPONENTS pile up with nothing to spend them on. The best move is to build one of everything everywhere on day one.
2. **COMPONENTS is a dead resource.** Earth Hub starts with 50 and nothing makes or uses them (the Plan 8 idea list flagged this).
3. **Levels don't exist in practice.** Every rate scales with `level`, the detail panel prints "L1", but nothing can raise a level. The source spec asks for upgrade buttons.
4. **Damage is permanent.** A meteor strike or equipment failure sets `enabled = false` and nothing ever sets it back (only a solar flare's power plants recover). The source spec says "disabled until repaired", but there is no repair.

Plan 8 turns building into a decision:

1. **Costs.** Every building costs METAL and COMPONENTS, paid from the colony's own stockpile when the order is placed. A colony can only build with what it has on hand, so a new colony needs materials shipped in, and haulers finally have something to carry besides food and water.
2. **A factory.** A new FACTORY building turns METAL and SILICATE into COMPONENTS, closing the chain the source spec describes.
3. **Construction time.** A new building takes a few days to finish. Until then it draws no power and makes nothing, and its row counts the days down.
4. **Upgrades, with building slots.** Each colony has 10 building slots. Upgrading a building raises its level (up to 5) without using a slot, at a cost that grows with the level. Cheap early upgrades beat a second building; late ones compete with using a slot.
5. **Repair, cancel and demolish.** Damaged buildings can be repaired for a quarter of their cost. Construction can be cancelled for a full refund. Demolishing a building frees its slot and returns half of what went into it.

The build dialog from Plan 7 shows each building's cost against the colony's stock, its build time and the colony's free slots, and says why *Build* is disabled when it is. Each building row in the detail panel gets a menu with Upgrade, Repair, Cancel and Demolish, each showing its cost or refund.

**Out of scope:** ship costs (the source spec wants COMPONENTS + METAL per ship; see §9); COMPONENTS upkeep for buildings or population; construction that needs workers, power or an engineering tech; building-cost techs; the other Plan 8 balance candidates (fuel only from gas giants, the farm's double brownout penalty, cargo overfill from aborted trips and retired ships).

## 2. Architecture overview

### 2.1 New and changed files

```
spacecolony/
├── sim/
│   ├── BuildingType.java           (CHANGED: + FACTORY, after REFINERY)
│   ├── BuildingSpec.java           (CHANGED: + BuildCost cost)
│   ├── BuildCost.java              (NEW: record of resources + days, with affordability helpers)
│   ├── BuildingCatalog.java        (CHANGED: costs, FACTORY rates and summary)
│   ├── Construction.java           (NEW: slots, level cap, upgrade/repair/refund rules, "why not" checks)
│   ├── Building.java               (CHANGED: + id, + daysLeft; isOperational(), isUnderConstruction())
│   ├── Site.java                   (CHANGED: addBuilding(), findBuilding(id), nextBuildingId())
│   ├── EventKind.java              (CHANGED: + BUILDING_COMPLETED)
│   ├── commands/
│   │   ├── Command.java                    (CHANGED: permits the four new commands)
│   │   ├── UpgradeBuildingCommand.java     (NEW)
│   │   ├── RepairBuildingCommand.java      (NEW)
│   │   ├── CancelConstructionCommand.java  (NEW)
│   │   └── DemolishBuildingCommand.java    (NEW)
│   ├── economy/
│   │   ├── SiteEconomy.java        (CHANGED: FACTORY; buildings under construction draw nothing)
│   │   ├── Limit.java              (CHANGED: + CONSTRUCTING)
│   │   └── BuildForecast.java      (CHANGED: cost/blocker; + ofUpgrade)
│   └── phases/
│       ├── CommandPhase.java       (CHANGED: pays costs; applies the new commands)
│       └── ProductionPhase.java    (CHANGED: step 9, construction progress)
├── save/SaveFile.java              (CHANGED: schema v3, building id + daysLeft; v2 still loads)
├── world/WorldGenerator.java       (CHANGED: addBuilding)
├── debug/DebugActions.java         (CHANGED: "Finish construction" while paused)
└── ui/
    ├── BuildingInfoPanel.java      (CHANGED: cost, time, slots section)
    ├── dialogs/BuildBuildingDialog.java  (CHANGED: Build disabled with a reason)
    ├── BuildingMenu.java           (NEW: the per-row Upgrade / Repair / Cancel / Demolish menu)
    ├── DetailPanel.java            (CHANGED: slots header, construction rows, row menus)
    └── ResourceLedgerPanel.java    (CHANGED: limit text for CONSTRUCTING and damage)
```

`tutorial/TutorialScript.java` changes one step's text (§7).

### 2.2 Layering and threading

Unchanged from Plan 7. The rules live in `sim.Construction`, which `CommandPhase` enforces and the UI calls for its "why not" text, the same pattern as `CommandPhase.fuelShortfall` and the dispatch dialog. The UI only enqueues commands. Everything runs on the EDT.

## 3. The rules

### 3.1 Costs

Costs live on the catalog spec as a `BuildCost`:

```java
/** What one construction step takes: resources paid up front, then days until it's done. */
public record BuildCost(Map<Resource, Double> resources, int days) {
    public boolean affordable(Map<Resource, Double> stock);          // every entry ≤ stock + 1e-9
    public Map<Resource, Double> shortfall(Map<Resource, Double> stock); // only the missing amounts
    public BuildCost scaled(double factor);   // each amount × factor, rounded up to a whole unit; days unchanged
    public String describe();                 // "20 METAL, 5 COMPONENTS · 4 days"
}
```

First-pass numbers, sized so Earth Hub's starting 100 METAL and 50 COMPONENTS pay for about four buildings:

| Type | METAL | COMPONENTS | Days |
|---|---|---|---|
| Habitat | 25 | 5 | 5 |
| Farm | 15 | 5 | 3 |
| Mine | 20 | 5 | 4 |
| Refinery | 25 | 10 | 5 |
| Factory | 30 | 5 | 5 |
| Power plant | 20 | 10 | 4 |
| Shipyard | 50 | 20 | 10 |
| Research lab | 20 | 10 | 5 |

A plausible opening at Earth Hub is a research lab, a refinery, a factory and a power plant (95 METAL, 35 COMPONENTS). Earth Hub's ground is poor in ore (yield 0.19 to 0.45 on the three test seeds), so after that it earns roughly half a METAL a day until its mine is upgraded or METAL arrives from elsewhere. That is intended to make ore-rich colonies worth founding. All the numbers sit in `BuildingCatalog` and are expected to move after play-testing.

Costs are paid from the **colony's own stockpile** only. There is no empire-wide treasury and no credits (the source spec's "credits paid from world" never existed in code).

### 3.2 The factory

| Type | Per level per day | Power |
|---|---|---|
| FACTORY ("Factory") | uses 0.5 METAL and 0.5 SILICATE → makes 0.5 COMPONENTS | −2 |

Summary: "Assembles components from metal and silicate. Components pay for new buildings and upgrades."

Like the refinery, it asks for `level × rate × powerFactor` of each input and makes output in proportion to the smaller input share, so a brownout cuts it once, not twice (the farm's double penalty is not copied). Its outcome reports `NO_INPUT` / `SHORT_INPUT` for whichever input ran shortest, then `BROWNOUT`.

At Earth Hub a level-1 mine and refinery make about 0.5 METAL and 0.45 SILICATE a day, so a factory there runs near full and eats all of that METAL. The ledger and build forecast show exactly this, which is the point.

### 3.3 Construction time

A building now has an `int id` (unique within its site) and an `int daysLeft` (0 when idle):

- **New building.** `BuildBuildingCommand` pays, then adds `Building(type, level 0)` with `daysLeft = cost.days`. At level 0 every rate is zero; `SiteEconomy` skips it for power and gives it a `CONSTRUCTING` outcome.
- **Upgrade.** Pays, sets `daysLeft`. The building keeps working at its current level while it upgrades.
- **Progress.** A new step 9 at the end of `ProductionPhase` (after the solar-flare recovery) takes one day off every enabled building with `daysLeft > 0`. At zero, `level++` and a `BUILDING_COMPLETED` INFO event names it: "Farm finished at Earth Hub", "Mine upgraded to L3 at Pavonis". So a building ordered on day N with a 3-day build first works on day N + 4 (the command applies on day N+1's command phase, then three production steps). The build dialog says "ready in about 4 days".
- **Damage pauses it.** A damaged building (§3.5) under construction makes no progress until repaired.
- `Building.isOperational()` is `enabled && level > 0`. Every "has an enabled shipyard" check (`CommandPhase.applyBuildShip`, `DetailPanel`, `BuildForecast`, `BuildShipDialog`) uses it, so a shipyard under construction can't build ships.

Construction needs nothing but time. No power, no workers.

### 3.4 Slots and upgrades

- **Slots.** A colony has `Construction.SLOTS = 10` building slots. Every building uses one, including those under construction. Earth Hub starts at 5 of 10; a new colony at 1 of 10 (its free Habitat).
- **Level cap.** `Construction.MAX_LEVEL = 5`.
- **Upgrade cost** to level L is the base cost × L / 2, rounded up per resource: L2 costs 1×, L3 1.5×, L4 2×, L5 2.5×. Days are the base days. So a second level is the same price as a second building but saves a slot; later levels cost more than a fresh building but use no slot.
- An upgrade is refused while the building is under construction, already upgrading, damaged, or at level 5.

Duplicates stay legal: a colony may have two farms. Nothing about production changes; a level-2 farm is exactly two level-1 farms' rates (Plan 7's per-level catalog).

### 3.5 Repair

`enabled == false` is now called **damaged** in the UI (meteor strike, equipment failure). Repair costs the base cost × ¼, rounded up per resource, and is instant: the command phase sets `enabled = true` and the building works that same day. Power plants are the exception: `ProductionPhase` step 8 already re-enables every power plant each day (added for solar flares, but it also undoes a meteor strike or equipment failure on a plant). So a disabled power plant reads "offline today" and offers no Repair. Plan 8 keeps that behaviour.

### 3.6 Cancel and demolish

- **Cancel construction.** Refunds 100% of the in-progress step. A new building is removed (its slot frees); an upgrade stops and the building stays at its level.
- **Demolish.** Only for buildings not under construction. Removes the building and refunds half of everything invested in it, rounded down per resource: the base cost plus every upgrade step it has had (computed from type and level, not stored). A level-3 farm has had 15+15+23 = 53 METAL and 5+5+8 = 18 COMPONENTS put in, and returns 26 METAL and 9 COMPONENTS. A damaged building refunds the same.
- **Refunds never overflow.** A refund is capped at the stockpile's free room, and the confirm dialog says so ("20 METAL would be lost: storage full"). This keeps the command phase from pushing stock over the cap, which the production clip would otherwise log as loss (the same class of problem as the cargo-overfill item on the Plan 8 list).
- Earth Hub's starting buildings and a colony's free Habitat count as having cost their base price, so demolishing them refunds half. This lets a player recycle Earth Hub's shipyard, which is fine.

### 3.7 Construction rules in one place

```java
public final class Construction {
    public static final int SLOTS = 10;
    public static final int MAX_LEVEL = 5;
    static final double UPGRADE_STEP = 0.5;   // upgrade to L costs base × L × 0.5
    static final double REPAIR = 0.25;
    static final double DEMOLISH_REFUND = 0.5;

    public static int slotsUsed(Site s);
    public static BuildCost buildCost(BuildingType t);
    public static BuildCost upgradeCost(BuildingType t, int toLevel);
    public static BuildCost repairCost(BuildingType t);
    public static Map<Resource, Double> invested(Building b);        // base + upgrade steps to b.level
    public static Map<Resource, Double> demolishRefund(Building b);  // floor(½ invested)
    public static Map<Resource, Double> cancelRefund(Building b);    // the in-progress step, in full

    /** Why this colony can't start one of these now, or null. "No free building slot (10 of 10)",
     *  "Need 12 more METAL (have 8 of 20)". */
    public static String whyNotBuild(Site s, BuildingType t);
    public static String whyNotUpgrade(Site s, Building b);   // "Already level 5", "Under construction", "Damaged: repair it first", or a shortfall
    public static String whyNotRepair(Site s, Building b);    // "Not damaged", or a shortfall
    public static String whyNotDemolish(Site s, Building b);  // "Under construction: cancel it instead"
}
```

`CommandPhase` calls the matching `whyNot…` first and rejects with that text as a `COMMAND_REJECTED` warning, so a stale click from the UI (the stock ran down while the dialog was open, say) reads the same as the dialog's own message.

### 3.8 Commands

```java
public record BuildBuildingCommand(String siteId, BuildingType type) implements Command {}        // unchanged shape, now pays
public record UpgradeBuildingCommand(String siteId, int buildingId) implements Command {}
public record RepairBuildingCommand(String siteId, int buildingId) implements Command {}
public record CancelConstructionCommand(String siteId, int buildingId) implements Command {}
public record DemolishBuildingCommand(String siteId, int buildingId) implements Command {}
```

Buildings are addressed by id, not list index, so two queued commands on the same colony can't hit the wrong building after the first one removes something. `Site.addBuilding(b)` assigns `nextBuildingId()` (max id + 1, starting at 1). `WorldGenerator`, colony founding and `BuildBuildingCommand` use it. The id isn't stored on the site; it is recomputed from the list.

Paying and refunding happen in the command phase, which Plan 7's ledger deliberately doesn't itemise (§3.3 of that spec: one-off moves aren't daily flows). The colony's stock simply drops by the cost; the building row and the event log say why. `LedgerReconciliationTest` compares phases 4–5 only, so it is unaffected.

### 3.9 Economy changes

- `BuildingType.FACTORY` and its `SiteEconomy` case (§3.2).
- Level-0 buildings are skipped in the power loop (no zero-energy line) and get `BuildingOutcome(…, enabled, 0.0, Limit.CONSTRUCTING, null)`. A level-0 building that is also damaged reports `DISABLED`.
- Nothing else in `SiteEconomy` changes. For buildings with `level ≥ 1` and no factory, the arithmetic is Plan 7's, and the parity fixture proves it (§8.1).

### 3.10 Saving

Schema version 3. Each building writes `id` and `daysLeft` next to `type`, `level` and `enabled`. Loading a v2 save assigns ids 1..n in list order and `daysLeft = 0`. `MIN_READABLE_VERSION` stays 1.

## 4. Build dialog

Plan 7's dialog keeps its layout. The right-hand card gains a **Cost** block above the per-level rates:

```
│ Farm                                                 │
│ Grows food from biomass and water, ...               │
│                                                      │
│ Cost          15 METAL (have 82)                     │
│               5 COMPONENTS (have 3)   ✗ need 2 more  │
│ Build time    3 days, ready in about 4               │
│ Slots         6 of 10 used                           │
│                                                      │
│ Per level, per day ...                               │
```

- Each cost line is `FOREGROUND` when affordable, `ERROR` with "need N more" when not.
- *Build* is disabled when `Construction.whyNotBuild` returns a reason; the reason sits beside the button in `WARNING`, and the button's tooltip repeats it. The list's ⚠ marker stays for forecast warnings; an unaffordable entry is drawn dim.
- The forecast is labelled "Once built, per day" and still compares today with an extra level-1 building, so the numbers mean the finished building.
- The dialog refreshes its cost block each time the selection changes, from the world at that moment, as the forecast already does.

Component names add `build.cost` and `build.reason`.

## 5. Detail panel

### 5.1 Buildings header and rows

```
Buildings  6 of 10 slots
  Habitat L1        +100 cap                           ⋯
  Farm L1           +1.5 FOOD · +0.4 BIOMASS           ⋯
  Mine L1 → L2      +0.9 ORE · +0.6 SILICATE  (upgrading, 3 d)   ⋯
  Refinery          building, 4 days left              ⋯
  Power plant L1    +10.0 energy                       ⋯
  Research lab L1   damaged: repair 5 METAL, 3 COMP    ⋯   (WARNING)
```

- Under construction (level 0): "building, N days left" in `FOREGROUND_DIM`, or "building paused: damaged" in `WARNING`.
- Upgrading: the level shows "L1 → L2" and its current output stays, followed by "(upgrading, N d)".
- Damaged: "damaged: repair …" with the repair cost, replacing Plan 7's "(disabled)".

### 5.2 The building menu

Each row ends in a small `⋯` button (`detail.building.<id>`) that opens `BuildingMenu`, a `JPopupMenu`:

| Item | Shown when | Text |
|---|---|---|
| Upgrade | level ≥ 1, not under construction | "Upgrade to L2 · 15 METAL, 5 COMPONENTS · 3 days" |
| Repair | damaged | "Repair · 4 METAL, 2 COMPONENTS" |
| Cancel construction | `daysLeft > 0` | "Cancel upgrade · refund 15 METAL, 5 COMPONENTS" |
| Demolish… | not under construction | "Demolish… · refund 7 METAL, 2 COMPONENTS" |

- An item whose `whyNot…` gives a reason is disabled, with the reason as its tooltip ("Need 12 more METAL").
- Upgrade's tooltip, when enabled, is the upgrade forecast: `BuildForecast.ofUpgrade(w, s, id)` runs the day with that building one level higher and lists the changed nets and power, the same lines as the build dialog's table ("FOOD +1.5 a day, power 10.0 made / 12.0 used").
- Demolish asks for confirmation (`JOptionPane`, "Demolish Farm L3 at Earth Hub? Refund: 26 METAL, 9 COMPONENTS."), with the storage-full note when it applies. Cancel doesn't ask; it refunds everything.
- Choosing an item enqueues its command. The row changes on the next tick.

### 5.3 Colony list and ledger

- `ColonyListPanel` is unchanged: its warnings come from outlooks and outcomes, and `CONSTRUCTING` isn't a warning.
- `ResourceLedgerPanel.limitText` gains `CONSTRUCTING → "under construction"` and `DISABLED → "damaged"`.

## 6. Events and debug

- `EventKind.BUILDING_COMPLETED`, INFO, with the site id so the event strip's click selects the colony.
- Rejections use the existing `COMMAND_REJECTED` path with the `whyNot…` text.
- Debug menu gains **Finish construction** (enabled while paused, like the other edits): sets every `daysLeft` at the selected colony to 1, so the next tick completes them. It goes through `DebugActions`, not a sim command, the same as Plan 5's other debug edits.

## 7. Tutorial

- Step `build-lab`: "... pick **Research lab** and press **Build**. Buildings cost METAL and COMPONENTS from the colony's stock, and take a few days to finish; the row counts them down." The step still completes when a lab exists at Earth Hub at any level, so the tutorial doesn't stall for five days.
- The research step after it gains one sentence: "Research starts once the lab is finished."
- `TutorialScenario` gives Earth Hub nothing extra: 100 METAL and 50 COMPONENTS cover the lab.

## 8. Testing strategy

### 8.1 Sim (JUnit, headless)

- **Parity stays byte-identical.** `ProductionParityTest` and `EconomyScenario` stop using `BuildBuildingCommand` at tick 0 and place the same seven level-1 buildings directly (via `Site.addBuilding`) before the first tick, which is exactly when the commands used to apply. They list the seven types explicitly instead of looping `BuildingType.values()`, so FACTORY doesn't join them. The fixture file must not change; that is the proof that Plan 8 moved no production number.
- `BuildCostTest`: `affordable`, `shortfall`, `scaled` rounding up, `describe`.
- `ConstructionTest`: slots count level-0 buildings; upgrade costs for L2..L5; upgrade refused at L5, while constructing, while damaged; invested and demolish refund for a level-3 farm (53/18 → 26/9); cancel refund of a new building and of an upgrade; each `whyNot…` message.
- `ConstructionCommandTest`:
  - Build pays the exact cost and adds a level-0 building with the catalog's days; a short colony is rejected with the shortfall text and nothing is paid; an 11th building is rejected.
  - A 3-day farm ordered before tick N has level 1 after tick N+3 and first produces food on tick N+4; a `BUILDING_COMPLETED` event fires once.
  - Upgrade pays, keeps producing at the old level while `daysLeft > 0`, then produces at the new level.
  - Repair pays and the building produces the same day.
  - Cancel refunds and removes (new) or reverts (upgrade). Demolish refunds half and frees the slot. A refund at a full stockpile stops at the cap.
  - Two queued demolish commands on the same colony remove the two intended buildings (ids, not indexes).
  - A damaged building under construction doesn't progress.
  - A shipyard under construction can't build ships.
- `SiteEconomyTest` additions: a factory with METAL and SILICATE makes 0.5 COMPONENTS per level; with half the SILICATE it makes half and reports `SHORT_INPUT(SILICATE)`; in a 50% brownout it makes 0.25 (single penalty); a level-0 building draws no power and reports `CONSTRUCTING`.
- `BuildForecastTest` additions: forecasting a factory shows +COMPONENTS and −METAL; `ofUpgrade` for a farm shows +1.5 FOOD and +2 power used; forecasting never mutates the world (existing JSON-equality check covers `ofUpgrade` too).
- `SaveFileSchemaTest` / `SaveFileLoadTest`: v3 round-trips id and `daysLeft`; a v2 fixture loads with ids 1..n and no construction.
- `DeterminismTest`: add upgrade, repair and demolish commands to its script.
- `ConstructionPacingTest` (balance guard): on seeds 42, 7777 and 12345, a scripted opening at Earth Hub (lab, refinery, factory, power plant, then mine L2 as soon as affordable) has all five finished by day 200. It pins "the start isn't a dead end" without pinning exact numbers.

### 8.2 UI (JUnit; frames under Xvfb)

- `BuildBuildingDialogTest` additions: the list has 8 entries with Factory; with METAL at 0 the Build button is disabled and `build.reason` reads "Need 15 more METAL (have 0 of 15)" for a farm; with stock it's enabled and enqueues as before.
- `BuildingMenuTest`: a level-1 farm offers Upgrade and Demolish; a level-0 farm offers only Cancel; a damaged mine offers Repair (enabled with stock, disabled with the shortfall tooltip otherwise); choosing Upgrade enqueues `UpgradeBuildingCommand(site, id)`.
- `PanelSmokeTest`: a site with a level-0 building, an upgrading building and a damaged one paints without exceptions; the slots header reads "6 of 10 slots".
- `TutorialScriptTest`: the build-lab text still names "Build".

### 8.3 Play-test drivers

`PlayTestDriver`'s build step reads the cost block from the dialog and screenshots it to `build/playtest/08-build-cost.png`; it then opens a building menu and screenshots it to `build/playtest/08-building-menu.png`. `StartupDriver`'s tutorial walk is unchanged apart from waiting for the lab only to exist.

### 8.4 Test count estimate

| Bucket | New tests |
|---|---|
| Costs and rules | ~12 |
| Commands and construction progress | ~14 |
| Economy, forecast, factory | ~7 |
| Save v3, determinism, pacing | ~5 |
| UI (dialog, menu, panel) | ~8 |
| **Plan 8 new** | **~46** |
| Carried over (current `main`) | ~398 |
| **Total after Plan 8** | **~444** |

### 8.5 Manual play-test checklist

1. New game. Earth Hub's Buildings header reads "5 of 10 slots". Open *Build building...*: every entry shows a cost against Earth Hub's stock; Shipyard (50 / 20) is affordable, and the new Factory is listed.
2. Build a research lab: METAL drops by 20 and COMPONENTS by 10 the next day, and the row reads "building, 5 days left". It counts down, an event says it finished, and research starts.
3. Build until COMPONENTS runs short: the dialog disables *Build* and says how many more are needed.
4. Build a factory at Earth Hub: after it finishes, the ledger's COMPONENTS row shows it making about 0.45 a day and METAL shows it being eaten.
5. Upgrade the mine: the menu shows the cost and, in its tooltip, the extra ORE; the row reads "L1 → L2 (upgrading, 4 d)" and keeps producing, then produces double.
6. Debug-trigger a meteor strike on Earth: the struck row reads "damaged: repair …"; Repair restores it the same day.
7. Cancel a building under construction: the full cost comes back and the slot frees. Demolish a farm: half comes back after a confirmation.
8. Save during construction and load: the countdown continues where it was.
9. Tutorial: the lab step completes as soon as the lab is ordered, and the research step says it starts once the lab is finished.

## 9. Risks, decisions and open choices

### 9.1 Decisions made here (defaults)

- **Local stockpile only.** Each colony pays for its own buildings. The alternative, an empire-wide pool, would make shipping pointless.
- **METAL and COMPONENTS only.** SILICATE, WATER or FUEL costs would add flavour (silicate for solar panels, say) but more to track; they can be added per type in the catalog later without code changes.
- **Pay up front, then wait.** No per-day draw during construction, no workers, no power. Simple to explain, simple to cancel.
- **Upgrade cost base × L / 2, slots 10, level cap 5.** Chosen so the first upgrade and a second building cost the same and slots make the difference.
- **Repair is instant, a quarter of the base cost.** No repair time.
- **Demolish refunds half; cancel refunds all.**
- **Starting stock unchanged** (100 METAL, 50 COMPONENTS), which keeps the parity fixture byte-identical.
- **Ships stay free.** Ship costs are the obvious next step and reuse `BuildCost`, but they change the colonizer loop the tutorial teaches, so they belong in their own change.

### 9.2 Risks

- **Balance.** The numbers are a first pass. `ConstructionPacingTest` guards against a dead start; everything else is tuning in `BuildingCatalog`.
- **The play-tester.** Its script builds freely today and will start getting "Need N more METAL" rejections. It needs to check `Construction.whyNotBuild` (or read the rejection events) and ship materials to young colonies. Expect its colonies to grow more slowly.
- **Saves.** Schema v3 is additive; v2 saves load with everything finished.
- **Earth Hub METAL poverty.** On seed 42 Earth's ore yield is 0.19, so METAL income is thin until another colony ships some. That is deliberate, but it could feel slow; raising the start stock is a one-line change (and a fixture regeneration).

### 9.3 Open choices for Steve

1. Slots per colony: 10 everywhere (default), or more at Earth Hub?
2. Upgrade pricing: base × L / 2 (default), or a flat discount per level?
3. Repair: instant for ¼ cost (default), or a couple of days?
4. Ship costs in this plan too, or next (default: next)?
5. COMPONENTS upkeep (a small daily cost per building level, as the source spec's "maintenance" suggests): not in this plan (default).

## 10. What's next

Ship costs from `BuildCost`; COMPONENTS upkeep; a construction tech (faster or cheaper building); an empire resource summary to see where METAL sits; the remaining Plan 8 balance items (fuel outside gas giants, farm double brownout, cargo overfill on abort and retire).
