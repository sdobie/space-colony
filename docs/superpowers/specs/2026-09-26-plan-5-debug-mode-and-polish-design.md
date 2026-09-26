# Plan 5 — Debug Mode, Save Slots + Autosave, Panel Polish

**Date:** 2026-09-26
**Status:** Draft (pre-implementation-plan)
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–4 merged to `main` (sim core, render adaptation, engine + Swing UI shell, save/load + tech wiring + HABITAT cap).
**Source spec:** `2026-05-03-space-colony-game-design.md` §7.3–§7.4, §8, §9, §10. Deferred list from Plan 4 (`2026-05-31-plan-4-save-and-sim-wiring.md`, "Out of scope (Plan 5)").

## 1. Vision

After Plan 4 the game is playable session to session, but three parts of the v1 scope in the game-design spec (§14) are still missing:

1. **Debug mode (spec §8).** Nothing in the game lets a developer step the sim, see where tick time goes, inspect an object, force an event, or read a log. There is no logging at all, and an uncaught exception on the EDT just prints to stderr.
2. **Save slots and autosave (spec §9).** Save and Load go through a raw `JFileChooser`. There is no slot list, no delete, and quitting without saving loses the session.
3. **Panel polish (spec §7.3–§7.4).** Plan 4 made tech and habitats matter in the sim, but the UI hides it. Morale can now exceed 1.0 (life-support techs) and the dock still prints `0.xx`. The pop cap has no breakdown. The tech list shows no prerequisites, and clicking a locked tech queues it anyway, because `CommandPhase.applyQueueResearch` never checks prerequisites. Goals show no progress. The event strip can't be filtered and doesn't link to what it's about.

Plan 5 closes all three. When it lands, every "In v1" bullet of spec §14 is implemented.

