using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;
using TDrawList = Trastazo.DrawList;
using TPaint = Trastazo.Paint;

namespace TrastazoUnity
{
    /**
     * Turns the frame the game painted (a Trastazo.DrawList: colored triangles and text runs in painting order) into
     * one dynamic Mesh drawn with an orthographic camera: one sub-mesh per run, solid triangles and glyph quads in
     * alternation, each drawn a little deeper than the previous one so the order is kept.
     */
    public sealed class UnityCanvasRenderer
    {
        private const int Layer = 31;
        private Mesh mesh;
        private Material solidMat, textMat;
        private Camera cam;
        private Font font;
        private bool flippedY;
        private Vector3[] pos = new Vector3[16384];
        private Color32[] col = new Color32[16384];
        private Vector2[] uv = new Vector2[16384];
        private int[] idx = new int[65536];
        private int nv, ni;
        private struct Sub { public bool text; public int start, count; }
        private Sub[] subs = new Sub[512];
        private int nSubs;
        private readonly Dictionary<int, float> advance = new Dictionary<int, float>();
        private bool firstFont = true;

        public Camera camera { get { return cam; } }

        public void Init()
        {
            mesh = new Mesh();
            mesh.name = "Trastazo frame";
            mesh.indexFormat = IndexFormat.UInt32;
            mesh.MarkDynamic();

            Shader solid = Resources.Load<Shader>("TrastazoSolid");
            Shader text = Resources.Load<Shader>("TrastazoText");
            if (solid == null || !solid.isSupported) solid = Shader.Find("Sprites/Default");
            if (text == null || !text.isSupported) text = Shader.Find("GUI/Text Shader");
            solidMat = new Material(solid);
            textMat = new Material(text);
            if (solidMat.HasProperty("_MainTex")) solidMat.mainTexture = Texture2D.whiteTexture;

            GameObject go = new GameObject("Trastazo Camera");
            Object.DontDestroyOnLoad(go);
            cam = go.AddComponent<Camera>();
            cam.orthographic = true;
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = Color.black;
            cam.cullingMask = 1 << Layer;
            cam.nearClip = 0.1f;
            cam.farClip = 200f;
            cam.depth = 100f;
            cam.allowHDR = false;
            cam.allowMSAA = true;
            go.transform.position = new Vector3(0f, 0f, -10f);
            if (Object.FindObjectsOfType<AudioListener>().Length == 0) go.AddComponent<AudioListener>();

            CreateFont();
            Font.textureRebuilt += OnFontRebuilt;
            // the game measures text through Paint.measureText
            TPaint.measurer = Measure;
        }

        public void Shutdown()
        {
            Font.textureRebuilt -= OnFontRebuilt;
            TPaint.measurer = null;
        }

        private void OnFontRebuilt(Font f) { }

        private void CreateFont()
        {
            string[] wanted = { "Segoe UI", "Roboto", "Noto Sans", "Helvetica Neue", "Helvetica", "Arial", "DejaVu Sans", "Liberation Sans", "Ubuntu" };
            try { font = Font.CreateDynamicFontFromOSFont(wanted, 32); } catch (System.Exception) { font = null; }
            if (font == null)
            {
                try { font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); } catch (System.Exception) { }
            }
            if (font == null)
            {
                try { font = Resources.GetBuiltinResource<Font>("Arial.ttf"); } catch (System.Exception) { }
            }
            DetectGlyphOrientation();
        }

        /** CharacterInfo.minY/maxY are documented loosely: check which way a letter standing on the baseline points. */
        private void DetectGlyphOrientation()
        {
            flippedY = false;
            if (font == null) return;
            CharacterInfo ci;
            font.RequestCharactersInTexture("H", 32, FontStyle.Normal);
            if (font.GetCharacterInfo('H', out ci, 32, FontStyle.Normal))
                flippedY = ci.maxY <= 0.5f && ci.minY < -1f;
        }

        // ------------------------------------------------------------------ text metrics ---
        private static int Quant(float size) { return Mathf.Clamp(Mathf.RoundToInt(size), 6, 256); }

        private float Adv(char c, int px, FontStyle st)
        {
            int key = (c << 10) | (px << 1) | (st == FontStyle.Bold ? 1 : 0);
            float a;
            if (advance.TryGetValue(key, out a)) return a;
            CharacterInfo ci;
            a = 0f;
            if (font != null)
            {
                font.RequestCharactersInTexture(c.ToString(), px, st);
                if (font.GetCharacterInfo(c, out ci, px, st)) a = ci.advance;
            }
            else a = px * 0.55f;
            advance[key] = a;
            return a;
        }

