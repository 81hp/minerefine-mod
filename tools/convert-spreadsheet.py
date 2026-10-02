#!/usr/bin/env python3
"""
Converts the community price spreadsheet into the progression file bundled with the mod.

    python tools/convert-spreadsheet.py ["MineRefine Gear Prices.xlsx"]

Writes src/main/resources/assets/minerefine-hud/progression.json, in the same shape the old
data.js used, so Mine and MineCatalog bind onto it unchanged. Needs openpyxl (pip install openpyxl).

How the spreadsheet is laid out, and what this relies on:

  - One tab per world. Tabs run from the newest world (Ruins) back to the first (Overworld), so
    progression order is the tab order reversed. Within a tab, mines run left to right.
  - Row 1 holds the mine names, each followed by an unnamed "credits" column, up to "Total".
  - Column A holds the row labels: Sword, Axe, Pickaxe, Shovel, Helmet ... Charm, Total. Rows
    below Total are the author's scratch work and are never read, apart from the rate block.
  - Each tab has its own "Factor" (1,000,000 for the endgame worlds, 1,000 for Nether, 1 for the
    first two). Mine costs are multiplied by it. Boss costs are fragments and are not.
  - A boss column is recognised by its credits: value / credits equals the tab's Fragment rate
    rather than its Resource rate.

Every column is checked against its own Total row, so a typo in the sheet stops the conversion
instead of shipping a wrong price.
"""

import json
import sys
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

import openpyxl

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/minerefine-hud/progression.json"

TOOLS = ("pickaxe", "axe", "shovel")
PIECES = ("helmet", "chestplate", "leggings", "boots")


def num(v):
    """Cell value as an exact Decimal, or None for an empty or non-numeric cell."""
    if v is None or isinstance(v, str) or isinstance(v, bool):
        return None
    return Decimal(repr(v))


def whole(v):
    return int(v.quantize(Decimal(1), rounding=ROUND_HALF_UP))


def slug(s):
    return "".join(c if c.isalnum() else "-" for c in s.lower()).strip("-")


def read_tab(ws):
    rows = list(ws.iter_rows(values_only=True))
    header = rows[0]

    # Gear rows run down to Total. Below it is scratch work and then the rate block, which reuses
    # the label "Shovel" for a rate, so the two halves must be read separately.
    labels = {}
    rates = {}
    for i, row in enumerate(rows[1:], start=1):
        label = row[0]
        if not isinstance(label, str):
            continue
        key = label.strip().lower()
        if "total" not in labels:
            labels[key] = i
        elif key in ("resource", "wood", "shovel", "fragment", "factor"):
            rates.setdefault(key, num(row[1]))

    factor = rates.get("factor") or Decimal(1)
    fragment_rate = rates.get("fragment") or Decimal(0)
    resource_rate = rates.get("resource") or Decimal(0)

    entries = []
    col = 1
    while col < len(header):
        name = header[col]
        if not isinstance(name, str) or not name.strip():
            col += 1
            continue
        name = name.strip()
        if name.lower() == "total":
            break

        def cell(label, c=col):
            r = labels.get(label)
            return None if r is None else num(rows[r][c])

        boss = is_boss(rows, labels, col, fragment_rate, resource_rate)
        scale = Decimal(1) if boss else factor

        items = {}
        for gear in ("sword",) + TOOLS + ("armor", "charm"):
            v = cell(gear)
            if v is not None and v > 0:
                items[gear] = whole(v * scale)
        pieces = {}
        for piece in PIECES:
            v = cell(piece)
            if v is not None and v > 0:
                pieces[piece] = whole(v * scale)

        check_totals(ws.title, name, cell, scale)

        entry = {
            "id": ("boss-" if boss else "mine-") + slug(ws.title) + "-" + slug(name),
            "type": "boss" if boss else "mine",
            "world": ws.title.strip(),
            "name": name,
            "items": items,
        }
        if pieces:
            entry["armorPieces"] = pieces
        if boss:
            entry["fragmentsPerCredit"] = float(fragment_rate)
        entries.append(entry)
        col += 2

    return entries


def is_boss(rows, labels, col, fragment_rate, resource_rate):
    if fragment_rate <= 0 or fragment_rate == resource_rate:
        return False
    for label in ("sword", "charm", "helmet"):
        r = labels.get(label)
        if r is None:
            continue
        value, credits = num(rows[r][col]), num(rows[r][col + 1])
        if value and credits:
            return abs(value / credits - fragment_rate) < Decimal("0.0001")
    return False


def check_totals(tab, name, cell, scale):
    """Pieces must add up to Armor, and everything to Total, or the sheet has a typo."""
    def v(label):
        x = cell(label)
        return x if x is not None else Decimal(0)

    pieces = sum(v(p) for p in PIECES)
    if cell("armor") is not None and abs(pieces - v("armor")) > Decimal("0.01"):
        raise SystemExit(f"{tab} / {name}: armour pieces sum to {pieces}, Armor row says {v('armor')}")

    parts = v("sword") + sum(v(t) for t in TOOLS) + v("armor") + v("charm")
    if cell("total") is not None and abs(parts - v("total")) > Decimal("0.01"):
        raise SystemExit(f"{tab} / {name}: pieces sum to {parts}, Total row says {v('total')}")


def main():
    if len(sys.argv) > 1:
        source = Path(sys.argv[1])
    else:
        found = sorted(ROOT.glob("MineRefine Gear Prices*.xlsx"))
        if not found:
            raise SystemExit("no spreadsheet given and none found in the project folder")
        source = found[-1]

    wb = openpyxl.load_workbook(source, data_only=True)
    entries = []
    # Tabs run newest world first, so reverse them for progression order.
    for ws in reversed(wb.worksheets):
        entries.extend(read_tab(ws))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(entries, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    mines = [e for e in entries if e["type"] == "mine"]
    bosses = [e for e in entries if e["type"] == "boss"]
    print(f"read {source.name}: {len(wb.worksheets)} worlds, {len(mines)} mines, {len(bosses)} bosses")
    print("bosses: " + ", ".join(f"{b['name']} ({b['world']})" for b in bosses))
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
