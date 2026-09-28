# Plan 6 — Startup Sequence, Options, Tutorial

**Date:** 2026-09-27
**Status:** Implemented (Plan 6). Deviation: `OptionsDialog` and `NewGameDialog` live in `ui.dialogs`, not `ui.startup`, so the in-game File menu can use them without `ui` importing `ui.startup`.
**Project root:** `/Users/steve/projects/space-colony/`
**Predecessor:** Plans 1–5 merged to `main` (sim core, render adaptation, engine + Swing UI shell, save/load + tech wiring, debug mode + save slots + panel polish). 281 tests.
**Source spec:** `2026-05-03-space-colony-game-design.md` §1–§2 (gameplay loops), §7 (UI), §9 (save/load). Plan 5 §9 "What's next" (timed autosave).

## 1. Vision

After Plan 5 every v1 bullet of the game spec is in, but the game still starts like a developer tool: `./gradlew play` drops the player straight into seed 42 at tick 0, there is nowhere to change a setting, and nothing explains what to do. Plan 6 adds the front door:

1. **Startup sequence.** A splash screen while the game gets ready, then a title screen with Continue, New Game, Tutorial, Load, Options and Quit. The in-game File menu gains a way back to it.
2. **Options.** A persisted options file and an Options dialog, reachable from the title screen and from the game.
3. **Tutorial.** A guided first game for new players that walks the short and medium loops of spec §2: run the clock, read a colony, research, build a colonizer, fly it to Mars and found a colony there.

While scoping the tutorial, two gaps turned up that make the core expansion loop impossible in a real game today. **Part A fixes them first**, because the tutorial's payoff step depends on both:

- **Ships never get fuel.** `Ship` starts with `fuel = 0`, nothing ever adds any, and `CommandPhase.applyDispatchShip` rejects any dispatch whose estimated cost exceeds `s.fuel`. So every dispatch of a newly built ship is rejected. (`PlayTestDriver` step 7 works around it with `h.fuel = 1_000_000`.)
- **Colonizers can't reach an empty body.** `DispatchShipCommand` only targets an existing site, and `BuildSiteCommand` requires the colonizer to be docked at a site on the target body. There's no way to get a colonizer to Mars before Mars has a site. (`SiteBaseTest` works around it with a stub site.)

**Out of scope:** difficulty levels or scenario options on New Game; contextual hints outside the tutorial; sound; saving and resuming a tutorial in progress; key rebinding (the Controls tab is a read-only reference); carrying colonists on a colonizer (new sites still start at population 0 and grow as today); economy balance beyond what the tutorial world needs; `distZip` packaging.

## 2. Architecture overview

### 2.1 New and changed packages

```
spacecolony/
├── SpaceColonyApp.java             (CHANGED: parses LaunchArgs, loads Options, hands off to AppController)
├── LaunchArgs.java                 (NEW: pure parse of --seed, --debug, --log-level, --skip-intro)
├── sim/
│   ├── Ship.java                   (CHANGED: orbitingBodyId)
│   ├── Transit.java                (CHANGED: destBodyId)
│   ├── World.java                  (CHANGED: randomEventsEnabled)
│   ├── commands/SetRandomEventsCommand.java   (NEW)
│   └── phases/                     (CHANGED: CommandPhase, TransitPhase, EventPhase)
├── save/SaveFile.java              (CHANGED: schema v2, still loads v1)
├── options/                        (NEW PACKAGE: no Swing)
│   ├── Options.java                (record + DEFAULTS)
│   └── OptionsStore.java           (~/.space-colony/options.properties)
├── tutorial/                       (NEW PACKAGE: no Swing; engine + sim only)
│   ├── TutorialStep.java           (record: id, title, text, target key, completion test)
│   ├── TutorialScript.java         (the 12 steps)
│   ├── TutorialContext.java        (read-only view: world, speed, selection, view)
│   ├── TutorialProgress.java       (current index, advance/skip rules)
│   └── TutorialScenario.java       (the tutorial world)
└── ui/
    ├── startup/                    (NEW)
    │   ├── AppController.java      (splash → title → game → title lifecycle, crash-dialog routing)
    │   ├── SplashWindow.java
    │   ├── TitleScreen.java
    │   ├── Starfield.java          (backdrop painter shared by splash and title)
    │   ├── NewGameDialog.java
    │   └── OptionsDialog.java
    ├── tutorial/                   (NEW)
    │   ├── TutorialController.java (listens to the engine, drives TutorialProgress, mounts the UI)
    │   ├── CoachPanel.java         (step card)
    │   ├── HighlightLayer.java     (pulsing outline around the step's target)
    │   └── TutorialTargets.java    (find a component by its target key)
    ├── AutosaveTimer.java          (NEW: timed autosave)
    └── (GameSession, FileMenu, SpaceColonyFrame, TopBar, DetailPanel, ColonyListPanel,
         dialogs/DispatchShipDialog, dialogs/PlaceSiteDialog changed)
```

