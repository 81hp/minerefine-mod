# MineRefine HUD

A client-side Fabric mod for Minecraft 1.21.11 and 26.1 – 26.2 on the MineRefine server, built to
run inside Lunar Client.

What it does:

1. **Mine costs.** Recognises the mine you are mining in and shows what every piece of gear there
   costs, plus a total for the whole mine.
2. **Progress bars.** Up to six bars tracking your next upgrade, or everything left to max a piece,
   filling as you mine. Each can count several pieces at once (12 chestplates).
3. **Boss timers.** Measures each boss's respawn interval from the server's broadcasts, shows only
   the bosses of the dimension you are in, and pings you before a respawn.
4. **Shop price learning.** Reads every price in a shop you open and remembers it forever.
5. **Turret size calculator.** What two turrets merge into, or what size merger you need to reach a
   target size, filled in from the turrets in your inventory.

Everything is movable, resizable and configurable in game.

---

## Build and install

```bash
./gradlew buildAll
```

One jar per Minecraft version lands in `build/libs/`:

| Minecraft | Jar | Lunar mods folder |
|---|---|---|
| 1.21.11 | `minerefine-mod-<version>+1.21.11.jar` | `.lunarclient/profiles/1.21/mods/fabric-1.21.11/` |
| 26.1 – 26.1.2 | `minerefine-mod-<version>+26.1.2.jar` | the profile's `fabric-26.1.x` folder |
| 26.2 | `minerefine-mod-<version>+26.2.jar` | `.lunarclient/profiles/26/mods/fabric-26.2/` |

Gradle runs on Java 25 (pinned in `gradle/gradle-daemon-jvm.properties`); 1.21.11 is compiled
with Java 21. Both are downloaded automatically when missing.

### One source tree, several Minecraft versions

