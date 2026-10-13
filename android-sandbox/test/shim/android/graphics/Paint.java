package android.graphics;
/** Desktop stand-in for android.graphics.Paint (test harness only). */
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
    public float measureText(String s) {
        java.awt.Font f = new java.awt.Font("SansSerif", bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, Math.max(1, (int) textSize));
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
        return (float) f.getStringBounds(s, frc).getWidth();
    }
}
