package spacecolony.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;

public class SystemMapPanel extends JPanel {
    private final Engine engine;
    private double scale = 70.0; // pixels per AU at zoom = 1
    private double zoom = 1.0;
    private double offsetX = 0, offsetY = 0;

    public SystemMapPanel(Engine engine) {
        this.engine = engine;
        setBackground(UiColors.STARFIELD_BG);
        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced
             || e instanceof EngineEvent.SelectionChanged) repaint();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int cxLocal = getWidth() / 2;
                int cyLocal = getHeight() / 2;
                Body best = null;
                double bestDist = 12.0;
                for (Body b : engine.world().bodies) {
                    double[] p = OrbitalGeometry.bodyPosition(engine.world(), b.id, engine.world().tick);
                    int x = cxLocal + (int) (p[0] * scale * zoom + offsetX);
                    int y = cyLocal + (int) (p[1] * scale * zoom + offsetY);
                    double d = Math.hypot(x - e.getX(), y - e.getY());
                    if (d < bestDist) { bestDist = d; best = b; }
                }
                if (best != null) engine.setSelection(Selection.body(best.id));
                else engine.setSelection(Selection.NONE);
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2;

        // Sun
        g2.setColor(UiColors.SUN);
        g2.fillOval(cx - 6, cy - 6, 12, 12);

        // Orbits (faint circles for top-level bodies)
        g2.setColor(UiColors.ORBIT_LINE);
        for (Body b : engine.world().bodies) {
            if (b.orbit.parentBodyId() != null) continue; // skip moons here
            int r = (int) (b.orbit.semiMajorAxis() * scale * zoom);
            g2.drawOval(cx - r, cy - r, r * 2, r * 2);
        }

        // Bodies
        Selection sel = engine.selection();
        for (Body b : engine.world().bodies) {
            double[] p = OrbitalGeometry.bodyPosition(engine.world(), b.id, engine.world().tick);
            int x = cx + (int) (p[0] * scale * zoom + offsetX);
            int y = cy + (int) (p[1] * scale * zoom + offsetY);
            int radius = b.orbit.parentBodyId() == null ? 5 : 3;
            boolean selected = sel.kind() == Selection.Kind.BODY && b.id.equals(sel.id());
            if (selected) {
                g2.setColor(new Color(255, 240, 120));
                g2.drawOval(x - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6);
            }
            g2.setColor(colorForBody(b));
            g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            g2.setColor(UiColors.FOREGROUND_DIM);
            g2.drawString(b.name, x + radius + 3, y + 4);
        }

        // In-transit ships
        for (Ship ship : engine.world().ships) {
            if (ship.state != ShipState.IN_TRANSIT) continue;
            var t = ship.transit;
            var originSite = engine.world().findSite(t.originSiteId());
            var destSite = engine.world().findSite(t.destSiteId());
            if (originSite == null || destSite == null) continue;
            double[] op = OrbitalGeometry.bodyPosition(engine.world(), originSite.bodyId, t.departureTick());
            double[] dp = OrbitalGeometry.bodyPosition(engine.world(), destSite.bodyId, t.arrivalTick());
            long now = engine.world().tick;
            double progress = (double)(now - t.departureTick()) / Math.max(1, t.arrivalTick() - t.departureTick());
            progress = Math.max(0, Math.min(1, progress));
            double sx = op[0] + (dp[0] - op[0]) * progress;
            double sy = op[1] + (dp[1] - op[1]) * progress;
            int x = cx + (int) (sx * scale * zoom + offsetX);
            int y = cy + (int) (sy * scale * zoom + offsetY);
            g2.setColor(UiColors.SHIP_DOT);
            g2.fillRect(x - 2, y - 2, 4, 4);
        }

        g2.dispose();
    }

    private static Color colorForBody(Body b) {
        return switch (b.type) {
            case ROCKY     -> new Color(180, 130, 100);
            case GAS_GIANT -> new Color(220, 180, 130);
            case ICE_BODY  -> new Color(180, 220, 240);
            case ASTEROID  -> new Color(130, 120, 110);
            case MOON      -> new Color(170, 165, 160);
        };
    }
}
