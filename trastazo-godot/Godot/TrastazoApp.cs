using System;
using System.Globalization;
using Godot;

namespace TrastazoGodot
{
    /**
     * The whole game as one Node2D: owns the Trastazo.Game, feeds it input and time, paints it into a DrawList every
     * frame and hands that to GodotCanvasRenderer from _Draw. The rules, physics, AI and drawing all live in the
     * platform-independent Core (the same C# the Unity version uses, translated from the Java game).
     */
    public partial class TrastazoApp : Node2D, Trastazo.Game.Host
    {
        public const string Edition = "Godot 1.0";

        private Trastazo.Game game;
        private Trastazo.Driver driver;
        private Trastazo.Sfx sfx;
        private Trastazo.MusicPlayer music;
        private GodotMusic godotMusic;
        private Trastazo.Canvas canvas;
        private Trastazo.DrawList list;
        private GodotCanvasRenderer view;
        private GodotInput input;
        private ModEditor modEditor;
        private Trastazo.File dataDir;
        private int w, h;
        private int drawErrors;
        private bool started;
        private bool mobile;

        public Trastazo.Game Game { get { return game; } }

        public override void _Ready()
        {
            try { Boot(); started = true; }
            catch (Exception e)
            {
                GD.PushError("Trastazo could not start: " + e);
                Trastazo.Report.crashed("arranque", e);
            }
        }

        private void Boot()
        {
            // number formatting must not depend on the player's language ("1,5" would break saves and mods)
            CultureInfo.DefaultThreadCurrentCulture = CultureInfo.InvariantCulture;
            System.Threading.Thread.CurrentThread.CurrentCulture = CultureInfo.InvariantCulture;
            mobile = OS.HasFeature("mobile");
            Engine.MaxFps = 60;
            DisplayServer.ScreenSetKeepOn(true);

            dataDir = new Trastazo.File(OS.GetUserDataDir());
            dataDir.mkdirs();
            Trastazo.Report.install(dataDir, OS.GetName() + " · " + OS.GetDistributionName() + " · Godot " + Engine.GetVersionInfo()["string"]);
            AppDomain.CurrentDomain.UnhandledException += (s, a) => Trastazo.Report.crashed("excepción", a.ExceptionObject as Exception ?? new Exception(a.ExceptionObject.ToString()));

            Trastazo.Game.desktop = !mobile;
            Trastazo.Game.lighting = true;
            Trastazo.Game.edition = Edition;
            Trastazo.File cache = new Trastazo.File(dataDir, "cache");
            cache.mkdirs();

            view = new GodotCanvasRenderer();
            view.Init();

            sfx = new Trastazo.Sfx(new GodotSoundBackend(this, 12));
            game = new Trastazo.Game(sfx, new Trastazo.File(dataDir, "mods"));
            game.host = this;
            game.saveFile = new Trastazo.File(dataDir, "autosave2.trz");
            game.settings = Trastazo.SettingsIO.fromText(ReadSettingsText());
            music = new Trastazo.MusicPlayer(cache);
            game.music = music;
            game.applySettings();
            music.play(Trastazo.Music.TITLE);
            music.start();
            godotMusic = new GodotMusic(this, music);

            driver = new Trastazo.Driver(game);
            input = new GodotInput(driver);
            list = new Trastazo.DrawList();
            modEditor = new ModEditor();
            AddChild(modEditor);
            Resize();
        }

        // ----------------------------------------------------------------------- frame ---
        private void Resize()
        {
            Vector2 size = GetViewportRect().Size;
            w = Math.Max(16, (int)size.X);
            h = Math.Max(16, (int)size.Y);
            float d = Trastazo.Driver.densityFor(w, h, mobile, DisplayServer.ScreenGetDpi());
            driver.resize(w, h, d);
            canvas = new Trastazo.Canvas(list, w, h);
            game.fullscreen = DisplayServer.WindowGetMode() == DisplayServer.WindowMode.Fullscreen;
        }

        public override void _Process(double delta)
        {
            if (!started) return;
            Vector2 size = GetViewportRect().Size;
            if ((int)size.X != w || (int)size.Y != h) Resize();
            float dt = Math.Min(0.1f, (float)delta);
            game.update(dt);
            godotMusic.Pump();
            try
            {
                canvas.begin();
                game.draw(canvas);
                canvas.end();
            }
            catch (Exception e)
            {
                // a bug in drawing must not close the game: log it (a few times) and try again next frame
                if (drawErrors++ < 20) Trastazo.Report.recovered("dibujo", e);
            }
            QueueRedraw();
        }

        public override void _Draw()
        {
            if (!started || list == null) return;
            try { view.Draw(this, list); }
            catch (Exception e) { if (drawErrors++ < 20) Trastazo.Report.recovered("pintar", e); }
        }

        public override void _Input(InputEvent e)
        {
            if (!started || modEditor.IsOpen) return;
            if (input.Handle(e)) GetViewport().SetInputAsHandled();
        }

        // ------------------------------------------------------------------- lifecycle ---
        public override void _Notification(int what)
        {
            if (!started) return;
            switch ((long)what)
            {
                case NotificationApplicationPaused:
                    if (music != null) music.setPaused(true);
                    game.saveScene(); SaveSettings(game.settings);
                    break;
                case NotificationApplicationResumed:
                    if (music != null) music.setPaused(false);
                    break;
                case NotificationApplicationFocusOut:
                    driver.releaseAll();
                    if (Trastazo.Game.desktop && game.screens.screen == Trastazo.Screens.NONE) game.paused = true;
                    break;
                case NotificationWMCloseRequest:
                    Shutdown();
                    break;
            }
        }

        private void Shutdown()
        {
            if (!started) return;
            started = false;
            try
            {
                game.saveScene();
                SaveSettings(game.settings);
                if (music != null) music.stop();
                if (godotMusic != null) godotMusic.Stop();
                if (sfx != null) sfx.release();
                if (view != null) view.Shutdown();
            }
            catch (Exception) { }
        }

        // --------------------------------------------------------------------- host ---
        public void editMod(Trastazo.Mod existing) { modEditor.Open(game, existing); }

        public void saveSettings(Trastazo.Settings s) { SaveSettings(s); }

        public void vibrate(int ms)
        {
            if (mobile) Input.VibrateHandheld(ms);
        }

        public void switchEngine(bool gpu) { }

        public void toggleFullscreen()
        {
            if (mobile) return;
            bool full = DisplayServer.WindowGetMode() == DisplayServer.WindowMode.Fullscreen;
            DisplayServer.WindowSetMode(full ? DisplayServer.WindowMode.Windowed : DisplayServer.WindowMode.Fullscreen);
            game.fullscreen = !full;
        }

        // ------------------------------------------------------------------ settings ---
        private Trastazo.File SettingsFile() { return new Trastazo.File(dataDir, "settings.txt"); }

        private string ReadSettingsText()
        {
            try { Trastazo.File f = SettingsFile(); return f.exists() ? f.readAllText() : null; }
            catch (Exception) { return null; }
        }

        private void SaveSettings(Trastazo.Settings s)
        {
            try { SettingsFile().writeAllText(Trastazo.SettingsIO.toText(s)); }
            catch (Exception e) { Trastazo.Report.recovered("guardar ajustes", e); }
        }
    }
}
