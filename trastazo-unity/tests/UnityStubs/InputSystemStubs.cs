#pragma warning disable CS0649
using UnityEngine;
namespace UnityEngine.InputSystem
{
    public enum Key { None, Space, P, Z, S, G, H, A, D, W, Digit0, Digit1, Digit2, Digit3, Digit4, Digit5, Digit6, Digit7, Digit8, Digit9, Equals, Minus, LeftArrow, RightArrow, UpArrow, DownArrow, Escape, F11, Enter }
}
namespace UnityEngine.InputSystem.Controls
{
    public class ButtonControl { public bool wasPressedThisFrame, wasReleasedThisFrame, isPressed; }
    public class KeyControl : ButtonControl { }
    public class Vector2Control { public Vector2 ReadValue() { return default(Vector2); } }
    public class IntegerControl { public int ReadValue() { return 0; } }
    public class TouchControl { public ButtonControl press; public Vector2Control position; public IntegerControl touchId; }
}
namespace UnityEngine.InputSystem
{
    using UnityEngine.InputSystem.Controls;
    public class Keyboard
    {
        public static Keyboard current;
        public KeyControl this[Key k] { get { return null; } }
        public KeyControl ctrlKey, leftCommandKey, altKey;
    }
    public class Mouse { public static Mouse current; public ButtonControl leftButton, rightButton, middleButton; public Vector2Control position, scroll; }
    public class TouchCollection { public int Count; public TouchControl this[int i] { get { return null; } } }
    public class Touchscreen { public static Touchscreen current; public TouchCollection touches; }
}
