# Plan 11 — Event Consequences

**Date:** 2026-11-01
**Status:** Design, awaiting Steve's approval.
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–9 merged to `main` (last: PR #40). Plan 10 (ship build times and fuels, PR #41) is in design; this plan is written to land after it (§9.2).
**Source spec:** `2026-05-03-space-colony-game-design.md` §3.10 (events), Plan 5 (event strip), Plan 8 (repair).

## 1. Vision

Steve's ask: *negative events need consequences, and the event needs to display what those are.*

### 1.1 What happens today

`EventPhase` rolls each of the 10 bodies every day at 0.0008 and picks one of four kinds. Measured over 20 seeds × 3000 days with an untouched Earth Hub, and by forcing events on 200 seeds:

| Event | Effect in code | What actually happens |
|---|---|---|
| Meteor strike | disables a random building at every site on the body | 92% land on a body with no colony and do nothing. On a colony it can pick an already damaged building (no change), and hitting a power plant only costs a day, since power plants switch back on by themselves. |
| Equipment failure | each enabled building at each site breaks with a 30% chance, stopping at the first | 92% land on an empty body. At Earth Hub it breaks nothing 16% of the time. |
| Disease outbreak | kills 10% of each site's people (at least 1), morale −0.2 | 92% land on an empty body. When it hits, the effect is real. |
| Solar flare | every power plant in the system off for one day | always hits, but one day of blackout in a year is invisible: nothing says it happened, and the next day is normal. |

So about nine events in ten are "Meteor strike on Belt-B" with no effect, and the event text never says what was damaged, killed or lost, even when something was. The player learns to ignore the strip.

### 1.2 What Plan 11 does

1. **Events strike colonies, not empty rocks.** Body events roll per colony; the solar flare rolls once for the system. An event that would do nothing doesn't fire.
2. **Each event has a concrete, stated cost.** A meteor strike comes in three sizes, from one damaged building and some lost stores up to several buildings, many stores, deaths and months of reduced farm output; an equipment failure always breaks one building; a solar flare blacks out solar power for several days, longest near the sun; a disease outbreak keeps its losses.
3. **The event says what happened.** "Major meteor strike at Mars Base: Farm L2 and Mine L1 damaged; 38 ICE and 12 METAL destroyed; 3 colonists died; impact dust: farms at 75% for 20 days". Clicking it selects the colony; the damaged building's row says what damaged it.

**Out of scope:** new event kinds (dust storms, ship accidents, good events); events that hit ships; insurance or prevention buildings; new techs (Medicine stays the only event tech); a pause-on-event option.

## 2. Architecture overview

### 2.1 New and changed files

```
spacecolony/
├── sim/
│   ├── Building.java              (CHANGED: + offlineDays, + damagedBy; isOperational() checks both)
│   ├── Site.java                  (CHANGED: + dustDays, dustFactor, for impact dust)
│   ├── Construction.java          (CHANGED: power plants can be repaired like any building)
│   ├── EventEffects.java          (NEW: the consequence list of one event, and its message text)
│   ├── economy/SiteEconomy.java   (CHANGED: a building with offlineDays > 0 produces and draws nothing; impact dust scales farm FOOD)
│   └── phases/
│       ├── EventPhase.java        (CHANGED: per-colony rolls, applicable kinds only, new effects, effect text)
│       ├── ProductionPhase.java   (CHANGED: step 8 counts offlineDays and dustDays down instead of re-enabling power plants)
│       └── CommandPhase.java      (CHANGED: repair clears damagedBy)
├── save/SaveFile.java             (CHANGED: building offlineDays and damagedBy; one schema bump)
├── debug/TriggerEventDialog.java  (CHANGED: target is a colony; flare needs none)
└── ui/
    ├── EventStripPanel.java       (CHANGED: tooltip lists each consequence; ERROR colour for deaths)
    ├── DetailPanel.java           (CHANGED: damaged row says by what and when; offline row says until when)
    └── BuildingMenu.java          (CHANGED: Repair offered for damaged power plants)
```

### 2.2 Layering

Unchanged. Effects mutate the world only in `EventPhase`. `EventEffects` is a plain value the phase builds while applying an event, then turns into the message.

## 3. Where and how often events strike

- **Body events** (meteor strike, equipment failure, disease outbreak) roll once a day **per colony** at `COLONY_EVENT_RATE = 0.004`, about one per colony every 250 days (12½ minutes at 1×). Today a lone Earth Hub sees a body event that touches it about once every 1250 days, so this is five times as often, and every one does something.
- **Kind.** Weighted among the kinds that would do something at that colony:
  - meteor strike: weight 1, or 2 on an airless body (ASTEROID, MOON, ICE_BODY), as the game-design spec §3.10 says;
  - equipment failure: weight 1, only if the colony has a working building;
  - disease outbreak: weight 1, only if the population is above 0.
- **Solar flare** rolls once a day for the system at `FLARE_RATE = 0.0015` (about one every 670 days) and fires only if at least one colony has a working power plant.
- `randomEventsEnabled`, the tutorial's switch, and the debug *Trigger event…* control work as before. Forced events on a colony with nothing to hit still fire, with the text "no damage" (§5), so the debug control never looks broken.

## 4. Consequences

### 4.1 Meteor strike (one colony), in three sizes

Steve: *meteor strikes should have multiple severity levels, which could include damaging multiple buildings and resources and killing some of the population. More severe ones may reduce farm output for some period of time.*

Each strike first rolls its size, then applies that row:

| Size | Chance | Buildings damaged | Stores destroyed | Colonists killed | Farm output |
|---|---|---|---|---|---|
| **Minor** | 60% | 1 | 10% of 1 resource | none | unchanged |
| **Major** | 30% | 2 | 20% of 2 resources | 2% of the population | 75% for 20 days |
| **Catastrophic** | 10% | 3 | 30% of 3 resources | 5% of the population | 50% for 60 days |

- **Buildings.** Distinct working buildings, chosen at random (level ≥ 1, not damaged). Each stays off until repaired with Plan 8's repair (25% of its build cost, instant). A building under construction can't be hit; its scaffolding is too small a target. If the colony has fewer working buildings than the row says, all of them are damaged.
- **Stores.** Distinct stockpiled resources the colony holds at least 5 units of, chosen at random; each loses its share, rounded down to whole units.
- **Colonists.** The share of the population, rounded down, at least 1 when the share applies and anyone lives there. Morale drops 0.1 (major) or 0.2 (catastrophic), like a small outbreak.
- **Farm output: impact dust.** Dust thrown up by a major or catastrophic strike dims the sky. For the given number of days every farm at that colony makes only 75% or 50% of its FOOD (it still plants its full BIOMASS and drinks its full WATER, so the loss is real). It applies only at the struck colony. A second strike during the dust keeps the lower factor and the longer remaining time. Stored as `Site.dustDays` and `Site.dustFactor`; `ProductionPhase` counts the days down; `SiteEconomy` multiplies farm FOOD by the factor and gives the farm the limit "Impact dust: 75%" so the ledger and the farm's row explain the shortfall.
- **Airless bodies** (ASTEROID, MOON, ICE_BODY) are hit twice as often (§3), but no dust hangs over them: there is no air to hold it. Strikes there skip the farm row. That makes a belt colony's risk buildings and stores, and a Mars or Earth colony's risk food.
- **Nothing to hit.** If the colony has no working building, nothing stored and nobody living there, the strike misses and nothing fires (a forced one says "no damage").

The size also names the event: "Meteor strike", "Major meteor strike", "Catastrophic meteor strike". A catastrophic one is ERROR (red) even if, rarely, it kills no one.

### 4.2 Equipment failure (one colony)

Exactly one working building breaks, chosen at random, and needs repair. Nothing else. Today's version can break nothing; this one never does.

### 4.3 Disease outbreak (one colony)

Unchanged arithmetic: `max(1, population / 10 × severity)` die and morale drops `0.2 × severity`, where Medicine halves severity. The difference is the text, and that it hits one colony instead of every site on the body.

### 4.4 Solar flare (system-wide)

- Every working **power plant** in the system goes offline for `ceil(3 / r²)` days, between 1 and 6, where `r` is the colony's distance from the sun in AU: Mercury 6, Venus 6, Earth 3, Mars 2, the belt and Jovian 1. Power plants are solar (output falls as 1/r²), so the flare hurts most where solar is best.
- Offline is not damage: the plant comes back by itself when `offlineDays` reaches 0 and needs no repair. A flare during an outage resets it to the longer of the two.
- While offline, the colony runs on whatever else makes power (nothing, today), so it browns out exactly as it does now for one day.

### 4.5 Power plants and repair

Today a damaged power plant switches itself back on the next day, because the flare's one-day outage reuses the `enabled` flag. With a separate `offlineDays` counter, a power plant hit by a meteor or an equipment failure is **damaged like any other building** and needs repair. `Construction.whyNotRepair`'s "Power plants come back on their own the next day" goes away, and the building menu offers *Repair* for power plants.

### 4.6 Balance

A minor meteor strike on Earth Hub's farm costs 25% of a farm (`BuildCost` × 0.25) plus a tenth of one stock. With the rate above a colony expects roughly 1.5 events a year, about a third of them meteors, so a catastrophic strike comes about once every 20 years per colony. At a starting Earth Hub (one L1 farm making 1.5 FOOD a day, 100 people eating 1.0) 60 days at 50% turns +0.5 FOOD a day into −0.25: 45 FOOD less than without dust, 15 of it drawn from the 200 in store. Survivable with stock, but a big colony running near break-even, or one whose farm was also damaged, will go hungry. That is the point of the tier, and the number to watch. Overall it is a real tax but not a collapse: the play-tester's neglectful style, which never repairs, will show how fast an ignored colony decays, and the constants are one line each.

## 5. Showing the consequences

### 5.1 Event text

`EventEffects` collects one line per consequence; the message is the kind and colony, a colon, and the lines joined with "; " (a repair cost already uses commas):

- "Meteor strike at Mars Base: Farm L2 damaged (repair 4 METAL, 1 COMPONENTS); 19 ICE destroyed"
- "Major meteor strike at Mars Base: Farm L2 and Mine L1 damaged (repair 4 METAL, 1 COMPONENTS and 5 METAL, 1 COMPONENTS); 38 ICE and 12 METAL destroyed; 3 colonists died; impact dust: farms at 75% for 20 days"
- "Equipment failure at Earth Hub: Mine L1 broke down (repair 3 METAL)"
- "Disease outbreak at Earth Hub: 12 colonists died; morale −20%"
- "Solar flare: power plants offline at Earth Hub for 3 days, Mars Base for 2 days"
- forced with nothing to hit: "Meteor strike at Ceres Base: no damage"

The repair cost in the text is the cost at the time; the row in the right column always shows the current one.

### 5.2 Event strip

- Body events now carry the colony's `siteId`, so clicking selects the colony, not just the body (`EventStripPanel.targetOf` already prefers the site).
- Tooltip: the kind and date on the first line, then each consequence on its own line (an HTML tooltip), then a hint: "Repair damaged buildings from their menu in the colony panel" for building damage.
- **Severity.** Events that kill colonists are ERROR (red); the rest stay WARNING. Today nothing uses ERROR, so its filter button finally does something.

### 5.3 Colony panel (right column)

- A damaged building's row: "Farm L2   damaged by meteor strike, Y1 D40: repair 4 METAL, 1 COMPONENTS". `Building.damagedBy` (an `EventKind`, null when fine) and the event tick are stored on the building; repair clears them.
- A colony under impact dust shows a line under its stockpile: "Impact dust: farms at 75% for 14 more days", and each farm's row reads "+1.1 FOOD (impact dust: 75%)".
- An offline power plant: "Power plant L1   offline after solar flare, back in 2 days" (replaces today's "offline today").
- Rows wrap within 330 px (PR #28).

## 6. Saving

Building gains `"offlineDays"` (int, default 0) and `"damagedBy"` / `"damagedTick"` (optional). Site gains `"dustDays"` and `"dustFactor"` (default 0 and 1). One schema bump (v5 → v6 if Plan 10's v5 has landed, else v4 → v5; §9.2). Older saves load with `offlineDays = 0` and no cause, except that a power plant saved disabled loads as offline for 1 day instead of damaged, since in old saves that was always the one-day flare (or a one-day hit), and no one should pay to repair it.

## 7. Debug and tutorial

- *Trigger event…* picks a colony (or nothing, for a solar flare) instead of any body, and shows the resulting message.
- The tutorial still turns random events off until it ends; nothing in its script changes.
- `ContinuousPlayTester`: its invariants already allow damage; it needs to accept power plants staying damaged and to repair them in the styles that repair.

## 8. Testing strategy

### 8.1 Sim (JUnit, headless)

- `EventRollTest`: over many days with one colony, body events fire only at colonies; empty bodies never get one; kinds that can't apply never fire (no disease at an empty outpost, no equipment failure with nothing working); a flare never fires with no power plants; the rate is within tolerance of 0.004 per colony-day.
- `EventEffectsTest`:
  - the size roll lands near 60/30/10 over many strikes;
  - each size damages its number of distinct working buildings (never one already damaged or under construction; all of them when there are fewer), takes its share of its number of distinct stocks, rounded down, and kills its share of the population (at least 1 for major and catastrophic when anyone lives there); with nothing to hit it misses;
  - impact dust: after a major strike a farm makes 75% of its FOOD for 20 production days, then 100%; a catastrophic strike during it sets 50% and the longer time; no dust on an airless body; the farm's outcome carries the dust limit;
  - equipment failure always breaks exactly one working building;
  - disease numbers unchanged from today (`EventTechEffectsTest` keeps passing);
  - flare sets `offlineDays = ceil(3/r²)` clamped to 1–6; a plant back online after that many production days; brownout on those days; a second flare keeps the longer count;
  - power plant damaged by a meteor stays off until repaired; repair cost charged; `damagedBy` cleared.
- `EventMessageTest`: each message format in §5.1; killing colonists is ERROR.
- Save round-trip of `offlineDays`, `damagedBy` and a site's dust; an old save with a disabled power plant loads as offline 1 day.
- `EventsTest` and `DeterminismTest` keep passing (seeds may need a colony for the first).
- `ProductionParityTest` is unchanged (it doesn't run events).

### 8.2 UI

- `EventStripPanel`: a meteor event row selects the colony on click; the tooltip lists each consequence; ERROR colour for a disease event.
- `DetailPanel.buildingText`: damaged-by row, offline row and dusty farm texts; the colony's impact dust line.
- `BuildingMenu`: *Repair* offered for a damaged power plant, not for an offline one.

### 8.3 Test count estimate

About 28 new tests.

### 8.4 Manual play-test checklist

1. New game, 16×. Within a few in-game years Earth Hub gets events; each says what it cost.
2. Debug → *Trigger event…* → meteor strike at Earth Hub, once per size (the dialog gets a size picker for meteors): the right number of building rows turn amber with "damaged by meteor strike", stocks drop, colonists die on major and catastrophic, and the event row says all of it.
2b. After a catastrophic strike, the farm row and the ledger show 50% FOOD from impact dust, counting down from 60 days.
3. Repair it from the building menu; the row clears.
4. Trigger a solar flare: Earth Hub's power plant reads "offline after solar flare, back in 3 days", the ledger shows the brownout, and it comes back by itself.
5. Trigger a disease outbreak: the row is red and gives the death toll.

## 9. Risks, decisions and open choices

### 9.1 Decisions made here (defaults)

- Body events roll **per colony**, 0.004 a day each; flares 0.0015 a day for the system.
- Meteor sizes 60/30/10%: 1/2/3 buildings, 10/20/30% of 1/2/3 stocks, 0/2/5% of colonists, farms 100%/75% for 20 days/50% for 60 days; no dust on airless bodies.
- Equipment failure: exactly one building, always.
- Solar flare: `ceil(3/r²)` days offline, 1 to 6, no repair needed.
- Power plants can be damaged and repaired like any building.
- Deaths make an event ERROR (red).

### 9.2 Risks

- **Plan 10 overlap.** Plan 10 touches `SaveFile` (schema v5), `ProductionPhase` step 9, `DetailPanel` building rows and `EventKind`. This plan's changes are in different methods (step 8, `buildingResult`, `EventPhase`), so the implementation branch starts from `main` after Plan 10's implementation merges and takes the next schema number. If Plan 10 is still in flight when Steve approves this one, Plan 11 goes first and Plan 10 rebases; the overlap is small either way.
- **Harder game.** Events now cost something about 1.5 times a year per colony. The neglectful play-tester style will show whether that kills colonies; if it does, lower `COLONY_EVENT_RATE`.
- **More colonies, more events.** Five colonies see five times the body events. That is intended (more to protect) but could feel noisy late; the rate could scale down with colony count later.

### 9.3 Open choices for Steve

1. Event rate per colony: 0.004 a day, about one per 250 days (default), or rarer/more frequent?
2. Meteor sizes as in §4.1 (default), or different chances, losses or dust lengths? In particular, is 60 days of half-rate farms after a catastrophic strike too harsh for a one-farm colony?
3. Solar flare length by distance from the sun, 1–6 days (default), or a flat 3 days everywhere?
4. Should power plants need repair after a meteor or failure (default: yes), or keep coming back on their own?
5. Red (ERROR) for events that kill colonists (default), or keep every event amber?

## 10. What's next

Ship events (a hauler holed by debris loses cargo), dust storms on Mars, good events (a rich vein, a supply windfall), a Shielding tech against meteors and flares, and an option to pause the clock on a serious event.
