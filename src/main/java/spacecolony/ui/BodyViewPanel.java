package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import spacecolony.debug.ObjectInspectorDialog;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.render.BodyAppearance;
import spacecolony.render.BodyAppearances;
import spacecolony.render.PlanetGenerator;
import spacecolony.render.SphereRenderer;
import spacecolony.render.YieldOverlay;
import spacecolony.sim.Body;
import spacecolony.sim.Resource;
import spacecolony.sim.ResourceSurvey;
import spacecolony.sim.Site;

public class BodyViewPanel extends JPanel {
    private static final int FLAT_W = 1024;
    private static final int FLAT_H = 512;

    private final Engine engine;
    private final SpherePanel sphere;
    private final Map<String, BufferedImage> flatCache = new HashMap<>();
    /** Tinted maps keyed {@code bodyId#RESOURCE}; cleared with {@link #flatCache}. */
    private final Map<String, BufferedImage> overlayCache = new HashMap<>();
    /** The overlay picked per body this session; absent means the plain surface. */
    private final Map<String, Resource> chosenOverlay = new HashMap<>();
    private final JComboBox<String> overlayPicker = new JComboBox<>();
    private final JLabel readout = new JLabel(" ");
    /** Body id + surveyed flag the picker was last built for, so a tick doesn't rebuild it. */
    private String pickerKey;
    private boolean syncingPicker;
    private Point mouse;
    /** Degrees added to the day's spin so a picked resource's best spot faces the camera. */
    private double turnOffset;
    /** Latitude, in degrees, the globe is tipped to face: a slight tilt, or a picked best spot's. */
    private double tilt = DEFAULT_TILT;
    private static final double DEFAULT_TILT = 12.0;
    /** Tipping further than this makes the globe look upside down at the poles. */
    private static final double MAX_TILT = 50.0;

    static final String SURFACE = "Surface";
    static final String UNSURVEYED = "Unsurveyed";
    static final String UNSURVEYED_TIP = "Send an explorer to survey this body.";

    /** Component name the tutorial highlights (Plan 6 §5.4). */
    public static final String TARGET_SPHERE = "bodyview.sphere";

