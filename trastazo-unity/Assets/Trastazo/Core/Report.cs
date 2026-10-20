using System;

namespace Trastazo
{
    /**
     * Crash / recovery log. A "closing flag" file is created on start and removed on a clean exit; if it is still
     * there on the next launch the previous session ended badly and the title screen says so.
     */
    public static class Report
    {
        public const string VERSION = "4.0";
        public static string lastCrash;
        private static File dir;
        private static string platform = "";
        private static int logged;
        private static readonly object gate = new object();

        public static File file() { return dir == null ? null : new File(dir, "trastazo-informe.txt"); }

        public static void install(File folder, string platformInfo)
        {
            dir = folder;
            platform = platformInfo;
            lastCrash = null;
            try
            {
                dir.mkdirs();
                File f = file();
                File crash = new File(dir, "trastazo-cierre.flag");
                if (crash.exists()) { lastCrash = f != null && f.exists() ? f.getPath() : "(sin informe)"; crash.delete(); }
            }
            catch (Exception) { }
        }

        /** An error the game survived (limited so a broken frame loop cannot fill the disk). */
        public static void recovered(string what, Exception e)
        {
            if (logged++ < 20) write("Error recuperado: " + what, e);
        }

        /** An unhandled error reached the top level: the next launch tells the player. */
        public static void crashed(string what, Exception e)
        {
            write("CIERRE INESPERADO: " + what, e);
            try { if (dir != null) new File(dir, "trastazo-cierre.flag").createNewFile(); } catch (Exception) { }
        }

        private static void write(string title, Exception e)
        {
            File f = file();
            if (f == null) return;
            lock (gate)
            {
                try
                {
                    if (f.length() > 200000) f.delete();
                    using (System.IO.StreamWriter o = new System.IO.StreamWriter(f.getPath(), true, new System.Text.UTF8Encoding(false)))
                    {
                        o.WriteLine("=== " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + " · Trastazo " + VERSION + " · " + platform);
                        o.WriteLine(title);
                        o.WriteLine(e == null ? "(sin excepción)" : e.ToString());
                        o.WriteLine();
                    }
                }
                catch (Exception) { }
            }
        }
    }
}
