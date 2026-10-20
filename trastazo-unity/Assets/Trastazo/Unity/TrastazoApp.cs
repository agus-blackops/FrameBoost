using System;
using System.Globalization;
using UnityEngine;

namespace TrastazoUnity
{
    /**
     * The whole game as one MonoBehaviour: owns the Trastazo.Game, feeds it input and time, paints it into a
     * DrawList every frame and hands that to UnityCanvasRenderer. Created automatically (TrastazoBootstrap), so
     * any scene - even an empty one - runs the game.
     */
    public sealed class TrastazoApp : MonoBehaviour, Trastazo.Game.Host
    {
        public static TrastazoApp Instance { get; private set; }

        private Trastazo.Game game;
        private Trastazo.Driver driver;
        private Trastazo.Sfx sfx;
        private Trastazo.MusicPlayer music;
        private UnityMusic unityMusic;
        private Trastazo.Canvas canvas;
        private Trastazo.DrawList list;
        private UnityCanvasRenderer view;
        private UnityInput input;
        private ModEditor modEditor;
        private Trastazo.File dataDir;
        private int w, h;
        private int drawErrors;
        private bool started;
#if UNITY_ANDROID && !UNITY_EDITOR
        private AndroidJavaObject vibrator;
#endif

        public Trastazo.Game Game { get { return game; } }

        private void Awake()
        {
            if (Instance != null) { Destroy(gameObject); return; }
            Instance = this;
            DontDestroyOnLoad(gameObject);
            try { Boot(); started = true; }
            catch (Exception e)
            {
                Debug.LogError("Trastazo could not start: " + e);
                Trastazo.Report.crashed("arranque", e);
            }
        }

        private void Boot()
        {
            // number formatting must not depend on the player's language ("1,5" would break saves and mods)
            CultureInfo.DefaultThreadCurrentCulture = CultureInfo.InvariantCulture;
            System.Threading.Thread.CurrentThread.CurrentCulture = CultureInfo.InvariantCulture;
            Application.targetFrameRate = 60;
            QualitySettings.vSyncCount = 0;
            QualitySettings.antiAliasing = 4;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
            Application.runInBackground = false;

            string root = Application.persistentDataPath;
            dataDir = new Trastazo.File(root);
            dataDir.mkdirs();
            Trastazo.Report.install(dataDir, Application.platform + " · " + SystemInfo.operatingSystem + " · Unity " + Application.unityVersion);
            Application.logMessageReceivedThreaded += OnLog;

            Trastazo.Game.desktop = !Application.isMobilePlatform;
            Trastazo.Game.lighting = true;
            Trastazo.File cache = new Trastazo.File(dataDir, "cache");
            cache.mkdirs();

            view = new UnityCanvasRenderer();
            view.Init();

            sfx = new Trastazo.Sfx(new UnitySoundBackend(gameObject, 12));
            game = new Trastazo.Game(sfx, new Trastazo.File(dataDir, "mods"));
            game.host = this;
            game.saveFile = new Trastazo.File(dataDir, "autosave2.trz");
            game.settings = Trastazo.SettingsIO.fromText(ReadSettingsText());
            music = new Trastazo.MusicPlayer(cache);
            game.music = music;
            game.applySettings();
            music.play(Trastazo.Music.TITLE);
            music.start();
            unityMusic = new UnityMusic(gameObject, music);

            driver = new Trastazo.Driver(game);
            input = new UnityInput(driver);
            list = new Trastazo.DrawList();
            modEditor = gameObject.AddComponent<ModEditor>();
            Resize();
        }

        // ----------------------------------------------------------------------- frame ---
        private void Resize()
        {
            w = Mathf.Max(16, Screen.width);
            h = Mathf.Max(16, Screen.height);
            float d = Trastazo.Driver.densityFor(w, h, Application.isMobilePlatform, Screen.dpi);
            driver.resize(w, h, d);
            canvas = new Trastazo.Canvas(list, w, h);
            game.fullscreen = Screen.fullScreen;
        }

        private void Update()
        {
            if (!started) return;
            if (Screen.width != w || Screen.height != h) Resize();
            float dt = Mathf.Min(0.1f, Time.unscaledDeltaTime);
            if (!modEditor.IsOpen) input.Poll(h, dt);
            game.update(dt);
        }

        private void LateUpdate()
        {
            if (!started) return;
            try
            {
                canvas.begin();
                game.draw(canvas);
                canvas.end();
                view.Render(list, w, h);
            }
            catch (Exception e)
            {
                // a bug in drawing must not close the game: log it (a few times) and try again next frame
                if (drawErrors++ < 20) Trastazo.Report.recovered("dibujo", e);
            }
        }

        // ------------------------------------------------------------------- lifecycle ---
        private void OnApplicationPause(bool paused)
        {
            if (!started) return;
            if (music != null) music.setPaused(paused);
            if (paused) { game.saveScene(); SaveSettings(game.settings); }
        }

        private void OnApplicationFocus(bool focus)
        {
            if (!started) return;
            if (!focus)
            {
                driver.releaseAll();
                if (Trastazo.Game.desktop && game.screens.screen == Trastazo.Screens.NONE) game.paused = true;
            }
        }

        private void OnApplicationQuit()
        {
            if (!started) return;
            try
            {
                game.saveScene();
                SaveSettings(game.settings);
                if (music != null) music.stop();
                if (unityMusic != null) unityMusic.Stop();
                if (sfx != null) sfx.release();
                if (view != null) view.Shutdown();
            }
            catch (Exception) { }
        }

        private void OnLog(string condition, string stackTrace, LogType type)
        {
            // exceptions that reach Unity's log (not the ones the game already survives) mark the session as crashed
            if (type == LogType.Exception) Trastazo.Report.crashed(condition, new Exception(condition + "\n" + stackTrace));
        }

        // --------------------------------------------------------------------- host ---
        public void editMod(Trastazo.Mod existing) { modEditor.Open(game, existing); }

        public void saveSettings(Trastazo.Settings s) { SaveSettings(s); }

        public void vibrate(int ms)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                if (vibrator == null)
                {
                    using (AndroidJavaClass unity = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                    using (AndroidJavaObject activity = unity.GetStatic<AndroidJavaObject>("currentActivity"))
                        vibrator = activity.Call<AndroidJavaObject>("getSystemService", "vibrator");
                }
                if (vibrator != null) vibrator.Call("vibrate", (long)ms);
            }
            catch (Exception) { }
#endif
        }

        /** Never called: using Handheld.Vibrate makes Unity add the VIBRATE permission to the Android manifest. */
        private static void KeepVibratePermission() { if (Application.isEditor && Time.frameCount < 0) Handheld.Vibrate(); }

        public void switchEngine(bool gpu) { }

        public void toggleFullscreen()
        {
            if (Application.isMobilePlatform) return;
            Screen.fullScreenMode = Screen.fullScreen ? FullScreenMode.Windowed : FullScreenMode.FullScreenWindow;
            game.fullscreen = !Screen.fullScreen;
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

    /** Creates the app when the first scene loads, so no scene setup is needed. */
    public static class TrastazoBootstrap
    {
        [RuntimeInitializeOnLoadMethod(RuntimeInitializeLoadType.AfterSceneLoad)]
        private static void Boot()
        {
            if (TrastazoApp.Instance != null) return;
            GameObject go = new GameObject("Trastazo");
            go.AddComponent<TrastazoApp>();
        }
    }
}
