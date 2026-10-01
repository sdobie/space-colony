package spacecolony.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Selection;
import spacecolony.render.BodyAppearance;
import spacecolony.render.BodyAppearances;
import spacecolony.render.PlanetGenerator;
import spacecolony.render.SphereRenderer;
import spacecolony.sim.Body;
import spacecolony.sim.Resource;
import spacecolony.sim.Ship;
import spacecolony.sim.ShipState;
import spacecolony.sim.Site;
import spacecolony.sim.Transit;
import spacecolony.sim.World;
import spacecolony.debug.ObjectInspectorDialog;
import spacecolony.sim.economy.BuildingOutcome;
import spacecolony.sim.economy.DayReport;
import spacecolony.sim.economy.Outlook;

/**
 * Left dock: an empire totals strip, colony cards (planet thumbnail, status dot, population, FOOD/WATER gauges) and
 * ship cards (hull icon, where it is or is headed, trip progress or cargo fill) over a faint
 * starfield. Cards read the world when they paint, so a tick only repaints; the card list is
 * rebuilt only when colonies or ships come or go.
 */
public class ColonyListPanel extends JPanel {
    private static final int ICON = 36;
    private static final Color FOOD_BAR  = new Color(120, 200, 130);
    private static final Color WATER_BAR = UiColors.INFO;
    private static final Color BAR_TRACK = new Color(255, 255, 255, 22);
    private static final Color HOVER     = new Color(255, 255, 255, 14);

    private final Engine engine;
    private final JPanel list = new JPanel();
    private final Map<String, BufferedImage> planetIcons = new HashMap<>();
    private final float[][] stars;
    private List<Object> shownKeys = List.of();

