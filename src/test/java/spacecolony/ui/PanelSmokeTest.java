package spacecolony.ui;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import spacecolony.engine.Engine;
import spacecolony.engine.Selection;
import spacecolony.world.WorldGenerator;
import static org.junit.jupiter.api.Assertions.*;

class PanelSmokeTest {

    @Test
    void sphereMiniRenderer_paintsWithoutCrashing() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        SphereMiniRenderer panel = new SphereMiniRenderer(engine);
        panel.setBody(engine.world().findBody("earth"));
        panel.setSize(200, 200);
        paintToImage(panel, 200, 200);
    }

    @Test
    void topBar_paintsWithoutCrashing() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        TopBar p = new TopBar(engine);
        p.setSize(800, 40);
        paintToImage(p, 800, 40);
    }

    @Test
    void colonyListPanel_paintsWithoutCrashing() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        ColonyListPanel p = new ColonyListPanel(engine);
        p.setSize(220, 600);
        paintToImage(p, 220, 600);
    }

    @Test
    void systemMapPanel_paintsWithoutCrashing() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        SystemMapPanel p = new SystemMapPanel(engine);
        p.setSize(800, 600);
        paintToImage(p, 800, 600);
    }

    @Test
    void detailPanel_paintsWithoutCrashing() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        DetailPanel p = new DetailPanel(engine);
        p.setSize(280, 600);
        paintToImage(p, 280, 600);
        engine.setSelection(Selection.body("earth"));
        paintToImage(p, 280, 600);
        engine.setSelection(Selection.site("site-earth-hub"));
        paintToImage(p, 280, 600);
    }

    @Test
    void eventStripPanel_paintsWithoutCrashing() {
        Engine engine = new Engine(WorldGenerator.generate(1L));
        EventStripPanel p = new EventStripPanel(engine);
        p.setSize(800, 110);
        paintToImage(p, 800, 110);
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
