// Test harness: runs the translated game code headless and writes SVG frames (convert them to PNG with ImageMagick).
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace Trastazo
{
    /** A frame buffer made of a DrawList: stands in for java.awt.image.BufferedImage. */
    public class BufferedImage
    {
        public const int TYPE_INT_RGB = 1;
        public readonly int width, height;
        public readonly DrawList list = new DrawList();
        private Canvas cv;
        public BufferedImage(int w, int h, int type) { width = w; height = h; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public Canvas canvas() { if (cv == null) cv = new Canvas(list, width, height); cv.begin(); return cv; }
        public void write(string path)
        {
            if (cv != null) cv.end();
            Svg.write(list, width, height, path);
        }
    }

    public static class Svg
    {
        static string hex(int c) { return "#" + (c & 0xFFFFFF).ToString("x6"); }

        public static void write(DrawList l, int w, int h, string path)
        {
            if (path.EndsWith(".png")) path = path.Substring(0, path.Length - 4) + ".svg";
            System.Text.StringBuilder sb = new System.Text.StringBuilder();
            CultureInfo ci = CultureInfo.InvariantCulture;
            sb.Append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + w + "\" height=\"" + h + "\" viewBox=\"0 0 " + w + " " + h + "\">\n");
            sb.Append("<rect width=\"100%\" height=\"100%\" fill=\"#000\"/>\n");
            foreach (DrawList.Op op in l.ops)
            {
                if (op.kind == DrawList.TRIS)
                {
                    for (int i = op.a; i < op.b; i += 3)
                    {
                        int a = l.idx[i], b = l.idx[i + 1], c = l.idx[i + 2];
                        int col = l.vc[a];
                        int alpha = (int)((uint)col >> 24);
                        sb.Append("<path d=\"M" + l.vx[a].ToString("0.#", ci) + " " + l.vy[a].ToString("0.#", ci) + "L" + l.vx[b].ToString("0.#", ci) + " " + l.vy[b].ToString("0.#", ci)
                            + "L" + l.vx[c].ToString("0.#", ci) + " " + l.vy[c].ToString("0.#", ci) + "Z\" fill=\"" + hex(col) + "\"" + (alpha < 255 ? " fill-opacity=\"" + (alpha / 255f).ToString("0.##", ci) + "\"" : "") + "/>\n");
                    }
                }
                else
                {
                    DrawList.TextItem t = l.texts[op.a];
                    string anchor = t.align == Paint.Align.CENTER ? "middle" : t.align == Paint.Align.RIGHT ? "end" : "start";
                    int alpha = (int)((uint)t.color >> 24);
                    sb.Append("<text x=\"" + t.x.ToString("0.#", ci) + "\" y=\"" + t.y.ToString("0.#", ci) + "\" font-family=\"DejaVu Sans, sans-serif\" font-size=\"" + t.size.ToString("0.#", ci)
                        + "\" text-anchor=\"" + anchor + "\" fill=\"" + hex(t.color) + "\"" + (alpha < 255 ? " fill-opacity=\"" + (alpha / 255f).ToString("0.##", ci) + "\"" : "")
                        + (t.bold ? " font-weight=\"bold\"" : "") + ">" + System.Security.SecurityElement.Escape(t.s) + "</text>\n");
                }
            }
            sb.Append("</svg>\n");
            System.IO.File.WriteAllText(path, sb.ToString());
        }
    }

    public sealed class HookFn : Sim.Hook
    {
        private readonly Action<float> f;
        public HookFn(Action<float> f) { this.f = f; }
        public void before(float dt) { f(dt); }
    }

    public static class Harness
    {
        public const int W = 2400, H = 1080;
        public const float DENS = 2.75f;

        public static Game newGame()
        {
            Game g = new Game(null);
            g.resize(W, H, DENS);
            g.clearWorldForLoad();
            g.ui.stripOpen = false;
            g.screens.screen = Screens.NONE;
            g.settings.tutorialDone = true;
            return g;
        }

        public static void run(Game g, float seconds)
        {
            int n = (int)(seconds * 60);
            for (int i = 0; i < n; i++) g.update(1f / 60f);
        }

        public static void shot(Game g, string name)
        {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Canvas c = img.canvas();
            g.draw(c);
            img.write(name);
            Console.WriteLine("wrote " + name);
        }

        public static float sx(Game g, float wx) { return (wx - g.camX) * g.zoom + g.sw / 2f; }
        public static float sy(Game g, float wy) { return (wy - g.camY) * g.zoom + g.sh / 2f; }
        public static void tap(Game g, float wx, float wy) { g.touch(Game.DOWN, 0, sx(g, wx), sy(g, wy)); g.update(1f / 60); g.touch(Game.UP, 0, sx(g, wx), sy(g, wy)); g.update(1f / 60); }
        public static void tapScreen(Game g, float x, float y) { g.touch(Game.DOWN, 0, x, y); g.update(1f / 60); g.touch(Game.UP, 0, x, y); g.update(1f / 60); }
    }

    /** Plain drawing of a Sim (physics tuning without the game renderer). */
    public static class DebugView
    {
        public static BufferedImage snap(Sim s, float camX, float camY, float zoom, int w, int h)
        {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Canvas g = img.canvas();
            Paint p = new Paint();
            p.setColor(unchecked((int)0xFFdfe6ee)); g.drawRect(0, 0, w, h, p);
            int fy = (int)((s.floorY - camY) * zoom + h / 2f);
            p.setColor(unchecked((int)0xFF59616e)); g.drawRect(0, fy, w, h, p);
            foreach (RB b in s.bodies)
            {
                foreach (Shape sh in b.shapes)
                {
                    p.setColor(sh.color | unchecked((int)0xFF000000));
                    float cx = (sh.cx - camX) * zoom + w / 2f, cy = (sh.cy - camY) * zoom + h / 2f;
                    if (sh.type == Shape.CIRCLE) g.drawCircle(cx, cy, sh.r * zoom, p);
                    else if (sh.type == Shape.CAPSULE)
                    {
                        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(sh.r * 2 * zoom); p.setStrokeCap(Paint.Cap.ROUND);
                        g.drawLine(cx - sh.ax * sh.hl * zoom, cy - sh.ay * sh.hl * zoom, cx + sh.ax * sh.hl * zoom, cy + sh.ay * sh.hl * zoom, p);
                        p.setStyle(Paint.Style.FILL);
                    }
                    else
                    {
                        float ux = sh.ax * sh.hl, uy = sh.ay * sh.hl, vx = -sh.ay * sh.hh, vy = sh.ax * sh.hh;
                        Path path = new Path();
                        float[][] c = { new[] { -ux - vx, -uy - vy }, new[] { ux - vx, uy - vy }, new[] { ux + vx, uy + vy }, new[] { -ux + vx, -uy + vy } };
                        for (int i = 0; i < 4; i++) { float x = cx + c[i][0] * zoom, y = cy + c[i][1] * zoom; if (i == 0) path.moveTo(x, y); else path.lineTo(x, y); }
                        g.drawPath(path, p);
                    }
                }
            }
            return img;
        }

        public static void strip(List<BufferedImage> frames, string file)
        {
            int w = 0, h = 0;
            foreach (BufferedImage f in frames) { w += f.getWidth(); h = Math.max(h, f.getHeight()); }
            BufferedImage outp = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            int x = 0;
            foreach (BufferedImage f in frames)
            {
                DrawList src = f.list;
                src.flushTris();
                int baseV = outp.list.nv;
                for (int i = 0; i < src.nv; i++) outp.list.vertex(src.vx[i] + x, src.vy[i], src.vc[i]);
                int start = outp.list.ni;
                for (int i = 0; i < src.ni; i += 3) outp.list.tri(src.idx[i] + baseV, src.idx[i + 1] + baseV, src.idx[i + 2] + baseV);
                x += f.getWidth();
            }
            outp.list.flushTris();
            Svg.write(outp.list, w, h, file);
        }
    }

    public static class Program
    {
        public static int Main(string[] args)
        {
            System.Globalization.CultureInfo.DefaultThreadCurrentCulture = System.Globalization.CultureInfo.InvariantCulture;
            System.Threading.Thread.CurrentThread.CurrentCulture = System.Globalization.CultureInfo.InvariantCulture;
            if (args.Length == 0) { Console.WriteLine("usage: Tests <TestClass> [args]"); return 2; }
            Type t = Type.GetType("Trastazo." + args[0]);
            if (t == null) { Console.WriteLine("no such test " + args[0]); return 2; }
            string[] rest = new string[args.Length - 1];
            Array.Copy(args, 1, rest, 0, rest.Length);
            try
            {
                t.GetMethod("RunMain").Invoke(null, new object[] { rest });
            }
            catch (System.Reflection.TargetInvocationException e)
            {
                Console.WriteLine("EXCEPTION " + e.InnerException);
                return 1;
            }
            return 0;
        }
    }
}
