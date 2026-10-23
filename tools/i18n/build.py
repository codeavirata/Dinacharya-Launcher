#!/usr/bin/env python3
"""
Builds the language files of the app (app/src/main/assets/lang/<code>.tsv) from the translations in work/<code>.txt.

  python3 tools/i18n/build.py              builds all languages
  python3 tools/i18n/build.py --check U    also checks a fresh list of the app's English text (U = the file written by extract.py)

Files here:
  master.json     every piece of English text in the program: id, text (with {1}, {2} where a value is filled in), where it is
  work/<code>.txt lines "id|translation" ({1}, {2} are kept; \\n stands for a new line)
  work/skip.txt   ids that are not translated (names, code words, pieces that are never shown)
  glossary.md     the words used for the same thing across the screens
  extract.py      lists the English text of the program again (python3 extract.py <source folder> units.json)

The language files read "English<TAB>translation". Besides each pair, the file also gets the pair without the punctuation at its
ends (a colon, a bracket, a comma), because a long text is cut at those marks and each piece is looked up on its own.
Digits in the translations are written 0-9 in every language. To change a word, change it in work/ and build again; or edit the .tsv
file itself (then do not build again, since building replaces it).
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'lang'))
LANGS = ['hi', 'mr', 'gu', 'bn', 'ta', 'te']

DIGITS = {}
for zero in (0x0966, 0x09E6, 0x0AE6, 0x0BE6, 0x0C66):   # Devanagari, Bengali, Gujarati, Tamil, Telugu
    for d in range(10):
        DIGITS[chr(zero + d)] = str(d)
MARK = re.compile(r'\{(\d+)\}')


def latin_digits(s):
    return ''.join(DIGITS.get(ch, ch) for ch in s)


def wrapped(s):
    """True when the first ( of s is closed by the last character."""
    depth = 0
    for i, ch in enumerate(s):
        if ch == '(':
            depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0 and i < len(s) - 1:
                return False
    return depth == 0


def strip_edges(s):
    """The text without the marks that a long text is cut at (colon, comma, bullet, bracket) at its ends."""
    prev = None
    s = s.strip()
    while s != prev:
        prev = s
        s = s.lstrip(' ,;:)·•|–—').rstrip(' ,;:(·•|–—').strip()
        o, c = s.count('('), s.count(')')
        if s.startswith('(') and o > c:
            s = s[1:]
        if s.endswith(')') and c > o:
            s = s[:-1]
        if s.startswith('(') and s.endswith(')') and o == c and wrapped(s):
            s = s[1:-1]
        s = s.strip()
    return s


def esc(s):
    return s.replace('\\', '\\\\').replace('\n', '\\n').replace('\t', '\\t').replace('\r', '\\r')


def load_master():
    with open(os.path.join(HERE, 'master.json'), encoding='utf-8') as f:
        return {r['id']: r['en'] for r in json.load(f)}


def load_skip():
    out = set()
    p = os.path.join(HERE, 'work', 'skip.txt')
    if os.path.exists(p):
        for line in open(p, encoding='utf-8'):
            line = line.strip()
            if line and not line.startswith('#'):
                out.add(int(re.split(r'[\s|]', line)[0]))
    return out


def load_work(code):
    out = {}
    for line in open(os.path.join(HERE, 'work', code + '.txt'), encoding='utf-8'):
        line = line.rstrip('\n')
        if '|' in line:
            i, t = line.split('|', 1)
            out[int(i)] = t
    return out


def build(code, master, skip):
    work = load_work(code)
    problems = []
    keys = {}
    rows = []
    for i in sorted(master):
        if i in skip:
            continue
        en = master[i]
        if i not in work:
            problems.append('%s: no translation for id %d: %r' % (code, i, en[:60]))
            continue
        tr = latin_digits(work[i])
        if sorted(MARK.findall(en)) != sorted(MARK.findall(tr)):
            problems.append('%s: marks differ in id %d: %r' % (code, i, en[:60]))
            continue
        k = esc(en.strip())
        if not k or not tr.strip():
            continue
        if k.startswith('#'):
            problems.append('%s: key starts with # (id %d) and would be read as a comment: %r' % (code, i, en[:60]))
            continue
        if k in keys and keys[k] != tr:
            problems.append('%s: same English with two translations (id %d): %r' % (code, i, en[:60]))
            continue
        if k in keys:
            continue
        keys[k] = tr
        rows.append((k, tr))
    extra = []
    for k, tr in list(rows):
        en = k.replace('\\n', '\n').replace('\\t', '\t').replace('\\\\', '\\')
        k2 = esc(strip_edges(en))
        t2 = strip_edges(tr.replace('\\n', '\n')).replace('\n', '\\n')
        if k2 and t2 and k2 != k and k2 not in keys and not k2.startswith('#'):
            keys[k2] = t2
            extra.append((k2, t2))
    os.makedirs(ASSETS, exist_ok=True)
    path = os.path.join(ASSETS, code + '.tsv')
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Dinacharya, language file "%s": English text TAB translation. Built by tools/i18n/build.py\n' % code)
        for k, t in rows:
            f.write('%s\t%s\n' % (k, t))
        f.write('# the same texts without the marks at their ends\n')
        for k, t in extra:
            f.write('%s\t%s\n' % (k, t))
    return len(rows), len(extra), problems


def check(units_path, master, skip):
    """English text of the program that is in neither a language file nor the skip list (compared by text)."""
    units = json.load(open(units_path, encoding='utf-8'))
    have = {en.strip() for en in (master[i] for i in master if i not in skip)}
    skipped = {master[i].strip() for i in skip if i in master}
    missing = [u for u in units if u.strip() not in have and u.strip() not in skipped]
    return missing


def main():
    master = load_master()
    skip = load_skip()
    bad = 0
    for code in LANGS:
        n, e, problems = build(code, master, skip)
        print('%s: %d texts + %d without edge marks' % (code, n, e))
        for p in problems:
            print('  PROBLEM', p)
            bad += 1
    if '--check' in sys.argv:
        units_path = sys.argv[sys.argv.index('--check') + 1]
        missing = check(units_path, master, skip)
        print('English text not in the files: %d' % len(missing))
        for u in missing:
            print('  MISSING', repr(u[:100]))
        bad += len(missing)
    sys.exit(1 if bad else 0)


if __name__ == '__main__':
    main()
