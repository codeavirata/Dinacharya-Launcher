#!/usr/bin/env python3
"""Finds the user-visible English text in the app's Java source, as translation units.

A unit is a string literal, or a chain of pieces joined with + (or consecutive .append(...) calls) that contains literals;
the pieces that are not literals become {1}, {2}... so that "Free: up to " + n + " apps" becomes "Free: up to {1} apps".
"""
import os, re, sys, json

SRC = sys.argv[1] if len(sys.argv) > 1 else "/home/claude/dinacharya-wallpaper/app/src/main/java/app/dinacharya/wallpaper"

def lex(s):
    """Tokens: (kind, text, start, end). kinds: str, chr, id, num, op, ws/comment skipped."""
    i, n, out = 0, len(s), []
    while i < n:
        c = s[i]
        if c.isspace():
            i += 1
        elif s.startswith("//", i):
            j = s.find("\n", i)
            i = n if j < 0 else j
        elif s.startswith("/*", i):
            j = s.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif s.startswith('"""', i):
            j = s.find('"""', i + 3)
            out.append(("str", s[i + 3:j], i, j + 3))
            i = j + 3
        elif c == '"':
            j = i + 1
            buf = []
            while j < n and s[j] != '"':
                if s[j] == '\\':
                    e = s[j + 1]
                    if e == 'n': buf.append("\n")
                    elif e == 't': buf.append("\t")
                    elif e == 'u':
                        buf.append(chr(int(s[j + 2:j + 6], 16))); j += 4
                    elif e in '"\\\'': buf.append(e)
                    else: buf.append(e)
                    j += 2
                else:
                    buf.append(s[j]); j += 1
            out.append(("str", "".join(buf), i, j + 1))
            i = j + 1
        elif c == "'":
            j = i + 1
            while j < n and s[j] != "'":
                j += 2 if s[j] == '\\' else 1
            out.append(("chr", s[i:j + 1], i, j + 1))
            i = j + 1
        elif c.isalpha() or c == '_' or c == '$':
            j = i + 1
            while j < n and (s[j].isalnum() or s[j] in '_$'):
                j += 1
            out.append(("id", s[i:j], i, j))
            i = j
        elif c.isdigit():
            j = i + 1
            while j < n and (s[j].isalnum() or s[j] in '._'):
                j += 1
            out.append(("num", s[i:j], i, j))
            i = j
        else:
            two = s[i:i + 2]
            if two in ("->", "::", "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=", "-=", "<<", ">>"):
                out.append(("op", two, i, i + 2)); i += 2
            else:
                out.append(("op", c, i, i + 1)); i += 1
    return out

OPEN, CLOSE = "([{", ")]}"

def operand_end(toks, k, direction):
    """Walks over one balanced operand starting at token k going right (+1) or left (-1) until a top-level delimiter. Returns index after."""
    depth = 0
    i = k
    n = len(toks)
    while 0 <= i < n:
        kind, t = toks[i][0], toks[i][1]
        if kind == "op":
            if direction > 0:
                if t in OPEN: depth += 1
                elif t in CLOSE:
                    if depth == 0: return i
                    depth -= 1
                elif depth == 0 and t in (",", ";", "?", ":", "+", "->", "&&", "||", "==", "!=", "<", ">", "=", "<=", ">=", "+="):
                    # generic '<' '>' are rare in these operands; stop
                    return i
            else:
                if t in CLOSE: depth += 1
                elif t in OPEN:
                    if depth == 0: return i + 1
                    depth -= 1
                elif depth == 0 and t in (",", ";", "?", ":", "+", "->", "&&", "||", "==", "!=", "<", ">", "=", "<=", ">=", "+="):
                    return i + 1
        i += direction
    return i if direction > 0 else 0

def chain_around(toks, k):
    """For the string token at k: the pieces of the + chain it belongs to as (start_token, end_token) spans, or None."""
    # extend left
    pieces_left = []
    i = k
    start = k
    # the string itself is a piece; find the span of its operand (it is just the token)
    spans = [(k, k + 1)]
    # go left: while token before the span is '+'
    lo = k
    while lo - 1 >= 0 and toks[lo - 1][0] == "op" and toks[lo - 1][1] == "+":
        s = operand_end(toks, lo - 2, -1)
        if s >= lo - 1: break
        spans.insert(0, (s, lo - 1))
        lo = s
    hi = k + 1
    while hi < len(toks) and toks[hi][0] == "op" and toks[hi][1] == "+":
        e = operand_end(toks, hi + 1, +1)
        if e <= hi + 1: break
        spans.append((hi + 1, e))
        hi = e
    return spans

def is_literal_span(toks, a, b):
    return b - a == 1 and toks[a][0] == "str"

