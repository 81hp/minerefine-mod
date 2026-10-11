package minerefinehud;

import minerefinehud.boss.BossAlerts;
import minerefinehud.boss.BossIndex;
import minerefinehud.boss.BossTracker;
import minerefinehud.halloween.Abilities;
import minerefinehud.halloween.HalloweenHud;
import minerefinehud.halloween.HalloweenText;
import minerefinehud.halloween.PumpkinSet;
import minerefinehud.halloween.Rates;
import minerefinehud.halloween.RecurringEvent;
import minerefinehud.hud.HudModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        bossIndexFixesTimers();
        panelsRender();

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
                50.0, 1), HalloweenHud.StatsLines.all());
        check("candy line", stats.stream().anyMatch(l -> l.text().equals("Candy 21.5  …/h")));
        check("pest line", stats.stream().anyMatch(l -> l.text().equals("Pests: +2.1 candy, 1 charm")));
        check("caravan estimate", stats.stream().anyMatch(l -> l.text().equals("Caravan in ~10:00")));
        check("refine line", stats.stream().anyMatch(l -> l.text().equals("Refine II: 21.5/50 candy")));

        var abilities = HalloweenHud.abilityPanel(List.of(new Abilities.Timer("Will-o'-Wisps", Abilities.Key.DROP, 0L),
                new Abilities.Timer("Harvest Moon", Abilities.Key.SHIFT_DROP, 12_300L)));
        eq("ready", "Will-o'-Wisps  READY", abilities.get(0).text());
        eq("cooling", "Harvest Moon  12.3s", abilities.get(1).text());
    }

    // ---------------------------------------------------------------- helpers

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
