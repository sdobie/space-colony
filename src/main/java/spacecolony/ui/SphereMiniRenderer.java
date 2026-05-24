package spacecolony.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.render.BodyAppearance;
import spacecolony.render.BodyAppearances;
import spacecolony.render.PlanetGenerator;
import spacecolony.render.SphereRenderer;
import spacecolony.sim.Body;

/** Small (~200px) sphere render for the right dock. Flat maps are cached per body id. */
public class SphereMiniRenderer extends JPanel {
    private static final int SIZE = 200;
    private static final int FLAT_W = 512;
    private static final int FLAT_H = 256;

    private final Engine engine;
    private final Map<String, BufferedImage> flatCache = new HashMap<>();
    private Body body;

    public SphereMiniRenderer(Engine engine) {
        this.engine = engine;
        setOpaque(false);
        setPreferredSize(new Dimension(SIZE, SIZE));
        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged) repaint();
        });
    }

    /** Switch which body this renderer shows. Pass null to clear. */
    public void setBody(Body body) {
        this.body = body;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (body == null) return;
        BufferedImage flat = flatCache.computeIfAbsent(body.id, id -> {
            BodyAppearance app = BodyAppearances.defaultFor(body.type);
            return new PlanetGenerator(FLAT_W, FLAT_H).generate(body.surfaceSeed, app);
        });
        BodyAppearance app = BodyAppearances.defaultFor(body.type);
        // Rotation advances slowly with tick: 1 full rotation per 360 ticks.
        double rotation = (engine.world().tick % 360) * 1.0;
        BufferedImage sphere = SphereRenderer.render(flat, SIZE, rotation, 12.0, 1.0,
            body.surfaceSeed, app.atmosphereColor());
        g.drawImage(sphere, 0, 0, null);
    }
}
