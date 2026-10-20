#if ENABLE_INPUT_SYSTEM && !ENABLE_LEGACY_INPUT_MANAGER
#define TRZ_NEW_INPUT
#endif
using UnityEngine;
#if TRZ_NEW_INPUT
using UnityEngine.InputSystem;
#endif
using TKey = Trastazo.Key;

namespace TrastazoUnity
{
    /**
     * Reads touches, mouse and keyboard (Input System package or the legacy Input Manager, whichever the project
     * uses) and forwards them to Trastazo.Driver / Game.touch. y is flipped: the game works with y down.
     */
    public sealed class UnityInput
    {
        private readonly Trastazo.Driver driver;
        private float holdTime;
        private TKey held = TKey.None;

        public UnityInput(Trastazo.Driver driver)
        {
            this.driver = driver;
#if !TRZ_NEW_INPUT
            Input.simulateMouseWithTouches = false;
            Input.multiTouchEnabled = true;
#endif
        }

        public void Poll(float screenH, float dt)
        {
            PollTouches(screenH);
            PollMouse(screenH);
            PollKeys(dt);
        }

        // ------------------------------------------------------------------- keys ---
        private static readonly TKey[] Repeating = { TKey.Left, TKey.Right, TKey.Up, TKey.Down, TKey.A, TKey.D, TKey.W, TKey.Plus, TKey.Minus };

        private static bool IsRepeating(TKey k)
        {
            for (int i = 0; i < Repeating.Length; i++) if (Repeating[i] == k) return true;
            return false;
        }

        private void PollKeys(float dt)
        {
            bool ctrl = CtrlDown(), alt = AltDown();
            TKey pressedNow = TKey.None;
            foreach (TKey k in System.Enum.GetValues(typeof(TKey)))
            {
                if (k == TKey.None) continue;
                if (Down(k)) { driver.key(k, ctrl, alt); pressedNow = k; }
            }
            // arrows, WASD and +/- repeat while held
            if (pressedNow != TKey.None && IsRepeating(pressedNow)) { held = pressedNow; holdTime = 0f; }
            else if (held != TKey.None)
            {
                if (!Held(held)) held = TKey.None;
                else
                {
                    float before = holdTime;
                    holdTime += dt;
                    const float delay = 0.35f, rate = 0.05f;
                    if (holdTime > delay)
                    {
                        int a = (int)((before - delay) / rate), b = (int)((holdTime - delay) / rate);
                        if (before < delay || b > a) driver.key(held, ctrl, alt);
                    }
                }
            }
        }

#if TRZ_NEW_INPUT
        private static UnityEngine.InputSystem.Key Map(TKey k)
        {
            switch (k)
            {
                case TKey.Space: return UnityEngine.InputSystem.Key.Space;
                case TKey.P: return UnityEngine.InputSystem.Key.P;
                case TKey.Z: return UnityEngine.InputSystem.Key.Z;
                case TKey.S: return UnityEngine.InputSystem.Key.S;
                case TKey.G: return UnityEngine.InputSystem.Key.G;
                case TKey.H: return UnityEngine.InputSystem.Key.H;
                case TKey.A: return UnityEngine.InputSystem.Key.A;
                case TKey.D: return UnityEngine.InputSystem.Key.D;
                case TKey.W: return UnityEngine.InputSystem.Key.W;
                case TKey.D0: return UnityEngine.InputSystem.Key.Digit0;
                case TKey.D1: return UnityEngine.InputSystem.Key.Digit1;
                case TKey.D2: return UnityEngine.InputSystem.Key.Digit2;
                case TKey.D3: return UnityEngine.InputSystem.Key.Digit3;
                case TKey.D4: return UnityEngine.InputSystem.Key.Digit4;
                case TKey.D5: return UnityEngine.InputSystem.Key.Digit5;
                case TKey.D6: return UnityEngine.InputSystem.Key.Digit6;
                case TKey.D7: return UnityEngine.InputSystem.Key.Digit7;
                case TKey.D8: return UnityEngine.InputSystem.Key.Digit8;
                case TKey.D9: return UnityEngine.InputSystem.Key.Digit9;
                case TKey.Plus: return UnityEngine.InputSystem.Key.Equals;
                case TKey.Minus: return UnityEngine.InputSystem.Key.Minus;
                case TKey.Left: return UnityEngine.InputSystem.Key.LeftArrow;
                case TKey.Right: return UnityEngine.InputSystem.Key.RightArrow;
                case TKey.Up: return UnityEngine.InputSystem.Key.UpArrow;
                case TKey.Down: return UnityEngine.InputSystem.Key.DownArrow;
                case TKey.Escape: return UnityEngine.InputSystem.Key.Escape;
                case TKey.F11: return UnityEngine.InputSystem.Key.F11;
                case TKey.Enter: return UnityEngine.InputSystem.Key.Enter;
                default: return UnityEngine.InputSystem.Key.None;
            }
        }