**Out of scope (post-v1 or later plan):** save thumbnails (Plan 4 listed them as optional; spec §9 doesn't require them); periodic (timed) autosave (spec asks only for autosave on quit); a graph-drawn tech tree (Plan 5 uses a tiered list, see §6.2); `distZip` packaging; the Java toolchain pin (`build.gradle.kts` asks for Java 25).

## 2. Architecture overview

### 2.1 New and changed packages

```
spacecolony/
├── sim/
│   ├── PhaseObserver.java        (NEW: optional per-phase timing hook, no Swing/IO)
│   ├── SimPhase.java             (NEW: enum of the 8 phases)
│   ├── PopCapBreakdown.java      (NEW: record + compute(), single source for the cap formula)
│   └── TechAvailability.java     (NEW: prereq checks shared by CommandPhase and TechModal)
├── engine/
│   └── (Engine gains debug + multi-tick API; EngineEvent gains DebugModeChanged)
├── save/
│   ├── SaveSlots.java            (NEW: slot directory, listing, naming, delete, autosave paths)
│   └── SlotInfo.java             (NEW: record for one listed save)
├── debug/                        (NEW PACKAGE, spec §4.2)
│   ├── DebugLogging.java         (JUL install: ring + rotating file handler, level parsing)
│   ├── RingBufferHandler.java
│   ├── CrashHandler.java         (uncaught-exception handler, spec §8.2)
│   ├── ExceptionLog.java         (bounded list of recent exceptions)
│   ├── PhaseTimings.java         (PhaseObserver impl: last 50 ticks)
│   ├── DeterminismCheck.java     (pure: snapshot, advance, restore, compare)
│   ├── ObjectTreeModel.java      (reflection walker for the inspector)
│   ├── YieldSummary.java         (per-body resource yield means for map labels)
│   ├── DebugController.java      (owns debug state, Ctrl+D, mounts/unmounts debug UI)
│   ├── DebugOverlayPanel.java
│   ├── DebugMenu.java
│   ├── ObjectInspectorDialog.java
│   ├── LogViewerDialog.java
│   └── TriggerEventDialog.java
└── ui/
    ├── GameSession.java          (NEW: current slot name, quit/autosave path)
    ├── SaveSlotDialog.java       (NEW: slot picker for Save As / Load)
    └── (FileMenu, TopBar, DetailPanel, TechModal, GoalsModal, EventStripPanel,
         SystemMapPanel, ColonyListPanel, BodyViewPanel, SpaceColonyFrame changed)
```

### 2.2 Layering (extends Plan 4)

- `world → sim`, `engine → sim`, `save → sim/world`, `ui → engine/save` (unchanged)
- `debug → engine, sim, save, world` (new)
- `ui → debug` (new): only `SpaceColonyFrame`, `SystemMapPanel`, `ColonyListPanel` and `BodyViewPanel` import from `debug`, and only `DebugController`, `YieldSummary` and `ObjectInspectorDialog`.
- **`debug` never imports `ui`.** Debug dialogs use stock Swing look and a monospaced font; they are a developer tool and don't need the game palette. This keeps the package graph acyclic.
- `sim` stays Swing-free and IO-free. `PhaseObserver` is a plain interface; timing uses `System.nanoTime()` only when an observer is set.

### 2.3 Threading (unchanged model)

Everything still runs on the EDT. New work that is not trivially fast runs in a `SwingWorker` against a **private copy** of the world, never the live one:

- Determinism check: copies via `SaveFile.toJson` / `fromJson`, advances copies off-EDT.
- Slot listing: parses save headers off-EDT.
- Save / Load / Dump: IO off-EDT, same pattern as Plan 4.

"Run N ticks" runs on the EDT (it mutates the live world) but fires one `WorldChanged` at the end instead of N. Plan 4 measured 1000 ticks at well under a second, so N is capped at 10,000 and the frame shows a wait cursor while it runs.

Autosave on quit runs synchronously on the EDT: the JVM is about to exit, and a worker would race `System.exit`.

## 3. Sim and engine groundwork

These are small, test-first changes that the UI work in §4–§6 depends on.

### 3.1 Research prerequisites (bug fix)

`CommandPhase.applyQueueResearch` accepts any known, unresearched tech, even when its prerequisites are missing, and `TechModal` lets the player click a locked row. Fix both through one helper:

```java
package spacecolony.sim;

public final class TechAvailability {
    private TechAvailability() {}
    /** True when every prereq of {@code t} is researched. */
    public static boolean prereqsMet(TechState s, Tech t);
    /** Prereq ids of {@code t} that are not yet researched, in catalog order. */
    public static List<String> missingPrereqs(TechState s, Tech t);
    /** Longest prereq chain length: 0 for roots. Used to group the tech list into tiers. */
    public static int tier(Tech t);
}
```

`applyQueueResearch` throws `CommandRejectedException("Missing prerequisites for <name>: <names>")` when `!prereqsMet`. The existing reject paths (unknown, already researched) are unchanged.

### 3.2 Goal categories and progress

`Goal` gains two components: `GoalCategory category` (`EXPANSION`, `POPULATION`, `FLEET`) and `ToDoubleFunction<World> progress` returning a value in `[0, 1]`. Predicates stay the source of truth for achievement; progress is display-only.

| Goal id | Category | Progress |
|---|---|---|
| `first-mars-colony` | EXPANSION | 0 or 1 |
| `self-sufficient-mars` | EXPANSION | 0 or 1 |
| `belt-presence` | EXPANSION | 0 or 1 |
| `jovian-presence` | EXPANSION | 0 or 1 |
| `five-bodies` | EXPANSION | settled bodies / 5 |
| `pop-1000` | POPULATION | total pop / 1,000 |
| `pop-10000` | POPULATION | total pop / 10,000 |
| `fleet-10` | FLEET | ships / 10 |

Progress is clamped to `[0, 1]`. An achieved goal always displays 1.0, even if its live measure has dropped since (population can fall after an outbreak).

### 3.3 Pop-cap breakdown

`ProductionPhase.recomputeCap` owns the HABITAT cap formula today. The dock needs the same numbers, so the formula moves into a sim record and the phase calls it:

```java
public record PopCapBreakdown(int siteBase, int habitatBoost, double techMultiplier, int cap) {
    public static PopCapBreakdown of(Site s, TechState t);  // same formula as Plan 4 §5.1
}
```

`recomputeCap` becomes `s.populationCap = PopCapBreakdown.of(s, tech).cap();`. Behaviour is identical and the existing `HabitatCapTest` must stay green.

### 3.4 Fuel-cost estimate for transits

The map overlay (§4.6) labels in-transit ships with their fuel cost, which is computed at departure and not stored. Extract the departure formula into a public pure function and have `loadingAndUnloading` call it:

```java
/** Fuel for a ship of this class carrying {@code cargoMass}, origin@t0 → dest@t1, under current tech. */
public static double fuelCost(World w, ShipClass c, double cargoMass,
                              String originSiteId, String destSiteId, long t0, long t1);
```

The overlay calls it with the transit's `cargoSnapshot` mass and its departure/arrival ticks. Because tech may have advanced since departure, the label reads "≈ fuel" and is documented as an estimate.

### 3.5 Phase observer

```java
package spacecolony.sim;

public enum SimPhase { COMMANDS, TICK, ARRIVALS, PRODUCTION, DEPARTURES, EVENTS, RESEARCH, GOALS }

@FunctionalInterface
public interface PhaseObserver { void phaseDone(long tick, SimPhase phase, long nanos); }
```

`Simulator.setPhaseObserver(PhaseObserver)` (nullable). `advance` wraps each phase call; when the observer is null it takes no timestamps, so production behaviour and determinism are untouched. `Simulator.queueDepth()` exposes the pending command count for the overlay.

### 3.6 Forced events

`EventPhase.applyForTest` is renamed `applyForced` and documented as the seam used by both tests and the debug "Trigger event…" control. Callers pass an RNG from `DeterministicRng.forStep(seed, tick, 99L)` (step id 99 is reserved for debug) so a forced event is reproducible from the log line that records it.

### 3.7 World snapshots

`SaveFile` gains string-level entry points, and the file methods delegate to them:

```java
public static String toJson(World w);
public static World  fromJson(String json) throws IncompatibleSaveException;  // JsonParseException on bad text
```

The determinism check and "Dump world" use these. `save`/`load` keep their Plan 4 behaviour, including atomic write.

### 3.8 Engine additions

```java
// Debug mode flag (UI reads it; sim never does).
public boolean debugEnabled();
public void setDebugEnabled(boolean on);          // fires DebugModeChanged

// Stepping. Both assert EDT.
public void step();                               // only while PAUSED: one tick + WorldChanged
public void advanceSilently(int n);               // n ticks, one WorldChanged at the end; 1 ≤ n ≤ 10_000

// Debug edits. Asserts EDT and PAUSED; applies fn to the live world, then fires WorldChanged.
public void applyDebugEdit(String description, Consumer<World> fn);

// Overlay data.
public int  commandQueueDepth();
public double ticksPerSecond();                   // over the last 2 s of real time
public void setPhaseObserver(PhaseObserver o);    // passthrough to Simulator
```

`EngineEvent` gains `record DebugModeChanged(boolean enabled)`. All current listeners use `instanceof`, so nothing else has to change for the new variant.

`applyDebugEdit` is the **only** path by which anything outside the simulator mutates `World`. It exists only for debug mode, and it logs `description` at `INFO` to `spacecolony.debug` so a changed world can always be traced to the edit that changed it. Refusing edits while running keeps edits from interleaving with ticks.

## 4. Debug mode (spec §8)

### 4.1 Turning it on

- `--debug` launch flag, parsed in `SpaceColonyApp` (`./gradlew play --args="--seed 1 --debug"`).
- **Ctrl+D** toggles at runtime. Bound through the frame's root pane `InputMap` (`WHEN_IN_FOCUSED_WINDOW`), so it works regardless of focus. The game has no other keyboard shortcuts yet, so there is no conflict (spec §15 open question).
- `--log-level=FINE|INFO|WARNING` (spec's `DEBUG|WARN` names are also accepted and mapped to `FINE|WARNING`). Default `INFO`.

`DebugController` listens for `DebugModeChanged`. When debug turns on it mounts the overlay and the Debug menu, installs a `PhaseTimings` observer, and enables the map overlays. When debug turns off it removes all of them and clears the observer. Logging and the crash handler are installed at startup whether or not debug is on, because a crash before Ctrl+D should still be captured.

### 4.2 Debug overlay

A panel stacked above the event strip in the frame's SOUTH region, visible only in debug mode. It refreshes on `WorldChanged`, and on a 500 ms Swing timer while paused.

- **Status line:** tick, ticks/sec (measured), speed, command queue depth, RNG key `(seed, tick)`. `DeterministicRng` is stateless and keyed off `(seed, tick, stepId)`, so the key *is* the RNG state.
- **Phase timings:** one row per `SimPhase`, with mean and max microseconds over the last 50 ticks (spec: "last 50 simulator phase timings").
- **Exceptions banner:** hidden when `ExceptionLog` is empty. Otherwise it shows the latest exception's class and message in the error colour, a count, and a "Details…" button that opens the log viewer filtered to `SEVERE`.
- **Sim controls** (spec §8 "Sim controls"):
  - **Step** (enabled only while paused) → `engine.step()`.
  - **Run N…** → input dialog (default 1000, max 10,000) → `engine.advanceSilently(n)`.
  - **Trigger event…** → `TriggerEventDialog`.
  - **Dump world** → `SaveFile.save(world, ~/.space-colony/debug/world-<yyyyMMdd-HHmmss>-t<tick>.json)` in a `SwingWorker`. The path is logged and shown in the overlay status line.
  - **Determinism check** → §4.5.
  - **Inspector**, **Logs** → open the dialogs for the current selection and the log.

### 4.3 Trigger event

`TriggerEventDialog`: a body combo (all bodies, sites first) and an `EventKind` combo limited to the four random kinds (`METEOR_STRIKE`, `SOLAR_FLARE`, `EQUIPMENT_FAILURE`, `DISEASE_OUTBREAK`). OK calls:

```java
engine.applyDebugEdit("trigger " + kind + " on " + body.id,
    w -> EventPhase.applyForced(w, w.findBody(bodyId), kind,
                                DeterministicRng.forStep(w.seed, w.tick, 99L)));
```

The event's normal `Event` is emitted, so it appears in the strip like a natural one. The action is available only while paused (via `applyDebugEdit`). The dialog's OK button is disabled with a tooltip while running.

### 4.4 Object inspector

- **Shift+click** on a body in `SystemMapPanel`, on the sphere in `BodyViewPanel` (inspects that body; a plain click still opens `PlaceSiteDialog`), or on a site/ship row in `ColonyListPanel` opens `ObjectInspectorDialog` for that object, only in debug mode. With debug off, Shift+click behaves like a plain click.
- The overlay's **Inspector** button opens the current selection, or the `World` itself when nothing is selected.
- `ObjectTreeModel` is a `TreeModel` built by reflection (`getDeclaredFields`, `setAccessible(true)`, walking superclasses). It covers private and final fields. Rules:
  - Leaves: primitives, boxed primitives, `String`, enums, `null`.
  - Branches: objects, arrays, `Collection` (children indexed `[i]`), `Map` (children keyed `[key]`), records (components).
  - Static fields and synthetic fields are skipped.
  - Lazy: children are computed when a node expands.
  - Cycle guard: an identity already on the path renders as `↺ <Type>@<id>` with no children.
  - Depth cap 12, and at most 500 children per node (a "… N more" leaf covers the rest).
  - `ResourceYieldSampler` instances render as a leaf `<sampler>`. They hold large grids and aren't useful as a tree.
- **Edit toggle** (off by default). When on, while paused, leaves for **non-final** fields of type primitive, boxed primitive, `String` or enum are editable. Enums get a combo box; the other types get a text field that must parse. Commit goes through `engine.applyDebugEdit("inspector: <path> = <value>", w -> field.set(owner, parsed))`. Collection elements and final fields are read-only. Plan 5 deliberately doesn't do structural edits (adding or removing list items).
- The tree refreshes on `WorldChanged` and keeps its expansion state by path. On `WorldReplaced` the dialog closes, because its root object is gone.

### 4.5 Determinism check

`DeterminismCheck.run(World live, int ticks)` returns `Result(boolean match, int ticks, String firstDiff)`:

1. `s0 = SaveFile.toJson(live)`.
2. `a = fromJson(s0)`; advance `a` by `ticks` with a fresh `Simulator`; `sa = toJson(a)`.
3. `b = fromJson(s0)`; advance `b` by `ticks`; `sb = toJson(b)`.
4. `match = sa.equals(sb)`. On mismatch, `firstDiff` is the first differing line number and both lines.

This is the spec's "snapshot, advance, restore, advance again, compare", done on two restored copies so the live world is never touched and the check can run in a `SwingWorker`. Default is 1000 ticks. The overlay shows "Determinism OK (1000 ticks, 312 ms)" or the diff, and logs the result.

The check compares the save envelope, so state that the envelope doesn't persist (for example `productionRateCache`) isn't compared directly. Any divergence there shows up in persisted state within a tick.

### 4.6 Map render overlays

When debug is on, `SystemMapPanel.paintComponent` draws three more layers:

- **Orbit labels:** each top-level orbit is labelled at its 45° point with its period in days (`b.orbit.period()`), and moons are labelled next to their parent.
- **Transit prediction:** for each `IN_TRANSIT` ship, a dashed line from the ship's current point to the destination body's position at `arrivalTick`, a hollow circle at that predicted point, and a label `t=<arrival>  ≈<fuel> fuel` computed with `TransitPhase.fuelCost` (§3.4).
- **Yield summary:** under each body's name, the top three resources by mean yield (`ORE .62 · ICE .41 · SIL .30`). `YieldSummary.of(Body)` samples an 8×16 lat/lon grid through `Body.resourceYields` and caches the result per body. The cache is cleared on `WorldReplaced`, because yields depend on the seed.

### 4.7 Logging (spec §8.1)

- `DebugLogging.install(Level level, Path logDir)` runs once at startup:
  - Root logger `spacecolony` at `level`, with parent handlers off so there's no duplicate console output.
  - `RingBufferHandler(2000)` keeps the last 2,000 `LogRecord`s for the viewer. It is thread-safe, because worker threads log too.
  - `FileHandler` writes to `~/.space-colony/logs/space-colony-<yyyy-MM-dd>.%g.log`, 1 MB × 5 rotating, with `SimpleFormatter` and a one-line format.
  - If the log directory can't be created, it logs a warning to the ring and continues without a file handler. Logging must never stop the game from starting.
- Runtime level change comes from the Debug menu ("Log level ▸ FINE / INFO / WARNING").
- **What logs:** `sim` stays logging-free (pure, and it runs in tests). The engine is the bridge:
  - After each tick, every `Event` with `tick == world.tick` is logged to `spacecolony.sim.events` (INFO for INFO events, WARNING for WARNING/ERROR).
  - `reset` logs INFO with the new seed and tick. Speed changes log FINE. `enqueue` logs FINE with the command record.
  - `applyDebugEdit` logs INFO with its description.
  - `FileMenu`/`GameSession` log saves, loads, autosaves and failures.

### 4.8 Uncaught exceptions (spec §8.2, §10)

`CrashHandler` is installed as `Thread.setDefaultUncaughtExceptionHandler`, which receives uncaught exceptions on the EDT and on plain threads. `SwingWorker.doInBackground` exceptions never reach it, because the worker's `Future` captures them, so every new worker's `done()` routes an unexpected `ExecutionException` to `CrashHandler.report(Throwable)`. Expected failures (IO, bad JSON, schema mismatch) keep their existing dialogs.

On an exception it:

1. Logs SEVERE to `spacecolony.crash` with the full stack.
2. Appends to `ExceptionLog` (last 20).
3. **Debug on:** does nothing else, because the overlay banner shows it.
4. **Debug off:** shows one modal dialog, "Something went wrong: <class>: <message>. Details were written to the log.", with **Continue** and **Quit**. Quit goes through the normal quit path, so autosave runs. While a crash dialog is open, further exceptions are only logged. This stops a failing `paintComponent` from stacking dialogs.

`CrashHandler` takes a `Reporter` interface for step 4 so tests can install a recording reporter instead of a dialog.

### 4.9 Debug menu

A "Debug" `JMenu` is added to the menu bar only while debug is on. It holds Step, Run N…, Trigger event…, Dump world, Determinism check, Inspector, Log viewer, Log level ▸, and Toggle map overlays. The menu and the overlay buttons share the same `Action` instances.

### 4.10 Log viewer

`LogViewerDialog` (non-modal) has two tabs:

- **Log:** a `JTable` over a snapshot of `RingBufferHandler`, with columns time, level, logger (shortened), and message. Filters are a minimum-level combo and a logger-prefix text field (e.g. `spacecolony.engine`). A "Follow" checkbox tails new records, refreshed on a 500 ms timer. Selecting a row shows the stack trace, if any, below the table.
- **Events:** the world's `recentEvents` (all 200, not just the 20 in the strip) with severity and kind filters.
- **Copy** copies the visible rows as text. **Save…** writes them to a file chosen with `JFileChooser`.

## 5. Save slots and autosave (spec §9)

### 5.1 Slots

A slot is `~/.space-colony/saves/<name>.json`. Its autosave sibling is `<name>.autosave.json`. An unnamed game (new, never saved) autosaves to `_autosave.json`.

```java
package spacecolony.save;

public record SlotInfo(String name, Path path, boolean autosave,
                       Instant modified, long tick, long seed, long credits) {}

public final class SaveSlots {
    public SaveSlots(Path dir);                         // default: ~/.space-colony/saves
    public static SaveSlots defaultDir();
    public List<SlotInfo> list() throws IOException;    // newest first; unreadable files skipped + logged
    public Path slotPath(String name);                  // validates name
    public Path autosavePath(String nameOrNull);        // null → _autosave.json
    public void delete(String name, boolean autosave) throws IOException;
    public static String validateName(String raw);      // trims; returns error message or null
}
```

- Names are 1–40 characters from `[A-Za-z0-9 _-]` and must not start with `_` (reserved). The `.autosave` suffix is added by `autosavePath` and can't be typed.
- `list()` reads `tick`, `seed` and `credits` with a full `JsonReader.parse` of each file. Saves are tens of KB, so a header-only parser isn't worth writing. Files that fail to parse or have another schema version still list, with `tick = -1` and "(unreadable)" or "(schema vN)" shown in the dialog, so the player can delete them.

### 5.2 GameSession

`GameSession` (in `ui`) holds the **current slot name** (null for an unnamed game) and implements quit:

- Save As… or Load of a slot sets the current slot to that slot's name. Loading an autosave sets it to the base name (`colony.autosave.json` → `colony`), and loading `_autosave.json` sets it to null.
- New Game sets it to null.
- Loading from an arbitrary file sets it to null.
- `quit()`: pause, confirm, write the autosave synchronously, then `System.exit(0)`. If the autosave fails, the player is asked "Autosave failed: <msg>. Quit anyway?". The exit function is injectable for tests.

### 5.3 Menu and dialog

The File menu becomes: **New Game**, **Save** (Ctrl+S; saves to the current slot, or acts as Save As when unnamed), **Save As…**, **Load…**, **Load from file…**, separator, **Quit**.

`SaveSlotDialog` has two modes:

- **Save As:** a table of existing slots plus a name field. Selecting a row fills the field. Overwriting asks for confirmation. An invalid name disables Save and shows the validation message.
- **Load:** the table (name, "autosave" tag, saved-at timestamp, in-game Y/D, credits), with Load and Delete (confirm) buttons. Double-click loads.

The table loads in a `SwingWorker`. Load/Save IO keeps Plan 4's `SwingWorker` and error-dialog behaviour (incompatible schema, bad JSON, IO error). "Load from file…" is Plan 4's `JFileChooser` path, kept for hand-edited or out-of-directory saves (and for the Plan 4 play-test steps 7–8).

### 5.4 Window close

`SpaceColonyFrame` switches to `DO_NOTHING_ON_CLOSE` and adds a `WindowListener` whose `windowClosing` calls `GameSession.quit()`. So the close box, File → Quit, and the crash dialog's Quit all autosave the same way.

### 5.5 Save confirmation

`TopBar` gains a transient status label. After a successful save or autosave it shows "Saved “colony”" for 3 seconds (a Swing `Timer`). Plan 4 §3.5 noted this was left for Plan 5.

## 6. Panel polish (spec §7.3–§7.4)

### 6.1 Detail panel (site)

- **Population:** `pop 250 / 468` plus a dim breakdown line from `PopCapBreakdown`: `base 200 + habitats 100 × 1.56 (colony mgmt)`. The multiplier part is omitted at ×1.00.
- **Morale:** a `JProgressBar` from 0 to the tech ceiling, labelled `1.12 / 1.56`. It turns the warning colour below 0.3 (the pop-decline threshold) and shows the plain `0.84 / 1.00` when no life-support tech is researched.
- **Production:** a "Net rate / day" grid from `productionRateCache`, showing non-zero resources with a sign and red for negatives. Spec §7.3 asks for production rates; the cache has existed since Plan 1 but was never shown.
- **Buildings:** each row gets its active tech multiplier, e.g. `MINE L2  ore ×1.43`, `FARM L1  food ×1.43 · water ×0.70`, `POWER_PLANT L1  ×1.25`, `REFINERY L1  ×1.20`, `RESEARCH_LAB L1  ×1.56`. Rows at ×1.00 show nothing extra. The mapping from building type to `TechEffects` calls lives in one private method.

### 6.2 Tech modal

- **Tiers:** techs are grouped under "Tier 0", "Tier 1" and "Tier 2" headers (antimatter is the deepest, at tier 2) by `TechAvailability.tier`, in catalog order within a tier. This gives the tree's shape without drawing a graph.
- **Row states:**
  - Researched: ✓ and dim.
  - Active: highlighted, with a progress bar of `accumulatedPoints / researchCost` and an ETA in days at the current lab rate.
  - Available: normal and clickable.
  - Locked: greyed and not clickable, with the tooltip "Requires: Ion Drives".
- Each row shows its description and prereq names.
- **Active effects:** a footer lists every non-1.0 multiplier from `TechEffects`, e.g. "Ore ×1.43 · Fuel cost ×0.56 · Pop cap ×1.20 · Morale cap ×1.20".
- **Lab rate:** the header shows the current research points per day. That's the same sum `ResearchPhase` uses, so the loop moves to a small public `ResearchPhase.pointsPerTick(World)` that both call.
- The modal stays **modal** but refreshes on `WorldChanged`. Its listener is added on show and removed on dispose, so the progress bar moves while the game runs underneath.

### 6.3 Goals modal

- Goals are grouped by `GoalCategory`. Each row shows ✓/○, name, rewards, description and a progress bar (§3.2) with its fraction label (`412 / 1,000`, `3 / 5 bodies`, or "done").
- Refreshes on `WorldChanged` like the tech modal.

### 6.4 Event strip

- Dates show as `Y2 D114` (matching `TopBar`) instead of `t=844`.
- **Filter toggles** on the right: INFO / WARNING / ERROR, all on by default. The toggles persist for the session only.
- **Click to select:** clicking an event that carries a `shipId`, `siteId` or `bodyId` (checked in that order) sets the selection, if the entity still exists. The cursor becomes a hand over such rows.
- Hovering shows the full message as a tooltip. Long messages are truncated in the row.

## 7. Testing strategy

### 7.1 Sim and engine (JUnit, headless)

- `TechAvailabilityTest`: roots have tier 0, `antimatter` is tier 2 (ion → fusion → antimatter), `missingPrereqs` lists unresearched prereqs in order.
- `CommandTest.queueResearch_missingPrereq_rejected`: queue `fusion-drives` fresh; after one tick, `activeId` is null and a `COMMAND_REJECTED` event names Ion Drives. `…_prereqMet_accepted` covers the success case.
- `GoalProgressTest`: fresh world gives pop goals the expected fraction, fleet 0, five-bodies 1/5 (Earth), and an achieved goal reports 1.0.
- `PopCapBreakdownTest`: reproduces the Plan 4 §5.2 sample table row by row. `HabitatCapTest` stays green, unchanged.
- `TransitFuelCostTest`: dispatch a ship; the fuel actually deducted at departure equals `TransitPhase.fuelCost(...)` for the stored transit.
- `SimulatorPhaseObserverTest`: an observer sees 8 phases per tick, in `SimPhase` order, with the right tick. A world advanced 500 ticks with an observer serialises identically (`SaveFile.toJson`) to one advanced without.
- `SaveFileJsonTest`: `toJson(fromJson(toJson(w)))` equals `toJson(w)` after 300 ticks of play. `save`/`load` still round-trip through a file.
- `EngineDebugTest` (on EDT): `step()` is refused (assertion) while running and advances exactly 1 tick while paused. `advanceSilently(250)` advances 250 ticks and fires exactly one `WorldChanged`. `applyDebugEdit` fires `WorldChanged` and is refused while running. `setDebugEnabled` fires `DebugModeChanged` once per actual change.
- `EventsTest`: switch to `applyForced`, no behaviour change.

### 7.2 Debug package (JUnit, headless)

- `RingBufferHandlerTest`: capacity is enforced oldest-first, `snapshot()` is ordered, and concurrent publish from 4 threads loses nothing up to capacity.
- `DebugLoggingTest`: level parsing (`DEBUG`→FINE, `WARN`→WARNING, bad value → INFO plus a warning record). Installing into a temp dir creates the log file. An unwritable dir still installs the ring handler.
- `EngineLoggingTest`: with a ring handler attached, a tick that emits a `RESEARCH_COMPLETED` event produces one INFO record on `spacecolony.sim.events`.
- `CrashHandlerTest`: an exception thrown on a new thread is logged SEVERE, lands in `ExceptionLog`, and calls the reporter once. A second exception while the reporter is "open" isn't reported again.
- `PhaseTimingsTest`: after 60 ticks it keeps 50 samples per phase, and mean ≤ max.
- `DeterminismCheckTest`: a fresh world matches over 1000 ticks. A deliberately non-deterministic hook (a test-only phase observer that mutates `credits` from `System.nanoTime()` parity on one copy) produces `match == false` with a `firstDiff` that mentions `credits`.
- `ObjectTreeModelTest`: a `Site` root exposes `siteBase` (final, read-only) and `population` (editable). A `Map` child shows keys. A self-referencing test object shows the `↺` leaf. `setValue` on `population` changes the field. `setValue` on `siteBase` throws.
- `YieldSummaryTest`: returns 3 entries sorted descending, and repeated calls hit the cache (same instance).

### 7.3 Save slots (JUnit, temp dirs)

- `SaveSlotsTest`:
  - `validateName` accepts `colony 1` and `mars_run-2` and rejects empty, 41 characters, `_x`, `a/b` and `a.json`.
  - `list()` after saving two slots and one autosave returns three entries, newest first, with the right tick and credits.
  - A garbage file lists as unreadable.
  - A schema-99 file lists as "schema 99".
  - `delete` removes only the targeted file.
  - `autosavePath(null)` is `_autosave.json`.
- `GameSessionTest`: slot-name transitions from §5.2. `quit()` writes the autosave to the right path and calls the injected exit. An autosave IO failure calls the "quit anyway?" prompt (injected) and exits only on yes.

### 7.4 UI smoke tests (extend `PanelSmokeTest`)

- The File menu has 7 components (New/Save/Save As/Load/Load from file/separator/Quit).
- `DetailPanel` with a site selected and `life-support-i` researched paints, and its text contains `/ 1.20`.
- `DebugOverlayPanel`, `ObjectInspectorDialog` (not shown, just built), `LogViewerDialog` and `SaveSlotDialog` construct and paint without throwing.
- `SystemMapPanel` paints with debug on and a ship in transit.
- `DebugController`: toggling on adds the Debug menu and the overlay, and toggling off removes both.

### 7.5 Play-test harness

`PlayTestDriver` updates its File menu expectations and switches Save/Load steps to `SaveSlotDialog`. Steps 7–8 (hand-edited saves) use "Load from file…". New scripted steps:

- Ctrl+D shows the overlay.
- Step advances one tick.
- Run 500 advances 500.
- Trigger a meteor strike, and the event appears in the strip.
- The determinism check reports OK.
- Close the window, then relaunch and confirm the autosave is listed.

### 7.6 Test count estimate

| Bucket | New tests |
|---|---|
| Sim + engine groundwork | ~20 |
| Debug package | ~20 |
| Save slots + session | ~10 |
| UI smoke | ~7 |
| **Plan 5 new** | **~57** |
| Carried over (current `main`) | 170 |
| **Total after Plan 5** | **~227** |

### 7.7 Manual play-test checklist

1. `./gradlew play --args="--seed 1 --debug --log-level=FINE"` opens with the overlay visible and the Debug menu present.
2. Ctrl+D hides both. Ctrl+D shows them again.
3. Pause, then Step: the tick increments by exactly 1. Run N 1000: the tick advances by 1000 and the UI stays responsive afterwards.
4. Trigger a disease outbreak on Earth: population drops and a WARNING shows in the strip and the log viewer.
5. Shift+click the Earth Hub in the colony list: the inspector opens. Edit toggle, set `population` to 999, commit: the dock shows 999. While running, the edit fields are disabled.
6. The determinism check reports OK.
7. Dump world: a file appears in `~/.space-colony/debug/`.
8. The map shows orbit periods, a ship's predicted arrival circle with its fuel label, and yield summaries.
9. Throw from a debug-only "Throw test exception" menu item: in debug mode the banner appears. Without debug, the crash dialog appears once, and Continue resumes the game.
10. Save As "colony", play on, then close the window: `colony.autosave.json` exists. Relaunch, Load…: both rows are listed, and loading the autosave restores the later tick.
11. Delete a slot from the Load dialog: the file is gone.
12. Research life-support-i: the dock's morale reads `x / 1.20`. Build a HABITAT: the breakdown line updates.
13. Tech modal: Fusion Drives is locked with "Requires: Ion Drives" and not clickable. The active tech's bar moves while the modal is open.
14. Goals modal: Population 1,000 shows a partial bar.
15. Event strip: untick INFO and only warnings remain. Click a ship event and the ship is selected.

## 8. Risks and decisions

- **Debug edits break the "UI never mutates World" rule.** This is contained to one audited, logged, paused-only method (§3.8). The sim itself is unchanged.
- **Reflection on sim classes.** `setAccessible(true)` works on the classpath build (no JPMS module descriptor). If modules are added later, `spacecolony.sim` would need to `opens` to `spacecolony.debug`.
- **The determinism check covers the envelope only.** See §4.5. It catches every divergence that affects saved state, which is what reproducibility means for a player.
- **Synchronous autosave on quit** can take a moment on a slow disk. That's acceptable, because the alternative loses the session.
- **The crash dialog is non-reentrant.** Exceptions during an open dialog are logged but not shown, which avoids dialog storms.

## 9. What's next

After Plan 5 the v1 scope in spec §14 is complete. Candidate Plan 6 themes: a balance pass driven by debug tooling (phase timings plus "Run N" make long-horizon tuning practical), save thumbnails, timed autosave, `distZip` packaging, and the deferred post-v1 list (sound, more bodies, Hohmann transfers).
