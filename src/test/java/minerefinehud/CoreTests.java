package minerefinehud;

import minerefinehud.boss.BossMessageParser;
import minerefinehud.boss.BossAlerts;
import minerefinehud.boss.BossWorlds;
import minerefinehud.boss.BossTracker;
import minerefinehud.hud.Formatting;
import minerefinehud.hud.HudModel;
import minerefinehud.mine.ArmorSplit;
import minerefinehud.mine.Mine;
import minerefinehud.mine.MineCatalog;
import minerefinehud.mine.MineDetector;
import minerefinehud.mine.MiningBlocks;
import minerefinehud.mine.ResourcePickups;
import minerefinehud.turret.TurretCalculator;
import minerefinehud.turret.TurretItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dependency-free test runner for the version-independent core. */
public final class CoreTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        parserRecognisesRealMessages();
        parserIgnoresNoise();
        trackerLearnsInterval();
        trackerDiscardsIntervalAcrossBreak();
        trackerUsesMedian();
        trackerReportsOverdue();
        trackerPersistenceRoundTrip();
        armorSplitMatchesPublishedData();
        toolKeyVaries();
        catalogPrefersLongestName();
        formattingMatchesSiteStyle();
        hudModelRenders();
        catalogUnderstandsServerSpelling();
        detectorFallsBackToItems();
        hudSplitsIntoPanels();
        resourcePickupsNameTheMine();
        turretMergesMatchTheOriginal();
        bossWorldsFromSheetAndBroadcasts();
        bossPanelShowsOnlyThisWorld();
        reminderNeedsATrustedTimer();
        reminderRingsOncePerRespawn();
        bossRowsCanBeHidden();
        resourcesNameTheirWorld();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------- parser

    private static void parserRecognisesRealMessages() {
        // Exactly as seen in game, decorative glyphs and all.
        check("slain message",
                BossMessageParser.parse("☠ Angry Archaeologist has been slain! ☠"),
                BossMessageParser.Kind.SLAIN, "Angry Archaeologist");

        check("spawn message",
                BossMessageParser.parse("Angry Archaeologist has spawned!"),
                BossMessageParser.Kind.SPAWNED, "Angry Archaeologist");

        check("spawn with banner on same line",
                BossMessageParser.parse("BOSS ALERT Angry Archaeologist has spawned!"),
                BossMessageParser.Kind.SPAWNED, "Angry Archaeologist");

        check("colour codes stripped",
                BossMessageParser.parse("§c§lBrass Colossus §rhas been slain!"),
                BossMessageParser.Kind.SLAIN, "Brass Colossus");

        check("apostrophe survives",
                BossMessageParser.parse("Guardian 'o Toole has spawned!"),
                BossMessageParser.Kind.SPAWNED, "Guardian 'o Toole");

        check("accented letters survive",
                BossMessageParser.parse("⚔ Björn Gear has been slain! ⚔"),
                BossMessageParser.Kind.SLAIN, "Björn Gear");

        check("hyphen survives",
                BossMessageParser.parse("T-Gardener has spawned!"),
                BossMessageParser.Kind.SPAWNED, "T-Gardener");

        // A pair takes "have", and the ampersand is part of the name.
        check("pair spawn",
                BossMessageParser.parse("BOSS ALERT The King & Queen have spawned!"),
                BossMessageParser.Kind.SPAWNED, "The King & Queen");

        check("pair slain",
                BossMessageParser.parse("⚔ The King & Queen have been slain! ⚔"),
                BossMessageParser.Kind.SLAIN, "The King & Queen");
    }

    private static void parserIgnoresNoise() {
        assertTrue("damage leaderboard ignored",
                BossMessageParser.parse("#1 81hp_ - 3.25B Damage").isEmpty());
        assertTrue("border line ignored",
                BossMessageParser.parse("------------------------").isEmpty());
        assertTrue("unrelated line ignored",
                BossMessageParser.parse("EXP SHOVEL >> 1.04M EXP (65.05%)").isEmpty());
        assertTrue("empty ignored", BossMessageParser.parse("").isEmpty());
        assertTrue("null ignored", BossMessageParser.parse(null).isEmpty());
        assertTrue("banner alone ignored", BossMessageParser.parse("BOSS ALERT").isEmpty());
    }

    // ------------------------------------------------------------ tracker

    private static void trackerLearnsInterval() {
        BossTracker tracker = new BossTracker();
        long t = 1_000_000L;

        tracker.onSlain("Angry Archaeologist", t);
        tracker.onSpawned("Angry Archaeologist", t + 180_000L);

        BossTracker.BossView view = tracker.view("Angry Archaeologist", t + 180_000L).orElseThrow();
        assertEquals("interval learned from one cycle", 180_000L, view.intervalMs().orElse(-1L));
        assertEquals("status alive after spawn", BossTracker.Status.ALIVE, view.status());

        // Second kill: countdown should now be predicted without any configuration.
        long killedAgain = t + 300_000L;
        tracker.onSlain("Angry Archaeologist", killedAgain);
        BossTracker.BossView after = tracker.view("Angry Archaeologist", killedAgain + 30_000L)
                .orElseThrow();
        assertEquals("eta predicted", 150_000L, after.etaMs().orElse(-1L));
    }

    private static void trackerDiscardsIntervalAcrossBreak() {
        BossTracker tracker = new BossTracker();
        long t = 1_000_000L;

        tracker.onSlain("Warden", t);
        tracker.onContinuityBreak();              // relog or world change
        tracker.onSpawned("Warden", t + 900_000L); // gap spans an absence, so is not trustworthy

        BossTracker.BossView view = tracker.view("Warden", t + 900_000L).orElseThrow();
        assertTrue("no interval learned across a break", view.intervalMs().isEmpty());
    }

    private static void trackerUsesMedian() {
        BossTracker tracker = new BossTracker();
        long t = 0L;

        // Two clean 180s cycles and one badly delayed observation.
        for (long gap : new long[] { 180_000L, 180_000L, 900_000L }) {
            tracker.onSlain("Ravager", t);
            t += gap;
            tracker.onSpawned("Ravager", t);
            t += 10_000L;
        }

        BossTracker.BossView view = tracker.view("Ravager", t).orElseThrow();
        assertEquals("median resists the outlier", 180_000L, view.intervalMs().orElse(-1L));
        assertEquals("three samples recorded", 3, view.sampleCount());
    }

    private static void trackerReportsOverdue() {
        BossTracker tracker = new BossTracker();
        long t = 1_000_000L;

        tracker.onSlain("Watcher", t);
        tracker.onSpawned("Watcher", t + 180_000L);
        tracker.onSlain("Watcher", t + 200_000L);

        BossTracker.BossView view = tracker.view("Watcher", t + 500_000L).orElseThrow();
        assertTrue("overdue once the countdown passes", view.overdue());
    }

    private static void trackerPersistenceRoundTrip() {
        BossTracker original = new BossTracker();
        original.onSlain("Endor", 1_000L);
        original.onSpawned("Endor", 181_000L);

        BossTracker restored = new BossTracker();
        restored.importAll(original.export());

        BossTracker.BossView view = restored.view("Endor", 181_000L).orElseThrow();
        assertEquals("interval survives a restart", 180_000L, view.intervalMs().orElse(-1L));
        assertEquals("one boss restored", 1, restored.size());
    }

    // -------------------------------------------------------------- mines

    private static void armorSplitMatchesPublishedData() {
        // Real published figures for the Debris mine in Ruins.
        long armor = 100_820_000_000L;
        assertEquals("helmet", 20_160_000_000L, round(ArmorSplit.helmet(armor)));
        assertEquals("chestplate", 31_370_000_000L, round(ArmorSplit.chestplate(armor)));
        assertEquals("leggings", 26_890_000_000L, round(ArmorSplit.leggings(armor)));
        assertEquals("boots", 22_400_000_000L, round(ArmorSplit.boots(armor)));

        long sum = ArmorSplit.helmet(armor) + ArmorSplit.chestplate(armor)
                + ArmorSplit.leggings(armor) + ArmorSplit.boots(armor);
        assertEquals("pieces sum to the bundle", armor, sum);
    }

    /** The published figures are given to the nearest ten million. */
    private static long round(long value) {
        return Math.round(value / 10_000_000.0) * 10_000_000L;
    }

    private static void toolKeyVaries() {
        assertEquals("pickaxe mine", Optional.of("pickaxe"), mine("Voidstone", "End", "pickaxe").toolKey());
        assertEquals("shovel mine", Optional.of("shovel"), mine("Debris", "Ruins", "shovel").toolKey());
        assertEquals("axe mine", Optional.of("axe"), mine("Rafter", "Ruins", "axe").toolKey());
    }

    private static void catalogPrefersLongestName() {
        MineCatalog catalog = new MineCatalog(List.of(
                mine("Prismarine", "Ocean", "pickaxe"),
                mine("Dark Prismarine", "Ocean", "pickaxe"),
                mine("Pale Tree", "Arctic", "axe"),
                mine("Snow", "Arctic", "shovel"),
                mine("Voidstone", "End", "pickaxe")));

        assertEquals("longest match wins", "Dark Prismarine",
                catalog.detectFrom("Mine: Dark Prismarine  |  x1.5").orElseThrow().name());

        assertEquals("found inside a sidebar", "Voidstone",
                catalog.detectFrom(List.of("MINEREFINE", "World: End", "Mine: Voidstone", "Bal: 4.2b"))
                        .orElseThrow().name());

        assertTrue("no false positive on a longer word",
                catalog.detectFrom("Snowy Tundra biome").isEmpty());

        assertTrue("nothing found in unrelated text",
                catalog.detectFrom("Welcome back to the server!").isEmpty());
    }

    private static void formattingMatchesSiteStyle() {
        assertEquals("billions", "3.25b", Formatting.blocks(3_250_000_000L));
        assertEquals("millions", "1.04m", Formatting.blocks(1_040_000L));
        assertEquals("trailing zeroes trimmed", "100.82b", Formatting.blocks(100_820_000_000L));
        assertEquals("small numbers", "12,345", Formatting.blocks(12_345L));
        assertEquals("mm:ss", "2:47", Formatting.duration(167_000L));
        assertEquals("hours", "1:03:20", Formatting.duration(3_800_000L));
        assertEquals("negative clamped", "0:00", Formatting.duration(-5_000L));
        // 200m blocks per credit is the site default.
        assertEquals("credits", "1c", Formatting.credits(200_000_000L, 200.0));
    }

    private static void hudModelRenders() {
        BossTracker tracker = new BossTracker();
        tracker.onSlain("Angry Archaeologist", 0L);
        tracker.onSpawned("Angry Archaeologist", 180_000L);
        tracker.onSlain("Angry Archaeologist", 200_000L);

        List<HudModel.Line> lines = HudModel.build(
                Optional.of(mine("Debris", "Ruins", "shovel")),
                tracker.views(260_000L),
                HudModel.Options.defaults(),
                260_000L);

        String rendered = String.join("\n", lines.stream().map(HudModel.Line::text).toList());
        assertTrue("mine name shown", rendered.contains("Debris"));
        assertTrue("shovel not pickaxe", rendered.contains("Shovel:"));
        assertTrue("no pickaxe label", !rendered.contains("Pickaxe:"));
        assertTrue("boss countdown shown", rendered.contains("Angry Archaeologist: 2:00"));
    }

    // ------------------------------------------------------- boss worlds

    private static MineCatalog bossCatalog() {
        Map<String, Long> fragments = new LinkedHashMap<>();
        fragments.put("sword", 50L);
        return new MineCatalog(List.of(
                mine("Relic", "Ruins", "pickaxe"),
                mine("Oak", "Overworld", "axe"),
                new Mine("boss-ruins-angry-archaeologist", "boss", "Ruins", "Angry Archaeologist", fragments, null),
                new Mine("boss-ocean-guardian", "boss", "Ocean", "Guardian o' Toole", fragments, null)));
    }

    private static void bossWorldsFromSheetAndBroadcasts() {
        MineCatalog catalog = bossCatalog();
        BossWorlds worlds = new BossWorlds();

        assertEquals("spreadsheet places a known boss", Optional.of("Ruins"),
                worlds.worldOf("Angry Archaeologist", catalog));
        assertEquals("chat spelling matches the sheet's", Optional.of("Ocean"),
                worlds.worldOf("Guardian 'o Toole", catalog));

        assertTrue("a sheet boss is never relearned",
                !worlds.learn("Angry Archaeologist", Optional.of("Overworld"), catalog));
        assertTrue("unknown world teaches nothing", !worlds.learn("Sky Tyrant", Optional.empty(), catalog));
        assertTrue("a new boss is learned from where it was heard",
                worlds.learn("Sky Tyrant", Optional.of("SKYBOUND"), catalog));
        assertEquals("and remembered", Optional.of("SKYBOUND"), worlds.worldOf("sky  tyrant", catalog));
        assertTrue("hearing it again in the same world is no change",
                !worlds.learn("Sky Tyrant", Optional.of("skybound"), catalog));

        BossWorlds restored = new BossWorlds();
        restored.importAll(worlds.export());
        assertEquals("survives a restart", Optional.of("SKYBOUND"), restored.worldOf("Sky Tyrant", catalog));

        assertEquals("world from the mine", Optional.of("Ruins"),
                BossWorlds.currentWorld(catalog.byName("Relic"), Optional.of("RUINBOUND")));
        assertEquals("world tag for a mine the data lacks", Optional.of("SKYBOUND"),
                BossWorlds.currentWorld(Optional.of(new Mine(null, "mine", "", "Cloud", Map.of(), null)),
                        Optional.of("SKYBOUND")));
        assertTrue("nothing mined yet, no world",
                BossWorlds.currentWorld(Optional.empty(), Optional.empty()).isEmpty());
    }

    private static void bossPanelShowsOnlyThisWorld() {
        MineCatalog catalog = bossCatalog();
        BossWorlds worlds = new BossWorlds();
        BossTracker tracker = new BossTracker();
        tracker.onSlain("Angry Archaeologist", 0L);
        tracker.onSlain("Guardian 'o Toole", 0L);
        tracker.onSlain("Brand New Boss", 0L);   // world not learned yet
        List<BossTracker.BossView> all = tracker.views(1_000L);

        List<String> inRuins = worlds.filter(all, Optional.of("Ruins"), catalog, false).stream()
                .map(BossTracker.BossView::displayName).toList();
        assertTrue("this world's boss shown", inRuins.contains("Angry Archaeologist"));
        assertTrue("another world's boss hidden", !inRuins.contains("Guardian 'o Toole"));
        assertTrue("a boss of unknown world still shown, learning", inRuins.contains("Brand New Boss"));

        assertEquals("world not known yet shows everything", 3,
                worlds.filter(all, Optional.empty(), catalog, false).size());
        assertEquals("show-all setting shows everything", 3,
                worlds.filter(all, Optional.of("Ruins"), catalog, true).size());
        assertEquals("world names compare loosely", 2,
                worlds.filter(all, Optional.of("ruins"), catalog, false).size());
    }

    /** Two clean 180s cycles measured, then killed at {@code killedAt}. */
    private static BossTracker measured(String boss, long killedAt) {
        BossTracker t = new BossTracker();
        t.onSlain(boss, 0L);
        t.onSpawned(boss, 180_000L);
        t.onSlain(boss, 200_000L);
        t.onSpawned(boss, 380_000L);
        t.onSlain(boss, killedAt);
        return t;
    }

    private static void reminderNeedsATrustedTimer() {
        BossTracker once = new BossTracker();
        once.onSlain("Warden", 0L);
        once.onSpawned("Warden", 180_000L);
        once.onSlain("Warden", 200_000L);
        assertTrue("one sample is a guess, no reminder",
                BossAlerts.active(once.views(375_000L), 10_000L).isEmpty());

        BossTracker twice = measured("Warden", 400_000L);
        assertTrue("too early, no reminder", BossAlerts.active(twice.views(560_000L), 10_000L).isEmpty());
        assertEquals("inside the last ten seconds", 1, BossAlerts.active(twice.views(571_000L), 10_000L).size());
        assertTrue("overdue is not about to respawn",
                BossAlerts.active(twice.views(590_000L), 10_000L).isEmpty());

        twice.onSpawned("Warden", 580_000L);
        assertTrue("alive bosses are not about to respawn",
                BossAlerts.active(twice.views(581_000L), 10_000L).isEmpty());

        assertEquals("panel text", "Warden is about to respawn!",
                HudModel.alertPanel(List.of("Warden")).get(0).text());
        assertTrue("nothing due, nothing drawn", HudModel.alertPanel(List.of()).isEmpty());
    }

    private static void reminderRingsOncePerRespawn() {
        BossAlerts alerts = new BossAlerts();
        BossTracker t = measured("Warden", 400_000L);
        assertEquals("rings on entering the window", 1, alerts.newlyDue(t.views(571_000L), 571_000L, 10_000L).size());
        assertTrue("silent for the rest of the window", alerts.newlyDue(t.views(575_000L), 575_000L, 10_000L).isEmpty());

        t.onSpawned("Warden", 580_000L);
        t.onSlain("Warden", 600_000L);
        assertEquals("rings again for the next respawn", 1,
                alerts.newlyDue(t.views(771_000L), 771_000L, 10_000L).size());
    }

    private static void bossRowsCanBeHidden() {
        BossTracker tracker = new BossTracker();
        tracker.onSpawned("Warden", 0L);
        tracker.onSlain("Ravager", 0L);
        var base = HudModel.Options.defaults();
        var noLearning = new HudModel.Options(true, false, false, 200.0, true, 5,
                HudModel.MineLines.all(), new HudModel.BossLines(true, false), HudModel.ProgressLines.all());
        List<String> rows = HudModel.bossPanel(tracker.views(1_000L), noLearning).stream()
                .map(HudModel.Line::text).toList();
        assertTrue("alive row kept", rows.stream().anyMatch(r -> r.startsWith("Warden: UP")));
        assertTrue("learning row hidden", rows.stream().noneMatch(r -> r.contains("learning")));

        var nothing = new HudModel.Options(true, false, false, 200.0, true, 5,
                HudModel.MineLines.all(), new HudModel.BossLines(false, false), HudModel.ProgressLines.all());
        assertTrue("every row off draws no lone header",
                HudModel.bossPanel(tracker.views(1_000L), nothing).isEmpty());
        assertEquals("defaults show both", 3, HudModel.bossPanel(tracker.views(1_000L), base).size());
    }

    private static void resourcesNameTheirWorld() {
        MineCatalog catalog = bossCatalog();
        ResourcePickups pickups = new ResourcePickups();
        List<String> lore = List.of("SKYBOUND");
        pickups.observe(List.of(new ResourcePickups.Item("Cloud", lore, 1)), catalog);
        pickups.observe(List.of(new ResourcePickups.Item("Cloud", lore, 5)), catalog);
        assertEquals("tag of what was just mined", Optional.of("SKYBOUND"), pickups.lastWorldTag());
        pickups.reset();
        assertTrue("forgotten on relog", pickups.lastWorldTag().isEmpty());
        assertTrue("a gain alongside a mined block is mining",
                ResourcePickups.fromMining(10_000L, 9_000L, 3_000L));
        assertTrue("a gain after mining stopped is an inventory shuffle, not mining",
                !ResourcePickups.fromMining(20_000L, 9_000L, 3_000L));
        assertTrue("nothing mined since joining, nothing counts",
                !ResourcePickups.fromMining(20_000L, 0L, 3_000L));
        assertTrue("gear lore is not a world tag",
                ResourcePickups.worldTag(List.of("RUINBOUND SHOVEL")).isEmpty());
    }

    // ------------------------------------------------------------ turrets

    /** Expected values computed by running the original turret_calculator V2.py formula. */
    private static void turretMergesMatchTheOriginal() {
        var r = TurretCalculator.combine(300, 500, 0);
        assertEquals("300 + 500", "689", TurretCalculator.size(r));
        assertTrue("exact value", Math.abs(r.size() - 688.8888888888889) < 1e-9);
        assertEquals("order does not matter", r, TurretCalculator.combine(500, 300, 0));
        assertEquals("detail line as in the original",
                "63.0% of smaller turret added  •  Merge #1  •  Max 1,900", TurretCalculator.details(r));

        assertEquals("small turret fully added", "1,100", TurretCalculator.size(TurretCalculator.combine(100, 1000, 0)));
        assertEquals("equal turrets: half added", "1,500", TurretCalculator.size(TurretCalculator.combine(1000, 1000, 0)));
        assertEquals("size 0 adds nothing", "800", TurretCalculator.size(TurretCalculator.combine(0, 800, 0)));

        var capped = TurretCalculator.combine(1200, 900, 3);
        assertEquals("three merges cap at 1,600", "1,600", TurretCalculator.size(capped));
        assertEquals("capped detail", "50.6% of smaller turret added  •  Merge #4  •  Max 1,600  (capped)",
                TurretCalculator.details(capped));
        assertEquals("five merges", "1,400", TurretCalculator.size(TurretCalculator.combine(2000, 1500, 5)));
        assertEquals("cap reaches zero at merge 20", "0", TurretCalculator.size(TurretCalculator.combine(50, 60, 19)));
        assertEquals("and stays there", 0.0, TurretCalculator.combine(10, 10, 20).cap());

        // Reverse: smallest whole merger, checked by brute force against the original formula.
        long[][] cases = {
                { 1400, 1900, 0, 1000 },   // the example: a 1000 merger adds 500 at 50%
                { 300, 1500, 0, 1312 },    // needs a bigger turret than yours
                { 281, 312, 0, 31 },       // the Sponge Turret in the reforger screenshot
                { 1000, 1200, 0, 325 },
                { 500, 900, 2, 623 },
                { 1800, 1850, 0, 50 },
                { 100, 250, 0, 150 } };
        for (long[] c : cases) {
            var n = TurretCalculator.needed(c[0], c[1], (int) c[2]);
            assertEquals(c[0] + " to " + c[1] + " needs", c[3], n.merger());
            assertTrue(c[0] + " to " + c[1] + " really gets there",
                    n.result().orElseThrow().size() >= c[1] - 1e-9);
        }
        assertEquals("1400 to 1900 is a smaller merger", TurretCalculator.Need.SMALLER_MERGER,
                TurretCalculator.needed(1400, 1900, 0).need());
        assertEquals("300 to 1500 is a bigger one", TurretCalculator.Need.LARGER_MERGER,
                TurretCalculator.needed(300, 1500, 0).need());
        assertEquals("1900 after one merge is over the cap", TurretCalculator.Need.OVER_CAP,
                TurretCalculator.needed(1400, 1900, 1).need());
        assertEquals("over-cap says the most it can reach", "Above the cap: this merge can reach at most 1,800",
                TurretCalculator.explain(TurretCalculator.needed(1400, 1900, 1)));
        assertEquals("nothing to do", TurretCalculator.Need.ALREADY_THERE,
                TurretCalculator.needed(1400, 1300, 0).need());
        assertEquals("explanation for the example", "50.0% of it is added  •  Merge #1  •  Max 1,900",
                TurretCalculator.explain(TurretCalculator.needed(1400, 1900, 0)));

        // Reading turrets off their items. The reforger tooltip, transcribed from the screenshot.
        List<String> reforger = List.of(
                "| Level: 0", "| EXP: 0/50",
                "| Size: 281 → 312 (+31)",
                "| Size Cap: 2000 → 1900 (-100)",
                "", "❤ Health: +1.87", "♣ Luck: +0.374", "✤ Fortune: +250", "",
                "ʙʟᴏᴄᴋ ᴛᴜʀʀᴇᴛ", "- ᴇᴠᴇʀʏ 20 ʜɪᴛꜱ:",
                "⚒ 0 → 1x ᴍᴇʀɢᴇᴅ • ꜱɪᴢᴇ ᴄᴀᴘ: 2000 → 1900",
                "ʟᴇɢᴇɴᴅᴀʀʏ");
        var sponge = TurretItem.parse("Sponge Turret", reforger).orElse(null);
        assertTrue("a turret is recognised", sponge != null);
        if (sponge != null) {
            assertEquals("its size now, not after the merge", 281.0, sponge.size());
            assertEquals("no merges yet", 0, sponge.merges());
            assertEquals("button label", "Sponge 281", sponge.label());
        }
        var twice = TurretItem.parse("§6Ceramic Turret", List.of("| Size: 1,400", "| Size Cap: 1800",
                "⚒ 2x ᴍᴇʀɢᴇᴅ • ꜱɪᴢᴇ ᴄᴀᴘ: 1800")).orElseThrow();
        assertEquals("size with a thousands separator", 1400.0, twice.size());
        assertEquals("two merges, from the cap", 2, twice.merges());
        assertEquals("merges from the MERGED count when there is no cap line", 3,
                TurretItem.parse("Rust Turret", List.of("Size: 900", "⚒ 3x ᴍᴇʀɢᴇᴅ")).orElseThrow().merges());
        assertTrue("not a turret", TurretItem.parse("[Debris Shovel] [VI]", List.of("Size: 5")).isEmpty());
        assertTrue("a turret without a size is not usable", TurretItem.parse("Sponge Turret", List.of("Level: 2")).isEmpty());

        boolean refused = false;
        try {
            TurretCalculator.combine(-1, 5, 0);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        assertTrue("negative sizes refused, as in the original", refused);
    }

    // ------------------------------------------------------------- helpers

    private static void catalogUnderstandsServerSpelling() {
        MineCatalog catalog = new MineCatalog(List.of(
                mine("Rusty", "Trials", "pickaxe"),
                mine("Pine Tree", "Arctic", "axe"),
                mine("Pale Tree", "Arctic", "axe"),
                mine("Björn Gear", "Arctic", "pickaxe"),
                mine("Gear", "Trials", "pickaxe")));

        // As seen in game: the spreadsheet says Rusty, the gear says "[Rust Pickaxe]".
        assertEquals("gear spelling resolves", "Rusty",
                catalog.detectFrom("[Rust Pickaxe] [II]").orElseThrow().name());
        assertEquals("spreadsheet spelling still works", "Rusty",
                catalog.detectFrom("Rusty").orElseThrow().name());
        assertEquals("Pine is Pine Tree", "Pine Tree",
                catalog.detectFrom("[Pine Sword] [III]").orElseThrow().name());
        assertEquals("Pale Wood is Pale Tree", "Pale Tree",
                catalog.detectFrom("[Pale Wood Helmet] [I]").orElseThrow().name());
        assertEquals("prices are looked up under the gear spelling", "Rust", catalog.serverName("Rusty"));
        assertEquals("for every renamed mine", "Pale Wood", catalog.serverName("Pale Tree"));
        assertEquals("accents ignored", "Björn Gear",
                catalog.detectFrom("Bjorn Gear").orElseThrow().name());
    }

    private static void detectorFallsBackToItems() {
        MineCatalog catalog = new MineCatalog(List.of(
                mine("Debris", "Ruins", "shovel"),
                mine("Marrow", "Ruins", "pickaxe")));

        var fromHeld = MineDetector.detect(catalog, Optional.empty(),
                Optional.of("Debris"), Optional.of("Marrow"));
        assertEquals("held item used when nothing is being mined", "Debris",
                fromHeld.mine().orElseThrow().name());
        assertEquals("held item source", MineDetector.Source.HELD_ITEM, fromHeld.source());

        var fromShop = MineDetector.detect(catalog, Optional.empty(), Optional.empty(), Optional.of("Marrow"));
        assertEquals("shop used last", MineDetector.Source.SHOP, fromShop.source());

        var unpriced = MineDetector.detect(catalog, Optional.empty(), Optional.of("Nether Quartz"),
                Optional.empty());
        assertEquals("unpriced mine still named", "Nether Quartz",
                unpriced.mine().orElseThrow().name());

        List<HudModel.Line> lines = HudModel.minePanel(unpriced.mine(), Optional.empty(),
                HudModel.Options.defaults());
        String rendered = String.join("\n", lines.stream().map(HudModel.Line::text).toList());
        assertTrue("unpriced mine says so", rendered.contains("no prices yet, open this mine's shop"));
        assertTrue("unpriced mine shows no zero costs", !rendered.contains("Sword"));

        var mined = MineDetector.detect(catalog, Optional.of("Marrow"),
                Optional.of("Debris"), Optional.of("Debris"));
        assertEquals("the block being mined beats the held item and the shop", "Marrow",
                mined.mine().orElseThrow().name());
        assertEquals("mining source", MineDetector.Source.MINING, mined.source());

        assertEquals("shop mine is the majority", "Marrow",
                MineDetector.mostCommon(List.of("Marrow", "Debris", "Marrow", "Marrow")).orElseThrow());
        assertTrue("empty shop has no mine", MineDetector.mostCommon(List.of()).isEmpty());

        assertTrue("nothing anywhere is not found",
                !MineDetector.detect(catalog, Optional.empty(), Optional.empty(), Optional.empty()).found());

        // At Marrow, mining bone blocks the mod has never placed, holding a Suspicious Sand tool.
        MineCatalog ruins = new MineCatalog(List.of(
                mine("Rubble", "Ruins", "pickaxe"),
                mine("Suspicious Sand", "Ruins", "shovel"),
                mine("Marrow", "Ruins", "pickaxe")));
        var ruledOut = java.util.Set.of("Rubble", "Suspicious Sand");
        var atMarrow = MineDetector.detect(ruins, Optional.empty(), Optional.of("Suspicious Sand"),
                Optional.of("Marrow"), ruledOut);
        assertEquals("a mine whose block is known is not the unknown one", "Marrow",
                atMarrow.mine().orElseThrow().name());
        assertTrue("nothing left means unknown, not a stale guess",
                !MineDetector.detect(ruins, Optional.empty(), Optional.of("Suspicious Sand"),
                        Optional.of("Rubble"), ruledOut).found());

        MiningBlocks blocks = new MiningBlocks();
        blocks.teach("block/suspicious_sand", "Suspicious Sand");
        Map<String, Long> balances = new LinkedHashMap<>();
        balances.put("Suspicious Sand", 5_690_000_000L);   // has a block: cannot be this one
        balances.put("Marrow", 5_700_000_000L);            // read from the Marrow shop earlier
        balances.put("Rubble", 919_000_000L);
        assertTrue("an unknown block just above one known balance is learned",
                blocks.inferFromBalances("block/bone_block", 5_710_000_000L, balances));
        assertEquals("as that mine", "Marrow",
                blocks.lookup("block/bone_block", ruins).orElseThrow().mine());
        Map<String, Long> close = new LinkedHashMap<>();
        close.put("Marrow", 5_700_000_000L);
        close.put("Ceramic", 5_650_000_000L);
        assertTrue("two that fit teach nothing",
                !new MiningBlocks().inferFromBalances("block/bone_block", 5_710_000_000L, close));
        assertTrue("far above any balance teaches nothing",
                !new MiningBlocks().inferFromBalances("block/bone_block", 9_000_000_000L, Map.of("Marrow", 5_700_000_000L)));
        assertTrue("well below a balance is not that resource being mined",
                !new MiningBlocks().inferFromBalances("block/bone_block", 5_000_000_000L, Map.of("Marrow", 5_700_000_000L)));

        // Arrived at Debris from Sniffer Egg, wrong tool, opened the Debris shop: as seen in game.
        assertEquals("a shop opened after the last mined block wins", Optional.empty(),
                MineDetector.stillMining(Optional.of("Sniffer Egg"), 100_000L, 160_000L));
        assertEquals("mining again takes over", Optional.of("Debris"),
                MineDetector.stillMining(Optional.of("Debris"), 200_000L, 160_000L));
        var atDebris = MineDetector.detect(new MineCatalog(List.of(
                        mine("Sniffer Egg", "Ruins", "pickaxe"), mine("Debris", "Ruins", "shovel"))),
                MineDetector.stillMining(Optional.of("Sniffer Egg"), 100_000L, 160_000L),
                Optional.empty(), Optional.of("Debris"));
        assertEquals("so the panel shows the new mine", "Debris", atDebris.mine().orElseThrow().name());

        // Leaving Scaffold (an axe mine, wood arrives as items) for Sniffer Egg.
        MiningBlocks learnedBlocks = new MiningBlocks();
        learnedBlocks.teach("block/green_glazed_terracotta", "Sniffer Egg");
        assertTrue("a leftover log pickup does not relabel a known block",
                !learnedBlocks.teach("block/green_glazed_terracotta", "Scaffold"));
        assertEquals("still Sniffer Egg", "Sniffer Egg",
                learnedBlocks.learned().get("block/green_glazed_terracotta"));
        assertTrue("a pickup while mining a known block does not move the panel",
                !ResourcePickups.namesTheMine(10_000L, 9_500L, 3_000L, true));
        assertTrue("a pickup while mining an unknown block does",
                ResourcePickups.namesTheMine(10_000L, 9_500L, 3_000L, false));

        // Boss gear is sold under a short name; its spreadsheet totals are still found.
        Map<String, Long> fragments = new LinkedHashMap<>();
        fragments.put("sword", 40L);
        Map<String, Long> pieces = new LinkedHashMap<>();
        pieces.put("chestplate", 40L);
        MineCatalog bosses = new MineCatalog(List.of(
                mine("Chamber", "Trials", "pickaxe"),
                new Mine("boss-trials-brass-colossus", "boss", "Trials", "Brass Colossus", fragments, pieces),
                new Mine("boss-ruins-angry-archaeologist", "boss", "Ruins", "Angry Archaeologist", fragments, pieces),
                new Mine("boss-ocean-guardian", "boss", "Ocean", "Guardian o' Toole", fragments, pieces)));
        assertEquals("boss by its full name", java.util.OptionalLong.of(40L), bosses.pieceTotal("Brass Colossus", "chestplate"));
        assertEquals("boss by the end of its name", java.util.OptionalLong.of(40L), bosses.pieceTotal("Archaeologist", "sword"));
        assertEquals("boss by the start of its name", java.util.OptionalLong.of(40L), bosses.pieceTotal("Guardian o'", "chestplate"));
        assertTrue("a boss is never detected as a mine",
                !MineDetector.detect(bosses, Optional.empty(), Optional.of("Brass Colossus"), Optional.empty())
                        .mine().map(m -> m.isBoss()).orElse(false));
    }

    private static void hudSplitsIntoPanels() {
        BossTracker tracker = new BossTracker();
        tracker.onSlain("Angry Archaeologist", 0L);
        Optional<Mine> debris = Optional.of(mine("Debris", "Ruins", "shovel"));
        var options = HudModel.Options.defaults();

        List<HudModel.Line> minePart = HudModel.minePanel(debris, Optional.empty(), options);
        List<HudModel.Line> bossPart = HudModel.bossPanel(tracker.views(1_000L), options);
        List<HudModel.Line> whole = HudModel.build(debris, tracker.views(1_000L), options, 1_000L);

        assertTrue("mine panel has no bosses",
                minePart.stream().noneMatch(l -> l.text().contains("Archaeologist")));
        assertTrue("boss panel has no mine", bossPart.stream().noneMatch(l -> l.text().contains("Debris")));
        assertEquals("combined is both plus one separator",
                minePart.size() + bossPart.size() + 1, whole.size());
        assertTrue("no boss panel before any boss is seen",
                HudModel.bossPanel(List.of(), options).isEmpty());
    }

    private static void resourcePickupsNameTheMine() {
        MineCatalog catalog = new MineCatalog(List.of(
                mine("Zircon", "Ruins", "pickaxe"),
                mine("Rubble", "Ruins", "pickaxe"),
                mine("Pine Tree", "Arctic", "axe"),
                mine("Debris", "Ruins", "shovel")));
        List<String> tag = List.of("RUINBOUND");

        assertEquals("plain resource", "Zircon",
                ResourcePickups.resourceName("Zircon", tag, catalog).orElseThrow());
        assertEquals("compressed", "Zircon",
                ResourcePickups.resourceName("Compressed Zircon", tag, catalog).orElseThrow());
        assertEquals("very compressed", "Zircon",
                ResourcePickups.resourceName("Very Compressed Zircon", tag, catalog).orElseThrow());
        assertEquals("server spelling kept", "Rubble",
                ResourcePickups.resourceName("Rubble", List.of(), catalog).orElseThrow());
        assertEquals("the resource's own name, which is the shop's currency", "Pine Tree",
                ResourcePickups.resourceName("Compressed Pine Tree", List.of(), catalog).orElseThrow());
        assertEquals("unlisted mine recognised by its world tag", "Netherrack",
                ResourcePickups.resourceName("Netherrack", List.of("NETHERBOUND"), catalog).orElseThrow());

        assertTrue("gear named after the mine is not the resource",
                ResourcePickups.resourceName("[Zircon Pickaxe] [II]", List.of("RUINBOUND PICKAXE"), catalog)
                        .isEmpty());
        assertTrue("special tool is not a resource",
                ResourcePickups.resourceName("Khan's Spoil 3[item/book@items]", List.of("5✪ 5✪"), catalog)
                        .isEmpty());
        assertTrue("an unknown name without a world tag is not a resource",
                ResourcePickups.resourceName("Cobblestone", List.of(), catalog).isEmpty());

        ResourcePickups pickups = new ResourcePickups();
        List<ResourcePickups.Item> start = List.of(
                new ResourcePickups.Item("Zircon", tag, 10),
                new ResourcePickups.Item("Debris", tag, 40));
        assertTrue("first scan only records", pickups.observe(start, catalog).isEmpty());

        List<ResourcePickups.Item> mined = List.of(
                new ResourcePickups.Item("Zircon", tag, 13),
                new ResourcePickups.Item("Debris", tag, 40));
        assertEquals("the resource that went up is the mine", "Zircon",
                pickups.observe(mined, catalog).orElseThrow());

        assertTrue("owning without gaining says nothing", pickups.observe(mined, catalog).isEmpty());

        List<ResourcePickups.Item> compressed = List.of(
                new ResourcePickups.Item("Compressed Zircon", tag, 1),
                new ResourcePickups.Item("Debris", tag, 40));
        assertTrue("compressing is not mining", pickups.observe(compressed, catalog).isEmpty());

        List<ResourcePickups.Item> newMine = List.of(
                new ResourcePickups.Item("Compressed Zircon", tag, 1),
                new ResourcePickups.Item("Debris", tag, 40),
                new ResourcePickups.Item("Rubble", tag, 5));
        assertEquals("a resource appearing for the first time counts", "Rubble",
                pickups.observe(newMine, catalog).orElseThrow());

        pickups.reset();
        assertTrue("after a relog the first scan only records again",
                pickups.observe(start, catalog).isEmpty());
    }

    private static Mine mine(String name, String world, String toolKey) {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 30_530_000_000L);
        items.put(toolKey, 28_050_000_000L);
        items.put("armor", 100_820_000_000L);
        items.put("charm", 15_700_000_000L);
        return new Mine("mine-" + name.toLowerCase().replace(' ', '-'), "mine", world, name,
                items, null);
    }

    private static void check(String what, Optional<BossMessageParser.Event> actual,
                              BossMessageParser.Kind kind, String name) {
        if (actual.isEmpty()) {
            fail(what, kind + " " + name, "nothing matched");
            return;
        }
        BossMessageParser.Event e = actual.get();
        if (e.kind() == kind && e.bossName().equals(name)) {
            pass(what);
        } else {
            fail(what, kind + " " + name, e.kind() + " " + e.bossName());
        }
    }

    private static void assertEquals(String what, Object expected, Object actual) {
        if (expected.equals(actual)) {
            pass(what);
        } else {
            fail(what, String.valueOf(expected), String.valueOf(actual));
        }
    }

    private static void assertTrue(String what, boolean condition) {
        if (condition) {
            pass(what);
        } else {
            fail(what, "true", "false");
        }
    }

    private static void pass(String what) {
        passed++;
        System.out.println("  ok   " + what);
    }

    private static void fail(String what, String expected, String actual) {
        failed++;
        System.out.println("  FAIL " + what + "  expected <" + expected + "> got <" + actual + ">");
    }
}
