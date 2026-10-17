package android.opengl;

import java.nio.Buffer;
import java.nio.IntBuffer;

/** PC port: OpenGL ES is not used (the game draws with Java2D); these stubs only let GLEngine compile. */
public class GLES20 {
    public static final int GL_NO_ERROR = 0, GL_TRIANGLES = 4, GL_TRIANGLE_STRIP = 5, GL_FLOAT = 0x1406, GL_UNSIGNED_SHORT = 0x1403,
        GL_UNSIGNED_BYTE = 0x1401, GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_STATIC_DRAW = 0x88E4, GL_STREAM_DRAW = 0x88E0,
        GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601, GL_TEXTURE_WRAP_S = 0x2802,
        GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F, GL_RGBA = 0x1908, GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0,
        GL_FRAMEBUFFER_COMPLETE = 0x8CD5, GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30, GL_COMPILE_STATUS = 0x8B81,
        GL_LINK_STATUS = 0x8B82, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_SRC_ALPHA = 0x0302,
        GL_ONE_MINUS_SRC_ALPHA = 0x0303, GL_ONE = 1, GL_COLOR_BUFFER_BIT = 0x4000, GL_TEXTURE0 = 0x84C0;
    public static int glGetUniformLocation(int p, String n) { return 0; }
    public static void glGenBuffers(int n, int[] ids, int off) {  }
    public static void glGenFramebuffers(int n, int[] ids, int off) {  }
    public static void glGenTextures(int n, int[] ids, int off) {  }
    public static void glBindBuffer(int t, int b) {  }
    public static void glBufferData(int t, int size, Buffer data, int usage) {  }
    public static void glBindTexture(int t, int tex) {  }
    public static void glTexParameteri(int t, int n, int v) {  }
    public static void glTexImage2D(int t, int l, int ifmt, int w, int h, int b, int fmt, int type, Buffer px) {  }
    public static void glBindFramebuffer(int t, int f) {  }
    public static void glFramebufferTexture2D(int t, int a, int tt, int tex, int l) {  }
    public static int glCheckFramebufferStatus(int t) { return 0; }
    public static int glCreateShader(int t) { return 0; }
    public static void glShaderSource(int s, String src) {  }
    public static void glCompileShader(int s) {  }
    public static void glGetShaderiv(int s, int n, int[] out, int off) {  }
    public static String glGetShaderInfoLog(int s) { return ""; }
    public static int glCreateProgram() { return 0; }
    public static void glAttachShader(int p, int s) {  }
    public static void glBindAttribLocation(int p, int i, String n) {  }
    public static void glLinkProgram(int p) {  }
    public static void glGetProgramiv(int p, int n, int[] out, int off) {  }
    public static String glGetProgramInfoLog(int p) { return ""; }
    public static int glGetError() { return 0; }
    public static void glDisable(int c) {  }
    public static void glEnable(int c) {  }
    public static void glClearColor(float r, float g, float b, float a) {  }
    public static void glClear(int m) {  }
    public static void glBlendFunc(int s, int d) {  }
    public static void glViewport(int x, int y, int w, int h) {  }
    public static void glUseProgram(int p) {  }
    public static void glUniform4fv(int l, int n, float[] v, int off) {  }
    public static void glUniform3f(int l, float a, float b, float c) {  }
    public static void glUniform2f(int l, float a, float b) {  }
    public static void glUniform1f(int l, float a) {  }
    public static void glUniform1i(int l, int a) {  }
    public static void glActiveTexture(int t) {  }
    public static void glEnableVertexAttribArray(int i) {  }
    public static void glDisableVertexAttribArray(int i) {  }
    public static void glVertexAttribPointer(int i, int size, int type, boolean norm, int stride, int off) {  }
    public static void glDrawArrays(int m, int first, int count) {  }
    public static void glDrawElements(int m, int count, int type, int off) {  }
    public static void glReadPixels(int x, int y, int w, int h, int f, int t, Buffer px) {  }
}
