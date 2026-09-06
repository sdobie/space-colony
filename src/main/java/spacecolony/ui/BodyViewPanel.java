package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.render.BodyAppearance;
import spacecolony.render.BodyAppearances;
import spacecolony.render.PlanetGenerator;
import spacecolony.render.SphereRenderer;
import spacecolony.sim.Body;

public class BodyViewPanel extends JPanel {
    private static final int FLAT_W = 1024;
    private static final int FLAT_H = 512;

    private final Engine engine;
    private final SpherePanel sphere;
    private final Map<String, BufferedImage> flatCache = new HashMap<>();

    public BodyViewPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.STARFIELD_BG);
        this.sphere = new SpherePanel();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        JButton back = new JButton("← Back to system map");
        back.addActionListener(e -> engine.setView(EngineEvent.ViewChanged.View.SYSTEM_MAP));
        top.add(back);

        add(top, BorderLayout.NORTH);
        add(sphere, BorderLayout.CENTER);

        engine.addListener(e -> {
            // A replaced World may carry different surfaceSeeds under the same body ids,
            // so the per-body flat maps must be discarded before repainting.
            if (e instanceof EngineEvent.WorldReplaced) flatCache.clear();
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced
             || e instanceof EngineEvent.SelectionChanged
             || e instanceof EngineEvent.ViewChanged) sphere.repaint();
        });
    }

    private Body currentBody() {
        Selection sel = engine.selection();
        if (sel.kind() != Selection.Kind.BODY) return null;
        return engine.world().findBody(sel.id());
    }

    private BufferedImage flatMap(Body b) {
        return flatCache.computeIfAbsent(b.id, id ->
            new PlanetGenerator(FLAT_W, FLAT_H).generate(b.surfaceSeed, BodyAppearances.defaultFor(b.type)));
    }

    private class SpherePanel extends JPanel {
        SpherePanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(600, 600));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    Body b = currentBody();
                    if (b == null) return;
                    int size = Math.min(getWidth(), getHeight()) - 40;
                    if (size < 64) return;
                    int x0 = (getWidth() - size) / 2;
                    int y0 = (getHeight() - size) / 2;
                    int px = e.getX() - x0;
                    int py = e.getY() - y0;
                    if (px < 0 || py < 0 || px >= size || py >= size) return;
                    double rotation = (engine.world().tick % 360) * 1.0;
                    double[] latLon = SphereRenderer.unproject(px, py, size, rotation, 12.0, 1.0);
                    if (latLon == null) return;
                    spacecolony.ui.dialogs.PlaceSiteDialog.show(BodyViewPanel.this, engine, b.id, latLon[0], latLon[1]);
                }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Body b = currentBody();
            if (b == null) return;
            int size = Math.min(getWidth(), getHeight()) - 40;
            if (size < 64) return;
            BodyAppearance app = BodyAppearances.defaultFor(b.type);
            double rotation = (engine.world().tick % 360) * 1.0;
            BufferedImage sphereImg = SphereRenderer.render(flatMap(b), size, rotation, 12.0, 1.0,
                b.surfaceSeed, app.atmosphereColor());
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g.drawImage(sphereImg, x, y, null);
        }
    }
}
