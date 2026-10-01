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
 * @param latitudeBanded true for gas giants — pipeline ignores elevation and paints cloud belts
 *                       and zones, darkest palette entry = belt, brightest = zone
 * @param polarCap ice color blended in near the poles; null for none
 * @param storms true to paint a great red spot and a few white ovals on a banded body
 */
public record BodyAppearance(
    Color atmosphereColor,
    Color[] oceanPalette,
    Color[] landPalette,
    boolean computeRivers,
    boolean latitudeBanded,
    Color polarCap,
    boolean storms
) {
    public BodyAppearance(Color atmosphereColor, Color[] oceanPalette, Color[] landPalette,
                          boolean computeRivers, boolean latitudeBanded) {
        this(atmosphereColor, oceanPalette, landPalette, computeRivers, latitudeBanded, null, false);
    }
}