        private static bool Down(TKey k) { Keyboard kb = Keyboard.current; return kb != null && kb[Map(k)].wasPressedThisFrame; }
        private static bool Held(TKey k) { Keyboard kb = Keyboard.current; return kb != null && kb[Map(k)].isPressed; }
        private static bool CtrlDown() { Keyboard kb = Keyboard.current; return kb != null && (kb.ctrlKey.isPressed || kb.leftCommandKey.isPressed); }
        private static bool AltDown() { Keyboard kb = Keyboard.current; return kb != null && kb.altKey.isPressed; }

        private void PollTouches(float h)
        {
            Touchscreen ts = Touchscreen.current;
            if (ts == null) return;
            for (int i = 0; i < ts.touches.Count; i++)
            {
                UnityEngine.InputSystem.Controls.TouchControl t = ts.touches[i];
                int id = t.touchId.ReadValue();
                Vector2 p = t.position.ReadValue();
                if (t.press.wasPressedThisFrame) driver.touch(Trastazo.Game.DOWN, id, p.x, h - p.y);
                else if (t.press.isPressed) driver.touch(Trastazo.Game.MOVE, id, p.x, h - p.y);
                if (t.press.wasReleasedThisFrame) driver.touch(Trastazo.Game.UP, id, p.x, h - p.y);
            }
        }

        private Vector2 lastMouse = new Vector2(-1f, -1f);

        private void PollMouse(float h)
        {
            Mouse m = Mouse.current;
            if (m == null) return;
            Vector2 p = m.position.ReadValue();
            float x = p.x, y = h - p.y;
            if (m.leftButton.wasPressedThisFrame) driver.mouseButton(0, true, x, y);
            if (m.rightButton.wasPressedThisFrame) driver.mouseButton(1, true, x, y);
            if (m.middleButton.wasPressedThisFrame) driver.mouseButton(2, true, x, y);
            if (p != lastMouse) { driver.mouseMove(x, y); lastMouse = p; }
            if (m.leftButton.wasReleasedThisFrame) driver.mouseButton(0, false, x, y);
            if (m.rightButton.wasReleasedThisFrame) driver.mouseButton(1, false, x, y);
            if (m.middleButton.wasReleasedThisFrame) driver.mouseButton(2, false, x, y);
            float sy = m.scroll.ReadValue().y;
            if (sy != 0f) driver.wheel(x, y, -(Mathf.Abs(sy) >= 100f ? sy / 120f : Mathf.Sign(sy)));
        }
#else
        private static KeyCode Map(TKey k)
        {
            switch (k)
            {
                case TKey.Space: return KeyCode.Space;
                case TKey.P: return KeyCode.P;
                case TKey.Z: return KeyCode.Z;
                case TKey.S: return KeyCode.S;
                case TKey.G: return KeyCode.G;
                case TKey.H: return KeyCode.H;
                case TKey.A: return KeyCode.A;
                case TKey.D: return KeyCode.D;
                case TKey.W: return KeyCode.W;
                case TKey.D0: return KeyCode.Alpha0;
                case TKey.D1: return KeyCode.Alpha1;
                case TKey.D2: return KeyCode.Alpha2;
                case TKey.D3: return KeyCode.Alpha3;
                case TKey.D4: return KeyCode.Alpha4;
                case TKey.D5: return KeyCode.Alpha5;
                case TKey.D6: return KeyCode.Alpha6;
                case TKey.D7: return KeyCode.Alpha7;
                case TKey.D8: return KeyCode.Alpha8;
                case TKey.D9: return KeyCode.Alpha9;
                case TKey.Plus: return KeyCode.Equals;
                case TKey.Minus: return KeyCode.Minus;
                case TKey.Left: return KeyCode.LeftArrow;
                case TKey.Right: return KeyCode.RightArrow;
                case TKey.Up: return KeyCode.UpArrow;
                case TKey.Down: return KeyCode.DownArrow;
                case TKey.Escape: return KeyCode.Escape;
                case TKey.F11: return KeyCode.F11;
                case TKey.Enter: return KeyCode.Return;
                default: return KeyCode.None;
            }
        }