### 2.2 Layering (extends Plan 5)

- `options → engine` (for `Speed`) and `java.util.logging` (for `Level`). No Swing, no `ui`.
- `tutorial → engine, sim, world`. No Swing. This keeps the script and its completion tests headless-testable.
- `ui.tutorial → tutorial, ui, engine`. `ui.startup → ui, ui.tutorial, options, save, world, debug, engine`.
- **`ui` (the existing panels) never imports `ui.tutorial` or `ui.startup`.** Panels expose target keys by calling `setName(...)` on the components the tutorial points at (§5.4), and `SpaceColonyFrame` takes a small `Hooks` object from whoever builds it (§3.5). The package graph stays acyclic.
- `debug` is unchanged and still never imports `ui`.

### 2.3 Threading

Unchanged: everything on the EDT. New IO follows Plan 5's pattern:

- The splash lists save slots in a `SwingWorker` (the slot list is what Continue needs).
- Timed autosave snapshots the world on the EDT with `SaveFile.toJson` (tens of KB, well under a frame) and writes the string in a `SwingWorker`, so the write never races a tick.
- Options are read synchronously in `main` before any AWT class loads (the UI scale must be set before the toolkit starts), and written synchronously when the Options dialog closes (one small file).

## 3. Startup sequence

### 3.1 Launch arguments

`LaunchArgs.parse(String[])` returns `LaunchArgs(Long seed, boolean debug, Level logLevel, String badLevel, boolean skipIntro)`. Rules:

- `--seed N` **starts a new game with that seed immediately**, skipping splash and title. This keeps today's `./gradlew play --args="--seed 7"` workflow and the play-test habits working.
- `--skip-intro` skips the splash but still shows the title.
- `--debug` and `--log-level=` keep their Plan 5 meaning. `--debug` wins over the options file's "Start in debug mode", and `--log-level` wins over its log level.
- `./gradlew play -Pdebug` keeps working (it only adds `--debug`).

### 3.2 Order of work in `main`

1. Parse `LaunchArgs`.
2. Load `Options` (§4). If `uiScalePercent != 0`, set `sun.java2d.uiScale` before any Swing class is touched.
3. Install logging and the crash handler, as in Plan 5, at the options' log level unless `--log-level` overrides it.
4. On the EDT: `new AppController(options, store, slots, exceptions, args).start()`.

### 3.3 Splash screen

`SplashWindow` is an undecorated `JWindow`, 640 × 360, centred:

- `Starfield` backdrop (dark sky, deterministic stars, the sun low on the left with two faint orbit arcs).
- "SPACE COLONY" in a large bold face, the version (`v0.6.0`, read from `spacecolony/version.properties`, which Gradle fills from `project.version`), and a status line with a thin progress bar underneath.
- Status lines as work progresses: "Reading options…" → "Looking for saved colonies…" → "Ready".

`AppController.start()` shows it, runs `SaveSlots.list()` in a `SwingWorker`, and moves on to the title when **both** the list is done and **1.5 s** have passed. A click or any key after the list is done skips the rest of the wait. A failed listing is logged and treated as "no saves" rather than blocking startup.

The splash is skipped when `options.showSplash` is off, with `--skip-intro`, or when the environment is headless (tests).

### 3.4 Title screen

`TitleScreen` is its own `JFrame` ("Space Colony", same 1280 × 800 default size as the game window, maximized if `options.startMaximized`). The `Starfield` backdrop animates slowly: the inner planets drift along their orbit arcs on a 30 fps Swing `Timer`, which stops while the window is hidden.

A centred column holds:

| Button | Behaviour |
|---|---|
| **Continue** | Loads the newest readable save from the splash's slot list, autosaves included. The button's second line reads e.g. `colony · Y3 D120 · 2 hours ago`. Disabled with "No saved games yet" when there's none. |
| **New Game…** | `NewGameDialog` (§3.6). |
| **Tutorial** | Starts the tutorial (§5). |
| **Load Game…** | Plan 5's `SaveSlotDialog.load`. |
| **Options…** | `OptionsDialog` (§4.3). |
| **Quit** | Exits. There's nothing to autosave on the title. |

