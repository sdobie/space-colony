# Space Colony Game — Plan 2: Renderer Adaptation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adapt `PlanetGenerator` and `SphereRenderer` from `/Users/steve/projects/planet-map/` into a new `spacecolony.render` package, parameterized by `BodyType`, with atmosphere color injection and a screen-to-lat/lon inverse projection. Output verified by deterministic JUnit tests + a `RenderDemo` driver that dumps one PNG per body type for visual inspection.

**Architecture:** A new `spacecolony.render` package depends on `spacecolony.sim` (for `BodyType` enum) and `spacecolony.world` (for `SimplexNoise`, already imported in Plan 1). A `BodyAppearance` record per body type drives per-type biome palettes and rendering tweaks (atmosphere color, ocean handling). The pipeline structure of the original `PlanetGenerator` is preserved; per-type variation comes from data, not control-flow forks, with one exception (gas giants use latitude bands instead of elevation).

**Tech Stack:** Java 25, Gradle 9 via `./gradlew`, JUnit 5. No new runtime dependencies. AWT `BufferedImage`/`Color` come from the JDK.

**Spec reference:** `/Users/steve/projects/space-colony/docs/superpowers/specs/2026-05-03-space-colony-game-design.md` §13.

**Plan 1 status:** merged on `main` at `dde0723`. Sim core is complete with 52 tests. Plan 2 ships independent of Plan 1 — no Simulator changes.

**Plan 3 / 4 (out of scope here):** Plan 3 wires the renderer into a Swing UI shell. Plan 4 adds save/load + tech/goals/events surfacing + debug mode.

---

## Context

The brainstorm spec says the renderer is "purely visual flavor" — the simulator already runs without it. Plan 2 produces the visual layer: a flat equirectangular `BufferedImage` per body (the surface map) and a 3D-projected `BufferedImage` (the rotating sphere view) that Plan 3 will put into Swing panels. Three properties matter:

1. **Determinism** — same inputs always produce the same pixels (so screenshots in tests are stable and the renderer can be exercised headlessly in CI).
2. **Body-type-aware appearance** — a gas giant should not look like Earth.
3. **Click-to-lat/lon** — Plan 3's BodyView needs to convert a mouse click on the rendered sphere back to a `(lat, lon)` so the player can plant sites at specific surface coordinates.

Plan 2 is intentionally bounded: no Swing imports, no UI, no game-state coupling. The rendered images are pure functions of their inputs.

---

## File Structure

```
/Users/steve/projects/space-colony/
├── src/main/java/spacecolony/
│   ├── render/                                  (NEW package)
│   │   ├── BodyAppearance.java                  (per-type rendering config record)
│   │   ├── BodyAppearances.java                 (default appearance for each BodyType)
│   │   ├── PlanetGenerator.java                 (adapted from planet-map; produces BufferedImage)
│   │   ├── SphereRenderer.java                  (adapted from planet-map; renders sphere + inverse projection)
│   │   └── RenderDemo.java                      (CLI: writes one PNG per body type to /tmp)
│   └── sim/, world/, Main.java                  (unchanged from Plan 1)
└── src/test/java/spacecolony/render/
    ├── PlanetGeneratorTest.java                 (determinism + body-type variance + dimensions)
    ├── SphereRendererTest.java                  (determinism + atmosphere color effect)
    └── SphereProjectionTest.java                (screen ↔ lat/lon round-trip)
```

The render package depends on `spacecolony.sim.BodyType` and on `spacecolony.world.SimplexNoise`. The latter creates `render → world` direction; that's still acyclic (sim has no inbound deps from render, world has no inbound deps from render except via SimplexNoise which is content-free). No reverse dependency is introduced.

---

## Tasks

### Task 0: Create branch (already done)

Branch `plan-2/renderer-adaptation` is already checked out off `main`.

---

### Task 1: BodyAppearance record + defaults

**Files:**
- Create: `src/main/java/spacecolony/render/BodyAppearance.java`
- Create: `src/main/java/spacecolony/render/BodyAppearances.java`

The per-body-type rendering config. Drives palette, ocean handling, and atmosphere color. Plan 1's `BodyType` enum has 5 values (ROCKY, GAS_GIANT, ICE_BODY, ASTEROID, MOON); each gets a default `BodyAppearance`.

- [ ] **Step 1: Write `BodyAppearance.java`**

```java
package spacecolony.render;

import java.awt.Color;

/**
 * Per-body-type rendering parameters. Drives palette selection, ocean handling, and
 * atmosphere color. Plan 2 maps each {@link spacecolony.sim.BodyType} to a default
 * {@code BodyAppearance} via {@link BodyAppearances#defaultFor}.
 *
 * @param atmosphereColor injected into SphereRenderer's glow/limb-darkening; pass null for airless
 * @param oceanPalette 4 colors from deep ocean to shore; null for bodies with no liquid surface
 * @param landPalette 11 colors keyed by elevation bucket (beach → snow); never null
 * @param computeRivers true to overlay rivers (rocky/Earth-like only)
 * @param latitudeBanded true for gas giants — pipeline ignores elevation and colors by latitude
 */
public record BodyAppearance(
    Color atmosphereColor,
    Color[] oceanPalette,
    Color[] landPalette,
    boolean computeRivers,
    boolean latitudeBanded
) {}
```

