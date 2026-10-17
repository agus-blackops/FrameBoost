package com.frameboost.ragdoll;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.imageio.ImageIO;

/** Runs the real PC build on a (virtual) display and drives it with the mouse and keyboard. */
public class PcSmoke {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static Robot r;
    static String out;
    static void shot(String name) throws Exception {
        ImageIO.write(r.createScreenCapture(new Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize())), "png", new File(out, name));
        System.out.println("wrote " + name);
    }
    static java.awt.Point origin() throws Exception { return frameView.getLocationOnScreen(); }
    static java.awt.Component frameView;
    static void click(float x, float y) throws Exception { java.awt.Point o = origin(); float k = PcMain.currentScale; r.mouseMove(o.x + (int) (x / k), o.y + (int) (y / k)); r.delay(60); r.mousePress(InputEvent.BUTTON1_DOWN_MASK); r.delay(60); r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); r.delay(200); }
    static void key(int k) { r.keyPress(k); r.delay(40); r.keyRelease(k); r.delay(150); }

    public static void main(String[] a) throws Exception {
        out = a[0];
        new Thread(new Runnable() { @Override public void run() { PcMain.main(new String[0]); } }).start();
        r = new Robot();
        for (int i = 0; i < 100 && PcMain.current == null; i++) Thread.sleep(100);
        Game g = PcMain.current;
        check("the window starts", g != null);
        Thread.sleep(3000);
        for (java.awt.Window w : java.awt.Window.getWindows()) if (w.isVisible() && w instanceof javax.swing.JFrame) frameView = ((javax.swing.JFrame) w).getContentPane().getComponent(0);
        check("the game is sized to the window", g.sw > 600 && g.sh > 300);
        shot("pc_title.png");
        synchronized (g) { g.settings.tutorialDone = true; g.menuAction(Screens.A_SANDBOX); }
        r.delay(800);
        check("sandbox opens", g.screens.screen == Screens.NONE);
        // open the people category with the number key and pick the first card with the mouse
        int people = -1; for (int i = 0; i < g.ui.cats.length; i++) if (g.ui.cats[i] == 1) people = i;
        synchronized (g) { g.ui.stripOpen = false; }
        int before = 0; for (Ent e : g.world.ents) if (e instanceof Person) before++;
        key(KeyEvent.VK_1 + people);
        check("number key opens a category", g.ui.stripOpen && g.ui.cat == 1);
        click(g.ui.cardX(0) + g.ui.cardW / 2, g.ui.cardY(0) + g.ui.cardH / 2);
        check("clicking a card picks it", g.tool == Items.HUMAN);
        float fx = g.sw * 0.6f, fy = (g.floorY() - 120 - g.camY) * g.zoom + g.sh / 2f;
        click(fx, fy);
        click(fx + 160, fy);
        r.delay(1500);
        int n = 0; for (Ent e : g.world.ents) if (e instanceof Person) n++;
        check("clicks place people", n == before + 2);
        // drag one with the mouse (the hand is always active)
        Person p = null; for (Ent e : g.world.ents) if (e instanceof Person) p = (Person) e;
        float k = PcMain.currentScale; float px = ((p.rb[Person.CHEST].x - g.camX) * g.zoom + g.sw / 2f) / k, py = ((p.rb[Person.CHEST].y - g.camY) * g.zoom + g.sh / 2f) / k;
        float y0 = p.rb[Person.CHEST].y;
        java.awt.Point o = origin();
        r.mouseMove(o.x + (int) px, o.y + (int) py); r.delay(80);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 15; i++) { r.mouseMove(o.x + (int) px, o.y + (int) (py - i * 14)); r.delay(25); }
        r.delay(200);
        float lifted = y0 - p.rb[Person.CHEST].y;
        shot("pc_drag.png");
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        System.out.println("   lifted " + (int) lifted);
        check("mouse drag grabs a person", lifted > 60f);
        // wheel zoom and right-drag pan
        float z0 = g.zoom;
        r.mouseMove(o.x + 400, o.y + 300); r.mouseWheel(-3); r.delay(300);
        check("wheel zooms in", g.zoom > z0 * 1.2f);
        float cx0 = g.camX;
        r.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        for (int i = 1; i <= 10; i++) { r.mouseMove(o.x + 400 + i * 20, o.y + 300); r.delay(20); }
        r.mouseRelease(InputEvent.BUTTON3_DOWN_MASK); r.delay(200);
        check("right drag pans", g.camX < cx0 - 50f);
        // keyboard: pause, escape menu
        key(KeyEvent.VK_SPACE);
        check("space pauses", g.paused);
        key(KeyEvent.VK_SPACE);
        key(KeyEvent.VK_ESCAPE);
        check("escape opens the menu", g.screens.screen == Screens.PAUSE);
        shot("pc_menu.png");
        key(KeyEvent.VK_ESCAPE);
        check("escape again resumes", g.screens.screen == Screens.NONE);
        // a blast, rendered for a while (the loop keeps running)
        synchronized (g) { g.world.explode(g.world.ents.get(0).cx(), g.floorY() - 40, 330, 1900, 95, true); }
        r.delay(500);
        shot("pc_boom.png");
        System.out.println("   render scale " + PcMain.currentScale);
        System.out.println(fails == 0 ? "PC ALL PASS" : "PC FAILURES " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