[Stonecutter](https://stonecutter.kikugie.dev/) builds every version from the same `src/`:

- `versions/<minecraft>/gradle.properties` holds what differs per version (Minecraft range,
  Fabric API, Java).
- The source is written against **26.2**, with Mojang's names on every version. Renames are
  swapped in for older versions automatically, listed in `stonecutter.gradle.kts`.
- Calls that changed shape go through `client/Compat.java`, where `//? if >=26.2 { ... }` comments
  pick each version's branch. A new Minecraft version that moves something is fixed there once.
- `./gradlew "Set active project to 1.21.11"` switches `src/` to another version for the IDE.
  Switch back to 26.2 before committing.

To add a version: append it in `settings.gradle.kts`, add its `versions/<v>/gradle.properties`,
add it to the CI matrix in `.github/workflows/build.yml`, and guard whatever no longer compiles.

### Tests and releases

- `./gradlew :26.2:runClientGameTest` starts the real game, opens a world and checks the action
  bar hook, the HUD and every screen (`src/gametest`). `./gradlew testAll` does every version.
- GitHub Actions builds and game-tests every version on every push, with screenshots as
  artifacts.
- On `main`, when `mod_version` in `gradle.properties` is new, it publishes release
  `V<mod_version>` with all jars and the commit list as notes. Bumping the version is the release
  button.

## Running the logic tests

```bash
./tools/run-core-tests.sh
```

A few seconds, no Gradle, no Minecraft, no downloads. It compiles the version-independent core and
runs every assertion over it, most of them built from real in-game text and the player's own
saved data. Run this before you run the game: it catches the logic bugs, the game only catches
the API ones.

---

## In game

| | |
|---|---|
| **F6** | Move and resize the panels |
| `/mrhud config` | Settings |
| `/mrhud turret` | Turret size calculator |
| `/mrhud debug` | Everything the mod can see and what it concluded, for bug reports |
| `/mrhud forget` | Forget what the block you are mining was learned as, so it is learned again |

Keys for show/hide, settings and the turret calculator can be bound in Options, Controls, under
"MineRefine HUD". Only F6 is bound by default.

### Moving and resizing

Drag a panel to move it and scroll over it to resize it, or use the Smaller and Bigger buttons.
There are four panels: mine costs, bosses, progress bars and the respawn reminder. Arrow keys
nudge by a pixel, Shift plus arrow by ten, and Snap to corner and Reset tidy up.

Position is stored as an **anchor plus an offset**, not as raw coordinates. Drop a panel in any
third of the screen and it pins to that corner or edge, so it stays put when the window is resized
or the GUI scale changes. A panel is clamped so it can never be lost off screen.

### Settings

| Tab | What is there |
|-----|---------------|
| General | Overlay, mine and boss panels on or off, costs in credits, block rate, the turret calculator |
| Mine | Which rows show: sword, tool, armour (or its four pieces), charm, mine total, item in hand |
| Bosses | Only this dimension, rows for alive and still-learning bosses, how many, the reminder, its timing, sound and volume |
| Progress | The bars (item, amount, remove, add), text line and bar on or off, and next tier or to max |
| Panels | A background behind each panel, off or at 25 to 100% |
| Colours | Every text and bar colour as `#RRGGBB`, with Reset |

Everything is stored in `.minecraft/config/minerefine-hud/config.json`, which can still be edited
by hand with the game closed. A value that makes no sense, such as a colour that is not hex or a
size of zero, falls back to its default rather than breaking the panel. The same file holds what
the mod learns by itself: `minedBlocks`, `learnedProgression` and `bossWorlds`.

---

## How it works

### Recognising the mine

The server never names the mine on screen, so the mod works it out from what you mine. While
mining, the action bar shows your balance of that mine's resource followed by its icon, as plain
text: `1.55B[block/cobblestone]` at Rubble, `881.71M[item/iron_ingot@items]` at Zircon. Each mine
has one icon, so once the mod knows which icon is which mine, mining one block is enough.

Icons are learned, never configured:

- **From a shop.** A shop prints your exact balance, `Rubble x919.4M (1,570,112,004)`. When it
  matches the action bar total from the last two minutes, that icon is that mine's. A shop match
  also drops any other icon filed under the same mine, so an earlier mistake repairs itself.
- **From a balance already known.** Mined resources are a balance, not items, so the usual route
  is this one: an unknown icon whose total sits just above exactly one balance seen this session,
  and whose mine has no icon yet, is learned on the spot.
- **From a resource pickup** at the same moment, for mines whose resource does arrive as items.
  A pickup only ever fills a gap; it never relabels an icon already known.

When the icon is unknown, the gear in your hand and the last shop you opened stand in, but never a
mine whose own icon is known (you would have been recognised mining it), and a shop opened after
the last block mined wins over that block. With nothing left, the panel says `Mine: unknown`
rather than showing a mine you have left. If a mine is ever recognised wrongly, mine a block there
and run `/mrhud forget`.

On-screen text is deliberately not used. The spreadsheet has mines called Gear, Wind, Ice, Robe
and Chamber, which are ordinary words; matching text only ever produced wrong answers.

### Prices

The baseline is the community gear price spreadsheet, kept out of this repository (its file
properties name the people who maintain it). `tools/convert-spreadsheet.py` turns it into
`progression.json`, which ships inside the mod: every
mine and boss in progression order, Overworld first and Ruins last, with each piece's full cost
(all its tiers together) multiplied by the tab's `Factor`. Boss costs are fragments and are not
multiplied. To update, replace the spreadsheet, rerun the script (needs `pip install openpyxl`)
and rebuild, or drop the new `progression.json` into `.minecraft/config/minerefine-hud/` to
override the bundled copy without a release.

The spreadsheet has no per-tier prices, so those come from the shop. When you open a shop menu,
the client already holds every item in it, lore included, and the mod reads each cost line:

```
[Debris Shovel] [VI]
Cost:
  [Debris Shovel] [V] x1 (0)     <- the item it requires, not a price
  Debris x5B (0)                 <- the price, recorded
```

Every tab you open is read, every price is saved the moment it is read, and prices read from the
game always beat the spreadsheet. When a price changes, the mod says so in chat. Reading is
strictly passive: it never opens a menu, clicks, hovers or sends a packet.

Things the shop taught that the code relies on:

- **Tier counts vary.** Ocean armour has 3 tiers, most other armour 4, Rubble's sword 5, most tools
  and swords 6, Rafter's axe 7, boss gear 2. The mod works the count out from the fact that the
  spreadsheet total is the sum of every tier.
- **Charms have no tier.** `Honeystone Charm`, one per mine, requiring the previous charm by name
  without brackets (`Vase Charm x1`).
- **Gear spelling differs** from the spreadsheet for three mines: Rust (Rusty), Pine (Pine Tree)
  and Pale Wood (Pale Tree). Boss gear uses short names (Archaeologist, Guardian o', Björn).
- **Tier I names the mine before it** (`[Suspicious Sand Shovel] [VI]` to buy Debris Shovel I).
  The mod saves these links, so the progress bar carries on into dimensions the spreadsheet does
  not list yet, through boss gear where the server requires it.

A shop item with a cost that cannot be read is written to `unrecognised-shop-items.txt` in the
config folder, so a new shape can be fixed quickly.

### The mine panel

Each piece and a **Total** for the whole mine. A piece whose tiers have all been read from the
shop shows their sum, marked `*`; otherwise it shows the spreadsheet figure. If any piece is
unknown the total says `incomplete` rather than guessing. Rows can be hidden in the settings.

### Progress bars

Each bar follows one kind of item (sword, a tool, an armour piece, charm). It starts from the most
advanced one you own, and when that is maxed it moves to the next mine in the chain. Its balance
comes from the action bar while you mine, or from a shop.

- **Amount.** Set it to 12 to buy twelve chestplates at once; the bar tracks the combined cost.
- **Track: next tier / to max.** "To max" shows everything still to buy for the piece at its mine,
  `Chestplate III-IV`, from the shop prices when they are all known and otherwise from the
  spreadsheet total minus the tiers you own.
- **Axe/shovel mines** (shown with a Pickaxe bar). ON: after a pickaxe mine is maxed, the bar shows
  the shovel or axe of a mine on the way (Soul Soil Shovel between City Wall and Blackstone), then
  carries on to the next pickaxe. OFF: those mines are skipped.
- **Armor set.** Helmet, chestplate, leggings and boots as one bar. It follows the set you are
  upgrading: with the helmet maxed and the rest not, it counts the three pieces left at that mine.
- **Total.** Everything at the mine. With **Minus bought** (the default) the tiers you already own
  come off it, so buying the pickaxe takes its cost off the Total; **Full price** is the whole mine,
  the same figure as the mine panel's Total.
- Every bar compares against your whole balance of that resource; bars do not split it.

### Boss timers

The server announces both events:

```
BOSS ALERT
Angry Archaeologist has spawned!

(X) Angry Archaeologist has been slain! (X)
```

The interval is the gap between a kill and the next spawn, measured per boss.

- **Anchored on the kill, not the clock.** A boss left alive pushes its schedule back.
- **Median, not mean.** One bad sample cannot drag the estimate off.
- **Continuity guard.** A relog or world change discards the pending measurement, because a gap
  that spans an absence may contain a spawn you never saw.

The panel says `learning...` before the first full cycle, shows a `?` while the estimate rests on
one sample, and `due now` when a spawn message was missed. Timers persist to `bosses.json`.

**Only this dimension.** Your world comes from the mine you are at, or from the world tag on what
you mined (`RUINBOUND`). A boss's world comes from the spreadsheet, or is learned from where you
were when its broadcast arrived, since broadcasts only reach that area.

**Respawn reminder.** Ten seconds before a respawn (adjustable), a ping and a placeable title,
`Angry Archaeologist is about to respawn!`. Only once the interval has been measured twice.

### Turret size calculator

A port of garfieldthelord21's community calculator
(github.com/garfieldthelord21/Turret-Calculator), checked against the original: the smaller
turret adds 4/9 + 500/(9 × size) of itself, between 50% and 100%, and each merge lowers the 2000
cap by 100; a merge that would go over is flattened to the cap.

- **Merge result:** two sizes and the merges so far give the combined size.
- **Size needed:** your turret and the size you want give the smallest whole merger that gets
  there. 1400 to 1900 needs a 1000 merger. When even a turret your size is not enough, the merger
  must be the bigger one: 300 to 1500 needs 1312. A target above the cap says what the merge can
  reach instead.
- **Auto-fill:** every turret in your inventory and off hand gets a button. Its size is read from
  the item, and its merges from its size cap.

---

## If it does not compile

First see which version failed (CI names it), then look in `client/Compat.java` and the renames
in `stonecutter.gradle.kts`. Everything that touches version-sensitive Minecraft API is marked
`VERSION SENSITIVE` in its header: `SidebarReader`, `ShopScanner`, `HudRenderer` (the matrix stack for scaling),
`HudPositionScreen`, `SettingsScreen`, `TurretScreen`, the `InGameHudMixin` that sees the action
bar, and `registerHud()` and `playReminderSound()` in `MinerefineHudClient`. Everything else is
plain Java and covered by the tests, so an API change never touches the logic.

## Layout

```
boss/     BossMessageParser, BossTracker, BossWorlds, BossAlerts   pure logic, tested
mine/     Mine, MineCatalog, MineDetector, MiningBlocks, pickups   pure logic, tested
shop/     ShopItemParser, PriceLedger, Amounts, ...                pure logic, tested
progress/ ProgressPlanner, MineCosts, ProgressionLinks, ...        pure logic, tested
hud/      HudModel, HudLayout, Theme, Formatting                   pure logic, tested
turret/   TurretCalculator, TurretItem                             pure logic, tested
data/     ProgressionData                                          bundled data, config override
client/   entry point, screens, renderer, readers                  touches Minecraft
          Compat: every call that differs between Minecraft versions
mixin/    InGameHudMixin                                           action bar hook
src/gametest/   client game test, run in the real game per version
```

Anything that makes a decision lives above `client/` and has no Minecraft imports, so it is
testable without a game running.
