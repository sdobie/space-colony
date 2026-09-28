package spacecolony.tutorial;

import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.engine.Speed;
import spacecolony.sim.World;

/** What a tutorial step's completion test may look at. Read-only by contract. */
public record TutorialContext(World world, Speed speed, Selection selection, EngineEvent.ViewChanged.View view) {}
