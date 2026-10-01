# Space Colony — Plan 9: Explorer Ships and Surveys

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the player learn what an unsettled world's ground yields. A new EXPLORER ship surveys any body it reaches; surveyed bodies show their resources in the detail panel, as a yield overlay in the body view, and at the clicked spot in the place-site dialog. Unsurveyed bodies say their resources are unknown.

**Architecture:** `World.surveyedBodies` (sorted set) is the only new state. `TransitPhase` surveys a body when an explorer arrives; `CommandPhase` surveys a body when a colony is founded on it. `sim.ResourceSurvey` (moved out of `debug.YieldSummary`) computes each body's average and best yield per resource. Explorers carry a 100-FUEL tank and can be dispatched from orbit; `Transit` gains `originBodyId` for those trips. UI reads `World.isSurveyed` and `ResourceSurvey`; `render.YieldOverlay` tints the body view. Save schema goes to v4.

**Tech Stack:** Java 25, Gradle 9 via wrapper, JUnit 5, Swing. No new dependencies.

**Spec reference:** `docs/superpowers/specs/2026-10-01-plan-9-explorer-and-surveys-design.md` (sections 1–10).

**Out of scope:** survey techs, partial surveys, explorer events, system-map fog, ship costs. See the design doc §1 and §9.

---

## Context

