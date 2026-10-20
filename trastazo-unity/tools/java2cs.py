#!/usr/bin/env python3
"""
Mechanical Java -> C# translator for the Trastazo game sources.

It is not a general Java compiler: it handles the Java subset the game is written in (no lambdas, anonymous
classes, inner classes, generics methods or labels) and relies on the Java-compatible runtime in
Assets/Trastazo/Core/Compat (lowercase Math, JList, StringBuilder, Random, Fmt.format ...).

Usage: python3 tools/java2cs.py <java source dir> <output dir>
"""
import os
import re
import sys

# Java files that are translated (the platform classes - GL view, activity, SoundPool, AudioTrack, crash
# reporter - are written by hand for Unity).
SKIP = {"GLEngine.java", "GLGameView.java", "GameView.java", "MainActivity.java", "MusicPlayer.java", "Report.java", "Sfx.java"}

# static final primitives whose initialiser the C# compiler cannot fold: they become static readonly
NOT_CONST = {"HELP"}

# names used both for a field and for a method in one class (legal in Java, not in C#): the methods get a trailing _
CONFLICTS = set()
SEEN = {}

# Patches applied to the Java text before translating: ("lit", old, new) or ("range", start, end, new)
# (range replaces from the start marker up to, not including, the end marker).
PATCHES = {
    "Challenges.java": [
        ("rename_in", "boolean left = g.rnd.nextBoolean();", "left ? 1 : -1, 1f);", {"left": "fromLeft"}),
    ],
    "Fauna.java": [
        ("lit", "private static Person threat(World w, Prop a)", "private static Person threatOf(World w, Prop a)"),
        ("lit", "Person threat = threat(w, a);", "Person threat = threatOf(w, a);"),
    ],
    "Items.java": [
        ("lit", "java.util.List<String[]> mods", "java.util.ArrayList<String[]> mods"),
    ],
    "Joint.java": [
        ("rename_in", "float cdx = b.vx - b.w * rby", "b.w += iB * (rbx * py - rby * px);\n            return;",
         {"cdx": "mcdx", "cdy": "mcdy", "rx": "mrx", "ry": "mry", "det": "mdet", "px": "mpx", "py": "mpy"}),
    ],
    "Machines.java": [
        ("lit", "for (Joint j : spikes.joints) if (!j.removed) stuck++;", "for (Joint sj : spikes.joints) if (!sj.removed) stuck++;"),
        ("lit", "for (Joint j : spikes.joints) if (!j.removed && j.b == p.rb[part]) return;",
         "for (Joint sj : spikes.joints) if (!sj.removed && sj.b == p.rb[part]) return;"),
    ],
    "Mind.java": [
        ("rename_in", "Person t = nearestHostile(w, gx, gy, 1500f, null);", "} else m.target = null;",
         {"t": "nt", "dx": "ndx", "dy": "ndy"}),
    ],
    "Screens.java": [
        ("rename_in", "float bw = 74 * d, bh = 22 * d", "Paint.Align.CENTER, true);", {"bh": "tbh"}),
    ],
    "World.java": [
        ("rename_in", "Joint j = Joint.pin(prev, seg, px, py);", "sim.add(j);", {"j": "sj"}),
    ],
    "Game.java": [
        ("range", "    /** Per-frame settings for the GPU engine", "    /** Everything that is interface", ""),
    ],
    "ModEngine.java": [
        ("range", "    private static String read(File f) throws Exception {", "    /** Saves (or replaces) a user mod.",
         "    private static String read(File f) throws Exception { return f.readAllText(); }\n\n"
         "    private static void write(File f, String s) throws Exception { f.writeAllText(s); }\n\n"),
    ],
    "SceneIO.java": [
        ("lit", 'new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))', "BufferedReader.open(f)"),
        ("lit", 'new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))', "PrintWriter.open(f)"),
    ],
}

CSKW = ["ref", "out", "in", "base", "is", "as", "params", "event", "lock", "operator", "checked", "unchecked", "fixed",
        "delegate", "decimal", "readonly", "override", "virtual", "namespace", "implicit", "explicit", "extern", "sizeof",
        "stackalloc", "unsafe", "using", "ulong", "uint", "ushort", "sbyte"]