def unit_from_spans(toks, spans):
    parts = []
    k = 0
    for a, b in spans:
        if is_literal_span(toks, a, b):
            parts.append(("lit", toks[a][1]))
        else:
            parts.append(("var", None))
    # collapse adjacent vars
    t = []
    n = 0
    for kind, v in parts:
        if kind == "lit":
            t.append(v)
        else:
            if t and t[-1] == "\0":
                continue
            n += 1
            t.append("\0")
    out = []
    n = 0
    for x in t:
        if x == "\0":
            n += 1
            out.append("{%d}" % n)
        else:
            out.append(x)
    return "".join(out)

def append_chains(toks):
    """Chains of consecutive .append(arg) calls: yields a list of spans-lists (each arg is a span)."""
    res = []
    i = 0
    n = len(toks)
    while i < n:
        if toks[i][0] == "op" and toks[i][1] == "." and i + 2 < n and toks[i + 1][1] == "append" and toks[i + 2][1] == "(":
            # collect consecutive appends
            args = []
            j = i
            while j + 2 < n and toks[j][1] == "." and toks[j + 1][1] == "append" and toks[j + 2][1] == "(":
                depth = 0
                a = j + 3
                k = a
                while k < n:
                    if toks[k][0] == "op":
                        if toks[k][1] in OPEN: depth += 1
                        elif toks[k][1] in CLOSE:
                            if depth == 0: break
                            depth -= 1
                    k += 1
                args.append((a, k))
                j = k + 1
            res.append(args)
            i = j
        else:
            i += 1
    return res

def line_of(src, pos):
    return src.count("\n", 0, pos) + 1

SKIP_CALLS = {"note", "noteVideo", "getSharedPreferences", "getString", "getInt", "getLong", "getBoolean", "putString", "putInt",
              "putLong", "putBoolean", "remove", "contains", "getAction", "setAction", "getStringExtra", "putExtra", "getIntExtra",
              "getBooleanExtra", "getLongExtra", "hasExtra", "getAttributeValue", "equals", "equalsIgnoreCase", "startsWith", "endsWith",
              "contains", "indexOf", "split", "replace", "getProperty", "d", "w", "e", "i", "v", "valueOf", "forName", "getDeclaredField",
              "setHeader", "addRequestProperty", "setRequestProperty", "setRequestMethod", "newWakeLock", "getSystemService",
              "createNotificationChannel", "hasSystemFeature", "checkSelfPermission", "getBroadcast", "getService", "getActivity",
              "addCategory", "newInstance", "getInstance", "parse", "compile", "matches", "format", "line", "tag", "key", "hasMatch"}

def call_name(toks, k):
    """The method name whose argument list directly holds token k, or ''."""
    depth = 0
    i = k - 1
    while i >= 0:
        t = toks[i]
        if t[0] == "op":
            if t[1] in CLOSE: depth += 1
            elif t[1] in OPEN:
                if depth == 0:
                    if t[1] == "(" and i > 0 and toks[i - 1][0] == "id":
                        return toks[i - 1][1]
                    return ""
                depth -= 1
            elif t[1] == ";" and depth == 0:
                return ""
        i -= 1
    return ""

def to_placeholders(u):
    n = [0]
    def rep(m):
        n[0] += 1
        return "{%d}" % n[0]
    # printf specs become placeholders too (only when no {n} is there already)
    if "{1}" in u:
        return u
    return re.sub(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXc]", rep, u)

def main():
    units = {}
    standalone = {}
    for fn in sorted(os.listdir(SRC)):
        if not fn.endswith(".java") or fn in ("Translator.java",):   # the translator's own word lists are not screen text
            continue
        s = open(os.path.join(SRC, fn), encoding="utf-8").read()
        toks = lex(s)
        used = set()
        for k, tk in enumerate(toks):
            if tk[0] != "str" or k in used:
                continue
            spans = chain_around(toks, k)
            if len(spans) > 1:
                has_var = any(not is_literal_span(toks, a, b) for a, b in spans)
                for a, b in spans:
                    if is_literal_span(toks, a, b): used.add(a)
                if call_name(toks, spans[0][0]) in SKIP_CALLS: continue
                u = to_placeholders(unit_from_spans(toks, spans))
                units.setdefault(u, []).append("%s:%d" % (fn, line_of(s, tk[2])))
        for args in append_chains(toks):
            if len(args) < 2:
                continue
            spans = [(a, b) for a, b in args]
            lits = [a for a, b in spans if is_literal_span(toks, a, b)]
            if not lits:
                continue
            for a in lits: used.add(a)
            u = to_placeholders(unit_from_spans(toks, spans))
            units.setdefault(u, []).append("%s:%d" % (fn, line_of(s, toks[spans[0][0]][2])))
        for k, tk in enumerate(toks):
            if tk[0] == "str" and k not in used:
                if call_name(toks, k) in SKIP_CALLS:
                    continue
                u = to_placeholders(tk[1])
                units.setdefault(u, []).append("%s:%d" % (fn, line_of(s, tk[2])))
    out = sys.argv[2] if len(sys.argv) > 2 else "units.json"
    json.dump(units, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=0)
    print(len(units), "units")

main()