Plans 1–8 are on `main` (last: PR #28, 2026-10-01). About 450 tests. GUI tests that build a frame need `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test` and skip themselves when headless. CI runs the full suite under xvfb on JDK 25.

Key facts about the existing code that this plan relies on:

- `Body.resourceYields` is a `ResourceYieldSampler` (`sample(Resource, lat, lon)` → 0..1). `SiteEconomy` reads ORE, SILICATE, ICE and (gas giants) FUEL for mines and BIOMASS for farms, at the site's `lat`/`lon`.
- The only player-visible yields today are the debug map labels from `debug.YieldSummary` (8 × 16 grid mean, top 3).
- `ShipClass` is `HAULER, TANKER, COLONIZER` with `dryMass`, `cargoCap`, `speed`. `BuildShipDialog` lists `ShipClass.values()`.
- `DispatchShipCommand` has `destSiteId` or `destBodyId`; `applyDispatchShip` rejects orbiting ships and non-colonizers going to a body. Dispatch puts the ship in LOADING at `currentSiteId`; `TransitPhase.loadingAndUnloading` departs it, drawing the trip's fuel shortfall from the origin's FUEL.
- `TransitPhase.advanceTransits`: a trip with `destBodyId` ends IDLE with `orbitingBodyId` set; a site trip ends UNLOADING.
- `Transit` origin is `originSiteId` only; `SystemMapPanel` (two loops) and `computeArrivalTick` look up the origin site's body.
- `applyBuildSite` adds the site and consumes the colonizer. `applyRetireShip` drains tank and cargo into a dock site if any.
- `SaveFile.SCHEMA_VERSION = 3`, `MIN_READABLE_VERSION = 1`.
- `DetailPanel.renderBody` shows name, type, site count, mini globe, *Open body view*. `renderShip` gives an orbiting ship *Found colony…* and no *Dispatch...*.
- `BodyViewPanel` caches a 1024 × 512 `PlanetGenerator` flat map per body and renders it with `SphereRenderer.render`; clicks unproject to (lat, lon) and open `PlaceSiteDialog`.

---

## File Structure

### Create

```
src/main/java/spacecolony/
├── sim/ResourceSurvey.java
└── render/YieldOverlay.java

src/test/java/spacecolony/
├── sim/ResourceSurveyTest.java
├── sim/SurveyTest.java
├── sim/ExplorerTransitTest.java
└── render/YieldOverlayTest.java
```

### Modify

- `sim/ShipClass.java`, `sim/Transit.java`, `sim/World.java`, `sim/EventKind.java`
- `sim/phases/CommandPhase.java`, `sim/phases/TransitPhase.java`
- `save/SaveFile.java`, `world/WorldGenerator.java` (the tutorial world comes from it too)
- `debug/YieldSummary.java`, `debug/DebugActions.java`, `debug/DebugMenu.java`
- `ui/DetailPanel.java`, `ui/BodyViewPanel.java`, `ui/SystemMapPanel.java` (origin lookup only), `ui/dialogs/PlaceSiteDialog.java`, `ui/dialogs/DispatchShipDialog.java`
- Tests: `CommandTest`, `DeterminismTest`, `SaveFileSchemaTest`, `SaveFileLoadTest`, `YieldSummaryTest`, `DispatchShipDialogTest`, `PanelSmokeTest`, `playtest/PlayTestDriver.java`
- Docs: the design doc's status line; game-design spec §3.4 gets the Explorer row.

---

## Tasks

Tasks 1–6 are Part A: the sim (headless, test-first). Tasks 7–11 are Part B: the UI. Task 12 is Part C: driver, docs, PR.

Every task ends with `./gradlew test` green (under `xvfb-run` for tasks that touch frames) and a commit in the existing `feat(<pkg>): …` / `test(<pkg>): …` / `refactor(<pkg>): …` style. **`production-parity.txt` must not change in any task.**

### Task 0: Branch

- [ ] Create `claude/plan-9-explorer-<suffix>` from the latest `main`.

---

## Part A — The sim

### Task 1: ResourceSurvey

**Files:** Create `sim/ResourceSurvey.java`, `test/.../sim/ResourceSurveyTest.java`. Modify `debug/YieldSummary.java`, `test/.../debug/YieldSummaryTest.java`.

- [ ] **Step 1: Failing tests.** On seed 42: Earth's entries are ORE, SILICATE, BIOMASS, ICE in that order (best-first), Earth's ORE best ≈ 0.85 and average ≈ 0.51 (match a brute-force loop over the same grid to 1e-12); Jovian has only FUEL; Europa's first entry is ICE; nothing with best < 0.02 appears; `rating(0.6) == RICH`, `rating(0.59) == GOOD`, `rating(0.4) == GOOD`, `rating(0.2) == FAIR`, `rating(0.19) == POOR`. A body with null `resourceYields` returns an empty survey.
- [ ] **Step 2: Implement.**

```java
/** What a survey of one body shows: per resource, the average and the best spot on a fixed grid. */
public final class ResourceSurvey {
    public static final List<Resource> SURVEYED = List.of(ORE, SILICATE, ICE, BIOMASS, FUEL);
    public static final double NONE_BELOW = 0.02;
    public enum Rating { RICH, GOOD, FAIR, POOR }
    public record Entry(Resource resource, double average, double best, double bestLat, double bestLon) {
        public Rating rating() { return ResourceSurvey.rating(best); }
    }
    public static List<Entry> of(Body b);        // sorted by best, descending; cached by body id
    public static Rating rating(double y);       // ≥.6 RICH, ≥.4 GOOD, ≥.2 FAIR, else POOR
    public static void clearCache();
}
```

The grid is the one `YieldSummary.compute` uses today (lat `-π/2 + π/16 + i·π/8`, i < 8; lon `-π + π/16 + j·π/8`, j < 16). The cache is a static map keyed by body id **plus surface seed**, so a replaced world never reads a stale entry and nothing needs to clear it (keep `clearCache` for tests).
- [ ] **Step 3:** `YieldSummary.top(b, k)` keeps its signature and output (mean of every stockpileable resource, top k) so the debug labels and `YieldSummaryTest` are unchanged; just share the grid constants with `ResourceSurvey`. Commit `feat(sim): ResourceSurvey computes a body's average and best yields`.

### Task 2: Survey state

**Files:** Modify `sim/World.java`, `sim/EventKind.java`, `world/WorldGenerator.java`, `sim/phases/CommandPhase.java` (`applyBuildSite`). Create `test/.../sim/SurveyTest.java`.

- [ ] **Step 1: Failing tests.** A generated world has exactly `{"earth"}` surveyed. `w.survey("mars", "Scout-1")` returns true, adds Mars and emits one `BODY_SURVEYED` INFO event with body id `mars` whose text starts "Scout-1 surveyed Mars. Best spots: ORE .8"; a second call returns false and emits nothing. Founding a colony on Mars (existing `ColonizeTest` setup) surveys Mars. A colonizer arriving in Mars orbit does not.
- [ ] **Step 2: Implement.** `public final SortedSet<String> surveyedBodies = new TreeSet<>();`, `isSurveyed(String)`, `boolean survey(String bodyId, String byWhom)`. The event text lists the top three entries of `ResourceSurvey.of(body)` as `NAME .83` (leading zero dropped, as `YieldSummary.format`), comma-separated; a body with no entries says "No useful resources." `WorldGenerator` calls `w.surveyedBodies.add("earth")` directly (no event at start). `applyBuildSite` calls `w.survey(body.id, bsc.name())` after adding the site.
- [ ] **Step 3:** Commit `feat(sim): bodies can be surveyed; Earth starts surveyed, founding a colony surveys`.

### Task 3: The Explorer class and its tank

**Files:** Modify `sim/ShipClass.java`, `sim/phases/TransitPhase.java`, `sim/phases/CommandPhase.java` (`fuelShortfall`). Create `test/.../sim/ExplorerTransitTest.java`.

- [ ] **Step 1: Failing tests.** `EXPLORER` has dry mass 20, cargo 0, speed 0.8, tank 100; the other classes have tank 0. An explorer dispatched from Earth Hub to the Mars **site** (use the `ColonizeTest`/`RefuelTest` helpers to make one) leaves with `fuel == 100 − tripCost` (± 1e-9) and Earth Hub's FUEL drops by exactly 100 (its starting tank was 0). With only 30 FUEL at Earth Hub and a 20-FUEL trip, it leaves with 10 and Earth Hub ends at 0. A hauler's draw is unchanged (`RefuelTest` passes untouched). `fuelShortfall` for an explorer at a site counts the top-up as available; unchanged for others.
- [ ] **Step 2: Implement.** `ShipClass` gains a fourth constructor arg `tankCap` and `tankCap()`. In `loadingAndUnloading`, after the existing shortfall draw and the abort check, if `s.shipClass.tankCap() > 0` draw `min(tankCap − (s.fuel − cost), originFUEL)` more, then subtract the cost as today. Record the draw on the ledger with the existing `Shipping.Kind.FUEL` line (one combined line is fine).
- [ ] **Step 3:** Commit `feat(sim): Explorer ship class with a 100-FUEL tank`.

### Task 4: Explorers fly to bodies and survey on arrival

**Files:** Modify `sim/phases/CommandPhase.java` (`applyDispatchShip`), `sim/phases/TransitPhase.java` (`advanceTransits`), tests in `SurveyTest`, `ExplorerTransitTest`, `CommandTest`.

- [ ] **Step 1: Failing tests.** `toBody` with an explorer is accepted; with a hauler it is still rejected ("Only colonizers and explorers can travel to a body without a site"). An explorer arriving at Mars (body) ends IDLE orbiting Mars, Mars is surveyed, and the arrival event reads "Explorer Scout-1 is orbiting Mars". An explorer arriving at a site on an unsurveyed body surveys it (construct a site directly on Belt-A in the test). An explorer manifest of 1 ORE is rejected by the existing cargo check.
- [ ] **Step 2: Implement.** In `advanceTransits`, after either arrival branch, `if (s.shipClass == EXPLORER) w.survey(bodyId, s.name)`; make the orbit event's noun follow the class ("Colonizer" / "Explorer"). Relax the class check in `applyDispatchShip`.
- [ ] **Step 3:** Commit `feat(sim): explorers survey the body they reach`.

### Task 5: Dispatch from orbit

**Files:** Modify `sim/Transit.java`, `sim/phases/CommandPhase.java`, `sim/phases/TransitPhase.java`, `ui/SystemMapPanel.java` (origin lookup only). Tests in `ExplorerTransitTest`, `CommandTest`, `TransitMathTest`.

- [ ] **Step 1: Failing tests.** An explorer orbiting Mars with 60 FUEL dispatched to Belt-A goes IN_TRANSIT on the same tick (no LOADING), `transit.originSiteId() == null`, `originBodyId() == "mars"`, fuel is 60 − cost, and the arrival tick equals what `computeArrivalTick` gives from Mars at that tick. With 5 FUEL the command is rejected with "Not enough fuel in Scout-1's tank: need ≈N, have 5. Send it to a colony to refuel." An orbiting colonizer is still rejected with today's message. An explorer orbiting Mars dispatched to the Earth Hub site arrives, unloads nothing, and is IDLE at the site with its remaining fuel.
- [ ] **Step 2: Implement.**
  - `Transit` gains `String originBodyId` as its second component; the existing constructors pass null. Add `originBody(World w)` returning `originBodyId` or the origin site's body. Update every `new Transit(…)` call in main and test code (the canonical constructor's arity changes).
  - `computeArrivalTick` and `fuelCostBetweenBodies` callers use `s.transit.originBody(w)`. `SystemMapPanel`'s two in-transit loops use `t.originBody(world)` instead of `findSite(t.originSiteId()).bodyId`; its debug fuel estimate uses `fuelCostBetweenBodies` via a new public `TransitPhase.fuelCostBodies(w, class, mass, originBody, destBody, t0, t1)`.
  - `applyDispatchShip`: an orbiting EXPLORER goes through a new `TransitPhase.departFromOrbit(w, s, destSiteId, destBodyId)`, which computes arrival and cost from the orbited body, rejects (via a returned message the command phase throws) if `s.fuel < cost`, otherwise deducts, sets the transit, clears `orbitingBodyId`, sets IN_TRANSIT and emits `SHIP_DEPARTED`.
  - `fuelShortfall`: when the ship has no site but orbits a body, estimate from that body and compare with `s.fuel` only, with the tank message.
