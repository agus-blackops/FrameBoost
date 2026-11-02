using System;

namespace Trastazo
{
    /**
     * android.graphics.Canvas replacement: keeps the transform / clip stack and tessellates every shape into
     * triangles of a DrawList, in device pixels (y down). Anti-aliasing is left to the GPU (MSAA).
     */
    public class Canvas
    {
        public readonly DrawList list;
        private float a = 1f, b = 0f, c = 0f, d = 1f, e = 0f, f = 0f;   // x' = a*x + c*y + e ; y' = b*x + d*y + f
        private bool clip;
        private float cl, ct, cr, cb;
        private readonly float[] stack = new float[16 * 64];
        private readonly bool[] stackClip = new bool[64];
        private int sp;
        public int width, height;

        // scratch buffers: drawing allocates nothing per shape
        private float[] lx = new float[128], ly = new float[128];        // local-space points
        private float[] dx = new float[128], dy = new float[128];        // device-space points
        private float[] kx = new float[136], ky = new float[136], k2x = new float[136], k2y = new float[136], k3x = new float[136], k3y = new float[136];
        private int[] earV = new int[128];
        private readonly float[] q4x = new float[4], q4y = new float[4], t3x = new float[3], t3y = new float[3], e3x = new float[3], e3y = new float[3];

        private void ensure(int n)
        {
            if (lx.Length >= n) return;
            int m = System.Math.Max(n, lx.Length * 2);
            lx = new float[m]; ly = new float[m]; dx = new float[m]; dy = new float[m];
            kx = new float[m + 8]; ky = new float[m + 8]; k2x = new float[m + 8]; k2y = new float[m + 8]; k3x = new float[m + 8]; k3y = new float[m + 8];
            earV = new int[m];
        }

        public Canvas(DrawList list, int w, int h) { this.list = list; width = w; height = h; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }

        public void begin()
        {
            list.clear();
            a = 1f; b = 0f; c = 0f; d = 1f; e = 0f; f = 0f;
            clip = false; sp = 0;
        }

        public void end() { list.flushTris(); }

        // ------------------------------------------------------------------ state ---
        public int save()
        {
            int o = sp * 10;
            stack[o] = a; stack[o + 1] = b; stack[o + 2] = c; stack[o + 3] = d; stack[o + 4] = e; stack[o + 5] = f;
            stack[o + 6] = cl; stack[o + 7] = ct; stack[o + 8] = cr; stack[o + 9] = cb;
            stackClip[sp] = clip;
            sp++;
            return sp;
        }

        public void restore()
        {
            if (sp == 0) return;
            sp--;
            int o = sp * 10;
            a = stack[o]; b = stack[o + 1]; c = stack[o + 2]; d = stack[o + 3]; e = stack[o + 4]; f = stack[o + 5];
            cl = stack[o + 6]; ct = stack[o + 7]; cr = stack[o + 8]; cb = stack[o + 9];
            clip = stackClip[sp];
        }

        public void translate(float x, float y) { e += a * x + c * y; f += b * x + d * y; }
        public void scale(float x, float y) { a *= x; b *= x; c *= y; d *= y; }
        public void rotate(float deg)
        {
            float r = deg * 0.017453292519943295f, cs = (float)System.Math.Cos(r), sn = (float)System.Math.Sin(r);
            float na = a * cs + c * sn, nb = b * cs + d * sn, nc = -a * sn + c * cs, nd = -b * sn + d * cs;
            a = na; b = nb; c = nc; d = nd;
        }

        public void clipRect(float l, float t, float r, float bt)
        {
            float x0 = tx(l, t), y0 = ty(l, t), x1 = tx(r, bt), y1 = ty(r, bt);
            float nl = System.Math.Min(x0, x1), nr = System.Math.Max(x0, x1), nt = System.Math.Min(y0, y1), nb = System.Math.Max(y0, y1);
            if (clip) { nl = System.Math.Max(nl, cl); nt = System.Math.Max(nt, ct); nr = System.Math.Min(nr, cr); nb = System.Math.Min(nb, cb); }
            clip = true; cl = nl; ct = nt; cr = nr; cb = nb;
        }

