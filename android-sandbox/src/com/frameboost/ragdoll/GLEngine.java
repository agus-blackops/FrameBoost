package com.frameboost.ragdoll;

import android.graphics.Paint;
import android.graphics.RectF;
import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * GPU renderer (OpenGL ES 2.0).
 *
 * Every primitive is a quad whose fragment shader evaluates a signed distance field (capsule, ring,
 * rounded box, ellipse, sector, arc), which gives exact anti-aliasing at any zoom. Shapes are batched
 * into a handful of draw calls. A frame goes through three off-screen buffers:
 *   scene  -> full-resolution colour,
 *   glow   -> emissive shapes, blurred (bloom),
 *   light  -> additive dynamic point lights,
 * and a final composite pass applies lighting, bloom, shock-wave refraction, vignette, grading and flashes.
 */
final class GLEngine implements Draw2D {
    // ------------------------------------------------------------------------------- shaders ----
    private static final String SHAPE_VS =
            "attribute vec2 aPos; attribute vec2 aLocal; attribute vec4 aA; attribute vec4 aB; attribute vec4 aCol;\n"
            + "uniform vec2 uScreen;\n"
            + "varying vec2 vLocal; varying vec4 vA; varying vec4 vB; varying vec4 vCol;\n"
            + "void main() {\n"
            + "  vLocal = aLocal; vA = aA; vB = aB; vCol = aCol;\n"
            + "  gl_Position = vec4(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0, 0.0, 1.0);\n"
            + "}\n";

    private static final String SHAPE_FS =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
            + "varying vec2 vLocal; varying vec4 vA; varying vec4 vB; varying vec4 vCol;\n"
            + "const float TAU = 6.2831853;\n"
            + "float sector(vec2 p, float a0, float sweep) {\n"           // signed distance to an angular wedge
            + "  float ang = atan(p.y, p.x);\n"
            + "  float rel = mod(ang - a0, TAU);\n"
            + "  float len = length(p);\n"
            + "  if (rel <= sweep) return -min(rel, sweep - rel) * len;\n"
            + "  return min(rel - sweep, TAU - rel) * len;\n"
            + "}\n"
            + "void main() {\n"
            + "  float t = vB.x; float d;\n"
            + "  vec2 p = vLocal;\n"
            + "  if (t < 0.5) {\n"                                       // 0 capsule: p=(u,v), A=(length, radius)
            + "    float u = max(0.0, max(-p.x, p.x - vA.x));\n"
            + "    d = length(vec2(u, p.y)) - vA.y;\n"
            + "  } else if (t < 1.5) {\n"                                // 1 ring: A=(radius, half width)
            + "    d = abs(length(p) - vA.x) - vA.y;\n"
            + "  } else if (t < 2.5) {\n"                                // 2 rounded box: A=(hx, hy, radius)
            + "    vec2 q = abs(p) - vA.xy + vA.z;\n"
            + "    d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - vA.z;\n"
            + "  } else if (t < 3.5) {\n"                                // 3 ellipse: A=(rx, ry)
            + "    d = (length(p / vA.xy) - 1.0) * min(vA.x, vA.y);\n"
            + "  } else if (t < 4.5) {\n"                                // 4 pie: A=(r, a0, sweep)
            + "    d = max(length(p) - vA.x, sector(p, vA.y, vA.z));\n"
            + "  } else if (t < 5.5) {\n"                                // 5 arc stroke: A=(r, a0, sweep, half width)
            + "    d = max(abs(length(p) - vA.x) - vA.w, sector(p, vA.y, vA.z));\n"
            + "  } else if (t < 6.5) {\n"                                // 6 solid triangles
            + "    d = -1.0;\n"
            + "  } else {\n"                                             // 7 light: p in -1..1, soft falloff
            + "    float f = clamp(1.0 - length(p), 0.0, 1.0);\n"
            + "    gl_FragColor = vec4(vCol.rgb * vCol.a * f * f, 1.0);\n"
            + "    return;\n"
            + "  }\n"
            + "  float a = clamp(0.5 - d, 0.0, 1.0);\n"                 // one pixel of anti-aliasing
            + "  if (a <= 0.0) discard;\n"
            + "  gl_FragColor = vec4(vCol.rgb, vCol.a * a);\n"
            + "}\n";

    private static final String QUAD_VS =
            "attribute vec2 aPos; varying vec2 vUv;\n"
            + "void main() { vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }\n";