- [ ] **Step 2: Write `BodyAppearances.java`**

```java
package spacecolony.render;

import java.awt.Color;
import spacecolony.sim.BodyType;

/** Default appearance for each BodyType. */
public final class BodyAppearances {
    private BodyAppearances() {}

    // Earth-like palettes — preserved from planet-map's constants.
    private static final Color[] EARTH_OCEAN = {
        new Color(15, 35, 90),     // deep
        new Color(25, 55, 130),    // ocean
        new Color(45, 85, 155),    // shallow
        new Color(65, 110, 170)    // shore
    };
    private static final Color[] EARTH_LAND = {
        new Color(190, 175, 130),  // beach
        new Color(175, 150, 95),   // subtropical desert
        new Color(150, 140, 75),   // dry grassland
        new Color(140, 155, 65),   // grassland
        new Color(75, 115, 50),    // temperate forest
        new Color(50, 100, 40),    // tropical forest
        new Color(55, 80, 45),     // boreal forest
        new Color(160, 170, 150),  // tundra
        new Color(120, 110, 95),   // alpine rock
        new Color(145, 135, 120),  // alpine scree
        new Color(235, 242, 248)   // snow
    };

    // Mars-ish red rocky body.
    private static final Color[] MARS_LAND = {
        new Color(150, 95, 70),    // light dust
        new Color(165, 95, 60),    // ochre
        new Color(180, 100, 55),   // orange-red
        new Color(155, 85, 50),    // rust
        new Color(130, 70, 45),    // dark rust
        new Color(115, 60, 40),    // basalt-red
        new Color(95, 50, 35),     // shadow
        new Color(180, 175, 165),  // polar dust
        new Color(100, 70, 50),    // crater rim
        new Color(120, 85, 65),    // crater floor
        new Color(245, 240, 235)   // polar cap
    };

    private static final Color[] ASTEROID_LAND = {
        new Color(85, 80, 72),
        new Color(95, 88, 78),
        new Color(110, 100, 85),
        new Color(120, 108, 90),
        new Color(105, 95, 80),
        new Color(95, 85, 70),
        new Color(80, 72, 60),
        new Color(70, 62, 50),
        new Color(60, 55, 45),
        new Color(115, 105, 92),
        new Color(140, 130, 115)
    };

    private static final Color[] ICE_LAND = {
        new Color(155, 175, 195),  // shadow ice
        new Color(180, 200, 220),
        new Color(200, 220, 235),
        new Color(215, 232, 245),
        new Color(225, 240, 250),
        new Color(235, 245, 252),
        new Color(220, 230, 240),
        new Color(180, 200, 220),
        new Color(140, 165, 195),  // crack
        new Color(120, 145, 180),  // deep crack
        new Color(250, 252, 255)   // bright ice
    };

    // Gas giant: latitude-banded cloud palette.
    private static final Color[] JOVIAN_BANDS = {
        new Color(190, 150, 100),  // dark belt
        new Color(215, 180, 130),  // dusty belt
        new Color(230, 200, 150),  // pale zone
        new Color(245, 220, 180),  // bright zone
        new Color(250, 230, 200),
        new Color(245, 220, 180),
        new Color(230, 200, 150),
        new Color(215, 180, 130),
        new Color(195, 155, 105),
        new Color(180, 140, 95),
        new Color(165, 125, 85)
    };

    // Rocky moon (like our Moon / Io): same as asteroid but slightly lighter.
    private static final Color[] MOON_LAND = {
        new Color(95, 90, 82),
        new Color(110, 102, 92),
        new Color(125, 115, 100),
        new Color(135, 122, 105),
        new Color(120, 108, 90),
        new Color(108, 98, 82),
        new Color(95, 86, 72),
        new Color(82, 74, 62),
        new Color(72, 65, 54),
        new Color(130, 118, 100),
        new Color(160, 145, 125)
    };

    public static BodyAppearance defaultFor(BodyType type) {
        return switch (type) {
            case ROCKY     -> new BodyAppearance(new Color(100, 150, 255), EARTH_OCEAN, EARTH_LAND,    true,  false);
            case ASTEROID  -> new BodyAppearance(null,                     null,        ASTEROID_LAND, false, false);
            case ICE_BODY  -> new BodyAppearance(new Color(180, 220, 240), null,        ICE_LAND,      false, false);
            case GAS_GIANT -> new BodyAppearance(new Color(220, 200, 150), null,        JOVIAN_BANDS,  false, true);
            case MOON      -> new BodyAppearance(null,                     null,        MOON_LAND,     false, false);
        };
    }

    /** Mars-flavoured rocky variant. Plan 2 doesn't auto-pick this — callers opt in by body id. */
    public static BodyAppearance mars() {
        return new BodyAppearance(new Color(220, 160, 110), null, MARS_LAND, false, false);
    }
}
```

- [ ] **Step 3: Compile**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew compileJava 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/render/BodyAppearance.java \
        src/main/java/spacecolony/render/BodyAppearances.java