    public BodyViewPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.STARFIELD_BG);
        this.sphere = new SpherePanel();
        sphere.setName(TARGET_SPHERE);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        JButton back = new JButton("← Back to system map");
        back.addActionListener(e -> engine.setView(EngineEvent.ViewChanged.View.SYSTEM_MAP));
        top.add(back);
        overlayPicker.setFocusable(false);
        overlayPicker.addActionListener(e -> {
            if (syncingPicker) return;
            Body b = currentBody();
            if (b == null) return;
            Object item = overlayPicker.getSelectedItem();
            if (item == null || SURFACE.equals(item) || UNSURVEYED.equals(item)) {
                chosenOverlay.remove(b.id);
                tilt = DEFAULT_TILT;
            }
            else {
                Resource r = Resource.valueOf((String) item);
                chosenOverlay.put(b.id, r);
                faceBestSpot(b, r);
            }
            updateReadout();
            sphere.repaint();
        });
        JLabel overlayLabel = new JLabel("Show resource:");
        overlayLabel.setForeground(UiColors.FOREGROUND_DIM);
        top.add(overlayLabel);
        top.add(overlayPicker);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.setOpaque(false);
        readout.setForeground(UiColors.FOREGROUND);
        bottom.add(readout);

        add(top, BorderLayout.NORTH);
        add(sphere, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        syncPicker();

        engine.addListener(e -> {
            // A replaced World may carry different surfaceSeeds under the same body ids,
            // so the per-body flat maps must be discarded before repainting.
            if (e instanceof EngineEvent.WorldReplaced) {
                flatCache.clear();
                overlayCache.clear();
                chosenOverlay.clear();
                pickerKey = null;
                turnOffset = 0;
                tilt = DEFAULT_TILT;
            }
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced
             || e instanceof EngineEvent.SelectionChanged) {
                syncPicker();
                updateReadout();
            }
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

    /** Rebuilds the overlay picker when the body or its survey state changes. */
    private void syncPicker() {
        Body b = currentBody();
        boolean surveyed = b != null && engine.world().isSurveyed(b.id);
        String key = b == null ? null : b.id + "#" + surveyed;
        if (key != null && key.equals(pickerKey)) return;
        pickerKey = key;
        syncingPicker = true;
        try {
            overlayPicker.removeAllItems();
            if (b == null || !surveyed) {
                overlayPicker.addItem(UNSURVEYED);
                overlayPicker.setEnabled(false);
                overlayPicker.setToolTipText(b == null ? null : UNSURVEYED_TIP);
                return;
            }
            overlayPicker.addItem(SURFACE);
            for (ResourceSurvey.Entry e : ResourceSurvey.of(b)) overlayPicker.addItem(e.resource().name());
            Resource chosen = chosenOverlay.get(b.id);
            overlayPicker.setSelectedItem(chosen == null ? SURFACE : chosen.name());
            overlayPicker.setEnabled(true);
            overlayPicker.setToolTipText("Colour the globe by how much of a resource is in the ground");
        } finally {
            syncingPicker = false;
        }
    }

    /** The overlay shown for {@code b}, or null for the plain surface (always null when unsurveyed). */
    Resource overlayFor(Body b) {
        if (b == null || !engine.world().isSurveyed(b.id)) return null;
        return chosenOverlay.get(b.id);
    }

    JComboBox<String> overlayPicker() { return overlayPicker; }
    JLabel readout() { return readout; }

    private void updateReadout() {
        Body b = currentBody();
        String text = mouse == null || b == null ? null : readoutText(b, mouse.x, mouse.y);
        readout.setText(text == null ? " " : text);
    }

    /**
     * "Here: ORE .72 · SIL .40 …" for a point in the sphere panel while an overlay is on;
     * null when the overlay is off or the point is off the globe.
     */
    String readoutText(Body b, int x, int y) {
        if (overlayFor(b) == null) return null;
        double[] latLon = latLonAt(x, y);
        if (latLon == null) return null;
        List<ResourceSurvey.Entry> here = ResourceSurvey.at(b, latLon[0], latLon[1]);
        StringBuilder sb = new StringBuilder("Here:");
        Resource shown = overlayFor(b);
        // The resource on the globe comes first, with its band, so the readout matches the colours.
        here.sort((p, q) -> Boolean.compare(q.resource() == shown, p.resource() == shown));
        for (int i = 0; i < here.size(); i++) {
            ResourceSurvey.Entry e = here.get(i);
            sb.append(i == 0 ? " " : " · ").append(ResourceSurvey.abbrev(e.resource()))
              .append(' ').append(ResourceSurvey.fmt(e.best()));
            if (e.resource() == shown) sb.append(" (").append(e.rating().label()).append(')');
        }
        if (here.isEmpty()) sb.append(" nothing to mine or farm");
        else if (here.get(0).resource() != shown) sb.append(" · no ").append(shown).append(" here");
        return sb.toString();
    }

    /** The globe's (lat, lon) under a point in the sphere panel, or null off the globe. */
    private double[] latLonAt(int x, int y) {
        int size = Math.min(sphere.getWidth(), sphere.getHeight()) - 40;
        if (size < 64) return null;
        int px = x - (sphere.getWidth() - size) / 2;
        int py = y - (sphere.getHeight() - size) / 2;
        if (px < 0 || py < 0 || px >= size || py >= size) return null;
        return SphereRenderer.unproject(px, py, size, rotation(), tilt, 1.0);
    }

    /** The globe's spin in degrees: one degree a day, plus any turn to show a best spot. */
    double rotation() {
        return ((engine.world().tick % 360) + turnOffset) % 360;
    }

    /** Turns and tips the globe so {@code r}'s best spot on {@code b} faces the camera. */
    private void faceBestSpot(Body b, Resource r) {
        ResourceSurvey.Entry best = surveyEntry(b, r);
        if (best == null) return;
        tilt = Math.max(-MAX_TILT, Math.min(MAX_TILT, Math.toDegrees(best.bestLat())));
        turnOffset = ((Math.toDegrees(best.bestLon()) - engine.world().tick % 360) % 360 + 360) % 360;
    }

    /** The screen point of {@code (lat, lon)} in the sphere panel, or null on the far side. */
    double[] screenPointOf(double lat, double lon) {
        int size = Math.min(sphere.getWidth(), sphere.getHeight()) - 40;
        if (size < 64) return null;
        double[] p = SphereRenderer.project(lat, lon, size, rotation(), tilt, 1.0);
        if (p == null) return null;
        return new double[] { p[0] + (sphere.getWidth() - size) / 2, p[1] + (sphere.getHeight() - size) / 2 };
    }

    private static ResourceSurvey.Entry surveyEntry(Body b, Resource r) {
        for (ResourceSurvey.Entry e : ResourceSurvey.of(b)) if (e.resource() == r) return e;
        return null;
    }

    /**
     * The legend's text: a title, the four bands Rich to Poor with their yield ranges, a line on
     * what the yield means, and where the survey's best spot is.
     */
    static List<String> legendLines(Body b, Resource r, ResourceSurvey.Entry best, boolean bestVisible) {
        String what = r == Resource.BIOMASS ? "farm" : "mine";
        List<String> out = new java.util.ArrayList<>();
        out.add(r + " in the ground");
        out.add("Rich  60% or more");
        out.add("Good  40 to 60%");
        out.add("Fair  20 to 40%");
        out.add("Poor  under 20%");
        out.add("% of a rich site's " + what + " output");
        if (best != null) {
            out.add("\u2605 Best spot " + Math.round(best.best() * 100) + "% at "
                + DetailPanel.degrees(best.bestLat(), best.bestLon())
                + (bestVisible ? "" : " (far side)"));
            if (best.best() < YieldOverlay.BAND_START[0]) out.add("Poor everywhere on " + b.name);
        }
        if (!b.sites.isEmpty()) out.add("\u25CF Colonies");
        return out;
    }

    /** The map the globe is painted from: the surface, or the tinted overlay if one is on. */
    private BufferedImage paintedMap(Body b) {
        Resource r = overlayFor(b);
        if (r == null) return flatMap(b);
        return overlayCache.computeIfAbsent(b.id + "#" + r, k -> YieldOverlay.tint(flatMap(b), b.resourceYields, r));
    }

    private BufferedImage flatMap(Body b) {
        return flatCache.computeIfAbsent(b.id, id ->
            new PlanetGenerator(FLAT_W, FLAT_H).generate(b.surfaceSeed, BodyAppearances.forBody(b)));
    }

    private class SpherePanel extends JPanel {
        SpherePanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(600, 600));
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    mouse = e.getPoint();
                    updateReadout();
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    mouse = null;
                    updateReadout();
                }

                @Override public void mousePressed(MouseEvent e) {
                    Body b = currentBody();
                    if (b == null) return;
                    if (e.isShiftDown() && engine.debugEnabled()) {
                        ObjectInspectorDialog.inspect(SpherePanel.this, engine, b, "Body " + b.id);
                        return;
                    }
                    double[] latLon = latLonAt(e.getX(), e.getY());
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
            BodyAppearance app = BodyAppearances.forBody(b);
            double rotation = rotation();
            BufferedImage sphereImg = SphereRenderer.render(paintedMap(b), size, rotation, tilt, 1.0,
                b.surfaceSeed, app.atmosphereColor());
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g.drawImage(sphereImg, x, y, null);
            Resource r = overlayFor(b);
            if (r == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                ResourceSurvey.Entry best = surveyEntry(b, r);
                for (Site s : b.sites) {
                    double[] p = SphereRenderer.project(s.lat, s.lon, size, rotation, tilt, 1.0);
                    if (p != null) drawSite(g2, x + p[0], y + p[1], s.name);
                }
                boolean bestVisible = false;
                if (best != null) {
                    double[] p = SphereRenderer.project(best.bestLat(), best.bestLon(), size, rotation, tilt, 1.0);
                    if (p != null) {
                        drawStar(g2, x + p[0], y + p[1], Color.WHITE);
                        bestVisible = true;
                    }
                }
                drawLegend(g2, b, r, best, bestVisible);
            } finally {
                g2.dispose();
            }
        }

        private void drawSite(Graphics2D g, double cx, double cy, String name) {
            g.setColor(Color.BLACK);
            g.fill(new java.awt.geom.Ellipse2D.Double(cx - 5, cy - 5, 10, 10));
            g.setColor(Color.WHITE);
            g.fill(new java.awt.geom.Ellipse2D.Double(cx - 3.5, cy - 3.5, 7, 7));
            g.setFont(getFont().deriveFont(Font.BOLD, 11f));
            outlinedText(g, name, (float) cx + 8, (float) cy + 4, Color.WHITE);
        }

        private void drawStar(Graphics2D g, double cx, double cy, Color fill) {
            Path2D star = new Path2D.Double();
            for (int i = 0; i < 10; i++) {
                double a = -Math.PI / 2 + i * Math.PI / 5, rad = i % 2 == 0 ? 11 : 4.5;
                double px = cx + rad * Math.cos(a), py = cy + rad * Math.sin(a);
                if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
            }
            star.closePath();
            g.setColor(fill);
            g.fill(star);
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(star);
        }

        /** Title, the four bands with their yields, what a yield means, and where the best spot is. */
        private void drawLegend(Graphics2D g, Body b, Resource r, ResourceSurvey.Entry best, boolean bestVisible) {
            List<String> lines = legendLines(b, r, best, bestVisible);
            Font body = getFont().deriveFont(Font.PLAIN, 12f), title = getFont().deriveFont(Font.BOLD, 13f);
            FontMetrics fm = g.getFontMetrics(body), tm = g.getFontMetrics(title);
            int lh = fm.getHeight(), sw = 14, pad = 8;
            int width = tm.stringWidth(lines.get(0));
            for (int i = 1; i < lines.size(); i++) width = Math.max(width, fm.stringWidth(lines.get(i)) + (i <= 4 ? sw + 6 : 0));
            int height = pad * 2 + tm.getHeight() + lh * (lines.size() - 1) + 4;
            int x0 = 8, y0 = 8;
            g.setColor(new Color(8, 10, 18, 215));
            g.fillRoundRect(x0, y0, width + pad * 2, height, 10, 10);
            g.setColor(UiColors.PANEL_BORDER);
            g.drawRoundRect(x0, y0, width + pad * 2, height, 10, 10);
            int ty = y0 + pad + tm.getAscent();
            g.setFont(title);
            g.setColor(UiColors.FOREGROUND);
            g.drawString(lines.get(0), x0 + pad, ty);
            ty += tm.getDescent() + 4;
            g.setFont(body);
            for (int i = 1; i < lines.size(); i++) {
                int base = ty + fm.getAscent();
                int tx = x0 + pad;
                if (i <= 4) {
                    int band = 4 - i; // Rich first
                    g.setColor(YieldOverlay.swatch(r, band));
                    g.fillRect(tx, base - fm.getAscent() + 2, sw, sw - 2);
                    g.setColor(UiColors.PANEL_BORDER);
                    g.drawRect(tx, base - fm.getAscent() + 2, sw, sw - 2);
                    tx += sw + 6;
                }
                g.setColor(i <= 4 ? UiColors.FOREGROUND : UiColors.FOREGROUND_DIM);
                g.drawString(lines.get(i), tx, base);
                ty += lh;
            }
        }

        private void outlinedText(Graphics2D g, String text, float tx, float ty, Color c) {
            g.setColor(Color.BLACK);
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) g.drawString(text, tx + dx, ty + dy);
            g.setColor(c);
            g.drawString(text, tx, ty);
        }
    }
}
