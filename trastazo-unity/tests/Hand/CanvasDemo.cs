using System;

namespace Trastazo
{
    /** Draws every primitive the game uses (fill, stroke, transforms, clip, text) to eyeball the tessellation. */
    public class CanvasDemo
    {
        public static void RunMain(string[] a)
        {
            BufferedImage img = new BufferedImage(900, 520, BufferedImage.TYPE_INT_RGB);
            Canvas c = img.canvas();
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(unchecked((int)0xFF20242c)); c.drawRect(0, 0, 900, 520, p);
            p.setColor(unchecked((int)0xFF2fd0b5)); c.drawRect(20, 20, 140, 90, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6); p.setColor(unchecked((int)0xFFf2b134)); c.drawRect(180, 20, 300, 90, p);
            p.setStyle(Paint.Style.FILL); p.setColor(unchecked((int)0xFFe0463c)); c.drawCircle(370, 55, 35, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(8); p.setColor(unchecked((int)0xFF3a9be0)); c.drawCircle(470, 55, 32, p);
            p.setStyle(Paint.Style.FILL); p.setColor(unchecked((int)0xFF9b59b6)); c.drawRoundRect(new RectF(540, 20, 700, 90), 20, 20, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); p.setColor(unchecked((int)0xFFffffff)); c.drawRoundRect(new RectF(720, 20, 880, 90), 20, 20, p);
            p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(14); p.setColor(unchecked((int)0xFF4cc36b)); c.drawLine(30, 150, 200, 190, p);
            p.setStrokeCap(Paint.Cap.BUTT); p.setStrokeWidth(10); p.setColor(unchecked((int)0xFFd0d4dc)); c.drawLine(230, 150, 400, 190, p);
            p.setStyle(Paint.Style.FILL); p.setColor(unchecked((int)0xFFf2b134));
            c.drawArc(new RectF(430, 130, 530, 230), -60, 240, true, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(6); p.setColor(unchecked((int)0xFFe0463c));
            c.drawArc(new RectF(560, 130, 660, 230), 20, 270, false, p);
            p.setStyle(Paint.Style.FILL); p.setColor(unchecked((int)0xFF2fd0b5));
            Path star = new Path();
            for (int i = 0; i < 10; i++)
            {
                float r = i % 2 == 0 ? 50 : 22, an = -1.5708f + i * 0.62832f;
                float x = 770 + r * (float)System.Math.Cos(an), y = 180 + r * (float)System.Math.Sin(an);
                if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
            }
            c.drawPath(star, p);
            // transforms
            c.save(); c.translate(120, 330); c.rotate(25); c.scale(1.5f, 1f);
            p.setColor(unchecked((int)0xFF3a9be0)); c.drawRect(-50, -25, 50, 25, p);
            p.setColor(unchecked((int)0xFFffffff)); c.drawCircle(0, 0, 12, p);
            c.restore();
            // translucent
            p.setColor(unchecked((int)0x88e0463c)); c.drawCircle(300, 330, 50, p);
            p.setColor(unchecked((int)0x882fd0b5)); c.drawCircle(340, 330, 50, p);
            // clip
            c.save(); c.clipRect(430, 280, 560, 380);
            p.setColor(unchecked((int)0xFFf2b134)); c.drawCircle(500, 330, 70, p);
            p.setColor(unchecked((int)0xFFffffff)); c.drawRect(400, 300, 700, 320, p);
            c.restore();
            // text
            p.setColor(unchecked((int)0xFFffffff)); p.setTextSize(28);
            c.drawText("Trastazo izquierda", 20, 440, p);
            p.setTextAlign(Paint.Align.CENTER); c.drawText("centro", 450, 440, p);
            p.setTextAlign(Paint.Align.RIGHT); c.drawText("derecha", 880, 440, p);
            p.setTextAlign(Paint.Align.LEFT); p.setFakeBoldText(true); p.setTextSize(20); c.drawText("Negrita · ñ á é í ó ú ¡!", 20, 480, p);
            img.write(a.Length > 0 ? a[0] + "/canvas_demo.svg" : "canvas_demo.svg");
        }
    }
}