- [ ] **Step 3:** Commit `feat(sim): explorers can leave orbit for another body or a colony`.

### Task 6: Save schema v4 and debug action

**Files:** Modify `save/SaveFile.java`, `debug/DebugActions.java`, `debug/DebugMenu.java`; tests `SaveFileSchemaTest`, `SaveFileLoadTest`, `DebugDialogsTest` (or a new `SurveyAllTest`), `DeterminismTest`.

- [ ] **Step 1: Failing tests.** v4 round-trip keeps `surveyedBodies` (sorted) and an in-transit explorer with `originBodyId`; the written JSON has `"surveyedBodies"`. A v3 save (write one with the v3 code path or a fixture string) loads with exactly the bodies that have sites surveyed. `DeterminismTest` gains a run that builds an explorer and sends it to Mars, and two runs match. Debug "Survey all bodies" surveys every body and emits one event per newly surveyed body.
- [ ] **Step 2: Implement.** `SCHEMA_VERSION = 4`; write `surveyedBodies` as a sorted array and `originBodyId` with `optStr`; on read, if the key is missing (v ≤ 3) add every body with sites. Debug action follows Plan 8's *Finish construction* pattern (only while paused, via the debug edit path).
- [ ] **Step 3:** Commit `feat(save): schema v4 saves surveys and orbit departures`.

