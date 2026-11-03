# Plan 10 — Ship Build Times, Fuel Types and Progress

**Date:** 2026-11-01
**Status:** Implemented (Plan 10) with the §9.3 defaults plus ship costs (§3.5). Deviations: the economy parity scenario places its hauler ready-built after the first tick, so the fixture doesn't depend on ship costs; the shipyard row's progress bar sits under the whole row (not nested), so wrapped text keeps its layout; the screenshot driver is `./gradlew shipsAndFuelShot` (`playtest/ShipsAndFuelDriver`). The continuous play-tester now builds a factory at the hub while it can still pay for one, since only factories make the COMPONENTS ships need.
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–9 merged to `main` (last: Plan 9 explorers and surveys, PRs #30 and #31), plus PRs through #40 (slower clock and travel #35, furnace #38, play-tester styles #39 and #40).
**Source spec:** `2026-05-03-space-colony-game-design.md` §3.4 (ship classes), §3.5 (resources and buildings).

## 1. Vision

Steve's ask: *ships need to have a build time, varying with the type. There should be different kinds of fuel. Hydrogen fuel from the gas giants would be the most efficient, but there should be another type that can be manufactured from a building.*

Two problems today:

1. **Ships appear instantly.** Pressing OK in *Build ship...* puts a ready ship in the dock the same tick. Since PR #35 a trip to Mars takes weeks, and Plan 8 gave buildings construction times, so an instant colonizer is the odd one out.
2. **FUEL has one source, and it's far away.** Only a gas-giant mine with Atmospheric Mining makes FUEL. Earth Hub starts with 400 and can never make more, so the inner system runs dry unless the player researches a 500-point tech and settles Jovian first. This is the oldest open balance concern in the project.

Plan 10:

1. **Ship build times and costs by class.** Ordering a ship pays METAL and COMPONENTS from the colony up front (§3.5). A shipyard builds one ship at a time. An explorer takes 10 days, a hauler 20, a tanker 25 and a colonizer 40. Each shipyard level adds a day of work per day, so a level 4 shipyard builds a colonizer in 10 days. The ship shows in the colony list as "Building · 12 days left" and can't fly until it's done.
2. **Two fuels.** The resource FUEL is renamed **HYDROGEN**: still mined from gas giants, and the best fuel, one unit burns for one unit of fuel value. A new resource **SYNFUEL** is made by a new **Fuel plant** building from BIOMASS, anywhere, from day one. A unit of synfuel is worth half a unit of hydrogen, so a trip needs twice as many units, and a tanker full of it carries half the range.

3. **Progress you can see** (Steve's follow-up ask): a research lab shows how far the current tech has got, and a shipyard shows how far its current ship has got, both in the colony's building list and as small progress rings next to the colony on the system map.

**Out of scope:** a build queue; letting the player pick which fuel a trip burns; fuel mass counting towards a trip's mass; new techs for synfuel.

## 2. Architecture overview

### 2.1 New and changed files

```
spacecolony/
├── sim/
│   ├── Resource.java               (CHANGED: FUEL renamed HYDROGEN; + SYNFUEL)
│   ├── Fuels.java                  (NEW: which resources are fuel, value per unit, draw order)
│   ├── ShipClass.java              (CHANGED: + buildDays)
│   ├── ShipState.java              (CHANGED: + BUILDING)
│   ├── Ship.java                   (CHANGED: tank holds each fuel; + buildDaysLeft, yardBuildingId)
│   ├── BuildingType.java           (CHANGED: + FUEL_PLANT)
│   ├── BuildingCatalog.java        (CHANGED: Fuel plant rates and cost)
│   ├── ShipYards.java              (NEW: free shipyard at a site, build days for a class there)
│   ├── EventKind.java              (CHANGED: + SHIP_COMPLETED)
│   ├── economy/SiteEconomy.java    (CHANGED: Fuel plant runs after farms; gas-giant mines make HYDROGEN)
│   └── phases/
│       ├── CommandPhase.java       (CHANGED: build ship starts a BUILDING ship; retire cancels a build; fuel checks use value)
│       ├── ProductionPhase.java    (CHANGED: step 9 also advances ships under construction)
│       └── TransitPhase.java       (CHANGED: draw and burn fuel by value, hydrogen first)
├── save/SaveFile.java              (CHANGED: schema v5; "FUEL" read as HYDROGEN; tank per fuel; ship build state)
├── world/WorldGenerator.java       (CHANGED: Earth Hub starts with 400 HYDROGEN)
├── tutorial/                       (CHANGED: a "wait for the colonizer" step)
└── ui/
    ├── ColonyListPanel.java        (CHANGED: ship cards show build progress; empire FUEL total is fuel value)
    ├── DetailPanel.java            (CHANGED: ship under construction; tank shows each fuel)
    ├── dialogs/BuildShipDialog.java    (CHANGED: build days per class, shipyard busy message)
    ├── dialogs/DispatchShipDialog.java (CHANGED: fuel popup counts both fuels)
    └── SystemMapPanel.java         (CHANGED: skip ships under construction when drawing docked ships)
```

### 2.2 Layering and threading

Unchanged. All new state is on `Ship` and in stockpiles and changes only in the sim phases. The UI reads it and enqueues commands.

## 3. Ship build times and costs

### 3.1 Build days

| Class | Build days (L1 shipyard) | Why |
|---|---|---|
| Explorer | 10 | small, no hold |
| Hauler | 20 | the workhorse |
| Tanker | 25 | bigger hold, pressure tanks |
| Colonizer | 40 | carries a colony's habitat |

`ShipClass` gains `buildDays()`. At 1× (3 s a day) a colonizer takes two minutes, about the length of a short Earth–Mars trip; at 16× it's eight seconds.

### 3.2 Shipyards

- **One ship per shipyard.** A colony can build as many ships at once as it has operational shipyards that aren't already building one. Each ship remembers its shipyard's building id (`Ship.yardBuildingId`); Plan 8 gave buildings per-site ids.
- **Speed.** Each day the ship's remaining work drops by its shipyard's level. `buildDaysLeft` counts work-days, so a level 2 shipyard finishes a 25-day tanker in 13 days.
- **Paused** while the shipyard is damaged, or under an upgrade (an upgrading shipyard is still level L and keeps working; only damage pauses, the same rule as building construction).
- **Demolishing** a shipyard that is building a ship is rejected: "Shipyard is building Hauler 3: finish or cancel it first."
- **Busy.** If every shipyard at the colony is busy, *Build ship* is rejected: "Every shipyard at Earth Hub is busy (Hauler 3, 12 days left)". The build dialog shows the same line and disables OK.

### 3.3 The ship while it's being built

- A new `ShipState.BUILDING`. The ship is in `World.ships` from the moment it's ordered, docked at the shipyard's colony (`currentSiteId`), with `buildDaysLeft > 0`.
- It can't be dispatched ("Hauler 3 is still being built: 12 days left"). Retiring it **cancels the build**, removes it and refunds its full cost (capped at free room, Plan 8's cancel rule).
- Ships still count towards the *Fleet of 10* goal only once built.
- When `buildDaysLeft` reaches 0 in ProductionPhase step 9 the ship becomes IDLE at the colony and a `SHIP_COMPLETED` INFO event fires: "Hauler 3 is ready at Earth Hub". Clicking the event selects the ship.

### 3.4 Commands

No new commands. `BuildShipCommand` now starts a build instead of making a ready ship. `RetireShipCommand` on a BUILDING ship cancels it.

### 3.5 Ship costs

Steve's call on review: ships cost METAL and COMPONENTS, paid from the ordering colony's stock when the build starts, like buildings (Plan 8).

| Class | METAL | COMPONENTS | Days |
|---|---|---|---|
| Explorer | 15 | 5 | 10 |
| Hauler | 30 | 10 | 20 |
| Tanker | 35 | 10 | 25 |
| Colonizer | 60 | 20 | 40 |

- `ShipClass.cost()` returns a `BuildCost` (Plan 8's record, so `describe()` and `shortfall()` come free). Build days live in it.
- A colony that can't pay is rejected with Plan 8's text: "Need 12 more METAL (have 48 of 60)". The build dialog shows the cost per class and disables OK with that text.
- Earth Hub starts with 100 METAL and 50 COMPONENTS, so a colonizer (60 / 20) is affordable on day one, with 40 METAL left for a building. The tutorial is unaffected.
- Cancelling a build refunds the full cost; retiring a built ship refunds nothing (its cargo and fuel go to the dock, as today).
- The play-tester's script must handle "can't afford" rejections and the wait for METAL.

## 4. Fuels

### 4.1 Hydrogen and synfuel

| Resource | Where it comes from | Fuel value per unit |
|---|---|---|
| **HYDROGEN** (was FUEL) | gas-giant mines, with Atmospheric Mining | 1.0 |
| **SYNFUEL** (new) | Fuel plant, from BIOMASS, anywhere | 0.5 |

`sim.Fuels` holds this table: `Fuels.ALL = [HYDROGEN, SYNFUEL]` (the draw order) and `Fuels.value(r)`.

A trip's cost is computed exactly as today, `0.5 × (dry mass + cargo) × distance × tech multiplier`, and is now read as **fuel value**. A trip on hydrogen alone burns exactly what it burned on FUEL before, so hydrogen-only play is unchanged. On synfuel the same trip burns twice the units.

"Most efficient" therefore shows up three ways: a unit of hydrogen goes twice as far; a tanker hauling hydrogen carries twice the range a tanker of synfuel does; and a gas-giant mine makes about twice the fuel value per level that a fuel plant does (§4.3).

### 4.2 Tanks and drawing fuel

- `Ship.fuel` (one number) becomes `Ship.tank`, an `EnumMap` with an amount of each fuel. `Ship.fuelValue()` is the sum of amount × value.
- **Departure from a colony.** If the tank's value doesn't cover the trip, the ship tops up from the colony: HYDROGEN first, then SYNFUEL (two units per unit of value missing). The abort rule is unchanged: if the colony can't cover the trip, the ship aborts and returns its cargo.
- **Burning.** The trip burns the tank's hydrogen first, then its synfuel.
- **Explorer tank.** The cap stays 100, measured in fuel value. Top-ups take hydrogen first, then synfuel; "Tank: 64 / 100 (40 H₂ + 48 SYN)".
- **Retiring or founding a colony** drains each fuel in the tank back into that resource at the colony (existing rule, per fuel).
- **Cargo.** Both are ordinary cargo; a tanker can haul either. Hauling synfuel costs as much fuel per unit of mass as anything else, which is why hauling hydrogen pays better.
- **Fuel checks.** `CommandPhase.fuelShortfall` and the Plan 6 popup compare the trip's value with what the colony and tank can supply in value: "Not enough fuel at Earth Hub for this trip: need ≈135, have 90 (60 hydrogen, 60 synfuel)".

Why hydrogen first: it is the simpler rule to explain and it means synfuel piles up where it's made, which is easy to see. The other order (save hydrogen for when synfuel runs out) is an open choice (§9.3).

### 4.3 The Fuel plant

| | |
|---|---|
| Input per level per day | 1.0 BIOMASS |
| Output per level per day | 1.0 SYNFUEL (0.5 fuel value) |
| Power draw | 2 per level (like every building) |
| Cost | 20 METAL, 10 COMPONENTS, 4 days |
| Available | from day one, not pre-built at Earth Hub (like the Furnace) |

Summary text: "Brews synfuel from biomass. Synfuel burns at half the value of hydrogen, but any colony with biomass can make it."

- **It runs after farms.** In `SiteEconomy` the fuel plant takes its biomass after every farm has planted, so a fuel plant can't starve a colony's food. If a farm needs the last of the biomass, the plant idles with the limit "Needs BIOMASS".
- **Comparison.** A gas-giant mine at L1 makes 2.0 × yield hydrogen a day (about 1.0 fuel value on a typical spot), twice an L1 fuel plant's 0.5. Earth Hub's farm grows roughly 1 BIOMASS a day more than it plants on seed 42, so one L1 fuel plant there uses about all of the surplus; a second farm feeds it.
- **Why biomass.** Earth Hub has no ice and falling water, but its living soil grows biomass, so a fuel plant there works on day one. That fixes the "Earth Hub can never make fuel" problem directly, and makes fuel compete with food, which is a decision worth making. Ice is the other candidate (§9.3).
- **Ledger and build preview** (Plan 7) show the plant like any other producer; `BuildForecast` gets a case for it.

### 4.4 The rename

`Resource.FUEL` becomes `Resource.HYDROGEN` everywhere: gas-giant mines, Earth Hub's starting 400, surveys (Plan 9 lists FUEL on gas giants; it now reads HYDROGEN), the Atmospheric Mining tech text ("Enables HYDROGEN extraction at gas giants"), the ledger, the play-tester and the tests. Player-facing labels say "hydrogen" or "H₂".

The empire strip's FUEL total becomes the **fuel value** across all colonies (hydrogen + half the synfuel), still labelled FUEL, with a tooltip splitting it: "FUEL value 480: 400 hydrogen, 160 synfuel".

### 4.5 Economy and parity

`production-parity.txt` changes in two mechanical ways only: every `FUEL` token becomes `HYDROGEN`, and each site gains a `SYNFUEL 0.000000000000e+00` line (the fixture lists every resource). No number changes. The task that does this checks it with a script that maps the old fixture to the expected new one and diffs.

## 5. Saving

Schema v5:
- Resource names: a `"FUEL"` key anywhere (stockpiles, caps, cargo, manifests) is read as `HYDROGEN`. Written as `HYDROGEN`.
- Ships: `"tank": {"HYDROGEN": 12.0, "SYNFUEL": 0.0}` replaces `"fuel"` (a v4 `"fuel"` loads as hydrogen); optional `"buildDaysLeft"` and `"yardBuildingId"`; state `"BUILDING"`.
- v4 and older saves load: their ships are all built, and their FUEL is hydrogen.

## 6. UI

- **Build ship dialog.** The class combo reads "HAULER · 30 METAL, 10 COMPONENTS · 20 days" (days at this colony's free shipyard's level). Below it, a line: "Built by Shipyard L1, ready in 20 days" or, when all are busy, the busy text with OK disabled.
- **Ship card (left column).** A ship under construction shows its drawing dimmed with a thin progress bar and "Building · 12 days left".
- **Detail panel, ship.** "Under construction at Earth Hub (Shipyard L1): 12 of 20 days left". Buttons: *Cancel build* (the retire command) only. A built ship's fuel line shows each fuel it holds: "Tank: 40 hydrogen, 48 synfuel (64 value)"; empty fuels are left out.
- **System map.** Ships under construction aren't drawn (they aren't flying and the docked marker would suggest a ready ship).
- **Dispatch.** The popup text from §4.2.
- **Building menu.** Fuel plant appears with its cost and the Plan 7 preview ("+1.0 SYNFUEL/day, −1.0 BIOMASS/day").

### 6.1 Lab and shipyard progress

Steve: *in the main view, a lab should have a visual indicating progress of the current item it is researching; a shipyard should similarly show the progress of the current ship build.*

The system map doesn't draw buildings today; colonies show as their body plus the colony cards and the right-hand building list. So progress shows in both places a player looks:

- **Building rows (right column, colony selected).** A research lab or a shipyard row gets a thin progress bar under its text, full row width (it wraps with the row, so it fits 330 px):
  - Lab: "Research lab L2   +2.0 research · Ion Drives 120 / 300" and a bar at 40%. Research is empire-wide, so every lab shows the same tech and bar; the text says what this lab adds. With nothing queued: "no research queued" and no bar; the row's tooltip says to pick a tech.
  - Shipyard: "Shipyard L1   building Hauler 3, 12 days left" and a bar at the ship's share of work done. An idle shipyard reads "idle" with no bar. A damaged one shows the bar greyed with "paused: damaged".
  - The same bar is drawn for buildings under construction or upgrade (Plan 8 already shows "building, 4 days left" as text), since it costs nothing extra and reads the same way.
- **System map (main view).** Next to each body that has a colony doing either, small badges sit below the body's label: a flask for active research (only on colonies with an operational lab) and a hull for a ship build, each with a progress ring around it. Hovering a badge gives the same text as the row. Clicking it selects the colony. Badges are placed with the label-collision code from PR #29 so they don't overlap Io and Europa.
- **Colony cards (left column).** No change; the ship card for a ship under construction already shows its countdown (§6).

`ui.ProgressBadge` paints the ring badge; `ui.ProgressRow` (or a bar inside the existing wrapping row) paints the row bar. Both read `World` only.

## 7. Events, debug and tutorial

- `SHIP_COMPLETED` INFO event, linked to the ship.
- Debug menu: Plan 8's *Finish construction* also finishes ships under construction.
- Tutorial: the "Build a colonizer" step completes when the colonizer is ordered (its text gains "It takes 40 days at Earth Hub's shipyard; press 16× to speed up"). A new step, "Wait for your colonizer", completes when it's ready. The dispatch step's fuel line says "hydrogen".
- The play-tester (`ContinuousPlayTester`) waits for ships to be built before dispatching, and its styles may build a fuel plant (the industrialist and expander styles first). It stays test-only.

## 8. Testing strategy

### 8.1 Sim (JUnit, headless)

- `ShipBuildTest`: ordering a hauler at Earth Hub creates a BUILDING ship with 20 days left and takes 30 METAL and 10 COMPONENTS; a colony short of METAL is rejected with the shortfall text and nothing is paid; cancelling refunds the cost; after 19 days it's still BUILDING, on day 20 it's IDLE and one `SHIP_COMPLETED` event fired; a level 2 shipyard finishes a tanker in 13 days; a damaged shipyard pauses it; a second order while the only shipyard is busy is rejected with the busy text; two shipyards build two ships at once; retiring a BUILDING ship removes it; dispatching one is rejected; demolishing its shipyard is rejected; *Fleet of 10* ignores BUILDING ships.
- `FuelsTest`: values; a hydrogen-only trip draws exactly what it drew before (`RefuelTest` and `TransitFuelCostTest` pass with FUEL renamed); a colony with 0 hydrogen and 300 synfuel sends a trip of value 100 using 200 synfuel; a mixed colony uses hydrogen first; the explorer tank fills to 100 value; retire and found drain each fuel; the fuel shortfall text.
- `FuelPlantTest`: 1 BIOMASS in, 1 SYNFUEL out per level; with too little biomass, farms are fed first and the plant reports "Needs BIOMASS"; power brownout slows it like other producers; `BuildForecast` preview numbers.
- Save: v5 round-trips a mixed tank and a BUILDING ship; the v1 fixture and a v4 save load with FUEL as HYDROGEN and ships built.
- `ProductionParityTest` passes with the mechanically updated fixture (§4.5); `DeterminismTest` covers a run with a ship build and a fuel plant.

### 8.2 UI (JUnit; frames under Xvfb)

- `BuildShipDialog`: class labels with days; busy message disables OK.
- `DetailPanel`: a BUILDING ship shows its progress and only *Cancel build*; a tank with both fuels lists both.
- `ColonyListPanel`: a BUILDING ship card shows "Building · N days left"; the empire FUEL total is the fuel value.
- `DetailPanel`: a lab row shows the active tech's name, points and a bar at the right fraction; a shipyard row shows its ship and days left; idle ones show no bar.
- `SystemMapPanel`: with research active and a ship building at Earth Hub, Earth has a flask badge and a hull badge whose ring fractions match; hit-testing a badge selects Earth Hub; no badges when idle.
- Tutorial: the new wait step; `startupPlayTest` and `playTest` drivers wait for the colonizer.

### 8.3 Test count estimate

About 36 new tests.

### 8.4 Manual play-test checklist

1. New game: Earth Hub shows 400 hydrogen. Order a colonizer; its card counts down 40 days and an event says it's ready.
2. Try ordering a second ship while the shipyard is busy: the dialog says why.
3. Upgrade the shipyard to L2 and order a hauler: 10 days.
4. Build a fuel plant at Earth Hub; synfuel starts to pile up, and Earth Hub's food is unaffected.
5. Run Earth Hub's hydrogen to 0 and dispatch a hauler: it leaves on synfuel, using twice the units the old popup quoted.
6. Save and reload mid-build: the ship is still building.
7. Queue a tech: Earth Hub's lab row shows its progress bar, and Earth gets a flask badge on the system map; while a ship builds, the shipyard row and a hull badge fill up.

## 9. Risks, decisions and open choices

### 9.1 Decisions made here (defaults)

- **Rename FUEL to HYDROGEN** rather than keep FUEL and add a second fuel next to it, so the player sees two named fuels.
- **Fuel value is the unit** for trip costs and the explorer tank, so hydrogen-only play and every existing fuel number is unchanged.
- **Synfuel is worth half** a unit of hydrogen.
- **The Fuel plant uses BIOMASS** and runs after farms.
- **No queue:** one ship per shipyard, more shipyards for more at once.
- **Shipyard level speeds builds** (work-days drop by the level each day).
- **Ships cost METAL and COMPONENTS** (§3.5), Steve's change on review.

### 9.2 Risks

- **Synfuel may be too slow or too fast.** An L1 plant makes 0.5 fuel value a day; a colonizer to Mars needs about 135, so one L1 plant takes most of a year (about 15 minutes at 1×) to fuel one. That is slow on purpose, so hydrogen stays worth fetching, but the rate is one constant and the play-tester will tell us if colonies still run dry.
- **Biomass competition.** Running after farms protects food, but a colony that ships its biomass out can leave a fuel plant idle. The ledger says why.
- **The rename touches many files** (about 17 test files and 13 UI places mention FUEL). It is mechanical, and done as its own first task with the parity check, before anything else changes.
- **Tutorial pacing.** A 40-day colonizer adds two minutes at 1×. The new step tells the player to speed up.

### 9.3 Open choices for Steve

1. Build days: explorer 10, hauler 20, tanker 25, colonizer 40 (default), or other numbers?
2. One ship per shipyard (default), or a queue per colony that builds them in order?
3. ~~Should ships also cost METAL and COMPONENTS now?~~ Yes (Steve), see §3.5.
4. Fuel plant input: BIOMASS (default), ICE (makes icy moons fuel depots, but Earth Hub has none), or WATER?
5. Synfuel worth half of hydrogen (default), or another ratio?
6. Burn hydrogen first (default) or synfuel first, to save hydrogen?
7. Progress visuals in both the building list and as system-map badges (default), or only one of them?

## 10. What's next

A shipyard build queue; a per-colony fuel preference; a synfuel tech (better yield, or ICE as a second recipe); fuel depots and automatic tanker routes.
