package com.frameboost.ragdoll;
import android.graphics.Canvas; import java.awt.image.BufferedImage; import javax.imageio.ImageIO; import java.io.File;
public class IconTest { public static void main(String[] a) throws Exception {
    Game g = new Game(null, new File("/tmp/rpg-mods")); g.resize(2400, 1080, 2.75f);
    g.menuAction(Screens.A_SANDBOX); g.screens.screen = Screens.NONE;
    for (int cat : new int[]{0, 2}) {
        g.ui.cat = cat; g.ui.stripOpen = true; g.update(1f/60);
        BufferedImage img = new BufferedImage(2400, 1080, BufferedImage.TYPE_INT_RGB);
        g.draw(new Canvas(img)); ImageIO.write(img.getSubimage(0, 1080 - 400, 2400, 400), "png", new File(a[0] + "/icons" + cat + ".png"));
    }
}}
