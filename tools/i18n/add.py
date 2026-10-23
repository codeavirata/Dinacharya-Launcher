#!/usr/bin/env python3
"""add.py < lines of English units (\\n escaped): adds units that code changes created (ids from 5001), prints "id<TAB>english"."""
import sys, json, os
base = os.path.dirname(os.path.abspath(__file__))
path = os.path.join(base, 'master.json')
m = json.load(open(path, encoding='utf-8'))
byen = {r['en']: r['id'] for r in m}
nxt = max(5000, max(r['id'] for r in m)) + 1
for line in sys.stdin:
    line = line.rstrip('\n')
    if not line.strip():
        continue
    en = line.replace('\\n', '\n').replace('\\t', '\t')
    if en in byen:
        print('%d\t%s\t(exists)' % (byen[en], line)); continue
    m.append({'id': nxt, 'en': en, 'src': ['added']})
    byen[en] = nxt
    print('%d\t%s' % (nxt, line)); nxt += 1
json.dump(m, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=0)
