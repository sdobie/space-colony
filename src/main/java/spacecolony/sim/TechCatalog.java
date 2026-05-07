package spacecolony.sim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Static catalog of v1 techs. Plan 4 will tie effects to gameplay multipliers. */
public final class TechCatalog {
    private static final Map<String, Tech> BY_ID = new LinkedHashMap<>();
    static {
        add(new Tech("basic-mining",   "Basic Mining",       "Improves ore output by 10%.",       100,  List.of()));
        add(new Tech("basic-farming",  "Basic Farming",      "Improves food output by 10%.",      100,  List.of()));
        add(new Tech("solar-panels",   "Solar Panels",       "+25% output from POWER_PLANT.",     150,  List.of()));
        add(new Tech("ion-drives",     "Ion Drives",         "Reduces fuel cost by 20%.",         300,  List.of()));
        add(new Tech("fusion-drives",  "Fusion Drives",      "Reduces fuel cost by another 30%.", 800,  List.of("ion-drives")));
        add(new Tech("atm-mining",     "Atmospheric Mining", "Enables FUEL extraction at gas giants.", 500, List.of()));
        add(new Tech("hydroponics",    "Hydroponics",        "+30% farm output, less water use.", 250,  List.of("basic-farming")));
        add(new Tech("smelting",       "Smelting",           "+20% refinery output.",             200,  List.of()));
        add(new Tech("medicine",       "Medicine",           "Reduces disease severity by 50%.",  300,  List.of()));
        add(new Tech("colony-mgmt-i",  "Colony Mgmt I",      "+20% population cap.",              300,  List.of()));
        add(new Tech("research-i",     "Research Methods I", "+25% RESEARCH_LAB output.",         200,  List.of()));
        add(new Tech("research-ii",    "Research Methods II","+25% more RESEARCH_LAB output.",    600,  List.of("research-i")));
        add(new Tech("auto-mining",    "Automated Mining",   "+30% mine output.",                 500,  List.of("basic-mining")));
        add(new Tech("colony-mgmt-ii", "Colony Mgmt II",     "+30% population cap.",              700,  List.of("colony-mgmt-i")));
        add(new Tech("life-support-i", "Life Support I",     "+20% morale cap.",                  300,  List.of()));
        add(new Tech("life-support-ii","Life Support II",    "+30% morale cap (stacks).",         700,  List.of("life-support-i")));
        add(new Tech("antimatter",     "Antimatter Drives",  "Halves fuel cost again.",          2000,  List.of("fusion-drives")));
    }
    private static void add(Tech t) { BY_ID.put(t.id(), t); }
    public static Tech get(String id) { return BY_ID.get(id); }
    public static java.util.Collection<Tech> all() { return BY_ID.values(); }
    private TechCatalog() {}
}