        public void drawColor(int col) { fillDevRect(0, 0, width, height, col); }

        private float tx(float x, float y) { return a * x + c * y + e; }
        private float ty(float x, float y) { return b * x + d * y + f; }
        private float scaleFactor() { return (float)System.Math.Sqrt(System.Math.Abs(a * d - b * c)); }

        // ------------------------------------------------------------- primitives ---
        public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }

        public void drawRect(float l, float t, float r, float bt, Paint p)
        {
            if (p.style != Paint.Style.STROKE)
            {
                q4x[0] = tx(l, t); q4y[0] = ty(l, t);
                q4x[1] = tx(r, t); q4y[1] = ty(r, t);
                q4x[2] = tx(r, bt); q4y[2] = ty(r, bt);
                q4x[3] = tx(l, bt); q4y[3] = ty(l, bt);
                emitPoly(q4x, q4y, 4, p.color);
            }
            if (p.style != Paint.Style.FILL)
            {
                ensure(4);
                lx[0] = l; ly[0] = t; lx[1] = r; ly[1] = t; lx[2] = r; ly[2] = bt; lx[3] = l; ly[3] = bt;
                strokePoly(4, true, p);
            }
        }

        public void drawLine(float x0, float y0, float x1, float y1, Paint p)
        {
            ensure(2);
            lx[0] = x0; ly[0] = y0; lx[1] = x1; ly[1] = y1;
            strokePoly(2, false, p);
        }

        public void drawCircle(float cx, float cy, float r, Paint p) { drawOvalImpl(cx - r, cy - r, cx + r, cy + r, p); }

        public void drawOval(RectF r, Paint p) { drawOvalImpl(r.left, r.top, r.right, r.bottom, p); }

        private void drawOvalImpl(float l, float t, float r, float bt, Paint p)
        {
            float cx = (l + r) * 0.5f, cy = (t + bt) * 0.5f, rx = (r - l) * 0.5f, ry = (bt - t) * 0.5f;
            int n = segs(System.Math.Max(rx, ry));
            ensure(n);
            for (int i = 0; i < n; i++)
            {
                float an = i * 6.2831853f / n;
                lx[i] = cx + rx * (float)System.Math.Cos(an);
                ly[i] = cy + ry * (float)System.Math.Sin(an);
            }
            if (p.style != Paint.Style.STROKE) fillConvex(n, p.color);
            if (p.style != Paint.Style.FILL) strokePoly(n, true, p);
        }

        public void drawRoundRect(RectF r, float rx, float ry, Paint p)
        {
            float w = r.right - r.left, h = r.bottom - r.top;
            rx = System.Math.Min(rx, w * 0.5f); ry = System.Math.Min(ry, h * 0.5f);
            int q = System.Math.Max(2, segs(System.Math.Max(rx, ry)) / 4);
            int n = (q + 1) * 4;
            ensure(n);
            int k = 0;
            for (int corner = 0; corner < 4; corner++)
            {
                // increasing angle: bottom-right, bottom-left, top-left, top-right
                float cx = (corner == 0 || corner == 3) ? r.right - rx : r.left + rx;
                float cy = corner < 2 ? r.bottom - ry : r.top + ry;
                float start = 90f * corner;
                for (int i = 0; i <= q; i++)
                {
                    float an = (start + 90f * i / q) * 0.017453292f;
                    lx[k] = cx + rx * (float)System.Math.Cos(an);
                    ly[k] = cy + ry * (float)System.Math.Sin(an);
                    k++;
                }
            }
            if (p.style != Paint.Style.STROKE) fillConvex(n, p.color);
            if (p.style != Paint.Style.FILL) strokePoly(n, true, p);
        }

