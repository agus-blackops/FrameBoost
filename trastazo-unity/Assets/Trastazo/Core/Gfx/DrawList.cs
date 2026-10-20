using System;
using System.Collections.Generic;

namespace Trastazo
{
    /**
     * What a Canvas produces for one frame: colored triangles in device pixels and text runs, in painting order.
     * The Unity layer turns it into a Mesh (and glyph quads); tests turn it into an SVG.
     */
    public sealed class DrawList
    {
        public const int TRIS = 0, TEXT = 1;

        public struct Op { public int kind, a, b; }   // TRIS: index range [a, b); TEXT: text item a

        public struct TextItem
        {
            public string s;
            public float x, y, size;
            public int color;
            public Paint.Align align;
            public bool bold;
            public float clipL, clipT, clipR, clipB;
            public bool clipped;
        }

        public float[] vx = new float[4096], vy = new float[4096];
        public int[] vc = new int[4096];
        public int nv;
        public int[] idx = new int[8192];
        public int ni;
        public readonly List<Op> ops = new List<Op>();
        public readonly List<TextItem> texts = new List<TextItem>();
        private int opStart;

        public void clear()
        {
            nv = 0; ni = 0; opStart = 0;
            ops.Clear();
            texts.Clear();
        }

        public int vertex(float x, float y, int c)
        {
            if (nv == vx.Length)
            {
                Array.Resize(ref vx, nv * 2); Array.Resize(ref vy, nv * 2); Array.Resize(ref vc, nv * 2);
            }
            vx[nv] = x; vy[nv] = y; vc[nv] = c;
            return nv++;
        }

        public void tri(int a, int b, int c)
        {
            if (ni + 3 > idx.Length) Array.Resize(ref idx, idx.Length * 2);
            idx[ni++] = a; idx[ni++] = b; idx[ni++] = c;
        }

        public void text(TextItem t)
        {
            flushTris();
            texts.Add(t);
            Op o; o.kind = TEXT; o.a = texts.Count - 1; o.b = 0;
            ops.Add(o);
        }

        /** Closes the open run of triangles (called before a text item and at the end of the frame). */
        public void flushTris()
        {
            if (ni > opStart)
            {
                Op o; o.kind = TRIS; o.a = opStart; o.b = ni;
                ops.Add(o);
                opStart = ni;
            }
        }
    }
}
