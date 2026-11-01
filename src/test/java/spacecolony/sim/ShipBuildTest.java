package spacecolony.sim;

import java.util.Map;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.BuildShipCommand;
import spacecolony.sim.commands.DemolishBuildingCommand;
import spacecolony.sim.commands.DispatchShipCommand;
import spacecolony.sim.commands.RetireShipCommand;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

/** Plan 10 §3: ships cost METAL and COMPONENTS and take days at a shipyard. */
class ShipBuildTest {
    private static final String HUB = "site-earth-hub";

    private World w;
    private final Simulator sim = new Simulator();

    private World world() {
        w = WorldGenerator.generate(1L);
        return w;
    }

    private void tick(Object... commands) {
        for (Object c : commands) sim.enqueue((spacecolony.sim.commands.Command) c);
        sim.advance(w);
    }

    private Site hub() { return w.findSite(HUB); }

    private Building yard() {
        return hub().buildings.stream().filter(b -> b.type == BuildingType.SHIPYARD).findFirst().orElseThrow();
    }

    private boolean rejected(String text) {
        return w.recentEvents.stream().anyMatch(e -> e.kind() == EventKind.COMMAND_REJECTED && e.message().contains(text));
    }

    private long completions() {
        return w.recentEvents.stream().filter(e -> e.kind() == EventKind.SHIP_COMPLETED).count();
    }

    @Test void costsAndDays() {
        assertEquals(BuildCost.of(15, 5, 10), ShipClass.EXPLORER.cost());
        assertEquals(BuildCost.of(30, 10, 20), ShipClass.HAULER.cost());
        assertEquals(BuildCost.of(35, 10, 25), ShipClass.TANKER.cost());
        assertEquals(BuildCost.of(60, 20, 40), ShipClass.COLONIZER.cost());
        assertEquals(13, ShipYards.days(ShipClass.TANKER, 2));
        assertEquals(10, ShipYards.days(ShipClass.COLONIZER, 4));
    }

    @Test void order_paysAndStartsABuild_thatFinishesOnDayTwenty() {
        world();
        double metal = hub().stockpile.get(Resource.METAL);
        double comps = hub().stockpile.get(Resource.COMPONENTS);
        tick(new BuildShipCommand("h1", "Mule", ShipClass.HAULER, HUB));
        Ship h = w.findShip("h1");
        assertEquals(ShipState.BUILDING, h.state);
        assertEquals(yard().id, h.yardBuildingId);
        assertEquals(metal - 30, hub().stockpile.get(Resource.METAL), 1e-9 + 2.0); // production runs this tick too
        assertEquals(comps - 10, hub().stockpile.get(Resource.COMPONENTS), 1e-9);
        // The order's own tick is the first day of work.
        for (int i = 1; i < 20; i++) {
            assertEquals(ShipState.BUILDING, h.state, "day " + i);
            tick();
        }
        assertEquals(ShipState.IDLE, h.state);
        assertEquals(0, h.buildDaysLeft);
        assertEquals(1, completions());
        assertTrue(w.recentEvents.stream().anyMatch(e -> e.message().equals("Mule is ready at Earth Hub")));
    }

    @Test void levelTwoShipyard_finishesATankerInThirteenDays() {
        world();
        yard().level = 2;
        tick(new BuildShipCommand("t1", "Tank", ShipClass.TANKER, HUB));
        for (int i = 1; i < 13; i++) tick();
        assertEquals(ShipState.IDLE, w.findShip("t1").state);
    }

    @Test void damagedShipyard_pausesTheBuild() {
        world();
        tick(new BuildShipCommand("x1", "Scout", ShipClass.EXPLORER, HUB));
        int left = w.findShip("x1").buildDaysLeft;
        yard().enabled = false;
        for (int i = 0; i < 5; i++) tick();
        assertEquals(left, w.findShip("x1").buildDaysLeft);
        assertEquals(ShipState.BUILDING, w.findShip("x1").state);
    }

    @Test void busyShipyard_rejectsASecondOrder() {
        world();
        tick(new BuildShipCommand("x1", "Scout", ShipClass.EXPLORER, HUB));
        tick(new BuildShipCommand("x2", "Scout 2", ShipClass.EXPLORER, HUB));
        assertNull(w.findShip("x2"));
        assertTrue(rejected("Every shipyard at Earth Hub is busy (Scout, "), w.recentEvents.toString());
    }

    @Test void twoShipyards_buildTwoShipsAtOnce() {
        world();
        hub().addBuilding(new Building(BuildingType.SHIPYARD, 1));
        tick(new BuildShipCommand("x1", "Scout", ShipClass.EXPLORER, HUB),
             new BuildShipCommand("x2", "Scout 2", ShipClass.EXPLORER, HUB));
        assertNotEquals(w.findShip("x1").yardBuildingId, w.findShip("x2").yardBuildingId);
    }

    @Test void shortOfMetal_isRejected_andNothingIsPaid() {
        world();
        hub().stockpile.put(Resource.METAL, 10.0);
        hub().stockpile.put(Resource.COMPONENTS, 50.0);
        sim.enqueue(new BuildShipCommand("c1", "Ark", ShipClass.COLONIZER, HUB));
        sim.advance(w);
        assertNull(w.findShip("c1"));
        assertTrue(rejected("Need 50 more METAL (have 10 of 60)"), w.recentEvents.toString());
        assertEquals(50.0, hub().stockpile.get(Resource.COMPONENTS), 1e-9);
    }

    @Test void cancel_refundsTheCost() {
        world();
        tick(new BuildShipCommand("h1", "Mule", ShipClass.HAULER, HUB));
        double comps = hub().stockpile.get(Resource.COMPONENTS);
        tick(new RetireShipCommand("h1"));
        assertNull(w.findShip("h1"));
        assertEquals(comps + 10, hub().stockpile.get(Resource.COMPONENTS), 1e-9);
    }

    @Test void shipUnderConstruction_cantBeDispatched() {
        world();
        w.findBody("mars").sites.add(new Site("site-mars-1", "Mars 1", "mars", 0, 0, 100));
        tick(new BuildShipCommand("h1", "Mule", ShipClass.HAULER, HUB));
        tick(new DispatchShipCommand("h1", "site-mars-1", Map.of()));
        assertTrue(rejected("Mule is still being built: 19 days left"), w.recentEvents.toString());
        assertEquals(ShipState.BUILDING, w.findShip("h1").state);
    }

    @Test void shipyardWithAShipOnIt_cantBeDemolished() {
        world();
        tick(new BuildShipCommand("h1", "Mule", ShipClass.HAULER, HUB));
        tick(new DemolishBuildingCommand(HUB, yard().id));
        assertTrue(rejected("Shipyard is building Mule: finish or cancel it first"));
        assertNotNull(yard());
    }

    @Test void fleetGoal_ignoresShipsUnderConstruction() {
        world();
        for (int i = 0; i < 10; i++) w.ships.add(new Ship("s" + i, "S" + i, ShipClass.HAULER, HUB));
        w.ships.get(0).state = ShipState.BUILDING;
        assertEquals(9, GoalCatalog.builtShips(w));
    }
}
