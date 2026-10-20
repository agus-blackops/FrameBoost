using System;

namespace Trastazo
{
    /** Night and neon scenes with fire and an explosion, to check the lights, tint and vignette. */
    public class AtmosphereShot
    {
        public static void RunMain(string[] a)
        {
            Game.lighting = true;
            foreach (int theme in new[] { 2, 4, 6 })
            {
                Game g = Harness.newGame();
                g.userTheme = theme;
                g.world.spawn(Items.HUMAN, 1500, -120, 1, 1f);
                Person p = (Person)g.world.spawn(Items.HUMAN, 1650, -120, -1, 1f);
                g.world.spawn(Items.BARREL, 1800, -100, 1, 1f);
                Harness.run(g, 1f);
                g.world.ignite(p);
                g.world.explode(1400, -60, 160, 500, 30, false);
                Harness.run(g, 0.25f);
                Harness.shot(g, a[0] + "/atm_" + theme + ".svg");
            }
        }
    }
}
