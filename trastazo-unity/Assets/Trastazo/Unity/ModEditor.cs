using UnityEngine;

namespace TrastazoUnity
{
    /** The workshop's mod editor: a text box over the game (IMGUI), like the Android dialog and the PC window. */
    public sealed class ModEditor : MonoBehaviour
    {
        private Trastazo.Game game;
        private Trastazo.Mod existing;
        private string text = "";
        private string error;
        private Vector2 helpScroll;
        private bool open;

        public bool IsOpen { get { return open; } }

        public void Open(Trastazo.Game g, Trastazo.Mod mod)
        {
            game = g;
            existing = mod;
            text = mod != null ? mod.source : "name Mi Mod\ndesc Lo que hace\ngravity 0.5\nonce spawn human 3\nevery 3 spawn crate\n";
            error = null;
            open = true;
        }

        private void OnGUI()
        {
            if (!open) return;
            float scale = Mathf.Clamp(Screen.height / 540f, 1f, 3f);
            GUI.matrix = Matrix4x4.Scale(new Vector3(scale, scale, 1f));
            float sw = Screen.width / scale, sh = Screen.height / scale;
            GUI.color = new Color(0f, 0f, 0f, 0.6f);
            GUI.DrawTexture(new Rect(0, 0, sw, sh), Texture2D.whiteTexture);
            GUI.color = Color.white;
            float bw = Mathf.Min(sw - 24f, 640f), bh = Mathf.Min(sh - 24f, 420f);
            Rect box = new Rect((sw - bw) / 2f, (sh - bh) / 2f, bw, bh);
            GUI.Box(box, existing == null ? "Crear mod" : "Editar mod");
            GUILayout.BeginArea(new Rect(box.x + 10f, box.y + 28f, box.width - 20f, box.height - 38f));
            text = GUILayout.TextArea(text, GUILayout.Height(box.height * 0.42f));
            if (error != null)
            {
                GUI.color = new Color(1f, 0.55f, 0.5f);
                GUILayout.Label(error);
                GUI.color = Color.white;
            }
            helpScroll = GUILayout.BeginScrollView(helpScroll, GUILayout.Height(box.height * 0.28f));
            GUILayout.Label(Trastazo.Mod.HELP);
            GUILayout.EndScrollView();
            GUILayout.BeginHorizontal();
            if (GUILayout.Button("Guardar", GUILayout.Height(30f)))
            {
                string err = game.saveMod(existing, text);
                if (err != null) error = err; else open = false;
            }
            if (GUILayout.Button("Cancelar", GUILayout.Height(30f))) open = false;
            if (existing != null && GUILayout.Button("Borrar", GUILayout.Height(30f))) { game.deleteMod(existing); open = false; }
            GUILayout.EndHorizontal();
            GUILayout.EndArea();
        }
    }
}
