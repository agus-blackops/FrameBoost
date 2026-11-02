using Godot;
using TKey = Trastazo.Key;

namespace TrastazoGodot
{
    /**
     * Turns Godot input events (touch, mouse, keyboard) into calls on Trastazo.Driver / Game.touch. Godot already
     * works in pixels with y down, like the game, so no flipping is needed.
     */
    public sealed class GodotInput
    {
        private readonly Trastazo.Driver driver;

        public GodotInput(Trastazo.Driver driver) { this.driver = driver; }

        private static TKey Map(Key k)
        {
            switch (k)
            {
                case Key.Space: return TKey.Space;
                case Key.P: return TKey.P;
                case Key.Z: return TKey.Z;
                case Key.S: return TKey.S;
                case Key.G: return TKey.G;
                case Key.H: return TKey.H;
                case Key.A: return TKey.A;
                case Key.D: return TKey.D;
                case Key.W: return TKey.W;
                case Key.Key0: return TKey.D0;
                case Key.Key1: return TKey.D1;
                case Key.Key2: return TKey.D2;
                case Key.Key3: return TKey.D3;
                case Key.Key4: return TKey.D4;
                case Key.Key5: return TKey.D5;
                case Key.Key6: return TKey.D6;
                case Key.Key7: return TKey.D7;
                case Key.Key8: return TKey.D8;
                case Key.Key9: return TKey.D9;
                case Key.Equal: case Key.Plus: case Key.KpAdd: return TKey.Plus;
                case Key.Minus: case Key.KpSubtract: return TKey.Minus;
                case Key.Left: return TKey.Left;
                case Key.Right: return TKey.Right;
                case Key.Up: return TKey.Up;
                case Key.Down: return TKey.Down;
                case Key.Escape: return TKey.Escape;
                case Key.F11: return TKey.F11;
                case Key.Enter: case Key.KpEnter: return TKey.Enter;
                default: return TKey.None;
            }
        }

        private static bool Repeats(TKey k)
        {
            return k == TKey.Left || k == TKey.Right || k == TKey.Up || k == TKey.Down || k == TKey.A || k == TKey.D || k == TKey.W || k == TKey.Plus || k == TKey.Minus;
        }

        /** Returns true when the event was used. */
        public bool Handle(InputEvent e)
        {
            InputEventScreenTouch st = e as InputEventScreenTouch;
            if (st != null)
            {
                driver.touch(st.Pressed ? Trastazo.Game.DOWN : Trastazo.Game.UP, st.Index, st.Position.X, st.Position.Y);
                return true;
            }
            InputEventScreenDrag sd = e as InputEventScreenDrag;
            if (sd != null)
            {
                driver.touch(Trastazo.Game.MOVE, sd.Index, sd.Position.X, sd.Position.Y);
                return true;
            }
            InputEventMouseButton mb = e as InputEventMouseButton;
            if (mb != null)
            {
                float x = mb.Position.X, y = mb.Position.Y;
                switch (mb.ButtonIndex)
                {
                    case MouseButton.Left: driver.mouseButton(0, mb.Pressed, x, y); return true;
                    case MouseButton.Right: driver.mouseButton(1, mb.Pressed, x, y); return true;
                    case MouseButton.Middle: driver.mouseButton(2, mb.Pressed, x, y); return true;
                    case MouseButton.WheelUp: if (mb.Pressed) driver.wheel(x, y, -1f); return true;
                    case MouseButton.WheelDown: if (mb.Pressed) driver.wheel(x, y, 1f); return true;
                    default: return false;
                }
            }
            InputEventMouseMotion mm = e as InputEventMouseMotion;
            if (mm != null)
            {
                driver.mouseMove(mm.Position.X, mm.Position.Y);
                return true;
            }
            InputEventKey ek = e as InputEventKey;
            if (ek != null && ek.Pressed)
            {
                TKey k = Map(ek.Keycode);
                if (k == TKey.None) return false;
                if (ek.Echo && !Repeats(k)) return true;
                driver.key(k, ek.CtrlPressed || ek.MetaPressed, ek.AltPressed);
                return true;
            }
            return false;
        }
    }
}