_unused = [k for k in CSKW if k not in ("foreach", "char16", "bool", "var", "sealed", "internal", "string", "object")]

TYPE_MAP = [
    (r"\bboolean\b", "bool"),
    (r"\bThrowable\b", "Exception"),
    (r"\bRuntimeException\b", "Exception"),
    (r"\bIllegalStateException\b", "InvalidOperationException"),
    (r"\bArrayIndexOutOfBoundsException\b", "IndexOutOfRangeException"),
    (r"\bIOException\b", "System.IO.IOException"),
    (r"\bArrayList\b", "JList"),
    (r"\bHashMap\b", "JMap"),
    (r"\bHashSet\b", "JSet"),
    (r"\bInteger\b(?!\s*\.)", "int"),
    (r"\bFloat\b(?!\s*\.)", "float"),
    (r"\bString\b(?!\s*\.)", "string"),
    (r"\bSystem\.arraycopy\b", "Sys.arraycopy"),
    (r"\bSystem\.currentTimeMillis\b", "Sys.currentTimeMillis"),
    (r"\bSystem\.nanoTime\b", "Sys.nanoTime"),
    (r"\bjava\.util\.Arrays\b", "Arrays"),
]


def mask(src):
    """Replaces strings, chars and comments by placeholders so regexes cannot touch their contents."""
    store = []
    out = []
    i, n = 0, len(src)
    while i < n:
        ch = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            store.append(src[i:j]); out.append("\x01%d\x01" % (len(store) - 1)); i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            store.append(src[i:j]); out.append("\x01%d\x01" % (len(store) - 1)); i = j
        elif ch == '"':
            j = i + 1
            while src[j] != '"':
                j += 2 if src[j] == "\\" else 1
            store.append(src[i:j + 1]); out.append("\x01%d\x01" % (len(store) - 1)); i = j + 1
        elif ch == "'":
            j = i + 1
            while src[j] != "'":
                j += 2 if src[j] == "\\" else 1
            store.append(src[i:j + 1]); out.append("\x01%d\x01" % (len(store) - 1)); i = j + 1
        else:
            out.append(ch); i += 1
    return "".join(out), store


def unmask(code, store):
    # placeholders may nest when a comment sits in a string (it cannot): one pass is enough
    return re.sub("\x01(\\d+)\x01", lambda m: store[int(m.group(1))], code)


def apply_patches(name, src):
    for p in PATCHES.get(name, []):
        if p[0] == "lit":
            if p[1] not in src:
                raise SystemExit("patch not found in %s: %s" % (name, p[1][:60]))
            src = src.replace(p[1], p[2])
        elif p[0] == "rename_block":
            a = src.index(p[1]) + len(p[1])
            a = src.index("{", a)
            depth, b = 0, a
            while True:
                if src[b] == "{":
                    depth += 1
                elif src[b] == "}":
                    depth -= 1
                    if depth == 0:
                        break
                b += 1
            seg = src[a:b + 1]
            for old, new in p[2].items():
                seg = re.sub(r"(?<![\w.])%s\b" % old, new, seg)
            src = src[:a] + seg + src[b + 1:]
        elif p[0] == "rename_in":
            a = src.index(p[1])
            b = src.index(p[2], a) + len(p[2])
            seg = src[a:b]
            for old, new in p[3].items():
                seg = re.sub(r"(?<![\w.])%s\b" % old, new, seg)
            src = src[:a] + seg + src[b:]
        elif p[0] == "range" and p[3] is not None:
            a = src.index(p[1])
            b = src.index(p[2], a)
            src = src[:a] + p[3] + src[b:]
    return src


def balanced(code, i):
    """Index just after the parenthesis group that starts at code[i] == '('."""
    depth = 0
    while i < len(code):
        if code[i] == "(":
            depth += 1
        elif code[i] == ")":
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    raise ValueError("unbalanced")


