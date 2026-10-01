package spacecolony.render;

import java.awt.Color;
import spacecolony.sim.Body;
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

    // Mars: rust and butterscotch dust over dark basalt, low to high; the caps come from latitude.
    private static final Color[] MARS_LAND = {
        new Color(178, 82, 44),    // lowland dust
        new Color(186, 88, 46),    // rust plains
        new Color(170, 76, 42),    // rust
        new Color(140, 62, 38),    // dark basalt
        new Color(124, 56, 36),    // basalt
        new Color(160, 72, 40),    // dark rust
        new Color(196, 100, 52),   // orange-red
        new Color(210, 118, 62),   // butterscotch
        new Color(200, 108, 58),   // highland ochre
        new Color(182, 94, 54),    // volcano flank
        new Color(166, 86, 54)     // summit
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

    // Gas giant (Jupiter): dark red-brown belts up to cream-white zones.
    private static final Color[] JOVIAN_BANDS = {
        new Color(122, 76, 52),    // dark belt
        new Color(148, 94, 62),
        new Color(172, 116, 78),   // belt
        new Color(194, 142, 98),
        new Color(210, 166, 120),  // tan
        new Color(222, 188, 145),
        new Color(232, 206, 166),  // pale zone
        new Color(240, 220, 186),
        new Color(245, 232, 205),  // bright zone
        new Color(246, 238, 218),
        new Color(242, 238, 228)   // white zone
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

    // Venus: sulphur-yellow cloud deck, seen as soft latitude bands.
    private static final Color[] VENUS_CLOUDS = {
        new Color(222, 205, 150),
        new Color(232, 218, 168),
        new Color(240, 228, 182),
        new Color(246, 236, 196),
        new Color(250, 242, 208),
        new Color(246, 236, 196),
        new Color(238, 226, 178),
        new Color(230, 214, 162),
        new Color(222, 204, 148),
        new Color(214, 194, 138),
        new Color(206, 186, 130)
    };

    /**
     * Appearance for a specific body: the type default, except the named rocky planets, which
     * would otherwise all render as Earth.
     */
    public static BodyAppearance forBody(Body body) {
        return switch (body.id) {
            case "mars"    -> mars();
            case "venus"   -> new BodyAppearance(new Color(250, 235, 190), null, VENUS_CLOUDS, false, true);
            case "mercury" -> new BodyAppearance(null, null, MOON_LAND, false, false);
            default        -> defaultFor(body.type);
        };
    }

    public static BodyAppearance defaultFor(BodyType type) {
        return switch (type) {
            case ROCKY     -> new BodyAppearance(new Color(100, 150, 255), EARTH_OCEAN, EARTH_LAND,    true,  false);
            case ASTEROID  -> new BodyAppearance(null,                     null,        ASTEROID_LAND, false, false);
            case ICE_BODY  -> new BodyAppearance(new Color(180, 220, 240), null,        ICE_LAND,      false, false);
            case GAS_GIANT -> new BodyAppearance(new Color(220, 200, 150), null,        JOVIAN_BANDS,  false, true, null, true);
            case MOON      -> new BodyAppearance(null,                     null,        MOON_LAND,     false, false);
        };
    }

    /** Mars-flavoured rocky variant, picked by {@link #forBody} for the body with id "mars". */
    public static BodyAppearance mars() {
        return new BodyAppearance(new Color(230, 150, 110), null, MARS_LAND, false, false, new Color(240, 236, 230), false);
    }
}
