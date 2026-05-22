package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import spacecolony.engine.Engine;
import spacecolony.engine.GameLoop;

/** Top-level Swing window. Owns the engine + game loop and wires the 5-region layout. */
public class SpaceColonyFrame extends JFrame {
    private final Engine engine;
    private final GameLoop gameLoop;

    public SpaceColonyFrame(Engine engine) {
        super("Space Colony");
        this.engine = engine;
        this.gameLoop = new GameLoop(engine);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1280, 800));
        setLayout(new BorderLayout());
        getContentPane().setBackground(UiColors.BACKGROUND);

        add(new TopBar(engine), BorderLayout.NORTH);
        ColonyListPanel colonyList = new ColonyListPanel(engine);
        colonyList.setPreferredSize(new Dimension(220, 0));
        add(colonyList, BorderLayout.WEST);
        add(new MainViewPanel(engine), BorderLayout.CENTER);
        add(placeholder("DetailPanel (Task 18)", new Dimension(280, 0)), BorderLayout.EAST);
        add(new EventStripPanel(engine), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private static JPanel placeholder(String text, Dimension preferred) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiColors.PANEL_BACKGROUND);
        p.setBorder(BorderFactory.createLineBorder(UiColors.PANEL_BORDER));
        p.setPreferredSize(preferred);
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setForeground(UiColors.FOREGROUND_DIM);
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    public Engine engine() { return engine; }
    public GameLoop gameLoop() { return gameLoop; }
}
