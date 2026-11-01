# Space Colony — Plan 11: Event Consequences

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Random events hit colonies instead of empty bodies, each one has a concrete cost (damaged buildings, destroyed goods, deaths, days without solar power; meteor strikes in three sizes, the big ones dimming farms with impact dust), and the event text, tooltip and building rows say exactly what it cost.

**Architecture:** `EventPhase` rolls body events per colony and the solar flare once for the system, picks only kinds that apply, and records each consequence in an `EventEffects` value that becomes the message. `Building` gains `offlineDays` (flare outage, self-healing) and `damagedBy`/`damagedTick` (damage cause, cleared by repair). `Site` gains `dustDays`/`dustFactor` for impact dust. Power plants become repairable. One save schema bump.

**Tech Stack:** Java 25, Gradle 9 via wrapper, JUnit 5, Swing. No new dependencies.

**Spec reference:** `docs/superpowers/specs/2026-11-01-plan-11-event-consequences-design.md` (sections 1–10).

**Out of scope:** new event kinds, ship events, prevention techs or buildings, pause on event. See the design doc §1 and §10.

---

## Context

Plans 1–9 are on `main` (last: PR #40, 2026-11-01). Plan 10 (PR #41) is in design and touches `SaveFile`, `ProductionPhase`, `DetailPanel` and `EventKind`. Start the implementation branch from `main` after whichever of Plan 10's implementation or this plan's approval comes first, and take the next free schema version (§6 of the design). GUI tests that build a frame need `xvfb-run -a -s "-screen 0 1400x900x24" ./gradlew test` and skip themselves when headless. CI runs the full suite under xvfb on JDK 25.

Key facts about the existing code:

- `Simulator.advance` runs Command, Transit, Production, Event, Research, Goal. Events therefore act after the day's production and show in the next day's ledger.
- `EventPhase.run` uses `DeterministicRng.forStep(seed, tick, 6L)`; `applyForced(w, body, kind, rng)` is used by the debug *Trigger event…* dialog and tests (`EventTechEffectsTest`, `DebugDialogsTest`, `DebugModeDriver`).
- `Building.enabled = false` means damaged; `CommandPhase.applyRepair` sets it back and charges `Construction.repairCost` (25% of build cost). `Construction.whyNotRepair` refuses power plants, and `ProductionPhase` step 8 re-enables every power plant each day (the flare's one-day outage).
- `SiteEconomy.run` skips `!bd.enabled` buildings (`Limit.DISABLED`) and computes solar output from `OrbitalGeometry.sunDistance(w, b)`.
- `DetailPanel.buildingResult` prints "offline today" for a disabled power plant and "damaged: repair …" for others; `BuildingMenu` hides *Repair* for power plants.
- `EventStripPanel.targetOf` prefers ship, then site, then body; today's random events set only `bodyId` (the flare sets nothing).
- `ProductionParityTest` doesn't run `EventPhase`, so the fixture shouldn't change.

---

## File Structure

### Create

```
src/main/java/spacecolony/sim/EventEffects.java
src/test/java/spacecolony/sim/EventRollTest.java
src/test/java/spacecolony/sim/EventEffectsTest.java
src/test/java/spacecolony/sim/EventMessageTest.java
```

### Modify

- `sim/Building.java`, `sim/Site.java`, `sim/economy/Limit.java` (+ DUST), `sim/Construction.java`, `sim/economy/SiteEconomy.java`, `sim/economy/BuildForecast.java` (copy the new fields), `sim/phases/EventPhase.java`, `ProductionPhase.java`, `CommandPhase.java`
- `save/SaveFile.java`
- `debug/TriggerEventDialog.java`, `debug/DebugActions.java` if it forwards the target
- `ui/EventStripPanel.java`, `ui/DetailPanel.java`, `ui/BuildingMenu.java`
- Tests: `EventsTest`, `EventTechEffectsTest`, `DebugDialogsTest`, `RepairCommandTest` or wherever power-plant repair refusal is asserted; `ContinuousPlayTester` (repair power plants).
- Docs: design doc status line; game-design spec §3.10.

---

## Tasks

Part A (1–5) is the sim, headless and test-first. Part B (6–8) is the UI. Part C (9) is drivers, docs and the PR. Every task ends with `./gradlew test` green and a commit in the `feat(<pkg>): …` style.

### Task 0: Branch

- [ ] Work on the thread branch restarted from the latest `main` after the design PR merges.

## Part A — The sim

### Task 1: Offline is not damage

**Files:** `Building`, `SiteEconomy`, `ProductionPhase`, `Construction`, `BuildingMenu` (repair item only), `BuildForecast`.

- [ ] **Step 1: Failing tests.** A power plant with `offlineDays = 2` produces nothing for two production days and works on the third without a command; a power plant with `enabled = false` stays off until repaired; `whyNotRepair` allows a damaged power plant and refuses an offline one ("Offline after a solar flare: back in 2 days").
- [ ] **Step 2: Implement.** `public int offlineDays;` and `isOperational()` → `enabled && offlineDays == 0 && level > 0`. `SiteEconomy` treats `offlineDays > 0` like disabled (same `Limit.DISABLED` outcome; the UI tells them apart from the building). ProductionPhase step 8 becomes `if (bd.offlineDays > 0) bd.offlineDays--;` for every building. Remove the power-plant clause in `whyNotRepair` and the `BuildingMenu` check. Construction still pauses only on damage.
- [ ] **Step 3:** Commit `feat(sim): solar outages count down; power plants can be damaged and repaired`.

### Task 2: EventEffects and the message

**Files:** create `EventEffects`, `EventMessageTest`.

```java
/** What one event did, in order, and the event text built from it (design §5.1). */
public final class EventEffects {
    public void damaged(Building b, String verb);          // "Farm L2 damaged (repair 4 METAL, 1 COMPONENTS)"
    public void destroyed(Resource r, double amount);      // "38 ICE destroyed"
    public void died(int colonists);                       // "12 colonists died"
    public void dust(double factor, int days);             // "impact dust: farms at 75% for 20 days"
    public void size(MeteorSize size);                     // prefixes "Major " / "Catastrophic "
    public void morale(double drop);                       // "morale −20%"
    public void offline(Site s, int days);                  // collected into "power plants offline at A for 3 days, B for 2 days"
    public boolean isEmpty();
    public boolean anyDeaths();
    /** "Meteor strike at Mars Base: …", or "…: no damage" when empty. */
    public String message(EventKind kind, Site siteOrNull);
}
```

- [ ] **Step 1: Failing tests** for each format in design §5.1, including singulars ("1 colonist died", "1 day").
- [ ] **Step 2: Implement.** Commit `feat(sim): event effects and their text`.

### Task 3: The rolls

**Files:** `EventPhase`, `EventRollTest`.

- [ ] **Step 1: Failing tests** (design §8.1 `EventRollTest`). Use a world with Earth Hub plus a test colony on Mars, and one with only an empty Belt-B outpost (no buildings, no people) for the "nothing applies" cases.
- [ ] **Step 2: Implement.**

```java
static final double COLONY_EVENT_RATE = 0.004; // per colony per day
static final double FLARE_RATE = 0.0015;       // per day, system-wide

public static void run(World w) {
    Random rng = DeterministicRng.forStep(w.seed, w.tick, 6L);
    if (!w.randomEventsEnabled) return;
    if (rng.nextDouble() < FLARE_RATE && anyWorkingPowerPlant(w)) applyEvent(w, null, null, SOLAR_FLARE, rng);
    for (Body b : w.bodies) for (Site s : List.copyOf(b.sites)) {
        if (rng.nextDouble() >= COLONY_EVENT_RATE) continue;
        EventKind k = pickApplicable(b, s, rng);   // weights per design §3; null if none apply
        if (k != null) applyEvent(w, b, s, k, rng);
    }
}
```

`randomKinds()` stays the four kinds. `applyForced(w, body, kind, rng)` keeps its signature for tests and picks the body's first site (or none); add `applyForced(w, site, kind, rng)` for the debug dialog.
- [ ] **Step 3:** `EventsTest.givenEnoughTicks_someRandomEventFires` and `sameSeed_sameEventTimeline` still pass (Earth Hub is a colony; raise ticks if the seed is unlucky, never the rate). Commit `feat(sim): events strike colonies, only when they can do something`.

### Task 4: The consequences

**Files:** `EventPhase`, `Building` (+ `damagedBy`, `damagedTick`), `CommandPhase.applyRepair`, `EventEffectsTest`.

- [ ] **Step 1: Failing tests** (design §8.1 `EventEffectsTest`).
- [ ] **Step 2: Implement** per design §4. Meteor: `enum MeteorSize { MINOR(0.6, 1, 0.10, 1, 0.0, 1.0, 0), MAJOR(0.3, 2, 0.20, 2, 0.02, 0.75, 20), CATASTROPHIC(0.1, 3, 0.30, 3, 0.05, 0.5, 60) }` (chance, buildings, store share, stores, death share, farm factor, dust days) in `EventPhase`; roll size, then apply its row; dust skipped on airless bodies; `dustFactor = min`, `dustDays = max` against current dust. ProductionPhase step 8 counts `dustDays` down and resets `dustFactor` to 1 at 0. `SiteEconomy` multiplies farm FOOD by `dustFactor` and sets `Limit.DUST` when < 1 (only if no stronger limit applies). Then: `workingBuildings(s)` = level ≥ 1, enabled, not under construction (an upgrading building is level ≥ 1 and can be hit; damage pauses the upgrade as today). Distinct buildings and distinct stocks (≥ 5 units, candidates in `Resource.values()` order) are picked by `rng`. Equipment failure: damage one. Disease: today's arithmetic, one site. Flare: for each site with a working power plant, `days = clamp(ceil(3 / r²), 1, 6)`, `offlineDays = max(offlineDays, days)` on each. Damage sets `enabled = false`, `damagedBy = kind`, `damagedTick = w.tick`; repair clears both. Emit `new Event(tick, anyDeaths || catastrophic ? ERROR : WARNING, kind, effects.message(kind, s), b.id, s.id, null)`; the flare emits with no ids.
- [ ] **Step 3:** `EventTechEffectsTest` passes (adjust only how it finds the site's loss, not the numbers). Commit `feat(sim): meteors, failures and flares cost something`.

### Task 5: Save

- [ ] **Step 1: Failing tests.** Round-trip `offlineDays`, `damagedBy`, `damagedTick`, `dustDays`, `dustFactor`; an older-schema save with a disabled power plant loads with `enabled = true, offlineDays = 1`; the v1 fixture still loads.
- [ ] **Step 2: Implement** the schema bump with optional fields (`optInt`, optional enum by name). Commit `feat(save): save event damage causes and solar outages`.

## Part B — The UI

### Task 6: Event strip

- [ ] **Step 1: Failing tests.** A meteor event row's target is the colony; the tooltip is HTML with the kind and date, then one line per consequence (split the message after the first ": " on "; "; an `Event` field isn't needed), then the repair hint when a building was damaged; a disease row uses the ERROR colour.
- [ ] **Step 2: Implement** `EventStripPanel.tooltip(Event)` as a static helper. Commit `feat(ui): event strip lists what each event cost`.

### Task 7: Building rows and menu

- [ ] **Step 1: Failing tests** for `DetailPanel.buildingText`: "Farm L2   damaged by meteor strike, Y1 D40: repair 4 METAL, 1 COMPONENTS"; damaged with no cause (old save) keeps today's "damaged: repair …"; "Power plant L1   offline after solar flare, back in 2 days"; a dusty farm "+1.1 FOOD (impact dust: 75%)" and the colony line "Impact dust: farms at 75% for 14 more days"; `ResourceLedgerPanel.limitText` for DUST. `BuildingMenu` offers *Repair* for a damaged power plant and not for an offline one.
- [ ] **Step 2: Implement.** The date uses the event strip's `Y%d D%d` format (share a helper). Rows wrap within 330 px. Commit `feat(ui): damaged buildings say what damaged them`.

### Task 8: Debug trigger

- [ ] `TriggerEventDialog` lists colonies ("Earth Hub (Earth)") and greys the target out for a solar flare; a size picker (Random, Minor, Major, Catastrophic) appears for meteor strikes; it calls the new `applyForced(w, site, …)`. `DebugDialogsTest` and `DebugModeDriver` updated. Commit `feat(debug): trigger events at a colony`.

## Part C — Drivers, docs, PR

### Task 9

- [ ] `ContinuousPlayTester`: repair styles also repair power plants; neglectful leaves them. Run one neglectful and one balanced seed for 720 days locally and note in the PR how many events fired and whether either colony collapsed.
- [ ] Screenshots under `/mnt/project-files/plan-11-events/`: event strip with minor, major and catastrophic meteor rows and a disease row, a dusty farm row and ledger, the tooltip, a damaged farm row, an offline power plant row, the debug trigger dialog.
- [ ] Update the design doc status line and game-design spec §3.10.
- [ ] Full `xvfb-run … ./gradlew test`, open the implementation PR, drive CI green.
