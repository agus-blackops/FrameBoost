using System;

namespace Trastazo
{
    /** Keys the game reacts to (the platform layer maps its own key codes to these). */
    public enum Key
    {
        None, Space, P, Z, S, G, H, A, D, W,
        D0, D1, D2, D3, D4, D5, D6, D7, D8, D9,
        Plus, Minus, Left, Right, Up, Down, Escape, F11, Enter
    }

    /**
     * Platform-independent glue between raw input events and the Game: mouse + keyboard rules of the desktop
     * version (left button = touch, other buttons pan, wheel zooms or scrolls the catalog, shortcuts) and the
     * on-screen scale. The Unity layer only forwards events here, so all of this runs (and is tested) without Unity.
     */
    public sealed class Driver
    {
        public readonly Game game;
        private bool leftDown, panning;
        private float lastX, lastY;

        public Driver(Game game) { this.game = game; }

        /** Interface scale: dp-like density from the screen DPI on phones, window height on desktop. */
        public static float densityFor(int w, int h, bool mobile, float dpi)
        {
            if (mobile)
            {
                float d = dpi > 0f ? dpi / 160f : h / 400f;
                return Math.max(1f, Math.min(4f, d));
            }
            return Math.max(1.2f, Math.min(3.6f, Math.min(h / 400f, w / 720f)));
        }

        /** Resizes keeping the camera when only the window size changed. */
        public void resize(int w, int h, float density)
        {
            int ow = game.sw;
            float cx = game.camX, cy = game.camY, z = game.zoom;
            game.resize(w, h, density);
            if (ow > 0 && game.screens.screen == Screens.NONE) { game.camX = cx; game.camY = cy; game.zoom = z * w / ow; game.clampCam(); }
        }

        // ------------------------------------------------------------------- touch ---
        public void touch(int action, int id, float x, float y) { game.touch(action, id, x, y); }

        // ------------------------------------------------------------------- mouse ---
        /** button: 0 left, 1 right, 2 middle. Coordinates in pixels, y down. */
        public void mouseButton(int button, bool down, float x, float y)
        {
            lastX = x; lastY = y;
            if (button == 0)
            {
                if (down && !leftDown) { leftDown = true; game.touch(Game.DOWN, 0, x, y); }
                else if (!down && leftDown) { leftDown = false; game.touch(Game.UP, 0, x, y); }
            }
            else panning = down;
        }

        public void mouseMove(float x, float y)
        {
            if (leftDown) game.touch(Game.MOVE, 0, x, y);
            if (panning && game.screens.screen == Screens.NONE) game.panBy(x - lastX, y - lastY);
            lastX = x; lastY = y;
        }

        /** The window lost focus: let go of everything. */
        public void releaseAll()
        {
            if (leftDown) { leftDown = false; game.touch(Game.CANCEL, 0, lastX, lastY); }
            panning = false;
        }

        /** rotation > 0 = wheel turned down (zoom out / scroll the catalog down). */
        public void wheel(float x, float y, float rotation)
        {
            if (game.screens.screen != Screens.NONE) return;
            if (game.ui.stripOpen && x < game.ui.leftW())
                game.ui.scrollY = Math.max(0, Math.min(game.ui.maxScrollY(), game.ui.scrollY + rotation * 60f * game.density));
            else game.zoomAt(x, y, (float)Math.pow(1.12, -rotation));
        }

        // ---------------------------------------------------------------- keyboard ---
        public void key(Key k, bool ctrl, bool alt)
        {
            if (k == Key.F11 || (k == Key.Enter && alt)) { if (game.host != null) game.host.toggleFullscreen(); return; }
            bool playing = game.screens.screen == Screens.NONE;
            if (k == Key.Escape)
            {
                if (!game.back() && game.fullscreen && game.host != null) game.host.toggleFullscreen();
                return;
            }
            if (!playing) return;
            if (k == Key.Space || k == Key.P) game.pressButton(Ui.BTN_PAUSE);
            else if (k == Key.Z && ctrl) { if (!game.ui.btnHidden[Ui.BTN_UNDO]) game.pressButton(Ui.BTN_UNDO); }
            else if (k == Key.S) game.pressButton(Ui.BTN_SLOW);
            else if (k == Key.G) { if (!game.ui.btnHidden[Ui.BTN_GRAV]) game.pressButton(Ui.BTN_GRAV); }
            else if (k == Key.H) game.tool = Items.HAND;
            else if (k >= Key.D0 && k <= Key.D9)
            {
                int i = k == Key.D0 ? 9 : k - Key.D1;
                if (!game.ui.dock && i < game.ui.cats.Length) game.toggleCategory(game.ui.cats[i]);
            }
            else if (k == Key.Plus) game.zoomAt(game.sw / 2f, game.sh / 2f, 1.15f);
            else if (k == Key.Minus) game.zoomAt(game.sw / 2f, game.sh / 2f, 1f / 1.15f);
            else if (k == Key.Left || k == Key.A) game.panBy(80, 0);
            else if (k == Key.Right || k == Key.D) game.panBy(-80, 0);
            else if (k == Key.Up || k == Key.W) game.panBy(0, 80);
            else if (k == Key.Down) game.panBy(0, -80);
        }
    }

    /** Settings as a small key=value text file (the same on every platform). */
    public static class SettingsIO
    {
        public static string toText(Settings s)
        {
            System.Text.StringBuilder b = new System.Text.StringBuilder();
            System.Globalization.CultureInfo ci = System.Globalization.CultureInfo.InvariantCulture;
            b.Append("music=").Append(s.music.ToString(ci)).Append('\n');
            b.Append("sfx=").Append(s.sfx.ToString(ci)).Append('\n');
            b.Append("haptics=").Append(s.haptics ? "1" : "0").Append('\n');
            b.Append("gore=").Append(s.gore ? "1" : "0").Append('\n');
            b.Append("fps=").Append(s.showFps ? "1" : "0").Append('\n');
            b.Append("quality=").Append(s.highQuality ? "1" : "0").Append('\n');
            b.Append("tutorial=").Append(s.tutorialDone ? "1" : "0").Append('\n');
            b.Append("textures=").Append(s.textures ? "1" : "0").Append('\n');
            b.Append("muted=").Append(s.muted ? "1" : "0").Append('\n');
            b.Append("stars=").Append(s.stars).Append('\n');
            b.Append("ach=").Append(s.ach).Append('\n');
            return b.ToString();
        }

        public static Settings fromText(string text)
        {
            Settings s = new Settings();
            s.gpu = false;   // there is no separate GPU engine in the Unity version: Unity batches the drawing itself
            if (text == null) return s;
            foreach (string raw in text.Split('\n'))
            {
                int eq = raw.IndexOf('=');
                if (eq <= 0) continue;
                string k = raw.Substring(0, eq).Trim(), v = raw.Substring(eq + 1).Trim();
                try
                {
                    switch (k)
                    {
                        case "music": s.music = Float.parseFloat(v); break;
                        case "sfx": s.sfx = Float.parseFloat(v); break;
                        case "haptics": s.haptics = v == "1"; break;
                        case "gore": s.gore = v == "1"; break;
                        case "fps": s.showFps = v == "1"; break;
                        case "quality": s.highQuality = v == "1"; break;
                        case "tutorial": s.tutorialDone = v == "1"; break;
                        case "textures": s.textures = v == "1"; break;
                        case "muted": s.muted = v == "1"; break;
                        case "stars": s.stars = v; break;
                        case "ach": s.ach = v; break;
                    }
                }
                catch (Exception) { }
            }
            return s;
        }
    }
}
