package spacecolony.ui.dialogs;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingSpec;
import spacecolony.sim.BuildingType;
import spacecolony.testutil.Edt;
import spacecolony.ui.BuildingInfoPanel;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class BuildBuildingDialogTest {
    private static final String HUB = "site-earth-hub";

    @BeforeEach
    void needsDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display; run under xvfb-run");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> p) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && p.test((T) c)) return (T) c;
            if (c instanceof Container k) {
                T hit = find(k, type, p);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    @Test
    void listsBuildingsByName_andShowsTheForecast() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            JDialog d = BuildBuildingDialog.create(null, engine, HUB);
            try {
                assertEquals("Build at Earth Hub", d.getTitle());
                @SuppressWarnings("unchecked")
                JList<BuildingSpec> list = find(d.getContentPane(), JList.class, l -> BuildBuildingDialog.LIST.equals(l.getName()));
                assertEquals(BuildingType.values().length, list.getModel().getSize());
                assertEquals("Habitat", list.getModel().getElementAt(0).displayName());
                list.setSelectedValue(BuildingCatalog.get(BuildingType.FARM), false);
                BuildingInfoPanel info = find(d.getContentPane(), BuildingInfoPanel.class, p -> true);
                String text = info.text();
                assertTrue(text.contains("Grows food from biomass"), text);
                assertTrue(text.contains("BIOMASS"), text);
                assertTrue(text.contains("At Earth Hub, per day"), text);
            } finally {
                d.dispose();
            }
        });
    }

    @Test
    void buildEnqueuesAndCancelDoesNot() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(42L));
            JDialog cancelled = BuildBuildingDialog.create(null, engine, HUB);
            find(cancelled.getContentPane(), JButton.class, b -> BuildBuildingDialog.CANCEL.equals(b.getName())).doClick();
            assertEquals(0, engine.commandQueueDepth());
            assertFalse(cancelled.isDisplayable());

            JDialog d = BuildBuildingDialog.create(null, engine, HUB);
            @SuppressWarnings("unchecked")
            JList<BuildingSpec> list = find(d.getContentPane(), JList.class, l -> true);
            list.setSelectedValue(BuildingCatalog.get(BuildingType.RESEARCH_LAB), false);
            JButton ok = find(d.getContentPane(), JButton.class, b -> BuildBuildingDialog.OK.equals(b.getName()));
            assertEquals("Build", ok.getText());
            ok.doClick();
            assertEquals(1, engine.commandQueueDepth());
            long labs = engine.world().findSite(HUB).buildings.stream().filter(b -> b.type == BuildingType.RESEARCH_LAB).count();
            engine.advanceSilently(1);
            assertEquals(labs + 1, engine.world().findSite(HUB).buildings.stream().filter(b -> b.type == BuildingType.RESEARCH_LAB).count());
        });
    }
}
