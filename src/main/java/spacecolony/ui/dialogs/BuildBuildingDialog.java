package spacecolony.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import spacecolony.engine.Engine;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingSpec;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Site;
import spacecolony.sim.commands.BuildBuildingCommand;
import spacecolony.sim.economy.BuildForecast;
import spacecolony.ui.BuildingInfoPanel;
import spacecolony.ui.UiColors;

/** Pick a building, see what it does and what it would do at this colony, then build it. */
public class BuildBuildingDialog {
    public static final String LIST = "build.list";
    public static final String OK = "build.ok";
    public static final String CANCEL = "build.cancel";
    public static final String REASON = "build.reason";
    public static final String OK_LABEL = "Build";

    public static void show(Component parent, Engine engine, String siteId) {
        JDialog d = create(parent, engine, siteId);
        if (d != null) d.setVisible(true);
    }

    /** The dialog, built but not shown; null when the site is gone. */
    public static JDialog create(Component parent, Engine engine, String siteId) {
        Site site = engine.world().findSite(siteId);
        if (site == null) return null;
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (owner == null && parent instanceof Window w) owner = w;
        JDialog dialog = new JDialog(owner, "Build at " + site.name, JDialog.DEFAULT_MODALITY_TYPE);

        // Forecasts for every type, so the list can flag the ones with warnings.
        Map<BuildingType, BuildForecast> forecasts = new EnumMap<>(BuildingType.class);
        for (BuildingType t : BuildingType.values()) forecasts.put(t, BuildForecast.of(engine.world(), site, t));

        JList<BuildingSpec> list = new JList<>(BuildingCatalog.all().toArray(new BuildingSpec[0]));
        list.setName(LIST);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(UiColors.PANEL_BACKGROUND);
        list.setForeground(UiColors.FOREGROUND);
        list.setSelectionBackground(UiColors.SELECTION);
        list.setSelectionForeground(UiColors.FOREGROUND);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                                    boolean selected, boolean focus) {
                BuildingSpec spec = (BuildingSpec) value;
                BuildForecast f = forecasts.get(spec.type());
                String text = spec.displayName() + (f.warnings().isEmpty() ? "" : "  ⚠");
                super.getListCellRendererComponent(l, text, index, selected, focus);
                // Entries that can't be built right now are drawn dim.
                setForeground(f.blocker() == null ? UiColors.FOREGROUND : UiColors.FOREGROUND_DIM);
                setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
                return this;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(170, 360));
        listScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiColors.PANEL_BORDER));

        BuildingInfoPanel info = new BuildingInfoPanel();
        JScrollPane infoScroll = new JScrollPane(info,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        infoScroll.setBorder(null);
        infoScroll.getViewport().setBackground(UiColors.PANEL_BACKGROUND);
        infoScroll.setPreferredSize(new Dimension(540, 360));

        JButton ok = new JButton(OK_LABEL);
        ok.setName(OK);
        JLabel reason = new JLabel(" ");
        reason.setName(REASON);
        reason.setForeground(UiColors.WARNING);
        list.addListSelectionListener(e -> {
            BuildingSpec spec = list.getSelectedValue();
            if (spec == null) return;
            // Fresh forecast: the game may have ticked since the dialog opened.
            BuildForecast f = BuildForecast.of(engine.world(), site, spec.type());
            forecasts.put(spec.type(), f);
            info.show(f, site, engine.world().tech);
            // Build waits on stock and slots; say why when it can't.
            ok.setEnabled(f.blocker() == null);
            ok.setToolTipText(f.blocker());
            reason.setText(f.blocker() == null ? " " : f.blocker());
        });

        JButton cancel = new JButton("Cancel");
        cancel.setName(CANCEL);
        Runnable build = () -> {
            BuildingSpec spec = list.getSelectedValue();
            if (spec == null || !ok.isEnabled()) return;
            engine.enqueue(new BuildBuildingCommand(siteId, spec.type()));
            dialog.dispose();
        };
        ok.addActionListener(e -> build.run());
        cancel.addActionListener(e -> dialog.dispose());
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) build.run();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(reason);
        buttons.add(cancel);
        buttons.add(ok);

        JPanel root = new JPanel(new BorderLayout());
        root.add(listScroll, BorderLayout.WEST);
        root.add(infoScroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        dialog.getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { dialog.dispose(); }
        });
        list.setSelectedIndex(0);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }
}
