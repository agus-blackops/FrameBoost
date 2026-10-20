// Java-compatible runtime used by the C# code that tools/java2cs.py generates from the Java sources.
// Lowercase names (get/size/append...) are deliberate: they are what the translated code calls.
using System;
using System.Collections.Generic;
using System.Globalization;

namespace Trastazo
{
    public static class Math
    {
        public const float PI = 3.14159265358979f;
        public static int min(int a, int b) { return a < b ? a : b; }
        public static long min(long a, long b) { return a < b ? a : b; }
        public static float min(float a, float b) { return a < b ? a : b; }
        public static double min(double a, double b) { return a < b ? a : b; }
        public static int max(int a, int b) { return a > b ? a : b; }
        public static long max(long a, long b) { return a > b ? a : b; }
        public static float max(float a, float b) { return a > b ? a : b; }
        public static double max(double a, double b) { return a > b ? a : b; }
        public static int abs(int a) { return a < 0 ? -a : a; }
        public static long abs(long a) { return a < 0 ? -a : a; }
        public static float abs(float a) { return a <= 0f ? 0f - a : a; }
        public static double abs(double a) { return a <= 0.0 ? 0.0 - a : a; }
        public static double sin(double a) { return System.Math.Sin(a); }
        public static double cos(double a) { return System.Math.Cos(a); }
        public static double tan(double a) { return System.Math.Tan(a); }
        public static double asin(double a) { return System.Math.Asin(a); }
        public static double acos(double a) { return System.Math.Acos(a); }
        public static double atan(double a) { return System.Math.Atan(a); }
        public static double atan2(double y, double x) { return System.Math.Atan2(y, x); }
        public static double sqrt(double a) { return System.Math.Sqrt(a); }
        public static double exp(double a) { return System.Math.Exp(a); }
        public static double log(double a) { return System.Math.Log(a); }
        public static double pow(double a, double b) { return System.Math.Pow(a, b); }
        public static double floor(double a) { return System.Math.Floor(a); }
        public static double ceil(double a) { return System.Math.Ceiling(a); }
        public static double tanh(double a) { return System.Math.Tanh(a); }
        public static double hypot(double a, double b) { return System.Math.Sqrt(a * a + b * b); }
        public static double toDegrees(double a) { return a * 180.0 / PI; }
        public static double toRadians(double a) { return a / 180.0 * PI; }
        public static float signum(float a) { return a > 0f ? 1f : a < 0f ? -1f : a; }
        public static double signum(double a) { return a > 0.0 ? 1.0 : a < 0.0 ? -1.0 : a; }
        public static int round(float a) { return (int)System.Math.Floor(a + 0.5f); }
        public static long round(double a) { return (long)System.Math.Floor(a + 0.5); }
    }

    public static class Integer
    {
        public const int MAX_VALUE = int.MaxValue;
        public const int MIN_VALUE = int.MinValue;
        public static int parseInt(string s)
        {
            int v;
            if (s != null && int.TryParse(s.Trim(), NumberStyles.AllowLeadingSign, CultureInfo.InvariantCulture, out v)) return v;
            throw new NumberFormatException("For input string: \"" + s + "\"");
        }
        public static int reverseBytes(int v) { uint u = (uint)v; return (int)((u << 24) | ((u & 0xFF00u) << 8) | ((u >> 8) & 0xFF00u) | (u >> 24)); }
    }

    public static class Float
    {
        public const float MAX_VALUE = float.MaxValue;
        public const float MIN_VALUE = float.Epsilon;
        public static float parseFloat(string s)
        {
            float v;
            if (s != null && float.TryParse(s.Trim(), NumberStyles.Float, CultureInfo.InvariantCulture, out v)) return v;
            throw new NumberFormatException("For input string: \"" + s + "\"");
        }
        public static bool isNaN(float f) { return float.IsNaN(f); }
        public static bool isInfinite(float f) { return float.IsInfinity(f); }
        public static bool isFinite(float f) { return !float.IsNaN(f) && !float.IsInfinity(f); }
    }

    public static class Character
    {
        public static char toLowerCase(char c) { return char.ToLowerInvariant(c); }
        public static char toUpperCase(char c) { return char.ToUpperInvariant(c); }
        public static bool isDigit(char c) { return char.IsDigit(c); }
        public static bool isLetter(char c) { return char.IsLetter(c); }
    }

