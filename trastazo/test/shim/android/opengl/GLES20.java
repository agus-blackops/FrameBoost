package android.opengl;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengles.GLES30;

/** Desktop stand-in for android.opengl.GLES20 forwarding to LWJGL (Mesa llvmpipe) - test harness only. */
public class GLES20 {
    public static final int GL_NO_ERROR = 0, GL_TRIANGLES = 4, GL_TRIANGLE_STRIP = 5, GL_FLOAT = 0x1406, GL_UNSIGNED_SHORT = 0x1403,
        GL_UNSIGNED_BYTE = 0x1401, GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_STATIC_DRAW = 0x88E4, GL_STREAM_DRAW = 0x88E0,
        GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601, GL_TEXTURE_WRAP_S = 0x2802,
        GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F, GL_RGBA = 0x1908, GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0,
        GL_FRAMEBUFFER_COMPLETE = 0x8CD5, GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30, GL_COMPILE_STATUS = 0x8B81,
        GL_LINK_STATUS = 0x8B82, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_SRC_ALPHA = 0x0302,
        GL_ONE_MINUS_SRC_ALPHA = 0x0303, GL_ONE = 1, GL_COLOR_BUFFER_BIT = 0x4000, GL_TEXTURE0 = 0x84C0;

    public static int glGetUniformLocation(int p, String n) { return GLES30.glGetUniformLocation(p, n); }
    public static void glGenBuffers(int n, int[] ids, int off) { for (int i = 0; i < n; i++) ids[off + i] = GLES30.glGenBuffers(); }
    public static void glGenFramebuffers(int n, int[] ids, int off) { for (int i = 0; i < n; i++) ids[off + i] = GLES30.glGenFramebuffers(); }
    public static void glGenTextures(int n, int[] ids, int off) { for (int i = 0; i < n; i++) ids[off + i] = GLES30.glGenTextures(); }
    public static void glBindBuffer(int t, int b) { GLES30.glBindBuffer(t, b); }
    public static void glBufferData(int t, int size, Buffer data, int usage) {
        if (data instanceof FloatBuffer) GLES30.glBufferData(t, (FloatBuffer) data, usage);
        else if (data instanceof ShortBuffer) GLES30.glBufferData(t, (ShortBuffer) data, usage);
        else if (data instanceof ByteBuffer) GLES30.glBufferData(t, (ByteBuffer) data, usage);
        else GLES30.glBufferData(t, size, usage);
    }
    public static void glBindTexture(int t, int tex) { GLES30.glBindTexture(t, tex); }
    public static void glTexParameteri(int t, int n, int v) { GLES30.glTexParameteri(t, n, v); }
    public static void glTexImage2D(int t, int l, int ifmt, int w, int h, int b, int fmt, int type, Buffer px) {
        GLES30.glTexImage2D(t, l, ifmt, w, h, b, fmt, type, (ByteBuffer) px);
    }
    public static void glBindFramebuffer(int t, int f) { GLES30.glBindFramebuffer(t, f); }
    public static void glFramebufferTexture2D(int t, int a, int tt, int tex, int l) { GLES30.glFramebufferTexture2D(t, a, tt, tex, l); }
    public static int glCheckFramebufferStatus(int t) { return GLES30.glCheckFramebufferStatus(t); }
    public static int glCreateShader(int t) { return GLES30.glCreateShader(t); }
    public static void glShaderSource(int s, String src) { GLES30.glShaderSource(s, src); }
    public static void glCompileShader(int s) { GLES30.glCompileShader(s); }
    public static void glGetShaderiv(int s, int n, int[] out, int off) { out[off] = GLES30.glGetShaderi(s, n); }
    public static String glGetShaderInfoLog(int s) { return GLES30.glGetShaderInfoLog(s); }
    public static int glCreateProgram() { return GLES30.glCreateProgram(); }
    public static void glAttachShader(int p, int s) { GLES30.glAttachShader(p, s); }
    public static void glBindAttribLocation(int p, int i, String n) { GLES30.glBindAttribLocation(p, i, n); }
    public static void glLinkProgram(int p) { GLES30.glLinkProgram(p); }
    public static void glGetProgramiv(int p, int n, int[] out, int off) { out[off] = GLES30.glGetProgrami(p, n); }
    public static String glGetProgramInfoLog(int p) { return GLES30.glGetProgramInfoLog(p); }
    public static int glGetError() { return GLES30.glGetError(); }
    public static void glDisable(int c) { GLES30.glDisable(c); }
    public static void glEnable(int c) { GLES30.glEnable(c); }
    public static void glClearColor(float r, float g, float b, float a) { GLES30.glClearColor(r, g, b, a); }
    public static void glClear(int m) { GLES30.glClear(m); }
    public static void glBlendFunc(int s, int d) { GLES30.glBlendFunc(s, d); }
    public static void glViewport(int x, int y, int w, int h) { GLES30.glViewport(x, y, w, h); }
    public static void glUseProgram(int p) { GLES30.glUseProgram(p); }
    public static void glUniform4fv(int l, int n, float[] v, int off) {
        float[] c = new float[n * 4]; System.arraycopy(v, off, c, 0, n * 4); GLES30.glUniform4fv(l, c);
    }
    public static void glUniform3f(int l, float a, float b, float c) { GLES30.glUniform3f(l, a, b, c); }
    public static void glUniform2f(int l, float a, float b) { GLES30.glUniform2f(l, a, b); }
    public static void glUniform1f(int l, float a) { GLES30.glUniform1f(l, a); }
    public static void glUniform1i(int l, int a) { GLES30.glUniform1i(l, a); }
    public static void glActiveTexture(int t) { GLES30.glActiveTexture(t); }
    public static void glEnableVertexAttribArray(int i) { GLES30.glEnableVertexAttribArray(i); }
    public static void glDisableVertexAttribArray(int i) { GLES30.glDisableVertexAttribArray(i); }
    public static void glVertexAttribPointer(int i, int size, int type, boolean norm, int stride, int off) {
        GLES30.glVertexAttribPointer(i, size, type, norm, stride, (long) off);
    }
    public static void glDrawArrays(int m, int first, int count) { GLES30.glDrawArrays(m, first, count); }
    public static void glDrawElements(int m, int count, int type, int off) { GLES30.glDrawElements(m, count, type, (long) off); }
    public static void glReadPixels(int x, int y, int w, int h, int f, int t, Buffer px) { GLES30.glReadPixels(x, y, w, h, f, t, (ByteBuffer) px); }
}
