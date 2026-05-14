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