        private static bool Down(TKey k) { return Input.GetKeyDown(Map(k)) || (k == TKey.Plus && Input.GetKeyDown(KeyCode.KeypadPlus)) || (k == TKey.Minus && Input.GetKeyDown(KeyCode.KeypadMinus)); }
        private static bool Held(TKey k) { return Input.GetKey(Map(k)) || (k == TKey.Plus && Input.GetKey(KeyCode.KeypadPlus)) || (k == TKey.Minus && Input.GetKey(KeyCode.KeypadMinus)); }
        private static bool CtrlDown() { return Input.GetKey(KeyCode.LeftControl) || Input.GetKey(KeyCode.RightControl) || Input.GetKey(KeyCode.LeftCommand) || Input.GetKey(KeyCode.RightCommand); }
        private static bool AltDown() { return Input.GetKey(KeyCode.LeftAlt) || Input.GetKey(KeyCode.RightAlt); }

        private void PollTouches(float h)
        {
            for (int i = 0; i < Input.touchCount; i++)
            {
                Touch t = Input.GetTouch(i);
                float x = t.position.x, y = h - t.position.y;
                switch (t.phase)
                {
                    case TouchPhase.Began: driver.touch(Trastazo.Game.DOWN, t.fingerId, x, y); break;
                    case TouchPhase.Moved:
                    case TouchPhase.Stationary: driver.touch(Trastazo.Game.MOVE, t.fingerId, x, y); break;
                    case TouchPhase.Ended: driver.touch(Trastazo.Game.UP, t.fingerId, x, y); break;
                    case TouchPhase.Canceled: driver.touch(Trastazo.Game.CANCEL, t.fingerId, x, y); break;
                }
            }
        }

        private Vector3 lastMouse = new Vector3(-1f, -1f, 0f);

        private void PollMouse(float h)
        {
            if (Input.touchCount > 0 || !Input.mousePresent) return;
            Vector3 p = Input.mousePosition;
            float x = p.x, y = h - p.y;
            if (Input.GetMouseButtonDown(0)) driver.mouseButton(0, true, x, y);
            if (Input.GetMouseButtonDown(1)) driver.mouseButton(1, true, x, y);
            if (Input.GetMouseButtonDown(2)) driver.mouseButton(2, true, x, y);
            if (p != lastMouse) { driver.mouseMove(x, y); lastMouse = p; }
            if (Input.GetMouseButtonUp(0)) driver.mouseButton(0, false, x, y);
            if (Input.GetMouseButtonUp(1)) driver.mouseButton(1, false, x, y);
            if (Input.GetMouseButtonUp(2)) driver.mouseButton(2, false, x, y);
            float sy = Input.mouseScrollDelta.y;
            if (sy != 0f) driver.wheel(x, y, -sy);
        }
#endif
    }
}
