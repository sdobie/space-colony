package spacecolony.sim;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spacecolony.sim.commands.*;
import spacecolony.sim.economy.FlowLine;
import spacecolony.sim.economy.Limit;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static spacecolony.sim.BuildingType.*;

class ConstructionCommandTest {
    static final String HUB = "site-earth-hub";
    World w;
    Site hub;
    final Simulator sim = new Simulator();

    @BeforeEach
    void setUp() {
        w = WorldGenerator.generate(1L);
        w.randomEventsEnabled = false;
        hub = w.findSite(HUB);
    }

    private void tick(Command... cmds) {
        for (Command c : cmds) sim.enqueue(c);
        sim.advance(w);
    }

    private double stock(Resource r) { return hub.stockpile.get(r); }

    private Building first(BuildingType t) {
        return hub.buildings.stream().filter(b -> b.type == t).findFirst().orElseThrow();
    }

    private List<String> rejections() {
        return w.recentEvents.stream().filter(e -> e.kind() == EventKind.COMMAND_REJECTED).map(Event::message).toList();
    }

    private double made(Building b, Resource r) {
        int i = hub.buildings.indexOf(b);
        return hub.lastDay.linesOf(i).stream().filter(l -> l.resource() == r)
            .mapToDouble(FlowLine::amount).sum();
    }

    @Test void buildPaysAndStartsAtLevelZero() {
        tick(new BuildBuildingCommand(HUB, FARM));
        assertEquals(85.0, stock(Resource.METAL), 1e-9);
        assertEquals(45.0, stock(Resource.COMPONENTS), 1e-9);
        Building farm = hub.buildings.get(hub.buildings.size() - 1);
        assertEquals(0, farm.level);
        assertEquals(2, farm.daysLeft); // 3 days, one already gone at the end of this tick
        assertEquals(6, farm.id);
        assertEquals(Limit.CONSTRUCTING, hub.lastDay.outcome(hub.buildings.size() - 1).limit());
        assertEquals(8.0, hub.lastDay.powerUsed, 1e-9, "a building under construction draws no power");
    }

    @Test void shortStockIsRejectedAndNothingIsPaid() {
        hub.stockpile.put(Resource.METAL, 0.0);
        tick(new BuildBuildingCommand(HUB, FARM));
        assertEquals(5, hub.buildings.size());
        assertEquals(50.0, stock(Resource.COMPONENTS), 1e-9);
        assertTrue(rejections().contains("Need 15 more METAL (have 0 of 15)"), rejections().toString());
    }

    @Test void eleventhBuildingIsRejected() {
        for (int i = 0; i < 5; i++) hub.addBuilding(new Building(HABITAT, 1));
        tick(new BuildBuildingCommand(HUB, FARM));
        assertEquals(10, hub.buildings.size());
        assertTrue(rejections().contains("No free building slot (10 of 10)"), rejections().toString());
    }

    @Test void newFarmFinishesAfterItsBuildTimeThenProduces() {
        tick(new BuildBuildingCommand(HUB, FARM));
        Building farm = hub.buildings.get(hub.buildings.size() - 1);
        tick();
        assertEquals(0, farm.level);
        tick();
        assertEquals(1, farm.level);
        assertEquals(0, farm.daysLeft);
        long done = w.recentEvents.stream().filter(e -> e.kind() == EventKind.BUILDING_COMPLETED).count();
        assertEquals(1, done);
        assertTrue(w.recentEvents.stream().anyMatch(e -> e.message().equals("Farm finished at Earth Hub")));
        assertEquals(0.0, made(farm, Resource.FOOD), 1e-9);
        tick();
        assertTrue(made(farm, Resource.FOOD) > 0.1);
    }

    @Test void upgradeKeepsWorkingThenDoubles() {
        Building farm = first(FARM);
        tick(new UpgradeBuildingCommand(HUB, farm.id));
        assertEquals(85.0, stock(Resource.METAL), 1e-9);
        assertEquals(1, farm.level);
        double l1 = made(farm, Resource.FOOD);
        assertTrue(l1 > 0.1);
        tick();
        tick();
        assertEquals(2, farm.level);
        assertTrue(w.recentEvents.stream().anyMatch(e -> e.message().equals("Farm upgraded to L2 at Earth Hub")));
        tick();
        assertEquals(2 * l1, made(farm, Resource.FOOD), 0.02 * l1); // power drifts a hair as Earth moves
    }

    @Test void repairIsInstant() {
        Building mine = first(MINE);
        mine.enabled = false;
        tick(new RepairBuildingCommand(HUB, mine.id));
        assertTrue(mine.enabled);
        assertTrue(made(mine, Resource.ORE) > 0);
        assertEquals(95.0, stock(Resource.METAL), 1e-9);
        assertEquals(48.0, stock(Resource.COMPONENTS), 1e-9);
    }

    @Test void cancelANewBuildingRefundsAllAndFreesTheSlot() {
        tick(new BuildBuildingCommand(HUB, FARM));
        Building farm = hub.buildings.get(hub.buildings.size() - 1);
        tick(new CancelConstructionCommand(HUB, farm.id));
        assertFalse(hub.buildings.contains(farm));
        assertEquals(100.0, stock(Resource.METAL), 1e-9);
        assertEquals(50.0, stock(Resource.COMPONENTS), 1e-9);
    }

    @Test void cancelAnUpgradeKeepsTheLevel() {
        Building farm = first(FARM);
        tick(new UpgradeBuildingCommand(HUB, farm.id));
        tick(new CancelConstructionCommand(HUB, farm.id));
        assertEquals(1, farm.level);
        assertEquals(0, farm.daysLeft);
        assertEquals(100.0, stock(Resource.METAL), 1e-9);
    }

    @Test void demolishRefundsHalfAndFreesTheSlot() {
        Building yard = first(SHIPYARD);
        tick(new DemolishBuildingCommand(HUB, yard.id));
        assertFalse(hub.buildings.contains(yard));
        assertEquals(125.0, stock(Resource.METAL), 1e-9);
        assertEquals(60.0, stock(Resource.COMPONENTS), 1e-9);
    }

    @Test void refundStopsAtTheCap() {
        hub.stockpile.put(Resource.METAL, 990.0);
        tick(new DemolishBuildingCommand(HUB, first(SHIPYARD).id));
        assertEquals(1000.0, stock(Resource.METAL), 1e-9);
    }

    @Test void queuedDemolitionsHitTheIntendedBuildings() {
        int farm = first(FARM).id, mine = first(MINE).id;
        tick(new DemolishBuildingCommand(HUB, farm), new DemolishBuildingCommand(HUB, mine));
        assertEquals(List.of(HABITAT, POWER_PLANT, SHIPYARD), hub.buildings.stream().map(b -> b.type).toList());
    }

    @Test void unknownBuildingIsRejected() {
        tick(new DemolishBuildingCommand(HUB, 99));
        assertTrue(rejections().stream().anyMatch(m -> m.startsWith("No such building")), rejections().toString());
    }

    @Test void damagePausesConstruction() {
        tick(new BuildBuildingCommand(HUB, FARM));
        Building farm = hub.buildings.get(hub.buildings.size() - 1);
        farm.enabled = false;
        tick();
        tick();
        assertEquals(2, farm.daysLeft);
        assertEquals(0, farm.level);
    }

    @Test void shipyardUnderConstructionCantBuildShips() {
        hub.buildings.remove(first(SHIPYARD));
        tick(new BuildBuildingCommand(HUB, SHIPYARD));
        tick(new BuildShipCommand("h1", "Mule", ShipClass.HAULER, HUB));
        assertNull(w.findShip("h1"));
    }
}
