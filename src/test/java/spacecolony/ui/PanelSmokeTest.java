package spacecolony.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.testutil.Edt;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class PanelSmokeTest {

    @Test
    void fileMenu_buildsWithoutCrashing() throws Exception {
        // Null owner: it is only used as the parent component for modal dialogs, which
        // this test never opens. Constructing the menu exercises the action wiring.
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            FileMenu menu = new FileMenu(null, engine);
            assertEquals(1, menu.getMenuCount());
            assertEquals("File", menu.getMenu(0).getText());
            assertEquals(5, menu.getMenu(0).getMenuComponentCount(), "New/Save/Load/separator/Quit");
        });
    }

    @Test
    void sphereMiniRenderer_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            SphereMiniRenderer panel = new SphereMiniRenderer(engine);
            panel.setBody(engine.world().findBody("earth"));
            panel.setSize(200, 200);
            paintToImage(panel, 200, 200);
        });
    }

    @Test
    void topBar_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            TopBar p = new TopBar(engine);
            p.setSize(800, 40);
            paintToImage(p, 800, 40);
        });
    }

    @Test
    void colonyListPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            ColonyListPanel p = new ColonyListPanel(engine);
            p.setSize(220, 600);
            paintToImage(p, 220, 600);
        });
    }

    @Test
    void systemMapPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            SystemMapPanel p = new SystemMapPanel(engine);
            p.setSize(800, 600);
            paintToImage(p, 800, 600);
        });
    }

    @Test
    void detailPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            DetailPanel p = new DetailPanel(engine);
            p.setSize(280, 600);
            paintToImage(p, 280, 600);
            engine.setSelection(Selection.body("earth"));
            paintToImage(p, 280, 600);
            engine.setSelection(Selection.site("site-earth-hub"));
            paintToImage(p, 280, 600);
        });
    }

    @Test
    void bodyViewPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            BodyViewPanel p = new BodyViewPanel(engine);
            p.setSize(600, 600);
            paintToImage(p, 600, 600);
            engine.setSelection(Selection.body("earth"));
            paintToImage(p, 600, 600);
        });
    }

    @Test
    void eventStripPanel_paintsWithoutCrashing() throws Exception {
        Edt.run(() -> {
            Engine engine = new Engine(WorldGenerator.generate(1L));
            EventStripPanel p = new EventStripPanel(engine);
            p.setSize(800, 110);
            paintToImage(p, 800, 110);
        });
    }

    private static void paintToImage(JPanel panel, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = img.createGraphics();
        try {
            assertDoesNotThrow(() -> panel.paint(g));
        } finally {
            g.dispose();
        }
    }
}
