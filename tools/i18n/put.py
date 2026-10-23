#!/usr/bin/env python3
"""put.py <lang>  < lines "id|translation": checks each against the English unit and stores it in work/<lang>.txt."""
import sys, json, re, os
lang = sys.argv[1]
base = os.path.dirname(os.path.abspath(__file__))
master = {r['id']: r['en'] for r in json.load(open(os.path.join(base, 'master.json'), encoding='utf-8'))}
path = os.path.join(base, 'work', lang + '.txt')
cur = {}
if os.path.exists(path):
    for line in open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if '|' in line:
            i, t = line.split('|', 1)
            cur[int(i)] = t
marks = lambda s: sorted(re.findall(r'\{\d+\}', s))
bad = 0
added = 0
for line in sys.stdin:
    line = line.rstrip('\n')
    if not line.strip() or '|' not in line:
        continue
    i, t = line.split('|', 1)
    try:
        i = int(i)
    except ValueError:
        print('BAD ID', line[:40]); bad += 1; continue
    if i not in master:
        print('UNKNOWN ID', i); bad += 1; continue
    en = master[i]
    en_e = en.replace('\\', '\\\\').replace('\n', '\\n').replace('\t', '\\t')
    if marks(en) != marks(t):
        print('MARKS', i, en[:50], '=>', t[:50]); bad += 1; continue
    if en_e.count('\\n') != t.count('\\n'):
        print('NEWLINES', i, en[:50]); bad += 1; continue
    if '\t' in t or not t.strip():
        print('EMPTY/TAB', i); bad += 1; continue
    cur[i] = t.strip() if not en.startswith(' ') else t
    added += 1
with open(path, 'w', encoding='utf-8') as f:
    for i in sorted(cur):
        f.write('%d|%s\n' % (i, cur[i]))
print(lang, 'added', added, 'bad', bad, 'total', len(cur))
