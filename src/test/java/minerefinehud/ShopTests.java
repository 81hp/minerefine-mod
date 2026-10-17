package minerefinehud;

import minerefinehud.shop.Amounts;
import minerefinehud.shop.PriceLedger;
import minerefinehud.shop.RomanNumerals;
import minerefinehud.shop.ShopItemParser;

import java.util.List;
import java.util.Optional;

/** Tests for passive shop price learning, written against the real in-game tooltips. */
public final class ShopTests {

    private static int passed = 0;
    private static int failed = 0;

    // The two tooltips captured in game, transcribed exactly, decoration and all.
    private static final List<String> DEBRIS_I_LORE = List.of(
            "Efficiency: +175↗",
            "Fortune: +315❈",
            "Blast: +8☢",
            "",
            "[ 2 Empty Enchantment Slots ]",
            "",
            "RUINBOUND SHOVEL",
            "",
            "Cost:",
            "➢ [Suspicious Sand Shovel] [VI] x1 (0) ✗",
            "➢ Debris x1.96B (7,725,607,214) ✓",
            "",
            "CLICK to purchase!",
            "minecraft:netherite_shovel",
            "23 component(s)");

    private static final List<String> DEBRIS_VI_LORE = List.of(
            "Efficiency: +200↗",
            "Fortune: +340❈",
            "Blast: +9☢",
            "",
            "[ 2 Empty Enchantment Slots ]",
            "",
            "RUINBOUND SHOVEL",
            "",
            "Cost:",
            "➢ [Debris Shovel] [V] x1 (0) ✗",
            "➢ Debris x5B (0) ✗",
            "",
            "CLICK to purchase!");