    private static final String BLUR_FS =
            "precision mediump float;\n"
            + "uniform sampler2D uTex; uniform vec2 uDir; varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec4 c = texture2D(uTex, vUv) * 0.227027;\n"
            + "  c += (texture2D(uTex, vUv + uDir * 1.3846153) + texture2D(uTex, vUv - uDir * 1.3846153)) * 0.3162162;\n"
            + "  c += (texture2D(uTex, vUv + uDir * 3.2307692) + texture2D(uTex, vUv - uDir * 3.2307692)) * 0.0702702;\n"
            + "  gl_FragColor = c;\n"
            + "}\n";

    private static final String COMPOSITE_FS =
            "precision mediump float;\n"
            + "uniform sampler2D uScene; uniform sampler2D uGlow; uniform sampler2D uLight;\n"
            + "uniform vec4 uRip[4]; uniform vec3 uAmbient; uniform float uAspect, uFlash, uBloom, uLightAmt, uVignette, uSat;\n"
            + "varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec2 off = vec2(0.0);\n"
            + "  for (int i = 0; i < 4; i++) {\n"                        // shock-wave refraction
            + "    vec4 r = uRip[i];\n"
            + "    if (r.w > 0.0) {\n"
            + "      vec2 dv = vUv - r.xy; dv.x *= uAspect;\n"
            + "      float dist = length(dv);\n"
            + "      float band = (dist - r.z) / max(0.02, r.z * 0.25);\n"
            + "      float w = r.w * exp(-band * band) * 0.018;\n"
            + "      vec2 dir = dv / max(dist, 0.0001); dir.x /= uAspect;\n"
            + "      off += dir * w;\n"
            + "    }\n"
            + "  }\n"
            + "  vec2 uv = vUv + off;\n"
            + "  vec3 c = texture2D(uScene, uv).rgb;\n"
            + "  vec3 L = uAmbient + texture2D(uLight, vUv).rgb * uLightAmt;\n"
            + "  c = c * min(L, vec3(1.6)) + texture2D(uLight, vUv).rgb * 0.035;\n"
            + "  vec3 g = texture2D(uGlow, vUv).rgb;\n  c += g * uBloom * (1.0 - c * 0.5);\n"
            + "  float l = dot(c, vec3(0.299, 0.587, 0.114));\n"
            + "  c = mix(vec3(l), c, uSat);\n"
            + "  vec2 q = vUv - 0.5;\n"
            + "  c *= 1.0 - uVignette * dot(q, q) * 1.9;\n"
            + "  c = mix(c, vec3(1.0, 0.97, 0.88), uFlash);\n"
            + "  gl_FragColor = vec4(c, 1.0);\n"
            + "}\n";

    // ------------------------------------------------------------------------------ settings ----
    float ambR = 1f, ambG = 1f, ambB = 1f;
    float flash, bloomAmt = 0.5f, lightAmt = 0.85f, vignette = 0.55f, saturation = 1.08f;
    boolean effects = true;          // bloom + lights (quality setting)
    int targetFbo = 0;               // where the final image goes (0 = the window)

    // -------------------------------------------------------------------------------- batches ----
    private static final int FLOATS = 16;               // pos2 local2 A4 B4 col4
    private static final int MAX_QUADS = 16000;

    private static final class Batch {
        float[] v = new float[4096 * FLOATS];
        int quads;
        void ensure(int more) {
            int need = (quads + more) * 4 * FLOATS;
            if (need > v.length) { float[] n = new float[Math.max(need, v.length * 2)]; System.arraycopy(v, 0, n, 0, quads * 4 * FLOATS); v = n; }
        }
    }

    private final Batch scene = new Batch(), glow = new Batch(), lights = new Batch();
    private boolean emissive;
    private FloatBuffer vbuf;
    private int vbo, ibo;

    // programs
    private int pShape, pBlur, pComp, quadVbo;
    private int uScreen, uBlurTex, uBlurDir, uCScene, uCGlow, uCLight, uCRip, uCAmb, uCAspect, uCFlash, uCBloom, uCLightAmt, uCVig, uCSat;
    // render targets
    private int sw, sh;
    private final int[] fbo = new int[5], tex = new int[5];        // 0 scene, 1 glowA, 2 glowB, 3 light
    private final int[] tw = new int[5], th = new int[5];
    boolean ready;
    String error;

    // ------------------------------------------------------------------------------ transform ----
    private final float[] stack = new float[6 * 32];
    private int sp;
    private float ma = 1, mb = 0, mc = 0, md = 1, me = 0, mf = 0;     // x' = a x + c y + e ; y' = b x + d y + f