        public void drawArc(RectF r, float start, float sweep, bool useCenter, Paint p)
        {
            float cx = (r.left + r.right) * 0.5f, cy = (r.top + r.bottom) * 0.5f, rx = (r.right - r.left) * 0.5f, ry = (r.bottom - r.top) * 0.5f;
            if (sweep >= 360f) { drawOvalImpl(r.left, r.top, r.right, r.bottom, p); return; }
            int n = System.Math.Max(2, (int)(segs(System.Math.Max(rx, ry)) * System.Math.Abs(sweep) / 360f) + 1);
            int cnt = n + 1 + (useCenter ? 1 : 0);
            ensure(cnt);
            for (int i = 0; i <= n; i++)
            {
                float an = (start + sweep * i / n) * 0.017453292f;
                lx[i] = cx + rx * (float)System.Math.Cos(an);
                ly[i] = cy + ry * (float)System.Math.Sin(an);
            }
            if (useCenter) { lx[n + 1] = cx; ly[n + 1] = cy; }
            if (p.style != Paint.Style.STROKE)
            {
                if (useCenter) fillConvex(cnt, p.color);
                else triangulate(cnt, p.color);   // circular segment (the chord closes it)
            }
            if (p.style != Paint.Style.FILL) strokePoly(cnt, useCenter, p);
        }

        public void drawPath(Path path, Paint p)
        {
            for (int s = 0; s < path.subs; s++)
            {
                int from = path.starts[s], to = s + 1 < path.subs ? path.starts[s + 1] : path.n, n = to - from;
                if (n < 2) continue;
                ensure(n);
                System.Array.Copy(path.xs, from, lx, 0, n);
                System.Array.Copy(path.ys, from, ly, 0, n);
                if (p.style != Paint.Style.STROKE && n >= 3) triangulate(n, p.color);
                if (p.style != Paint.Style.FILL) strokePoly(n, true, p);
            }
        }

        public void drawText(string s, float x, float y, Paint p)
        {
            if (string.IsNullOrEmpty(s)) return;
            DrawList.TextItem t = new DrawList.TextItem();
            t.s = s; t.x = tx(x, y); t.y = ty(x, y); t.size = p.textSize * scaleFactor();
            t.color = p.color; t.align = p.align; t.bold = p.bold;
            t.clipped = clip; t.clipL = cl; t.clipT = ct; t.clipR = cr; t.clipB = cb;
            if (clip && (t.y < ct - t.size * 2 || t.y > cb + t.size * 2)) return;
            list.text(t);
        }

        // ---------------------------------------------------------- tessellation ---
        private int segs(float rLocal)
        {
            float r = System.Math.Max(0.5f, rLocal * scaleFactor());
            float step = (float)System.Math.Acos(System.Math.Max(0f, 1f - 0.3f / r));
            int n = (int)(6.2831853f / System.Math.Max(0.05f, step));
            return System.Math.Max(8, System.Math.Min(96, n));
        }

        private void fillDevRect(float l, float t, float r, float bt, int col)
        {
            q4x[0] = l; q4y[0] = t; q4x[1] = r; q4y[1] = t; q4x[2] = r; q4y[2] = bt; q4x[3] = l; q4y[3] = bt;
            emitPoly(q4x, q4y, 4, col);
        }

        /** Convex fill of the local points lx/ly[0..n). */
        private void fillConvex(int n, int col)
        {
            for (int i = 0; i < n; i++) { dx[i] = tx(lx[i], ly[i]); dy[i] = ty(lx[i], ly[i]); }
            emitPoly(dx, dy, n, col);
        }

