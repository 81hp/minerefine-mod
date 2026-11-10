package minerefinehud;

import minerefinehud.beacon.BeaconTimer;
import minerefinehud.drops.RareDrops;
import minerefinehud.hud.ExtrasHud;
import minerefinehud.hud.HudModel;
import minerefinehud.job.JobCalc;
import minerefinehud.mine.ExpTotal;
import minerefinehud.mine.HeldRate;
import minerefinehud.mine.MiningRate;
import minerefinehud.mine.XpRate;
import minerefinehud.outpost.OutpostTimers;
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
        job();
        dropSpoofs();
        dropCounting();
        stats();
        statsUpdates();
        miningRate();
        vaultIsNotMining();
        rateOverWindow();
        heldRate();
        expTotal();
        panels();
        beacon();
        outposts();
        xp();

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
        yes("no block yet: no rate", Double.isNaN(r.perHour("Shale", 10)));
        // One block a second, +100 each: 360,000 an hour from the very first block.
        t += 1_000L;
        r.tick(t);
        r.observe("Shale", 1_100.0, t);
        eq("first block gives the rate straight away", 360_000.0, r.perHour("Shale", 10));
        for (int i = 2; i <= 120; i++) {
            t += 1_000L;
            r.tick(t);
            r.observe("Shale", 1_000.0 + 100.0 * i, t);
        }
        eq("steady mining stays the same, never creeps up", 360_000.0, r.perHour("Shale", 10));
        eq("session", 12_000.0, r.session("shale"));

        // A lucky block: +1,000 in one second.
        t += 1_000L;
        r.tick(t);
        r.observe("Shale", 13_000.0 + 1_000.0, t);
        eq("last block alone", 3_600_000.0, r.perHour("Shale", 1));
        eq("averaged over 10 blocks", (900.0 + 1_000.0) * 360.0, r.perHour("Shale", 10));

        // The action bar showing the same figure again is not a block.
        r.observe("Shale", 14_000.0, t + 200L);
        eq("same figure again changes nothing", 3_600_000.0, r.perHour("Shale", 1));

        // A shop purchase: the balance falls. Nothing taken off, and the next block counts from it.
        t += 1_000L;
        r.tick(t);
        r.observe("Shale", 500.0, t);
        eq("spending is ignored", 13_000.0, r.session("Shale"));
        t += 1_000L;
        r.tick(t);
        r.observe("Shale", 600.0, t);
        eq("mining after spending counts from the lower figure", 13_100.0, r.session("Shale"));

        // A slow block (looking around for 20 seconds) counts as at most 10.
        for (int i = 0; i < 20; i++) {
            t += 1_000L;
            r.tick(t);
        }
        r.observe("Shale", 700.0, t);
        eq("a long gap is capped", 100.0 * 360.0, r.perHour("Shale", 1));

        // A long break: not mining time, and what came in meanwhile (turrets) is no gain.
        long before = r.miningMs();
        for (int i = 0; i < 600; i++) {
            t += 1_000L;
            r.tick(t);
        }
        yes("a break is not mining time", r.miningMs() - before <= MiningRate.IDLE_MS + 1_000L);
        r.observe("Shale", 900_000.0, t);
        eq("first reading after a break is a baseline", 13_200.0, r.session("Shale"));

        // Another mine in between: its first reading is a baseline, then back to Shale too.
        t += 1_000L;
        r.observe("Amber", 5_000_000.0, t);
        yes("another mine has no rate yet", Double.isNaN(r.perHour("Amber", 10)));
        yes("the old mine's rate is not shown for it", Double.isNaN(r.perHour("Shale", 10)));
        t += 1_000L;
        r.observe("Shale", 950_000.0, t);
        eq("switching mines counts nothing", 13_200.0, r.session("Shale"));
        eq("nor for the other mine", 0.0, r.session("Amber"));
        t += 2_000L;
        r.observe("Shale", 950_100.0, t);
        eq("then counts again", 13_300.0, r.session("Shale"));
        eq("from that block", 180_000.0, r.perHour("Shale", 10));

        MiningRate fresh = new MiningRate();
        fresh.observe("Shale", 10.0, 0L);
        fresh.observe("Shale", 20.0, 0L);
        yes("two readings in the same instant: no rate", Double.isNaN(fresh.perHour("Shale", 10)));
        fresh.observe(null, 5.0, 6_000L);
        fresh.observe("Shale", Double.NaN, 6_000L);
        eq("junk readings ignored", 10.0, fresh.session("Shale"));
        fresh.reset();
        eq("reset", 0.0, fresh.session("Shale"));
    }

    /** The rate over the last 30 seconds, 1, 2, 5 or 10 minutes: wall clock, falls when mining stops. */
    private static void rateOverWindow() {
        MiningRate r = new MiningRate();
        long t = 0L;
        double balance = 0.0;
        r.observe("Shale", balance, t);
        yes("window: no block, no rate", Double.isNaN(r.perHourOver("Shale", 30_000L, t)));
        // 3 minutes at +100 a second, then 3 minutes at +300 a second, one block a second.
        for (int i = 1; i <= 360; i++) {
            t += 1_000L;
            r.tick(t);
            balance += 100.0 * (i <= 180 ? 1 : 3);
            r.observe("Shale", balance, t);
            if (i == 1) {
                eq("window: right after the first block", 360_000.0, r.perHourOver("Shale", 30_000L, t));
            }
        }
        eq("30 s: only the fast part", 300.0 * 3_600.0, r.perHourOver("Shale", 30_000L, t));
        eq("1 min", 300.0 * 3_600.0, r.perHourOver("Shale", 60_000L, t));
        eq("5 min mixes both", (180 * 300.0 + 120 * 100.0) * 3_600.0 / 300.0, r.perHourOver("Shale", 300_000L, t));
        eq("10 min before it has filled is over what there is", (180 * 300.0 + 180 * 100.0) * 3_600.0 / 360.0,
                r.perHourOver("Shale", 600_000L, t));
        yes("another mine: none", Double.isNaN(r.perHourOver("Amber", 60_000L, t)));

        // Mining stops: it eases for 4 seconds, then holds where it was.
        double at4 = 26 * 300.0 * 3_600.0 / 30.0;
        eq("4 s after the last block", at4, r.perHourOver("Shale", 30_000L, t + 4_000L));
        eq("then it holds, 15 s later", at4, r.perHourOver("Shale", 30_000L, t + 15_000L));
        eq("and for a whole window and more", at4, r.perHourOver("Shale", 30_000L, t + 120_000L));
        eq("the 1 min rate holds too", 56 * 300.0 * 3_600.0 / 60.0, r.perHourOver("Shale", 60_000L, t + 90_000L));
        // Mining again moves it on from the real time.
        r.observe("Shale", balance, t + 40_000L);
        balance += 300.0;
        r.observe("Shale", balance, t + 41_000L);
        eq("a new block after the pause moves it on", 300.0 * 3_600.0 / 30.0,
                r.perHourOver("Shale", 30_000L, t + 41_000L));

        XpRate e = new XpRate();
        long u = 1_000_000L;
        yes("xp window: no blocks, no rate", Double.isNaN(e.perHourOver(60_000L, u)));
        for (int i = 0; i <= 120; i++) {
            e.observe(1_000, i, u + i * 1_000L);
        }
        long end = u + 120_000L;
        yes("xp window: 1000 a second is 3.6m an hour", Math.abs(e.perHourOver(60_000L, end) - 3_600_000.0) < 1.0);
        yes("xp window: holds after the pause",
                Math.abs(e.perHourOver(120_000L, end + 600_000L) - 116_000.0 * 3_600.0 / 120.0) < 1.0);
        eq("xp window: the same a minute later", e.perHourOver(30_000L, end + 4_000L),
                e.perHourOver(30_000L, end + 60_000L));
        XpRate longRun = new XpRate();
        for (int i = 0; i < 4_000; i++) {
            longRun.observe(10, i, u + i * 1_000L);
        }
        yes("xp window: right after a long session",
                Math.abs(longRun.perHourOver(600_000L, u + 3_999_000L) - 36_000.0) < 1.0);
    }

    /** The figure on the panel changes at most every 5 seconds. */
    private static void heldRate() {
        HeldRate h = new HeldRate();
        double[] live = { Double.NaN };
        eq("held: no figure yet", true, Double.isNaN(h.get(0L, "a", () -> live[0])));
        live[0] = 100.0;
        eq("held: the first figure shows at once", 100.0, h.get(100L, "a", () -> live[0]));
        live[0] = 200.0;
        eq("held: not changed after 1 s", 100.0, h.get(1_100L, "a", () -> live[0]));
        eq("held: nor after 4.9 s", 100.0, h.get(5_000L, "a", () -> live[0]));
        eq("held: replaced after 5 s", 200.0, h.get(5_100L, "a", () -> live[0]));
        live[0] = 300.0;
        eq("held: another mine shows at once", 300.0, h.get(5_200L, "b", () -> live[0]));
        live[0] = 400.0;
        eq("held: a clock that went back refreshes", 400.0, h.get(1_000L, "b", () -> live[0]));
        h.reset();
        live[0] = 7.0;
        eq("held: reset", 7.0, h.get(1_001L, "b", () -> live[0]));
    }

    /** EXP per hour from the total in /profile. */
    private static void expTotal() {
        eq("profile: the head's lore", 653_952_884L,
                ExpTotal.parse(List.of("", "REBIRTH 3", "2,707,799/3,500,000 EXP (77.87%)", "FREE: EMERALD",
                        "TOTAL EXP: \u2726 653,952,884")).orElse(-1));
        eq("profile: colour codes", 1_200L, ExpTotal.parse(List.of("§bTOTAL EXP: §f1,200")).orElse(-1));
        yes("profile: the level line is not the total",
                ExpTotal.parse(List.of("2,707,799/3,500,000 EXP (77.87%)")).isEmpty());
        yes("profile: nothing", ExpTotal.parse(List.of()).isEmpty() && ExpTotal.parse(null).isEmpty());

        ExpTotal e = new ExpTotal();
        long t = 1_000_000L;
        yes("profile: no reading, no rate", Double.isNaN(e.perHourOver(60_000L)) && !e.any());
        e.observe(1_000_000L, t);
        yes("profile: one reading gives no rate", Double.isNaN(e.perHourOver(60_000L)));
        eq("profile: the first reading is a baseline", 0.0, e.session());
        // +500,000 per 30 s: 60m an hour.
        for (int i = 1; i <= 10; i++) {
            e.observe(1_000_000L + 500_000L * i, t + 30_000L * i);
        }
        eq("profile: rate over 30 s", 60_000_000.0, e.perHourOver(30_000L));
        eq("profile: rate over 5 min", 60_000_000.0, e.perHourOver(300_000L));
        eq("profile: session", 5_000_000.0, e.session());
        // Faster for the last 30 s.
        e.observe(1_000_000L + 5_000_000L + 1_500_000L, t + 330_000L);
        eq("profile: the last 30 s alone", 180_000_000.0, e.perHourOver(30_000L));
        yes("profile: 1 min mixes", Math.abs(e.perHourOver(60_000L) - 120_000_000.0) < 1.0);
        yes("profile: fresh readings are not stale", !e.stale(t + 340_000L));
        yes("profile: old readings are stale", e.stale(t + 330_000L + ExpTotal.MAX_GAP_MS + 1L));

        // A long gap: what came in meanwhile is not counted, and the rate starts again.
        double before = e.session();
        long later = t + 330_000L + 600_000L;
        e.observe(900_000_000L, later);
        eq("profile: a gap counts nothing", before, e.session());
        yes("profile: and gives no rate yet", Double.isNaN(e.perHourOver(60_000L)));
        e.observe(900_000_000L + 100_000L, later + 30_000L);
        eq("profile: then counts again", before + 100_000L, e.session());
        eq("profile: at the new rate", 12_000_000.0, e.perHourOver(60_000L));
        // The total falls (a rebirth): start again, never a negative rate.
        e.observe(5_000L, later + 60_000L);
        eq("profile: a fall counts nothing", before + 100_000L, e.session());
        e.observe(5_000L + 10_000L, later + 90_000L);
        eq("profile: counts from the new total", before + 110_000L, e.session());
        e.reset();
        yes("profile: reset", !e.any() && e.session() == 0.0 && Double.isNaN(e.perHourOver(60_000L)));
    }

    /** A user took Rosewood out of their vault: 174,318b/h and +15,041b in the session. */
    private static void vaultIsNotMining() {
        MiningRate r = new MiningRate();
        long t = 0L;
        r.observe("Rosewood", 1_000.0, t);
        for (int i = 1; i <= 5; i++) {
            t += 1_000L;
            r.tick(t);
            r.observe("Rosewood", 1_000.0 + i, t);
        }
        eq("five blocks", 5.0, r.session("Rosewood"));
        t += 1_000L;
        r.observe("Rosewood", 2_005.0, t);
        eq("a thousand from the vault is not mined", 5.0, r.session("Rosewood"));
        eq("nor in the rate", 3_600.0, r.perHour("Rosewood", 5));
        t += 1_000L;
        r.observe("Rosewood", 2_006.0, t);
        eq("the next block counts from there", 6.0, r.session("Rosewood"));

        // With the vault open the items come in before the next block.
        r.interrupt();
        t += 1_000L;
        r.observe("Rosewood", 2_009.0, t);
        eq("the reading after a menu is a baseline", 6.0, r.session("Rosewood"));
        t += 1_000L;
        r.observe("Rosewood", 2_010.0, t);
        eq("then mining counts again", 7.0, r.session("Rosewood"));
        eq("and the blocks before still make the rate", 3_600.0, r.perHour("Rosewood", 5));

        // A far better pickaxe: every block is 30 times more. Three in a row and it starts again.
        for (int i = 1; i <= 3; i++) {
            t += 1_000L;
            r.observe("Rosewood", 2_010.0 + 30.0 * i, t);
        }
        t += 1_000L;
        r.observe("Rosewood", 2_130.0, t);
        eq("after three, the new yield counts", 37.0, r.session("Rosewood"));
        eq("at its own rate", 30.0 * 3_600.0, r.perHour("Rosewood", 1));
        t += 1_000L;
        r.observe("Rosewood", 2_160.0, t);
        eq("and keeps counting", 67.0, r.session("Rosewood"));
    }

    /** From the /outpost screenshots, 2026-11-09. */
    private static void xp() {
        yes("xp: read from the mining line",
                XpRate.parse("+140,732 EXP   +34.20 Coins   +0.135 Tokens  |  1.55B[block/cobblestone]")
                        .orElse(-1) == 140_732.0);
        yes("xp: colour codes", XpRate.parse("§b+1,200 EXP §7| §f1.55B[block/cobblestone]").orElse(-1) == 1_200.0);
        yes("xp: a decimal", XpRate.parse("+734.5 EXP  +2.04 Coins").orElse(-1) == 734.5);
        yes("xp: a suffix", XpRate.parse("+1.5M EXP | 3B[block/sponge]").orElse(-1) == 1_500_000.0);
        yes("xp: coins are not exp", XpRate.parse("+34.20 Coins  +0.135 Tokens").isEmpty());
        yes("xp: the idle line has none", XpRate.parse("267.1/267.1 \u2764   24,290 \u26e8").isEmpty());
        yes("xp: hunting exp is its own line", XpRate.parse("Hunting EXP").isEmpty());
        yes("xp: nothing", XpRate.parse("").isEmpty() && XpRate.parse(null).isEmpty());

        XpRate x = new XpRate();
        long t = 1_000_000L;
        yes("xp: no rate before a block", Double.isNaN(x.perHour(10)));
        x.observe(1_000, 100, t);
        yes("xp: the first line counts for the session", x.session() == 1_000.0);
        yes("xp: but gives no rate", Double.isNaN(x.perHour(10)));
        x.observe(1_000, 101, t + 2_000L);
        yes("xp: 1000 in 2s is 1.8m an hour", Math.abs(x.perHour(10) - 1_800_000.0) < 1.0);
        x.observe(1_000, 101, t + 3_000L);
        yes("xp: the same line again is not counted", x.session() == 2_000.0);
        x.observe(2_000, 102, t + 6_000L);
        // blocks: 1000 in 2s, 2000 in 4s -> 3000 / 6s = 1.8m
        yes("xp: two blocks together", Math.abs(x.perHour(10) - 1_800_000.0) < 1.0);
        yes("xp: last block alone", Math.abs(x.perHour(1) - 1_800_000.0) < 1.0);
        yes("xp: session adds up", x.session() == 4_000.0);

        x.observe(500, 103, t + 6_000L + 5 * 60_000L);
        yes("xp: after a break the first line is a baseline, rate restarts", Double.isNaN(x.perHour(10)));
        yes("xp: and is still in the session", x.session() == 4_500.0);
        x.observe(500, 104, t + 6_000L + 5 * 60_000L + 1_000L);
        yes("xp: 500 in 1s", Math.abs(x.perHour(10) - 1_800_000.0) < 1.0);

        XpRate slow = new XpRate();
        slow.observe(100, 1, t);
        slow.observe(100, 2, t + 20_000L);
        yes("xp: a long gap counts as 10s, not 20s", Math.abs(slow.perHour(10) - 36_000.0) < 1.0);

        XpRate timed = new XpRate();
        timed.tick(t);
        timed.observe(10, 1, t);
        for (int i = 1; i <= 120; i++) {
            timed.tick(t + i * 1_000L);
            if (i % 5 == 0) {
                timed.observe(10, 1 + i, t + i * 1_000L);
            }
        }
        yes("xp: mining time runs while lines come", timed.miningMs() >= 110_000L);
        timed.reset();
        yes("xp: reset empties it", !timed.any() && timed.session() == 0.0 && timed.miningMs() == 0L
                && Double.isNaN(timed.perHour(10)));

        var mine = new ExtrasHud.Mining("Shale", OptionalLong.of(36_420_000_000L), 1_250_000_000.0,
                4_510_000_000.0, 3L * 3_600_000L, List.of());
        yes("xp panel: no EXP row unless asked", ExtrasHud.miningPanel(mine).size() == ExtrasHud.miningPanel(mine, null).size());
        var plain = ExtrasHud.miningPanel(mine, null);
        var withXp = ExtrasHud.miningPanel(mine, new ExtrasHud.Xp(1_200_000.0, 4_100_000.0));
        yes("xp panel: one row more", withXp.size() == plain.size() + 1);
        var row = withXp.get(withXp.size() - 1);
        yes("xp panel: EXP row has the session and the rate", row.text().startsWith("EXP") && row.text().contains("+4.1m")
                && row.text().contains("1.2m/h"));
        yes("xp panel: sits under the session row", withXp.get(withXp.size() - 2).text().startsWith("Session"));
        var measuring = ExtrasHud.miningPanel(mine, new ExtrasHud.Xp(Double.NaN, 1_500.0));
        yes("xp panel: no rate yet", measuring.get(measuring.size() - 1).text().contains("\u2026/h"));
        yes("xp panel: nothing earned yet adds no row",
                ExtrasHud.miningPanel(mine, new ExtrasHud.Xp(Double.NaN, 0.0)).size() == plain.size());
        yes("xp panel: the sample has the row", minerefinehud.hud.SamplePanels.mining().stream()
                .anyMatch(l -> l.text().startsWith("EXP")));
    }

    private static void outposts() {
        List<String> lore = List.of("", "♝ King - Arreba__", "♗ Prince VACANT", "", "ᴏᴘᴇɴs ɪɴ: 3h 58m 40s", "",
                "YOU HOLD SEAT #1", "", "CLICK FOR MORE INFO");
        Optional<OutpostTimers.Reading> r = OutpostTimers.read("Ruins Outpost", lore);
        yes("outpost: read from the menu", r.isPresent() && r.get().name().equals("Ruins")
                && r.get().opensInMs() == (3 * 3600 + 58 * 60 + 40) * 1000L);
        yes("outpost: plain capitals too", OutpostTimers.read("Sift Outpost", List.of("OPENS IN: 1h 17m 55s"))
                .map(x -> x.opensInMs() == (3600 + 17 * 60 + 55) * 1000L).orElse(false));
        yes("outpost: colour codes in the name", OutpostTimers.read("§6§lSift Outpost", List.of("opens in: 5m"))
                .map(x -> x.name().equals("Sift")).orElse(false));
        yes("outpost: not an outpost", OutpostTimers.read("Gold Block", lore).isEmpty());
        yes("outpost: no time line", OutpostTimers.read("Ruins Outpost", List.of("CLICK FOR MORE INFO")).isEmpty());
        yes("outpost: unreadable time", OutpostTimers.read("Ruins Outpost", List.of("OPENS IN: soon")).isEmpty());
        yes("outpost: null", OutpostTimers.read(null, null).isEmpty());
        eq("outpost: a menu with two", 2, OutpostTimers.readAll(
                List.of("Ruins Outpost", "Glass Pane", "Sift Outpost", "Ruins Outpost"),
                List.of(lore, List.of(), List.of("OPENS IN: 1h"), lore)).size());

        long h = 3_600_000L;
        OutpostTimers.Saved saved = OutpostTimers.update(null, List.of(
                new OutpostTimers.Reading("Ruins", 4 * h), new OutpostTimers.Reading("Sift", h)), 1_000L);
        List<OutpostTimers.View> v0 = OutpostTimers.views(saved, 1_000L);
        eq("outpost: soonest first", "Sift", v0.get(0).name());
        eq("outpost: counts down", OutpostTimers.Phase.WAITING, v0.get(0).phase());
        eq("outpost: time left", h, v0.get(0).ms());
        yes("outpost: read, not guessed", !v0.get(0).estimated());

        long opens = 1_000L + h;
        OutpostTimers.View open = OutpostTimers.views(saved, opens + 60_000L).get(0);
        eq("outpost: open for the battle", OutpostTimers.Phase.BATTLE, open.phase());
        eq("outpost: battle time left", 4 * 60_000L, open.ms());
        yes("outpost: battle start is still read", !open.estimated());

        long afterBattle = opens + OutpostTimers.BATTLE_MS;
        OutpostTimers.View waiting = named(OutpostTimers.views(saved, afterBattle + 1_000L), "Sift");
        eq("outpost: four hours after the battle", OutpostTimers.Phase.WAITING, waiting.phase());
        eq("outpost: time to the next", OutpostTimers.WAIT_MS - 1_000L, waiting.ms());
        yes("outpost: next one is a guess", waiting.estimated());
        OutpostTimers.View second = named(OutpostTimers.views(saved,
                opens + OutpostTimers.BATTLE_MS + OutpostTimers.WAIT_MS + 30_000L), "Sift");
        eq("outpost: second battle", OutpostTimers.Phase.BATTLE, second.phase());
        eq("outpost: second battle time", OutpostTimers.BATTLE_MS - 30_000L, second.ms());

        yes("outpost: no recheck before it opens", !OutpostTimers.needsRecheck(saved, opens));
        yes("outpost: no recheck during the battle", !OutpostTimers.needsRecheck(saved, opens + 60_000L));
        yes("outpost: recheck after the battle", OutpostTimers.needsRecheck(saved, afterBattle));
        OutpostTimers.Saved reread = OutpostTimers.update(saved,
                List.of(new OutpostTimers.Reading("Sift", 4 * h - 30_000L)), afterBattle + 5_000L);
        yes("outpost: reading again settles it", !OutpostTimers.needsRecheck(
                OutpostTimers.update(reread, List.of(new OutpostTimers.Reading("Ruins", h)), afterBattle + 5_000L),
                afterBattle + 6_000L));
        eq("outpost: other outposts kept", 2, reread.outposts.size());
        eq("outpost: the old reading is not changed", 1_000L + h, saved.outposts.get(1).opensAt);
        eq("outpost: repaired nothing", 0, OutpostTimers.repair(null).outposts.size());

        eq("outpost: clock hours", "3:58:40", OutpostTimers.clock((3 * 3600 + 58 * 60 + 40) * 1000L));
        eq("outpost: clock minutes", "4:12", OutpostTimers.clock(4 * 60_000L + 12_000L));
        eq("outpost: short name", "Ruins", OutpostTimers.shortName("Ruins Outpost"));
        eq("outpost: short name without the word", "Ruins", OutpostTimers.shortName("Ruins"));

        eq("outpost alert: nothing yet", List.of(), OutpostTimers.alerts(v0, 60_000L));
        eq("outpost alert: opens soon", List.of("Sift Outpost opens in 0:45"),
                OutpostTimers.alerts(OutpostTimers.views(saved, opens - 45_000L).subList(0, 1), 60_000L));
        eq("outpost alert: just open", List.of("Sift Outpost is open!"),
                OutpostTimers.alerts(OutpostTimers.views(saved, opens + 2_000L).subList(0, 1), 60_000L));
        eq("outpost alert: open a while", List.of(),
                OutpostTimers.alerts(OutpostTimers.views(saved, opens + 60_000L).subList(0, 1), 60_000L));

        OutpostTimers.Pings pings = new OutpostTimers.Pings();
        yes("outpost ping: quiet", !pings.due(OutpostTimers.views(saved, 2_000L), 60_000L));
        yes("outpost ping: warning", pings.due(OutpostTimers.views(saved, opens - 50_000L), 60_000L));
        yes("outpost ping: once", !pings.due(OutpostTimers.views(saved, opens - 40_000L), 60_000L));
        yes("outpost ping: opening", pings.due(OutpostTimers.views(saved, opens + 1_000L), 60_000L));
        yes("outpost ping: once open", !pings.due(OutpostTimers.views(saved, opens + 3_000L), 60_000L));
        yes("outpost ping: off", !new OutpostTimers.Pings().due(OutpostTimers.views(saved, opens - 10_000L), 0L));

        List<HudModel.Line> panel = ExtrasHud.outpostPanel(OutpostTimers.views(saved, 1_000L), 60_000L);
        yes("outpost panel: header", panel.get(0).text().equals("Outposts"));
        yes("outpost panel: soonest row", panel.get(1).text().contains("Sift") && panel.get(1).text().contains("1:00:00"));
        yes("outpost panel: empty says how", ExtrasHud.outpostPanel(List.of(), 0L).stream()
                .anyMatch(l -> l.text().contains("/outpost")));
        yes("outpost panel: battle", ExtrasHud.outpostPanel(OutpostTimers.views(saved, opens + 60_000L), 0L).stream()
                .anyMatch(l -> l.text().contains("OPEN 4:00")));
        yes("outpost panel: guess marked", ExtrasHud.outpostPanel(OutpostTimers.views(saved, afterBattle + 1_000L), 0L)
                .stream().anyMatch(l -> l.text().contains("~")));
    }

    private static OutpostTimers.View named(List<OutpostTimers.View> views, String name) {
        return views.stream().filter(v -> v.name().equals(name)).findFirst().orElseThrow();
    }

    private static void beacon() {
        yes("beacon: server line", BeaconTimer.isReset("Beacon Mine has reset!"));
        yes("beacon: any case, with a prefix", BeaconTimer.isReset("[Mines] BEACON MINE HAS RESET!"));
        yes("beacon: another line", !BeaconTimer.isReset("Rubble Mine has reset!"));
        yes("beacon: player chat does not count", !BeaconTimer.isReset("Steve: beacon mine has reset"));
        yes("beacon: angle bracket chat does not count", !BeaconTimer.isReset("<Steve> beacon mine has reset"));
        yes("beacon: null", !BeaconTimer.isReset(null));

        BeaconTimer t = new BeaconTimer();
        eq("beacon: unknown", -1L, t.remaining(1_000L));
        yes("beacon: unknown never warns", !t.warnNow(1_000L, 15_000L));
        t.onReset(10_000L);
        eq("beacon: right after", BeaconTimer.CYCLE_MS, t.remaining(10_000L));
        eq("beacon: a minute in", 240_000L, t.remaining(70_000L));
        eq("beacon: a missed reset keeps cycling", 240_000L, t.remaining(70_000L + BeaconTimer.CYCLE_MS));
        eq("beacon: clock behind the reset", BeaconTimer.CYCLE_MS, t.remaining(5_000L));
        yes("beacon: notice just after", t.justReset(11_000L));
        yes("beacon: notice gone", !t.justReset(10_000L + BeaconTimer.NOTICE_MS));

        long warnAt = 10_000L + BeaconTimer.CYCLE_MS - 15_000L;
        yes("beacon: quiet before the warning", !t.warnNow(warnAt - 1L, 15_000L));
        yes("beacon: warns at 15s", t.warnNow(warnAt, 15_000L));
        yes("beacon: warns only once", !t.warnNow(warnAt + 5_000L, 15_000L));
        yes("beacon: warns again next cycle", !t.warnNow(warnAt + 20_000L, 15_000L)
                && t.warnNow(warnAt + BeaconTimer.CYCLE_MS, 15_000L));
        BeaconTimer quiet = new BeaconTimer();
        quiet.onReset(0L);
        yes("beacon: warning off", !quiet.warnNow(BeaconTimer.CYCLE_MS - 1_000L, 0L));
        t.clear();
        yes("beacon: cleared", !t.known() && t.remaining(99_000L) == -1L);

        List<HudModel.Line> waiting = ExtrasHud.beaconPanel(-1L, false);
        yes("beacon panel: waiting", waiting.stream().anyMatch(l -> l.text().contains("waiting for a reset")));
        List<HudModel.Line> running = ExtrasHud.beaconPanel(160_000L, false);
        yes("beacon panel: time", running.stream().anyMatch(l -> l.text().contains("Next reset") && l.text().contains("2:40")));
        yes("beacon panel: bar", running.stream().anyMatch(l -> l.style() == HudModel.Style.BAR
                && Math.abs(l.progress() - 160_000.0 / BeaconTimer.CYCLE_MS) < 1e-9));
        yes("beacon panel: urgent is a warning", ExtrasHud.beaconPanel(9_000L, true).stream()
                .anyMatch(l -> l.text().contains("0:09") && l.style() == HudModel.Style.WARN));
        eq("beacon alert: nothing", List.of(), ExtrasHud.beaconAlerts(100_000L, false, 15_000L));
        eq("beacon alert: soon", List.of("Beacon Mine resets in 11s"), ExtrasHud.beaconAlerts(11_200L, false, 15_000L));
        eq("beacon alert: reset", List.of("Beacon Mine has reset!"), ExtrasHud.beaconAlerts(300_000L, true, 15_000L));
        eq("beacon alert: warning off", List.of(), ExtrasHud.beaconAlerts(5_000L, false, 0L));
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


    // ------------------------------------------------------------------- job

    private static void job() {
        eq("amount 1B", 1e9, JobCalc.parseAmount("1B").getAsDouble());
        eq("amount 1.25b", 1.25e9, JobCalc.parseAmount(" 1.25b ").getAsDouble());
        eq("amount 250M", 2.5e8, JobCalc.parseAmount("250M").getAsDouble());
        eq("amount plain", 5000.0, JobCalc.parseAmount("5,000").getAsDouble());
        yes("amount junk", JobCalc.parseAmount("abc").isEmpty() && JobCalc.parseAmount("").isEmpty()
                && JobCalc.parseAmount(null).isEmpty() && JobCalc.parseAmount("0").isEmpty());
        eq("rate 1B", 1e9, JobCalc.parseRate("1B").getAsDouble());
        eq("rate 1.5B", 1.5e9, JobCalc.parseRate("1.5B").getAsDouble());
        eq("rate 1B/1c", 1e9, JobCalc.parseRate("1B/1c").getAsDouble());
        eq("rate 1.25B/1c", 1.25e9, JobCalc.parseRate("1.25B/1c").getAsDouble());
        eq("rate per credit", 1e9, JobCalc.parseRate("1b per credit").getAsDouble());
        eq("rate 2B/2c", 1e9, JobCalc.parseRate("2B/2c").getAsDouble());
        yes("rate half typed", JobCalc.parseRate("1.").isEmpty() && JobCalc.parseRate("B").isEmpty()
                && JobCalc.parseRate("1B/0c").isEmpty());

        List<JobCalc.Part> jade = List.of(new JobCalc.Part("sword", OptionalLong.of(136_510_000_000L)),
                new JobCalc.Part("charm", OptionalLong.of(70_290_000_000L)),
                new JobCalc.Part("boots", OptionalLong.of(112_780_000_000L)));
        JobCalc.Entry full = JobCalc.entry("Jade", jade, List.of(), 1);
        eq("entry sums every piece", 319_580_000_000L, full.mats());
        JobCalc.Entry noCharm = JobCalc.entry("Jade", jade, List.of(JobCalc.skipKey("JADE", "Charm")), 1);
        eq("a left-out piece is not counted", 249_290_000_000L, noCharm.mats());
        eq("skip key", "jade|charm", JobCalc.skipKey(" Jade ", "CHARM"));
        var unpriced = JobCalc.entry("New", List.of(new JobCalc.Part("sword", OptionalLong.empty()),
                new JobCalc.Part("charm", OptionalLong.of(5L))), List.of(), 1);
        eq("unpriced piece counted as missing", 1, unpriced.missing());
        eq("unpriced piece adds nothing", 5L, unpriced.mats());
        eq("skipping the unpriced piece clears it", 0, JobCalc.entry("New",
                List.of(new JobCalc.Part("sword", OptionalLong.empty())), List.of("new|sword"), 1).missing());
        JobCalc.Total t = JobCalc.total(List.of(noCharm, new JobCalc.Entry("Sift", 240_000_000_000L, 2, 0), unpriced),
                java.util.OptionalDouble.of(1e9));
        eq("total mats", 729_290_000_005.0, t.mats());
        eq("total credits", 729.290000005, t.credits());
        eq("total unknown", 1, t.unknown());
        yes("no rate gives NaN", Double.isNaN(JobCalc.total(List.of(noCharm), java.util.OptionalDouble.empty()).credits()));
        eq("world tag", "Sift", minerefinehud.job.JobWorlds.fromTag("SIFTBOUND").get());
        yes("not a world tag", minerefinehud.job.JobWorlds.fromTag("RARE").isEmpty()
                && minerefinehud.job.JobWorlds.fromTag("BOUND").isEmpty() && minerefinehud.job.JobWorlds.fromTag(null).isEmpty());
        eq("known Sift mine", "Sift", minerefinehud.job.JobWorlds.of("Jade", java.util.Map.of()));
        eq("learned world wins", "Dunes", minerefinehud.job.JobWorlds.of("Jade", java.util.Map.of("jade", "Dunes")));
        eq("unlisted mine goes to the Sift", "Sift", minerefinehud.job.JobWorlds.of("Petal", null));
        yes("known mines only", minerefinehud.job.JobWorlds.known("Petal", null)
                && !minerefinehud.job.JobWorlds.known("Great Fruit", java.util.Map.of())
                && minerefinehud.job.JobWorlds.known("Zorp", java.util.Map.of("zorp", "Sift")));
        eq("eleven Sift mines", 11, minerefinehud.job.JobWorlds.SIFT.size());
        eq("Sift order starts with Shale", "Shale", minerefinehud.job.JobWorlds.SIFT.get(0).get(0));
        yes("either spelling is known", minerefinehud.job.JobWorlds.known("Blub", null) && minerefinehud.job.JobWorlds.known("bulb", null));
        eq("decimal comma", 1.5e9, JobCalc.parseRate("1,5B").getAsDouble());
        eq("decimal comma two places", 1.25e9, JobCalc.parseRate("1,25B/1c").getAsDouble());
        eq("thousands comma", 1000.0, JobCalc.parseAmount("1,000").getAsDouble());
        eq("thousands commas", 1_250_000.0, JobCalc.parseAmount("1,250,000").getAsDouble());
        yes("odd commas are no number", JobCalc.parseAmount("1,5,5").isEmpty());
        JobCalc.Entry partial = JobCalc.entry("Jade", List.of(new JobCalc.Part("sword", OptionalLong.of(100L), true),
                new JobCalc.Part("charm", OptionalLong.of(50L))), List.of(), 1);
        eq("a partial piece is counted", 150L, partial.mats());
        eq("a partial piece marks the mine short", 1, partial.missing());
        eq("same mine under two spellings", java.util.Map.of("Sulfer", 2), JobCalc.counts(List.of("Sulfer", "Sulfur")));
        eq("remove by either spelling", List.of("Sulfer"), JobCalc.removeOne(List.of("Sulfer", "Sulfer"), "Sulfur"));
        eq("skip key ignores spelling", JobCalc.skipKey("Sulfer", "charm"), JobCalc.skipKey("sulfur", "Charm"));
        eq("decimal comma in the per part", 1e9 / 1.5, JobCalc.parseRate("1B/1,5c").getAsDouble());
        yes("an old skip under the other spelling still counts", JobCalc.isSkipped(List.of("sulfur|charm"), "Sulfer", "Charm"));
        eq("toggle puts a skipped piece back whatever the spelling", List.of(),
                JobCalc.toggleSkip(List.of("sulfur|charm"), "Sulfer", "charm"));
        eq("toggle leaves a piece out", List.of("jade|charm"), JobCalc.toggleSkip(List.of(), "Jade", "Charm"));
        eq("title case", "Ancient Debris", minerefinehud.job.JobWorlds.title("ancient  debris"));
        eq("mats text", "760.34b", JobCalc.mats(760_340_000_000.0));
        eq("mats small", "12,345", JobCalc.mats(12_345.0));
        eq("credits text", "1,000.34c", JobCalc.credits(1000.34));
        eq("credits none", "?", JobCalc.credits(Double.NaN));
        eq("counts", java.util.Map.of("a", 2, "b", 1), JobCalc.counts(List.of("a", "b", "a")));
        eq("remove one", List.of("a", "b"), JobCalc.removeOne(List.of("a", "b", "a"), "a").stream().sorted().toList());
        eq("remove missing", List.of("a"), JobCalc.removeOne(List.of("a"), "z"));
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