def fix_foreach(code):
    """for (T x : expr)  ->  foreach (T x in expr)"""
    pos = 0
    while True:
        m = re.compile(r"\bfor\s*\(").search(code, pos)
        if not m:
            return code
        end = balanced(code, m.end() - 1)
        inner = code[m.end():end - 1]
        mm = re.match(r"\s*([\w.<>\[\], ]+?)\s+(\w+)\s*:\s*(.*)$", inner, re.S)
        if mm and ";" not in inner:
            code = code[:m.start()] + "foreach (%s %s in %s)" % (mm.group(1), mm.group(2), mm.group(3)) + code[end:]
        pos = m.end()


def fix_nested_init(code):
    """T[][] x = {{..},{..}}  ->  inner groups become new T[] {..} (C# jagged arrays need it)."""
    pat = re.compile(r"(?:([\w.]+)\[\]\[\]\s+\w+\s*=\s*|new\s+([\w.]+)\[\]\[\]\s*)\{")
    pos = 0
    while True:
        m = pat.search(code, pos)
        if not m:
            return code
        t = m.group(1) or m.group(2)
        i = m.end() - 1
        depth, out, j = 0, [], i
        while j < len(code):
            ch = code[j]
            if ch == "{":
                depth += 1
                if depth == 2:
                    out.append("new %s[] " % t)
            elif ch == "}":
                depth -= 1
            out.append(ch)
            j += 1
            if depth == 0:
                break
        code = code[:i] + "".join(out) + code[j:]
        pos = i + 1


