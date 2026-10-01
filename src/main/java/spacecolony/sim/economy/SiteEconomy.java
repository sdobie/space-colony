package spacecolony.sim.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import spacecolony.sim.Body;
import spacecolony.sim.BodyType;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingType;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceYieldSampler;
import spacecolony.sim.Site;
import spacecolony.sim.TechEffects;
import spacecolony.sim.World;

/**
 * One colony's day of power, eating and building production, as an itemised report.
 * The arithmetic and its order are exactly Plan 6's ProductionPhase (ProductionParityTest
 * pins them). Mutates only {@code stock}: the production phase passes the site's real
 * stockpile, previews pass a copy.
 */
public final class SiteEconomy {
    public static final double POP_FOOD_PER_DAY = 0.01;     // per person
    public static final double POP_WATER_PER_DAY = 0.005;
    /** Ground yields under these are "none" and "poor" in the UI. */
    static final double NO_YIELD = 0.01;
    static final double LOW_YIELD = 0.25;

    private SiteEconomy() {}

    public static DayReport run(World w, Body b, Site s, List<Building> buildings, Map<Resource, Double> stock) {
        DayReport report = new DayReport();

        // 1. Power balance.
        double powerProduced = 0;
        double powerDemand = 0;
        for (int i = 0; i < buildings.size(); i++) {
            Building bd = buildings.get(i);
            if (!bd.enabled) continue;
            if (bd.level == 0) continue; // still under construction: draws nothing
            FlowSource src = new FlowSource.Building(i, bd.type, bd.level);
            if (bd.type == BuildingType.POWER_PLANT) {
                // Solar output scales with 1/r^2 (r = distance from sun, in AU).
                double r = OrbitalGeometry.sunDistance(w, b);
                double output = BuildingCatalog.POWER_PLANT_OUTPUT * bd.level / Math.max(0.05, r * r)
                              * TechEffects.powerPlantMultiplier(w.tech);
                powerProduced += output;
                report.add(new FlowLine(src, Resource.ENERGY, output, output));
            } else {
                double draw = BuildingCatalog.POWER_DRAW * bd.level;
                powerDemand += draw;
                report.add(new FlowLine(src, Resource.ENERGY, -draw, -draw));
            }
        }
        double powerFactor = powerDemand <= 0 ? 1.0 : Math.min(1.0, powerProduced / powerDemand);
        report.powerMade = powerProduced;
        report.powerUsed = powerDemand;
        report.powerFactor = powerFactor;

        // 2. Population eats (always paid first).
        FlowSource people = new FlowSource.Population(s.population);
        double foodNeed = s.population * POP_FOOD_PER_DAY;
        double waterNeed = s.population * POP_WATER_PER_DAY;
        consume(stock, report, people, Resource.FOOD, foodNeed, foodNeed);
        consume(stock, report, people, Resource.WATER, waterNeed, waterNeed);

        // 3. Production by building type, in list order (earlier buildings get inputs first).
        ResourceYieldSampler yields = b.resourceYields;
        // Farms' replanting biomass lands at the end of the day, so it can't feed another farm today.
        List<FlowLine> harvests = new ArrayList<>();
        for (int i = 0; i < buildings.size(); i++) {
            Building bd = buildings.get(i);
            FlowSource src = new FlowSource.Building(i, bd.type, bd.level);
            if (!bd.enabled) {
                report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, false, 0.0, Limit.DISABLED, null));
                continue;
            }
            if (bd.level == 0) {
                report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, true, 0.0, Limit.CONSTRUCTING, null));
                continue;
            }
            switch (bd.type) {
                case MINE -> {
                    if (yields == null) {
                        report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, true, 0.0, Limit.NO_YIELD, Resource.ORE));
                        break;
                    }
                    double y = yields.sample(Resource.ORE, s.lat, s.lon);
                    double oreMult = TechEffects.mineOreMultiplier(w.tech);
                    double produced = bd.level * BuildingCatalog.MINE_ORE * y * powerFactor
                                    * oreMult;
                    produce(stock, report, src, Resource.ORE, produced,
                        bd.level * BuildingCatalog.MINE_ORE * y * oreMult);
                    // Mines also yield silicate, scaled.
                    double sy = yields.sample(Resource.SILICATE, s.lat, s.lon);
                    double siMult = TechEffects.mineSilicateMultiplier(w.tech);
                    double si = bd.level * BuildingCatalog.MINE_SILICATE * sy * powerFactor
                              * siMult;
                    produce(stock, report, src, Resource.SILICATE, si,
                        bd.level * BuildingCatalog.MINE_SILICATE * sy * siMult);
                    // Ice, where the ground holds it (spec: mines yield ORE / SILICATE / ICE by body).
                    double iy = yields.sample(Resource.ICE, s.lat, s.lon);
                    double ice = bd.level * BuildingCatalog.MINE_ICE * iy * powerFactor;
                    if (iy > 0) produce(stock, report, src, Resource.ICE, ice, bd.level * BuildingCatalog.MINE_ICE * iy);
                    // Atmospheric mining: gas-giant MINE buildings extract FUEL when the tech is researched.
                    boolean gasGiant = b.type == BodyType.GAS_GIANT;
                    boolean fuelTech = TechEffects.gasGiantFuelEnabled(w.tech);
                    double fy = gasGiant ? yields.sample(Resource.FUEL, s.lat, s.lon) : 0.0;
                    if (gasGiant && fuelTech) {
                        double fuel = bd.level * BuildingCatalog.MINE_GAS_FUEL * fy * powerFactor;
                        produce(stock, report, src, Resource.FUEL, fuel, bd.level * BuildingCatalog.MINE_GAS_FUEL * fy);
                    }
                    // A fuel-mining gas-giant mine is judged by its fuel yield, an ice mine by its ice,
                    // any other by its ore.
                    boolean fuelMine = gasGiant && fuelTech;
                    boolean iceMine = !fuelMine && iy > y;
                    double mainYield = fuelMine ? fy : iceMine ? iy : y;
                    Resource mainRes = fuelMine ? Resource.FUEL : iceMine ? Resource.ICE : Resource.ORE;
                    Limit limit = null;
                    Resource limitRes = null;
                    if (powerFactor < 1.0) limit = Limit.BROWNOUT;
                    else if (gasGiant && !fuelTech && fy >= NO_YIELD) { limit = Limit.NEEDS_TECH; limitRes = Resource.FUEL; }
                    else if (mainYield < NO_YIELD) { limit = Limit.NO_YIELD; limitRes = mainRes; }
                    else if (mainYield < LOW_YIELD) { limit = Limit.LOW_YIELD; limitRes = mainRes; }
                    report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, true, efficiency(report, i), limit, limitRes));
                }
                case FARM -> {
                    // Consume biomass + water; produce food.
                    double waterDemandFactor = TechEffects.farmWaterDemandMultiplier(w.tech);
                    double biomassAsked = bd.level * BuildingCatalog.FARM_BIOMASS * powerFactor;
                    double biomassConsumed = consume(stock, report, src, Resource.BIOMASS, biomassAsked,
                        bd.level * BuildingCatalog.FARM_BIOMASS);
                    consume(stock, report, src, Resource.WATER, bd.level * BuildingCatalog.FARM_WATER * powerFactor * waterDemandFactor,
                        bd.level * BuildingCatalog.FARM_WATER * waterDemandFactor);
                    double foodMult = TechEffects.farmFoodMultiplier(w.tech);
                    double foodProduced = bd.level * BuildingCatalog.FARM_FOOD * powerFactor *
                                          Math.min(1.0, biomassConsumed / Math.max(1e-6, bd.level * BuildingCatalog.FARM_BIOMASS))
                                        * foodMult;
                    produce(stock, report, src, Resource.FOOD, foodProduced, bd.level * BuildingCatalog.FARM_FOOD * foodMult);
                    // Replanting: seed stock back from the harvest, plus what living soil grows. Scales
                    // with the biomass actually planted, so an idle farm regrows nothing.
                    double soil = yields == null ? 0.0 : yields.sample(Resource.BIOMASS, s.lat, s.lon);
                    double regrowPerLevel = BuildingCatalog.FARM_BIOMASS_RESEED + BuildingCatalog.FARM_SOIL_BIOMASS * soil;
                    harvests.add(new FlowLine(src, Resource.BIOMASS,
                        bd.level * regrowPerLevel * biomassConsumed / Math.max(1e-6, bd.level * BuildingCatalog.FARM_BIOMASS),
                        bd.level * regrowPerLevel));
                    Limit limit = inputLimit(biomassAsked, biomassConsumed);
                    Resource limitRes = limit != null ? Resource.BIOMASS : null;
                    if (limit == null && powerFactor < 1.0) limit = Limit.BROWNOUT;
                    report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, true, efficiency(report, i), limit, limitRes));
                }
                case REFINERY -> {
                    double mult = TechEffects.refineryMultiplier(w.tech);
                    double oreAsked = bd.level * BuildingCatalog.REFINERY_ORE * powerFactor;
                    double oreUsed = consume(stock, report, src, Resource.ORE, oreAsked,
                        bd.level * BuildingCatalog.REFINERY_ORE);
                    produce(stock, report, src, Resource.METAL, oreUsed * BuildingCatalog.REFINERY_METAL_PER_ORE * mult,
                        bd.level * BuildingCatalog.REFINERY_ORE * BuildingCatalog.REFINERY_METAL_PER_ORE * mult);
                    double iceAsked = bd.level * BuildingCatalog.REFINERY_ICE * powerFactor;
                    double iceUsed = consume(stock, report, src, Resource.ICE, iceAsked,
                        bd.level * BuildingCatalog.REFINERY_ICE);
                    produce(stock, report, src, Resource.WATER, iceUsed * BuildingCatalog.REFINERY_WATER_PER_ICE * mult,
                        bd.level * BuildingCatalog.REFINERY_ICE * BuildingCatalog.REFINERY_WATER_PER_ICE * mult);
                    // Two independent chains: name the input that got the smallest share.
                    double oreShare = oreAsked <= 0 ? 1.0 : oreUsed / oreAsked;
                    double iceShare = iceAsked <= 0 ? 1.0 : iceUsed / iceAsked;
                    Limit limit;
                    Resource limitRes;
                    if (oreUsed <= 1e-9 && iceUsed <= 1e-9 && (oreAsked > 0 || iceAsked > 0)) {
                        limit = Limit.NO_INPUT;
                        limitRes = oreShare <= iceShare ? Resource.ORE : Resource.ICE;
                    } else {
                        Resource worst = oreShare <= iceShare ? Resource.ORE : Resource.ICE;
                        limit = Math.min(oreShare, iceShare) < 0.99 ? Limit.SHORT_INPUT : null;
                        limitRes = limit != null ? worst : null;
                    }
                    if (limit == null && powerFactor < 1.0) limit = Limit.BROWNOUT;
                    report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, true, efficiency(report, i), limit, limitRes));
                }
                case FACTORY -> {
                    double metalAsked = bd.level * BuildingCatalog.FACTORY_METAL * powerFactor;
                    double siAsked = bd.level * BuildingCatalog.FACTORY_SILICATE * powerFactor;
                    // Take only what the scarcer input can match, so neither is wasted, and scale
                    // output by that share: a brownout cuts it once, not twice.
                    double metalShare = share(stock, Resource.METAL, metalAsked);
                    double siShare = share(stock, Resource.SILICATE, siAsked);
                    double share = Math.min(metalShare, siShare);
                    consume(stock, report, src, Resource.METAL, metalAsked * share,
                        bd.level * BuildingCatalog.FACTORY_METAL);
                    consume(stock, report, src, Resource.SILICATE, siAsked * share,
                        bd.level * BuildingCatalog.FACTORY_SILICATE);
                    produce(stock, report, src, Resource.COMPONENTS,
                        bd.level * BuildingCatalog.FACTORY_COMPONENTS * powerFactor * share,
                        bd.level * BuildingCatalog.FACTORY_COMPONENTS);
                    Resource scarce = metalShare <= siShare ? Resource.METAL : Resource.SILICATE;
                    Limit limit = share <= 1e-9 ? Limit.NO_INPUT : share < 0.99 ? Limit.SHORT_INPUT : null;
                    Resource limitRes = limit != null ? scarce : null;
                    if (limit == null && powerFactor < 1.0) limit = Limit.BROWNOUT;
                    report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, true, efficiency(report, i), limit, limitRes));
                }
                case POWER_PLANT, HABITAT, SHIPYARD, RESEARCH_LAB ->
                    // Their effects (power, cap, ships, research) aren't slowed by brownouts.
                    report.addOutcome(new BuildingOutcome(i, bd.type, bd.level, true, 1.0, null, null));
            }
        }
        for (FlowLine h : harvests) produce(stock, report, h.source(), h.resource(), h.amount(), h.wanted());
        return report;
    }

    /** Fraction of {@code asked} the stock can cover, in [0, 1]; 1 when nothing is asked. */
    private static double share(Map<Resource, Double> stock, Resource r, double asked) {
        if (asked <= 1e-12) return 1.0;
        return Math.max(0.0, Math.min(1.0, stock.getOrDefault(r, 0.0) / asked));
    }

    private static Limit inputLimit(double asked, double got) {
        if (asked <= 1e-12) return null;
        if (got <= 1e-9) return Limit.NO_INPUT;
        if (got < 0.99 * asked) return Limit.SHORT_INPUT;
        return null;
    }

    /** Actual over wanted, summed over the building's non-energy outputs; 1 when it wanted none. */
    private static double efficiency(DayReport report, int index) {
        double got = 0, wanted = 0;
        for (FlowLine l : report.linesOf(index)) {
            if (l.resource() == Resource.ENERGY || l.wanted() <= 0) continue;
            got += l.amount();
            wanted += l.wanted();
        }
        return wanted <= 1e-12 ? 1.0 : Math.max(0.0, Math.min(1.0, got / wanted));
    }

    private static double consume(Map<Resource, Double> stock, DayReport report, FlowSource src,
                                  Resource r, double amount, double wanted) {
        double have = stock.getOrDefault(r, 0.0);
        double taken = Math.min(have, amount);
        stock.put(r, have - taken);
        report.add(new FlowLine(src, r, -taken, -wanted));
        return taken;
    }

    private static void produce(Map<Resource, Double> stock, DayReport report, FlowSource src,
                                Resource r, double amount, double wanted) {
        stock.merge(r, amount, Double::sum);
        report.add(new FlowLine(src, r, amount, wanted));
    }
}