    private final float[] rip = new float[16];
    private int ripN;

    // =============================================================================== lifecycle ====

    /** Creates every GL object. Throws with a readable message if the device can't run the engine. */
    void init() {
        pShape = program(SHAPE_VS, SHAPE_FS, new String[]{"aPos", "aLocal", "aA", "aB", "aCol"});
        pBlur = program(QUAD_VS, BLUR_FS, new String[]{"aPos"});
        pComp = program(QUAD_VS, COMPOSITE_FS, new String[]{"aPos"});
        uScreen = GLES20.glGetUniformLocation(pShape, "uScreen");
        uBlurTex = GLES20.glGetUniformLocation(pBlur, "uTex");
        uBlurDir = GLES20.glGetUniformLocation(pBlur, "uDir");
        uCScene = GLES20.glGetUniformLocation(pComp, "uScene");
        uCGlow = GLES20.glGetUniformLocation(pComp, "uGlow");
        uCLight = GLES20.glGetUniformLocation(pComp, "uLight");
        uCRip = GLES20.glGetUniformLocation(pComp, "uRip");
        uCAmb = GLES20.glGetUniformLocation(pComp, "uAmbient");
        uCAspect = GLES20.glGetUniformLocation(pComp, "uAspect");
        uCFlash = GLES20.glGetUniformLocation(pComp, "uFlash");
        uCBloom = GLES20.glGetUniformLocation(pComp, "uBloom");
        uCLightAmt = GLES20.glGetUniformLocation(pComp, "uLightAmt");
        uCVig = GLES20.glGetUniformLocation(pComp, "uVignette");
        uCSat = GLES20.glGetUniformLocation(pComp, "uSat");

        int[] ids = new int[3];
        GLES20.glGenBuffers(3, ids, 0);
        vbo = ids[0]; ibo = ids[1]; quadVbo = ids[2];
        ShortBuffer ib = ByteBuffer.allocateDirect(MAX_QUADS / 4 * 6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        // 16-bit indices address 65536 vertices = 16384 quads per draw call
        int quadsPerDraw = Math.min(MAX_QUADS, 16383);
        ib = ByteBuffer.allocateDirect(quadsPerDraw * 6 * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        for (int i = 0; i < quadsPerDraw; i++) {
            short o = (short) (i * 4);
            ib.put(o).put((short) (o + 1)).put((short) (o + 2)).put(o).put((short) (o + 2)).put((short) (o + 3));
        }
        ib.position(0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, quadsPerDraw * 6 * 2, ib, GLES20.GL_STATIC_DRAW);
        FloatBuffer q = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        q.put(new float[]{-1, -1, 1, -1, -1, 1, 1, 1}).position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, q, GLES20.GL_STATIC_DRAW);
        vbuf = ByteBuffer.allocateDirect(4096 * 4 * FLOATS * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        GLES20.glGenFramebuffers(5, fbo, 0);
        GLES20.glGenTextures(5, tex, 0);
        check("init");
        ready = true;
    }

    /** (Re)creates the off-screen buffers for a screen size. */
    void resize(int w, int h) {
        sw = w; sh = h;
        target(0, w, h);
        target(1, Math.max(1, w / 2), Math.max(1, h / 2));
        target(2, Math.max(1, w / 2), Math.max(1, h / 2));
        target(3, Math.max(1, w / 4), Math.max(1, h / 4));
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFbo);
        check("resize");
    }

    private void target(int i, int w, int h) {
        tw[i] = w; th[i] = h;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[i]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[i], 0);
        int st = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (st != GLES20.GL_FRAMEBUFFER_COMPLETE) throw new RuntimeException("Framebuffer " + i + " incompleto: 0x" + Integer.toHexString(st));
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("Shader: " + GLES20.glGetShaderInfoLog(s));
        return s;
    }

