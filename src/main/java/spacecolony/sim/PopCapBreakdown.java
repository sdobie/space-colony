package spacecolony.sim;

/** populationCap = round((siteBase + Σ enabled HABITAT level × BuildingCatalog.HABITAT_CAP) × popCapMultiplier). */
public record PopCapBreakdown(int siteBase, int habitatBoost, double techMultiplier, int cap) {
    public static PopCapBreakdown of(Site s, TechState t) {
        int boost = 0;
        for (Building b : s.buildings) {
            if (b.enabled && b.type == BuildingType.HABITAT) boost += b.level * BuildingCatalog.HABITAT_CAP;
        }
        double mult = TechEffects.popCapMultiplier(t);
        return new PopCapBreakdown(s.siteBase, boost, mult, (int) Math.round((s.siteBase + boost) * mult));
    }
}
