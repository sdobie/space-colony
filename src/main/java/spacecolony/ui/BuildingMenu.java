package spacecolony.ui;

import java.awt.Component;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import spacecolony.engine.Engine;
import spacecolony.sim.BuildCost;
import spacecolony.sim.Building;
import spacecolony.sim.BuildingCatalog;
import spacecolony.sim.BuildingType;
import spacecolony.sim.Construction;
import spacecolony.sim.Resource;
import spacecolony.sim.Site;
import spacecolony.sim.commands.CancelConstructionCommand;
import spacecolony.sim.commands.DemolishBuildingCommand;
import spacecolony.sim.commands.RepairBuildingCommand;
import spacecolony.sim.commands.UpgradeBuildingCommand;
import spacecolony.sim.economy.BuildForecast;

/**
 * The menu behind each building row's ⋯ button (Plan 8 §5.2): Upgrade, Repair, Cancel
 * construction and Demolish, each with its cost or refund. Items that can't be used now are
 * disabled with the reason as their tooltip.
 */
public final class BuildingMenu {
    public static final String UPGRADE = "building.upgrade";
    public static final String REPAIR = "building.repair";
    public static final String CANCEL = "building.cancel";
    public static final String DEMOLISH = "building.demolish";

    /** Asks before demolishing; tests replace it. */
    static BiPredicate<Component, String> confirm = (parent, message) ->
        JOptionPane.showConfirmDialog(parent, message, "Demolish", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;

    private BuildingMenu() {}

    public static JPopupMenu create(Engine engine, Site s, Building b) {
        JPopupMenu menu = new JPopupMenu();
        String name = BuildingCatalog.displayName(b.type);
        if (b.level >= 1 && !b.isUnderConstruction()) {
            String text = b.level >= Construction.MAX_LEVEL ? "Upgrade"
                : "Upgrade to L" + (b.level + 1) + " · " + Construction.upgradeCost(b.type, b.level + 1).describe();
            String why = Construction.whyNotUpgrade(s, b);
            JMenuItem item = item(menu, UPGRADE, text, why,
                () -> engine.enqueue(new UpgradeBuildingCommand(s.id, b.id)));
            if (why == null) item.setToolTipText(upgradePreview(engine, s, b));
        }
        if (!b.enabled && b.type != BuildingType.POWER_PLANT) {
            item(menu, REPAIR, "Repair · " + Construction.repairCost(b.type).describe(),
                Construction.whyNotRepair(s, b), () -> engine.enqueue(new RepairBuildingCommand(s.id, b.id)));
        }
        if (b.isUnderConstruction()) {
            String what = b.level == 0 ? "Cancel construction" : "Cancel upgrade";
            item(menu, CANCEL, what + " · refund " + BuildCost.describe(Construction.cancelRefund(b)), null,
                () -> engine.enqueue(new CancelConstructionCommand(s.id, b.id)));
        } else {
            Map<Resource, Double> refund = Construction.demolishRefund(b);
            item(menu, DEMOLISH, "Demolish… · refund " + BuildCost.describe(refund), Construction.whyNotDemolish(b),
                () -> {
                    String msg = "Demolish " + name + " L" + b.level + " at " + s.name + "?\nRefund: "
                        + BuildCost.describe(refund) + "." + lostNote(s, refund);
                    if (confirm.test(menu.getInvoker(), msg)) engine.enqueue(new DemolishBuildingCommand(s.id, b.id));
                });
        }
        return menu;
    }

    /** " 20 METAL would be lost: storage full." when the refund won't fit; "" otherwise. */
    static String lostNote(Site s, Map<Resource, Double> refund) {
        Map<Resource, Double> lost = new EnumMap<>(Resource.class);
        for (var e : refund.entrySet()) {
            double room = s.stockpileCap.getOrDefault(e.getKey(), 1000.0) - s.stockpile.getOrDefault(e.getKey(), 0.0);
            if (e.getValue() > room + 1e-9) lost.put(e.getKey(), e.getValue() - Math.max(0.0, room));
        }
        return lost.isEmpty() ? "" : "\n" + BuildCost.describe(lost) + " would be lost: storage full.";
    }

    /** "FOOD +1.5 a day · power 10.0 made / 12.0 used", from the upgrade forecast. */
    static String upgradePreview(Engine engine, Site s, Building b) {
        BuildForecast f = BuildForecast.ofUpgrade(engine.world(), s, b.id);
        List<String> parts = new ArrayList<>();
        for (Resource r : Resource.values()) {
            if (!r.isStockpileable()) continue;
            double d = f.delta(r);
            if (Math.abs(d) >= 0.05) parts.add(String.format("%s %+.1f a day", r, d));
        }
        parts.addAll(f.facts());
        parts.add(String.format("power %.1f made / %.1f used", f.after().powerMade, f.after().powerUsed));
        return String.join(" · ", parts);
    }

    private static JMenuItem item(JPopupMenu menu, String name, String text, String whyNot, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setName(name);
        item.setEnabled(whyNot == null);
        if (whyNot != null) item.setToolTipText(whyNot);
        item.addActionListener(e -> action.run());
        menu.add(item);
        return item;
    }
}