    public static void main(String[] args) {
        amounts();
        romans();
        parsesRealTooltips();
        ignoresPrerequisiteLine();
        rejectsNonShopItems();
        ledgerPrefersObserved();
        ledgerDetectsNerf();
        ledgerRemainingCost();
        ledgerPersistence();
        readsCarriedGear();
        prerequisitesLinkMines();
        ledgerWholePieceTotals();
        tierCountsComeFromTheSheet();
        unreadableShopItemsAreCaught();
        charmsHaveOneTier();
        roundedBalancesStillParse();
        listingsLearnTierCounts();
        verifiedPricesReplaceOlderOnes();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void amounts() {
        eq("1.96B exact (no float drift)", 1_960_000_000L, Amounts.parse("1.96B").orElse(-1));
        eq("5B", 5_000_000_000L, Amounts.parse("5B").orElse(-1));
        eq("grouped digits", 7_725_607_214L, Amounts.parse("7,725,607,214").orElse(-1));
        eq("millions", 1_040_000L, Amounts.parse("1.04M").orElse(-1));
        eq("zero", 0L, Amounts.parse("0").orElse(-1));
        eq("lowercase suffix", 2_500_000_000L, Amounts.parse("2.5b").orElse(-1));
        yes("rubbish rejected", Amounts.parse("abc").isEmpty());
        yes("null rejected", Amounts.parse(null).isEmpty());
        eq("round trips", "1.96B", Amounts.format(1_960_000_000L));
        eq("formats whole", "5B", Amounts.format(5_000_000_000L));
    }

    private static void romans() {
        eq("I", 1, RomanNumerals.parse("I").orElse(-1));
        eq("IV", 4, RomanNumerals.parse("IV").orElse(-1));
        eq("VI", 6, RomanNumerals.parse("VI").orElse(-1));
        eq("X", 10, RomanNumerals.parse("X").orElse(-1));
        yes("malformed IIII rejected", RomanNumerals.parse("IIII").isEmpty());
        yes("non-roman rejected", RomanNumerals.parse("B2").isEmpty());
    }

    private static void parsesRealTooltips() {
        ShopItemParser.Entry one =
                ShopItemParser.parse("[Debris Shovel] [I]", DEBRIS_I_LORE).orElse(null);
        if (one == null) {
            fail("level I tooltip", "an entry", "nothing");
        } else {
            eq("level I mine", "Debris", one.mine());
            eq("level I gear", "shovel", one.gear());
            eq("level I level", 1, one.level());
            eq("level I currency", "Debris", one.currency());
            eq("level I price", 1_960_000_000L, one.price());
        }

        ShopItemParser.Entry six =
                ShopItemParser.parse("[Debris Shovel] [VI]", DEBRIS_VI_LORE).orElse(null);
        if (six == null) {
            fail("level VI tooltip", "an entry", "nothing");
        } else {
            eq("level VI level", 6, six.level());
            eq("level VI price", 5_000_000_000L, six.price());
        }

        // Multi-word mine names must not be eaten by the gear split.
        ShopItemParser.Entry multi = ShopItemParser.parse("[Suspicious Sand Shovel] [III]",
                List.of("Cost:", "Suspicious Sand x2.5B (0)")).orElse(null);
        if (multi == null) {
            fail("multi-word mine", "an entry", "nothing");
        } else {
            eq("multi-word mine name", "Suspicious Sand", multi.mine());
            eq("multi-word gear", "shovel", multi.gear());
        }
    }

    private static void ignoresPrerequisiteLine() {
        // The prerequisite row also carries "x1 (0)". Reading it as a price would record 1 block.
        ShopItemParser.Entry e =
                ShopItemParser.parse("[Debris Shovel] [VI]", DEBRIS_VI_LORE).orElse(null);
        yes("prerequisite not mistaken for a price", e != null && e.price() == 5_000_000_000L);
    }

    private static void rejectsNonShopItems() {
        yes("no level bracket", ShopItemParser.parse("[Debris Shovel]", DEBRIS_VI_LORE).isEmpty());
        yes("unknown gear word",
                ShopItemParser.parse("[Debris Banana] [I]", DEBRIS_VI_LORE).isEmpty());
        yes("no cost block",
                ShopItemParser.parse("[Debris Shovel] [I]", List.of("just a description")).isEmpty());
        yes("null title", ShopItemParser.parse(null, DEBRIS_VI_LORE).isEmpty());
    }

    private static void ledgerPrefersObserved() {
        PriceLedger ledger = new PriceLedger();
        ledger.seed("Debris", "shovel", 6, 7_390_000_000L, "Debris");   // stale sheet guess

        PriceLedger.Price before = ledger.price("Debris", "shovel", 6).orElseThrow();
        eq("prefill used when nothing observed", PriceLedger.Source.PREFILL, before.source());

        ledger.record(new ShopItemParser.Entry("Debris", "shovel", 6, "Debris", 5_000_000_000L), 1000L);
        PriceLedger.Price after = ledger.price("Debris", "shovel", 6).orElseThrow();
        eq("observed overrides prefill", PriceLedger.Source.OBSERVED, after.source());
        eq("observed value wins", 5_000_000_000L, after.amount());
    }

    private static void ledgerDetectsNerf() {
        PriceLedger ledger = new PriceLedger();
        var entry = new ShopItemParser.Entry("Debris", "shovel", 6, "Debris", 7_390_000_000L);
        eq("first sighting", PriceLedger.Result.NEW, ledger.record(entry, 1000L));
        eq("same price again", PriceLedger.Result.UNCHANGED, ledger.record(entry, 2000L));

        var nerfed = new ShopItemParser.Entry("Debris", "shovel", 6, "Debris", 5_000_000_000L);
        eq("price drop detected", PriceLedger.Result.CHANGED, ledger.record(nerfed, 3000L));

        List<PriceLedger.PriceChange> changes = ledger.drainChanges();
        eq("one change reported", 1, changes.size());
        eq("change from", 7_390_000_000L, changes.get(0).from());
        eq("change to", 5_000_000_000L, changes.get(0).to());
        eq("drained once", 0, ledger.drainChanges().size());
    }

    private static void ledgerRemainingCost() {
        PriceLedger ledger = new PriceLedger();
        long[] ladder = { 1_960_000_000L, 2_568_000_000L, 3_176_000_000L,
                          3_784_000_000L, 4_392_000_000L, 5_000_000_000L };
        for (int i = 0; i < ladder.length; i++) {
            ledger.record(new ShopItemParser.Entry("Debris", "shovel", i + 1, "Debris", ladder[i]), 1L);
        }
        eq("remaining from level 4", 4_392_000_000L + 5_000_000_000L,
                ledger.remainingCost("Debris", "shovel", 4, 6).orElse(-1));
        eq("all six seen", 6, ledger.observedLevels("Debris", "shovel", 6));

        PriceLedger gappy = new PriceLedger();
        gappy.record(new ShopItemParser.Entry("Debris", "shovel", 6, "Debris", 5_000_000_000L), 1L);
        yes("partial ladder gives no total, rather than a wrong one",
                gappy.remainingCost("Debris", "shovel", 3, 6).isEmpty());
    }

    private static void ledgerPersistence() {
        PriceLedger a = new PriceLedger();
        a.record(new ShopItemParser.Entry("Debris", "shovel", 1, "Debris", 1_960_000_000L), 50L);
        PriceLedger b = new PriceLedger();
        b.importAll(a.export());
        PriceLedger.Price p = b.price("Debris", "shovel", 1).orElseThrow();
        eq("survives a restart", 1_960_000_000L, p.amount());
        eq("still counts as observed", PriceLedger.Source.OBSERVED, p.source());
    }

    private static void readsCarriedGear() {
        // Gear in the inventory is named like the shop entries but has no cost block, so the
        // level the player already holds can be read straight off the item in hand.
        ShopItemParser.GearRef ref = ShopItemParser.parseTitle("[Debris Shovel] [IV]").orElse(null);
        if (ref == null) {
            fail("carried gear", "a reference", "nothing");
        } else {
            eq("carried mine", "Debris", ref.mine());
            eq("carried gear slot", "shovel", ref.gear());
            eq("carried level", 4, ref.level());
        }
        yes("plain item ignored", ShopItemParser.parseTitle("Netherite Shovel").isEmpty());

        // The whole point: knowing the held level makes the next price answerable.
        PriceLedger ledger = new PriceLedger();
        ledger.record(new ShopItemParser.Entry("Debris", "shovel", 5, "Debris", 4_392_000_000L), 1L);
        eq("next upgrade from the item in hand", 4_392_000_000L,
                ledger.nextUpgrade(ref.mine(), ref.gear(), ref.level()).orElseThrow().amount());
    }

    // ---------------------------------------------------------------- harness

    private static void prerequisitesLinkMines() {
        List<ShopItemParser.Link> links = ShopItemParser.links(List.of(
                new ShopItemParser.ItemView("[Debris Shovel] [I]", DEBRIS_I_LORE),
                new ShopItemParser.ItemView("[Debris Shovel] [VI]", DEBRIS_VI_LORE)));
        eq("tier I names the mine before it, tier VI only its own tier V", 1, links.size());
        eq("link mine", "Debris", links.get(0).mine());
        eq("link gear", "shovel", links.get(0).gear());
        eq("previous mine, multi-word", "Suspicious Sand", links.get(0).previousMine());

        yes("an item with no cost block links nothing", ShopItemParser.links(List.of(
                new ShopItemParser.ItemView("[Debris Shovel] [I]", List.of("RUINBOUND SHOVEL")))).isEmpty());
    }

    private static void ledgerWholePieceTotals() {
        PriceLedger ledger = new PriceLedger();
        for (int level = 1; level <= 5; level++) {
            ledger.record(new ShopItemParser.Entry("Rafter", "axe", level, "Rafter", 1_000L * level), 1L);
        }
        yes("with a sheet saying there is more, five tiers is no total",
                ledger.observedTotal("Rafter", "axe", java.util.OptionalLong.of(21_000L)).isEmpty());
        eq("without a sheet, the shop's highest tier is the last", 15_000L,
                ledger.observedTotal("Rafter", "axe").orElse(-1));
        ledger.record(new ShopItemParser.Entry("Rafter", "axe", 6, "Rafter", 6_000L), 1L);
        eq("all six summed", 21_000L, ledger.observedTotal("Rafter", "axe").orElse(-1));

        ledger.seed("Rafter", "sword", 1, 5L, "Rafter");
        yes("seeded tiers never count as seen in the shop", ledger.observedTotal("Rafter", "sword").isEmpty());

        ledger.record(new ShopItemParser.Entry("Rafter", "charm", 2, "Rafter", 9L), 1L);
        eq("gears seen at the mine", java.util.Set.of("axe", "charm"), ledger.observedGears("Rafter"));
        yes("other mines not mixed in", ledger.observedGears("Relic").isEmpty());
    }

    /** Records tiers from {@code first} upwards for one item. */
    private static PriceLedger ladder(String mine, String gear, int first, long... amounts) {
        PriceLedger ledger = new PriceLedger();
        for (int i = 0; i < amounts.length; i++) {
            ledger.record(new ShopItemParser.Entry(mine, gear, first + i, mine, amounts[i]), 1L);
        }
        return ledger;
    }

    private static void unreadableShopItemsAreCaught() {
        List<String> cost = List.of("Cost:", "➢ Ruby x120M (0) ✗");
        eq("numeral inside the name still parses", 3,
                ShopItemParser.parse("[Ruby Charm III]", cost).map(ShopItemParser.Entry::level).orElse(-1));
        eq("and without brackets", "charm",
                ShopItemParser.parse("Ruby Charm II", cost).map(ShopItemParser.Entry::gear).orElse(""));
        yes("not without a gear word before the numeral",
                ShopItemParser.parseTitle("Khan's Spoil III").isEmpty());
        eq("the usual form is unchanged", 6,
                ShopItemParser.parseTitle("[Debris Shovel] [VI]").map(ShopItemParser.GearRef::level).orElse(-1));

        List<ShopItemParser.ItemView> screen = List.of(
                new ShopItemParser.ItemView("[Debris Shovel] [I]", DEBRIS_I_LORE),
                new ShopItemParser.ItemView("[Ruby Chestplate] [?]", cost),
                new ShopItemParser.ItemView("Darkwater Palace", List.of("Cost:", "1T Shards")),
                new ShopItemParser.ItemView("Back", List.of("Return to the menu")));
        List<ShopItemParser.ItemView> unknown = ShopItemParser.unrecognised(screen);
        eq("only gear with a cost that could not be read is caught", 1, unknown.size());
        eq("which one", "[Ruby Chestplate] [?]", unknown.get(0).title());
    }

    /** Transcribed from the game: a charm has no tier, and names the previous charm without brackets. */
    private static final List<String> HONEYSTONE_CHARM_LORE = List.of(
            "",
            "When in Off Hand:",
            " Health: +190\u200C❤",
            " Fortune: +44%\u200C\u200C☘",
            " Speed: +50\u200C✦",
            "",
            "[ 2 Empty Enchantment Slots ]",
            "",
            "ᴡᴏᴏᴅʟᴀɴᴅɪᴄ ᴏꜰꜰʜᴀɴᴅ",
            "                         ",
            "Cost: ",
            "➼ Vase Charm x1 (0) ✗",
            "➼ Honeystone x996.8M (1,083,387,296) ✔",
            "",
            "CLICK to purchase!");

    private static void charmsHaveOneTier() {
        ShopItemParser.Entry charm = ShopItemParser.parse("Honeystone Charm", HONEYSTONE_CHARM_LORE).orElse(null);
        if (charm == null) {
            fail("charm parses", "an entry", "nothing");
            return;
        }
        eq("charm mine", "Honeystone", charm.mine());
        eq("charm is tier I", 1, charm.level());
        eq("priced in the resource, not the charm it requires", "Honeystone", charm.currency());
        eq("the real price", 996_800_000L, charm.price());

        var balances = ShopItemParser.balances(List.of(new ShopItemParser.ItemView("Honeystone Charm", HONEYSTONE_CHARM_LORE)));
        yes("the required charm is not a balance", !balances.containsKey("Vase Charm"));

        List<ShopItemParser.Link> links = ShopItemParser.links(
                List.of(new ShopItemParser.ItemView("Honeystone Charm", HONEYSTONE_CHARM_LORE)));
        eq("the required charm places the mine", "Vase", links.isEmpty() ? "" : links.get(0).previousMine());

        eq("a charm in the off hand reads as tier I", 1,
                ShopItemParser.parseTitle("Honeystone Charm").map(ShopItemParser.GearRef::level).orElse(-1));
        yes("other untiered names are not gear", ShopItemParser.parseTitle("Watcher Slayer").isEmpty());

        PriceLedger ledger = new PriceLedger();
        ledger.record(charm, 1L);
        eq("one tier, so it is complete", 996_800_000L,
                ledger.observedTotal("Honeystone", "charm", java.util.OptionalLong.of(996_800_000L)).orElse(-1));
        eq("a charm has one tier even with no sheet", 1, ledger.maxLevel("Honeystone", "charm"));
    }

    private static void tierCountsComeFromTheSheet() {
        // All real figures from the game and the spreadsheet.
        var relicChest = java.util.OptionalLong.of(43_220_000_000L);
        PriceLedger fourTiers = ladder("Relic", "chestplate", 1,
                6_180_000_000L, 9_570_000_000L, 12_970_000_000L, 14_500_000_000L);
        eq("armour with four tiers is complete at IV", 4, fourTiers.maxLevel("Relic", "chestplate", relicChest));
        eq("so its shop total exists", 43_220_000_000L,
                fourTiers.observedTotal("Relic", "chestplate", relicChest).orElse(-1));
        eq("without the sheet it still assumes six", 6, fourTiers.maxLevel("Relic", "chestplate"));

        // Ocean armour has three tiers. Tier I was never seen, yet III is still provably the last.
        PriceLedger gap = ladder("Dark Prismarine", "chestplate", 2, 38_900_000L, 56_400_000L);
        eq("a gap below does not hide the last tier", 3,
                gap.maxLevel("Dark Prismarine", "chestplate", java.util.OptionalLong.of(118_962_000L)));

        PriceLedger partial = ladder("Relic", "chestplate", 1, 6_180_000_000L, 9_570_000_000L);
        eq("two of four seen: at most four fit in what is left", 4,
                partial.maxLevel("Relic", "chestplate", relicChest));

        PriceLedger five = ladder("Rubble", "sword", 1,
                919_400_000L, 1_380_000_000L, 1_790_000_000L, 2_060_000_000L, 2_230_000_000L);
        eq("a five-tier sword", 5, five.maxLevel("Rubble", "sword", java.util.OptionalLong.of(8_379_400_000L)));

        PriceLedger seven = ladder("Rafter", "axe", 1, 2_860_000_000L, 4_300_000_000L, 5_580_000_000L,
                6_420_000_000L, 6_940_000_000L, 7_280_000_000L, 7_500_000_000L);
        eq("a seven-tier axe", 7, seven.maxLevel("Rafter", "axe", java.util.OptionalLong.of(40_880_000_000L)));

        PriceLedger sixOfSix = ladder("Relic", "pickaxe", 5, 6_560_000_000L, 6_880_000_000L);
        eq("top tiers only, rest missing below: still six", 6,
                sixOfSix.maxLevel("Relic", "pickaxe", java.util.OptionalLong.of(31_560_000_000L)));
    }

    // October 2026: the balance in brackets is rounded too, and every such item went unread.
    private static final List<String> GLACITE_III_LORE = List.of(
            "",
            " Protection: +568‌⛨",
            " Fortune: +26%‌‌☘",
            "",
            "[ 2 Empty Enchantment Slots ]",
            "",
            "FROSTED CHESTPLATE",
            "                         ",
            "Cost: ",
            "➼ [Glacite Chestplate] [II] x1 (0) ✗",
            "➼ Glacite x98.02M (637M) ✔",
            "",
            "CLICK to purchase!");

    private static void roundedBalancesStillParse() {
        Optional<ShopItemParser.Entry> e = ShopItemParser.parse("[Glacite Chestplate] [III]", GLACITE_III_LORE);
        yes("an item with a rounded balance is read", e.isPresent());
        e.ifPresent(x -> {
            eq("its tier", 3, x.level());
            eq("its price", 98_020_000L, x.price());
            eq("its currency", "Glacite", x.currency());
        });
        List<ShopItemParser.ItemView> view = List.of(new ShopItemParser.ItemView("[Glacite Chestplate] [III]", GLACITE_III_LORE));
        yes("no longer reported as unreadable", ShopItemParser.unrecognised(view).isEmpty());
        yes("a rounded balance is not an exact one", ShopItemParser.balances(view).isEmpty());
        ShopItemParser.Balance b = ShopItemParser.balanceReadings(view).get("Glacite");
        yes("but it is read", b != null);
        if (b != null) {
            eq("rounded amount", 637_000_000L, b.amount());
            eq("to the million", 1_000_000L, b.precision());
            yes("an exact balance it rounds from agrees", b.agreesWith(636_612_345L));
            yes("one it cannot round from does not", !b.agreesWith(638_100_000L));
        }
        eq("precision of 17.25M", 10_000L, Amounts.precision("17.25M"));
        eq("precision of an exact figure", 1L, Amounts.precision("7,725,607,214"));
        eq("precision of 5B", 1_000_000_000L, Amounts.precision("5B"));
        eq("precision of nonsense", 0L, Amounts.precision("abc"));
    }

    private static ShopItemParser.Entry entry(String mine, String gear, int level, long price) {
        return new ShopItemParser.Entry(mine, gear, level, mine, price);
    }

    /** Pine armour lost tier IV; the old tier IV price kept the bar on a Pine Chestplate IV. */
    private static void listingsLearnTierCounts() {
        PriceLedger ledger = new PriceLedger();
        ledger.knowTierCount("Pine", "chestplate", 4);
        for (int l = 1; l <= 4; l++) {
            ledger.record(entry("Pine", "chestplate", l, l * 10L), 1_000L);
        }
        eq("before: four tiers", 4, ledger.maxLevel("Pine", "chestplate"));

        List<ShopItemParser.Entry> listing = List.of(entry("Pine", "chestplate", 1, 17L),
                entry("Pine", "chestplate", 2, 29L), entry("Pine", "chestplate", 3, 43L),
                entry("Pine", "helmet", 3, 26L));
        listing.forEach(x -> ledger.record(x, 2_000L));
        yes("a listing of I to III is learned", ledger.listingsSeen(listing, 2_000L));
        eq("now three tiers, over the table's four", 3, ledger.maxLevel("Pine", "chestplate"));
        yes("the old tier IV price is gone", ledger.price("Pine", "chestplate", 4).isEmpty());
        yes("seen again, nothing changes", !ledger.listingsSeen(listing, 2_500L));
        yes("one tier on its own teaches nothing", !ledger.exportLearnedTiers().containsKey("pine|helmet"));
        eq("saved", 3, ledger.exportLearnedTiers().get("pine|chestplate").tiers);

        PriceLedger reloaded = new PriceLedger();
        reloaded.knowTierCount("Pine", "chestplate", 4);
        reloaded.importLearnedTiers(ledger.exportLearnedTiers());
        eq("still three after a restart", 3, reloaded.maxLevel("Pine", "chestplate"));
        yes("and counted as known", reloaded.knowsTierCount("Pine", "chestplate"));
    }

    private static void verifiedPricesReplaceOlderOnes() {
        PriceLedger ledger = new PriceLedger();
        // The table ships with the prices, updated from the same shop visit.
        ledger.knowTierCount("Glacite", "chestplate", 3);
        for (int l = 1; l <= 4; l++) {
            ledger.record(entry("Glacite", "chestplate", l, l * 10L), 1_000L);
        }
        ledger.record(entry("Glacite", "helmet", 1, 5L), 9_000L);
        ledger.importVerified(List.of(
                new PriceLedger.Persisted("glacite|chestplate|1", 40_970_000L, "Glacite", 5_000L),
                new PriceLedger.Persisted("glacite|chestplate|2", 67_610_000L, "Glacite", 5_000L),
                new PriceLedger.Persisted("glacite|chestplate|3", 98_020_000L, "Glacite", 5_000L),
                new PriceLedger.Persisted("glacite|helmet|1", 26_330_000L, "Glacite", 5_000L)));
        eq("an older price is replaced", 40_970_000L, ledger.price("Glacite", "chestplate", 1).get().amount());
        yes("an older tier above the last is dropped", ledger.price("Glacite", "chestplate", 4).isEmpty());
        eq("so the ladder ends at III", 3, ledger.maxLevel("Glacite", "chestplate"));
        eq("a price seen since stays", 5L, ledger.price("Glacite", "helmet", 1).get().amount());

        // A listing since showed two tiers: the shipped tier III must not come back on a restart.
        PriceLedger later = new PriceLedger();
        later.knowTierCount("Glacite", "chestplate", 3);
        later.importLearnedTiers(java.util.Map.of("glacite|chestplate", new PriceLedger.Learned(2, 9_000L)));
        later.importVerified(List.of(
                new PriceLedger.Persisted("glacite|chestplate|2", 67_610_000L, "Glacite", 5_000L),
                new PriceLedger.Persisted("glacite|chestplate|3", 98_020_000L, "Glacite", 5_000L)));
        eq("a newer listing's count stands", 2, later.maxLevel("Glacite", "chestplate"));
        yes("its dropped tier stays dropped", later.price("Glacite", "chestplate", 3).isEmpty());

        // A count learned before the shipped prices were read gives way to them.
        PriceLedger older = new PriceLedger();
        older.knowTierCount("Glacite", "chestplate", 3);
        older.importLearnedTiers(java.util.Map.of("glacite|chestplate", new PriceLedger.Learned(4, 1_000L)));
        older.importVerified(List.of(
                new PriceLedger.Persisted("glacite|chestplate|3", 98_020_000L, "Glacite", 5_000L)));
        eq("an older learned count gives way", 3, older.maxLevel("Glacite", "chestplate"));
    }

    private static void eq(String what, Object expected, Object actual) {
        if (expected.equals(actual)) {
            pass(what);
        } else {
            fail(what, String.valueOf(expected), String.valueOf(actual));
        }
    }

    private static void yes(String what, boolean condition) {
        if (condition) pass(what); else fail(what, "true", "false");
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