**New players.** When `!options.tutorialCompleted` and the slot list is empty, a banner above the buttons reads "New to Space Colony? The tutorial takes about 10 minutes.", Tutorial becomes the default button (Enter) and gets the accent style. Otherwise Continue is the default when enabled, then New Game. The rule lives in a pure static `TitleScreen.defaultAction(Options, List<SlotInfo>)` so it can be unit-tested.

Keyboard: Up/Down moves focus through the buttons, Enter activates, Escape does Quit (which asks for no confirmation, because nothing is at stake).

Load errors from Continue or Load Game reuse Plan 4's dialogs (incompatible schema, bad JSON, IO error) and leave the title showing. That logic moves out of `FileMenu.loadFrom` into a shared `ui.SaveLoading.load(Component owner, Path, Consumer<World> onLoaded)` so the title and File menu don't duplicate it.

A footer shows the version, and "Debug mode on" when the next game will start in debug mode.

### 3.5 Starting and leaving a game

`AppController` owns the lifecycle:

```java
void startGame(World world, String slotOrNull, GameSession.Mode mode);  // builds the frame
void returnToTitle(SpaceColonyFrame frame);                              // tears it down
```

`startGame` creates a new `Engine` and `SpaceColonyFrame` for each game, applies `options.startSpeed` (Paused by default) and the debug flag, starts the autosave timer (§4.4), hides the title, and shows the frame. A fresh engine per game avoids any listener leaking from one game to the next.

`SpaceColonyFrame` gains a constructor that takes `Hooks`:

```java
public record Hooks(IntConsumer exit, Runnable mainMenu, Supplier<Options> options,
                    Consumer<Options> saveOptions) {
    /** What the existing constructors use: System::exit, no Main Menu item, in-memory default options. */
    public static Hooks standalone();
}
```

The existing `SpaceColonyFrame(Engine)` and `(Engine, ExceptionLog)` constructors keep working with `Hooks.standalone()`, so the play-test drivers and tests that build a frame directly don't change.

**File menu** becomes: New Game, Save, Save As…, Load…, Load from file…, separator, **Options…**, **Main Menu**, separator, Quit. Main Menu is omitted when `hooks.mainMenu()` is null.

**Main Menu** goes through `GameSession.leave()`, which shares its steps with `quit()`: pause, confirm ("Return to the main menu? Your game will be autosaved."), autosave, then run the `mainMenu` hook instead of `exit`. The hook calls `AppController.returnToTitle`, which stops the autosave timer, disposes the `GameLoop`, `TutorialController` (if any) and frame, re-lists the slots (so Continue now points at the game just left), and shows the title.

