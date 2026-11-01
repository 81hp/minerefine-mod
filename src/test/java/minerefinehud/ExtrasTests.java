package minerefinehud;

import minerefinehud.drops.RareDrops;
import minerefinehud.hud.ExtrasHud;
import minerefinehud.hud.HudModel;
import minerefinehud.mine.MiningRate;
import minerefinehud.stats.PlayerStats;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** Rare drops from chat, stats from /stats, mining per hour and their panels. */
public final class ExtrasTests {

    private static int passed = 0;
    private static int failed = 0;

    /** The glyph the server draws before "Luck", a private-use character in its font. */
    private static final String G = "";

    public static void main(String[] args) {
        drops();
        dropSpoofs();
        dropCounting();
        stats();
        statsUpdates();
        miningRate();
        panels();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // Every shape below is from the player's own chat logs, 2026-10 and 2026-11.
    private static void drops() {
        eq("special, count first", Optional.of(new RareDrops.Drop("SPECIAL", "Nebula", 1)),
                RareDrops.parse("SPECIAL! You found x1 Nebula! (+321.9 " + G + " Luck)"));
        eq("count first, two words", Optional.of(new RareDrops.Drop("SPECIAL", "Blub Frond", 1)),
                RareDrops.parse("SPECIAL! You found x1 Blub Frond! (+321.8 " + G + " Luck)"));
        eq("count last", Optional.of(new RareDrops.Drop("RARE", "Warden Fragment", 1)),
                RareDrops.parse("RARE! You found Warden Fragment x1 (+133.9 🍀 Luck)"));
        eq("turret: /plot dropped, Turret Luck", Optional.of(new RareDrops.Drop("RARE", "Netherrack Turret", 1)),
                RareDrops.parse("RARE! You found Netherrack Turret /plot x1 (+640.6 " + G + " Turret Luck)"));
        eq("turret, count first", Optional.of(new RareDrops.Drop("EXOTIC", "Crystalite Turret", 1)),
                RareDrops.parse("EXOTIC! You found x1 Crystalite Turret! (+412.0 " + G + " Turret Luck)"));
        eq("charm brackets made plain", Optional.of(new RareDrops.Drop("RARE", "Sculk Charm", 1)),
                RareDrops.parse("RARE! You found « Sculk » - Charm x1 (+395.1 " + G + " Luck)"));
        eq("charm, count first", Optional.of(new RareDrops.Drop("RARE", "The Sift Charm", 2)),
                RareDrops.parse("RARE! You found x2 « The Sift » - Charm! (+321.9 " + G + " Luck)"));
        eq("small caps key", Optional.of(new RareDrops.Drop("ELITE", "RARE Key", 1)),
                RareDrops.parse("ELITE! You found RARE ᴋᴇʏ x1 (+133.9 " + G + " Luck)"));
        eq("no luck, known rarity", Optional.of(new RareDrops.Drop("TRANSCENDENT", "MECHANICAL Key", 1)),
                RareDrops.parse("TRANSCENDENT! You found MECHANICAL ᴋᴇʏ x1"));
        eq("big counts", Optional.of(new RareDrops.Drop("RARE", "Shard", 1200)),
                RareDrops.parse("RARE! You found x1,200 Shard! (+1.2 " + G + " Luck)"));
        eq("colour codes in front", Optional.of(new RareDrops.Drop("SPECIAL", "Nebula", 1)),
                RareDrops.parse("§d§lSPECIAL! §7You found x1 §dNebula! (+321.9 " + G + " Luck)"));
        eq("a new rarity counts with Luck", Optional.of(new RareDrops.Drop("COSMIC", "Star", 1)),
                RareDrops.parse("COSMIC! You found Star x1 (+12 " + G + " Luck)"));
    }

    private static void dropSpoofs() {
        // The user's own example: typed by them, it must never count.
        eq("typed by the player", Optional.empty(),
                RareDrops.parse("[★508] ✠:ʀᴜɴɪᴄ:✠ 81hp_: SPECIAL! You found x1 Nebula! (+321.9 " + G + " Luck)"));
        eq("typed by another player", Optional.empty(),
                RareDrops.parse("[★644] ELITE aMatya: EXOTIC! You found LEGENDARY key x1 (+56.08  Luck)"));
        eq("a whisper", Optional.empty(),
                RareDrops.parse("From 81hp_: SPECIAL! You found x1 Nebula! (+321.9 " + G + " Luck)"));
        eq("indented", Optional.empty(), RareDrops.parse(" SPECIAL! You found x1 Nebula! (+321.9 " + G + " Luck)"));
        eq("someone else's drop", Optional.empty(), RareDrops.parse("SPECIAL! 81hp_ found Nebula x1!"));
        eq("another player's broadcast", Optional.empty(), RareDrops.parse("EXOTIC! JsMew found Star x1!"));
        eq("essence is not a drop", Optional.empty(), RareDrops.parse("ESSENCE! You found x10 Ice Essence!"));
        eq("dungeon buff", Optional.empty(), RareDrops.parse("DUNGEON BUFF! You found a Blessing of Life V! (12s)"));
        eq("something apocalyptic", Optional.empty(), RareDrops.parse("You found something APOCALYPTIC!"));
        eq("gemstone", Optional.empty(), RareDrops.parse("✦ You found a Malachite gemstone!"));
        eq("brackets that are not Luck", Optional.empty(), RareDrops.parse("RARE! You found Thing x1 (30s)"));
        eq("lower case", Optional.empty(), RareDrops.parse("special! You found x1 Nebula! (+1 Luck)"));
        eq("null", Optional.empty(), RareDrops.parse(null));
        eq("empty", Optional.empty(), RareDrops.parse(""));
    }

    private static void dropCounting() {
        RareDrops.Counter c = new RareDrops.Counter(new RareDrops.Saved());
        c.add(new RareDrops.Drop("SPECIAL", "Nebula", 1), 1_000L);
        c.add(new RareDrops.Drop("RARE", "Sculk Charm", 1), 2_000L);
        c.add(new RareDrops.Drop("SPECIAL", "nebula", 2), 3_000L);
        List<RareDrops.Row> rows = c.rows(Set.of(), 10);
        eq("one row per item, any case", 2, rows.size());
        eq("latest found on top", "nebula", rows.get(0).name());
        eq("session count adds up", 3L, rows.get(0).session());
        eq("all time too", 3L, rows.get(0).allTime());
        eq("hidden rarity left out", 1, c.rows(Set.of("RARE"), 10).size());
        eq("row limit", 1, c.rows(Set.of(), 1).size());
        eq("recent newest first", "nebula", c.recent(Set.of(), 5).get(0).name);
        eq("recent skips hidden", 2, c.recent(Set.of("RARE"), 5).size());

        // A new session: all time stays, the session starts over; items from before sort after.
        RareDrops.Counter next = new RareDrops.Counter(c.saved());
        next.add(new RareDrops.Drop("RARE", "Sculk Charm", 1), 4_000L);
        List<RareDrops.Row> after = next.rows(Set.of(), 10);
        eq("found this session first", "Sculk Charm", after.get(0).name());
        eq("old item, no session count", 0L, after.get(1).session());
        eq("old item keeps all time", 3L, after.get(1).allTime());

        for (int i = 0; i < 30; i++) {
            next.add(new RareDrops.Drop("RARE", "Item " + i, 1), 5_000L + i);
        }
        eq("recent list is capped", RareDrops.RECENT_KEPT, next.saved().recent.size());
        next.resetSession();
        eq("session reset keeps all time", 3L, next.rows(Set.of(), 50).stream()
                .filter(r -> r.name().equals("nebula")).findFirst().map(RareDrops.Row::allTime).orElse(-1L));
        next.resetAll();
        eq("reset all", 0, next.rows(Set.of(), 50).size());

        RareDrops.Saved broken = new RareDrops.Saved();
        broken.items = null;
        broken.recent = null;
        eq("repaired config", 0, new RareDrops.Counter(broken).rows(Set.of(), 5).size());
    }

    private static void stats() {
        List<String> lore = List.of("MINING STAT", "", "How many drops you get from each", "block. Works like vanilla Fortune.",
                "", "CLICK to see breakdown");
        eq("fortune, from the screenshot", Optional.of(new PlayerStats.Stat("Fortune", "967,900", "Mining")),
                PlayerStats.read("✤ Fortune 967,900", lore));
        eq("colour codes", Optional.of(new PlayerStats.Stat("Luck", "321.9", "Mining")),
                PlayerStats.read("§a☘ §fLuck §e321.9", List.of("§7MINING STAT")));
        eq("two words, percent", Optional.of(new PlayerStats.Stat("Crit Chance", "12.5%", "Combat")),
                PlayerStats.read("Crit Chance: 12.5%", List.of("COMBAT STAT")));
        eq("multiplier", Optional.of(new PlayerStats.Stat("Turret Luck", "x1.5", "Mining")),
                PlayerStats.read("✦ Turret Luck x1.5", List.of("ᴍɪɴɪɴɢ ѕᴛᴀᴛ")));
        eq("no STAT line: not a stat", Optional.empty(), PlayerStats.read("Fortune 967,900", List.of("Some item")));
        eq("glass pane", Optional.empty(), PlayerStats.read(" ", List.of()));
        eq("close button", Optional.empty(), PlayerStats.read("Close", List.of("MINING STAT")));
        eq("a sentence mentioning stats", Optional.empty(),
                PlayerStats.read("Fortune 5", List.of("Click to see your stats in detail")));

        List<PlayerStats.Stat> all = PlayerStats.readAll(
                List.of("✤ Fortune 967,900", "Back", "☘ Luck 321.9", "✤ Fortune 967,900"),
                List.of(lore, List.of(), List.of("MINING STAT"), lore));
        eq("menu read in order, once each", List.of("Fortune", "Luck"), all.stream().map(PlayerStats.Stat::name).toList());
        eq("one stat is not the stats menu", List.of(), PlayerStats.readAll(List.of("✤ Fortune 1"), List.of(lore)));

        eq("number with commas", 967900.0, PlayerStats.number("967,900"));
        eq("percent", 12.5, PlayerStats.number("12.5%"));
        eq("multiplier", 1.5, PlayerStats.number("x1.5"));
        eq("suffix", 2_500_000.0, PlayerStats.number("2.5M"));
        eq("negative", -3.0, PlayerStats.number("-3"));
        yes("junk", Double.isNaN(PlayerStats.number("abc")));
    }

    private static void statsUpdates() {
        PlayerStats.Saved first = PlayerStats.update(null, List.of(new PlayerStats.Stat("Fortune", "100", "Mining"),
                new PlayerStats.Stat("Luck", "5", "Mining")), 1L);
        eq("first reading has no trend", 0, PlayerStats.trend(first.stats.get(0)));
        PlayerStats.Saved second = PlayerStats.update(first, List.of(new PlayerStats.Stat("Fortune", "120", "Mining"),
                new PlayerStats.Stat("Luck", "4", "Mining")), 2L);
        eq("went up", 1, PlayerStats.trend(second.stats.get(0)));
        eq("went down", -1, PlayerStats.trend(second.stats.get(1)));
        PlayerStats.Saved third = PlayerStats.update(second, List.of(new PlayerStats.Stat("Fortune", "120", "Mining")), 3L);
        eq("unchanged since: arrow gone", 0, PlayerStats.trend(third.stats.get(0)));
        eq("a stat gone from the menu is dropped", 1, third.stats.size());
        eq("read time kept", 3L, third.readAt);
    }

    private static void miningRate() {
        MiningRate r = new MiningRate();
        long t = 0L;
        r.observe("Shale", 1_000.0, t);
        // Two minutes of mining, +100 a second.
        for (int i = 1; i <= 120; i++) {
            t += 1_000L;
            r.tick(t);
            r.observe("Shale", 1_000.0 + 100.0 * i, t);
        }
        // The first tick only starts the clock, so two minutes measure as 119 seconds.
        yes("per hour", Math.abs(r.perHour("Shale", 10 * 60_000L) - 360_000.0) < 360_000.0 * 0.02);
        eq("session", 12_000.0, r.session("shale"));

        // A shop purchase: the balance falls. Not mining, and nothing taken off.
        t += 1_000L;
        r.tick(t);
        r.observe("Shale", 500.0, t);
        eq("spending is ignored", 12_000.0, r.session("Shale"));

        // A long break: the time does not count, and what came in meanwhile (turrets) is no gain.
        long before = r.miningMs();
        for (int i = 0; i < 600; i++) {
            t += 1_000L;
            r.tick(t);
        }
        yes("a break is not mining time", r.miningMs() - before <= MiningRate.IDLE_MS + 1_000L);
        r.observe("Shale", 900_000.0, t);
        eq("first reading after a break is a baseline", 12_000.0, r.session("Shale"));

        // Another mine in between: its first reading is a baseline, then back to Shale too.
        t += 1_000L;
        r.tick(t);
        r.observe("Amber", 5_000_000.0, t);
        t += 1_000L;
        r.tick(t);
        r.observe("Shale", 950_000.0, t);
        eq("switching mines counts nothing", 12_000.0, r.session("Shale"));
        eq("nor for the other mine", 0.0, r.session("Amber"));
        t += 1_000L;
        r.tick(t);
        r.observe("Shale", 950_100.0, t);
        eq("then counts again", 12_100.0, r.session("Shale"));

        MiningRate fresh = new MiningRate();
        fresh.observe("Shale", 10.0, 0L);
        fresh.tick(5_000L);
        fresh.observe("Shale", 20.0, 5_000L);
        yes("too little time: no rate yet", Double.isNaN(fresh.perHour("Shale", 600_000L)));
        fresh.observe(null, 5.0, 6_000L);
        fresh.observe("Shale", Double.NaN, 6_000L);
        eq("junk readings ignored", 10.0, fresh.session("Shale"));
    }

    private static void panels() {
        eq("eta minutes", "12m", ExtrasHud.eta(12 * 60_000L + 5_000L));
        eq("eta hours", "2h 13m", ExtrasHud.eta((2 * 60 + 13) * 60_000L));
        eq("eta days", "3d 4h", ExtrasHud.eta((3 * 24 + 4) * 3_600_000L));
        eq("time to, simple", OptionalLong.of(3_600_000L), ExtrasHud.timeTo(1_000L, 1_000.0));
        eq("time to, nothing left", OptionalLong.of(0L), ExtrasHud.timeTo(0L, Double.NaN));
        eq("time to, no rate", OptionalLong.empty(), ExtrasHud.timeTo(10L, Double.NaN));
        eq("time to, over a year", OptionalLong.empty(), ExtrasHud.timeTo(Long.MAX_VALUE / 2, 1.0));
        eq("ago", "12m ago", ExtrasHud.ago(12 * 60_000L));
        eq("ago now", "now", ExtrasHud.ago(1_000L));

        List<HudModel.Line> mining = ExtrasHud.miningPanel(new ExtrasHud.Mining("Shale", OptionalLong.of(36_420_000_000L),
                1_250_000_000.0, 4_500_000_000.0, 3_600_000L,
                List.of(new ExtrasHud.Eta("Total left", 7_200_000L))));
        yes("mining panel: mine, balance and rate",
                mining.stream().anyMatch(l -> l.text().contains("Shale") && l.text().contains("36.42b")
                        && l.text().contains("1.25b/h")));
        yes("mining panel: eta", mining.stream().anyMatch(l -> l.text().contains("Total left") && l.text().contains("in 2h 0m")));
        yes("mining panel before any mining", ExtrasHud.miningPanel(null).stream()
                .anyMatch(l -> l.text().contains("start mining")));

        PlayerStats.Saved saved = PlayerStats.update(null, List.of(new PlayerStats.Stat("Fortune", "967,900", "Mining"),
                new PlayerStats.Stat("Luck", "321.9", "Mining")), 1L);
        List<HudModel.Line> stats = ExtrasHud.statsPanel(saved, Set.of("luck"));
        yes("stats panel shows a stat", stats.stream().anyMatch(l -> l.text().contains("Fortune") && l.text().contains("967,900")));
        yes("hidden stat left out", stats.stream().noneMatch(l -> l.text().contains("Luck")));
        yes("stats panel before reading", ExtrasHud.statsPanel(new PlayerStats.Saved(), Set.of()).stream()
                .anyMatch(l -> l.text().contains("/stats")));

        RareDrops.Counter c = new RareDrops.Counter(new RareDrops.Saved());
        c.add(new RareDrops.Drop("SPECIAL", "Nebula", 1), 0L);
        List<HudModel.Line> drops = ExtrasHud.dropsPanel(c.rows(Set.of(), 5), c.recent(Set.of(), 3), 120_000L);
        yes("drops panel row", drops.stream().anyMatch(l -> l.text().contains("Nebula x1") && l.text().contains("1 total")));
        yes("drops panel latest", drops.stream().anyMatch(l -> l.text().contains("2m ago")));
        yes("empty drops panel", ExtrasHud.dropsPanel(List.of(), List.of(), 0L).stream()
                .anyMatch(l -> l.text().contains("none yet")));
    }

    // ------------------------------------------------------------------ util

    private static void eq(String what, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) pass(what); else fail(what, expected, actual);
    }

    private static void yes(String what, boolean c) {
        if (c) pass(what); else fail(what, "true", "false");
    }

    private static void pass(String what) {
        passed++;
        System.out.println("  ok   " + what);
    }

    private static void fail(String what, Object e, Object a) {
        failed++;
        System.out.println("  FAIL " + what + "  expected <" + e + "> got <" + a + ">");
    }
}