git commit -m "feat(render): add BodyAppearance config and per-body-type defaults"
```

---

### Task 2: Copy PlanetGenerator from planet-map, repackage

**Files:**
- Create: `src/main/java/spacecolony/render/PlanetGenerator.java`

This is a verbatim copy of `/Users/steve/projects/planet-map/src/main/java/planetmap/PlanetGenerator.java` with only the package changed. Subsequent tasks refactor it to accept a `BodyAppearance` parameter.

- [ ] **Step 1: Copy the file**

```bash
cp /Users/steve/projects/planet-map/src/main/java/planetmap/PlanetGenerator.java \
   /Users/steve/projects/space-colony/src/main/java/spacecolony/render/PlanetGenerator.java
```

- [ ] **Step 2: Change package declaration**

The copied file starts with `package planetmap;`. Change it to `package spacecolony.render;`.

It also imports `SimplexNoise` from the same package. Since SimplexNoise lives in `spacecolony.world` (imported during Plan 1), add the explicit import. Find the existing import line that references SimplexNoise (or if there's no import because the class was same-package, add one). After the change:

```java
package spacecolony.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import spacecolony.world.SimplexNoise;
```

(Adjust based on actual existing imports — preserve all imports already present, add the SimplexNoise one if missing.)

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

If compilation fails due to any private symbol that the copied file references, report BLOCKED with the missing symbol.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/render/PlanetGenerator.java
git commit -m "chore(render): import PlanetGenerator from planet-map"
```

---

### Task 3: PlanetGenerator — accept BodyType + BodyAppearance, swap palette

**Files:**
- Modify: `src/main/java/spacecolony/render/PlanetGenerator.java`

The copied class hardcodes Earth biome colors via named `Color` constants (DEEP_OCEAN, OCEAN, BEACH, TUNDRA, etc.) and a biome-selection helper that blends them by moisture + temperature. Plan 2 replaces the helper with a simpler elevation-bucket-based palette lookup. The visual loss (no moisture/temperature variation within a body) is acceptable for Plan 2; if real Earth-like biomes are needed later, the moisture/temperature grids are still computed and can drive a richer palette in a follow-up.

- [ ] **Step 1: Read the copied file end-to-end**

Open `/Users/steve/projects/space-colony/src/main/java/spacecolony/render/PlanetGenerator.java` and skim the whole file. Identify three regions:
   - Instance fields (top of class)
   - The per-pixel final color computation inside `generate(long seed)` (in the original around lines 323-353)
   - The biome-selection helper(s) — typically a private method named something like `getLandBiome(...)` returning a `Color`

Take note of:
   - Which named `Color` constants are referenced where
   - The exact name and signature of the biome-selection helper(s)
   - Whether the per-pixel loop directly references named constants for the ocean bands or only via a helper

- [ ] **Step 2: Add the BodyAppearance parameter + field**

Near the top of the class, add a field:

```java
private BodyAppearance currentAppearance = BodyAppearances.defaultFor(spacecolony.sim.BodyType.ROCKY);
```

Add a new public overload near the existing `generate(long seed)` method:

```java
public BufferedImage generate(long seed, BodyAppearance appearance) {
    this.currentAppearance = appearance;
    return generate(seed);
}
```

The default keeps `generate(seed)` backward-compatible (used by Earth-like calls and any prior tests that still expect the planet-map defaults).

- [ ] **Step 3: Replace the biome-selection helper with palette lookup**

