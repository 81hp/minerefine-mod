"""Builds src/main/resources/assets/minerefine-hud/tiers.json from a saved prices.json.

Every item's tier count, so the mod knows a piece is maxed before its shop has been opened.
A shop lists every tier above the ones owned, so the highest tier it has shown is the last one;
like the mod itself, a count is only trusted once two or more tiers of the item were seen.

    python tools/export-tier-counts.py [path/to/prices.json]

Defaults to the Minecraft config folder. Run it again after visiting new shops and commit the result.
"""
import collections
import json
import os
import sys

DEFAULT = os.path.join(os.environ.get('APPDATA', ''), '.minecraft', 'config', 'minerefine-hud', 'prices.json')
OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'minerefine-hud', 'tiers.json')

source = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
with open(source, encoding='utf-8') as f:
    prices = json.load(f)

seen = collections.defaultdict(set)
for entry in prices:
    mine, gear, level = entry['key'].rsplit('|', 2)
    seen[mine + '|' + gear].add(int(level))

counts = {key: max(levels) for key, levels in sorted(seen.items()) if len(levels) >= 2}

with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(counts, f, indent=2, ensure_ascii=False)
    f.write('\n')
print(f'{len(counts)} tier counts from {source}')