def fix_switch_end(code):
    """Java lets the last switch section run off the end; C# wants a break."""
    pos = 0
    pat = re.compile(r"\bswitch\s*\(")
    while True:
        m = pat.search(code, pos)
        if not m:
            return code
        pos = m.end()
        try:
            e = balanced(code, m.end() - 1)
        except ValueError:
            continue
        mm = re.match(r"\s*\{", code[e:])
        if not mm:
            continue
        start = e + mm.end() - 1
        depth, j = 0, start
        while j < len(code):
            if code[j] == "{":
                depth += 1
            elif code[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = code[start + 1:j]
        tail = re.sub(r"\x01\d+\x01", "", body).rstrip()
        if re.search(r"\b(break|continue|return|throw)\b[^;{}]*;\s*$", tail) or tail.endswith(":") or tail == "":
            continue
        code = code[:j] + " break; " + code[j:]


def fix_hex(code):
    def rep(m):
        digits, suffix = m.group(1), m.group(2)
        if suffix in ("L", "l"):
            if len(digits) >= 16 and int(digits[0], 16) >= 8:
                return "unchecked((long)0x%sUL)" % digits
            return "0x%sL" % digits
        if len(digits) == 8 and int(digits[0], 16) >= 8:
            return "unchecked((int)0x%sU)" % digits
        return m.group(0)
    return re.sub(r"\b0[xX]([0-9a-fA-F]+)([lL]?)\b", rep, code)


def fix_new_array_2d(code):
    # new T[a][b]  ->  JArr.make<T>(a, b)     (new T[a][] stays: jagged arrays are the same in C#)
    pat = re.compile(r"new\s+([\w.]+)\s*\[([^\[\]]+)\]\s*\[([^\[\]]+)\]")
    return pat.sub(lambda m: "JArr.make<%s>(%s, %s)" % (m.group(1), m.group(2), m.group(3)), code)


def fix_diamond(code):
    # new ArrayList<>(...) takes its type arguments from the declaration on the same statement
    lines = code.split("\n")
    out = []
    for ln in lines:
        if "<>" in ln:
            m = re.search(r"\b(JList|JMap|JSet)<([^=;]*?)>\s+\w+\s*=", ln) or re.search(r"\b(JList|JMap|JSet)<(.*?)>\s+\w+\s*(=|;)", ln)
            if m:
                targs = m.group(2)
                ln = ln.replace("<>", "<" + targs + ">")
        out.append(ln)
    return "\n".join(out)


def fix_ctor_chain(code, cname):
    # Name(args) { this(...); ... }  ->  Name(args) : this(...) { ... }
    pat = re.compile(r"\b%s\s*\(" % re.escape(cname))
    pos = 0
    while True:
        m = pat.search(code, pos)
        if not m:
            return code
        pos = m.end()
        try:
            end = balanced(code, m.end() - 1)
        except ValueError:
            continue
        rest = code[end:]
        m2 = re.match(r"\s*\{\s*(this|super)\s*\(", rest)
        if not m2:
            continue
        a_start = end + m2.end() - 1
        a_end = balanced(code, a_start)
        semi = code.index(";", a_end)
        kw = "this" if m2.group(1) == "this" else "base"
        args = code[a_start:a_end]
        head = code[:end] + " : " + kw + args + " {"
        code = head + code[semi + 1:]
        pos = len(head)


def rename_keywords(code):
    for k in CSKW:
        code = re.sub(r"(?<![\w$])%s(?![\w$])" % k, k + "_", code)
    return code


def classify(pending, top):
    p = pending.strip()
    if re.search(r"(^|[\s)])interface\s+\w+", p) and "(" not in p.split("interface")[0]:
        return "iface"
    if re.search(r"(^|\s)(class|enum)\s+\w+", p):
        return "class"
    if top == "class" and "(" in p and "=" not in p.split("(")[0]:
        return "method"
    return "other"


def members(code, base_methods, class_info):
    """Adds access modifiers / const / virtual / override to the declarations in class bodies."""
    lines = code.split("\n")
    out = []
    stack = []            # (kind, class name)
    pending = ""
    override_next = False
    for ln in lines:
        s = ln.strip()
        if re.match(r"^\x01\d+\x01$", s):
            out.append(ln)
            continue
        top = stack[-1][0] if stack else "top"
        starting = pending.strip() == ""
        new = ln
        if starting and s:
            indent = ln[:len(ln) - len(ln.lstrip())]
            if s.startswith("@Override"):
                override_next = True
                s2 = s[len("@Override"):].strip()
                if not s2:
                    out.append("")
                    continue
                s = s2
                ln = indent + s
            if s.startswith("@"):
                m = re.match(r"@\w+(\([^)]*\))?\s*(.*)", s)
                s = m.group(2)
                ln = indent + s
                if not s:
                    out.append("")
                    continue
            new = ln
            if top == "class" and s.count(";") > 1 and "{" not in s and "(" not in s.split(";")[0]:
                parts = [x.strip() for x in s.split(";") if x.strip()]
                if len(parts) > 1 and all(not x.startswith("\x01") or True for x in parts):
                    new = indent + " ".join(decl(indent + x + ("" if x.startswith("\x01") else ";"), x + ("" if x.startswith("\x01") else ";"), "", top, stack, base_methods, class_info, False) for x in parts)
                    out.append(new)
                    for ch in re.sub("\x01\\d+\x01", "", ln):
                        if ch == ";":
                            pending = ""
                    continue
            if top in ("top", "class"):
                new = decl(ln, s, indent, top, stack, base_methods, class_info, override_next)
                if re.match(r".*[;{]\s*$", s) or True:
                    pass
                override_next = False if re.search(r"[({;]", s) else override_next
        out.append(new)
        # track braces on the original line text (comments and strings do not count)
        for ch in re.sub("\x01\\d+\x01", "", ln):
            if ch == "{":
                kind = classify(pending, top if not stack else stack[-1][0])
                name = None
                if kind in ("class", "iface"):
                    mm = re.search(r"(?:class|interface|enum)\s+(\w+)", pending)
                    name = mm.group(1) if mm else None
                stack.append((kind, name))
                pending = ""
            elif ch == "}":
                if stack:
                    stack.pop()
                pending = ""
            elif ch == ";":
                if stack and stack[-1][0] in ("class", "iface", "top") or not stack:
                    pending = ""
                else:
                    pending += ch
            else:
                pending += ch
        pending += "\n"
        if pending.strip() == "":
            pending = ""
    return "\n".join(out)


MODS = r"(?:(?:public|private|protected|static|final|abstract|synchronized|native|transient|volatile)\s+)*"


def decl(ln, s, indent, top, stack, base_methods, class_info, override_next):
    cls = stack[-1][1] if stack and stack[-1][0] == "class" else None
    # type declarations
    m = re.match(r"((?:(?:public|private|protected|static|final|abstract)\s+)*)(class|interface)\s+(\w+)(.*)$", s)
    if m:
        mods = m.group(1).split()
        kind, name, rest = m.group(2), m.group(3), m.group(4)
        is_final = "final" in mods
        is_static = "static" in mods and top == "top"
        is_abstract = "abstract" in mods
        words = ["public"]
        if "private" in mods:
            words = ["private"]
        if kind == "class":
            if is_abstract:
                words.append("abstract")
            if is_final:
                words.append("sealed")
        words.append(kind)
        rest = rest.split("{")[0].strip()
        bases = []
        mm = re.search(r"\bextends\s+([\w.<>, ]+?)(?=\s+implements\b|\s*$)", rest)
        if mm:
            bases += [b.strip() for b in mm.group(1).split(",")]
        mm = re.search(r"\bimplements\s+([\w.<>, ]+)", rest)
        if mm:
            bases += [b.strip() for b in mm.group(1).split(",")]
        base = (" : " + ", ".join(bases)) if bases else ""
        tail = (" {" + s.split("{", 1)[1]) if "{" in s else ""
        if kind == "class" and "{" in s and s.rstrip().endswith("}") and s.count(";") >= 1:
            # the whole body is on this line: declare its members too
            inner = s.split("{", 1)[1].rsplit("}", 1)[0]
            stmts = [x.strip() + ";" for x in inner.split(";") if x.strip()]
            fake = [decl(x, x, "", "class", [("class", name)], base_methods, class_info, False) for x in stmts]
            tail = " { " + " ".join(fake) + " }"
        return "%s%s %s%s%s" % (indent, " ".join(words), name, base, tail)
    if top != "class":
        return ln
    cname = cls
    info = class_info.get(cname, {"final": True})
    # constructor
    if cname and re.match(r"(public\s+|private\s+|protected\s+)?%s\s*\(" % re.escape(cname), s):
        if re.match(r"(private|protected|public)\b", s):
            return indent + s
        return indent + "public " + s
    # fields and methods
    m = re.match(r"((?:(?:public|private|protected|static|final|abstract|synchronized|volatile|transient)\s+)*)(.*)$", s)
    mods = m.group(1).split()
    rest = m.group(2)
    if not rest or rest.startswith("}") or rest.startswith("\x01"):
        return ln
    access = "public"
    if "private" in mods:
        access = "private"
    elif "protected" in mods:
        access = "protected"
    is_static = "static" in mods
    is_final = "final" in mods
    is_abstract = "abstract" in mods
    # is it a method?  <type> name(
    mm = re.match(r"([\w.<>\[\], ?]+?)\s+(\w+)\s*\(", rest)
    is_method = bool(mm) and "=" not in rest.split("(")[0]
    words = [access]
    if cname:
        seen = SEEN.setdefault(cname, ([], []))
    if is_method:
        name = mm.group(2)
        seen[1].append(name)
        if is_static:
            words.append("static")
        elif is_abstract:
            words.append("abstract")
        elif override_next and name in base_methods:
            words.append("override")
        elif not info.get("final", True) and access != "private":
            words.append("virtual")
        return indent + " ".join(words) + " " + rest
    # field
    seen[0].extend(field_names(rest))
    fm = re.match(r"(int|float|bool|boolean|long|double|char|short|byte|string|String)\s+(\w+\s*=.*)$", rest)
    if is_static and is_final and fm and "[" not in rest.split("=")[0]:
        decls = fm.group(2)
        init = decls.split("=", 1)[1]
        name0 = decls.split("=")[0].strip()
        if not re.search(r"[A-Za-z_]\w*\s*\(", init) and name0 not in NOT_CONST:
            return indent + " ".join([access, "const"]) + " " + rest
        return indent + " ".join([access, "static", "readonly"]) + " " + rest
    if is_static:
        words.append("static")
        if is_final:
            words.append("readonly")
    return indent + " ".join(words) + " " + rest


def field_names(rest):
    """Names declared by 'type a = 1, b = {1, 2}, c;' (declarators split at depth 0)."""
    m = re.match(r"[\w.<>\[\], ?]+?\s+(\w+)\s*(.*)$", rest, re.S)
    if not m:
        return []
    names = [m.group(1)]
    tail, depth, cur = m.group(2), 0, ""
    for ch in tail:
        if ch in "({[":
            depth += 1
        elif ch in ")}]":
            depth -= 1
        if ch == "," and depth == 0:
            mm = re.match(r"\s*(\w+)", "")
            cur = ""
            continue
        cur += ch
        if depth == 0 and cur.endswith(",") is False:
            pass
    for mm in re.finditer(r",\s*(\w+)\s*(?:=|;|,)", tail):
        # only declarators at depth 0
        before = tail[:mm.start()]
        d = sum(before.count(c) for c in "({[") - sum(before.count(c) for c in ")}]")
        if d == 0:
            names.append(mm.group(1))
    return names


TEST_FILES = ["PhysTest", "PersonTest", "WorldTest", "AiTest", "InputTest", "BuildTest", "DestroyTest", "AnimalTest",
              "MachineTest", "Test39", "SoakTest", "MonkeyTest"]
TESTS = False

TEST_PATCHES = {
    "AnimalTest.java": [
        ("rename_block", "for (int it : new int[]{Items.DOG, Items.PIG, Items.RAT}) ", {"w": "aw"}),
    ],
    "BuildTest.java": [
        ("rename_block", "for (int it : items) ", {"w": "bw", "before": "b4", "after": "af"}),
    ],
    "DestroyTest.java": [
        ("lit", "for (Ent e : l.ents) { Person q = (Person) e; q.walk = 0; }", "for (Ent ee : l.ents) { Person q = (Person) ee; q.walk = 0; }"),
    ],
    "MonkeyTest.java": [
        ("range", "StackTraceElement[] st = t.getStackTrace();", "            }\n        }\n        System.out.println(fails == 0 ? \"MONKEY PASS\"",
         "System.out.println(t.toString());\n"),
    ],
    "SoakTest.java": [
        ("lit", "t.printStackTrace(System.out);", "System.out.println(t.toString());"),
    ],
    "PersonTest.java": [
        ("lit", "new Sim.Hook() { @Override public void before(float dt) { for (Person p : ps) if (p != null) p.control(s, dt); } }",
         "new HookFn((float dt) -> { for (Person p : ps) if (p != null) p.control(s, dt); })"),
    ],
    "AiTest.java": [
        ("lit", "java.util.function.Predicate<World> c)", "Func<World, bool> c)"),
        ("lit", "!c.test(w)", "!c(w)"),
    ],
}


def translate(name, src, base_methods, class_info):
    if TESTS:
        PATCHES.update(TEST_PATCHES)
    src = apply_patches(name, src)
    src = re.sub(r"^\s*(package|import)\s[^;]*;\s*$", "", src, flags=re.M)
    code, store = mask(src)
    code = re.sub(r"\b(android\.graphics|java\.util|java\.io|java\.text|java\.lang)\.", "", code)
    if TESTS:
        code = re.sub(r"System\.out\.(printf|println|print)\(", r"Sys.\1(", code)
        code = re.sub(r"\bmain\(String\[\]", "RunMain(string[]", code)
        code = re.sub(r"\bList\b<", "JList<", code)
        code = code.replace("->", "=>")
        code = re.sub(r"new (?:android\.graphics\.)?Canvas\((\w+)\)", r"\1.canvas()", code)
        code = re.sub(r"ImageIO\.write\((\w+), \x01\d+\x01, new File\((.*?)\)\);", r"\1.write(\2);", code)
    code = rename_keywords(code)
    for cn in sorted(CONFLICTS):
        code = re.sub(r"(?<![\w$])%s(\s*\()" % cn, cn + "_\\1", code)
    # a field called "length" would collide with the translation of array.length
    if re.search(r"\b(?:int|float|long|double)\s+length\s*[;=,]", code):
        code = re.sub(r"\blength\b(?!\s*\()", "Length", code)
    else:
        code = re.sub(r"\.length\b(?!\s*\()", ".Length", code)
    code = re.sub(r"String\.format\(\s*Locale\.US\s*,\s*", "Fmt.format(", code)
    code = re.sub(r"String\.format\(", "Fmt.format(", code)
    for pat, rep in TYPE_MAP:
        code = re.sub(pat, rep, code)
    code = re.sub(r"\binstanceof\b", "is", code)
    code = re.sub(r"\bsuper\.", "base.", code)
    code = re.sub(r"\)\s*throws\s+[\w., ]+(?=\s*[{;])", ")", code)
    code = re.sub(r"\bcatch\s*\(\s*(\w+)\s+(\w+)\s*\)", lambda m: "catch (%s %s)" % (m.group(1), m.group(2)), code)
    code = fix_new_array_2d(code)
    code = fix_nested_init(code)
    code = fix_diamond(code)
    # X >>> n
    code = re.sub(r"([\w.]+|\([^()]*\))\s*>>>\s*(\w+)", lambda m: "Sys.ushr(%s, %s)" % (m.group(1), m.group(2)), code)
    code = fix_hex(code)
    code = fix_foreach(code)
    code = fix_switch_end(code)
    # members first (needs "final"), then drop the remaining finals
    code = members(code, base_methods, class_info)
    code = re.sub(r"\bfinal\s+", "", code)
    # constructor chaining for every class declared here
    for cname in re.findall(r"\b(?:class)\s+(\w+)", code):
        code = fix_ctor_chain(code, cname)
    code = unmask(code, store)
    body = "\n".join(("    " + l if l.strip() else l) for l in code.strip("\n").split("\n"))
    return ("// <auto-generated> Translated from %s by tools/java2cs.py - do not edit by hand.\n"
            "using System;\nusing System.Collections.Generic;\n\nnamespace Trastazo\n{\n%s\n}\n") % (name, body)


def scan_classes(files):
    """Which classes are not final (their methods become virtual) and which methods the subclasses override."""
    class_info = {}
    base_methods = set()
    for name, src in files.items():
        code, _ = mask(apply_patches(name, src))
        for m in re.finditer(r"((?:(?:public|private|protected|static|final|abstract)\s+)*)(class)\s+(\w+)", code):
            mods = m.group(1).split()
            class_info[m.group(3)] = {"final": "final" in mods}
    for name, src in files.items():
        code, _ = mask(apply_patches(name, src))
        for m in re.finditer(r"(?<!final )\babstract\s+class\s+(\w+)", code):
            cname = m.group(1)
            # method names declared in that class (rough: lines indented by 4 with a paren)
            body = code[m.end():]
            for mm in re.finditer(r"^    (?:(?:public|protected|abstract|static)\s+)*[\w<>\[\], ]+\s+(\w+)\s*\(", body, re.M):
                base_methods.add(mm.group(1))
    return class_info, base_methods


def read_dir(d, accept):
    files = {}
    for f in sorted(os.listdir(d)):
        if f.endswith(".java") and accept(f):
            files[f] = open(os.path.join(d, f), encoding="utf-8").read()
    return files


def main():
    """java2cs.py <game src> <out>            translates the game
       java2cs.py --tests <game src> <test src> <out>   translates the tests (game src is scanned for name conflicts)"""
    global TESTS
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    TESTS = "--tests" in sys.argv
    game = read_dir(args[0], lambda f: f not in SKIP)
    if TESTS:
        todo = read_dir(args[1], lambda f: f[:-5] in TEST_FILES)
        outdir = args[2]
    else:
        todo = game
        outdir = args[1]
    os.makedirs(outdir, exist_ok=True)
    class_info, base_methods = scan_classes(dict(game, **todo))
    for name, src in dict(game, **todo).items():       # first pass only collects member names
        translate(name, src, base_methods, class_info)
    for fields, methods in SEEN.values():
        CONFLICTS.update(set(fields) & set(methods))
    SEEN.clear()
    for name, src in todo.items():
        res = translate(name, src, base_methods, class_info)
        with open(os.path.join(outdir, name[:-5] + ".cs"), "w", encoding="utf-8", newline="\n") as o:
            o.write(res)
    print("translated %d files" % len(todo))


if __name__ == "__main__":
    main()