**Crash dialog routing.** `CrashHandler` is installed once, in `main`, with a reporter and a debug-flag supplier that both delegate to `AppController`. While a game frame is showing, those go to that frame (`frame::showCrashDialog`, `engine::debugEnabled`). On the title screen, the reporter shows a plain "Something went wrong" dialog with Continue and Quit (Quit exits, since there's nothing to autosave), and the debug flag is false.

### 3.6 New Game dialog

A small modal dialog:

- **Seed:** a text field prefilled with a random `long` (from `System.nanoTime()`), plus a **Randomize** button. It must parse as a `long`. An invalid value disables Start and shows "Seed must be a whole number".
- **Start** and **Cancel**.

It returns the seed, or null. The in-game File → New Game keeps its current "Discard the current game?" confirm and then shows this same dialog instead of today's bare input box.

## 4. Options

### 4.1 Options model

```java
package spacecolony.options;

public record Options(
    Speed startSpeed,          // PAUSED or X1                               default PAUSED
    int autosaveMinutes,       // 0 (off), 5, 10, 15, 30                      default 10
    boolean confirmQuit,       //                                            default true
    boolean showSplash,        //                                            default true
    boolean startMaximized,    //                                            default false
    int uiScalePercent,        // 0 (automatic), 100, 125, 150, 200           default 0
    boolean startInDebug,      //                                            default false
    Level logLevel,            // FINE, INFO, WARNING                         default INFO
    boolean tutorialCompleted  // not shown in the dialog                     default false
) {
    public static final Options DEFAULTS = ...;
    public Options withTutorialCompleted(boolean done);
}
```

The allowed values are constants on `Options` so the dialog's combo boxes and the store's validation share them.

### 4.2 OptionsStore

- File: `~/.space-colony/options.properties`, or the path in the `spacecolony.optionsFile` system property (tests and play-test drivers point it at a temp dir, the same way `spacecolony.savesDir` works).
- Keys: `gameplay.startSpeed`, `gameplay.autosaveMinutes`, `gameplay.confirmQuit`, `display.showSplash`, `display.startMaximized`, `display.uiScalePercent`, `developer.startInDebug`, `developer.logLevel`, `progress.tutorialCompleted`.
- `load()`: a missing file gives `DEFAULTS`. Each key is parsed independently, so one bad or unknown value falls back to its default, logs at `WARNING` once with the key and raw value, and leaves the rest intact. Unknown keys are ignored, so an older build can read a newer file. `load()` never throws, because options must never stop the game from starting.
- `save(Options)`: writes to `options.properties.tmp` and moves it into place (the same atomic pattern as `SaveFile.save`), with a header comment naming the game. It throws `IOException`, and the dialog shows "Could not save options: <msg>" and keeps the new values for this session.

### 4.3 Options dialog

`OptionsDialog.show(Component owner, Options current) → Options or null`. It's modal and has four tabs:

| Tab | Controls |
|---|---|
| **Gameplay** | Starting speed (Paused / 1×); Autosave (Off / every 5 / 10 / 15 / 30 minutes); Confirm before quitting (checkbox). |
| **Display** | Show splash screen; Start maximized; Interface scale (Automatic / 100% / 125% / 150% / 200%), with "Takes effect the next time the game starts." underneath. |
| **Controls** | A read-only table of every shortcut: speed buttons, File shortcuts (platform menu key + S, Shift+S, O, Q), Ctrl+D debug mode, and the debug-only F10, Ctrl+R, Ctrl+I and Ctrl+L. Built from the same `KeyStroke` constants the menus use, so the table can't drift from the bindings. |
| **Developer** | Start in debug mode; Log level (FINE / INFO / WARNING). |

Buttons: **OK**, **Cancel**, **Restore defaults** (resets the controls, not `tutorialCompleted`). In-game, opening the dialog pauses the game and restores the prior speed on close, like the other File actions.

When the dialog is accepted, `AppController` (or `Hooks.options` in a standalone frame) saves the file and applies each change as follows:

| Option | Takes effect |
|---|---|
| Starting speed, Start maximized, Start in debug mode | Next game started |
| Autosave interval | Immediately (the timer restarts) |
| Confirm before quitting | Immediately. When off, Quit and Main Menu skip the confirm but still autosave. |
| Log level | Immediately (`DebugLogging.current().setLevel`) |
| Show splash, Interface scale | Next launch |

### 4.4 Timed autosave

Plan 5 kept autosave to quit only. Plan 6 adds `AutosaveTimer`, a Swing `Timer` at `autosaveMinutes` of wall-clock time:

- On each fire, if the world's tick has moved since the last autosave and the game isn't a tutorial, it runs `GameSession.autosaveInBackground()`. That snapshots with `SaveFile.toJson` on the EDT, writes the string off-EDT to the same `slots.autosavePath(currentSlot)` that quit uses, logs it, and shows the TopBar toast "Autosaved".
- A failure is logged at `WARNING` and toasts "Autosave failed". It never shows a modal in the middle of play.
- The timer is off when `autosaveMinutes == 0` and is stopped by `returnToTitle` and quit.

`SaveFile` gains `writeJson(String json, Path)`, the atomic-write half of `save`, so the worker doesn't touch the live world.

## 5. Tutorial

### 5.1 Shape

The tutorial is a normal game on a prepared world, with a **coach card** that shows one step at a time and a **highlight** around the control that step is about. Steps complete by **watching the game state**, so the player learns by using the real UI. Only the reading steps have a Next button. The script is 12 steps, and a player who knows the controls can finish it in about 10 minutes of real time.

### 5.2 Tutorial world

`TutorialScenario.world()`:

- `WorldGenerator.generate(TUTORIAL_SEED)` with a fixed seed, so every player sees the same system and the steps' numbers match the text.
- **Random events off** (`world.randomEventsEnabled = false`, §6.3), so a meteor strike can't derail step 8.
- Earth Hub gets extra stock for a comfortable first flight: FUEL 400 (the worst-case Earth→Mars colonizer run with a full hold is about 200, see §6.1), FOOD 300 and WATER 300.

### 5.3 The script

| # | Id | Card says (short form) | Target | Done when |
|---|---|---|---|---|
| 1 | `welcome` | What Space Colony is; the goal is a network of colonies; the game is paused. | — | Next |
| 2 | `start-clock` | Press **1×** to start time. One tick is one day. | `topbar.speed.x1` | speed ≠ PAUSED |
| 3 | `select-hub` | Click **Earth Hub** in the colony list. | `colonylist` | selection = site `site-earth-hub` |
| 4 | `read-dock` | Tour of the dock: population vs cap, morale, stockpiles, net/day, buildings. | `detail` | Next |
| 5 | `build-lab` | Research needs a lab. **Build building… → RESEARCH_LAB**. | `detail.buildBuilding` | Earth Hub has a RESEARCH_LAB |
| 6 | `research` | Open **Tech** and pick any tech without a lock. | `topbar.tech` | `tech.activeId != null` or any tech researched |
| 7 | `build-colonizer` | New colonies need a colonizer. With Earth Hub selected: **Build ship… → COLONIZER**. | `detail.buildShip` | a COLONIZER exists |
| 8 | `dispatch` | Select the colonizer, **Dispatch…** to **Mars (unsettled)**. Pack FOOD 40, WATER 40, METAL 20. Fuel comes from the hub's stock. | `detail.dispatch` | a colonizer is LOADING or IN_TRANSIT with `destBodyId = "mars"` |
| 9 | `fast-forward` | Transits take days. Try **16×**. | `topbar.speed.x16` | a colonizer has `orbitingBodyId = "mars"` |
| 10 | `found-colony` | The colonizer is orbiting Mars. **Found colony…**, then click a spot on the surface. | `detail.foundColony`, then `bodyview.sphere` once the view is on Mars | a site exists on Mars |
| 11 | `goals` | That completed the "Settle Mars" goal. **Goals** lists the rest. | `topbar.goals` | Next |
| 12 | `finish` | Recap, then **Keep playing** or **Main menu**. | — | a button |

The card text is written in `TutorialScript` as plain sentences, with the buttons named exactly as the UI labels them. `TutorialScriptTest` checks every bold label in the text against the real component text.

### 5.4 Targets

A target key names a component with `Component.setName(key)`. The panels set these names themselves: `TopBar` (`topbar.speed.x1`, `topbar.speed.x16`, `topbar.tech`, `topbar.goals`), `ColonyListPanel` (`colonylist`), `DetailPanel` (`detail`, `detail.buildBuilding`, `detail.buildShip`, `detail.dispatch`, `detail.foundColony`) and `BodyViewPanel` (`bodyview.sphere`). This is the only change the existing panels need for the tutorial.

`TutorialTargets.find(Container root, String key)` walks the component tree and returns the first **showing** match, or null. A step can name several targets in order, and the first one showing wins, which is how step 10 moves from the Found colony… button to the Mars sphere. When a step's target isn't showing (say, the Build ship… button while a ship is selected), the highlight hides and the card adds a hint line from the step, e.g. "Select Earth Hub first."

`TutorialScriptTest` also builds a `SpaceColonyFrame` on the tutorial world and checks that every target key resolves in the state its step expects, so a renamed button fails a test rather than silently breaking the tutorial.

### 5.5 Progress rules

`TutorialProgress` holds the current step index and is pure:

- `update(TutorialContext)`: while the current step is automatic and its test passes, advance. So a player who works ahead (for example, builds the colonizer before the lab) skips the steps they've already done.
- `next()`: advance a manual step.
- `skip()`: advance any step. This is the escape hatch if a player gets stuck.
- Completion tests take a `TutorialContext` record, `(World world, Speed speed, Selection selection, View view)`, and never mutate anything.

### 5.6 UI

`TutorialController` (in `ui.tutorial`) is created by `AppController.startGame` for a TUTORIAL game. It:

- listens to `WorldChanged`, `SelectionChanged`, `SpeedChanged` and `ViewChanged`, builds a `TutorialContext`, and calls `progress.update`;
- on a step change, updates the card and moves the highlight, and logs `INFO` to `spacecolony.tutorial` ("Tutorial step 5/12: build-lab");
- on `WorldReplaced` (the player used New Game or Load from the File menu), ends the tutorial quietly and switches the session to NORMAL, because the tutorial world is gone.

**CoachPanel.** A 340 px wide rounded card in the lower-left of the main view, above the event strip. It sits on the frame's `JLayeredPane` at `PALETTE_LAYER` and repositions on resize. It shows "Step 5 of 12", a title, the body text (HTML in a non-editable `JEditorPane`, game palette), the optional hint line, and a button row: **Next** (manual steps), **Skip step**, **Exit tutorial**, and a collapse chevron that shrinks the card to its title line. Exit asks "Leave the tutorial? You can keep playing this game or return to the main menu." with Keep playing, Main menu and Cancel.

**HighlightLayer.** A non-opaque `JComponent` on the layered pane at `PALETTE_LAYER`, sized to the layered pane, which paints a 2 px `UiColors.INFO` rounded outline around the target's bounds (converted with `SwingUtilities.convertRectangle`), pulsing alpha on a 50 ms timer. Its `contains(x, y)` returns false, so it never takes mouse events from the UI underneath. It repaints on target component moves and resizes.

Modal dialogs (Tech, Build ship…, Dispatch…) cover the card while open. The card doesn't try to follow into them. Their steps complete from the resulting game state after the dialog closes.

### 5.7 Tutorial mode in the session

`GameSession` gains `enum Mode { NORMAL, TUTORIAL }`:

- **TUTORIAL:** Save and Save As… are disabled, with the tooltip "Not available during the tutorial". Quit and Main Menu don't autosave, and their confirm text is "Leave the tutorial?". The autosave timer skips. The title bar reads "Space Colony — Tutorial".
- **Keep playing** (from step 12 or from Exit): enqueues `SetRandomEventsCommand(true)`, switches the mode to NORMAL (so Save works, and the game is unnamed until Save As), removes the card and highlight, and restores the title bar.
- Reaching step 12, or choosing Keep playing or Main menu from Exit at any step, sets `tutorialCompleted = true` in the options file. Exiting early still counts, because the point is to stop nagging, and Tutorial stays on the title screen for anyone who wants it again.

## 6. Part A: making expansion playable

### 6.1 Ships refuel from their origin

At departure (`TransitPhase.loadingAndUnloading`, when the manifest is filled):

```
cost = fuelCost(...)
if (s.fuel < cost) {
    double draw = min(cost − s.fuel, origin.stockpile[FUEL]);
    origin.stockpile[FUEL] −= draw;  s.fuel += draw;
}
if (s.fuel < cost) → existing SHIP_OUT_OF_FUEL abort (unchanged)
```

The ship draws only the shortfall for this trip. There is no tank capacity to model. FUEL packed in the manifest is still cargo, and is loaded before the draw, so a player shipping fuel to a colony and the ship's own burn draw from the same stock in a predictable order.

`CommandPhase.applyDispatchShip`'s early check becomes `s.fuel + originFuel − manifestFuel < estCost → reject`. The message changes to "Not enough fuel at <origin> for this trip: need ≈N, have M", which is also what a player sees when Earth runs dry. (Earth starts with 100 FUEL and nothing but gas-giant MINEs with Atmospheric Mining produce more. That's a balance question outside this plan. The tutorial world stocks enough, see §5.2.)

