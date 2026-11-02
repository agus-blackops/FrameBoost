using System;
using System.Collections.Generic;
using Godot;
using TDrawList = Trastazo.DrawList;
using TPaint = Trastazo.Paint;

namespace TrastazoGodot
{
    /**
     * Paints the frame the game produced (a Trastazo.DrawList: colored triangles in device pixels and text runs, in
     * painting order) onto a Godot CanvasItem. Triangles go straight to the RenderingServer as triangle arrays (one
     * per run, so they keep their order relative to the text); text uses a Godot font.
     */
    public sealed class GodotCanvasRenderer
    {
        private Font regular, bold;
        private readonly Dictionary<long, float> widthCache = new Dictionary<long, float>();
        private Vector2[] pts = new Vector2[0];
        private Color[] cols = new Color[0];

        public void Init()
        {
            string[] names = { "Segoe UI", "Roboto", "Noto Sans", "Helvetica Neue", "Helvetica", "Arial", "DejaVu Sans", "Liberation Sans", "Ubuntu" };
            SystemFont r = new SystemFont();
            r.FontNames = names;
            r.Antialiasing = TextServer.FontAntialiasing.Gray;
            r.Oversampling = 1.0f;
            SystemFont b = new SystemFont();
            b.FontNames = names;
            b.FontWeight = 700;
            b.Antialiasing = TextServer.FontAntialiasing.Gray;
            regular = r;
            bold = b;
            // the game measures its text through Paint.measureText
            TPaint.measurer = Measure;
        }

        public void Shutdown() { TPaint.measurer = null; }

        private static int Quant(float size) { return Math.Clamp((int)MathF.Round(size), 6, 256); }

        public float Measure(string s, float size, bool isBold)
        {
            if (s == null || s.Length == 0) return 0f;
            int px = Quant(size);
            Font f = isBold ? bold : regular;
            // the same string at the same size is measured many times per frame: remember it
            long key = ((long)s.GetHashCode() << 20) ^ ((long)px << 2) ^ (isBold ? 1L : 0L) ^ ((long)s.Length << 40);
            float w;
            if (widthCache.TryGetValue(key, out w)) return w * size / px;
            w = f.GetStringSize(s, HorizontalAlignment.Left, -1, px).X;
            if (widthCache.Count > 6000) widthCache.Clear();
            widthCache[key] = w;
            return w * size / px;
        }

        private static Color Argb(int c)
        {
            return new Color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, ((c >> 24) & 255) / 255f);
        }

        /** Draws the frame; must be called from the CanvasItem's _Draw. */
        public void Draw(CanvasItem item, TDrawList l)
        {
            l.flushTris();
            Rid rid = item.GetCanvasItem();
            // vertices are created in painting order, so each run of triangles uses a contiguous range of them
            foreach (TDrawList.Op op in l.ops)
            {
                if (op.kind == TDrawList.TRIS) DrawTris(rid, l, op.a, op.b);
                else DrawText(item, l.texts[op.a]);
            }
        }

        private void DrawTris(Rid rid, TDrawList l, int a, int b)
        {
            int n = b - a;
            if (n < 3) return;
            int lo = int.MaxValue, hi = -1;
            for (int i = a; i < b; i++)
            {
                int v = l.idx[i];
                if (v < lo) lo = v;
                if (v > hi) hi = v;
            }
            int nv = hi - lo + 1;
            int[] indices = new int[n];
            for (int i = 0; i < n; i++) indices[i] = l.idx[a + i] - lo;
            if (pts.Length < nv) { pts = new Vector2[nv * 2]; cols = new Color[nv * 2]; }
            Vector2[] p = new Vector2[nv];
            Color[] c = new Color[nv];
            for (int i = 0; i < nv; i++)
            {
                p[i] = new Vector2(l.vx[lo + i], l.vy[lo + i]);
                c[i] = Argb(l.vc[lo + i]);
            }
            RenderingServer.CanvasItemAddTriangleArray(rid, indices, p, c);
        }

        private void DrawText(CanvasItem item, TDrawList.TextItem t)
        {
            if (t.s == null || t.s.Length == 0) return;
            if (t.clipped && (t.y < t.clipT || t.y - t.size > t.clipB)) return;     // scrolled out of its list
            int px = Quant(t.size);
            Font f = t.bold ? bold : regular;
            float width = f.GetStringSize(t.s, HorizontalAlignment.Left, -1, px).X * t.size / px;
            float x = t.align == TPaint.Align.CENTER ? t.x - width * 0.5f : t.align == TPaint.Align.RIGHT ? t.x - width : t.x;
            if (t.clipped && (x > t.clipR || x + width < t.clipL)) return;
            item.DrawString(f, new Vector2(x, t.y), t.s, HorizontalAlignment.Left, -1, px, Argb(t.color));
        }
    }
}
