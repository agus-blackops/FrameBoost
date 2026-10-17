package android.graphics;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.HashMap;

/** Desktop version of android.graphics.Paint on top of Java2D. */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Align { LEFT, CENTER, RIGHT }
    public int color = 0xFF000000;
    public Style style = Style.FILL;
    public Cap cap = Cap.BUTT;
    public Align align = Align.LEFT;
    public float strokeWidth = 1, textSize = 12;
    public boolean bold;
    public Paint() {}
    public Paint(int flags) {}
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
    public void setFakeBoldText(boolean b) { bold = b; }

    private static final HashMap<Integer, Font> FONTS = new HashMap<>();
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /** Fonts are cached by size and weight: creating them every frame is slow. */
    static Font font(float size, boolean bold) {
        int px = Math.max(1, Math.round(size));
        int key = px * 2 + (bold ? 1 : 0);
        Font f = FONTS.get(key);
        if (f == null) { f = new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, px); FONTS.put(key, f); }
        return f;
    }

    public float measureText(String s) { return (float) font(textSize, bold).getStringBounds(s, FRC).getWidth(); }
}
