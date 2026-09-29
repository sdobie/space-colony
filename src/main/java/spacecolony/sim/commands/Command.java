package spacecolony.sim.commands;

public sealed interface Command
    permits BuildSiteCommand, BuildBuildingCommand, BuildShipCommand,
            DispatchShipCommand, RetireShipCommand, QueueResearchCommand,
            SetRandomEventsCommand, UpgradeBuildingCommand, RepairBuildingCommand,
            CancelConstructionCommand, DemolishBuildingCommand {}