    private static int program(String vs, String fs, String[] attribs) {
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs));
        GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs));
        for (int i = 0; i < attribs.length; i++) GLES20.glBindAttribLocation(p, i, attribs[i]);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("Programa: " + GLES20.glGetProgramInfoLog(p));
        return p;
    }

    private static void check(String where) {
        int e = GLES20.glGetError();
        if (e != GLES20.GL_NO_ERROR) throw new RuntimeException("GL error 0x" + Integer.toHexString(e) + " en " + where);
    }

    // ================================================================================== frame ====

    void begin() {
        scene.quads = glow.quads = lights.quads = 0;
        ripN = 0;
        sp = 0;
        ma = 1; mb = 0; mc = 0; md = 1; me = 0; mf = 0;
        emissive = false;
    }

    /** Runs every pass and puts the final picture on targetFbo. */
    void end() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        // scene
        bindTarget(0);
        GLES20.glClearColor(0.05f, 0.06f, 0.08f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        drawBatch(scene, sw, sh);
        // glow (bloom)
        bindTarget(1);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (effects) {
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
            drawBatch(glow, sw, sh);
            GLES20.glDisable(GLES20.GL_BLEND);
            for (int it = 0; it < 2; it++) {
                blur(1, 2, 1f / tw[1], 0);
                blur(2, 1, 0, 1f / th[1]);
            }
            GLES20.glEnable(GLES20.GL_BLEND);
        }
        // lights
        bindTarget(3);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (effects) {
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
            drawBatch(lights, sw, sh);
        }
        // composite
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFbo);
        GLES20.glViewport(0, 0, sw, sh);
        GLES20.glUseProgram(pComp);
        bindTex(0, tex[0], uCScene);
        bindTex(1, tex[1], uCGlow);
        bindTex(2, tex[3], uCLight);
        for (int i = ripN; i < 4; i++) rip[i * 4 + 3] = 0f;
        GLES20.glUniform4fv(uCRip, 4, rip, 0);
        GLES20.glUniform3f(uCAmb, ambR, ambG, ambB);
        GLES20.glUniform1f(uCAspect, sw / (float) sh);
        GLES20.glUniform1f(uCFlash, flash);
        GLES20.glUniform1f(uCBloom, effects ? bloomAmt : 0f);
        GLES20.glUniform1f(uCLightAmt, effects ? lightAmt : 0f);
        GLES20.glUniform1f(uCVig, vignette);
        GLES20.glUniform1f(uCSat, saturation);
        fullscreen();
        check("frame");
    }

    private void bindTarget(int i) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[i]);
        GLES20.glViewport(0, 0, tw[i], th[i]);
    }

    private void bindTex(int unit, int t, int uniform) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t);
        GLES20.glUniform1i(uniform, unit);
    }

    private void blur(int from, int to, float dx, float dy) {
        bindTarget(to);
        GLES20.glUseProgram(pBlur);
        bindTex(0, tex[from], uBlurTex);
        GLES20.glUniform2f(uBlurDir, dx, dy);
        fullscreen();
    }

    private void fullscreen() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo);
        for (int i = 1; i < 5; i++) GLES20.glDisableVertexAttribArray(i);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 8, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void drawBatch(Batch b, int w, int h) {
        if (b.quads == 0) return;
        GLES20.glUseProgram(pShape);
        GLES20.glUniform2f(uScreen, w, h);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        int done = 0;
        while (done < b.quads) {
            int n = Math.min(16383, b.quads - done);
            int floats = n * 4 * FLOATS;
            if (vbuf.capacity() < floats) vbuf = ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vbuf.clear();
            vbuf.put(b.v, done * 4 * FLOATS, floats);
            vbuf.position(0);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floats * 4, vbuf, GLES20.GL_STREAM_DRAW);
            int stride = FLOATS * 4;
            for (int i = 0; i < 5; i++) GLES20.glEnableVertexAttribArray(i);
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, stride, 8);
            GLES20.glVertexAttribPointer(2, 4, GLES20.GL_FLOAT, false, stride, 16);
            GLES20.glVertexAttribPointer(3, 4, GLES20.GL_FLOAT, false, stride, 32);
            GLES20.glVertexAttribPointer(4, 4, GLES20.GL_FLOAT, false, stride, 48);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, n * 6, GLES20.GL_UNSIGNED_SHORT, 0);
            done += n;
        }
    }

    // ============================================================================ geometry out ====

    private float cr, cg, cb, ca;

    private void color(int c) {
        ca = (c >>> 24) / 255f; cr = ((c >> 16) & 255) / 255f; cg = ((c >> 8) & 255) / 255f; cb = (c & 255) / 255f;
    }

    /** Appends one quad: corners in screen pixels (x,y) with local coordinates (lx,ly). */
    private void quad(Batch b, float[] q, float type, float a0, float a1, float a2, float a3) {
        if (b.quads >= MAX_QUADS * 4) return;
        b.ensure(1);
        float[] v = b.v;
        int o = b.quads * 4 * FLOATS;
        for (int k = 0; k < 4; k++) {
            v[o] = q[k * 4]; v[o + 1] = q[k * 4 + 1]; v[o + 2] = q[k * 4 + 2]; v[o + 3] = q[k * 4 + 3];
            v[o + 4] = a0; v[o + 5] = a1; v[o + 6] = a2; v[o + 7] = a3;
            v[o + 8] = type; v[o + 9] = 0; v[o + 10] = 0; v[o + 11] = 0;
            v[o + 12] = cr; v[o + 13] = cg; v[o + 14] = cb; v[o + 15] = ca;
            o += FLOATS;
        }
        b.quads++;
    }

    private final float[] q = new float[16];

    private void emit(float type, float a0, float a1, float a2, float a3) {
        quad(scene, q, type, a0, a1, a2, a3);
        if (emissive && effects) quad(glow, q, type, a0, a1, a2, a3);
    }

    private float tx(float x, float y) { return ma * x + mc * y + me; }
    private float ty(float x, float y) { return mb * x + md * y + mf; }
    private float scaleOf() { return (float) Math.sqrt(ma * ma + mb * mb); }

    /** Quad around a shape centred at (x,y) in user space with half-size (hx,hy); local coords in pixels. */
    private void box(float x, float y, float hx, float hy) {
        float s = scaleOf(), pad = 1.5f / s;
        float ex = hx + pad, ey = hy + pad;
        float[] cx = {-ex, ex, ex, -ex}, cy = {-ey, -ey, ey, ey};
        for (int k = 0; k < 4; k++) {
            q[k * 4] = tx(x + cx[k], y + cy[k]);
            q[k * 4 + 1] = ty(x + cx[k], y + cy[k]);
            q[k * 4 + 2] = cx[k] * s;
            q[k * 4 + 3] = cy[k] * s;
        }
    }

    private void capsule(float x0, float y0, float x1, float y1, float r) {
        float X0 = tx(x0, y0), Y0 = ty(x0, y0), X1 = tx(x1, y1), Y1 = ty(x1, y1);
        float R = r * scaleOf();
        float dx = X1 - X0, dy = Y1 - Y0, L = (float) Math.sqrt(dx * dx + dy * dy);
        float ux = 1, uy = 0;
        if (L > 1e-4f) { ux = dx / L; uy = dy / L; }
        float nx = -uy, ny = ux, e = R + 1.5f;
        // corners in (u, v) along the segment
        float[] us = {-e, L + e, L + e, -e}, vs = {-e, -e, e, e};
        for (int k = 0; k < 4; k++) {
            q[k * 4] = X0 + ux * us[k] + nx * vs[k];
            q[k * 4 + 1] = Y0 + uy * us[k] + ny * vs[k];
            q[k * 4 + 2] = us[k];
            q[k * 4 + 3] = vs[k];
        }
        emit(0, L, R, 0, 0);
    }

    // =============================================================================== Draw2D ====

    @Override public void save() {
        if (sp + 6 > stack.length) return;
        stack[sp] = ma; stack[sp + 1] = mb; stack[sp + 2] = mc; stack[sp + 3] = md; stack[sp + 4] = me; stack[sp + 5] = mf;
        sp += 6;
    }

    @Override public void restore() {
        if (sp < 6) return;
        sp -= 6;
        ma = stack[sp]; mb = stack[sp + 1]; mc = stack[sp + 2]; md = stack[sp + 3]; me = stack[sp + 4]; mf = stack[sp + 5];
    }

    @Override public void translate(float x, float y) { me += ma * x + mc * y; mf += mb * x + md * y; }
    @Override public void scale(float sx, float sy) { ma *= sx; mb *= sx; mc *= sy; md *= sy; }
    @Override public void rotate(float deg) {
        double r = Math.toRadians(deg);
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        float a = ma * c + mc * s, b = mb * c + md * s, cc = -ma * s + mc * c, d = -mb * s + md * c;
        ma = a; mb = b; mc = cc; md = d;
    }

    @Override public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        color(p.getColor());
        capsule(x0, y0, x1, y1, Math.max(0.5f / scaleOf(), p.getStrokeWidth() * 0.5f));
    }

    @Override public void drawCircle(float x, float y, float r, Paint p) {
        color(p.getColor());
        if (p.getStyle() == Paint.Style.STROKE) {
            float hw = p.getStrokeWidth() * 0.5f;
            box(x, y, r + hw, r + hw);
            float s = scaleOf();
            emit(1, r * s, Math.max(0.5f, hw * s), 0, 0);
        } else capsule(x, y, x, y, r);
    }

    @Override public void drawRect(float l, float t, float r, float b, Paint p) {
        color(p.getColor());
        if (p.getStyle() == Paint.Style.STROKE) {
            drawLine(l, t, r, t, p); drawLine(r, t, r, b, p); drawLine(r, b, l, b, p); drawLine(l, b, l, t, p);
            return;
        }
        float hx = (r - l) * 0.5f, hy = (b - t) * 0.5f;
        box(l + hx, t + hy, hx, hy);
        float s = scaleOf();
        emit(2, hx * s, hy * s, 0, 0);
    }

    @Override public void drawRect(RectF r, Paint p) { drawRect(r.left, r.top, r.right, r.bottom, p); }

    @Override public void drawOval(RectF r, Paint p) {
        color(p.getColor());
        float hx = (r.right - r.left) * 0.5f, hy = (r.bottom - r.top) * 0.5f;
        box(r.left + hx, r.top + hy, hx, hy);
        float s = scaleOf();
        emit(3, Math.max(0.5f, hx * s), Math.max(0.5f, hy * s), 0, 0);
    }

    @Override public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        color(p.getColor());
        float hx = (r.right - r.left) * 0.5f, hy = (r.bottom - r.top) * 0.5f;
        box(r.left + hx, r.top + hy, hx, hy);
        float s = scaleOf();
        emit(2, hx * s, hy * s, Math.min(Math.min(hx, hy), rx) * s, 0);
    }

    @Override public void drawArc(RectF r, float start, float sweep, boolean center, Paint p) {
        color(p.getColor());
        float hx = (r.right - r.left) * 0.5f;
        float cx = r.left + hx, cy = (r.top + r.bottom) * 0.5f;
        if (sweep < 0) { start += sweep; sweep = -sweep; }
        float a0 = (float) Math.toRadians(start), sw0 = (float) Math.toRadians(Math.min(360f, sweep));
        float s = scaleOf();
        if (p.getStyle() == Paint.Style.STROKE) {
            float hw = p.getStrokeWidth() * 0.5f;
            box(cx, cy, hx + hw, hx + hw);
            emit(5, hx * s, a0, sw0, Math.max(0.5f, hw * s));
        } else {
            box(cx, cy, hx, hx);
            emit(4, hx * s, a0, sw0, 0);
        }
    }

    @Override public void drawPoly(float[] xy, int n, Paint p) {
        color(p.getColor());
        for (int i = 1; i + 1 < n; i++) {               // fan -> degenerate quads (triangle + repeated vertex)
            int[] idx = {0, i, i + 1, i + 1};
            for (int k = 0; k < 4; k++) {
                float x = xy[idx[k] * 2], y = xy[idx[k] * 2 + 1];
                q[k * 4] = tx(x, y); q[k * 4 + 1] = ty(x, y); q[k * 4 + 2] = 0; q[k * 4 + 3] = 0;
            }
            emit(6, 0, 0, 0, 0);
        }
    }

    @Override public void emissive(boolean on) { emissive = on; }

    @Override public void light(float x, float y, float radius, int c, float intensity) {
        if (!effects || intensity <= 0.01f) return;
        if (lights.quads > 400) return;
        float X = tx(x, y), Y = ty(x, y), R = radius * scaleOf();
        if (X + R < 0 || Y + R < 0 || X - R > sw || Y - R > sh) return;
        cr = ((c >> 16) & 255) / 255f; cg = ((c >> 8) & 255) / 255f; cb = (c & 255) / 255f; ca = Math.min(2f, intensity);
        float[] lx = {-1, 1, 1, -1}, ly = {-1, -1, 1, 1};
        for (int k = 0; k < 4; k++) {
            q[k * 4] = X + lx[k] * R; q[k * 4 + 1] = Y + ly[k] * R; q[k * 4 + 2] = lx[k]; q[k * 4 + 3] = ly[k];
        }
        quad(lights, q, 7, 0, 0, 0, 0);
    }

    @Override public boolean hasLighting() { return effects; }

    @Override public void ripple(float x, float y, float radius, float strength) {
        if (ripN >= 4 || sw == 0) return;
        float X = tx(x, y), Y = ty(x, y);
        rip[ripN * 4] = X / sw;
        rip[ripN * 4 + 1] = 1f - Y / sh;
        rip[ripN * 4 + 2] = radius * scaleOf() / sh;
        rip[ripN * 4 + 3] = strength;
        ripN++;
    }
}
