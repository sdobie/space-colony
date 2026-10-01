package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
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
            if (item == null || SURFACE.equals(item) || UNSURVEYED.equals(item)) chosenOverlay.remove(b.id);
            else chosenOverlay.put(b.id, Resource.valueOf((String) item));
            updateReadout();
            sphere.repaint();
        });
        JLabel overlayLabel = new JLabel("Overlay:");
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
            overlayPicker.setToolTipText("Tint the globe by where a resource is rich");
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
        for (int i = 0; i < here.size(); i++)
            sb.append(i == 0 ? " " : " · ").append(ResourceSurvey.abbrev(here.get(i).resource()))
              .append(' ').append(ResourceSurvey.fmt(here.get(i).best()));
        if (here.isEmpty()) sb.append(" nothing to mine or farm");
        return sb.toString();
    }

    /** The globe's (lat, lon) under a point in the sphere panel, or null off the globe. */
    private double[] latLonAt(int x, int y) {
        int size = Math.min(sphere.getWidth(), sphere.getHeight()) - 40;
        if (size < 64) return null;
        int px = x - (sphere.getWidth() - size) / 2;
        int py = y - (sphere.getHeight() - size) / 2;
        if (px < 0 || py < 0 || px >= size || py >= size) return null;
        double rotation = (engine.world().tick % 360) * 1.0;
        return SphereRenderer.unproject(px, py, size, rotation, 12.0, 1.0);
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
            double rotation = (engine.world().tick % 360) * 1.0;
            BufferedImage sphereImg = SphereRenderer.render(paintedMap(b), size, rotation, 12.0, 1.0,
                b.surfaceSeed, app.atmosphereColor());
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;
            g.drawImage(sphereImg, x, y, null);
        }
    }
}