        public float Measure(string s, float size, bool bold)
        {
            int px = Quant(size);
            FontStyle st = bold ? FontStyle.Bold : FontStyle.Normal;
            float w = 0f;
            for (int i = 0; i < s.Length; i++) w += Adv(s[i], px, st);
            return w * size / px;
        }

        // ------------------------------------------------------------------------ frame ---
        public void Render(TDrawList l, int w, int h)
        {
            cam.orthographicSize = h * 0.5f;
            Build(l, w, h);
            if (nv == 0 || nSubs == 0) return;
            mesh.Clear();
            mesh.indexFormat = IndexFormat.UInt32;
            mesh.SetVertices(pos, 0, nv);
            mesh.SetColors(col, 0, nv);
            mesh.SetUVs(0, uv, 0, nv);
            mesh.subMeshCount = nSubs;
            for (int i = 0; i < nSubs; i++)
                mesh.SetIndices(idx, subs[i].start, subs[i].count, MeshTopology.Triangles, i, false);
            mesh.bounds = new Bounds(Vector3.zero, new Vector3(w * 4f, h * 4f, 100f));
            cam.farClip = 50f + nSubs * 0.01f;
            if (font != null && textMat != null) textMat.mainTexture = font.material.mainTexture;
            for (int i = 0; i < nSubs; i++)
            {
                Matrix4x4 m = Matrix4x4.Translate(new Vector3(0f, 0f, (nSubs - i) * 0.01f));
                Graphics.DrawMesh(mesh, m, subs[i].text ? textMat : solidMat, Layer, cam, i);
            }
        }

        private void Grow(int addV, int addI)
        {
            if (nv + addV > pos.Length)
            {
                int n = Mathf.Max(nv + addV, pos.Length * 2);
                System.Array.Resize(ref pos, n); System.Array.Resize(ref col, n); System.Array.Resize(ref uv, n);
            }
            if (ni + addI > idx.Length) System.Array.Resize(ref idx, Mathf.Max(ni + addI, idx.Length * 2));
        }

        private void AddSub(bool text, int start, int count)
        {
            if (count == 0) return;
            if (nSubs > 0 && subs[nSubs - 1].text == text && subs[nSubs - 1].start + subs[nSubs - 1].count == start)
            {
                subs[nSubs - 1].count += count;
                return;
            }
            if (nSubs == subs.Length) System.Array.Resize(ref subs, nSubs * 2);
            subs[nSubs].text = text; subs[nSubs].start = start; subs[nSubs].count = count;
            nSubs++;
        }

        private static Color32 Argb(int c)
        {
            return new Color32((byte)((c >> 16) & 255), (byte)((c >> 8) & 255), (byte)(c & 255), (byte)((c >> 24) & 255));
        }

        private void Build(TDrawList l, int w, int h)
        {
            l.flushTris();
            nv = 0; ni = 0; nSubs = 0;
            float hw = w * 0.5f, hh = h * 0.5f;
            // 1. every glyph the frame needs goes into the font atlas first (it may be rebuilt once, not per glyph)
            if (font != null)
            {
                for (int i = 0; i < l.texts.Count; i++)
                {
                    TDrawList.TextItem t = l.texts[i];
                    font.RequestCharactersInTexture(t.s, Quant(t.size), t.bold ? FontStyle.Bold : FontStyle.Normal);
                }
            }
            // 2. triangles keep their vertices at the start of the buffer, text quads follow
            Grow(l.nv, 0);
            for (int i = 0; i < l.nv; i++)
            {
                pos[i] = new Vector3(l.vx[i] - hw, hh - l.vy[i], 0f);
                col[i] = Argb(l.vc[i]);
                uv[i] = Vector2.zero;
            }
            nv = l.nv;
            foreach (TDrawList.Op op in l.ops)
            {
                if (op.kind == TDrawList.TRIS)
                {
                    Grow(0, op.b - op.a);
                    System.Array.Copy(l.idx, op.a, idx, ni, op.b - op.a);
                    AddSub(false, ni, op.b - op.a);
                    ni += op.b - op.a;
                }
                else if (font != null)
                {
                    int before = ni;
                    AddText(l.texts[op.a], hw, hh);
                    AddSub(true, before, ni - before);
                }
            }
        }

