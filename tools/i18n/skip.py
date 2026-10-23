#!/usr/bin/env python3
"""skip.py ids...   (ranges a-b allowed) marks units as not for translation (diagnostics, keys, developer text)."""
import sys, os
base = os.path.dirname(os.path.abspath(__file__))
path = os.path.join(base, 'work', 'skip.txt')
cur = set(int(x) for x in open(path).read().split()) if os.path.exists(path) else set()
for a in sys.argv[1:]:
    if '-' in a:
        x, y = a.split('-'); cur.update(range(int(x), int(y) + 1))
    else:
        cur.add(int(a))
open(path, 'w').write('\n'.join(str(x) for x in sorted(cur)) + '\n')
print(len(cur), 'skipped')