        /** Ear-clipping triangulation of the simple polygon lx/ly[0..n). */
        private void triangulate(int n, int col)
        {
            for (int i = 0; i < n; i++) { dx[i] = tx(lx[i], ly[i]); dy[i] = ty(lx[i], ly[i]); }
            float[] x = dx, y = dy;
            // signed area picks the winding
            float area = 0f;
            for (int i = 0; i < n; i++) { int j = (i + 1) % n; area += x[i] * y[j] - x[j] * y[i]; }
            float sgn = area >= 0f ? 1f : -1f;
            int[] v = earV;
            for (int i = 0; i < n; i++) v[i] = i;
            int cnt = n, guard = n * n + 8;
            while (cnt > 3 && guard-- > 0)
            {
                bool cut = false;
                for (int i = 0; i < cnt; i++)
                {
                    int i0 = v[(i + cnt - 1) % cnt], i1 = v[i], i2 = v[(i + 1) % cnt];
                    float cross = (x[i1] - x[i0]) * (y[i2] - y[i0]) - (y[i1] - y[i0]) * (x[i2] - x[i0]);
                    if (cross * sgn <= 1e-6f) continue;
                    bool inside = false;
                    for (int k = 0; k < cnt && !inside; k++)
                    {
                        int q = v[k];
                        if (q == i0 || q == i1 || q == i2) continue;
                        float d1 = (x[i1] - x[i0]) * (y[q] - y[i0]) - (y[i1] - y[i0]) * (x[q] - x[i0]);
                        float d2 = (x[i2] - x[i1]) * (y[q] - y[i1]) - (y[i2] - y[i1]) * (x[q] - x[i1]);
                        float d3 = (x[i0] - x[i2]) * (y[q] - y[i2]) - (y[i0] - y[i2]) * (x[q] - x[i2]);
                        if (d1 * sgn >= 0f && d2 * sgn >= 0f && d3 * sgn >= 0f) inside = true;
                    }
                    if (inside) continue;
                    e3x[0] = x[i0]; e3x[1] = x[i1]; e3x[2] = x[i2];
                    e3y[0] = y[i0]; e3y[1] = y[i1]; e3y[2] = y[i2];
                    emitPoly(e3x, e3y, 3, col);
                    for (int k = i; k < cnt - 1; k++) v[k] = v[k + 1];
                    cnt--;
                    cut = true;
                    break;
                }
                if (!cut) break;
            }
            if (cnt == 3)
            {
                e3x[0] = x[v[0]]; e3x[1] = x[v[1]]; e3x[2] = x[v[2]];
                e3y[0] = y[v[0]]; e3y[1] = y[v[1]]; e3y[2] = y[v[2]];
                emitPoly(e3x, e3y, 3, col);
            }
        }

        /** Stroke of the local polyline lx/ly[0..n): a quad per segment plus a bevel wedge at the joints. */
        private void strokePoly(int n, bool closed, Paint p)
        {
            float sf = scaleFactor();
            float hw = System.Math.Max(0.5f, p.strokeWidth * sf * 0.5f);
            for (int i = 0; i < n; i++) { dx[i] = tx(lx[i], ly[i]); dy[i] = ty(lx[i], ly[i]); }
            float[] x = dx, y = dy;
            int segCount = closed ? n : n - 1;
            float[] qx = q4x, qy = q4y;
            for (int i = 0; i < segCount; i++)
            {
                int j = (i + 1) % n;
                float ddx = x[j] - x[i], ddy = y[j] - y[i];
                float len = (float)System.Math.Sqrt(ddx * ddx + ddy * ddy);
                if (len < 1e-4f) continue;
                float nx = -ddy / len * hw, ny = ddx / len * hw;
                float ex = 0f, ey = 0f;
                if (p.cap == Paint.Cap.SQUARE && !closed) { ex = ddx / len * hw; ey = ddy / len * hw; }
                qx[0] = x[i] + nx - ex; qy[0] = y[i] + ny - ey;
                qx[1] = x[j] + nx + ex; qy[1] = y[j] + ny + ey;
                qx[2] = x[j] - nx + ex; qy[2] = y[j] - ny + ey;
                qx[3] = x[i] - nx - ex; qy[3] = y[i] - ny - ey;
                emitPoly(qx, qy, 4, p.color);
                // joint with the next segment
                if (hw > 1.2f && (closed || i + 1 < segCount))
                {
                    int k = (j + 1) % n;
                    float edx = x[k] - x[j], edy = y[k] - y[j];
                    float l2 = (float)System.Math.Sqrt(edx * edx + edy * edy);
                    if (l2 > 1e-4f)
                    {
                        float mx = -edy / l2 * hw, my = edx / l2 * hw;
                        float cross = ddx * edy - ddy * edx;
                        float s = cross > 0f ? -1f : 1f;
                        t3x[0] = x[j]; t3x[1] = x[j] + s * nx; t3x[2] = x[j] + s * mx;
                        t3y[0] = y[j]; t3y[1] = y[j] + s * ny; t3y[2] = y[j] + s * my;
                        emitPoly(t3x, t3y, 3, p.color);
                    }
                }
            }
            if (p.cap == Paint.Cap.ROUND && !closed && hw > 1f)
            {
                float ax = x[0], ay = y[0], bx = x[n - 1], by = y[n - 1];
                disc(ax, ay, hw, p.color);
                disc(bx, by, hw, p.color);
            }
        }