    public static class Sys
    {
        public static void arraycopy(Array src, int sp, Array dst, int dp, int n) { Array.Copy(src, sp, dst, dp, n); }
        public static void printf(string f, params object[] a) { Console.Write(Fmt.format(f, a)); }
        public static void println() { Console.WriteLine(); }
        public static void println(object o) { Console.WriteLine(o == null ? "null" : o is bool ? ((bool)o ? "true" : "false") : Convert.ToString(o, CultureInfo.InvariantCulture)); }
        public static void print(object o) { Console.Write(o == null ? "null" : Convert.ToString(o, CultureInfo.InvariantCulture)); }
        public static int ushr(int v, int n) { return (int)((uint)v >> n); }
        public static long ushr(long v, int n) { return (long)((ulong)v >> n); }
        public static long currentTimeMillis() { return DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(); }
        public static long nanoTime() { return System.Diagnostics.Stopwatch.GetTimestamp() * (1000000000L / System.Diagnostics.Stopwatch.Frequency); }
    }

    public static class Arrays
    {
        public static void sort<T>(T[] a) { Array.Sort(a); }
        public static void fill<T>(T[] a, T v) { for (int i = 0; i < a.Length; i++) a[i] = v; }
    }

    public static class JArr
    {
        /** new T[a][b] */
        public static T[][] make<T>(int a, int b)
        {
            T[][] r = new T[a][];
            for (int i = 0; i < a; i++) r[i] = new T[b];
            return r;
        }
        public static T[] clone<T>(this T[] a) { return (T[])a.Clone(); }
    }

    public class RuntimeException : Exception
    {
        public RuntimeException() { }
        public RuntimeException(string m) : base(m) { }
    }
    public class IllegalArgumentException : RuntimeException
    {
        public IllegalArgumentException() { }
        public IllegalArgumentException(string m) : base(m) { }
    }
    public class NumberFormatException : IllegalArgumentException
    {
        public NumberFormatException(string m) : base(m) { }
    }

    public static class Ext
    {
        // ---- strings
        public static int length(this string s) { return s.Length; }
        public static char charAt(this string s, int i) { return s[i]; }
        public static bool isEmpty(this string s) { return s.Length == 0; }
        public static string substring(this string s, int a) { return s.Substring(a); }
        public static string substring(this string s, int a, int b) { return s.Substring(a, b - a); }
        public static string trim(this string s) { return s.Trim(); }
        public static bool startsWith(this string s, string p) { return s.StartsWith(p, StringComparison.Ordinal); }
        public static bool endsWith(this string s, string p) { return s.EndsWith(p, StringComparison.Ordinal); }
        public static string toLowerCase(this string s) { return s.ToLowerInvariant(); }
        public static string toUpperCase(this string s) { return s.ToUpperInvariant(); }
        public static bool contains(this string s, string p) { return s.IndexOf(p, StringComparison.Ordinal) >= 0; }
        public static int indexOf(this string s, string p) { return s.IndexOf(p, StringComparison.Ordinal); }
        public static int indexOf(this string s, char c) { return s.IndexOf(c); }
        public static int lastIndexOf(this string s, char c) { return s.LastIndexOf(c); }
        public static string replace(this string s, string a, string b) { return s.Replace(a, b); }
        public static string replace(this string s, char a, char b) { return s.Replace(a, b); }
        public static string replaceAll(this string s, string rx, string rep) { return System.Text.RegularExpressions.Regex.Replace(s, rx, rep); }
        public static bool equalsIgnoreCase(this string s, string o) { return string.Equals(s, o, StringComparison.OrdinalIgnoreCase); }
        public static byte[] getBytes(this string s, string enc) { return System.Text.Encoding.UTF8.GetBytes(s); }
        public static bool matches(this string s, string rx) { return System.Text.RegularExpressions.Regex.IsMatch(s, "^(?:" + rx + ")$"); }
        /** Java String.split: regular expression, trailing empty strings removed. */
        public static string[] split(this string s, string rx)
        {
            string[] parts = System.Text.RegularExpressions.Regex.Split(s, rx);
            int n = parts.Length;
            while (n > 1 && parts[n - 1].Length == 0) n--;
            if (n == parts.Length) return parts;
            string[] r = new string[n];
            Array.Copy(parts, r, n);
            return r;
        }
        // ---- any object
        public static bool equals(this object a, object b) { return a.Equals(b); }
        public static string toString(this object o) { return o.ToString(); }
        public static string getMessage(this Exception e) { return e.Message; }
        public static void printStackTrace(this Exception e) { Console.Error.WriteLine(e); }
    }

