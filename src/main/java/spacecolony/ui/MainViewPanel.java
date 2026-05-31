package spacecolony.ui;

import java.awt.CardLayout;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;

/** CardLayout host that swaps between SystemMapPanel and BodyViewPanel. */
public class MainViewPanel extends JPanel {
    private static final String SYSTEM = "system";
    private static final String BODY   = "body";
    private final CardLayout cards = new CardLayout();
    private final SystemMapPanel systemMap;
    private final BodyViewPanel bodyView;

    public MainViewPanel(Engine engine) {
        setLayout(cards);
        setBackground(UiColors.BACKGROUND);
        this.systemMap = new SystemMapPanel(engine);
        this.bodyView = new BodyViewPanel(engine);
        add(systemMap, SYSTEM);
        add(bodyView, BODY);
        engine.addListener(e -> {
            if (e instanceof EngineEvent.ViewChanged vc) {
                cards.show(this, vc.view() == EngineEvent.ViewChanged.View.BODY_VIEW ? BODY : SYSTEM);
            }
        });
    }
}
