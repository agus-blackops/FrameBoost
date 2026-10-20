using System;

namespace Trastazo
{
    /** 4.0 beta robustness: bug reports, error recovery, safe saves (hand port of Test40.java). */
    public class Test40
    {
        static int fails;
        static void check(string n, bool ok) { Console.WriteLine((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }

        sealed class Odd : Ent
        {
            public override float cx() { return 0; }
            public override float cy() { return 0; }
        }

        public static void RunMain(string[] a)
        {
            File dir = new File(System.IO.Path.Combine(System.IO.Path.GetTempPath(), "trz40_" + Guid.NewGuid().ToString("N")));
            dir.mkdirs();
            // 1. an unhandled error leaves a report and the next launch knows about it
            Report.install(dir, "Test");
            check("a clean start has no crash note", Report.lastCrash == null);
            Report.crashed("prueba", new InvalidOperationException("prueba de cierre"));
            File rep = Report.file();
            string txt = rep.readAllText();
            check("the report has version, platform and error", txt.contains("Trastazo " + Report.VERSION) && txt.contains("Test") && txt.contains("prueba de cierre"));
            Report.install(dir, "Test");
            check("the next launch is told about the crash", Report.lastCrash != null);
            Report.install(dir, "Test");
            check("and only once", Report.lastCrash == null);
            // 2. errors the game survives are logged and capped
            for (int i = 0; i < 40; i++) Report.recovered("prueba " + i, new Exception("x" + i));
            string txt2 = rep.readAllText();
            int entries = txt2.split("Error recuperado").Length - 1;
            check("recovered errors are logged but capped", entries >= 1 && entries <= 20);
            // 3. a bug inside the simulation does not kill the game
            Game g = Harness.newGame();
            g.world.spawn(Items.HUMAN, 1400, -120, 1, 1f);
            g.world.ents.add(new Odd());
            bool threw = false;
            try { for (int i = 0; i < 25; i++) g.update(1f / 60f); } catch (Exception) { threw = true; }
            check("an error in the world does not propagate", !threw);
            check("after repeated errors the scene is cleared and play continues", g.world.ents.isEmpty());
            g.world.spawn(Items.CRATE, 1400, -120, 1, 1f);
            try { for (int i = 0; i < 60; i++) g.update(1f / 60f); } catch (Exception) { threw = true; }
            check("the game keeps working afterwards", !threw && g.world.ents.size() == 1);
            // 4. saves are written safely
            g.screens.screen = Screens.NONE;
            File sf = new File(dir, "save.trz");
            g.saveFile = sf;
            g.saveScene();
            check("the save exists and no temporary file is left", sf.exists() && !new File(dir, "save.trz.tmp").exists());
            g.world.spawn(Items.BARREL, 1600, -100, 1, 1f);
            g.saveScene();
            Game g2 = Harness.newGame();
            check("a second save replaces the first and loads", SceneIO.load(g2, sf) && g2.world.ents.size() == 2);
            // 5. loose guns are never drawn upside down
            World w = new World(null);
            Prop gun = (Prop)w.spawn(Items.GUN_RIFLE, 1500, -100, 1, 1f);
            gun.main().setAngle(3.1f);
            Renderer r = new Renderer();
            BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
            try { r.draw(new CanvasDraw(img.canvas()), w, 1500, -100, 1f, 400, 300); check("a gun pointing left draws", true); } catch (Exception) { check("a gun pointing left draws", false); }
            // 6. the top buttons never hide behind the open catalog, on any screen
            int[][] sizes = { new[] { 800, 480 }, new[] { 960, 540 }, new[] { 1024, 768 }, new[] { 1280, 720 }, new[] { 1920, 1080 }, new[] { 2400, 1080 }, new[] { 2560, 1600 } };
            float[] dens = { 1.5f, 1.7f, 2f, 2f, 2.7f, 2.75f, 3f };
            bool fit = true;
            for (int i = 0; i < sizes.Length; i++)
            {
                Game q = new Game(null);
                q.resize(sizes[i][0], sizes[i][1], dens[i]);
                q.ui.stripOpen = true;
                q.ui.placeButtons();
                float left = 1e9f;
                for (int b = 0; b < Ui.BTN_COUNT; b++) left = Math.min(left, q.ui.btnLeft(b));
                if (left < q.ui.leftW() - 0.5f) { fit = false; Console.WriteLine("   overlap at " + sizes[i][0] + "x" + sizes[i][1] + ": " + left + " < " + q.ui.leftW()); }
            }
            check("buttons clear the catalog panel at every size", fit);
            Console.WriteLine(fails == 0 ? "T40 ALL PASS" : "T40 FAILURES " + fails);
        }
    }

    /** Renders the main screens at several phone/tablet resolutions (SVG) to check the layout. */
    public class LayoutShot
    {
        static void shot(Game g, string file, int w, int h)
        {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            g.draw(img.canvas());
            img.write(file);
        }

        public static void RunMain(string[] a)
        {
            int[][] sizes = { new[] { 800, 480 }, new[] { 1280, 720 }, new[] { 1024, 768 }, new[] { 2400, 1080 } };
            float[] dens = { 1.5f, 2f, 2f, 2.75f };
            for (int i = 0; i < sizes.Length; i++)
            {
                int w = sizes[i][0], h = sizes[i][1];
                Game g = new Game(null);
                g.resize(w, h, dens[i]);
                g.settings.tutorialDone = true;
                Harness.run(g, 0.1f);
                g.screens.screen = Screens.TITLE; g.screens.time = 5f;
                for (int k = 0; k < 30; k++) g.update(1f / 60);
                shot(g, a[0] + "/l" + w + "_title.svg", w, h);
                g.screens.screen = Screens.SETTINGS;
                shot(g, a[0] + "/l" + w + "_settings.svg", w, h);
                g.screens.screen = Screens.NONE;
                g.clearWorldForLoad();
                g.world.spawn(Items.HUMAN, 1500, -120, 1, 1f);
                g.ui.stripOpen = true; g.ui.cat = Items.CAT_MACHINES;
                Harness.run(g, 1f);
                shot(g, a[0] + "/l" + w + "_game.svg", w, h);
            }
        }
    }
}