---

## Part B — The UI

### Task 7: Body detail Resources section

**Files:** Modify `ui/DetailPanel.java`; tests in `PanelSmokeTest` (or a new `DetailPanelSurveyTest`).

- [ ] **Step 1: Failing tests.** Selecting Earth shows "Resources (surveyed)" then rows starting `ORE`, `SILICATE`, `BIOMASS`, `ICE`, each with a rating word and `avg .xx  best .xx`; the ORE row's tooltip names the best spot in degrees ("Best ORE at 23°N 104°W"). Selecting Mars shows "Resources unknown. Send an explorer to survey it." The panel's preferred width stays ≤ 330 px (reuse PR #28's width assertion).
- [ ] **Step 2: Implement** with `wrappingText` rows; rows coloured by rating (RICH foreground, others dim). Degrees: `round(toDegrees(lat))` with N/S, `round(toDegrees(lon))` with E/W.
- [ ] **Step 3:** Commit `feat(ui): body detail lists surveyed resources`.

### Task 8: Colony Ground line and explorer ship rows

**Files:** Modify `ui/DetailPanel.java`; tests as Task 7.

- [ ] **Step 1: Failing tests.** Earth Hub shows a line starting "Ground: ORE .19" (seed 42) with ORE, SIL, ICE, BIO and no FUEL; a Jovian site would show FUEL. An explorer shows "Tank: N / 100 FUEL" instead of the plain fuel line; an orbiting explorer has a *Dispatch...* button and no *Found colony…*; an orbiting colonizer is unchanged.
- [ ] **Step 2: Implement.** Commit `feat(ui): colonies show their ground; explorers show their tank`.

### Task 9: Dispatch and place-site dialogs

**Files:** Modify `ui/dialogs/DispatchShipDialog.java`, `ui/dialogs/PlaceSiteDialog.java`; tests `DispatchShipDialogTest`, new `PlaceSiteDialogTest` (static helpers only, no modal).

- [ ] **Step 1: Failing tests.** `destinations(w, explorer)` lists every site, then every body as "Mars (unsurveyed)" / "Earth (surveyed)", including bodies that have sites; for a colonizer, unsettled bodies read "Mars (unsettled, unsurveyed)"; for a hauler, sites only (unchanged). Extract `PlaceSiteDialog.yieldRows(World, Body, lat, lon)`: on a surveyed body it returns `ORE  Rich  .72` style rows for the surveyed resources with yield ≥ 0.02 at that spot; on an unsurveyed body one row "Yields here: unknown (unsurveyed)".
- [ ] **Step 2: Implement.** Explorer dispatch hides the manifest fields and shows "Explorers carry no cargo". The fuel popup path is unchanged (it already calls `fuelShortfall`). Commit `feat(ui): explorer dispatch targets and yields in the place-site dialog`.

### Task 10: YieldOverlay

**Files:** Create `render/YieldOverlay.java`, `test/.../render/YieldOverlayTest.java`.

- [ ] **Step 1: Failing tests.** For a flat map of width W and height H, pixel (x, y) corresponds to `lon = -π + (x + 0.5) · 2π / W`, `lat = π/2 − (y + 0.5) · π / H`; check this against `SphereRenderer`'s sampling by rendering a flat map with one marked pixel column and unprojecting the sphere pixel where it appears (or by reading the sampling code and asserting its formula; whichever pins the mapping). `tint(flat, sampler, ORE)` leaves a pixel whose yield is 0 unchanged and makes a pixel whose yield is 1 measurably closer to the ramp's top colour; output size equals input.
- [ ] **Step 2: Implement** `public static BufferedImage tint(BufferedImage flat, ResourceYieldSampler s, Resource r)` with a three-stop ramp (transparent → amber `#E0A030` → pale yellow `#FFF2A0`), alpha `0.6 × min(1, y / 0.8)`. Commit `feat(render): yield overlay tints a surface map by one resource`.

### Task 11: Body view overlay picker and readout

**Files:** Modify `ui/BodyViewPanel.java`; test in `PanelSmokeTest` or a new `BodyViewPanelTest`.

- [ ] **Step 1: Failing tests.** With Mars selected and unsurveyed the picker is disabled and reads "Unsurveyed"; after `w.survey("mars", …)` and a `WorldChanged`, it is enabled with Surface plus Mars's surveyed resources. Choosing ORE swaps the painted map for the tinted one (assert the cache key or a test hook). The readout for a mouse point on the globe reads "Here: ORE .xx · SIL .xx …" matching the sampler at the unprojected (lat, lon); off the globe it is blank.
- [ ] **Step 2: Implement.** Combo in the top bar after *Back*; remember the choice per body while the session lasts; tinted maps cached in a second map keyed `bodyId#resource`, cleared on `WorldReplaced` alongside `flatCache`. A `mouseMoved` listener on `SpherePanel` updates a `JLabel` in a bottom bar. Commit `feat(ui): body view yield overlay and readout`.

---

## Part C — Driver, docs, PR

### Task 12: Play-test driver, screenshots, docs

- [ ] **Step 1:** `PlayTestDriver` builds an explorer at Earth Hub, dispatches it to Mars, waits for the survey, then founds the Mars colony at `ResourceSurvey.of(mars)`'s best ORE spot. Capture screenshots: Earth's and Mars's body panels, the Mars body view with the ORE overlay, the place-site dialog.
- [ ] **Step 2:** Update the design doc's status line, the game-design spec §3.4 ship table.
- [ ] **Step 3:** Full `xvfb-run … ./gradlew test`; confirm `production-parity.txt` is unchanged (`git diff --exit-code src/test/resources/parity`). Open the implementation PR with the screenshots and drive CI green.
