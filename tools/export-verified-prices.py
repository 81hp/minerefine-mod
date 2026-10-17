"""Builds src/main/resources/assets/minerefine-hud/verified-prices.json from shop tooltips the mod
saved to unrecognised-shop-items.txt, and raises or lowers the matching tiers.json counts.

Used after a server change the parser could not read yet (October 2026: balances printed rounded,
and a tier taken off most Arctic armour). Players with prices saved before the change get these
instead, without opening every shop again.

    python tools/export-verified-prices.py <unrecognised-shop-items.txt> <first title> <seenAt ms>

Only items from the first block titled <first title> onwards are used, so older sessions in the
file are left out. A shop lists every tier above the ones owned, so the highest tier seen of an
item is its last.
"""
import json
import os
import re
import sys

ROMAN = {'I': 1, 'II': 2, 'III': 3, 'IV': 4, 'V': 5, 'VI': 6, 'VII': 7, 'VIII': 8}
MULT = {'': 1, 'K': 10**3, 'M': 10**6, 'B': 10**9, 'T': 10**12}
GEAR = ('Helmet', 'Chestplate', 'Leggings', 'Boots', 'Sword', 'Pickaxe', 'Axe', 'Shovel')
HERE = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'minerefine-hud')

source, first, seen_at = sys.argv[1], sys.argv[2], int(sys.argv[3])
text = open(source, encoding='utf-8').read()
text = text[text.index('\n' + first) + 1:] if not text.startswith(first) else text

title = re.compile(r'^\[([^\]]+?) (' + '|'.join(GEAR) + r')\] \[([IVX]+)\]$')
cost = re.compile(r"^\s*\|\s*\S*\s*([A-Za-z' ]+?) x([0-9][0-9.,]*)([KMBT]?) \(")
prices = {}
for block in text.split('\n\n'):
    lines = block.strip('\n').split('\n')
    m = title.match(lines[0])
    if not m:
        continue
    in_cost = False
    for line in lines[1:]:
        if 'Cost:' in line:
            in_cost = True
            continue
        c = cost.match(line) if in_cost else None
        if c and '[' not in line:
            amount = round(float(c.group(2).replace(',', '')) * MULT[c.group(3)])
            key = f'{m.group(1).lower()}|{m.group(2).lower()}|{ROMAN[m.group(3)]}'
            prices[key] = {'key': key, 'amount': amount, 'currency': c.group(1).strip(), 'seenAt': seen_at}
            break

with open(os.path.join(HERE, 'verified-prices.json'), 'w', encoding='utf-8', newline='\n') as f:
    json.dump(sorted(prices.values(), key=lambda p: p['key']), f, indent=2, ensure_ascii=False)
    f.write('\n')

tiers_file = os.path.join(HERE, 'tiers.json')
tiers = json.load(open(tiers_file, encoding='utf-8'))
for key in prices:
    item, level = key.rsplit('|', 1)
    top = max(int(k.rsplit('|', 1)[1]) for k in prices if k.rsplit('|', 1)[0] == item)
    tiers[item] = top
with open(tiers_file, 'w', encoding='utf-8', newline='\n') as f:
    json.dump(dict(sorted(tiers.items())), f, indent=2, ensure_ascii=False)
    f.write('\n')
print(f'{len(prices)} prices')