### 6.2 Colonizers fly to unsettled bodies

- `Transit` gains `String destBodyId`. A site-bound trip has `destSiteId` set and `destBodyId` null (as today). A body-bound trip has `destSiteId` null and `destBodyId` set.
- `DispatchShipCommand` gains a `destBodyId` component, and exactly one of `destSiteId` and `destBodyId` must be non-null. The record keeps a three-argument constructor `(shipId, destSiteId, manifest)` for site trips, so existing call sites don't change, and adds a `toBody(shipId, bodyId, manifest)` factory.
- Only a **COLONIZER** may take a body-bound trip. Otherwise it's rejected with "Only colonizers can travel to a body without a site". An unknown body is rejected too.
- Arrival and fuel math already work body to body underneath (`OrbitalGeometry.bodyPosition`). `computeArrivalTick` and `distanceBetweenSitesAtTicks` become `…Between(originBodyId, destBodyId…)` with a small `destBodyOf(Transit)` helper.
- On arrival at a body, the ship becomes `IDLE` with `currentSiteId = null` and the new **`Ship.orbitingBodyId`** set. Its cargo stays aboard, and it emits "Colonizer <name> is orbiting <body>".
- An orbiting colonizer can't be dispatched again ("Ship is orbiting <body>; found a colony or retire it"). It can be retired.
- `BuildSiteCommand` accepts a colonizer that is docked at a site on the body (today's rule) **or** orbiting it. On success, the colonizer's **cargo is unloaded into the new site's stockpile** (all at once; a normal unload also has no cap) before the ship is consumed. So "pack food and water" in step 8 is what keeps the colony alive.
- The UI:
  - `DispatchShipDialog` lists sites by name first, then, for a COLONIZER only, each body without a site as "<Body> (unsettled)".
  - `DetailPanel` shows an orbiting ship's state as "Orbiting Mars", hides Dispatch…, and shows **Found colony…**, which selects the body and switches to its body view.
  - `ColonyListPanel` shows "orbiting Mars".
  - `PlaceSiteDialog` finds a colonizer that is orbiting the body as well as one docked there.
  - The system map draws orbiting ships as a small dot next to their body.

### 6.3 Random events switch

`World` gains `boolean randomEventsEnabled = true`. `EventPhase.run` returns immediately when it's false, **after** creating its RNG as today, so determinism across the switch is simple to reason about. `applyForced` (debug and tests) ignores the flag. `SetRandomEventsCommand(boolean enabled)` is the only way the flag changes after world creation, and it runs in `CommandPhase` like every other command. This keeps "the UI never mutates World".

### 6.4 Save schema v2

`SaveFile.SCHEMA_VERSION` becomes 2. v2 adds `randomEventsEnabled` (root), `orbitingBodyId` (ship, nullable) and `destBodyId` (transit, nullable). **Loading accepts v1 and v2.** A v1 file gets `randomEventsEnabled = true` and null for the new fields, which is exactly its meaning. `IncompatibleSaveException` is still raised for anything else. `SaveSlots.list()` treats v1 files as OK, not "(schema v1)". Existing players' slots keep loading.

## 7. Testing strategy

### 7.1 Sim and save (JUnit, headless)

- `RefuelTest`: a new HAULER with 0 fuel departs, and the origin's FUEL drops by exactly `fuelCost(...)`. With origin FUEL less than the cost, the dispatch is rejected at command time with the new message. A ship that already has enough fuel draws nothing. FUEL in the manifest is loaded as cargo before the draw.
- `ColonizeTest`: dispatch a colonizer `toBody("mars")`. It moves LOADING → IN_TRANSIT → IDLE with `orbitingBodyId = "mars"` and `currentSiteId = null`. `BuildSiteCommand` from orbit creates the site, moves cargo into its stockpile, and consumes the ship. A HAULER `toBody` is rejected. An orbiting colonizer can't be re-dispatched, but can be retired. This test replaces `SiteBaseTest`'s stub-site setup with the real path, and the stub test stays as a regression test for the docked case.
- `RandomEventsSwitchTest`: a seed known to roll events within 5,000 ticks rolls none with the flag off. `SetRandomEventsCommand(true)` turns them back on. Advancing with the flag off is still deterministic (two copies match).
- `SaveFileSchemaTest`: v2 round-trips an orbiting ship, a body-bound transit and the flag. A v1 fixture (checked in under `src/test/resources/saves/v1-sample.json`) loads with the defaults. A v3 file is rejected. `SaveSlotsTest`: a v1 file lists as OK.
- Existing tests that build `DispatchShipCommand` keep the three-argument constructor unchanged. `PlayTestDriver` step 7 drops its `h.fuel = 1_000_000` line.

### 7.2 Options, args, tutorial (JUnit, headless)

- `OptionsStoreTest`: a missing file gives defaults. Round-trip works. One bad value falls back per key and keeps the others. Unknown keys are ignored. The write is atomic (no `.tmp` left behind). The system-property override works.
- `LaunchArgsTest`: `--seed 7` means skip the title. `--skip-intro` works. `--debug` works. A bad `--log-level` is kept in `badLevel`. No args gives no seed, no debug, INFO.
- `TutorialProgressTest`: automatic steps advance when their tests pass, manual steps wait for `next()`, `skip()` always advances, and working ahead skips satisfied steps.
- `TutorialScriptTest` (headless walk-through): starting from `TutorialScenario.world()`, drive the engine with the same commands the UI would enqueue (speed, selection, build lab, queue research, build colonizer, dispatch `toBody("mars")`, advance until orbiting, build site) and assert the progress lands on each step in order, ending on `finish` with "Settle Mars" achieved. This is also the end-to-end test for Part A.
- `TutorialScriptTest.labels`: every bold UI label in step text matches a real button or menu label.
- `TitleScreenTest`: `defaultAction` returns TUTORIAL for a first run, CONTINUE with saves, NEW_GAME when saves exist but none are readable.
- `GameSessionTest` additions: TUTORIAL mode quits without autosaving. `leave()` autosaves and calls the main-menu hook, not exit. `autosaveInBackground()` writes the autosave path and skips when the tick hasn't moved.

### 7.3 UI smoke (JUnit; frames need Xvfb, like `DebugControllerTest`)

- The File menu has 10 components with a Main Menu hook, and 9 without.
- `SplashWindow`, `TitleScreen`, `NewGameDialog` (built, not shown) and `OptionsDialog` construct and paint without throwing.
- `TutorialTargetsTest`: on a tutorial frame, every step's target key resolves in the state its step expects (hub selected for `detail.buildShip`, colonizer selected for `detail.dispatch`, orbiting colonizer for `detail.foundColony`, Mars body view for `bodyview.sphere`).
- `HighlightLayer.contains` is false everywhere. A click at a highlighted button's centre still reaches the button.
- `DispatchShipDialog` lists "Mars (unsettled)" for a colonizer and not for a hauler.

### 7.4 Play-test harness

A new `StartupDriver` (`./gradlew startupPlayTest`, runs under `xvfb-run` like `debugPlayTest`), with options and saves pointed at temp dirs:

1. The splash shows, then the title. Continue is disabled and the first-run banner is visible.
2. Options: set autosave to 5 minutes and click OK. The file on disk has `gameplay.autosaveMinutes=5`.
3. Tutorial: click through all 12 steps with real clicks on the highlighted controls, running `advanceSilently` only to shorten the Mars flight. Screenshot each step to `build/playtest/tutorial-NN.png`.
4. Keep playing: Save As works.
5. Main Menu: the title shows again, and Continue now names that game.

### 7.5 Test count estimate

| Bucket | New tests |
|---|---|
| Part A (refuel, colonize, events switch, schema) | ~18 |
| Options + launch args | ~10 |
| Tutorial (progress, script, labels, targets) | ~10 |
| Session + title logic | ~7 |
| UI smoke | ~6 |
| **Plan 6 new** | **~51** |
| Carried over (current `main`) | 281 |
| **Total after Plan 6** | **~332** |

### 7.6 Manual play-test checklist

1. `./gradlew play` shows the splash for about 1.5 s, then the title. A click on the splash skips it once the saves are listed.
2. First run (empty `~/.space-colony/saves`, no options file): the banner shows and Enter starts the tutorial.
3. Tutorial: each step's highlight sits on the right control. Doing step 7 before step 5 skips ahead correctly. Skip step and Exit tutorial work.
4. The colonizer flies to Mars on its own fuel draw. Found colony… opens the Mars body view, clicking the surface founds the colony, and "Settle Mars" is achieved.
5. Keep playing: Save As "first" works. The title bar drops "Tutorial".
6. File → Main Menu: the confirm appears, the game autosaves, the title returns, and Continue reads "first · …".
7. Options: turn autosave to 5 minutes, play for 5 minutes at 1×, and see the "Autosaved" toast and a fresh `first.autosave.json`.
8. Options: set the interface scale to 150%, restart, and the UI is larger. Turn off the splash, restart, and the title comes straight up.
9. `./gradlew play --args="--seed 7"` skips splash and title and starts seed 7 directly. `./gradlew play -Pdebug` shows the title with "Debug mode on", and the next game has the overlay.
10. An old Plan 5 save (schema v1) still appears in Load and loads.
11. Throw a test exception from the Debug menu with debug off: the crash dialog appears over the game, and its Quit autosaves as before.

## 8. Risks and decisions

- **Two frames instead of one.** The title is its own window and each game gets a fresh `Engine` and frame. That costs a window swap, but it means no game state or listener survives into the next game, and the existing `SpaceColonyFrame` stays close to how it is.
- **Tutorial isn't saveable.** This keeps the save format free of tutorial state. Keep playing converts the tutorial into a normal game that saves like any other.
- **Refuel draws only this trip's shortfall.** It's simple and predictable, but a ship leaving a colony with no FUEL stock can strand itself for the return trip. That's the player's problem to plan, and a "can't afford the trip" rejection makes it visible before departure.
- **Schema bump.** v1 saves load, but saves written by Plan 6 can't be opened by a Plan 5 build. That's acceptable for a single-player game on one machine.
- **UI scale needs a restart.** Swing reads `sun.java2d.uiScale` only at toolkit start. On macOS the Retina scale already applies, and the option has no effect there. The dialog says so on macOS.
- **Highlight by component name.** It's cheap and needs no new API, but it's stringly typed. `TutorialTargetsTest` is what keeps it honest.

## 9. What's next

Candidate Plan 7 themes: a balance pass (fuel production outside gas giants, colonist transport, early-game water), contextual hints for non-tutorial games driven by the same `TutorialStep` machinery, sound, save thumbnails, and `distZip` packaging.
