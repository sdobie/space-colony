package spacecolony.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.sim.Body;
import spacecolony.sim.OrbitalGeometry;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.debug.DebugController;
import spacecolony.debug.ObjectInspectorDialog;
import spacecolony.debug.YieldSummary;
import spacecolony.sim.phases.TransitPhase;

public class SystemMapPanel extends JPanel {
    private final Engine engine;
    private double scale = 70.0; // pixels per AU at zoom = 1
    private double zoom = 1.0;
    private double offsetX = 0, offsetY = 0;
    /** Set by the frame after construction (the controller is built after the panels). */
    private DebugController debug;
    private final YieldSummary yields = new YieldSummary();

    public SystemMapPanel(Engine engine) {
        this.engine = engine;
        setBackground(UiColors.STARFIELD_BG);
        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldReplaced) yields.clear();
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
                    int[] s = screenPoint(b.id, engine.world().tick, cxLocal, cyLocal);
                    double d = Math.hypot(s[0] - e.getX(), s[1] - e.getY());
                    if (d < bestDist) { bestDist = d; best = b; }
                }
                if (best != null && e.isShiftDown() && engine.debugEnabled()) {
                    ObjectInspectorDialog.inspect(SystemMapPanel.this, engine, best, "Body " + best.id);
                    return;
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

        // Moon orbits (display rings around the parent; see moonRingRadius)
        for (Body b : engine.world().bodies) {
            if (b.orbit.parentBodyId() == null) continue;
            int[] parent = screenPoint(b.orbit.parentBodyId(), engine.world().tick, cx, cy);
            int r = (int) Math.round(moonRingRadius(b));
            g2.drawOval(parent[0] - r, parent[1] - r, r * 2, r * 2);
        }

        // Bodies
        Selection sel = engine.selection();
        List<Rectangle> occupied = new ArrayList<>();
        for (Body b : engine.world().bodies) {
            int[] s = screenPoint(b.id, engine.world().tick, cx, cy);
            int r = b.orbit.parentBodyId() == null ? 5 : 3;
            occupied.add(new Rectangle(s[0] - r, s[1] - r, r * 2, r * 2));
        }
        FontMetrics fm = g2.getFontMetrics();
        for (Body b : engine.world().bodies) {
            int[] s = screenPoint(b.id, engine.world().tick, cx, cy);
            int x = s[0];
            int y = s[1];
            int radius = b.orbit.parentBodyId() == null ? 5 : 3;
            boolean selected = sel.kind() == Selection.Kind.BODY && b.id.equals(sel.id());
            if (selected) {
                g2.setColor(new Color(255, 240, 120));
                g2.drawOval(x - radius - 3, y - radius - 3, radius * 2 + 6, radius * 2 + 6);
            }
            g2.setColor(colorForBody(b));
            g2.fillOval(x - radius, y - radius, radius * 2, radius * 2);
            g2.setColor(UiColors.FOREGROUND_DIM);
            Rectangle label = placeLabel(fm, b.name, x, y, radius, occupied);
            occupied.add(label);
            g2.drawString(b.name, label.x, label.y + fm.getAscent());
        }

        // In-transit ships
        for (Ship ship : engine.world().ships) {
            if (ship.state != ShipState.IN_TRANSIT) continue;
            var t = ship.transit;
            var originSite = engine.world().findSite(t.originSiteId());
            String destBody = t.destBody(engine.world());
            if (originSite == null || destBody == null) continue;
            int[] op = screenPoint(originSite.bodyId, t.departureTick(), cx, cy);
            int[] dp = screenPoint(destBody, t.arrivalTick(), cx, cy);
            long now = engine.world().tick;
            double progress = (double)(now - t.departureTick()) / Math.max(1, t.arrivalTick() - t.departureTick());
            progress = Math.max(0, Math.min(1, progress));
            int x = (int) (op[0] + (dp[0] - op[0]) * progress);
            int y = (int) (op[1] + (dp[1] - op[1]) * progress);
            g2.setColor(UiColors.SHIP_DOT);
            g2.fillRect(x - 2, y - 2, 4, 4);
        }

        // Colonizers waiting in orbit: a ship dot just up and right of the body.
        for (Ship ship : engine.world().ships) {
            if (ship.orbitingBodyId == null) continue;
            int[] p = screenPoint(ship.orbitingBodyId, engine.world().tick, cx, cy);
            int x = p[0] + 6;
            int y = p[1] - 6;
            g2.setColor(UiColors.SHIP_DOT);
            g2.fillRect(x - 2, y - 2, 4, 4);
        }

        if (debug != null && debug.mapOverlaysOn()) paintDebugOverlays(g2, cx, cy);
        g2.dispose();
    }

    public void setDebug(DebugController debug) { this.debug = debug; }

    /** Smallest on-screen orbit radius for the innermost moon, in pixels. */
    static final double MOON_RING_MIN = 14.0;
    /** Extra on-screen radius for each further-out moon of the same parent, in pixels. */
    static final double MOON_RING_STEP = 9.0;

    /**
     * Screen position of a body. Planets sit at their true scaled position. Moon orbits are a
     * few thousandths of an AU, well under a pixel at map scale, so a moon is drawn along its
     * true direction from the parent but on a ring at least {@link #moonRingRadius} out.
     */
    int[] screenPoint(String bodyId, long tick, int cx, int cy) {
        var world = engine.world();
        Body b = world.findBody(bodyId);
        if (b == null || b.orbit.parentBodyId() == null) {
            double[] p = OrbitalGeometry.bodyPosition(world, bodyId, tick);
            return new int[] { cx + (int) (p[0] * scale * zoom + offsetX), cy + (int) (p[1] * scale * zoom + offsetY) };
        }
        int[] parent = screenPoint(b.orbit.parentBodyId(), tick, cx, cy);
        double[] rel = b.orbit.position(tick);
        double len = Math.hypot(rel[0], rel[1]);
        double ring = moonRingRadius(b);
        double ux = len == 0 ? 1 : rel[0] / len;
        double uy = len == 0 ? 0 : rel[1] / len;
        return new int[] { parent[0] + (int) Math.round(ux * ring), parent[1] + (int) Math.round(uy * ring) };
    }

    /** On-screen orbit radius of a moon: its true scaled radius, or a readable minimum ring by rank. */
    double moonRingRadius(Body moon) {
        int rank = 0;
        for (Body other : engine.world().bodies) {
            if (other != moon && moon.orbit.parentBodyId().equals(other.orbit.parentBodyId())
                && other.orbit.semiMajorAxis() < moon.orbit.semiMajorAxis()) rank++;
        }
        double trueRadius = moon.orbit.semiMajorAxis() * scale * zoom;
        return Math.max(trueRadius, MOON_RING_MIN + rank * MOON_RING_STEP);
    }

    /**
     * Picks a spot for a body's name that does not cover another label or body dot, trying
     * right, left, below and above the dot, then further out. Falls back to the right side.
     */
    static Rectangle placeLabel(FontMetrics fm, String text, int x, int y, int radius, List<Rectangle> occupied) {
        int w = fm.stringWidth(text);
        int h = fm.getAscent() + fm.getDescent();
        int gap = radius + 3;
        Rectangle first = null;
        for (int extra = 0; extra <= 24; extra += 8) {
            int g = gap + extra;
            Rectangle[] candidates = {
                new Rectangle(x + g, y - h / 2, w, h),
                new Rectangle(x - g - w, y - h / 2, w, h),
                new Rectangle(x - w / 2, y + g, w, h),
                new Rectangle(x - w / 2, y - g - h, w, h),
            };
            for (Rectangle c : candidates) {
                if (first == null) first = c;
                boolean clear = true;
                for (Rectangle o : occupied) {
                    if (o.intersects(c)) { clear = false; break; }
                }
                if (clear) return c;
            }
        }
        return first;
    }

    /** Debug map layers (design §4.6): orbit periods, transit predictions, yield summaries. */
    private void paintDebugOverlays(Graphics2D g2, int cx, int cy) {
        var world = engine.world();
        Font small = getFont().deriveFont(10f);
        g2.setFont(small);
        Color orbitLabel = UiColors.ORBIT_LINE.brighter().brighter();
        orbitLabel = new Color(orbitLabel.getRed(), orbitLabel.getGreen(), orbitLabel.getBlue());

        for (Body b : world.bodies) {
            int[] s = screenPoint(b.id, world.tick, cx, cy);
            int x = s[0];
            int y = s[1];
            g2.setColor(orbitLabel);
            if (b.orbit.parentBodyId() == null) {
                int r = (int) (b.orbit.semiMajorAxis() * scale * zoom);
                double c45 = Math.cos(Math.PI / 4);
                g2.drawString(b.orbit.period() + " d", cx + (int) (r * c45), cy - (int) (r * c45));
            } else {
                g2.drawString(b.orbit.period() + " d", x + 6, y + 14);
            }
            String ys = YieldSummary.format(yields.top(b, 3));
            if (!ys.isEmpty()) {
                g2.setColor(UiColors.FOREGROUND_DIM);
                g2.drawString(ys, x + 8, y + 16);
            }
        }

        Stroke dashed = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 4f }, 0f);
        Stroke prior = g2.getStroke();
        for (Ship ship : world.ships) {
            if (ship.state != ShipState.IN_TRANSIT) continue;
            var t = ship.transit;
            var originSite = world.findSite(t.originSiteId());
            String destBody = t.destBody(world);
            if (originSite == null || destBody == null) continue;
            int[] op = screenPoint(originSite.bodyId, t.departureTick(), cx, cy);
            int[] dp = screenPoint(destBody, t.arrivalTick(), cx, cy);
            double progress = (double) (world.tick - t.departureTick()) / Math.max(1, t.arrivalTick() - t.departureTick());
            progress = Math.max(0, Math.min(1, progress));
            int sx = (int) (op[0] + (dp[0] - op[0]) * progress);
            int sy = (int) (op[1] + (dp[1] - op[1]) * progress);
            int dx = dp[0];
            int dy = dp[1];
            g2.setColor(UiColors.SHIP_DOT);
            g2.setStroke(dashed);
            g2.drawLine(sx, sy, dx, dy);
            g2.setStroke(prior);
            g2.drawOval(dx - 4, dy - 4, 8, 8);
            double mass = 0;
            for (double v : t.cargoSnapshot().values()) mass += v;
            double fuel = t.destSiteId() != null
                ? TransitPhase.fuelCost(world, ship.shipClass, mass,
                    t.originSiteId(), t.destSiteId(), t.departureTick(), t.arrivalTick())
                : TransitPhase.fuelCostToBody(world, ship.shipClass, mass,
                    t.originSiteId(), t.destBodyId(), t.departureTick(), t.arrivalTick());
            g2.drawString(String.format("t=%d  ≈%.1f fuel", t.arrivalTick(), fuel), dx + 6, dy - 6);
        }
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