    public sealed class StringBuilder
    {
        private readonly System.Text.StringBuilder b = new System.Text.StringBuilder();
        public StringBuilder() { }
        public StringBuilder(string s) { b.Append(s); }
        public StringBuilder append(string s) { b.Append(s); return this; }
        public StringBuilder append(char c) { b.Append(c); return this; }
        public StringBuilder append(int v) { b.Append(v.ToString(CultureInfo.InvariantCulture)); return this; }
        public StringBuilder append(long v) { b.Append(v.ToString(CultureInfo.InvariantCulture)); return this; }
        public StringBuilder append(float v) { b.Append(v.ToString(CultureInfo.InvariantCulture)); return this; }
        public StringBuilder append(double v) { b.Append(v.ToString(CultureInfo.InvariantCulture)); return this; }
        public StringBuilder append(bool v) { b.Append(v ? "true" : "false"); return this; }
        public StringBuilder append(object v) { b.Append(v == null ? "null" : v.ToString()); return this; }
        public StringBuilder append(StringBuilder v) { b.Append(v.ToString()); return this; }
        public int length() { return b.Length; }
        public char charAt(int i) { return b[i]; }
        public void setLength(int n) { b.Length = n; }
        public void setCharAt(int i, char c) { b[i] = c; }
        public StringBuilder insert(int i, string s) { b.Insert(i, s); return this; }
        public StringBuilder insert(int i, char c) { b.Insert(i, c); return this; }
        public StringBuilder deleteCharAt(int i) { b.Remove(i, 1); return this; }
        public string toString() { return b.ToString(); }
        public override string ToString() { return b.ToString(); }
    }

    public class JList<T> : List<T>
    {
        public JList() { }
        public JList(int cap) : base(cap) { }
        public JList(IEnumerable<T> c) : base(c) { }
        public int size() { return Count; }
        public T get(int i) { return this[i]; }
        public T set(int i, T v) { T o = this[i]; this[i] = v; return o; }
        public bool add(T v) { Add(v); return true; }
        public void add(int i, T v) { Insert(i, v); }
        public T remove(int i) { T o = this[i]; RemoveAt(i); return o; }
        public bool remove(T v) { return Remove(v); }
        public void clear() { Clear(); }
        public bool isEmpty() { return Count == 0; }
        public bool contains(T v) { return Contains(v); }
        public int indexOf(T v) { return IndexOf(v); }
        public int lastIndexOf(T v) { return LastIndexOf(v); }
        public bool addAll(IEnumerable<T> c) { AddRange(c); return true; }
        public T[] toArray(T[] a)
        {
            if (a.Length < Count) a = new T[Count];
            CopyTo(a);
            return a;
        }
    }

    public class JSet<T> : HashSet<T>
    {
        public bool add(T v) { return Add(v); }
        public bool contains(T v) { return Contains(v); }
        public bool remove(T v) { return Remove(v); }
        public void clear() { Clear(); }
        public int size() { return Count; }
        public bool isEmpty() { return Count == 0; }
    }

    public sealed class JMap<K, V> : Dictionary<K, V>
    {
        public V get(K k) { V v; return TryGetValue(k, out v) ? v : default(V); }
        public V put(K k, V v) { V o = get(k); this[k] = v; return o; }
        public bool containsKey(K k) { return ContainsKey(k); }
        public int size() { return Count; }
        public void clear() { Clear(); }
    }

    /** java.util.Random with the same linear congruential generator, so seeds give the same numbers. */
    public class Random
    {
        private long seed;
        private bool haveGauss;
        private double nextGauss;
        private static long uniq = 8682522807148012L;

