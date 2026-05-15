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
