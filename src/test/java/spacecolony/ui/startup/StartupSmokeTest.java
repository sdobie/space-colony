package spacecolony.ui.startup;

import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import spacecolony.options.Options;
import spacecolony.testutil.Edt;
import spacecolony.ui.UiColors;
import static org.junit.jupiter.api.Assertions.*;

class StartupSmokeTest {
    @Test void starfield_isDeterministic_andNotEmpty() {
        BufferedImage a = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        BufferedImage b = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        new Starfield(7L).paint(a.createGraphics(), 320, 180, 0);
        new Starfield(7L).paint(b.createGraphics(), 320, 180, 0);
        int bg = UiColors.STARFIELD_BG.getRGB(), lit = 0;
        for (int y = 0; y < 180; y++) for (int x = 0; x < 320; x++) {
            assertEquals(a.getRGB(x, y), b.getRGB(x, y));
            if (a.getRGB(x, y) != bg) lit++;
        }
        assertTrue(lit > 50, "lit pixels: " + lit);
    }

    @Test void splashContent_paints() throws Exception {
        Edt.run(() -> {
            SplashWindow.Content c = new SplashWindow.Content("0.6.0");
            c.setStatus("Ready", 1.0);
            c.setSize(SplashWindow.W, SplashWindow.H);
            BufferedImage img = new BufferedImage(SplashWindow.W, SplashWindow.H, BufferedImage.TYPE_INT_RGB);
            assertDoesNotThrow(() -> c.paint(img.createGraphics()));
            assertEquals("Ready", c.status());
        });
    }

    @Test void titleScreen_buildsAndReports() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display (run under xvfb-run)");
        List<TitleScreen.Action> clicked = new ArrayList<>();
        Edt.run(() -> {
            TitleScreen t = new TitleScreen("0.6.0", clicked::add);
            t.update(Options.DEFAULTS, List.of(), true, Instant.now());
            assertFalse(t.button(TitleScreen.Action.CONTINUE).isEnabled());
            assertTrue(t.bannerVisible());
            assertSame(t.button(TitleScreen.Action.TUTORIAL), t.getRootPane().getDefaultButton());
            t.button(TitleScreen.Action.OPTIONS).doClick();
            BufferedImage img = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
            t.getContentPane().setSize(1280, 800);
            t.getContentPane().doLayout();
            assertDoesNotThrow(() -> t.getContentPane().paint(img.createGraphics()));
            t.dispose();
        });
        assertEquals(List.of(TitleScreen.Action.OPTIONS), clicked);
    }
}