        public Random() : this(unchecked(++uniq * 1181783497276652981L) ^ DateTime.UtcNow.Ticks) { }
        public Random(long s) { setSeed(s); }
        public void setSeed(long s) { seed = (s ^ 0x5DEECE66DL) & ((1L << 48) - 1); haveGauss = false; }
        private int next(int bits)
        {
            seed = unchecked(seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
            return (int)(seed >> (48 - bits));
        }
        public int nextInt() { return next(32); }
        public int nextInt(int bound)
        {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            if ((bound & -bound) == bound) return (int)((bound * (long)next(31)) >> 31);
            int bits, val;
            do { bits = next(31); val = bits % bound; } while (bits - val + (bound - 1) < 0);
            return val;
        }
        public long nextLong() { return unchecked(((long)next(32) << 32) + next(32)); }
        public bool nextBoolean() { return next(1) != 0; }
        public float nextFloat() { return next(24) / (float)(1 << 24); }
        public double nextDouble() { return (((long)next(26) << 27) + next(27)) * 1.1102230246251565E-16; }
        public double nextGaussian()
        {
            if (haveGauss) { haveGauss = false; return nextGauss; }
            double v1, v2, s;
            do
            {
                v1 = 2 * nextDouble() - 1;
                v2 = 2 * nextDouble() - 1;
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);
            double mul = System.Math.Sqrt(-2 * System.Math.Log(s) / s);
            nextGauss = v2 * mul;
            haveGauss = true;
            return v1 * mul;
        }
    }

    /** java.lang.String.format subset: %d %s %c %x %f %e %n %% with flags - 0 + space, width and precision. */
    public static class Fmt
    {
        public static string format(string f, params object[] args)
        {
            System.Text.StringBuilder o = new System.Text.StringBuilder();
            int ai = 0;
            for (int i = 0; i < f.Length; i++)
            {
                char c = f[i];
                if (c != '%') { o.Append(c); continue; }
                i++;
                bool left = false, zero = false, plus = false, space = false;
                for (; i < f.Length; i++)
                {
                    char fl = f[i];
                    if (fl == '-') left = true; else if (fl == '0') zero = true; else if (fl == '+') plus = true; else if (fl == ' ') space = true; else break;
                }
                int width = 0;
                while (i < f.Length && f[i] >= '0' && f[i] <= '9') width = width * 10 + (f[i++] - '0');
                int prec = -1;
                if (i < f.Length && f[i] == '.')
                {
                    i++; prec = 0;
                    while (i < f.Length && f[i] >= '0' && f[i] <= '9') prec = prec * 10 + (f[i++] - '0');
                }
                char conv = f[i];
                string s;
                if (conv == '%') { o.Append('%'); continue; }
                if (conv == 'n') { o.Append('\n'); continue; }
                object a = ai < args.Length ? args[ai++] : null;
                switch (conv)
                {
                    case 'd':
                        {
                            long v = Convert.ToInt64(a, CultureInfo.InvariantCulture);
                            s = System.Math.Abs((decimal)v).ToString(CultureInfo.InvariantCulture);
                            string sign = v < 0 ? "-" : plus ? "+" : space ? " " : "";
                            if (zero && !left && s.Length + sign.Length < width) s = new string('0', width - s.Length - sign.Length) + s;
                            s = sign + s;
                            break;
                        }
                    case 'x': s = Convert.ToInt64(a, CultureInfo.InvariantCulture).ToString("x", CultureInfo.InvariantCulture); break;
                    case 'f':
                    case 'e':
                        {
                            double v = Convert.ToDouble(a, CultureInfo.InvariantCulture);
                            if (double.IsNaN(v)) { s = "NaN"; break; }
                            if (double.IsInfinity(v)) { s = v > 0 ? "Infinity" : "-Infinity"; break; }
                            int p = prec < 0 ? 6 : prec;
                            // Java rounds half up on the decimal expansion
                            decimal dv;
                            string body;
                            if (conv == 'f' && System.Math.Abs(v) < 7.9e27 && (decimal.MaxValue > (decimal)System.Math.Abs(v)))
                            {
                                dv = System.Math.Round((decimal)System.Math.Abs(v), p > 15 ? 15 : p, MidpointRounding.AwayFromZero);
                                body = dv.ToString("F" + p, CultureInfo.InvariantCulture);
                            }
                            else body = System.Math.Abs(v).ToString(conv == 'f' ? "F" + p : "0." + new string('0', p) + "e+00", CultureInfo.InvariantCulture);
                            bool neg = v < 0 || (v == 0 && 1 / v < 0);
                            bool allZero = true;
                            for (int k = 0; k < body.Length; k++) if (body[k] >= '1' && body[k] <= '9') { allZero = false; break; }
                            string sign = neg && !(allZero && false) ? "-" : plus ? "+" : space ? " " : "";
                            if (neg && allZero) sign = "-";
                            if (zero && !left && body.Length + sign.Length < width) body = new string('0', width - body.Length - sign.Length) + body;
                            s = sign + body;
                            break;
                        }
                    case 'c': s = a is char ? ((char)a).ToString() : ((char)Convert.ToInt32(a, CultureInfo.InvariantCulture)).ToString(); break;
                    case 'b': s = (a is bool && (bool)a) ? "true" : "false"; break;
                    default:
                        s = a == null ? "null" : (a is bool ? ((bool)a ? "true" : "false") : Convert.ToString(a, CultureInfo.InvariantCulture));
                        if (prec >= 0 && s.Length > prec) s = s.Substring(0, prec);
                        break;
                }
                if (s.Length < width) s = left ? s.PadRight(width) : s.PadLeft(width);
                o.Append(s);
            }
            return o.ToString();
        }
    }