        private void AddText(TDrawList.TextItem t, float hw, float hh)
        {
            int px = Quant(t.size);
            float sc = t.size / px;
            FontStyle st = t.bold ? FontStyle.Bold : FontStyle.Normal;
            float width = 0f;
            for (int i = 0; i < t.s.Length; i++) width += Adv(t.s[i], px, st) * sc;
            float pen = t.align == TPaint.Align.CENTER ? t.x - width * 0.5f : t.align == TPaint.Align.RIGHT ? t.x - width : t.x;
            Color32 color = Argb(t.color);
            for (int i = 0; i < t.s.Length; i++)
            {
                char ch = t.s[i];
                CharacterInfo ci;
                if (!font.GetCharacterInfo(ch, out ci, px, st)) continue;
                if (ch != ' ' && ci.maxX > ci.minX)
                {
                    float x0 = pen + ci.minX * sc, x1 = pen + ci.maxX * sc, yTop, yBot;
                    if (flippedY) { yTop = t.y + ci.minY * sc; yBot = t.y + ci.maxY * sc; }
                    else { yTop = t.y - ci.maxY * sc; yBot = t.y - ci.minY * sc; }
                    Vector2 uTL = ci.uvTopLeft, uTR = ci.uvTopRight, uBR = ci.uvBottomRight, uBL = ci.uvBottomLeft;
                    bool keep = true;
                    if (t.clipped)
                    {
                        if (x1 < t.clipL || x0 > t.clipR || yBot < t.clipT || yTop > t.clipB) keep = false;
                        else
                        {
                            // trim the quad to the clip rectangle, moving the texture coordinates with it
                            float fx0 = x1 > x0 ? Mathf.Max(0f, (t.clipL - x0) / (x1 - x0)) : 0f, fx1 = x1 > x0 ? Mathf.Max(0f, (x1 - t.clipR) / (x1 - x0)) : 0f;
                            float fy0 = yBot > yTop ? Mathf.Max(0f, (t.clipT - yTop) / (yBot - yTop)) : 0f, fy1 = yBot > yTop ? Mathf.Max(0f, (yBot - t.clipB) / (yBot - yTop)) : 0f;
                            if (fx0 > 0f || fx1 > 0f || fy0 > 0f || fy1 > 0f)
                            {
                                float nx0 = x0 + (x1 - x0) * fx0, nx1 = x1 - (x1 - x0) * fx1, ny0 = yTop + (yBot - yTop) * fy0, ny1 = yBot - (yBot - yTop) * fy1;
                                Vector2 tl = Lerp2(uTL, uTR, uBL, uBR, fx0, fy0), tr = Lerp2(uTL, uTR, uBL, uBR, 1f - fx1, fy0);
                                Vector2 br = Lerp2(uTL, uTR, uBL, uBR, 1f - fx1, 1f - fy1), bl = Lerp2(uTL, uTR, uBL, uBR, fx0, 1f - fy1);
                                uTL = tl; uTR = tr; uBR = br; uBL = bl;
                                x0 = nx0; x1 = nx1; yTop = ny0; yBot = ny1;
                            }
                        }
                    }
                    if (keep)
                    {
                        Grow(4, 6);
                        int b = nv;
                        pos[b] = new Vector3(x0 - hw, hh - yTop, 0f); uv[b] = uTL;
                        pos[b + 1] = new Vector3(x1 - hw, hh - yTop, 0f); uv[b + 1] = uTR;
                        pos[b + 2] = new Vector3(x1 - hw, hh - yBot, 0f); uv[b + 2] = uBR;
                        pos[b + 3] = new Vector3(x0 - hw, hh - yBot, 0f); uv[b + 3] = uBL;
                        col[b] = color; col[b + 1] = color; col[b + 2] = color; col[b + 3] = color;
                        idx[ni++] = b; idx[ni++] = b + 1; idx[ni++] = b + 2;
                        idx[ni++] = b; idx[ni++] = b + 2; idx[ni++] = b + 3;
                        nv += 4;
                    }
                }
                pen += ci.advance * sc;
            }
        }

        private static Vector2 Lerp2(Vector2 tl, Vector2 tr, Vector2 bl, Vector2 br, float fx, float fy)
        {
            Vector2 top = Vector2.Lerp(tl, tr, fx), bottom = Vector2.Lerp(bl, br, fx);
            return Vector2.Lerp(top, bottom, fy);
        }
    }
}