Replace the existing biome-selection helper body (whatever it's called — keep its name and signature to minimise call-site churn) with this implementation:

```java
private java.awt.Color elevationToColor(double elevation, double moisture, double temperature) {
    java.awt.Color[] ocean = currentAppearance.oceanPalette();
    java.awt.Color[] land = currentAppearance.landPalette();

    // Gas giants: ignore elevation buckets, return latitude-banded color.
    // (Plan 2 simplification — uses the elevation value as a latitude proxy.)
    if (currentAppearance.latitudeBanded()) {
        double idx = elevation * (land.length - 1);
        int i = (int) Math.floor(Math.max(0, Math.min(land.length - 1, idx)));
        return land[i];
    }

    if (elevation < SEA_LEVEL && ocean != null) {
        // 4 ocean bands keyed on absolute elevation
        if (elevation < 0.20) return ocean[0];
        if (elevation < 0.35) return ocean[1];
        if (elevation < 0.45) return ocean[2];
        return ocean[3];
    }

    // Land bucket: linear mapping from elevation in [SEA_LEVEL or 0, 1] to palette index.
    double base = (ocean != null) ? SEA_LEVEL : 0.0;
    double t = (elevation - base) / Math.max(1e-9, 1.0 - base);
    int idx = (int) Math.floor(t * (land.length - 1));
    return land[Math.max(0, Math.min(land.length - 1, idx))];
}
```

If the original helper has a different name (e.g. `getLandBiome`), preserve that name so call sites continue to compile. If the original helper had different parameters (e.g. takes lat/lon instead of elevation/moisture/temperature), adapt the signature to match — the caller in the per-pixel loop already has elevation/moisture/temperature available.

If the per-pixel loop in `generate()` directly references the named `Color` constants for ocean bands (bypassing the helper), replace those direct references with calls to `elevationToColor(...)` so the whole color decision flows through one place.

You may now safely delete the unused biome-color named constants (DEEP_OCEAN, OCEAN, SHALLOW_WATER, SHORE_WATER, BEACH, etc.) since nothing references them. If anything still does, leave the constant in place rather than chasing the reference — the goal is for the rendered output to use the BodyAppearance palette, not to scrub every legacy line.

- [ ] **Step 4: Compile**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew compileJava 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

If compile fails because the original helper had a different return type (e.g. `int`) or a non-`Color` use site, adjust either the new helper's return type or its call sites — keep changes minimal. Report BLOCKED if the file structure is materially different from the description above.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/spacecolony/render/PlanetGenerator.java
git commit -m "feat(render): swap PlanetGenerator palette to BodyAppearance lookup"
```

---

### Task 4: Gate river overlay on `computeRivers`

**Files:**
- Modify: `src/main/java/spacecolony/render/PlanetGenerator.java`

The river accumulation + overlay step in the original `generate()` (around lines 229-316 of the planet-map source) writes blue river pixels on top of the land coloring. It's Earth-specific — asteroids, gas giants, and ice bodies shouldn't have surface rivers. Gate the entire pass on `currentAppearance.computeRivers()`.

(Gas-giant latitude banding is already handled in Task 3's `elevationToColor` via the `latitudeBanded` flag, so this task focuses only on the river gate.)

- [ ] **Step 1: Locate the river block**

```bash
grep -n "river\|flow\|RIVER" /Users/steve/projects/space-colony/src/main/java/spacecolony/render/PlanetGenerator.java | head -20
```

Identify the start and end of the river accumulation + overlay block. It's typically:
   - A flow-direction computation (per cell) that walks downhill
   - A flow-accumulation pass that counts upstream contributions
   - A pixel-overlay pass that paints cells above the accumulation threshold (≈30) blue

- [ ] **Step 2: Wrap the block in a feature flag**

Wrap the entire river block — from the flow-direction setup through the pixel-overlay loop — in a single `if (currentAppearance.computeRivers()) { ... }`. If the block consists of multiple disjoint sections, wrap them all in one check at the top:

```java
if (currentAppearance.computeRivers()) {
    // ... all river accumulation + overlay logic stays exactly as-is ...
}
```

If the river code uses local variables that are also referenced later (outside the block), keep their declarations outside the if-block and only conditionally initialise inside.

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/render/PlanetGenerator.java
git commit -m "feat(render): gate river overlay on BodyAppearance.computeRivers"
```

---

### Task 5: PlanetGenerator tests (determinism + body-type variance)

**Files:**
- Create: `src/test/java/spacecolony/render/PlanetGeneratorTest.java`

- [ ] **Step 1: Write the test**

```java
package spacecolony.render;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BodyType;
import static org.junit.jupiter.api.Assertions.*;

class PlanetGeneratorTest {

    @Test
    void sameSeed_producesIdenticalImage() {
        BufferedImage a = new PlanetGenerator(256, 128).generate(42L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage b = new PlanetGenerator(256, 128).generate(42L, BodyAppearances.defaultFor(BodyType.ROCKY));
        assertPixelEqual(a, b);
    }

    @Test
    void differentSeeds_produceDifferentImages() {
        BufferedImage a = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage b = new PlanetGenerator(128, 64).generate(2L, BodyAppearances.defaultFor(BodyType.ROCKY));
        assertFalse(pixelsEqual(a, b), "Different seeds should produce different images");
    }

    @Test
    void rockyBody_hasOceanColors() {
        BufferedImage img = new PlanetGenerator(256, 128).generate(7L, BodyAppearances.defaultFor(BodyType.ROCKY));
        // Earth ocean color (15,35,90) ARGB == 0xff0f2358; at least one pixel should be near this band.
        long oceanish = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                if (b > 100 && b > r && b > g) oceanish++;
            }
        }
        assertTrue(oceanish > 100, "Rocky body should have significant blue ocean pixels");
    }

    @Test
    void asteroid_hasNoOceanColors() {
        BufferedImage img = new PlanetGenerator(256, 128).generate(7L, BodyAppearances.defaultFor(BodyType.ASTEROID));
        long blueish = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
                if (b > 100 && b > r && b > g + 20) blueish++;
            }
        }
        assertEquals(0, blueish, "Asteroid should have no blue ocean pixels");
    }

    @Test
    void gasGiant_predominantlyTanPalette() {
        // Gas giant uses the JOVIAN_BANDS palette (warm tan/orange tones).
        // Verify average pixel is in the tan family (R > B, G > B).
        BufferedImage img = new PlanetGenerator(256, 128).generate(7L, BodyAppearances.defaultFor(BodyType.GAS_GIANT));
        long rSum = 0, gSum = 0, bSum = 0, count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                rSum += (rgb >> 16) & 0xff;
                gSum += (rgb >> 8) & 0xff;
                bSum += rgb & 0xff;
                count++;
            }
        }
        double rAvg = (double) rSum / count;
        double gAvg = (double) gSum / count;
        double bAvg = (double) bSum / count;
        assertTrue(rAvg > bAvg + 20, "Gas giant should be warm-toned (R - B > 20): R=" + rAvg + " B=" + bAvg);
        assertTrue(gAvg > bAvg + 10, "Gas giant should be warm-toned (G - B > 10): G=" + gAvg + " B=" + bAvg);
    }

    @Test
    void dimensions_matchConstructor() {
        BufferedImage img = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        assertEquals(128, img.getWidth());
        assertEquals(64, img.getHeight());
    }

    private static boolean pixelsEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    private static void assertPixelEqual(BufferedImage a, BufferedImage b) {
        assertTrue(pixelsEqual(a, b), "Images must be pixel-identical");
    }
}
```

- [ ] **Step 2: Run, expect failures or passes depending on prior tasks**

```bash
./gradlew test --tests PlanetGeneratorTest 2>&1 | tail -15
```

The tests exercise the API just built in Tasks 3-4. They should pass if those tasks were done correctly. If `gasGiant_isVisiblyBanded` fails, the latitudeBanded code path may not be wired; debug and adjust the threshold (1.2×) only if the math is right but the noise produces less variance than expected.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/spacecolony/render/PlanetGeneratorTest.java
git commit -m "test(render): PlanetGenerator determinism + body-type variance"
```

---

### Task 6: Copy SphereRenderer from planet-map, repackage

**Files:**
- Create: `src/main/java/spacecolony/render/SphereRenderer.java`

- [ ] **Step 1: Copy and repackage**

```bash
cp /Users/steve/projects/planet-map/src/main/java/planetmap/SphereRenderer.java \
   /Users/steve/projects/space-colony/src/main/java/spacecolony/render/SphereRenderer.java
```

Change the package declaration from `package planetmap;` to `package spacecolony.render;`. Preserve all other imports. SphereRenderer uses one static `SimplexNoise` instance for detail enhancement (line 95 in the original) — update that import to `spacecolony.world.SimplexNoise` if it's a same-package reference.

- [ ] **Step 2: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/spacecolony/render/SphereRenderer.java
git commit -m "chore(render): import SphereRenderer from planet-map"
```

---

### Task 7: SphereRenderer — atmosphere color parameter

**Files:**
- Modify: `src/main/java/spacecolony/render/SphereRenderer.java`

The current `SphereRenderer.render(...)` overloads have hardcoded atmosphere constants `ATMO_R=100, ATMO_G=150, ATMO_B=255`. Add an overload that accepts a `Color` (nullable, where null means "no atmosphere" for airless bodies — limb darkening still applies but the blue glow doesn't).

- [ ] **Step 1: Read the existing render method**

```bash
grep -n "ATMO_R\|public static BufferedImage render" /Users/steve/projects/space-colony/src/main/java/spacecolony/render/SphereRenderer.java
```

- [ ] **Step 2: Add the new overload**

Add this overload near the existing render overloads:

```java
/**
 * Render with explicit atmosphere color. Pass null for airless bodies (asteroids/moons);
 * the limb-darkening pass still runs but the colored glow is omitted.
 */
public static BufferedImage render(BufferedImage flatMap, int size, double rotationDeg,
                                   double tiltDeg, double zoom, long starSeed,
                                   Color atmosphereColor) {
    return renderInternal(flatMap, size, rotationDeg, tiltDeg, zoom, starSeed, atmosphereColor);
}
```

Rename the existing full-arg render(flatMap, size, rotDeg, tiltDeg, zoom, starSeed) implementation to `renderInternal(...)` and add `Color atmosphereColor` as the last parameter. Have the existing 6-arg overload call:

```java
public static BufferedImage render(BufferedImage flatMap, int size, double rotationDeg,
                                   double tiltDeg, double zoom, long starSeed) {
    return renderInternal(flatMap, size, rotationDeg, tiltDeg, zoom, starSeed,
                          new Color(100, 150, 255));  // default Earth-like atmosphere
}
```

Inside `renderInternal`, find the atmosphere/glow computation. Replace references to `ATMO_R/G/B` with `atmosphereColor.getRed()`, `.getGreen()`, `.getBlue()`. Guard the glow-add path with `if (atmosphereColor != null)`.

- [ ] **Step 3: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/render/SphereRenderer.java
git commit -m "feat(render): SphereRenderer atmosphere color is now a parameter"
```

---

### Task 8: SphereRenderer tests

**Files:**
- Create: `src/test/java/spacecolony/render/SphereRendererTest.java`

- [ ] **Step 1: Write the test**

```java
package spacecolony.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import spacecolony.sim.BodyType;
import static org.junit.jupiter.api.Assertions.*;

class SphereRendererTest {

    @Test
    void sameInputs_producesIdenticalSphere() {
        BufferedImage flat = new PlanetGenerator(256, 128).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage a = SphereRenderer.render(flat, 200, 30.0, 5.0, 1.0, 42L, new Color(100, 150, 255));
        BufferedImage b = SphereRenderer.render(flat, 200, 30.0, 5.0, 1.0, 42L, new Color(100, 150, 255));
        assertPixelEqual(a, b);
    }

    @Test
    void differentAtmosphere_producesDifferentSphere() {
        BufferedImage flat = new PlanetGenerator(256, 128).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage blue = SphereRenderer.render(flat, 200, 30.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        BufferedImage red  = SphereRenderer.render(flat, 200, 30.0, 0.0, 1.0, 42L, new Color(255, 100, 100));
        assertFalse(pixelsEqual(blue, red), "Different atmosphere colors should produce different spheres");
    }

    @Test
    void nullAtmosphere_doesNotThrow() {
        BufferedImage flat = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ASTEROID));
        BufferedImage img = SphereRenderer.render(flat, 200, 0.0, 0.0, 1.0, 42L, null);
        assertNotNull(img);
        assertEquals(200, img.getWidth());
        assertEquals(200, img.getHeight());
    }

    @Test
    void rotationChange_movesContent() {
        BufferedImage flat = new PlanetGenerator(256, 128).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage a = SphereRenderer.render(flat, 200, 0.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        BufferedImage b = SphereRenderer.render(flat, 200, 90.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        assertFalse(pixelsEqual(a, b), "90 degrees of rotation should change pixels");
    }

    @Test
    void outputDimensionsMatchSize() {
        BufferedImage flat = new PlanetGenerator(128, 64).generate(1L, BodyAppearances.defaultFor(BodyType.ROCKY));
        BufferedImage img = SphereRenderer.render(flat, 300, 0.0, 0.0, 1.0, 42L, new Color(100, 150, 255));
        assertEquals(300, img.getWidth());
        assertEquals(300, img.getHeight());
    }

    private static boolean pixelsEqual(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    private static void assertPixelEqual(BufferedImage a, BufferedImage b) {
        assertTrue(pixelsEqual(a, b), "Images must be pixel-identical");
    }
}
```

- [ ] **Step 2: Run, expect 5 passes**

```bash
./gradlew test --tests SphereRendererTest 2>&1 | tail -15
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/spacecolony/render/SphereRendererTest.java
git commit -m "test(render): SphereRenderer determinism + atmosphere effect"
```

---

### Task 9: SphereRenderer — screen-to-lat/lon inverse projection

**Files:**
- Modify: `src/main/java/spacecolony/render/SphereRenderer.java`

The renderer's forward pass maps (lat, lon) on the sphere to a screen pixel. For Plan 3's "click to place site" interaction, we need the inverse: given a click at (px, py) on the rendered sphere, return the corresponding (lat, lon) or null if the click missed the sphere.

The forward math at lines 77-85 of the original (per the exploration agent):
```java
double lat = Math.asin(clamp(-ny2, -1, 1));
double lon = Math.atan2(nx, nz2) + rotRad;
```

where `(nx, ny, nz2)` is the surface normal at the screen pixel after rotation and tilt. The inverse needs to:

1. Convert pixel (px, py) to centered coordinates and divide by radius → (nx, ny_screen).
2. Check `nx² + ny_screen² ≤ 1`; if outside, return null.
3. Compute nz = sqrt(1 − nx² − ny_screen²) (front-facing point on unit sphere).
4. Apply inverse tilt to get (nx, ny2, nz2) in the planet's coordinate frame.
5. Apply forward lat/lon math to (nx, ny2, nz2) to recover (lat, lon).

- [ ] **Step 1: Add the unproject method**

Place this as a public static method on SphereRenderer:

```java
/**
 * Convert a screen pixel on a rendered sphere back to (lat, lon).
 *
 * @param px screen x in [0, size)
 * @param py screen y in [0, size)
 * @param size the size argument that was passed to render()
 * @param rotationDeg the rotationDeg passed to render()
 * @param tiltDeg the tiltDeg passed to render() (clamped to [-80, 80] inside the renderer)
 * @param zoom the zoom passed to render()
 * @return {lat, lon} in radians, or null if the pixel is outside the sphere disc.
 *         lat in [-π/2, π/2]; lon in [-π, π].
 */
public static double[] unproject(int px, int py, int size, double rotationDeg, double tiltDeg, double zoom) {
    double radius = size * 0.45 * zoom;
    double cx = size * 0.5;
    double cy = size * 0.5;
    double nx = (px - cx) / radius;
    double nyScreen = (py - cy) / radius;
    double d2 = nx * nx + nyScreen * nyScreen;
    if (d2 > 1.0) return null; // outside the disc
    double nz = Math.sqrt(1.0 - d2); // facing camera
    // Undo tilt: planet frame (nx, ny2, nz2) is screen frame rotated about X by tilt.
    double tiltClamped = Math.max(-80.0, Math.min(80.0, tiltDeg));
    double tiltRad = Math.toRadians(tiltClamped);
    double cosT = Math.cos(tiltRad), sinT = Math.sin(tiltRad);
    // Screen-to-planet rotation about X-axis by +tilt (the renderer applies -tilt going planet→screen).
    double ny2 = nyScreen * cosT - nz * sinT;
    double nz2 = nyScreen * sinT + nz * cosT;
    double rotRad = Math.toRadians(rotationDeg);
    double lat = Math.asin(Math.max(-1.0, Math.min(1.0, -ny2)));
    double lon = Math.atan2(nx, nz2) + rotRad;
    // Normalize lon to (-π, π].
    lon = ((lon + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI;
    return new double[] { lat, lon };
}
```

Note on the tilt sign: the renderer's forward pass rotates the planet about X by `tiltRad` (positive tilt brings the north pole toward the camera). The inverse rotates by `-tiltRad`. The code above writes the inverse explicitly using sin/cos identities. Plan 2 tests verify round-trips empirically — if the tests show a consistent offset, flip the sign of `sinT` in the two formula lines.

- [ ] **Step 2: Compile**

```bash
./gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/spacecolony/render/SphereRenderer.java
git commit -m "feat(render): SphereRenderer.unproject for screen → lat/lon mapping"
```

---

### Task 10: Inverse projection tests

**Files:**
- Create: `src/test/java/spacecolony/render/SphereProjectionTest.java`

- [ ] **Step 1: Write the test**

```java
package spacecolony.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SphereProjectionTest {

    @Test
    void clickOutsideDisc_returnsNull() {
        // (0, 0) corner is well outside the centered disc.
        assertNull(SphereRenderer.unproject(0, 0, 200, 0.0, 0.0, 1.0));
    }

    @Test
    void clickAtCenter_returnsEquatorialOrigin() {
        // Center of the sphere at zero rotation/tilt — nx=0, ny=0, nz=1 → lat=0, lon=0.
        double[] r = SphereRenderer.unproject(100, 100, 200, 0.0, 0.0, 1.0);
        assertNotNull(r);
        assertEquals(0.0, r[0], 1e-9, "lat at center should be 0");
        assertEquals(0.0, r[1], 1e-9, "lon at center should be 0");
    }

    @Test
    void rotation_shiftsLongitude() {
        double[] zero = SphereRenderer.unproject(100, 100, 200, 0.0, 0.0, 1.0);
        double[] r90 = SphereRenderer.unproject(100, 100, 200, 90.0, 0.0, 1.0);
        assertEquals(0.0, zero[1], 1e-9);
        assertEquals(Math.toRadians(90.0), r90[1], 1e-9);
    }

    @Test
    void clickAboveCenter_isNorthernHemisphere() {
        // py below center (smaller y in screen coords) maps to positive latitude (north).
        double[] r = SphereRenderer.unproject(100, 60, 200, 0.0, 0.0, 1.0);
        assertNotNull(r);
        assertTrue(r[0] > 0, "Click above center should yield northern lat");
    }

    @Test
    void clickBelowCenter_isSouthernHemisphere() {
        double[] r = SphereRenderer.unproject(100, 140, 200, 0.0, 0.0, 1.0);
        assertNotNull(r);
        assertTrue(r[0] < 0, "Click below center should yield southern lat");
    }

    @Test
    void unproject_rangeIsBounded() {
        // 1000 evenly-spaced points inside the disc must all produce valid lat/lon.
        int size = 200;
        for (int i = 0; i < 1000; i++) {
            int px = (int) (size * (0.1 + 0.8 * (i % 32) / 32.0));
            int py = (int) (size * (0.1 + 0.8 * ((i / 32) % 32) / 32.0));
            double[] r = SphereRenderer.unproject(px, py, size, 0.0, 0.0, 1.0);
            if (r == null) continue;
            assertTrue(r[0] >= -Math.PI / 2 - 1e-9 && r[0] <= Math.PI / 2 + 1e-9, "lat out of range: " + r[0]);
            assertTrue(r[1] >= -Math.PI - 1e-9 && r[1] <= Math.PI + 1e-9, "lon out of range: " + r[1]);
        }
    }
}
```

- [ ] **Step 2: Run, expect 6 passes**

```bash
./gradlew test --tests SphereProjectionTest 2>&1 | tail -15
```

If `clickAtCenter_returnsEquatorialOrigin` fails because the renderer's pixel-center convention is `(size/2, size/2)` vs `((size-1)/2, (size-1)/2)`, adjust the expected click coordinates by 0 or 1 pixel. The forward render loop's convention must match.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/spacecolony/render/SphereProjectionTest.java
git commit -m "test(render): screen-to-lat/lon inverse projection round-trips"
```

---

### Task 11: RenderDemo — write one PNG per body type

**Files:**
- Create: `src/main/java/spacecolony/render/RenderDemo.java`

A small CLI that exercises the renderer end-to-end and writes the results to disk for human verification. Not a test — a debugging / showcase utility.

- [ ] **Step 1: Write RenderDemo**

```java
package spacecolony.render;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import spacecolony.sim.BodyType;

/**
 * Generates one flat-map PNG plus one rendered-sphere PNG per BodyType and writes them to
 * a directory. Useful for visually inspecting the renderer output without launching the UI.
 *
 * Usage: ./gradlew render-demo --args="--seed 42 --out /tmp/space-colony-render"
 */
public class RenderDemo {
    public static void main(String[] args) throws IOException {
        long seed = 42L;
        String out = "/tmp/space-colony-render";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[i + 1]);
            if (args[i].equals("--out") && i + 1 < args.length) out = args[i + 1];
        }
        File outDir = new File(out);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Could not create output dir: " + outDir);
        }
        PlanetGenerator gen = new PlanetGenerator(1024, 512);
        for (BodyType type : BodyType.values()) {
            BodyAppearance app = BodyAppearances.defaultFor(type);
            BufferedImage flat = gen.generate(seed, app);
            File flatFile = new File(outDir, type.name().toLowerCase() + "-flat.png");
            ImageIO.write(flat, "png", flatFile);
            BufferedImage sphere = SphereRenderer.render(flat, 512, 30.0, 15.0, 1.0, seed, app.atmosphereColor());
            File sphereFile = new File(outDir, type.name().toLowerCase() + "-sphere.png");
            ImageIO.write(sphere, "png", sphereFile);
            System.out.printf("%s: %s, %s%n", type.name(), flatFile.getPath(), sphereFile.getPath());
        }
    }
}
```

- [ ] **Step 2: Add a Gradle task for it**

Edit `build.gradle.kts` and add a task before the existing `tasks.named<JavaExec>("run")` block:

```kotlin
tasks.register<JavaExec>("render-demo") {
    group = "application"
    description = "Render one flat-map + sphere PNG per BodyType for visual inspection."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "spacecolony.render.RenderDemo"
}
```

- [ ] **Step 3: Run the demo**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew render-demo --args="--seed 42 --out /tmp/space-colony-render" 2>&1 | tail -10
```

Expected: 10 PNG files in `/tmp/space-colony-render/` (5 flat-maps + 5 spheres). Open them in an image viewer to confirm each body type looks distinct.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/spacecolony/render/RenderDemo.java build.gradle.kts
git commit -m "feat(render): RenderDemo writes one PNG per BodyType for inspection"
```

---

### Task 12: Final verification

- [ ] **Step 1: Run the full test suite**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
./gradlew test 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL with all tests passing. Test count = 52 (Plan 1) + ~17 new (PlanetGeneratorTest 6 + SphereRendererTest 5 + SphereProjectionTest 6) = ~69 tests.

- [ ] **Step 2: Re-run RenderDemo to confirm deterministic output**

```bash
./gradlew render-demo --args="--seed 42 --out /tmp/space-colony-render-2" 2>&1 | tail -2
shasum -a 256 /tmp/space-colony-render/*.png /tmp/space-colony-render-2/*.png | sort -k2 | uniq -f1 -c | sort -rn | head -10
```

Each PNG should have a duplicate count of 2 in the shasum output (one from each run).

- [ ] **Step 3: Verify no UI-thread leakage**

```bash
grep -r "import javax.swing\|EventQueue\|SwingUtilities" src/main/java/spacecolony/render/
```

Expected: empty (renderer is headless; no Swing).

---

## Verification

After all tasks complete, the deliverables are:

1. **Tests:** `./gradlew test` shows BUILD SUCCESSFUL with ~69 total tests.
2. **Demo:** `./gradlew render-demo --args="--seed 1 --out /tmp/sc-render"` writes 10 PNGs. Two runs with the same seed produce byte-identical PNGs.
3. **Determinism:** PlanetGenerator + SphereRenderer + unproject are all pure functions of their inputs. No Math.random.
4. **Inverse round-trip:** Clicking the center of any rendered sphere yields (lat=0, lon=0) at rotation=0,tilt=0.
5. **Headless contract:** `grep` for Swing imports in `render/` returns empty.

## What's Next

Plan 3 will wire `Simulator` (Plan 1) and the renderer (Plan 2) into a Swing UI shell with the system map, dock panels, and click-to-place-site flow. The unproject method from Task 9 is the bridge between mouse events and Site placement.

## Spec Coverage

This plan implements the following spec sections:
- §13 "World-gen — adapted from planet-map" — Tasks 2, 3, 4, 6, 7 (copy + parameterize PlanetGenerator and SphereRenderer)
- §7.2 "BodyViewPanel ... Click marker to select" — Task 9 (inverse projection is the prerequisite)
- §11.3 "World-gen tests" — Task 5 (deterministic PlanetGenerator output by seed)

Deferred to later plans:
- The actual `BodyViewPanel` + `SphereMiniRenderer` Swing UI — Plan 3.
- Lat/lon click → Site placement command — Plan 3.
- Resource-yield overlay on the sphere render (debug mode) — Plan 4.

## Self-Review Notes

Inline fixes applied during the post-write self-review:
- Originally Task 3 asked the engineer to refactor the planet-map biome-selection logic (moisture + temperature + elevation) into an index lookup; that was vague and risky for a fresh subagent. Tightened to "replace the helper with a concrete elevation-bucket lookup" with the full helper body provided. Visual fidelity loss (no moisture/temp variation) is acceptable for Plan 2; the input grids still exist and can drive a richer palette later.
- Task 4 originally also handled gas-giant banding; that was moved into Task 3's `elevationToColor` (latitudeBanded branch) since both touch the same helper. Task 4 now does only the river gate.
- Gas-giant rendering uses `elevation` as a band-index proxy (not true latitude). This means gas giants in Plan 2 look like a noisy warm-toned blob rather than a real banded gas giant. The Task 5 test verifies the predominant-tan-palette property instead of banded variance, since true banding requires extracting `lat` per pixel — out of scope for Plan 2 v1.
- `BodyAppearance.landPalette` requires 11 entries (to match the original planet-map bucket count). If a body type wants fewer distinct tones, repeat colors in the palette.
- `BodyAppearances.mars()` exists but isn't auto-applied. Plan 3 can call it explicitly for the Mars body; without that wire-up, Mars renders with the EARTH_LAND palette (still fine for Plan 2 — distinguishable from gas giants and asteroids).
- The inverse-projection sign convention in Task 9 may need a flip if tests show consistent latitude offsets — documented inline in the task.
- `RenderDemo` deliberately doesn't add a JUnit test for the file-writing path (would couple tests to I/O); the rendering itself is exercised by PlanetGenerator/SphereRenderer tests.
