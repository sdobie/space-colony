package spacecolony.render;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import spacecolony.sim.BodyType;

/**
 * Generates one flat-map PNG plus one rendered-sphere PNG per BodyType and writes them to
 * a directory. Useful for visually inspecting the renderer output without launching the UI.
 *
 * Usage: ./gradlew render-demo --args="--seed 42 --out /tmp/space-colony-render"
 */
public class RenderDemo {
    public static void main(String[] args) throws IOException {
        long seed = 42L;
        String out = "/tmp/space-colony-render";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--seed") && i + 1 < args.length) seed = Long.parseLong(args[i + 1]);
            if (args[i].equals("--out") && i + 1 < args.length) out = args[i + 1];
        }
        File outDir = new File(out);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Could not create output dir: " + outDir);
        }
        PlanetGenerator gen = new PlanetGenerator(1024, 512);
        for (BodyType type : BodyType.values()) {
            BodyAppearance app = BodyAppearances.defaultFor(type);
            BufferedImage flat = gen.generate(seed, app);
            File flatFile = new File(outDir, type.name().toLowerCase() + "-flat.png");
            ImageIO.write(flat, "png", flatFile);
            BufferedImage sphere = SphereRenderer.render(flat, 512, 30.0, 15.0, 1.0, seed, app.atmosphereColor());
            File sphereFile = new File(outDir, type.name().toLowerCase() + "-sphere.png");
            ImageIO.write(sphere, "png", sphereFile);
            System.out.printf("%s: %s, %s%n", type.name(), flatFile.getPath(), sphereFile.getPath());
        }
    }
}