        private float[] discX = new float[40], discY = new float[40];

        private void disc(float cx, float cy, float r, int col)
        {
            int n = System.Math.Max(6, System.Math.Min(32, (int)(r * 2.2f) + 6));
            for (int i = 0; i < n; i++)
            {
                float an = i * 6.2831853f / n;
                discX[i] = cx + r * (float)System.Math.Cos(an);
                discY[i] = cy + r * (float)System.Math.Sin(an);
            }
            emitPoly(discX, discY, n, col);
        }

        /** Adds a convex device-space polygon as a triangle fan, clipped to the clip rectangle. */
        private void emitPoly(float[] x, float[] y, int n, int col)
        {
            if (((uint)col >> 24) == 0) return;
            if (clip)
            {
                float minx = float.MaxValue, maxx = float.MinValue, miny = float.MaxValue, maxy = float.MinValue;
                for (int i = 0; i < n; i++)
                {
                    if (x[i] < minx) minx = x[i];
                    if (x[i] > maxx) maxx = x[i];
                    if (y[i] < miny) miny = y[i];
                    if (y[i] > maxy) maxy = y[i];
                }
                if (maxx < cl || minx > cr || maxy < ct || miny > cb) return;
                if (minx < cl || maxx > cr || miny < ct || maxy > cb)
                {
                    int m = clipToRect(x, y, n);
                    if (m < 3) return;
                    fan(kx, ky, m, col);
                    return;
                }
            }
            fan(x, y, n, col);
        }

        private void fan(float[] x, float[] y, int n, int col)
        {
            int first = list.vertex(x[0], y[0], col);
            int prev = list.vertex(x[1], y[1], col);
            for (int i = 2; i < n; i++)
            {
                int cur = list.vertex(x[i], y[i], col);
                list.tri(first, prev, cur);
                prev = cur;
            }
        }

        /** Sutherland-Hodgman against the clip rectangle; the result is left in kx/ky. Returns the point count. */
        private int clipToRect(float[] x, float[] y, int n)
        {
            if (kx.Length < n + 8) ensure(n + 8);
            float[] ax = k2x, ay = k2y, bx = k3x, by = k3y;
            System.Array.Copy(x, ax, n); System.Array.Copy(y, ay, n);
            int cnt = n;
            for (int edge = 0; edge < 4 && cnt > 0; edge++)
            {
                int m = 0;
                for (int i = 0; i < cnt; i++)
                {
                    int j = (i + 1) % cnt;
                    bool ia = inside(ax[i], ay[i], edge), ib = inside(ax[j], ay[j], edge);
                    if (ia) { bx[m] = ax[i]; by[m] = ay[i]; m++; }
                    if (ia != ib)
                    {
                        float t, ix, iy;
                        if (edge < 2)
                        {
                            float xe = edge == 0 ? cl : cr;
                            t = (xe - ax[i]) / (ax[j] - ax[i]);
                            ix = xe; iy = ay[i] + t * (ay[j] - ay[i]);
                        }
                        else
                        {
                            float ye = edge == 2 ? ct : cb;
                            t = (ye - ay[i]) / (ay[j] - ay[i]);
                            iy = ye; ix = ax[i] + t * (ax[j] - ax[i]);
                        }
                        bx[m] = ix; by[m] = iy; m++;
                    }
                }
                float[] sx = ax, sy = ay; ax = bx; ay = by; bx = sx; by = sy;
                cnt = m;
            }
            System.Array.Copy(ax, kx, cnt);
            System.Array.Copy(ay, ky, cnt);
            return cnt;
        }

        private bool inside(float px, float py, int edge)
        {
            switch (edge)
            {
                case 0: return px >= cl;
                case 1: return px <= cr;
                case 2: return py >= ct;
                default: return py <= cb;
            }
        }
    }
}