    public ColonyListPanel(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.PANEL_BORDER));
        setName(TARGET);
        stars = starField(90, 7L);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        // Pin the cards to the top instead of stretching them over the column.
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldReplaced) { planetIcons.clear(); shownKeys = List.of(); }
            if (e instanceof EngineEvent.WorldChanged
             || e instanceof EngineEvent.WorldReplaced
             || e instanceof EngineEvent.SelectionChanged) refresh();
        });
        refresh();
    }

    /** Component name the tutorial highlights (Plan 6 §5.4). */
    public static final String TARGET = "colonylist";

    private void refresh() {
        World w = engine.world();
        List<Object> keys = new ArrayList<>();
        for (var body : w.bodies) for (Site s : body.sites) keys.add(Selection.site(s.id));
        for (Ship s : w.ships) keys.add(Selection.ship(s.id));
        if (!keys.equals(shownKeys)) {
            shownKeys = keys;
            list.removeAll();
            int colonies = (int) keys.stream().filter(k -> ((Selection) k).kind() == Selection.Kind.SITE).count();
            list.add(new Totals());
            list.add(new Header("Colonies", colonies));
            for (Object k : keys) if (((Selection) k).kind() == Selection.Kind.SITE) list.add(new ColonyCard((Selection) k));
            list.add(new Header("Ships", w.ships.size()));
            for (Object k : keys) if (((Selection) k).kind() == Selection.Kind.SHIP) list.add(new ShipCard((Selection) k));
            list.revalidate();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth(), h = getHeight();
        g2.setPaint(new GradientPaint(0, 0, UiColors.PANEL_BACKGROUND, 0, h, UiColors.STARFIELD_BG));
        g2.fillRect(0, 0, w, h);
        for (float[] s : stars) {
            g2.setColor(new Color(200, 210, 240, (int) s[2]));
            g2.fillRect((int) (s[0] * w), (int) (s[1] * h), 1, 1);
        }
        g2.dispose();
    }

    private static float[][] starField(int n, long seed) {
        Random r = new Random(seed);
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) out[i] = new float[] { r.nextFloat(), r.nextFloat(), 30 + r.nextInt(90) };
        return out;
    }

    /** Debug Shift+click: open the inspector on the row's Site or Ship. */
    private void inspect(Component from, Selection sel) {
        Object target = sel.kind() == Selection.Kind.SITE ? engine.world().findSite(sel.id())
                      : engine.world().findShip(sel.id());
        if (target == null) return;
        String kind = sel.kind() == Selection.Kind.SITE ? "Site " : "Ship ";
        ObjectInspectorDialog.inspect(from, engine, target, kind + sel.id());
    }

    /**
     * Red when FOOD or WATER is gone; amber when either runs out within 10 days or a
     * building sat short of an input yesterday.
     */
    static Color rowColor(Site s) {
        double food = s.stockpile.getOrDefault(Resource.FOOD, 0.0);
        double water = s.stockpile.getOrDefault(Resource.WATER, 0.0);
        if (food < 1e-6 || water < 1e-6) return UiColors.ERROR;
        DayReport d = s.lastDay;
        if (d == null) return UiColors.FOREGROUND;
        if (soon(s, Resource.FOOD, d) || soon(s, Resource.WATER, d)) return UiColors.WARNING;
        if (d.buildings().stream().anyMatch(BuildingOutcome::starved)) return UiColors.WARNING;
        return UiColors.FOREGROUND;
    }

    private static boolean soon(Site s, Resource r, DayReport d) {
        return Outlook.of(s.stockpile.getOrDefault(r, 0.0), s.stockpileCap.getOrDefault(r, 1000.0), d.net(r))
            instanceof Outlook.EmptyIn e && e.days() <= 10;
    }

    /** "Ark  ·", or "Ark  · orbiting Mars" for a colonizer waiting at an unsettled body. */
    static String shipRowText(World w, Ship s) {
        String text = s.name + "  " + stateGlyph(s);
        if (s.orbitingBodyId != null) {
            var b = w.findBody(s.orbitingBodyId);
            text += " orbiting " + (b != null ? b.name : s.orbitingBodyId);
        }
        return text;
    }

    private static String stateGlyph(Ship s) {
        return switch (s.state) {
            case IDLE        -> "·";
            case LOADING     -> "▾";
            case IN_TRANSIT  -> "→";
            case UNLOADING   -> "▴";
        };
    }

    /** Where a ship is or is headed, e.g. "→ Ares Base · 20d" or "Docked at Earth Hub". */
    static String shipStatusText(World w, Ship s) {
        if (s.orbitingBodyId != null) return "Orbiting " + bodyName(w, s.orbitingBodyId);
        Transit t = s.transit;
        return switch (s.state) {
            case IDLE       -> "Docked at " + siteName(w, s.currentSiteId);
            case LOADING    -> "Loading for " + destName(w, t);
            case UNLOADING  -> "Unloading at " + siteName(w, s.currentSiteId);
            case IN_TRANSIT -> "→ " + destName(w, t)
                + (t != null ? " · " + Math.max(0, t.arrivalTick() - w.tick) + "d" : "");
        };
    }

    /** Summed population and FOOD/WATER/FUEL stock and yesterday's net across all colonies. */
    record EmpireTotals(int population, int populationCap, Map<Resource, Double> stock, Map<Resource, Double> net) {
        static final List<Resource> SHOWN = List.of(Resource.FOOD, Resource.WATER, Resource.FUEL);

        static EmpireTotals of(World w) {
            int pop = 0, cap = 0;
            Map<Resource, Double> stock = new java.util.EnumMap<>(Resource.class);
            Map<Resource, Double> net = new java.util.EnumMap<>(Resource.class);
            for (Body b : w.bodies) {
                for (Site s : b.sites) {
                    pop += s.population;
                    cap += s.populationCap;
                    for (Resource r : SHOWN) {
                        stock.merge(r, s.stockpile.getOrDefault(r, 0.0), Double::sum);
                        net.merge(r, s.lastDay == null ? 0.0 : s.lastDay.net(r), Double::sum);
                    }
                }
            }
            return new EmpireTotals(pop, cap, stock, net);
        }
    }

    /** "1.2k" style figures so four tiles fit across the column. */
    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 9_999.5) return String.format("%.0fk", v / 1000);
        if (a >= 999.5) return String.format("%.1fk", v / 1000);
        return String.format("%.0f", v);
    }

    private static String destName(World w, Transit t) {
        if (t == null) return "?";
        return t.destSiteId() != null ? siteName(w, t.destSiteId()) : bodyName(w, t.destBodyId());
    }

    private static String siteName(World w, String id) {
        Site s = id == null ? null : w.findSite(id);
        return s != null ? s.name : String.valueOf(id);
    }

    private static String bodyName(World w, String id) {
        Body b = id == null ? null : w.findBody(id);
        return b != null ? b.name : String.valueOf(id);
    }

    private static double fill(double v, double cap) {
        return cap <= 0 ? 0 : Math.max(0, Math.min(1, v / cap));
    }

    /** Planet thumbnail with a soft round edge, rendered once per body. */
    private BufferedImage planetIcon(Body body) {
        return planetIcons.computeIfAbsent(body.id, id -> {
            int size = ICON * 2;
            BodyAppearance app = BodyAppearances.forBody(body);
            BufferedImage flat = new PlanetGenerator(128, 64).generate(body.surfaceSeed, app);
            BufferedImage sphere = SphereRenderer.render(flat, size, 30.0, 12.0, 1.0,
                body.surfaceSeed, app.atmosphereColor());
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            double c = size / 2.0, edge = size * 0.48;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double d = Math.hypot(x + 0.5 - c, y + 0.5 - c);
                    int a = (int) Math.round(255 * Math.max(0, Math.min(1, edge - d)));
                    if (a > 0) out.setRGB(x, y, (a << 24) | (sphere.getRGB(x, y) & 0xFFFFFF));
                }
            }
            return out;
        });
    }

    private static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    private static String clip(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) return text;
        while (!text.isEmpty() && fm.stringWidth(text + "…") > width) text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    /** Spaced small-caps section title with a count pill and a fading rule. */
    private final class Header extends JComponent {
        private final String title;
        private final int count;

        Header(String title, int count) {
            this.title = title;
            this.count = count;
            setPreferredSize(new Dimension(10, 34));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            Font f = getFont().deriveFont(Font.BOLD, 10.5f)
                .deriveFont(Map.of(TextAttribute.TRACKING, 0.15f));
            g2.setFont(f);
            FontMetrics fm = g2.getFontMetrics();
            int base = 22;
            String text = title.toUpperCase();
            g2.setColor(UiColors.INFO);
            g2.drawString(text, 12, base);
            int x = 12 + fm.stringWidth(text) + 8;
            String n = String.valueOf(count);
            Font nf = getFont().deriveFont(Font.BOLD, 10f);
            g2.setFont(nf);
            FontMetrics nfm = g2.getFontMetrics();
            int pw = nfm.stringWidth(n) + 10;
            g2.setColor(new Color(UiColors.INFO.getRed(), UiColors.INFO.getGreen(), UiColors.INFO.getBlue(), 45));
            g2.fill(new RoundRectangle2D.Double(x, base - 11, pw, 14, 14, 14));
            g2.setColor(UiColors.FOREGROUND);
            g2.drawString(n, x + 5, base);
            int lineX = x + pw + 8;
            g2.setPaint(new GradientPaint(lineX, 0, UiColors.PANEL_BORDER, getWidth() - 8, 0, new Color(0, 0, 0, 0)));
            g2.fillRect(lineX, base - 4, Math.max(0, getWidth() - 8 - lineX), 1);
            g2.dispose();
        }
    }

    /** Empire strip: population and FOOD/WATER/FUEL totals, each with yesterday's net trend. */
    private final class Totals extends JComponent {
        Totals() {
            setPreferredSize(new Dimension(10, 58));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setAlignmentX(LEFT_ALIGNMENT);
            setToolTipText("");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            EmpireTotals t = EmpireTotals.of(engine.world());
            var box = new RoundRectangle2D.Double(6, 8, getWidth() - 12, getHeight() - 12, 10, 10);
            g2.setPaint(new GradientPaint(0, 8, new Color(26, 46, 74, 150), 0, getHeight(), new Color(26, 46, 74, 40)));
            g2.fill(box);
            g2.setColor(UiColors.PANEL_BORDER);
            g2.draw(box);

            int cols = 4, inner = getWidth() - 12;
            Font label = getFont().deriveFont(Font.BOLD, 8.5f).deriveFont(Map.of(TextAttribute.TRACKING, 0.1f));
            Font value = getFont().deriveFont(Font.BOLD, 13f);
            Font sub = getFont().deriveFont(Font.PLAIN, 9f);
            String[] labels = { "POP", "FOOD", "H₂O", "FUEL" };
            for (int i = 0; i < cols; i++) {
                int cx = 6 + inner * i / cols + inner / (2 * cols);
                if (i > 0) {
                    g2.setColor(new Color(255, 255, 255, 18));
                    g2.fillRect(6 + inner * i / cols, 16, 1, getHeight() - 28);
                }
                String v, d;
                Color dc;
                if (i == 0) {
                    v = compact(t.population());
                    d = "of " + compact(t.populationCap());
                    dc = UiColors.FOREGROUND_DIM;
                } else {
                    Resource r = EmpireTotals.SHOWN.get(i - 1);
                    v = compact(t.stock().get(r));
                    double n = t.net().get(r);
                    d = Math.abs(n) < 0.05 ? "steady" : (n > 0 ? "▲ " : "▼ ") + String.format("%.1f", Math.abs(n));
                    dc = Math.abs(n) < 0.05 ? UiColors.FOREGROUND_DIM : n > 0 ? FOOD_BAR : UiColors.ERROR;
                }
                centered(g2, label, UiColors.INFO, labels[i], cx, 22);
                centered(g2, value, UiColors.FOREGROUND, v, cx, 37);
                centered(g2, sub, dc, d, cx, 48);
            }
            g2.dispose();
        }

        private void centered(Graphics2D g, Font f, Color c, String text, int cx, int y) {
            g.setFont(f);
            g.setColor(c);
            g.drawString(text, cx - g.getFontMetrics().stringWidth(text) / 2, y);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            EmpireTotals t = EmpireTotals.of(engine.world());
            return String.format("Population %d / %d · FOOD %.0f (%+.1f/d) · WATER %.0f (%+.1f/d) · FUEL %.0f (%+.1f/d)",
                t.population(), t.populationCap(),
                t.stock().get(Resource.FOOD), t.net().get(Resource.FOOD),
                t.stock().get(Resource.WATER), t.net().get(Resource.WATER),
                t.stock().get(Resource.FUEL), t.net().get(Resource.FUEL));
        }
    }

    /** Clickable card base: selection fill with an accent bar, hover tint, debug inspect. */
    private abstract class Card extends JComponent {
        final Selection sel;
        private boolean hover;

        Card(Selection sel, int height) {
            this.sel = sel;
            setPreferredSize(new Dimension(10, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            setAlignmentX(LEFT_ALIGNMENT);
            setToolTipText("");  // registers with the ToolTipManager; text comes from getToolTipText(e)
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (e.isShiftDown() && engine.debugEnabled()) inspect(Card.this, sel);
                    else engine.setSelection(sel);
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            var shape = new RoundRectangle2D.Double(6, 2, getWidth() - 12, getHeight() - 4, 10, 10);
            if (sel.equals(engine.selection())) {
                g2.setColor(UiColors.SELECTION);
                g2.fill(shape);
                g2.setColor(UiColors.INFO);
                g2.fill(new RoundRectangle2D.Double(6, 8, 3, getHeight() - 16, 3, 3));
            } else if (hover) {
                g2.setColor(HOVER);
                g2.fill(shape);
            }
            paintCard(g2);
            g2.dispose();
        }

        abstract void paintCard(Graphics2D g);

        /** Name line in bold, with an optional dim tag after it. */
        void title(Graphics2D g, String name, Color fg, String tag, int x, int y) {
            Font bold = getFont().deriveFont(Font.BOLD, 12.5f);
            Font small = getFont().deriveFont(Font.PLAIN, 9.5f).deriveFont(Map.of(TextAttribute.TRACKING, 0.1f));
            int room = getWidth() - x - 12;
            int tagW = tag == null ? 0 : g.getFontMetrics(small).stringWidth(tag) + 6;
            g.setFont(bold);
            String n = clip(name, g.getFontMetrics(), room - tagW);
            g.setColor(fg);
            g.drawString(n, x, y);
            if (tag != null) {
                g.setFont(small);
                g.setColor(UiColors.FOREGROUND_DIM);
                g.drawString(tag, x + g.getFontMetrics(bold).stringWidth(n) + 6, y);
            }
        }

        void dim(Graphics2D g, String text, int x, int y) {
            g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
            g.setColor(UiColors.FOREGROUND_DIM);
            g.drawString(clip(text, g.getFontMetrics(), getWidth() - x - 12), x, y);
        }

        static void bar(Graphics2D g, int x, int y, int w, double fill, Color c) {
            g.setColor(BAR_TRACK);
            g.fill(new RoundRectangle2D.Double(x, y, w, 4, 4, 4));
            if (fill <= 0) return;
            g.setColor(c);
            g.fill(new RoundRectangle2D.Double(x, y, Math.max(4, w * fill), 4, 4, 4));
        }
    }

    private final class ColonyCard extends Card {
        ColonyCard(Selection sel) { super(sel, 62); }

        @Override
        void paintCard(Graphics2D g) {
            World w = engine.world();
            Site s = w.findSite(sel.id());
            if (s == null) return;
            Body body = w.findBody(s.bodyId);
            int iy = (getHeight() - ICON) / 2;
            if (body != null) g.drawImage(planetIcon(body), 14, iy, ICON, ICON, null);
            Color status = rowColor(s);
            Color dot = status == UiColors.FOREGROUND ? FOOD_BAR : status;
            g.setColor(UiColors.STARFIELD_BG);
            g.fill(new Ellipse2D.Double(14 + ICON - 11, iy + ICON - 11, 12, 12));
            g.setColor(dot);
            g.fill(new Ellipse2D.Double(14 + ICON - 9, iy + ICON - 9, 8, 8));

            int x = 14 + ICON + 10;
            title(g, s.name, status, null, x, 20);
            dim(g, (body != null ? body.name : s.bodyId) + " · pop " + s.population + "/" + s.populationCap, x, 35);

            Font tiny = getFont().deriveFont(Font.BOLD, 8.5f);
            g.setFont(tiny);
            FontMetrics fm = g.getFontMetrics();
            int half = (getWidth() - x - 12 - 6) / 2;
            gauge(g, fm, "FOOD", s, Resource.FOOD, FOOD_BAR, x, 49, half);
            gauge(g, fm, "H₂O", s, Resource.WATER, WATER_BAR, x + half + 6, 49, half);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            Site s = engine.world().findSite(sel.id());
            return s == null ? null : String.format("FOOD %.0f / %.0f · WATER %.0f / %.0f",
                s.stockpile.getOrDefault(Resource.FOOD, 0.0), s.stockpileCap.getOrDefault(Resource.FOOD, 0.0),
                s.stockpile.getOrDefault(Resource.WATER, 0.0), s.stockpileCap.getOrDefault(Resource.WATER, 0.0));
        }

        private void gauge(Graphics2D g, FontMetrics fm, String label, Site s, Resource r, Color c, int x, int y, int w) {
            g.setColor(UiColors.FOREGROUND_DIM);
            g.drawString(label, x, y + 5);
            int lw = fm.stringWidth(label) + 4;
            double f = fill(s.stockpile.getOrDefault(r, 0.0), s.stockpileCap.getOrDefault(r, 1000.0));
            bar(g, x + lw, y, w - lw, f, f < 0.1 ? UiColors.WARNING : c);
        }
    }

    private final class ShipCard extends Card {
        ShipCard(Selection sel) { super(sel, 54); }

        @Override
        void paintCard(Graphics2D g) {
            World w = engine.world();
            Ship s = w.findShip(sel.id());
            if (s == null) return;
            int iy = (getHeight() - ICON) / 2;
            Color ring = switch (s.state) {
                case IN_TRANSIT -> UiColors.INFO;
                case LOADING, UNLOADING -> UiColors.WARNING;
                case IDLE -> UiColors.PANEL_BORDER.brighter();
            };
            g.setColor(UiColors.STARFIELD_BG);
            g.fill(new Ellipse2D.Double(14, iy, ICON, ICON));
            g.setColor(ring);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(14.75, iy + 0.75, ICON - 1.5, ICON - 1.5));
            hull(g, s, 14 + ICON / 2.0, iy + ICON / 2.0);

            int x = 14 + ICON + 10;
            title(g, s.name, UiColors.FOREGROUND, s.shipClass.name(), x, 19);
            dim(g, shipStatusText(w, s), x, 34);
            int bw = getWidth() - x - 12;
            Transit t = s.transit;
            if (s.state == ShipState.IN_TRANSIT && t != null && t.arrivalTick() > t.departureTick()) {
                double p = (double) (w.tick - t.departureTick()) / (t.arrivalTick() - t.departureTick());
                bar(g, x, 42, bw, Math.max(0, Math.min(1, p)), UiColors.INFO);
            } else if (s.cargoMass() > 1e-6) {
                bar(g, x, 42, bw, fill(s.cargoMass(), s.shipClass.cargoCap()), UiColors.SHIP_DOT);
            }
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            Ship s = engine.world().findShip(sel.id());
            return s == null ? null : shipRowText(engine.world(), s) + String.format("  ·  cargo %.0f / %.0f · fuel %.0f",
                s.cargoMass(), s.shipClass.cargoCap(), s.fuel);
        }

        /** Small top-down silhouette per ship class, nose pointing right. */
        private void hull(Graphics2D g, Ship s, double cx, double cy) {
            Graphics2D h = (Graphics2D) g.create();
            h.translate(cx, cy);
            h.rotate(-Math.PI / 4);
            h.setColor(UiColors.SHIP_DOT);
            switch (s.shipClass) {
                case HAULER -> {
                    Path2D p = new Path2D.Double();
                    p.moveTo(10, 0); p.lineTo(4, -5); p.lineTo(-9, -5); p.lineTo(-9, 5); p.lineTo(4, 5);
                    p.closePath();
                    h.fill(p);
                    h.setColor(UiColors.STARFIELD_BG);
                    h.fillRect(-6, -3, 3, 6);
                    h.fillRect(-1, -3, 3, 6);
                }
                case TANKER -> {
                    h.fill(new RoundRectangle2D.Double(-10, -4.5, 18, 9, 9, 9));
                    Path2D nose = new Path2D.Double();
                    nose.moveTo(7, -2.5); nose.lineTo(11, 0); nose.lineTo(7, 2.5); nose.closePath();
                    h.fill(nose);
                    h.setColor(UiColors.STARFIELD_BG);
                    h.fillRect(-3, -4, 1, 8);
                    h.fillRect(2, -4, 1, 8);
                }
                case COLONIZER -> {
                    h.setStroke(new BasicStroke(2f));
                    h.draw(new Ellipse2D.Double(-8, -8, 12, 12));
                    h.fillRect(2, -1, 9, 2);
                    h.fill(new Ellipse2D.Double(-4.5, -4.5, 5, 5));
                }
            }
            if (s.state == ShipState.IN_TRANSIT) {
                h.setComposite(AlphaComposite.SrcOver.derive(0.7f));
                h.setColor(UiColors.INFO);
                h.fill(new Ellipse2D.Double(-14, -2, 4, 4));
            }
            h.dispose();
        }
    }
}
