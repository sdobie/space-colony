package spacecolony.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import spacecolony.engine.Engine;
import spacecolony.engine.EngineEvent;
import spacecolony.engine.Speed;

public class TopBar extends JPanel {
    private final Engine engine;
    private final JLabel tickLabel = new JLabel();
    private final JLabel creditsLabel = new JLabel();
    private final JButton pauseBtn = speedButton("⏸", Speed.PAUSED);
    private final JButton x1Btn = speedButton("1×", Speed.X1);
    private final JButton x4Btn = speedButton("4×", Speed.X4);
    private final JButton x16Btn = speedButton("16×", Speed.X16);

    public TopBar(Engine engine) {
        this.engine = engine;
        setLayout(new BorderLayout());
        setBackground(UiColors.PANEL_BACKGROUND);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiColors.PANEL_BORDER));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        left.setOpaque(false);
        tickLabel.setForeground(UiColors.FOREGROUND);
        creditsLabel.setForeground(UiColors.FOREGROUND);
        left.add(tickLabel);
        left.add(creditsLabel);

        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        center.setOpaque(false);
        center.add(pauseBtn);
        center.add(x1Btn);
        center.add(x4Btn);
        center.add(x16Btn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        right.setOpaque(false);
        right.add(menuButton("Tech",  () -> { /* Task 25 wires this */ }));
        right.add(menuButton("Goals", () -> { /* Task 26 wires this */ }));

        add(left,   BorderLayout.WEST);
        add(center, BorderLayout.CENTER);
        add(right,  BorderLayout.EAST);

        engine.addListener(e -> {
            if (e instanceof EngineEvent.WorldChanged || e instanceof EngineEvent.SpeedChanged) {
                refresh();
            }
        });
        refresh();
    }

    private JButton speedButton(String label, Speed s) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.addActionListener(e -> engine.setSpeed(s));
        return b;
    }

    private JButton menuButton(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void refresh() {
        long tick = engine.world().tick;
        long year = tick / 365;
        long day = tick % 365 + 1;
        tickLabel.setText(String.format("Tick %d  ·  Y%d D%d", tick, year, day));
        creditsLabel.setText("Credits: " + engine.world().credits);
        Speed sp = engine.speed();
        pauseBtn.setEnabled(sp != Speed.PAUSED);
        x1Btn.setEnabled(sp != Speed.X1);
        x4Btn.setEnabled(sp != Speed.X4);
        x16Btn.setEnabled(sp != Speed.X16);
    }
}
