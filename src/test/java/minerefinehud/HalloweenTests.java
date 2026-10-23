package minerefinehud;

import minerefinehud.boss.BossAlerts;
import minerefinehud.boss.BossIndex;
import minerefinehud.boss.BossTracker;
import minerefinehud.halloween.Abilities;
import minerefinehud.halloween.Buildings;
import minerefinehud.halloween.HalloweenHud;
import minerefinehud.halloween.HalloweenText;
import minerefinehud.halloween.PumpkinSet;
import minerefinehud.halloween.Rates;
import minerefinehud.halloween.RecurringEvent;
import minerefinehud.halloween.Resurrection;
import minerefinehud.hud.HudModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** The Halloween event, the Reforger and the boss index, from text seen in game. */
public final class HalloweenTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        sidebarIsRead();
        actionBarCrop();
        chatLines();
        pumpkinShopIsRead();
        pumpkinBars();
        ratesCountOnlyGains();
        abilityTimers();
        reforgerAndCaravan();
        bossIndexIsRead();
        bossIndexIntervalsAndWatchList();
        bossIndexFixesTimers();
        panelsRender();
        testerReports();
        buildingsAreRead();
        buildingUpgrades();
        buildingUpgradeSeenInTheMenu();
        buildingPanel();
        upgradesMenuAndChat();
        spookyLookPaints();
        resurrectionIsRead();
        resurrectionNeverGhosts();
        resurrectionPanel();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void sidebarIsRead() {
        // The sidebar as the screenshot shows it, rows unsorted as the scoreboard hands them over.
        List<String> lines = List.of("MINEREFINE", "Resurrection 1", "│ [item/carrot@items] Carrots 45,395",
                "🎃 HALLOWEEN", "│ Beetroots 86,824", "│ Nether Wart 13,535", "│ ⋈ Candy 21.5‌",
                "Next: Nether Wart Hoe II", "13,535/17,500 Nether Wart", "MINEREFINE.MINEHUT.GG");
        HalloweenText.Sidebar s = HalloweenText.sidebar(lines);
        check("halloween sidebar recognised", s.halloween());
        eq("carrots", 45395.0, s.balances().get("Carrots"));
        eq("beetroots", 86824.0, s.balances().get("Beetroots"));
        eq("nether wart, not the hoe's 17,500", 13535.0, s.balances().get("Nether Wart"));
        eq("candy keeps its decimal", 21.5, s.balances().get("Candy"));
        check("resurrection is not a balance", !s.balances().containsKey("Resurrection"));
        check("the title says MineRefine", HalloweenText.isMineRefineTitle(lines.get(0)));

        HalloweenText.Sidebar mining = HalloweenText.sidebar(List.of("MINEREFINE", "Balance: 1.2B", "Rank: VIP"));
        check("a mining sidebar is not Halloween", !mining.halloween());
        check("and has no balances", mining.balances().isEmpty());

        eq("2.5M", 2_500_000.0, HalloweenText.number("2.5M").orElse(-1));
        eq("250k", 250_000.0, HalloweenText.number("250k").orElse(-1));
    }

    private static void actionBarCrop() {
        var r = HalloweenText.actionBar("15/15 ❤   4.94 ☽   [item/beetroot@items] 34,347");
        check("crop read from the action bar", r.isPresent());
        r.ifPresent(c -> {
            eq("crop", "Beetroots", c.crop());
            eq("the total after the icon, not the hex before it", 34347.0, c.amount());
        });
        var after = HalloweenText.actionBar("15/15 ❤  34,347[item/nether_wart@items]");
        eq("number before the icon", 34347.0, after.map(HalloweenText.CropReading::amount).orElse(-1.0));
        check("no crop icon, nothing", HalloweenText.actionBar("15/15 ❤   4.94 ☽").isEmpty());
    }

    private static void chatLines() {
        eq("pest candy, two spaces where the icon was", 1.0, HalloweenText.pestCandy("+1  Candy from the Pest!").orElse(-1));
        eq("pest candy with decimals", 1.1, HalloweenText.pestCandy("+1.1  Candy from the Pest!").orElse(-1));
        eq("pest charm", 1, HalloweenText.pestCharms("+1 Halloween Charm from the Pest! (right-click it to redeem)"));
        eq("a candy line is not a charm", 0, HalloweenText.pestCharms("+1.1  Candy from the Pest!"));

        var recharging = HalloweenText.recharge("Will-o'-Wisps is recharging (2.31s)");
        eq("recharging name", "Will-o'-Wisps", recharging.map(HalloweenText.Recharge::ability).orElse(""));
        eq("recharging seconds", 2.31, recharging.get().secondsLeft().orElse(-1));
        var recharged = HalloweenText.recharge("Will-o'-Wisps has recharged! [DROP]");
        check("recharged has no seconds", recharged.isPresent() && recharged.get().secondsLeft().isEmpty());
        eq("refined", 1, HalloweenText.refinedTo("Your hoe has been refined to [I]."));
        eq("refined III", 3, HalloweenText.refinedTo("Your hoe has been refined to [III]."));
    }

    /** The Pumpkin Helmet exactly as saved in unrecognised-shop-items.txt. */
    private static final List<String> HELMET_LORE = List.of("",
            " Event Health: +3‌❤", " Protection: +1,500‌⛨", " Fortune: +200‌☘", "",
            "[ 2 Empty Enchantment Slots ]", "", "+ 20% ᴏᴠᴇʀᴀʟʟ 🌾 ᴄʀᴏᴘ ᴍᴜʟᴛɪᴘʟɪᴇʀ",
            "+ 3% ᴏᴠᴇʀᴀʟʟ 🍬 ᴄᴀɴᴅʏ ᴍᴜʟᴛɪᴘʟɪᴇʀ", "", "ʜᴀʟʟᴏᴡᴇᴇɴ ʜᴇʟᴍᴇᴛ", "", "                        ",
            "Cost:", "➼ 250 🍬 Candy ❌", "➼ [item/carrot@items] 2.5M Carrots ❌",
            "➼ [item/beetroot@items] 2.5M Beetroots ❌", "➼ [item/nether_wart@items] 250k Nether Wart ❌",
            "", "1/1 Lifetime Stock", "", "You cannot afford this.");

    private static void pumpkinShopIsRead() {
        check("event helmet recognised", PumpkinSet.isEventItem("Pumpkin Helmet", HELMET_LORE));
        check("the Pumpkin mine's helmet is not the event's", !PumpkinSet.isEventItem("[Pumpkin Helmet] [IV]",
                List.of("Health: +5", "Cost:", "➼ Pumpkin x2.33B (0) ✗")));
        Map<String, Double> cost = PumpkinSet.costs(HELMET_LORE);
        eq("candy", 250.0, cost.get("Candy"));
        eq("carrots", 2_500_000.0, cost.get("Carrots"));
        eq("beetroots", 2_500_000.0, cost.get("Beetroots"));
        eq("nether wart", 250_000.0, cost.get("Nether Wart"));
        eq("four currencies only", 4, cost.size());
        eq("not bought", Optional.of(false), PumpkinSet.soldOut(HELMET_LORE));
        eq("bought", Optional.of(true), PumpkinSet.soldOut(List.of("0/1 Lifetime Stock")));
        eq("piece from a bracketed name", Optional.of("Chestplate"), PumpkinSet.piece("[Pumpkin Chestplate] [I]"));
    }

    private static void pumpkinBars() {
        Map<String, Double> have = Map.of("Candy", 21.5, "Carrots", 45_395.0, "Beetroots", 86_824.0,
                "Nether Wart", 13_535.0);
        var views = PumpkinSet.plan(List.of("Helmet", "Chestplate", PumpkinSet.SET), Set.of("Chestplate"),
                PumpkinSet.bundledPrices(), have);
        eq("owned chestplate gets no bar", 2, views.size());
        eq("helmet first", "Pumpkin Helmet", views.get(0).name());
        eq("set counts the three pieces left", 3, views.get(1).pieces());
        double setCandy = views.get(1).parts().stream().filter(p -> p.currency().equals("Candy")).findFirst()
                .orElseThrow().cost();
        eq("set candy is three pieces", 750.0, setCandy);
        double f = views.get(0).fraction();
        double expected = (21.5 / 250 + 45_395 / 2.5e6 + 86_824 / 2.5e6 + 13_535 / 250_000.0) / 4;
        check("bar is the average of each currency", Math.abs(f - expected) < 1e-9);
        check("everything owned, no set bar", PumpkinSet.plan(List.of(PumpkinSet.SET),
                Set.copyOf(PumpkinSet.PIECES), PumpkinSet.bundledPrices(), have).isEmpty());
        var full = PumpkinSet.plan(List.of("Boots"), Set.of(), PumpkinSet.bundledPrices(),
                Map.of("Candy", 300.0, "Carrots", 3e6, "Beetroots", 3e6, "Nether Wart", 3e5));
        check("affordable", full.get(0).affordable());
    }

    private static void ratesCountOnlyGains() {
        Rates r = new Rates();
        long t = 1_000_000L;
        r.tick(t, true);
        r.observe("Candy", 10.0);           // baseline only
        for (int i = 1; i <= 120; i++) {    // two minutes, 0.5 candy a second
            t += 1000L;
            r.tick(t, true);
            r.observe("Candy", 10.0 + i * 0.5);
        }
        eq("60 candy in two minutes is 1800/h", 1800.0, Math.round(r.perHour("Candy", 10 * 60_000L)));
        r.observe("Candy", 0.0);            // spent everything
        eq("spending is not a negative rate", 1800.0, Math.round(r.perHour("Candy", 10 * 60_000L)));
        eq("session candy", 60.0, r.session().get("Candy"));

        // Away from the dimension: time does not count, and what changed there is not a gain.
        r.forgetBaselines();
        t += 30 * 60_000L;
        r.tick(t, false);
        r.observe("Candy", 500.0);
        eq("first reading back is a baseline", 60.0, r.session().get("Candy"));
        check("too little time is no rate", Double.isNaN(new Rates().perHour("Candy", 600_000L)));
    }

    private static void abilityTimers() {
        Abilities a = new Abilities();
        long t = 0L;
        check("no class known yet, no timers", a.timers(t).isEmpty());
        check("the class is learned from a recharge message", a.recharged("Will-o'-Wisps", t));
        eq("two timers", 2, a.timers(t).size());
        eq("drop ability", "Will-o'-Wisps", a.timers(t).get(0).ability());

        a.pressed(Abilities.Key.SHIFT_DROP, t);
        eq("harvest moon 20s", 20_000L, a.timers(t).get(1).msLeft());
        a.pressed(Abilities.Key.SHIFT_DROP, t + 5_000L);
        eq("pressing again during the cooldown does not restart it", 15_000L, a.timers(t + 5_000L).get(1).msLeft());
        a.recharging("Harvest Moon", 2.31, t + 10_000L);
        eq("recharging corrects it", 2_310L, a.timers(t + 10_000L).get(1).msLeft());
        eq("rings once when ready", 1, a.newlyReady(t + 13_000L).size());
        eq("and only once", 0, a.newlyReady(t + 14_000L).size());

        // The Farming Abilities menu, Witch selected with a changed cooldown.
        boolean changed = a.learnFromMenu("Witch", List.of("Hunts down pests.", "",
                "Every 100 crops: Hex Chain", "Drop [Q]: Homing Hex (5s)",
                "Shift drop [Shift+Q]: Coven Circle (20s)", "Only Drop and Shift Drop hurt pests.", "",
                "✔ SELECTED"));
        check("menu read", changed);
        eq("witch selected", "Witch", a.selectedName());
        a.pressed(Abilities.Key.DROP, t + 20_000L);
        eq("menu cooldown used", 5_000L, a.timers(t + 20_000L).get(0).msLeft());

        Abilities b = new Abilities();
        b.importClasses(a.exportClasses(), a.selectedName());
        eq("classes survive a save", "Witch", b.selectedName());
    }

    private static void reforgerAndCaravan() {
        eq("1h 57m 17s", (3600 + 57 * 60 + 17) * 1000L, RecurringEvent.reforgerCountdown("Time Until Reforger: 1h 57m 17s"));
        eq("5m 37s", (5 * 60 + 37) * 1000L, RecurringEvent.reforgerCountdown("Time Until Reforger: 5m 37s"));
        eq("not the reforger", -1L, RecurringEvent.reforgerCountdown("[★1] VIP someone: Time Until Reforger: 1h"));
        check("arrived, with the leading space", RecurringEvent.reforgerArrived(" The Reforger has arrived to town!"));
        check("departed", RecurringEvent.reforgerDeparted(" The Reforger has departed!"));

        RecurringEvent r = RecurringEvent.reforger();
        long t = 1_000_000_000L;
        eq("unknown at first", RecurringEvent.State.UNKNOWN, r.view(t).state());
        r.startsIn(117 * 60_000L, t);
        eq("exact countdown", 117 * 60_000L, r.view(t).ms());
        check("marked exact", r.view(t).exact());
        long arrive = t + 117 * 60_000L;
        r.started(arrive);
        eq("here", RecurringEvent.State.ACTIVE, r.view(arrive + 60_000L).state());
        r.ended(arrive + 15 * 60_000L);
        RecurringEvent.View after = r.view(arrive + 15 * 60_000L);
        eq("back to waiting", RecurringEvent.State.WAITING, after.state());
        eq("next estimated 3h15m after arriving", 180 * 60_000L, after.ms());
        check("an estimate", !after.exact());
        r.started(arrive + 190 * 60_000L);
        eq("cycle learned from two arrivals", 190 * 60_000L, r.cycleMs());

        RecurringEvent c = RecurringEvent.caravan();
        check("warning in capitals", RecurringEvent.caravanWarning("PROTECT THE CARAVAN"));
        check("not the title line", !RecurringEvent.caravanWarning("Protect the Caravan"));
        check("start", RecurringEvent.caravanStarted("The caravan is leaving for the nether portal!"));
        check("delivered ends it", RecurringEvent.caravanEnded("Caravan Delivered!"));
        check("destroyed ends it", RecurringEvent.caravanEnded("Caravan Destroyed!"));
        c.startsIn(60_000L, t);
        eq("one minute", 60_000L, c.view(t).ms());
        c.started(t + 60_000L);
        c.ended(t + 4 * 60_000L);
        eq("next in 45 minutes from the start", 42 * 60_000L, c.view(t + 4 * 60_000L).ms());
        // Missed starts roll on by whole cycles.
        long later = t + 60_000L + 3 * 45 * 60_000L + 10 * 60_000L;
        eq("rolled on", 35 * 60_000L, c.view(later).ms());

        RecurringEvent saved = RecurringEvent.caravan();
        saved.importFrom(c.export());
        eq("state survives a save", c.view(later).ms(), saved.view(later).ms());
    }

    private static void bossIndexIntervalsAndWatchList() {
        long s = 1_000L;
        long m = 60_000L;
        eq("1s", java.util.OptionalLong.of(s), minerefinehud.boss.Interval.parse("1s"));
        eq("1 second", java.util.OptionalLong.of(s), minerefinehud.boss.Interval.parse("1 second"));
        eq("90 secs", java.util.OptionalLong.of(90 * s), minerefinehud.boss.Interval.parse("90 secs"));
        eq("5m", java.util.OptionalLong.of(5 * m), minerefinehud.boss.Interval.parse("5m"));
        eq("5 Minutes", java.util.OptionalLong.of(5 * m), minerefinehud.boss.Interval.parse(" 5 Minutes "));
        eq("5min", java.util.OptionalLong.of(5 * m), minerefinehud.boss.Interval.parse("5min"));
        eq("2 hours", java.util.OptionalLong.of(120 * m), minerefinehud.boss.Interval.parse("2 hours"));
        eq("1h30m", java.util.OptionalLong.of(90 * m), minerefinehud.boss.Interval.parse("1h30m"));
        eq("1 hour and 30 minutes", java.util.OptionalLong.of(90 * m),
                minerefinehud.boss.Interval.parse("1 hour and 30 minutes"));
        eq("1h, 2m, 3s", java.util.OptionalLong.of(3600 * s + 123 * s), minerefinehud.boss.Interval.parse("1h, 2m, 3s"));
        eq("1.5h", java.util.OptionalLong.of(90 * m), minerefinehud.boss.Interval.parse("1.5h"));
        eq("a plain number is seconds", java.util.OptionalLong.of(45 * s), minerefinehud.boss.Interval.parse("45"));
        eq("half-typed is nothing", java.util.OptionalLong.empty(), minerefinehud.boss.Interval.parse("5 mi"));
        eq("words are nothing", java.util.OptionalLong.empty(), minerefinehud.boss.Interval.parse("soon"));
        eq("milliseconds are not minutes", java.util.OptionalLong.empty(), minerefinehud.boss.Interval.parse("5ms"));
        eq("empty is nothing", java.util.OptionalLong.empty(), minerefinehud.boss.Interval.parse(""));
        eq("5m back", "5m", minerefinehud.boss.Interval.format(5 * m));
        eq("1h 30m back", "1h 30m", minerefinehud.boss.Interval.format(90 * m));
        eq("45s back", "45s", minerefinehud.boss.Interval.format(45 * s));
        for (long v : new long[] { 30 * s, 125 * s, 90 * m, 3 * 3_600_000L + 5 * s }) {
            eq("reads its own " + v, java.util.OptionalLong.of(v),
                    minerefinehud.boss.Interval.parse(minerefinehud.boss.Interval.format(v)));
        }

        BossTracker t = new BossTracker();
        t.onSpawned("Zeus", 0L);
        t.onSpawned("Guardian 'o Toole", 0L);
        t.onSpawned("Endor", 0L);
        var views = t.views(1_000L);
        var picked = minerefinehud.boss.BossWorlds.watching(views, List.of("zeus", "Guardian o' Toole"));
        eq("only the picked bosses", 2, picked.size());
        check("spelling and case do not matter", picked.stream().anyMatch(v -> v.displayName().startsWith("Guardian")));
        check("an unpicked boss is left out", picked.stream().noneMatch(v -> v.displayName().equals("Endor")));
    }

    private static void bossIndexIsRead() {
        var zeus = BossIndex.entry("« Zeus »", List.of("Health: 1,250,000❤", "Damage: 500", "Speed: 1", "",
                "Total Loot", "■ 7,500 EXP", "■ 150,000 Coins", "■ 20 Tokens", "",
                "★ Threshold at 5% damage dealt", "↳ 9x Fragments for you", "", "SPAWNED! (43.86% HP)", "",
                "CLICK to view rankings rewards!"));
        check("zeus read", zeus.isPresent());
        eq("zeus name", "Zeus", zeus.get().bossName());
        check("zeus up", zeus.get().alive());

        var endor = BossIndex.entry("« Endor »", List.of("Health: 750,000❤", "Total Loot", "■ 15 Tokens",
                "★ Threshold at 10% damage dealt", "⌚ 9m 40s", "CLICK to view rankings rewards!"));
        eq("endor back in 9m 40s", 580_000L, endor.map(BossIndex.Entry::respawnMs).orElse(-5L));
        check("a shop item is no boss", BossIndex.entry("Pumpkin Helmet", HELMET_LORE).isEmpty());
    }

    private static void bossIndexFixesTimers() {
        BossTracker tracker = new BossTracker();
        long t = 10_000_000L;
        // Never seen: the index gives the timer outright, trusted enough for the reminder.
        tracker.onIndexRespawnIn("Endor", 580_000L, t);
        BossTracker.BossView v = tracker.view("Endor", t).orElseThrow();
        eq("eta from the index", 580_000L, v.etaMs().orElse(-1));
        check("exact enough for the reminder", v.intervalConfident());
        check("re-reading the same timer changes nothing", !tracker.onIndexRespawnIn("Endor", 579_500L, t + 500L));
        BossAlerts alerts = new BossAlerts();
        long due = t + 575_000L;
        eq("reminder rings", 1, alerts.newlyDue(tracker.views(due), due, 10_000L).size());
        tracker.onIndexRespawnIn("Endor", 4_000L, due + 1_000L);
        eq("not again for the same respawn", 0, alerts.newlyDue(tracker.views(due + 1_000L), due + 1_000L, 10_000L).size());

        // A watched kill plus the index's timer is a real interval.
        BossTracker watched = new BossTracker();
        watched.onSlain("Zeus", t);
        watched.onIndexRespawnIn("Zeus", 120_000L, t + 60_000L);
        BossTracker.BossView z = watched.view("Zeus", t + 60_000L).orElseThrow();
        eq("interval learned", 180_000L, z.intervalMs().orElse(-1));
        watched.onSpawned("Zeus", t + 180_000L);
        eq("the spawn does not count it twice", 1, watched.view("Zeus", t + 180_000L).orElseThrow().sampleCount());

        // Alive in the index while the tracker thought it dead.
        watched.onSlain("Zeus", t + 200_000L);
        check("alive from the index", watched.onIndexAlive("Zeus", t + 250_000L));
        eq("now up", BossTracker.Status.ALIVE, watched.view("Zeus", t + 250_000L).orElseThrow().status());

        // The timer survives a save.
        BossTracker restored = new BossTracker();
        restored.importAll(tracker.export());
        check("respawn time saved", restored.view("Endor", t).orElseThrow().etaMs().isPresent());
    }

    private static void panelsRender() {
        List<HudModel.Line> boss = HudModel.bossPanel(List.of(), HudModel.Options.defaults(),
                List.of(HalloweenHud.reforgerLine(new RecurringEvent.View(RecurringEvent.State.WAITING, 7_037_000L, true))));
        eq("reforger shows without bosses", 2, boss.size());
        eq("reforger row", "Reforger: 1:57:17", boss.get(1).text());

        Rates rates = new Rates();
        var stats = HalloweenHud.statsPanel(new HalloweenHud.Stats(Map.of("Candy", 21.5, "Carrots", 45_395.0),
                rates, 600_000L, 2.1, 1, new RecurringEvent.View(RecurringEvent.State.WAITING, 600_000L, false),
                50.0, 1, List.of(), Optional.empty()), HalloweenHud.StatsLines.all());
        check("candy line", stats.stream().anyMatch(l -> l.text().equals("Candy 21.5  …/h")));
        check("pest line", stats.stream().anyMatch(l -> l.text().equals("Pests: +2.1 candy, 1 charm")));
        check("caravan estimate", stats.stream().anyMatch(l -> l.text().equals("Caravan: in ~10:00")));
        check("refine line", stats.stream().anyMatch(l -> l.text().equals("Refine II: 21.5/50 candy")));
        List<String> sections = stats.stream().filter(l -> l.kind() == HudModel.Kind.SECTION).map(HudModel.Line::text).toList();
        eq("sections, none for buildings never seen", List.of("RATES", "EVENTS"), sections);
        eq("title has the pumpkin", HalloweenHud.PUMPKIN_ICON, stats.get(0).icon());

        var abilities = HalloweenHud.abilityPanel(List.of(new Abilities.Timer("Will-o'-Wisps", Abilities.Key.DROP, 0L),
                new Abilities.Timer("Harvest Moon", Abilities.Key.SHIFT_DROP, 12_300L)));
        eq("ready", "Will-o'-Wisps: READY", abilities.get(0).text());
        eq("ready chip", "READY", abilities.get(0).cells().get(0).chip());
        eq("cooling", "Harvest Moon: 12.3s", abilities.get(1).text());
    }

    /** The Forge and Barn menus exactly as the screenshots of 2026-10-19 show them. */
    static final List<String> FORGE = List.of("", " ▍ ʟᴇᴠᴇʟ 2 / 10", "", " ▍ Grants x1.2 ☽ Hex (event damage)",
            " ▍ Grants +10 ❤ Event Health", "▼↓↓⇓—[ UPGRADE ]—⇓↓↓▼", " ▍ ʟᴇᴠᴇʟ 3 / 10", "",
            " ▍ Grants x1.3 ☽ Hex (event damage)", " ▍ Grants +15 ❤ Event Health", "", "ᴄᴏѕᴛ",
            "⇢ 1,500,000 Beetroots ✗", "⇢ Upgrade Time 1m", "", "ᴇᴀᴄʜ ᴜᴘɢʀᴀᴅᴇ ᴀʟѕᴏ ɢʀᴀɴᴛѕ +10 ᴇᴠᴇɴᴛ ѕᴘᴇᴇᴅ",
            "(!) You need 1,440,287 more Beetroots.");
    static final List<String> BARN = List.of("", " ▍ ʟᴇᴠᴇʟ 4 / 10", "", " ▍ Grants x1.8 more crops",
            "▼↓↓⇓—[ UPGRADE ]—⇓↓↓▼", " ▍ ʟᴇᴠᴇʟ 5 / 10", "", " ▍ Grants x2 more crops", "", "ᴄᴏѕᴛ",
            "⇢ [item/carrot@items] 1,500,000 Carrots ✗", "⇢ Upgrade Time 3m", "",
            "ᴇᴀᴄʜ ᴜᴘɢʀᴀᴅᴇ ᴀʟѕᴏ ɢʀᴀɴᴛѕ +10 ᴇᴠᴇɴᴛ ѕᴘᴇᴇᴅ", "(!) You need 1,443,800 more Carrots.");

    private static void buildingsAreRead() {
        Buildings.Info forge = Buildings.read("§dForge", FORGE).orElseThrow();
        eq("forge name", "Forge", forge.name());
        eq("forge level", 2, forge.level());
        eq("forge max", 10, forge.max());
        eq("forge grants now", 2, forge.now().size());
        eq("hex stat", "Hex", forge.now().get(0).stat);
        eq("hex value", "x1.2", forge.now().get(0).value);
        eq("health stat", "Event Health", forge.now().get(1).stat);
        eq("next health", "+15", forge.next().get(1).value);
        eq("forge cost", Map.of("Beetroots", 1_500_000.0), forge.cost());
        eq("forge time", 60_000L, forge.upgradeMs());
        eq("no timer shown", 0L, forge.remainingMs());

        Buildings.Info barn = Buildings.read("Barn", BARN).orElseThrow();
        eq("barn crops", "crops", barn.now().get(0).stat);
        eq("barn next", "x2", barn.next().get(0).value);
        eq("barn cost through the sprite", Map.of("Carrots", 1_500_000.0), barn.cost());
        eq("barn time", 180_000L, barn.upgradeMs());

        Buildings.Info maxed = Buildings.read("Barn", List.of(" ▍ ʟᴇᴠᴇʟ 10 / 10", " ▍ Grants x4 more crops")).orElseThrow();
        check("maxed", maxed.maxed());
        check("no cost when maxed", maxed.cost().isEmpty());

        check("a pumpkin piece is not a building", Buildings.read("Pumpkin Helmet",
                List.of(" Event Health: +3❤", "Cost:", "➼ 250 🍬 Candy ❌", "1/1 Lifetime Stock")).isEmpty());
        check("gear with a level but no grants is not a building", Buildings.read("Debris Shovel",
                List.of("Level 3 / 5", "Cost: 5b")).isEmpty());
        check("the 'each upgrade also grants' line is not a grant", forge.next().size() == 2);
        eq("upgrade times", 5_400_000L, Buildings.duration("1h 30m"));
        eq("written together", 5_400_000L, Buildings.duration("1h30m"));
        eq("not a word", 0L, Buildings.duration("3 minutes of 5 hex"));
        eq("a bracketed name keeps the name", "Barn", Buildings.read("[Barn]", BARN).orElseThrow().name());
        eq("small-caps name", "Barn", Buildings.read("ʙᴀʀɴ", BARN).orElseThrow().name());
    }

    private static void buildingUpgrades() {
        Buildings b = new Buildings();
        Map<String, Buildings.Saved> saved = new java.util.LinkedHashMap<>();
        long t = 1_000_000L;
        check("first sight is saved", b.learn(saved, Buildings.read("Barn", BARN).orElseThrow(), t));
        check("the same menu again changes nothing", !b.learn(saved, Buildings.read("Barn", BARN).orElseThrow(), t + 500));
        b.learn(saved, Buildings.read("Forge", FORGE).orElseThrow(), t);

        check("spending something else is no upgrade",
                b.spent(saved, "Carrots", 250_000.0, t + 1_000).isEmpty());
        eq("the barn's price leaving carrots starts it", Optional.of("Barn"),
                b.spent(saved, "Carrots", 1_500_000.0, t + 2_000));
        eq("upgrading view", Buildings.State.UPGRADING,
                Buildings.views(saved, Map.of(), t + 92_000).get(0).state());
        eq("time left", 90_000L, Buildings.views(saved, Map.of(), t + 92_000).get(0).msLeft());
        check("not twice", b.spent(saved, "Carrots", 1_500_000.0, t + 3_000).isEmpty());
        check("not long after the menu closed", b.spent(saved, "Beetroots", 1_500_000.0,
                t + Buildings.SPEND_WINDOW_MS + 1).isEmpty());

        check("nothing done early", b.finish(saved, t + 100_000).isEmpty());
        List<Buildings.Finished> done = b.finish(saved, t + 182_500);
        eq("barn finished", 1, done.size());
        eq("to level 5", 5, done.get(0).level());
        check("rings", done.get(0).ring());
        Buildings.View after = Buildings.views(saved, Map.of(), t + 183_000).stream()
                .filter(v -> v.name().equals("Barn")).findFirst().orElseThrow();
        eq("next cost unknown until the menu opens", Buildings.State.OPEN_MENU, after.state());
        eq("level 5 now", 5, after.level());
        eq("its grants are now x2", "crops x2", after.perks());

        // An upgrade that ended while the game was closed is applied without ringing.
        b.learn(saved, Buildings.read("Forge", FORGE).orElseThrow(), t + 200_000);
        b.spent(saved, "Beetroots", 1_499_000.0, t + 201_000);
        List<Buildings.Finished> late = b.finish(saved, t + 3_600_000);
        check("late one does not ring", late.size() == 1 && !late.get(0).ring());
        eq("forge level 3", 3, saved.get("Forge").level);
    }

    /** The menu shows the next level before the balance falls: the level jump starts the timer. */
    private static void buildingUpgradeSeenInTheMenu() {
        Buildings b = new Buildings();
        Map<String, Buildings.Saved> saved = new java.util.LinkedHashMap<>();
        b.learn(saved, Buildings.read("Barn", BARN).orElseThrow(), 0L);
        List<String> level5 = BARN.stream().map(l -> l.replace("ʟᴇᴠᴇʟ 5", "ʟᴇᴠᴇʟ 6").replace("ʟᴇᴠᴇʟ 4", "ʟᴇᴠᴇʟ 5")
                .replace("1,500,000", "3,000,000").replace("Time 3m", "Time 5m")).toList();
        check("the jump is a change", b.learn(saved, Buildings.read("Barn", level5).orElseThrow(), 500L));
        Buildings.View v = Buildings.views(saved, Map.of(), 1_000L).get(0);
        eq("upgrading after the jump", Buildings.State.UPGRADING, v.state());
        eq("to level 5", 5, v.target());
        eq("with the bought level's 3 minutes", 179_500L, v.msLeft());
        check("the balance falling after is not a second upgrade",
                b.spent(saved, "Carrots", 1_500_000.0, 1_500L).isEmpty());
        List<Buildings.Finished> done = b.finish(saved, 181_000L);
        eq("finished once", 1, done.size());
        Buildings.View after = Buildings.views(saved, Map.of("Carrots", 0.0), 181_000L).get(0);
        eq("already knows the next cost", Buildings.State.SAVING, after.state());
        eq("next is level 6", 6, after.target());

        // The menu still shows the old level after our timer ended, then catches up: one upgrade.
        Buildings d = new Buildings();
        Map<String, Buildings.Saved> stale = new java.util.LinkedHashMap<>();
        d.learn(stale, Buildings.read("Barn", BARN).orElseThrow(), 0L);
        d.spent(stale, "Carrots", 1_500_000.0, 1_000L);
        List<Buildings.Finished> first = d.finish(stale, 181_000L);
        eq("noticed time is now", 181_000L, first.get(0).at());
        check("old menu ignored", !d.learn(stale, Buildings.read("Barn", BARN).orElseThrow(), 181_500L));
        eq("level stays 5", 5, stale.get("Barn").level);
        d.learn(stale, Buildings.read("Barn", level5).orElseThrow(), 182_000L);
        check("no second upgrade", d.finish(stale, 400_000L).isEmpty());
        eq("saving for 6", Buildings.State.SAVING, Buildings.views(stale, Map.of(), 400_000L).get(0).state());

        // A level seen higher after a long time away is not a purchase now.
        Buildings c = new Buildings();
        Map<String, Buildings.Saved> later = new java.util.LinkedHashMap<>();
        c.learn(later, Buildings.read("Barn", BARN).orElseThrow(), 0L);
        c.learn(later, Buildings.read("Barn", level5).orElseThrow(), 3_600_000L);
        eq("no timer for an old upgrade", Buildings.State.SAVING,
                Buildings.views(later, Map.of(), 3_600_000L).get(0).state());
    }

    /**
     * The user's report of 2026-10-23: /upgrades stats shown as buildings, maxed ones shown,
     * Windmill/Minion/Cauldron never shown. Chat lines are copied from their game logs.
     */
    private static void upgradesMenuAndChat() {
        // /upgrades: the same layout but paid in Tokens and instant (their saved More Damage had no time).
        List<String> moreDamage = List.of(" ▍ ʟᴇᴠᴇʟ 100 / 250", " ▍ Grants +100 Damage when hitting",
                "▼↓↓⇓—[ UPGRADE ]—⇓↓↓▼", " ▍ ʟᴇᴠᴇʟ 101 / 250", " ▍ Grants +102 Damage when hitting", "", "ᴄᴏѕᴛ",
                "⇢ 34 Tokens ✗");
        check("an /upgrades stat is not timed", !Buildings.read("More Damage", moreDamage).orElseThrow().timed());
        check("a building is timed", Buildings.read("Barn", BARN).orElseThrow().timed());

        // Boosts not worded "Grants".
        List<String> windmill = BARN.stream().map(l -> l.replace("Grants x1.8 more crops", "Sweep +1.8 adjacent crops")
                .replace("Grants x2 more crops", "Sweep +2.1 adjacent crops")).toList();
        Buildings.Info w = Buildings.read("Windmill", windmill).orElseThrow();
        eq("windmill stat", "Sweep adjacent crops", w.now().get(0).stat);
        eq("windmill next", "+2.1", w.next().get(0).value);
        eq("windmill perk", "Sweep adjacent crops +1.8 → +2.1", Buildings.views(Map.of("Windmill", saved(w)),
                Map.of(), 0L).get(0).perks());
        List<String> minion = BARN.stream().map(l -> l.replace("Grants x1.8 more crops", "Auto Harvest every 2.6s")
                .replace("Grants x2 more crops", "Auto Harvest every 2.3s")).toList();
        Buildings.Info m = Buildings.read("Minion", minion).orElseThrow();
        eq("minion value", "2.3s", m.next().get(0).value);
        check("a line without the bar is no boost", Buildings.read("Debris Shovel",
                List.of("Level 3 / 5", "Speed +5 blocks")).isEmpty());

        eq("upgrading message", Optional.of(new Buildings.Chat("Cauldron", 5, 180_000L, false)),
                Buildings.chat("Upgrading Cauldron to Lv 5. Done in 3m."));
        eq("hour", 3_600_000L, Buildings.chat("Upgrading Barn to Lv 9. Done in 1h.").orElseThrow().ms());
        eq("complete message", Optional.of(new Buildings.Chat("Minion", 9, 0L, true)),
                Buildings.chat("COMPLETE! Minion is now Lv 9! Auto Harvest every 2.3s."));
        check("a player pasting it is nothing", Buildings.chat(
                "[★972] ✠:ʀᴜɴɪᴄ:✠ 81hp_: COMPLETE! Windmill is now Lv 7! Sweep +2.1 adjacent crops.").isEmpty());
        check("nor a player's upgrading line", Buildings.chat(
                "[★3467] JR MOD Melonius: Upgrading Cauldron to Lv 15. Done in 1h.").isEmpty());

        // Saved as in their config: Barn and Forge maxed, Candy Jar saving.
        Buildings b = new Buildings();
        Map<String, Buildings.Saved> saved = new java.util.LinkedHashMap<>();
        b.learn(saved, Buildings.read("Barn", List.of(" ▍ ʟᴇᴠᴇʟ 10 / 10", " ▍ Grants x3 more crops")).orElseThrow(), 0L);
        b.learn(saved, Buildings.read("Forge", FORGE).orElseThrow(), 0L);
        long t = 1_000_000L;
        check("never-read Cauldron added from chat", b.chat(saved, Buildings.chat("Upgrading Cauldron to Lv 5. Done in 3m.").orElseThrow(), t));
        List<Buildings.View> views = Buildings.views(saved, Map.of(), t + 1_000L);
        eq("maxed barn hidden, upgrading first", List.of("Cauldron", "Forge"), views.stream().map(Buildings.View::name).toList());
        eq("cauldron upgrading", Buildings.State.UPGRADING, views.get(0).state());
        eq("to 5", 5, views.get(0).target());
        eq("its 3 minutes", 179_000L, views.get(0).msLeft());
        // The server finishes a little before our count: ends now and rings once.
        check("complete changes it", b.chat(saved, Buildings.chat("COMPLETE! Cauldron is now Lv 5!").orElseThrow(), t + 179_000L));
        List<Buildings.Finished> done = b.finish(saved, t + 179_100L);
        check("rings once", done.size() == 1 && done.get(0).ring() && done.get(0).level() == 5);
        check("the timer then finishing changes nothing", b.finish(saved, t + 181_000L).isEmpty());
        check("a second complete is nothing", !b.chat(saved, Buildings.chat("COMPLETE! Cauldron is now Lv 5!").orElseThrow(), t + 182_000L));
        eq("open for the next cost", Buildings.State.OPEN_MENU, Buildings.views(saved, Map.of(), t + 182_000L).stream()
                .filter(v -> v.name().equals("Cauldron")).findFirst().orElseThrow().state());

        // A menu naming it in another case is the same building.
        b.chat(saved, Buildings.chat("Upgrading Candy Jar to Lv 3. Done in 1m.").orElseThrow(), t);
        b.learn(saved, Buildings.read("CANDY JAR", BARN).orElseThrow(), t + 1_000L);
        eq("one candy jar", 1L, saved.keySet().stream().filter(k -> k.equalsIgnoreCase("candy jar")).count());

        // Our timer finished first: the server's message after it is nothing new.
        Buildings c = new Buildings();
        Map<String, Buildings.Saved> own = new java.util.LinkedHashMap<>();
        c.chat(own, Buildings.chat("Upgrading Forge to Lv 3. Done in 1m.").orElseThrow(), 0L);
        eq("finished by the timer", 1, c.finish(own, 60_000L).size());
        check("complete after it changes nothing", !c.chat(own, Buildings.chat("COMPLETE! Forge is now Lv 3! Hex x1.3.").orElseThrow(), 60_500L));
    }

    private static Buildings.Saved saved(Buildings.Info info) {
        Map<String, Buildings.Saved> saved = new java.util.LinkedHashMap<>();
        new Buildings().learn(saved, info, 0L);
        return saved.get(info.name());
    }

    private static void buildingPanel() {
        Buildings b = new Buildings();
        Map<String, Buildings.Saved> saved = new java.util.LinkedHashMap<>();
        b.learn(saved, Buildings.read("Barn", BARN).orElseThrow(), 0L);
        b.learn(saved, Buildings.read("Forge", FORGE).orElseThrow(), 0L);
        List<Buildings.View> views = Buildings.views(saved, Map.of("Carrots", 56_200.0, "Beetroots", 59_713.0), 0L);
        List<HudModel.Line> lines = HalloweenHud.buildingLines(views);
        eq("barn title", "Barn → Lv 5  56.2k/1.5m", lines.get(0).text());
        eq("carrot icon", "item:carrot", lines.get(0).icon());
        check("barn bar", lines.get(1).kind() == HudModel.Kind.BAR && Math.abs(lines.get(1).progress() - 56_200 / 1.5e6) < 1e-9);
        eq("barn perk", "   crops x1.8 → x2", lines.get(2).text());
        eq("forge hex", "   Hex x1.2 → x1.3", lines.get(5).text());
        eq("forge health on its own line", "   Health +10 → +15", lines.get(6).text());

        List<HudModel.Line> rich = HalloweenHud.buildingLines(Buildings.views(saved,
                Map.of("Carrots", 2_000_000.0), 0L));
        eq("affordable is green", HudModel.Style.GOOD, rich.get(0).style());
    }

    /** The Halloween look draws sections, item icons and a bar fading orange to purple. */
    private static void spookyLookPaints() {
        minerefinehud.hud.HudStyle style = minerefinehud.hud.HudStyle.defaults()
                .withTitles(minerefinehud.hud.HudStyle.Titles.BAND)
                .withNumbers(minerefinehud.hud.HudStyle.Numbers.COLUMNS)
                .withBar(minerefinehud.hud.HudStyle.BarStyle.THICK)
                .withPercent(minerefinehud.hud.HudStyle.Percent.INSIDE);
        var painted = minerefinehud.hud.PanelPainter.paint(minerefinehud.hud.SamplePanels.halloween(), style,
                minerefinehud.hud.HudStyle.Background.TOOLTIP, 85, minerefinehud.hud.Theme.spooky(), s -> s.length() * 6);
        long icons = painted.ops().stream().filter(o -> o instanceof minerefinehud.hud.PanelPainter.Icon).count();
        check("icons drawn with gear labels set to words", icons >= 6);
        java.util.Set<Integer> barColours = new java.util.HashSet<>();
        for (var op : painted.ops()) {
            if (op instanceof minerefinehud.hud.PanelPainter.Fill f && f.y2() - f.y1() == 10) {
                barColours.add(f.color());
            }
        }
        check("bars fade through several colours", barColours.size() > 5);
        check("section text drawn", painted.ops().stream().anyMatch(o ->
                o instanceof minerefinehud.hud.PanelPainter.Text t && t.text().equals("BUILDINGS")));
        eq("halfway colour", 0xFFD06B8F, minerefinehud.hud.Theme.mix(0xFFFF8A1F, 0xFFA04BFF, 0.5));
    }

    // ---------------------------------------------------------------- helpers

    /** The 1.3.0 tester reports, each from what the game really sends. */
    private static void testerReports() {
        // The sidebar title and address are small capitals, the title's "f" a Cyrillic letter.
        List<String> real = List.of("ᴍɪɴᴇʀᴇғɪɴᴇ", "§b", "| 17,430,000 (ʀᴀɴᴋ #36)§7", "| ⛃ ᴄᴏɪɴѕ: 82.6B§5",
                "ᴍɪɴᴇʀᴇꜰɪɴᴇ.ᴍɪɴᴇʜᴜᴛ.ɢɢ§0");
        check("small-caps title is MineRefine", HalloweenText.isMineRefineTitle(real.get(0)));
        check("any sidebar line names the server", HalloweenText.isMineRefine(real.subList(1, real.size())));
        check("another server's sidebar is not MineRefine",
                !HalloweenText.isMineRefine(List.of("ʜʏᴘɪxᴇʟ", "Coins: 5")));
        check("small-caps halloween heading", HalloweenText.sidebar(List.of("ᴍɪɴᴇʀᴇғɪɴᴇ", "🎃 ʜᴀʟʟᴏᴡᴇᴇɴ")).halloween());
        check("a mining sidebar in small caps is not Halloween", !HalloweenText.sidebar(real).halloween());

        // Fire Coral is a crop: in the sidebar, the per-crop rows and the crop rate.
        HalloweenText.Sidebar coral = HalloweenText.sidebar(List.of("🎃 HALLOWEEN", "│ Fire Coral 12,345",
                "│ Carrots 45,395", "│ ⋈ Candy 21.5"));
        eq("fire coral read", 12345.0, coral.balances().get("Fire Coral"));
        eq("fire coral on the action bar", Optional.of(new HalloweenText.CropReading("Fire Coral", 1234.0)),
                HalloweenText.actionBar("[item/fire_coral@items] 1,234"));
        Rates rates = new Rates();
        rates.observe("Fire Coral", 100.0);
        for (int i = 1; i <= 240; i++) {
            rates.tick(i * 500L, true);
        }
        rates.observe("Fire Coral", 160.0);
        var stats = HalloweenHud.statsPanel(new HalloweenHud.Stats(Map.of("Fire Coral", 160.0, "Candy", 1.0),
                rates, 600_000L, 0, 0, new RecurringEvent.View(RecurringEvent.State.UNKNOWN, 0L, false), 0, 0,
                List.of(), Optional.empty()), HalloweenHud.StatsLines.all());
        check("fire coral has its own row", stats.stream().anyMatch(l -> l.text().startsWith("Fire Coral 160")
                && l.icon().equals("item:fire_coral")));
        check("fire coral counts in the crop rate", stats.stream().anyMatch(l -> l.text().equals("Crops 1.8k/h")));

        // The Halloween Update's new crop.
        HalloweenText.Sidebar hallowed = HalloweenText.sidebar(List.of("🎃 HALLOWEEN", "│ Hallowed 4,321",
                "│ Candy 2.5", "Resurrection 7"));
        eq("hallowed read", 4321.0, hallowed.balances().get("Hallowed"));
        check("candy next to hallowed alone is the event", HalloweenText.sidebar(List.of("│ Hallowed 4,321",
                "│ Candy 2.5")).halloween());

        // The Belfry shortens cooldowns below the menu's: press to "recharged" is measured.
        Abilities belfry = new Abilities();
        belfry.importClasses(Map.of(), "Jack o'Lantern");
        belfry.pressed(Abilities.Key.DROP, 10_000L);
        belfry.recharged("Will-o'-Wisps", 15_600L);
        belfry.pressed(Abilities.Key.DROP, 20_000L);
        eq("the measured cooldown is used next time", 5_600L, belfry.timers(20_000L).get(0).msLeft());
        belfry.pressed(Abilities.Key.SHIFT_DROP, 30_000L);
        eq("the other ability keeps its own", 20_000L, belfry.timers(30_000L).get(1).msLeft());
        // A press while still recharging is answered at once; it did not fire.
        Abilities dud = new Abilities();
        dud.importClasses(Map.of(), "Jack o'Lantern");
        dud.pressed(Abilities.Key.DROP, 1_000L);
        dud.recharging("Will-o'-Wisps", 0.5, 1_100L);
        dud.recharged("Will-o'-Wisps", 1_600L);
        dud.pressed(Abilities.Key.DROP, 5_000L);
        eq("a press that did not fire teaches nothing", 7_000L, dud.timers(5_000L).get(0).msLeft());
        Abilities slow = new Abilities();
        slow.importClasses(Map.of(), "Jack o'Lantern");
        slow.pressed(Abilities.Key.DROP, 0L);
        slow.recharged("Will-o'-Wisps", 60_000L);
        slow.pressed(Abilities.Key.DROP, 70_000L);
        eq("a minute is not a cooldown of seven seconds", 7_000L, slow.timers(70_000L).get(0).msLeft());

        // Picking a class at the NPC says so in chat; the panel follows without reading the menu.
        eq("ability set read", Optional.of("Witch"), HalloweenText.abilitySet("Farming ability set to Witch."));
        eq("other chat is no pick", Optional.empty(), HalloweenText.abilitySet("Will-o'-Wisps has recharged! [DROP]"));
        Abilities a = new Abilities();
        a.importClasses(Map.of(), "Jack o'Lantern");
        check("picked from chat", a.select("Witch"));
        eq("witch timers now", "Homing Hex", a.timers(0L).get(0).ability());
        check("same pick again changes nothing", !a.select("witch"));
        a.select("Scarecrow");
        check("an unseen class shows no other class's timers", a.timers(0L).isEmpty());

        // Using an ability (drop with the hoe) starts its timer; the server only says "recharged".
        Abilities used = new Abilities();
        used.importClasses(Map.of(), "Jack o'Lantern");
        used.pressed(Abilities.Key.DROP, 1_000L);
        eq("cooldown runs after a use", 4_000L, used.timers(4_000L).get(0).msLeft());
        used.recharged("Will-o'-Wisps", 8_000L);
        check("ready at the server's recharged", used.timers(8_000L).get(0).ready());

        // A missed caravan is two cycles, not one long one.
        RecurringEvent c = RecurringEvent.caravan();
        long t = 50_000_000L;
        c.started(t);
        c.started(t + 90 * 60_000L);
        eq("cycle stays 45 minutes", 45 * 60_000L, c.cycleMs());
        c.ended(t + 94 * 60_000L);
        eq("next in about 41 minutes", 41 * 60_000L, c.view(t + 94 * 60_000L).ms());
        RecurringEvent late = RecurringEvent.caravan();
        late.started(t);
        late.started(t + 68 * 60_000L);
        eq("one late caravan is not two short cycles", 45 * 60_000L, late.cycleMs());
        RecurringEvent three = RecurringEvent.caravan();
        three.started(t);
        three.started(t + 3 * 44 * 60_000L);
        eq("three missed cycles of 44", 44 * 60_000L, three.cycleMs());
        RecurringEvent.Saved bad = new RecurringEvent.Saved();
        bad.cycleMs = 85 * 60_000L;
        bad.lastStart = t;
        bad.nextAt = t + 85 * 60_000L;
        RecurringEvent old = RecurringEvent.caravan();
        old.importFrom(bad);
        eq("a stretched cycle saved by 1.3.0 is ignored", 45 * 60_000L, old.cycleMs());
        RecurringEvent.Saved sane = new RecurringEvent.Saved();
        sane.cycleMs = 40 * 60_000L;
        RecurringEvent good = RecurringEvent.caravan();
        good.importFrom(sane);
        eq("a sane learned cycle is kept", 40 * 60_000L, good.cycleMs());

        // A boss the index shows spawned stays in the panel, even with alive rows off.
        HudModel.Options noAlive = new HudModel.Options(true, false, false, 200.0, true, 5,
                HudModel.MineLines.all(), new HudModel.BossLines(false, true), HudModel.ProgressLines.all());
        BossTracker b = new BossTracker();
        b.onSlain("Zeus", t);
        b.onSpawned("Zeus", t + 120_000L);
        b.onSlain("Zeus", t + 140_000L);
        b.onIndexAlive("Zeus", t + 400_000L);
        List<HudModel.Line> panel = HudModel.bossPanel(b.views(t + 401_000L), noAlive);
        check("index-up boss still shown", panel.stream().anyMatch(l -> l.text().equals("Zeus: UP")));
        eq("unknown once its kill went unheard", BossTracker.Status.UNKNOWN,
                b.view("Zeus", t + 400_000L + BossTracker.INDEX_UP_MS + 1_000L).orElseThrow().status());
        b.onSlain("Zeus", t + 420_000L);
        eq("a heard kill gives the countdown back", BossTracker.Status.DEAD,
                b.view("Zeus", t + 430_000L).orElseThrow().status());
        BossTracker heard = new BossTracker();
        heard.onSpawned("Ares", t);
        heard.onIndexAlive("Ares", t + 10_000L);
        check("a spawn heard just now stays a normal alive row",
                HudModel.bossPanel(heard.views(t + 10_000L), noAlive).isEmpty());
        BossTracker stale = new BossTracker();
        stale.onSpawned("Ares", t);
        check("a spawn heard long ago is replaced by the index", stale.onIndexAlive("Ares", t + 3_600_000L));
        b.onIndexAlive("Zeus", t + 500_000L);
        long stillUp = t + 500_000L + BossTracker.INDEX_UP_MS + 1_000L;
        check("the index saying it again renews it", b.onIndexAlive("Zeus", stillUp));
        eq("up again", BossTracker.Status.ALIVE, b.view("Zeus", stillUp).orElseThrow().status());
        b.onIndexAlive("Hera", t);
        BossTracker reloaded = new BossTracker();
        reloaded.importAll(b.export());
        check("index-up survives a save", reloaded.view("Hera", t).orElseThrow().upFromIndex());
    }

    /** The Resurrection 6 card as the user's screenshot shows it (2026-10-23). */
    private static final List<String> CARD_6_LORE = List.of(
            "Crop Multiplier: x8 → x10",
            "Unlocks the Scarecrow Upgrade",
            "",
            "Cost:",
            "⊢ Fire Coral Hoe VII ✔",
            "⊢ 350,000,000 Total Crops ✘ (303,808,740)",
            "⊢ Your crops & hoe reset, but your candy & refines are kept.",
            "",
            "ʜᴏᴇ ᴘʀɪᴄᴇѕ ɪɴᴄʀᴇᴀѕᴇ ʙʏ x1.5 ᴇᴀᴄʜ ʀᴇѕᴜʀʀᴇᴄᴛɪᴏɴ");

    private static void resurrectionIsRead() {
        // The player's own lines, from the game logs of resurrections 1 to 5.
        eq("own resurrection 1", OptionalInt.of(1), Resurrection.chatLevel("RESURRECTED! You are now Resurrection 1!"));
        eq("own resurrection 5", OptionalInt.of(5),
                Resurrection.chatLevel("§6§lRESURRECTED! §fYou are now Resurrection 5!"));
        eq("our own news to everyone is not read", OptionalInt.empty(),
                Resurrection.chatLevel(" 81hp_ rose to Resurrection 5! "));
        eq("someone else's news", OptionalInt.empty(), Resurrection.chatLevel(" jee213 rose to Resurrection 2! "));
        eq("a player pasting it", OptionalInt.empty(),
                Resurrection.chatLevel("[★252] VIP catboyeepy: RESURRECTED! You are now Resurrection 7!"));
        eq("the multiplier line is not a level", OptionalInt.empty(),
                Resurrection.chatLevel("Crop Multiplier: x6.5 → x8"));

        eq("hoe upgrade", Resurrection.rank("Fire Coral", "VII"),
                Resurrection.chatHoe("UPGRADE! Fire Coral Hoe VII: Fire Coral: +7"));
        eq("new crop hoe", Resurrection.rank("Beetroot", "I"),
                Resurrection.chatHoe("NEW CROP! Beetroot Hoe I: You can now harvest Beetroot!"));
        eq("carrot I is the first", OptionalInt.of(0), Resurrection.rank("Carrot", "I"));
        eq("beetroot I follows carrot VII", OptionalInt.of(7), Resurrection.rank("Beetroot", "I"));
        eq("fire coral VII", OptionalInt.of(27), Resurrection.rank("Fire Coral", "VII"));
        eq("no tier VIII", OptionalInt.empty(), Resurrection.rank("Carrot", "VIII"));
        eq("a refine is not a hoe", OptionalInt.empty(), Resurrection.chatHoe("Your hoe has been refined to [X]."));
        eq("a player's hoe talk", OptionalInt.empty(),
                Resurrection.chatHoe("[★1] MEMBER x: UPGRADE! Fire Coral Hoe VII: Fire Coral: +7"));

        List<String> sidebar = List.of("ᴍɪɴᴇʀᴇғɪɴᴇ", "Resurrection 5", "│ Carrots 45,395", "🎃 HALLOWEEN",
                "Next: Nether Wart Hoe II", "13,535/17,500 Nether Wart");
        eq("sidebar level", OptionalInt.of(5), Resurrection.sidebarLevel(sidebar));
        eq("sidebar level in small caps", OptionalInt.of(5), Resurrection.sidebarLevel(List.of("│ ʀᴇѕᴜʀʀᴇᴄᴛɪᴏɴ 5")));
        eq("sidebar hoe is the one before next", Resurrection.rank("Nether Wart", "I"),
                Resurrection.sidebarHoe(sidebar));
        eq("next carrot I: no hoe yet", OptionalInt.of(-1), Resurrection.sidebarHoe(List.of("Next: Carrot Hoe I")));
        eq("no row, no level", OptionalInt.empty(), Resurrection.sidebarLevel(List.of("Carrots 45,395")));
        eq("a row about something else", OptionalInt.empty(),
                Resurrection.sidebarLevel(List.of("Next Resurrection 87%")));
        eq("two rows that disagree", OptionalInt.empty(),
                Resurrection.sidebarLevel(List.of("Resurrection 5", "Resurrection 6")));
        check("the level row is no crop balance", !HalloweenText.sidebar(sidebar).balances().containsKey("Resurrection"));

        Optional<Resurrection.Card> card = Resurrection.card("Resurrection 6", CARD_6_LORE);
        check("card read", card.isPresent());
        Resurrection.Card c = card.orElseThrow();
        eq("card target", 6, c.target);
        eq("card crops", 350_000_000.0, c.crops);
        eq("card crops held", 303_808_740.0, c.have);
        eq("card hoe", "Fire Coral Hoe VII", c.hoe);
        eq("card hoe rank", 27, c.hoeRank);
        eq("card hoe ticked", Boolean.TRUE, c.hoeMet);
        eq("card multiplier", "x8 → x10", c.multiplier);
        eq("card unlocks", "Scarecrow", c.unlocks);
        check("small caps title", Resurrection.card("ʀᴇѕᴜʀʀᴇᴄᴛɪᴏɴ 6", CARD_6_LORE).isPresent());
        eq("cross on the hoe", Boolean.FALSE, Resurrection.card("Resurrection 6",
                List.of("⊢ Fire Coral Hoe VII ✘", "⊢ 350,000,000 Total Crops ✘ (1)")).orElseThrow().hoeMet);
        check("no cost, no card", Resurrection.card("Resurrection 6", List.of("Crop Multiplier: x8 → x10")).isEmpty());
        check("other items are not cards", Resurrection.card("Fire Coral Hoe VII", CARD_6_LORE).isEmpty());
        Resurrection.Card unknownHoe = Resurrection.card("Resurrection 7",
                List.of("⊢ Spooky Hoe III ✘", "⊢ 1,000,000,000 Total Crops ✘")).orElseThrow();
        eq("a hoe not in the list is still asked for", "Spooky Hoe III", unknownHoe.hoe);
        eq("its rank is not known", -1, unknownHoe.hoeRank);
    }

    /** Nothing but the server saying so moves the level, and saying it twice counts once. */
    private static void resurrectionNeverGhosts() {
        Resurrection.Saved s = new Resurrection.Saved();
        Resurrection.Steady level = new Resurrection.Steady();
        Resurrection.Steady hoe = new Resurrection.Steady();
        List<String> at5 = List.of("Resurrection 5", "Next: Hallowed Hoe I", "🎃 HALLOWEEN");

        // Joining: the first steady reading is taken, but not a single one.
        check("one read is not enough", level.read(Resurrection.sidebarLevel(at5)).isEmpty());
        OptionalInt first = level.read(Resurrection.sidebarLevel(at5));
        eq("two reads agree", OptionalInt.of(5), first);
        Resurrection.setLevel(s, first.getAsInt(), 1_000L);
        hoe.read(Resurrection.sidebarHoe(at5));
        Resurrection.setHoe(s, hoe.read(Resurrection.sidebarHoe(at5)).getAsInt(), 1_000L);
        eq("level from the sidebar", 5, s.level);
        eq("hoe from the sidebar", 27, s.hoeRank);
        check("a steady row says nothing new", level.read(Resurrection.sidebarLevel(at5)).isEmpty());

        // The menu: a card for the next one is kept, one for a past resurrection is not.
        check("card kept", Resurrection.learn(s, Resurrection.card("Resurrection 6", CARD_6_LORE).orElseThrow(), 2_000L));
        check("same card again is nothing new", !Resurrection.learn(s,
                Resurrection.card("Resurrection 6", CARD_6_LORE).orElseThrow(), 2_500L));
        check("past card ignored", !Resurrection.learn(s,
                Resurrection.card("Resurrection 5", CARD_6_LORE).orElseThrow(), 2_500L));
        eq("opening the menu changes no level", 5, s.level);

        // Spending crops on hoes, or crops dropping for any reason, is not a resurrection.
        eq("still aiming for 6 with crops", 6, Resurrection.view(s, 303_808_740.0, Double.NaN).orElseThrow().target());
        eq("still aiming for 6 after spending", 6, Resurrection.view(s, 5_000.0, Double.NaN).orElseThrow().target());
        eq("still at 5", 5, s.level);

        // Other players resurrecting: never ours.
        for (String other : List.of(" jee213 rose to Resurrection 6! ", " 81hp_ rose to Resurrection 6! ")) {
            Resurrection.chatLevel(other).ifPresent(n -> Resurrection.setLevel(s, n, 3_000L));
        }
        eq("others' news leaves us at 5", 5, s.level);

        // The real one: the chat line, then the sidebar catching up a moment later.
        check("resurrected", Resurrection.setLevel(s, Resurrection.chatLevel(
                "RESURRECTED! You are now Resurrection 6!").getAsInt(), 4_000L));
        eq("now 6", 6, s.level);
        eq("hoe back to carrot I", 0, s.hoeRank);
        check("the passed card is gone", s.cards.isEmpty());
        check("told again, nothing more", !Resurrection.setLevel(s, 6, 4_100L));
        eq("still 6", 6, s.level);

        // The sidebar a beat behind still says 5 (and the old hoe): it has not changed, so it is
        // not taken, and the level never drops back.
        for (int i = 0; i < 4; i++) {
            level.read(Resurrection.sidebarLevel(at5)).ifPresent(n -> Resurrection.setLevel(s, n, 4_200L));
            hoe.read(Resurrection.sidebarHoe(at5)).ifPresent(n -> Resurrection.setHoe(s, n, 4_200L));
        }
        eq("a lagging sidebar does not undo it", 6, s.level);
        eq("nor give the old hoe back", 0, s.hoeRank);

        List<String> at6 = List.of("Resurrection 6", "Next: Carrot Hoe II", "🎃 HALLOWEEN");
        for (int i = 0; i < 4; i++) {
            level.read(Resurrection.sidebarLevel(at6)).ifPresent(n -> Resurrection.setLevel(s, n, 4_500L));
            hoe.read(Resurrection.sidebarHoe(at6)).ifPresent(n -> Resurrection.setHoe(s, n, 4_500L));
        }
        eq("sidebar catching up is the same resurrection", 6, s.level);
        eq("carrot I held", 0, s.hoeRank);

        // A sidebar that flickers to a wrong number for one read is never taken.
        level.read(Resurrection.sidebarLevel(List.of("Resurrection 7"))).ifPresent(n -> Resurrection.setLevel(s, n, 5_000L));
        level.read(Resurrection.sidebarLevel(at6)).ifPresent(n -> Resurrection.setLevel(s, n, 5_500L));
        eq("one odd read is ignored", 6, s.level);

        // No card for 7 yet: the bar asks for the menu rather than guess.
        eq("next cost unknown", Resurrection.State.OPEN_MENU,
                Resurrection.view(s, 0, Double.NaN).orElseThrow().state());

        // After the last one there is no bar.
        Resurrection.setLevel(s, 7, 6_000L);
        check("hidden at 7", Resurrection.view(s, 0, Double.NaN).isEmpty());

        // Nothing known: no bar, and nothing guessed.
        check("nothing known, nothing shown", Resurrection.view(new Resurrection.Saved(), 1e9, Double.NaN).isEmpty());
        Resurrection.Saved fresh = new Resurrection.Saved();
        check("first level read is not a resurrection", Resurrection.setLevel(fresh, 5, 1L));
        check("so the hoe is not reset", fresh.hoeAt == 0L);
    }

    private static void resurrectionPanel() {
        Resurrection.Saved s = new Resurrection.Saved();
        Resurrection.setLevel(s, 5, 1_000L);
        Resurrection.learn(s, Resurrection.card("Resurrection 6", CARD_6_LORE).orElseThrow(), 2_000L);

        // 46,191,260 crops short at 23,095,630 an hour.
        Resurrection.View v = Resurrection.view(s, 303_808_740.0, 23_095_630.0).orElseThrow();
        eq("saving", Resurrection.State.SAVING, v.state());
        eq("hoe ticked from the menu", Boolean.TRUE, v.hoeMet());
        eq("two hours of farming", 7_200_000L, v.msLeft());
        List<HudModel.Line> lines = HalloweenHud.resurrectionLines(v);
        eq("title", "Resurrection 6  303.81m/350m", lines.get(0).text());
        eq("hoe line", "   Fire Coral Hoe VII ✔", lines.get(2).text());
        check("time left line", lines.stream().anyMatch(l -> l.text().equals("   ~2:00:00 of farming left")));
        check("multiplier line", lines.stream().anyMatch(l -> l.text().equals("   crops x8 → x10")));
        check("unlock line", lines.stream().anyMatch(l -> l.text().equals("   unlocks Scarecrow")));

        eq("enough crops and the hoe: ready", Resurrection.State.READY,
                Resurrection.view(s, 350_000_000.0, Double.NaN).orElseThrow().state());

        // The menu crossed the hoe; buying it afterwards ticks it without reopening the menu.
        Resurrection.Card crossed = Resurrection.card("Resurrection 6",
                List.of("⊢ Fire Coral Hoe VII ✘", "⊢ 350,000,000 Total Crops ✘ (1)")).orElseThrow();
        Resurrection.setHoe(s, Resurrection.rank("Fire Coral", "VI").getAsInt(), 2_500L);
        Resurrection.learn(s, crossed, 3_000L);
        eq("crossed in the menu", Boolean.FALSE, Resurrection.view(s, 4e8, Double.NaN).orElseThrow().hoeMet());
        eq("crops alone are not ready", Resurrection.State.SAVING,
                Resurrection.view(s, 4e8, Double.NaN).orElseThrow().state());
        Resurrection.setHoe(s, Resurrection.chatHoe("UPGRADE! Fire Coral Hoe VII: Fire Coral: +7").getAsInt(), 4_000L);
        eq("bought after", Boolean.TRUE, Resurrection.view(s, 4e8, Double.NaN).orElseThrow().hoeMet());
        eq("now ready", Resurrection.State.READY, Resurrection.view(s, 4e8, Double.NaN).orElseThrow().state());

        // A hoe the mod does not know: only the menu's tick can make it ready.
        Resurrection.Saved u = new Resurrection.Saved();
        Resurrection.setLevel(u, 6, 1L);
        Resurrection.learn(u, Resurrection.card("Resurrection 7",
                List.of("⊢ Spooky Hoe III ✘", "⊢ 1,000,000,000 Total Crops ✘")).orElseThrow(), 2L);
        Resurrection.setHoe(u, 34, 3L);
        eq("unknown hoe stays crossed", Resurrection.State.SAVING,
                Resurrection.view(u, 2e9, Double.NaN).orElseThrow().state());

        var stats = HalloweenHud.statsPanel(new HalloweenHud.Stats(Map.of("Carrots", 1.0), new Rates(), 600_000L, 0, 0,
                new RecurringEvent.View(RecurringEvent.State.UNKNOWN, 0L, false), 0, 0, List.of(),
                Resurrection.view(s, 303_808_740.0, Double.NaN)), HalloweenHud.StatsLines.all());
        check("panel has the section", stats.stream().anyMatch(l -> l.text().equals("RESURRECTION")));
        check("panel has the bar", stats.stream().anyMatch(l -> l.text().startsWith("Resurrection 6")));
        var off = HalloweenHud.statsPanel(new HalloweenHud.Stats(Map.of("Carrots", 1.0), new Rates(), 600_000L, 0, 0,
                new RecurringEvent.View(RecurringEvent.State.UNKNOWN, 0L, false), 0, 0, List.of(),
                Resurrection.view(s, 303_808_740.0, Double.NaN)),
                new HalloweenHud.StatsLines(true, true, true, true, true, true, true, false));
        check("switched off, no section", off.stream().noneMatch(l -> l.text().startsWith("Resurrection")));
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }

    private static void eq(String name, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual)
                || (expected instanceof Number e && actual instanceof Number a && e.doubleValue() == a.doubleValue());
        if (ok) {
            passed++;
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name + ": expected " + expected + ", got " + actual);
        }
    }
}
