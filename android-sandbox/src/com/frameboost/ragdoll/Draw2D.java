package com.frameboost.ragdoll;

import android.graphics.Paint;
import android.graphics.RectF;

/**
 * Engine-independent drawing API. It mirrors the Canvas calls the world renderer uses (so the same
 * renderer runs on the GPU engine and on the Canvas fallback) and adds what only a real engine can do:
 * dynamic lights, emissive (bloom) shapes and convex polygons.
 */
interface Draw2D {
    void save();
    void restore();
    void translate(float x, float y);
    void scale(float sx, float sy);
    void rotate(float degrees);

    void drawLine(float x0, float y0, float x1, float y1, Paint p);
    void drawCircle(float x, float y, float r, Paint p);
    void drawRect(float l, float t, float r, float b, Paint p);
    void drawRect(RectF r, Paint p);
    void drawOval(RectF r, Paint p);
    void drawRoundRect(RectF r, float rx, float ry, Paint p);
    void drawArc(RectF r, float startDeg, float sweepDeg, boolean useCenter, Paint p);
    /** Filled convex polygon, xy pairs. */
    void drawPoly(float[] xy, int points, Paint p);

    /** Shapes drawn while emissive is on also glow (bloom). */
    void emissive(boolean on);
    /** A dynamic point light (world units). No-op on engines without lighting. */
    void light(float x, float y, float radius, int color, float intensity);
    /** Does this engine render lights and bloom? (lets the renderer skip fake glows) */
    boolean hasLighting();
    /** Screen-space refraction ripple (shock waves). No-op without post-processing. */
    void ripple(float x, float y, float radius, float strength);
}
