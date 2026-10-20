// Minimal stand-ins for the UnityEngine API the Unity layer uses, so that layer can be type-checked with dotnet
// (against the real translated game code) without a Unity Editor. Behaviour is NOT emulated.
#pragma warning disable CS0067, CS0414, CS0649, CS0169
using System;
using System.Collections.Generic;

namespace UnityEngine
{
    public class Object
    {
        public string name;
        public static void DontDestroyOnLoad(Object o) { }
        public static void Destroy(Object o) { }
        public static T[] FindObjectsOfType<T>() where T : Object { return new T[0]; }
        public static implicit operator bool(Object o) { return o != null; }
    }
    public class Component : Object { public GameObject gameObject; public Transform transform; }
    public class Behaviour : Component { public bool enabled; }
    public class MonoBehaviour : Behaviour { }
    public class Transform : Component { public Vector3 position; public void SetParent(Transform p, bool worldPositionStays) { } }
    public class GameObject : Object
    {
        public Transform transform;
        public GameObject() { }
        public GameObject(string n) { }
        public T AddComponent<T>() where T : Component, new() { return new T(); }
    }
    public class AudioListener : Behaviour { }
    public class AudioClip : Object
    {
        public delegate void PCMReaderCallback(float[] data);
        public delegate void PCMSetPositionCallback(int position);
        public static AudioClip Create(string n, int len, int ch, int freq, bool stream) { return null; }
        public static AudioClip Create(string n, int len, int ch, int freq, bool stream, PCMReaderCallback r, PCMSetPositionCallback s) { return null; }
        public bool SetData(float[] d, int off) { return true; }
    }
    public class AudioSource : Behaviour
    {
        public bool playOnAwake, loop, isPlaying; public float spatialBlend, volume, pitch; public AudioClip clip;
        public void Play() { } public void Stop() { }
    }
    public enum CameraClearFlags { SolidColor }
    public class Camera : Behaviour
    {
        public bool orthographic, allowHDR, allowMSAA; public CameraClearFlags clearFlags; public Color backgroundColor;
        public int cullingMask; public float nearClip, farClip, depth, orthographicSize;
    }
    public enum MeshTopology { Triangles }
    public struct Bounds { public Bounds(Vector3 c, Vector3 s) { } }
    public class Mesh : Object
    {
        public Rendering.IndexFormat indexFormat; public int subMeshCount; public Bounds bounds;
        public void MarkDynamic() { } public void Clear() { }
        public void SetVertices(Vector3[] v, int s, int l) { }
        public void SetColors(Color32[] c, int s, int l) { }
        public void SetUVs(int ch, Vector2[] u, int s, int l) { }
        public void SetIndices(int[] i, int s, int l, MeshTopology t, int sub, bool calc) { }
    }
    public class Shader : Object { public bool isSupported; public static Shader Find(string n) { return null; } }
    public class Texture : Object { }
    public class Texture2D : Texture { public static Texture2D whiteTexture; }
    public class Material : Object
    {
        public Material(Shader s) { }
        public Texture mainTexture;
        public bool HasProperty(string n) { return true; }
    }
    public static class Resources
    {
        public static T Load<T>(string p) where T : Object { return null; }
        public static T GetBuiltinResource<T>(string p) where T : Object { return null; }
    }
    public enum FontStyle { Normal, Bold }
    public struct CharacterInfo
    {
        public float advance, minX, maxX, minY, maxY; public Vector2 uvTopLeft, uvTopRight, uvBottomRight, uvBottomLeft;
    }
    public class Font : Object
    {
        public Material material;
        public static event Action<Font> textureRebuilt;
        public static Font CreateDynamicFontFromOSFont(string[] n, int s) { return null; }
        public void RequestCharactersInTexture(string c, int s, FontStyle st) { }
        public bool GetCharacterInfo(char c, out CharacterInfo i, int s, FontStyle st) { i = default(CharacterInfo); return true; }
    }
    public static class Graphics
    {
        public static void DrawMesh(Mesh m, Matrix4x4 mat, Material mt, int layer, Camera cam, int sub) { }
    }
    public struct Matrix4x4
    {
        public static Matrix4x4 Translate(Vector3 v) { return default(Matrix4x4); }
        public static Matrix4x4 Scale(Vector3 v) { return default(Matrix4x4); }
    }
    public struct Vector2
    {
        public float x, y;
        public Vector2(float x, float y) { this.x = x; this.y = y; }
        public static Vector2 zero;
        public static Vector2 Lerp(Vector2 a, Vector2 b, float t) { return a; }
        public static bool operator !=(Vector2 a, Vector2 b) { return a.x != b.x || a.y != b.y; }
        public static bool operator ==(Vector2 a, Vector2 b) { return a.x == b.x && a.y == b.y; }
        public override bool Equals(object o) { return false; }
        public override int GetHashCode() { return 0; }
    }
    public struct Vector3
    {
        public float x, y, z;
        public Vector3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        public static Vector3 zero, one;
        public static bool operator !=(Vector3 a, Vector3 b) { return a.x != b.x || a.y != b.y || a.z != b.z; }
        public static bool operator ==(Vector3 a, Vector3 b) { return a.x == b.x && a.y == b.y && a.z == b.z; }
        public override bool Equals(object o) { return false; }
        public override int GetHashCode() { return 0; }
    }
    public struct Color { public float r, g, b, a; public Color(float r, float g, float b, float a) { this.r = r; this.g = g; this.b = b; this.a = a; } public Color(float r, float g, float b) { this.r = r; this.g = g; this.b = b; a = 1; } public static Color black, white; }
    public struct Color32 { public Color32(byte r, byte g, byte b, byte a) { } }
    public struct Rect { public Rect(float x, float y, float w, float h) { this.x = x; this.y = y; width = w; height = h; } public float x, y, width, height; }
    public static class Mathf
    {
        public static float Min(float a, float b) { return a < b ? a : b; }
        public static float Max(float a, float b) { return a > b ? a : b; }
        public static int Max(int a, int b) { return a > b ? a : b; }
        public static float Abs(float a) { return a < 0 ? -a : a; }
        public static float Sign(float a) { return a < 0 ? -1 : 1; }
        public static float Clamp(float v, float a, float b) { return v < a ? a : v > b ? b : v; }
        public static int Clamp(int v, int a, int b) { return v < a ? a : v > b ? b : v; }
        public static int RoundToInt(float v) { return (int)Math.Round(v); }
    }
    public enum LogType { Error, Assert, Warning, Log, Exception }
    public static class Debug { public static void Log(object o) { } public static void LogError(object o) { } }
    public enum RuntimeInitializeLoadType { AfterSceneLoad }
    public class RuntimeInitializeOnLoadMethodAttribute : Attribute { public RuntimeInitializeOnLoadMethodAttribute(RuntimeInitializeLoadType t) { } }
    public static class SleepTimeout { public const int NeverSleep = -1; }
    public enum FullScreenMode { Windowed, FullScreenWindow }
    public static class Screen
    {
        public static int width, height; public static float dpi; public static bool fullScreen; public static FullScreenMode fullScreenMode; public static int sleepTimeout;
    }
    public static class Application
    {
        public static string persistentDataPath, unityVersion; public static bool isMobilePlatform, isEditor; public static int targetFrameRate; public static bool runInBackground;
        public static RuntimePlatform platform;
        public delegate void LogCallback(string c, string s, LogType t);
        public static event LogCallback logMessageReceivedThreaded;
    }
    public enum RuntimePlatform { Android }
    public static class QualitySettings { public static int vSyncCount, antiAliasing; }
    public static class SystemInfo { public static string operatingSystem; }
    public static class Time { public static float unscaledDeltaTime; public static int frameCount; }
    public static class Handheld { public static void Vibrate() { } }
    public static class GUI
    {
        public static Matrix4x4 matrix; public static Color color;
        public static void DrawTexture(Rect r, Texture t) { }
        public static void Box(Rect r, string s) { }
    }
    public static class GUILayout
    {
        public static void BeginArea(Rect r) { } public static void EndArea() { }
        public static string TextArea(string t, params GUILayoutOption[] o) { return t; }
        public static void Label(string t) { }
        public static Vector2 BeginScrollView(Vector2 v, params GUILayoutOption[] o) { return v; }
        public static void EndScrollView() { }
        public static void BeginHorizontal() { } public static void EndHorizontal() { }
        public static bool Button(string t, params GUILayoutOption[] o) { return false; }
        public static GUILayoutOption Height(float h) { return null; }
    }
    public class GUILayoutOption { }
    public enum KeyCode
    {
        None, Space, P, Z, S, G, H, A, D, W, Alpha0, Alpha1, Alpha2, Alpha3, Alpha4, Alpha5, Alpha6, Alpha7, Alpha8, Alpha9,
        Equals, Minus, LeftArrow, RightArrow, UpArrow, DownArrow, Escape, F11, Return, KeypadPlus, KeypadMinus,
        LeftControl, RightControl, LeftCommand, RightCommand, LeftAlt, RightAlt
    }
    public enum TouchPhase { Began, Moved, Stationary, Ended, Canceled }
    public struct Touch { public int fingerId; public Vector2 position; public TouchPhase phase; }
    public static class Input
    {
        public static bool simulateMouseWithTouches, multiTouchEnabled, mousePresent; public static int touchCount; public static Vector3 mousePosition; public static Vector2 mouseScrollDelta;
        public static Touch GetTouch(int i) { return default(Touch); }
        public static bool GetKeyDown(KeyCode k) { return false; } public static bool GetKey(KeyCode k) { return false; }
        public static bool GetMouseButtonDown(int b) { return false; } public static bool GetMouseButtonUp(int b) { return false; }
    }
    public class AndroidJavaObject : IDisposable
    {
        public void Dispose() { }
        public T Call<T>(string m, params object[] a) { return default(T); }
        public void Call(string m, params object[] a) { }
        public T GetStatic<T>(string f) { return default(T); }
    }
    public class AndroidJavaClass : AndroidJavaObject { public AndroidJavaClass(string n) { } }
}

namespace UnityEngine.Rendering { public enum IndexFormat { UInt16, UInt32 } }