    /** java.io.File on top of System.IO. */
    public sealed class File : IComparable<File>
    {
        private readonly string path;
        public File(string p) { path = p; }
        public File(string dir, string name) { path = System.IO.Path.Combine(dir, name); }
        public File(File dir, string name) { path = System.IO.Path.Combine(dir.path, name); }
        public string getPath() { return path; }
        public string getAbsolutePath() { return System.IO.Path.GetFullPath(path); }
        public string getName() { return System.IO.Path.GetFileName(path); }
        public bool exists() { return System.IO.File.Exists(path) || System.IO.Directory.Exists(path); }
        public bool isDirectory() { return System.IO.Directory.Exists(path); }
        public long length() { return System.IO.File.Exists(path) ? new System.IO.FileInfo(path).Length : 0L; }
        public bool delete()
        {
            try
            {
                if (System.IO.File.Exists(path)) { System.IO.File.Delete(path); return true; }
                if (System.IO.Directory.Exists(path)) { System.IO.Directory.Delete(path); return true; }
            }
            catch (Exception) { }
            return false;
        }
        public bool mkdirs() { try { System.IO.Directory.CreateDirectory(path); return true; } catch (Exception) { return false; } }
        public bool createNewFile()
        {
            if (System.IO.File.Exists(path)) return false;
            System.IO.File.WriteAllBytes(path, new byte[0]);
            return true;
        }
        public bool renameTo(File to)
        {
            try { System.IO.File.Move(path, to.path); return true; } catch (Exception) { return false; }
        }
        public File[] listFiles()
        {
            if (!System.IO.Directory.Exists(path)) return null;
            string[] names = System.IO.Directory.GetFileSystemEntries(path);
            File[] r = new File[names.Length];
            for (int i = 0; i < names.Length; i++) r[i] = new File(names[i]);
            return r;
        }
        public int CompareTo(File o) { return string.CompareOrdinal(path, o.path); }
        public string readAllText() { return System.IO.File.ReadAllText(path, new System.Text.UTF8Encoding(false)); }
        public void writeAllText(string s) { System.IO.File.WriteAllText(path, s, new System.Text.UTF8Encoding(false)); }
        public override string ToString() { return path; }
    }

    public sealed class BufferedReader : IDisposable
    {
        private readonly System.IO.StreamReader r;
        private BufferedReader(System.IO.StreamReader r) { this.r = r; }
        public static BufferedReader open(File f) { return new BufferedReader(new System.IO.StreamReader(f.getPath(), new System.Text.UTF8Encoding(false))); }
        public string readLine() { return r.ReadLine(); }
        public void close() { r.Dispose(); }
        public void Dispose() { r.Dispose(); }
    }

    public sealed class PrintWriter : IDisposable
    {
        private readonly System.IO.StreamWriter w;
        private PrintWriter(System.IO.StreamWriter w) { this.w = w; }
        public static PrintWriter open(File f)
        {
            System.IO.StreamWriter sw = new System.IO.StreamWriter(f.getPath(), false, new System.Text.UTF8Encoding(false));
            sw.NewLine = "\n";
            return new PrintWriter(sw);
        }
        public void println(string s) { w.WriteLine(s); }
        public void println(StringBuilder s) { w.WriteLine(s.ToString()); }
        public void close() { w.Dispose(); }
        public void Dispose() { w.Dispose(); }
    }
}
