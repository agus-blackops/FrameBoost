using System;

namespace Trastazo
{
    /** Subset of android.graphics.Paint used by the game. */
    public class Paint
    {
        public const int ANTI_ALIAS_FLAG = 1;
        public enum Style { FILL, STROKE, FILL_AND_STROKE }
        public enum Cap { BUTT, ROUND, SQUARE }
        public enum Align { LEFT, CENTER, RIGHT }

        public int color = unchecked((int)0xFF000000);
        public Style style = Style.FILL;
        public Cap cap = Cap.BUTT;
        public Align align = Align.LEFT;
        public float strokeWidth = 1f, textSize = 12f;
        public bool bold;

        /** Text width provider (size, bold, text). The Unity layer replaces it with real font metrics. */
        public static Func<string, float, bool, float> measurer = DefaultMeasure;
        private static float DefaultMeasure(string s, float size, bool bold) { return s.Length * size * (bold ? 0.58f : 0.54f); }

        public Paint() { }
        public Paint(int flags) { }
        public void setColor(int c) { color = c; }
        public void setStyle(Style s) { style = s; }
        public void setStrokeCap(Cap c) { cap = c; }
        public void setStrokeWidth(float w) { strokeWidth = w; }
        public void setTextSize(float s) { textSize = s; }
        public float getTextSize() { return textSize; }
        public int getColor() { return color; }
        public Style getStyle() { return style; }
        public float getStrokeWidth() { return strokeWidth; }
        public Cap getStrokeCap() { return cap; }
        public void setTextAlign(Align a) { align = a; }
        public void setFakeBoldText(bool b) { bold = b; }
        public float measureText(string s) { return measurer(s, textSize, bold); }
    }

    public class RectF
    {
        public float left, top, right, bottom;
        public RectF() { }
        public RectF(float l, float t, float r, float b) { left = l; top = t; right = r; bottom = b; }
        public void set(float l, float t, float r, float b) { left = l; top = t; right = r; bottom = b; }
        public float width() { return right - left; }
        public float height() { return bottom - top; }
    }

    /** A polyline path (moveTo / lineTo / close); curves are not used by the game. */
    public class Path
    {
        public float[] xs = new float[32], ys = new float[32];
        public int n;
        /** Index in xs/ys where each sub-path starts. */
        public int[] starts = new int[4];
        public int subs;

        public void moveTo(float x, float y)
        {
            if (subs == starts.Length) Array.Resize(ref starts, subs * 2);
            starts[subs++] = n;
            add(x, y);
        }
        public void lineTo(float x, float y)
        {
            if (subs == 0) { moveTo(x, y); return; }
            add(x, y);
        }
        public void close() { }
        public void reset() { n = 0; subs = 0; }
        private void add(float x, float y)
        {
            if (n == xs.Length) { Array.Resize(ref xs, n * 2); Array.Resize(ref ys, n * 2); }
            xs[n] = x; ys[n] = y; n++;
        }
    }
}
