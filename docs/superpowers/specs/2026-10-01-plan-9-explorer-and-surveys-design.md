# Plan 9 — Explorer Ships and Surveys

**Date:** 2026-10-01
**Status:** Design, awaiting Steve's approval. Nothing is implemented yet.
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–8 merged to `main` (last: Plan 8 building costs, PRs #24 and #25), plus play-tester and UI PRs through #28. About 450 tests.
**Source spec:** `2026-05-03-space-colony-game-design.md` §3.2 (bodies carry per-resource yield maps baked at world-gen), §3.4 (ship classes), §7 (detail panel, body view).

## 1. Vision

Every body already has a resource yield map: a value from 0 to 1 per resource at every latitude and longitude, baked from the seed. A mine's ORE, SILICATE and ICE, a gas-giant mine's FUEL and a farm's soil BIOMASS all come from the map at the colony's exact spot. But the player can't see any of it. The only place yields show is the debug map overlay. So:

1. **Choosing where to settle is blind.** The colonizer reaches Mars and the player clicks somewhere on the globe. Nothing says whether that spot is ore-rich or barren.
2. **The spot matters far more than the body.** On seed 42 every rocky planet averages about 0.5 ORE, but within one planet the ORE yield runs from under 0.1 to 0.85. Earth Hub sits on 0.19 ORE while Earth's richest ground is 0.85. Knowing "Mars has ore" is useless; knowing *where* on Mars is the whole game.
3. **Plan 8 made it urgent.** Buildings now cost METAL, and Earth Hub's poor ground makes METAL slow. The obvious fix, a colony on rich ore, is a guess today.

Plan 9 adds a way to find out:

1. **An Explorer ship class.** Small, fast, no cargo. It is built at a shipyard like any ship and dispatched to any body, settled or not.
2. **Surveys.** When an explorer reaches a body, the body is surveyed, for good. Earth starts surveyed, and founding a colony surveys its body.
3. **Hopping.** An explorer leaves a colony with a full tank and can fly from body to body without docking, surveying as it goes, until it needs to come back to a colony to refuel.
4. **What a survey shows.**
   - The body's detail panel lists each resource the ground holds with its average and its best spot.
   - The body view gets a resource overlay: pick ORE (or SILICATE, ICE, BIOMASS, FUEL) and the globe is tinted by yield, so rich ground is visible at a glance, with a readout of the yields under the mouse.
   - The place-site dialog shows what the ground at the clicked spot yields before the player commits the colonizer.
   - A colony's detail panel shows its own ground ("Ground: ORE .19 · SIL .45 · ICE .10 · BIO .21"), which explains a slow mine.
5. **Unsurveyed means unknown.** An unsurveyed body says "Resources unknown. Send an explorer to survey it." Its overlay is off. A colonizer can still found a colony there blind, and the place-site dialog says the yields are unknown.

**Out of scope:** survey techs (better scanners, remote surveys from a telescope); partial or noisy surveys; explorers finding anything besides yields (anomalies, derelicts, events); a fog of war on the system map; ship costs (still a later plan, per Plan 8 §9.1).

## 2. Architecture overview

### 2.1 New and changed files

```
spacecolony/
├── sim/
│   ├── ShipClass.java              (CHANGED: + EXPLORER; + tank capacity)
│   ├── Transit.java                (CHANGED: + originBodyId, for a trip that starts in orbit)
│   ├── World.java                  (CHANGED: + surveyedBodies, isSurveyed(), survey())
│   ├── ResourceSurvey.java         (NEW: per-body average and best yield per resource; moved from debug.YieldSummary)
│   ├── EventKind.java              (CHANGED: + BODY_SURVEYED)
│   └── phases/
│       ├── CommandPhase.java       (CHANGED: explorers go to any body, and leave from orbit; founding surveys)
│       └── TransitPhase.java       (CHANGED: explorer arrival surveys; tank fill; orbit departure)
├── save/SaveFile.java              (CHANGED: schema v4, surveyedBodies and transit originBodyId; v3 still loads)
├── world/WorldGenerator.java       (CHANGED: Earth starts surveyed)
├── debug/
│   ├── YieldSummary.java           (CHANGED: thin wrapper over ResourceSurvey, or deleted)
│   └── DebugActions.java           (CHANGED: "Survey all bodies")
├── render/YieldOverlay.java        (NEW: tints a flat surface map by one resource's yield)
└── ui/
    ├── DetailPanel.java            (CHANGED: body Resources section; site Ground line; orbiting explorer gets Dispatch)
    ├── BodyViewPanel.java          (CHANGED: overlay picker and hover readout)
    ├── dialogs/PlaceSiteDialog.java     (CHANGED: yields at the clicked spot)
    └── dialogs/DispatchShipDialog.java  (CHANGED: explorer destinations, no manifest)
```

The system map (`SystemMapPanel`) only changes where it reads a transit's origin (§3.4). Its labels and drawing stay as they are, so this plan doesn't collide with PR #29 (Jovian labels) or PR #27 (left column).

### 2.2 Layering and threading

Unchanged. Survey state lives on `World` and changes only in the sim phases. `ResourceSurvey` sits in `sim` next to `ResourceYieldSampler` and reads yields through that interface, so `sim` still doesn't import `world`. The UI reads `World.isSurveyed` and `ResourceSurvey`, and only enqueues commands.

## 3. The rules

### 3.1 The Explorer

| Class | Dry mass | Cargo | Speed (AU/day) | Tank |
|---|---|---|---|---|
| Hauler | 50 | 200 | 0.5 | trip only |
| Tanker | 40 | 300 | 0.4 | trip only |
| Colonizer | 80 | 100 | 0.3 | trip only |
| **Explorer** | **20** | **0** | **0.8** | **100 FUEL** |

`ShipClass` gains a `tankCap()`: 100 for EXPLORER, 0 for the rest, meaning "draws only this trip's fuel", which is today's behaviour. Fuel per trip is the existing formula, `0.5 × dry mass × distance × tech multiplier`, so an explorer burns a quarter of a colonizer's fuel per AU. Rough costs from Earth, depending on where the planets are: Mars 5 to 25, a belt body 15 to 40, Jovian 40 to 60, and a Jovian moon from Jovian about 0. So a full tank covers two or three inner hops, or the Jovian system and a trip back to an inner colony, depending on alignment.

Explorers are free and need an operational shipyard, like every ship (Plan 8 left ship costs for later). They appear in the build-ship dialog automatically.

### 3.2 Surveys

- `World.surveyedBodies` is a sorted set of body ids. `World.isSurveyed(bodyId)` and `World.survey(bodyId, shipName)`; the second adds the id and, if it was new, emits a `BODY_SURVEYED` INFO event.
- **At start** Earth is surveyed (it has Earth Hub). The tutorial world is the same.
- **Explorer arrival.** When an explorer's trip ends, at a body or at a site, its body is surveyed that tick. The event names the best ground: "Scout-1 surveyed Mars. Best spots: ORE .83, SILICATE .78, BIOMASS .31."
- **Founding a colony** surveys its body (the colonists are standing on it). A colonizer merely orbiting a body does not survey it; that is the explorer's job.
- Surveys never expire and never change. The yield maps are fixed at world-gen, so a survey is exact, not an estimate.

### 3.3 What a survey reveals

`ResourceSurvey.of(Body)` samples the body's yield map on a fixed 8 × 16 latitude/longitude grid (the grid `debug.YieldSummary` uses today) and returns, per resource, the average and the best sample with its latitude and longitude. It covers the resources the ground feeds: ORE, SILICATE, ICE, BIOMASS and FUEL. Resources whose best sample is under 0.02 are left out ("none"). Results are cached per body id and cleared on `WorldReplaced`.

Each entry gets a word from its best value, so a body reads at a glance: **Rich** ≥ 0.6, **Good** ≥ 0.4, **Fair** ≥ 0.2, **Poor** below that. The word is about the best spot, because that is where a colony would go.

### 3.4 Flying from orbit

Today a ship in orbit (a colonizer at an unsettled body) can only found a colony or be retired; dispatch refuses it. An explorer in orbit can be dispatched:

- **Departure.** A dispatch from orbit has no site to load at, so `CommandPhase` sends the ship straight into transit that tick: it computes the arrival tick and the fuel cost from the orbited body's position, takes the fuel from the tank, and sets `Transit(originSiteId = null, originBodyId = <body>, …)`. `Transit` gains `originBodyId` and an `originBody(World)` helper that mirrors `destBody`. Everything that reads a transit's origin position (`TransitPhase.computeArrivalTick`, the two loops in `SystemMapPanel`) uses `originBody`.
- **Fuel from orbit** comes only from the tank. `CommandPhase.fuelShortfall` checks the tank when the ship isn't at a site: "Not enough fuel in Scout-1's tank: need ≈34, have 20. Send it to a colony to refuel."
- **Fuel from a colony.** On departure from a site, an explorer tops its tank up to 100 from the site's FUEL (after this trip's cost is covered; if the site can't cover the trip, the trip aborts as today). Other classes are unchanged.
- **Destinations.** An explorer can go to any site or any body. Arriving at a site, it docks idle with its tank as it was; arriving at a body, it orbits.
- **Retiring** an explorer at a site drains its tank into the site's FUEL (existing rule). Retiring it in orbit loses the fuel (as for a colonizer today).

### 3.5 Commands

No new commands. `DispatchShipCommand.toBody` now accepts EXPLORER as well as COLONIZER, and `applyDispatchShip` accepts an orbiting explorer. A manifest for an explorer must be empty (the existing cargo check already rejects anything above 0).

### 3.6 Economy and parity

Nothing in production changes. Surveys only reveal what the sim already uses. `production-parity.txt` must stay byte-identical.

### 3.7 Saving

Schema v4:
- World: `"surveyedBodies": ["earth", "mars"]` (sorted).
- Transit: optional `"originBodyId"`.
- Ships: class `"EXPLORER"` is just a new enum name.

A v3 save loads with every body that has a site marked surveyed, and no other.

## 4. Detail panel

### 4.1 A body

Below the mini globe and *Open body view*, a **Resources** section:

```
Resources (surveyed)
  ORE        Rich   avg .51  best .85
  SILICATE   Rich   avg .48  best .78
  BIOMASS    Fair   avg .19  best .33
  ICE        Poor   avg .10  best .15
```

Rows are sorted by best value. Each row's tooltip says where the best spot is ("Best ORE at 23°N 104°W") and that the body view can show it. An unsurveyed body shows one dim line: "Resources unknown. Send an explorer to survey it." The section uses the right column's wrapping rows, so it fits 330 px (per PR #28).

### 4.2 A colony

Under the "Body: … · pop" line, one dim line with the ground at the colony's spot, ORE, SIL, ICE, BIO and (on a gas giant) FUEL, omitting zeros: "Ground: ORE .19 · SIL .45 · ICE .10 · BIO .21". Its tooltip: "Yield at this colony's spot (0 to 1). Mines and farms scale with it."

### 4.3 A ship

An explorer's fuel line reads "Tank: 64 / 100 FUEL". An explorer in orbit reads "Orbiting Mars" and gets the *Dispatch...* button (a colonizer in orbit keeps *Found colony…*).

## 5. Body view

- **Overlay picker.** The top bar gets a combo next to *Back to system map*: Surface, ORE, SILICATE, ICE, BIOMASS, FUEL (only the resources the body's survey lists). On an unsurveyed body the combo shows "Unsurveyed" and is disabled, with the tooltip "Send an explorer to survey this body."
- **The overlay.** `render.YieldOverlay.tint(flatMap, sampler, resource)` returns a copy of the body's flat surface map blended with a heat ramp: dark and transparent where the yield is near 0, through amber, to bright where it is 1, at about 60% opacity so the terrain still reads. Computed once per (body, resource) and cached with the flat maps (cleared on `WorldReplaced`). The sphere renders the tinted map exactly as it renders the plain one. The flat map's pixel to (lat, lon) mapping must be the same one `SphereRenderer.unproject` returns, and a test pins that.
- **Readout.** With the overlay on, moving the mouse over the globe shows a label under it: "Here: ORE .72 · SIL .40 · ICE .10 · BIO .25". Off the globe, the label is blank.
- **Colony markers** are out of scope (the body view doesn't draw sites today).

## 6. Dialogs

- **Place site.** Adds rows for the clicked spot's ORE, SILICATE, ICE, BIOMASS (and FUEL on a gas giant) with the same Rich/Good/Fair/Poor words. On an unsurveyed body: "Yields here: unknown (unsurveyed)". The dialog still lets the player place the colony.
- **Dispatch.** For an explorer: every site, then every body as "Mars (unsurveyed)" or "Mars (surveyed)", and no manifest fields (a note says "Explorers carry no cargo"). For a colonizer, unsettled bodies read "Mars (unsettled, unsurveyed)" when that's true. The fuel popup from Plan 6 also covers the explorer's tank shortfall.

## 7. Events, debug and tutorial

- `BODY_SURVEYED` INFO event, linked to the body (clicking it selects the body).
- Debug menu: **Survey all bodies** (while paused, like Plan 8's Finish construction). The debug map's yield labels keep showing every body, surveyed or not.
- `debug.YieldSummary` becomes a thin adapter over `ResourceSurvey` (or is folded into it), so there is one sampling grid.
- Tutorial: unchanged. Its colonizer still flies to Mars; Mars is unsurveyed, and the place-site dialog says so. A later tutorial step about explorers is a candidate follow-up.

## 8. Testing strategy

### 8.1 Sim (JUnit, headless)

- `ResourceSurveyTest`: averages and best values match a brute-force sample of the grid; a gas giant lists only FUEL; Europa lists ICE first; resources under 0.02 are omitted; the rating thresholds.
- `SurveyTest`: Earth is surveyed at start and nothing else is; an explorer arriving at Mars surveys it and emits one `BODY_SURVEYED` event; a second arrival emits nothing; founding a colony surveys its body; a colonizer arriving in orbit does not.
- `ExplorerTransitTest`: an explorer dispatched from Earth Hub tops its tank to 100 and the site's FUEL drops by the trip cost plus the top-up; it hops Mars → Belt-A from orbit using only its tank; a hop it can't afford is rejected with the tank message; arrival at a site docks it idle with its tank intact; hauler, tanker and colonizer fuel draws are unchanged (`RefuelTest` and `TransitFuelCostTest` still pass untouched).
- `CommandTest` additions: a hauler can't go to a body; an orbiting colonizer still can't be dispatched; an explorer manifest above 0 is rejected.
- Save: v4 round-trips surveyed bodies and an orbit-departure transit; a v3 fixture loads with exactly the settled bodies surveyed.
- `ProductionParityTest` passes with the fixture untouched; `DeterminismTest` covers a run with an explorer.

### 8.2 UI (JUnit; frames under Xvfb)

- `DetailPanel`: a surveyed body lists its resources best-first; an unsurveyed one shows the unknown line; a colony shows its Ground line; an orbiting explorer has *Dispatch...*.
- `BodyViewPanel`: the picker is disabled on an unsurveyed body and lists the surveyed body's resources; `YieldOverlayTest` checks a pixel at a known (lat, lon) is tinted by that spot's yield, and that unproject and the flat-map mapping agree.
- `DispatchShipDialogTest`: an explorer's destinations include every body with its survey tag.
- `PlaceSiteDialog`: the yield rows at a spot, and the unknown line.

### 8.3 Play-test drivers

`PlayTestDriver` builds an explorer at Earth Hub, surveys Mars, and places the Mars colony on Mars's best ORE spot instead of a fixed latitude. Screenshots for the PR: the body panel's Resources section, the body view with the ORE overlay, and the place-site dialog.

### 8.4 Test count estimate

About 35 new tests, to roughly 485.

### 8.5 Manual play-test checklist

1. New game: Earth's body panel lists resources; Mars says unknown.
2. Build an explorer at Earth Hub; dispatch it to Mars. On arrival a "surveyed Mars" event appears and Mars's panel fills in.
3. Open Mars's body view, pick ORE, and find the bright patch; the readout follows the mouse.
4. Dispatch the explorer from Mars orbit to Belt-A; then try a hop it can't afford and see the tank popup.
5. Send a colonizer to Mars, click the bright patch, and see Rich ORE in the place-site dialog.
6. Save and reload: surveys persist.

## 9. Risks, decisions and open choices

### 9.1 Decisions made here (defaults)

- **A new ship class, not a building or a tech.** Steve's ask; it also gives haulers' fuel network a new customer.
- **Surveys are instant on arrival and exact.** The flight is the cost. A survey that takes days in orbit, or gets sharper with a tech, is easy to add later.
- **Founding a colony surveys its body; a colonizer in orbit does not.** Keeps the explorer's job distinct while never hiding ground a colony already stands on.
- **Explorers hop between bodies on a 100-FUEL tank.** Without hopping, every survey is a round trip from a colony, which is tedious.
- **Explorers are free**, like all ships until ship costs land.
- **Old saves:** settled bodies count as surveyed.
- **System map unchanged** (no "?" markers), to stay clear of PRs #27 and #29.

### 9.2 Risks

- **Fuel.** Earth Hub starts with 400 FUEL and makes none, and an explorer can take up to 100 of it at once. A survey trip to Mars and the belt can take a quarter of that. `ExplorerTransitTest` pins the numbers; the tank size is one constant.
- **Overlay cost.** Tinting a 1024 × 512 map samples the yield noise half a million times. It runs once per (body, resource) and is cached; if it is noticeably slow it can sample at half resolution and scale up.
- **The play-tester** keeps working unchanged (it never dispatches to a body except with colonizers), but it won't benefit until its script builds explorers.

### 9.3 Open choices for Steve

1. Should a colonizer arriving in orbit survey the body too (default: no)?
2. Tank of 100 FUEL with body-to-body hops (default), or explorers that only make round trips from a colony?
3. Survey instantly on arrival (default), or after a few days in orbit?
4. Add a goal for surveying (for example "Survey 4 bodies") (default: no)?
5. Mark unsurveyed bodies on the system map (default: not in this plan)?

## 10. What's next

Survey techs (remote survey from Earth for nearby bodies, deeper scans); a tutorial step that sends an explorer before the colonizer; explorer events (derelicts, anomalies); colony markers on the body view; ship costs.
