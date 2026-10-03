package minerefinehud;

import minerefinehud.hud.HudModel;
import minerefinehud.mine.Mine;
import minerefinehud.mine.MineCatalog;
import minerefinehud.mine.MineDetector;
import minerefinehud.mine.MiningBlocks;
import minerefinehud.progress.ActionBarReader;
import minerefinehud.progress.MineCosts;
import minerefinehud.progress.ProgressPlanner;
import minerefinehud.progress.ProgressPlanner.State;
import minerefinehud.progress.ProgressSlot;
import minerefinehud.progress.ProgressionLinks;
import minerefinehud.progress.ResourceBalances;
import minerefinehud.shop.PriceLedger;
import minerefinehud.shop.ShopItemParser;
import minerefinehud.shop.ShopItemParser.GearRef;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Tests for the progress bar: which upgrade it follows, and when it says finished. */
public final class ProgressTests {

    private static int passed = 0;
    private static int failed = 0;

    // A slice of the real Ruins order. Marrow sits between the two shovel mines, which is what
    // proves the shovel chain skips it, exactly as the server's own prerequisite line does.
    private static final MineCatalog CATALOG = new MineCatalog(List.of(
            mine("Rusty", "pickaxe"),
            mine("Suspicious Sand", "shovel"),
            mine("Marrow", "pickaxe"),
            mine("Debris", "shovel"),
            mine("Relic", "pickaxe")));

    public static void main(String[] args) {
        balancesReadFromShop();
        tracksTheNextTier();
        saysFinishedWhenAffordable();
        advancesAfterBuying();
        movesToNextMineOfSameTool();
        nonToolsUseEveryMine();
        stopsAtTheEnd();
        startsFromCurrentMineWithNothingOwned();
        prefersTheMostAdvancedItem();
        serverSpellingStillPrices();
        extraTiersFromTheShopAreHonoured();
        panelTextMatchesTheDesign();
        actionBarReading();
        miningBlocksLearnAndMatch();
        learnedLinksReachNewMines();
        learnedLinksRankNewMinesLast();
        bossGearSitsBetweenWorlds();
        learnedLinkLoopsDoNotHang();
        mineWithTwoTools();
        mineCostsFromSheet();
        mineCostsPreferCompleteShopLadders();
        mineCostsForANewDimension();
        minePanelShowsTotal();
        quantityMultipliesTheCost();
        severalBarsStack();
        mineRowsCanBeHidden();
        maxedThreeTierArmourMovesOn();
        newAreaWithoutSpreadsheet();
        barCanTrackEverythingLeftToMax();
        mineTotalProgressBar();

        System.out.println();
        System.out.println("passed: " + passed + "   failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void balancesReadFromShop() {
        List<String> lore = List.of(
                "Cost:",
                "➢ [Suspicious Sand Shovel] [VI] x1 (0) ✗",
                "➢ Debris x1.96B (7,725,607,214) ✓",
                "",
                "CLICK to purchase!");
        Map<String, Long> b = ShopItemParser.balances(
                List.of(new ShopItemParser.ItemView("[Debris Shovel] [I]", lore)));
        eq("balance read from the bracket", 7_725_607_214L, b.get("Debris"));
        yes("prerequisite count is not a balance", !b.containsKey("Suspicious Sand Shovel"));
        eq("only one currency", 1, b.size());
    }

    private static void tracksTheNextTier() {
        var v = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 3)),
                Map.of("Debris|shovel|4", 4_390_000_000L), 3_100_000_000L);
        eq("target is one above owned", 4, v.targetLevel());
        eq("tracking", State.TRACKING, v.state());
        yes("bar fill matches have/cost", Math.abs(v.fraction() - 3.1 / 4.39) < 1e-9);
    }

    private static void saysFinishedWhenAffordable() {
        var v = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 3)),
                Map.of("Debris|shovel|4", 4_390_000_000L), 5_000_000_000L);
        eq("finished once affordable", State.FINISHED, v.state());
        eq("finished bar is full", 1.0, v.fraction());
    }

    private static void advancesAfterBuying() {
        var v = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 4)),
                Map.of("Debris|shovel|4", 4_390_000_000L), 5_000_000_000L);
        eq("buying a tier moves the target up", 5, v.targetLevel());
        eq("unseen price says so instead of guessing", State.PRICE_UNKNOWN, v.state());
    }

    private static void movesToNextMineOfSameTool() {
        var v = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Suspicious Sand", "shovel", 6)),
                Map.of(), -1L);
        eq("maxed shovel moves to the next shovel mine, skipping Marrow", "Debris", v.mine());
        eq("and starts at tier I", 1, v.targetLevel());
    }

    private static void nonToolsUseEveryMine() {
        var v = plan(ProgressSlot.SWORD, List.of(new GearRef("Suspicious Sand", "sword", 6)),
                Map.of(), -1L);
        eq("a sword moves to the very next mine", "Marrow", v.mine());
    }

    private static void stopsAtTheEnd() {
        var v = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 6)), Map.of(), -1L);
        eq("last shovel mine maxed is the end", State.ALL_MAXED, v.state());
    }

    private static void startsFromCurrentMineWithNothingOwned() {
        var atMarrow = ProgressPlanner.plan(ProgressSlot.SHOVEL, List.of(), CATALOG,
                CATALOG.byName("Marrow"), new PriceLedger(), new ResourceBalances());
        eq("no shovel owned at a pickaxe mine: the next shovel mine", "Debris", atMarrow.mine());
        eq("from tier I", 1, atMarrow.targetLevel());

        var nowhere = ProgressPlanner.plan(ProgressSlot.SWORD, List.of(), CATALOG,
                Optional.empty(), new PriceLedger(), new ResourceBalances());
        eq("nothing to go on", State.NO_MINE, nowhere.state());
    }

    private static void prefersTheMostAdvancedItem() {
        // An old maxed shovel still in the inventory must not drag the bar back a mine.
        var v = plan(ProgressSlot.SHOVEL, List.of(
                        new GearRef("Suspicious Sand", "shovel", 6),
                        new GearRef("Debris", "shovel", 2)),
                Map.of(), -1L);
        eq("latest mine wins", "Debris", v.mine());
        eq("its tier", 3, v.targetLevel());
    }

    private static void serverSpellingStillPrices() {
        // Item and shop say "Rust", the spreadsheet says "Rusty".
        var v = plan(ProgressSlot.PICKAXE, List.of(new GearRef("Rust", "pickaxe", 2)),
                Map.of("Rust|pickaxe|3", 900_000_000L), -1L);
        eq("server spelling kept for lookups", "Rust", v.mine());
        eq("so the shop price is found", State.TRACKING, v.state());
    }

    private static void extraTiersFromTheShopAreHonoured() {
        var v = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 6)),
                Map.of("Debris|shovel|7", 9_000_000_000L), -1L);
        eq("a seventh tier seen in the shop is not skipped", 7, v.targetLevel());
        eq("still at Debris", "Debris", v.mine());
    }

    private static void panelTextMatchesTheDesign() {
        var tracking = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 3)),
                Map.of("Debris|shovel|4", 4_390_000_000L), 3_100_000_000L);
        List<HudModel.Line> lines = HudModel.progressPanel(Optional.of(tracking));
        eq("piece then have/needed", "Debris Shovel IV  3.1b/4.39b", lines.get(0).text());
        eq("bar underneath", HudModel.Style.BAR, lines.get(1).style());

        var done = plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 3)),
                Map.of("Debris|shovel|4", 4_390_000_000L), 5_020_000_000L);
        eq("finished then what is held", "Debris Shovel IV  finished  5.02b",
                HudModel.progressPanel(Optional.of(done)).get(0).text());

        yes("off draws nothing", HudModel.progressPanel(Optional.empty()).isEmpty());
    }

    private static void actionBarReading() {
        // Exactly as /mrhud debug printed them in game.
        String mining = "+140,732 EXP   +34.20 Coins   +0.135 Tokens  |  1.55B[block/cobblestone]";
        String totalOnly = "1.57B[block/cobblestone]";
        String idle = "267.1/267.1 ❤        24,290 ⛨        1,885 ⛏";

        eq("the total, not a gain", 1_550_000_000L, ActionBarReader.read(mining).orElseThrow().amount());
        eq("the block being mined", "block/cobblestone", ActionBarReader.read(mining).orElseThrow().sprite());
        eq("total on its own", 1_570_000_000L, ActionBarReader.read(totalOnly).orElseThrow().amount());
        yes("the idle stats line is not a balance", ActionBarReader.read(idle).isEmpty());
        eq("colour codes ignored", 1_550_000_000L,
                ActionBarReader.read("§b+1 EXP §7| §f1.55B[block/cobblestone]").orElseThrow().amount());
        eq("atlas suffix tolerated", 950L,
                ActionBarReader.read("+1 EXP | 950[block/sponge@blocks]").orElseThrow().amount());

        // On screen these all look like "773.16M[block/cobblestone]".
        for (String gap : List.of(" ", "​", "", "", "  ")) {
            eq("hidden spacing U+" + Integer.toHexString(gap.codePointAt(0)).toUpperCase()
                            + " between total and block", 773_160_000L,
                    ActionBarReader.read("+246,378 EXP   +58.90 Coins   +0.233 Tokens  |  773.16M"
                            + gap + "[block/cobblestone]").orElseThrow().amount());
        }
        yes("a gain is never joined to the block",
                ActionBarReader.read("+0.233 Tokens[block/cobblestone]").isEmpty());

        yes("gains alone are not a balance",
                ActionBarReader.read("+734.5 EXP  +2.04 Coins  +0.003 Tokens").isEmpty());
        yes("a tool name ending in a number is not a balance",
                ActionBarReader.read("Khan's Spoil 3[item/book@items]").isEmpty());

        // Exactly as logged at Zircon, whose icon is an item: invisible padding and all.
        var zircon = ActionBarReader.read("881.71M‌‌[item/iron_ingot@items]");
        eq("an item icon still carries the balance", 881_710_000L,
                zircon.map(ActionBarReader.Reading::amount).orElse(-1L));
        eq("and names the sprite", "item/iron_ingot",
                zircon.map(ActionBarReader.Reading::sprite).orElse(""));
        eq("after the gains separator too", 881_710_000L,
                ActionBarReader.read("+140 EXP  +0.2 Coins | 881.71M[item/iron_ingot@items]")
                        .map(ActionBarReader.Reading::amount).orElse(-1L));
        yes("the idle stats line is still not a balance",
                ActionBarReader.read("331.5/331.5         24,730 ⛨        25 ").isEmpty());
        yes("nothing to read", ActionBarReader.read("").isEmpty());

        eq("the mined block's mine comes first", "Rubble",
                ActionBarReader.currencyFor(Optional.of("Rubble"), Optional.of("Wind")).orElseThrow());
        eq("then a resource picked up with this block", "Wind",
                ActionBarReader.currencyFor(Optional.empty(), Optional.of("Wind")).orElseThrow());
        yes("an unknown block alone files the balance nowhere",
                ActionBarReader.currencyFor(Optional.empty(), Optional.empty()).isEmpty());

        // At Wind, block not learned yet, Lodestone remembered from before.
        eq("an unknown block drops the old mine", Optional.empty(),
                MineDetector.afterMining(Optional.of("Lodestone"), Optional.empty(), false));
        eq("a known block names its mine", Optional.of("Wind"),
                MineDetector.afterMining(Optional.of("Lodestone"), Optional.of("Wind"), false));
        eq("a pickup with the block keeps what the pickup said", Optional.of("Wind"),
                MineDetector.afterMining(Optional.of("Wind"), Optional.empty(), true));
    }

    private static void miningBlocksLearnAndMatch() {
        MineCatalog catalog = new MineCatalog(List.of(
                mine("Rumble", "pickaxe"), mine("Suspicious Sand", "shovel"), mine("Amethyst", "pickaxe")));

        MiningBlocks blocks = new MiningBlocks();
        yes("cobblestone is not obviously any mine",
                blocks.lookup("block/cobblestone", catalog).isEmpty());

        eq("an obvious name matches without a shop", "Suspicious Sand",
                blocks.lookup("block/suspicious_sand", catalog).orElseThrow().mine());
        eq("_block suffix ignored", "Amethyst",
                blocks.lookup("block/amethyst_block", catalog).orElseThrow().mine());
        eq("by name, not learned", MiningBlocks.How.BY_NAME,
                blocks.lookup("block/amethyst_block", catalog).orElseThrow().how());

        // Mine first, then open the shop: the exact balance confirms the rounded one.
        blocks.onMining("block/cobblestone", 1_570_000_000L, 1_000L);
        yes("shop balance matching the mining total teaches the block",
                blocks.onShopBalance("Rubble", 1_571_204_331L, 30_000L));
        eq("cobblestone is now Rubble", "Rubble",
                blocks.lookup("block/cobblestone", catalog).orElseThrow().mine());
        eq("learned", MiningBlocks.How.LEARNED,
                blocks.lookup("block/cobblestone", catalog).orElseThrow().how());

        // Shop first, then mine: works the other way round too.
        MiningBlocks other = new MiningBlocks();
        other.onShopBalance("Rubble", 1_571_204_331L, 1_000L);
        yes("mining after the shop also teaches", other.onMining("block/cobblestone", 1_570_000_000L, 5_000L));

        // Nothing learned from a balance that does not match.
        MiningBlocks wrong = new MiningBlocks();
        wrong.onShopBalance("Rubble", 1_571_204_331L, 1_000L);
        yes("a different total teaches nothing", !wrong.onMining("block/stone", 406_800_000L, 2_000L));

        // Two currencies that both fit: trust neither.
        MiningBlocks tie = new MiningBlocks();
        tie.onShopBalance("Rubble", 1_571_000_000L, 1_000L);
        tie.onShopBalance("Marrow", 1_569_000_000L, 1_000L);
        yes("ambiguous match teaches nothing", !tie.onMining("block/cobblestone", 1_570_000_000L, 2_000L));

        // Too long apart to be the same moment.
        MiningBlocks stale = new MiningBlocks();
        stale.onShopBalance("Rubble", 1_571_204_331L, 0L);
        yes("a shop visit an hour ago teaches nothing",
                !stale.onMining("block/cobblestone", 1_570_000_000L, 3_600_000L));

        // As it went in game: mine Zircon, teleport to Debris, open its shop a few minutes later.
        // The Debris balance happened to sit within 1% of the Zircon total.
        MiningBlocks trip = new MiningBlocks();
        trip.teach("item/iron_ingot", "Zircon");
        trip.onMining("item/iron_ingot", 1_350_000_000L, 0L);
        yes("a shop opened minutes after mining elsewhere teaches nothing",
                !trip.onShopBalance("Debris", 1_348_000_000L, 4 * 60_000L));
        eq("Zircon's icon stays Zircon", "Zircon", trip.learned().get("item/iron_ingot"));

        // And if a mistake had been made already, learning the real icon repairs it.
        MiningBlocks repaired = new MiningBlocks();
        repaired.importLearned(Map.of("item/iron_ingot", "Debris"));
        repaired.onMining("item/netherite_scrap", 2_190_000_000L, 0L);
        yes("Debris's real icon is learned", repaired.onShopBalance("Debris", 2_191_500_000L, 20_000L));
        yes("and the icon wrongly filed under Debris is dropped, free to relearn",
                !repaired.learned().containsKey("item/iron_ingot"));
        yes("a pickup cannot give a mine a second icon",
                !repaired.teach("item/iron_ingot", "Debris"));
        yes("forget clears one icon", repaired.forget("item/netherite_scrap"));
        yes("and only once", !repaired.forget("item/netherite_scrap"));

        // Learned mappings survive a restart.
        MiningBlocks restored = new MiningBlocks();
        restored.importLearned(blocks.learned());
        eq("persisted mapping restored", "Rubble",
                restored.lookup("block/cobblestone", catalog).orElseThrow().mine());
    }

    // ------------------------------------------------------------- helpers

    /** prices keyed "Mine|gear|level"; a negative balance means none seen. */
    // ------------------------------------------------------- learned links

    private static void learnedLinksReachNewMines() {
        ProgressionLinks links = new ProgressionLinks();
        links.learn("Quarry", "shovel", "Debris");   // a mine newer than the data
        links.learn("Bedrock", "shovel", "Quarry");

        var v = ProgressPlanner.plan(ProgressSlot.SHOVEL, List.of(new GearRef("Debris", "shovel", 6)),
                CATALOG, links, Optional.empty(), new PriceLedger(), new ResourceBalances());
        eq("maxed last known shovel moves on to the learned mine", "Quarry", v.mine());
        eq("from tier I", 1, v.targetLevel());

        var further = ProgressPlanner.plan(ProgressSlot.SHOVEL, List.of(new GearRef("Quarry", "shovel", 6)),
                CATALOG, links, Optional.empty(), new PriceLedger(), new ResourceBalances());
        eq("and keeps following learned links", "Bedrock", further.mine());

        yes("links are per item kind", links.next("Debris", "sword").isEmpty());

        ProgressionLinks restored = new ProgressionLinks();
        restored.importAll(links.export());
        eq("survive a restart", Optional.of("Quarry"), restored.next("Debris", "shovel"));
        yes("relearning the same link is not a change", !restored.learn("Quarry", "shovel", "Debris"));
    }

    private static void learnedLinksRankNewMinesLast() {
        ProgressionLinks links = new ProgressionLinks();
        links.learn("Quarry", "shovel", "Debris");
        // A Debris shovel kept in the inventory must not pull the bar back from the new mine.
        var v = ProgressPlanner.plan(ProgressSlot.SHOVEL, List.of(
                        new GearRef("Debris", "shovel", 6),
                        new GearRef("Quarry", "shovel", 2)),
                CATALOG, links, Optional.empty(), new PriceLedger(), new ResourceBalances());
        eq("the newer mine's item wins", "Quarry", v.mine());
        eq("its next tier", 3, v.targetLevel());
    }

    private static void bossGearSitsBetweenWorlds() {
        // Exactly as learned in game: Bjorn's chestplate requires Aurora's, Darkstone's requires
        // Bjorn's. With both chestplates in the inventory, the bar must follow Darkstone, the one
        // being mined for, not the leftover boss piece.
        MineCatalog arcticToSculk = new MineCatalog(List.of(
                mine("Permafrost", "pickaxe"), mine("Aurora", "pickaxe"),
                mine("Darkstone", "pickaxe"), mine("Warpwood", "axe")));
        ProgressionLinks links = new ProgressionLinks();
        links.learn("Bjorn", "chestplate", "Aurora");
        links.learn("Darkstone", "chestplate", "Bjorn");

        var v = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, List.of(
                        new GearRef("Bjorn", "chestplate", 6),
                        new GearRef("Darkstone", "chestplate", 2)),
                arcticToSculk, links, Optional.empty(), new PriceLedger(), new ResourceBalances());
        eq("the Darkstone chestplate is the newest", "Darkstone", v.mine());
        eq("its next tier", 3, v.targetLevel());

        var fromBoss = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, List.of(
                        new GearRef("Aurora", "chestplate", 4),
                        new GearRef("Bjorn", "chestplate", 1)),
                arcticToSculk, links, Optional.empty(), new PriceLedger(), new ResourceBalances());
        eq("boss gear still outranks the world before it", "Bjorn", fromBoss.mine());
    }

    private static void learnedLinkLoopsDoNotHang() {
        ProgressionLinks links = new ProgressionLinks();
        links.learn("Quarry", "shovel", "Debris");
        links.learn("Debris", "shovel", "Quarry");   // corrupt saved data
        var v = ProgressPlanner.plan(ProgressSlot.SHOVEL, List.of(
                        new GearRef("Debris", "shovel", 6),
                        new GearRef("Quarry", "shovel", 6)),
                CATALOG, links, Optional.empty(), new PriceLedger(), new ResourceBalances());
        eq("a loop ends instead of spinning", State.ALL_MAXED, v.state());
    }

    private static void mineWithTwoTools() {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 16L);
        items.put("pickaxe", 16L);
        items.put("axe", 408L);
        items.put("armor", 58L);
        items.put("charm", 64L);
        Mine oak = new Mine("mine-oak", "mine", "Overworld", "Oak", items, null);
        eq("both tools listed", List.of("pickaxe", "axe"), oak.toolKeys());

        MineCatalog overworld = new MineCatalog(List.of(oak, mine("Stone", "pickaxe")));
        var v = ProgressPlanner.plan(ProgressSlot.AXE, List.of(), overworld,
                overworld.byName("Oak"), new PriceLedger(), new ResourceBalances());
        eq("an axe is found at a mine that also sells a pickaxe", "Oak", v.mine());
    }

    // ----------------------------------------------------------- mine costs

    /** Rafter as the spreadsheet has it. */
    private static Mine rafter() {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 44_520_000_000L);
        items.put("axe", 40_880_000_000L);
        items.put("armor", 117_568_000_000L);
        items.put("charm", 18_320_000_000L);
        Map<String, Long> pieces = new LinkedHashMap<>();
        pieces.put("helmet", 23_520_000_000L);
        pieces.put("chestplate", 36_552_000_000L);
        pieces.put("leggings", 31_360_000_000L);
        pieces.put("boots", 26_136_000_000L);
        return new Mine("mine-ruins-rafter", "mine", "Ruins", "Rafter", items, pieces);
    }

    private static MineCosts.Piece piece(MineCosts.View view, String gear) {
        return view.pieces().stream().filter(p -> p.gear().equals(gear)).findFirst().orElseThrow();
    }

    private static void mineCostsFromSheet() {
        var v = MineCosts.of("Rafter", Optional.of(rafter()), new PriceLedger(), false);
        eq("sword from the sheet", 44_520_000_000L, piece(v, "sword").cost().orElse(-1));
        eq("marked as sheet", MineCosts.Source.SHEET, piece(v, "sword").source());
        eq("armour is the four pieces", 117_568_000_000L, piece(v, "armor").cost().orElse(-1));
        eq("whole mine", 44_520_000_000L + 40_880_000_000L + 117_568_000_000L + 18_320_000_000L,
                v.total().orElse(-1));
        eq("world shown", "Ruins", v.world());

        var split = MineCosts.of("Rafter", Optional.of(rafter()), new PriceLedger(), true);
        eq("pieces listed separately", 36_552_000_000L, piece(split, "chestplate").cost().orElse(-1));
        eq("same total either way", v.total(), split.total());
    }

    private static void mineCostsPreferCompleteShopLadders() {
        PriceLedger ledger = new PriceLedger();
        for (int level = 1; level <= 6; level++) {
            ledger.record(new ShopItemParser.Entry("Rafter", "sword", level, "Rafter", 5_000_000_000L), 1L);
        }
        ledger.record(new ShopItemParser.Entry("Rafter", "axe", 1, "Rafter", 1L), 1L);

        var v = MineCosts.of("Rafter", Optional.of(rafter()), ledger, false);
        eq("six shop tiers beat the sheet", 30_000_000_000L, piece(v, "sword").cost().orElse(-1));
        eq("marked as shop", MineCosts.Source.SHOP, piece(v, "sword").source());
        eq("one shop tier does not replace a sheet total", 40_880_000_000L, piece(v, "axe").cost().orElse(-1));
        eq("and keeps the sheet mark", MineCosts.Source.SHEET, piece(v, "axe").source());
    }

    private static void mineCostsForANewDimension() {
        PriceLedger ledger = new PriceLedger();
        var nothing = MineCosts.of("Quarry", Optional.empty(), ledger, false);
        yes("a never-opened new mine knows nothing", !nothing.anyKnown());
        yes("and has no total", nothing.total().isEmpty());

        for (String gear : List.of("sword", "shovel", "helmet", "chestplate", "leggings", "boots")) {
            for (int level = 1; level <= 6; level++) {
                ledger.record(new ShopItemParser.Entry("Quarry", gear, level, "Quarry", 100L), 1L);
            }
        }
        var partial = MineCosts.of("Quarry", Optional.empty(), ledger, false);
        eq("its tool is learned from the shop", 600L, piece(partial, "shovel").cost().orElse(-1));
        eq("armour from four shop ladders", 2_400L, piece(partial, "armor").cost().orElse(-1));
        eq("and is marked shop", MineCosts.Source.SHOP, piece(partial, "armor").source());
        yes("charm not seen yet, so no total", partial.total().isEmpty());

        for (int level = 1; level <= 6; level++) {
            ledger.record(new ShopItemParser.Entry("Quarry", "charm", level, "Quarry", 100L), 1L);
        }
        eq("complete once every piece is seen", 600L + 600L + 2_400L + 600L,
                MineCosts.of("Quarry", Optional.empty(), ledger, false).total().orElse(-1));
    }

    private static void minePanelShowsTotal() {
        var options = HudModel.Options.defaults();
        PriceLedger ledger = new PriceLedger();
        for (int level = 1; level <= 6; level++) {
            ledger.record(new ShopItemParser.Entry("Rafter", "sword", level, "Rafter", 5_000_000_000L), 1L);
        }
        List<String> text = HudModel.costPanel(
                Optional.of(MineCosts.of("Rafter", Optional.of(rafter()), ledger, false)),
                Optional.empty(), options).stream().map(HudModel.Line::text).toList();
        eq("header", "Rafter  Ruins", text.get(0));
        yes("shop figure starred", text.contains("Sword: 30b*"));
        yes("sheet figure plain", text.contains("Axe: 40.88b"));
        yes("whole-mine total", text.contains("Total: 206.77b"));

        List<String> unknown = HudModel.costPanel(
                Optional.of(MineCosts.of("Quarry", Optional.empty(), new PriceLedger(), false)),
                Optional.empty(), options).stream().map(HudModel.Line::text).toList();
        yes("new mine asks for its shop", unknown.contains("no prices yet, open this mine's shop"));
    }

    /** Darkstone as the spreadsheet has it, chestplate 495.08m over four tiers. */
    private static MineCatalog darkstoneCatalog() {
        Map<String, Long> pieces = new LinkedHashMap<>();
        pieces.put("helmet", 317_260_000L);
        pieces.put("chestplate", 495_080_000L);
        pieces.put("leggings", 424_340_000L);
        pieces.put("boots", 353_630_000L);
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 519_610_000L);
        items.put("pickaxe", 389_710_000L);
        items.put("armor", 1_590_310_000L);
        items.put("charm", 346_410_000L);
        return new MineCatalog(List.of(
                new Mine("mine-sculk-darkstone", "mine", "Sculk", "Darkstone", items, pieces),
                mine("Warpwood", "axe")));
    }

    private static void barCanTrackEverythingLeftToMax() {
        MineCatalog catalog = darkstoneCatalog();
        long[] tiers = { 64_920_000L, 107_110_000L, 155_310_000L, 167_740_000L };   // as read in game
        PriceLedger all = new PriceLedger();
        for (int i = 0; i < tiers.length; i++) {
            all.record(new ShopItemParser.Entry("Darkstone", "chestplate", i + 1, "Darkstone", tiers[i]), 1L);
        }
        ResourceBalances balances = new ResourceBalances();
        balances.update("Darkstone", 200_000_000L, 1L);
        List<GearRef> atTwo = List.of(new GearRef("Darkstone", "chestplate", 2));

        var next = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, atTwo, catalog, new ProgressionLinks(),
                Optional.empty(), all, balances, ProgressPlanner.Goal.NEXT_TIER);
        eq("next tier only, as before", 155_310_000L, next.cost().orElse(-1));
        eq("finished for the next tier", State.FINISHED, next.state());

        var toMax = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, atTwo, catalog, new ProgressionLinks(),
                Optional.empty(), all, balances, ProgressPlanner.Goal.TO_MAX);
        eq("III and IV together", 155_310_000L + 167_740_000L, toMax.cost().orElse(-1));
        eq("covers up to the last tier", 4, toMax.toLevel());
        eq("not yet affordable", State.TRACKING, toMax.state());
        eq("panel names the range", "Darkstone Chestplate III-IV  200m/323.05m",
                HudModel.progressPanel(List.of(toMax), HudModel.ProgressLines.all()).get(0).text());
        eq("twelve of them", 12 * (155_310_000L + 167_740_000L), toMax.withQuantity(12).cost().orElse(-1));
        eq("and the range survives the amount", 4, toMax.withQuantity(12).toLevel());

        // Only the tiers already owned were ever seen: the spreadsheet total covers the rest.
        PriceLedger owned = new PriceLedger();
        owned.record(new ShopItemParser.Entry("Darkstone", "chestplate", 1, "Darkstone", tiers[0]), 1L);
        owned.record(new ShopItemParser.Entry("Darkstone", "chestplate", 2, "Darkstone", tiers[1]), 1L);
        var fromSheet = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, atTwo, catalog, new ProgressionLinks(),
                Optional.empty(), owned, balances, ProgressPlanner.Goal.TO_MAX);
        eq("sheet total minus what is owned", 495_080_000L - tiers[0] - tiers[1], fromSheet.cost().orElse(-1));

        var fresh = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, List.of(), catalog, new ProgressionLinks(),
                catalog.byName("Darkstone"), new PriceLedger(), balances, ProgressPlanner.Goal.TO_MAX);
        eq("nothing owned yet: the whole piece, with no shop visit", 495_080_000L, fresh.cost().orElse(-1));

        var lastTier = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, List.of(new GearRef("Darkstone", "chestplate", 3)),
                catalog, new ProgressionLinks(), Optional.empty(), all, balances, ProgressPlanner.Goal.TO_MAX);
        eq("one tier left is just that tier", 167_740_000L, lastTier.cost().orElse(-1));
        eq("so no range is shown", lastTier.targetLevel(), lastTier.toLevel());
    }

    /** Frost, a new area the spreadsheet does not have, as read in game on 2 October. */
    private static void newAreaWithoutSpreadsheet() {
        PriceLedger ledger = new PriceLedger();
        java.util.function.BiConsumer<String, long[]> tiers = (gear, prices) -> {
            for (int i = 0; i < prices.length; i += 2) {
                ledger.record(new ShopItemParser.Entry("Frost", gear, (int) prices[i], "Frost", prices[i + 1]), 1L);
            }
        };
        tiers.accept("pickaxe", new long[] { 1, 1_550_000_000L, 2, 2_330_000_000L, 3, 3_020_000_000L,
                4, 3_480_000_000L, 5, 3_760_000_000L });
        tiers.accept("sword", new long[] { 2, 3_100_000_000L, 3, 4_030_000_000L, 4, 4_640_000_000L, 5, 5_010_000_000L });
        tiers.accept("chestplate", new long[] { 1, 3_540_000_000L, 2, 5_490_000_000L, 3, 7_430_000_000L, 4, 8_320_000_000L });
        tiers.accept("helmet", new long[] { 2, 3_530_000_000L, 3, 4_780_000_000L, 4, 5_350_000_000L });
        tiers.accept("leggings", new long[] { 2, 4_700_000_000L, 3, 6_370_000_000L, 4, 7_130_000_000L });
        tiers.accept("boots", new long[] { 2, 3_920_000_000L, 3, 5_310_000_000L, 4, 5_940_000_000L });
        tiers.accept("charm", new long[] { 1, 12_400_000_000L });

        eq("the shop listed pickaxe up to V, so it has five tiers", 5,
                ledger.maxLevel("Frost", "pickaxe", java.util.OptionalLong.empty()));
        var v = MineCosts.of("Frost", Optional.empty(), ledger, true);
        eq("pickaxe complete from the shop", MineCosts.Source.SHOP, piece(v, "pickaxe").source());
        eq("its total", 14_140_000_000L, piece(v, "pickaxe").cost().orElse(-1));
        eq("chestplate complete too", MineCosts.Source.SHOP, piece(v, "chestplate").source());
        eq("sword missing tier I (owned, so never listed) is shown in part", MineCosts.Source.PARTIAL,
                piece(v, "sword").source());
        eq("with the tiers it covers", "II-V", piece(v, "sword").tiers());
        yes("a part never makes a total", v.total().isEmpty());

        List<String> text = HudModel.costPanel(Optional.of(v), Optional.empty(), HudModel.Options.defaults())
                .stream().map(HudModel.Line::text).toList();
        yes("panel shows the part, labelled", text.contains("Sword: 16.78b* (II-V)"));
        yes("and the complete pickaxe", text.contains("Pickaxe: 14.14b*"));
        yes("and the charm", text.contains("Charm: 12.4b*"));
        yes("and says the total is incomplete", text.contains("Total: incomplete"));

        eq("a name with a count and icon after the tier still reads", 1,
                ShopItemParser.parseTitle("[Frost Pickaxe] [I] 2‌[item/book@items]")
                        .map(GearRef::level).orElse(-1));
    }

    private static void maxedThreeTierArmourMovesOn() {
        Map<String, Long> pieces = new LinkedHashMap<>();
        pieces.put("helmet", 76_482_000L);
        pieces.put("chestplate", 118_962_000L);
        pieces.put("leggings", 101_970_000L);
        pieces.put("boots", 84_978_000L);
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 124_866_000L);
        items.put("pickaxe", 93_636_000L);
        items.put("armor", 382_392_000L);
        items.put("charm", 83_250_000L);
        Mine dark = new Mine("mine-ocean-dark-prismarine", "mine", "Ocean", "Dark Prismarine", items, pieces);
        MineCatalog ocean = new MineCatalog(List.of(dark, mine("Tube Coral", "pickaxe")));

        PriceLedger ledger = new PriceLedger();
        ledger.record(new ShopItemParser.Entry("Dark Prismarine", "chestplate", 2, "Dark Prismarine", 38_900_000L), 1L);
        ledger.record(new ShopItemParser.Entry("Dark Prismarine", "chestplate", 3, "Dark Prismarine", 56_400_000L), 1L);

        var v = ProgressPlanner.plan(ProgressSlot.CHESTPLATE,
                List.of(new GearRef("Dark Prismarine", "chestplate", 3)), ocean,
                Optional.empty(), ledger, new ResourceBalances());
        eq("a chestplate at its last tier, III, moves to the next mine", "Tube Coral", v.mine());
        eq("at tier I", 1, v.targetLevel());
    }

    // ------------------------------------------------------------ quantity

    private static void quantityMultipliesTheCost() {
        var one = plan(ProgressSlot.CHESTPLATE, List.of(new GearRef("Debris", "chestplate", 3)),
                Map.of("Debris|chestplate|4", 1_000_000_000L), 5_000_000_000L);
        eq("one chestplate is affordable", State.FINISHED, one.state());

        var twelve = one.withQuantity(12);
        eq("twelve cost twelve times as much", 12_000_000_000L, twelve.cost().orElse(-1));
        eq("which is no longer affordable", State.TRACKING, twelve.state());
        yes("bar fill against the combined cost", Math.abs(twelve.fraction() - 5.0 / 12.0) < 1e-9);
        eq("re-applying is not compounded", 12_000_000_000L, twelve.withQuantity(12).cost().orElse(-1));
        eq("changing the amount starts from one piece", 3_000_000_000L, twelve.withQuantity(3).cost().orElse(-1));
        eq("zero is treated as one", 1, one.withQuantity(0).quantity());

        var unknown = plan(ProgressSlot.CHESTPLATE, List.of(new GearRef("Debris", "chestplate", 3)),
                Map.of(), -1L).withQuantity(12);
        eq("no price stays no price", State.PRICE_UNKNOWN, unknown.state());

        eq("panel shows the count", "12x Debris Chestplate IV  5b/12b",
                HudModel.progressPanel(List.of(twelve), HudModel.ProgressLines.all()).get(0).text());
    }

    private static void mineTotalProgressBar() {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 10L);
        items.put("pickaxe", 20L);
        items.put("armor", 450L);
        items.put("charm", 30L);
        Mine mineData = new Mine("mine-rusty", "mine", "Ruins", "Rusty", items, null);
        MineCatalog catalog = new MineCatalog(List.of(mineData));
        ResourceBalances balances = new ResourceBalances();
        balances.update("Rust", 400L, 1L);
        var total = ProgressPlanner.plan(ProgressSlot.TOTAL, List.of(), catalog,
                Optional.of(mineData), new PriceLedger(), balances);
        eq("whole-mine total sums applicable items", 510L, total.cost().orElse(-1L));
        eq("whole-mine total tracks its balance", State.TRACKING, total.state());
        eq("total label", "Rust still to buy  400/510",
                HudModel.progressPanel(List.of(total), HudModel.ProgressLines.all()).get(0).text());

        balances.update("Rust", 510L, 2L);
        var affordable = ProgressPlanner.plan(ProgressSlot.TOTAL, List.of(), catalog,
                Optional.of(mineData), new PriceLedger(), balances);
        eq("whole-mine total finishes when affordable", State.FINISHED, affordable.state());

        mineTotalCountsWhatIsLeft();
        secondSetIsTheOneUpgraded();
    }

    /** A maxed set worn while a second one is upgraded, or the other way round. */
    private static void secondSetIsTheOneUpgraded() {
        MineCatalog catalog = totalCatalog();
        Optional<Mine> rusty = catalog.byName("Rusty");
        ResourceBalances balances = new ResourceBalances();
        GearRef maxed = new GearRef("Rust", "chestplate", 6);
        GearRef second = new GearRef("Rust", "chestplate", 2);

        // Inventory comes first in the list, worn armour last; both orders must agree.
        for (List<GearRef> owned : List.of(List.of(second, maxed), List.of(maxed, second))) {
            var bar = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, owned, catalog, Optional.empty(),
                    new PriceLedger(), balances);
            eq("bar follows the unfinished set, not the maxed one", "Rust", bar.mine());
            eq("bar targets the unfinished set's next tier", 3, bar.targetLevel());
        }

        eq("bar moves on once every copy is maxed", "Marrow",
                ProgressPlanner.plan(ProgressSlot.CHESTPLATE, List.of(maxed), catalog, Optional.empty(),
                        new PriceLedger(), balances).mine());

        // The total counts what the second chestplate still needs: 140 less the 30 paid.
        PriceLedger seen = new PriceLedger();
        seen.seed("Rust", "chestplate", 1, 10L, "Rust");
        seen.seed("Rust", "chestplate", 2, 20L, "Rust");
        eq("total counts the set being upgraded", 480L,
                ProgressPlanner.plan(ProgressSlot.TOTAL, List.of(maxed, second), catalog, rusty, seen, balances)
                        .cost().orElse(-1L));

        // The maxed main set is from a later mine, the set being upgraded from an earlier one.
        GearRef mainSet = new GearRef("Marrow", "chestplate", 6);
        for (List<GearRef> owned : List.of(List.of(second, mainSet), List.of(mainSet, second))) {
            var bar = ProgressPlanner.plan(ProgressSlot.CHESTPLATE, owned, catalog, Optional.empty(),
                    new PriceLedger(), balances);
            eq("bar follows an older set being upgraded over a later maxed one", "Rust", bar.mine());
            eq("bar targets the older set's next tier", 3, bar.targetLevel());
        }
        eq("total counts an older set being upgraded despite a later one", 480L,
                ProgressPlanner.plan(ProgressSlot.TOTAL, List.of(mainSet, second), catalog, rusty, seen, balances)
                        .cost().orElse(-1L));
        eq("total still skips a piece owned only from a later mine", 370L,
                ProgressPlanner.plan(ProgressSlot.TOTAL, List.of(mainSet), catalog, rusty, seen, balances)
                        .cost().orElse(-1L));
        eq("away from a mine, the total follows the set being upgraded", "Rust",
                ProgressPlanner.plan(ProgressSlot.TOTAL, List.of(mainSet, second), catalog, Optional.empty(),
                        seen, balances).mine());

        noShopNeeded();
        wholeMineTotalWhenTrackingToMax();
    }

    /** "Track: to max" makes the Total the whole mine at full price, the mine panel's figure. */
    private static void wholeMineTotalWhenTrackingToMax() {
        MineCatalog catalog = totalCatalog();
        Optional<Mine> rusty = catalog.byName("Rusty");
        ResourceBalances balances = new ResourceBalances();
        balances.update("Rust", 400L, 1L);
        List<GearRef> allMaxed = List.of(new GearRef("Rust", "sword", 6), new GearRef("Rust", "pickaxe", 6),
                new GearRef("Rust", "helmet", 6), new GearRef("Rust", "chestplate", 6),
                new GearRef("Rust", "leggings", 6), new GearRef("Rust", "boots", 6),
                new GearRef("Rust", "charm", 1));

        var whole = ProgressPlanner.plan(ProgressSlot.TOTAL, allMaxed, catalog, new ProgressionLinks(), rusty,
                new PriceLedger(), balances, ProgressPlanner.Goal.TO_MAX);
        eq("to-max total is the whole mine, gear owned or not", 510L, whole.cost().orElse(-1L));
        eq("to-max total matches the mine panel",
                MineCosts.of("Rust", rusty, new PriceLedger(), false).total().orElse(-2L), whole.cost().orElse(-1L));
        eq("to-max total tracks the balance", State.TRACKING, whole.state());
        eq("to-max total label", "Rust Total  400/510",
                HudModel.progressPanel(List.of(whole), HudModel.ProgressLines.all()).get(0).text());

        var left = ProgressPlanner.plan(ProgressSlot.TOTAL, allMaxed, catalog, new ProgressionLinks(), rusty,
                new PriceLedger(), balances, ProgressPlanner.Goal.NEXT_TIER);
        eq("next-tier total is what is still to buy", State.ALL_MAXED, left.state());
    }

    /** Tier counts from the bundled table and tier prices from the sheet, with no shop opened. */
    private static void noShopNeeded() {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 1_000_000L);
        items.put("pickaxe", 1_000_000L);
        items.put("armor", 1_000_000L);
        items.put("charm", 1_000_000L);
        Mine woodland = new Mine("mine-woodland-copper", "mine", "Woodland", "Woodland Copper", items, null);
        MineCatalog catalog = new MineCatalog(List.of(woodland));
        ResourceBalances balances = new ResourceBalances();

        PriceLedger ledger = new PriceLedger();
        ledger.knowTierCount("Woodland Copper", "chestplate", 3);
        eq("tier count from the table", 3, ledger.maxLevel("Woodland Copper", "chestplate"));

        // Chestplate III of three is maxed, so the bar moves on rather than waiting for a tier IV.
        var maxed = ProgressPlanner.plan(ProgressSlot.CHESTPLATE,
                List.of(new GearRef("Woodland Copper", "chestplate", 3)), catalog, Optional.empty(), ledger, balances);
        eq("three-tier armour maxed at III without a shop", State.ALL_MAXED, maxed.state());

        // Sword II of six, nothing priced: tier III and the rest estimated from the 1M piece.
        long nextTier = minerefinehud.progress.TierCurve.estimate(1_000_000L, 6, 3);
        var next = ProgressPlanner.plan(ProgressSlot.SWORD,
                List.of(new GearRef("Woodland Copper", "sword", 2)), catalog, Optional.empty(), ledger, balances);
        eq("next tier priced from the sheet", nextTier, next.cost().orElse(-1L));
        yes("next tier estimate is about a sixth of the piece", nextTier > 160_000L && nextTier < 175_000L);

        long rest = 0L;
        for (int l = 3; l <= 6; l++) {
            rest += minerefinehud.progress.TierCurve.estimate(1_000_000L, 6, l);
        }
        var toMax = ProgressPlanner.plan(ProgressSlot.SWORD, List.of(new GearRef("Woodland Copper", "sword", 2)),
                catalog, new ProgressionLinks(), Optional.empty(), ledger, balances, ProgressPlanner.Goal.TO_MAX);
        eq("to-max priced from the sheet", rest, toMax.cost().orElse(-1L));
        yes("tiers I and II are about a fifth of a six-tier piece",
                1_000_000L - rest > 205_000L && 1_000_000L - rest < 225_000L);
    }

    /** Sheet: Rusty 10/20/450/30 and Marrow ten times that. Armour splits 9:14:12:10. */
    private static MineCatalog totalCatalog() {
        return new MineCatalog(List.of(totalMine("Rusty", 1L), totalMine("Marrow", 10L)));
    }

    private static Mine totalMine(String name, long factor) {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 10L * factor);
        items.put("pickaxe", 20L * factor);
        items.put("armor", 450L * factor);
        items.put("charm", 30L * factor);
        return new Mine("mine-" + name.toLowerCase(), "mine", "Ruins", name, items, null);
    }

    private static ProgressPlanner.ProgressView total(List<GearRef> owned, Optional<Mine> at,
                                                      PriceLedger ledger, ResourceBalances balances) {
        MineCatalog catalog = totalCatalog();
        return ProgressPlanner.plan(ProgressSlot.TOTAL, owned, catalog, at, ledger, balances);
    }

    private static void mineTotalCountsWhatIsLeft() {
        Optional<Mine> rusty = totalCatalog().byName("Rusty");
        ResourceBalances balances = new ResourceBalances();
        balances.update("Rust", 400L, 1L);

        // Sword II owned, tiers I and II cost 1 and 2: 7 of the sword's 10 still to pay.
        PriceLedger seen = new PriceLedger();
        seen.seed("Rust", "sword", 1, 1L, "Rust");
        seen.seed("Rust", "sword", 2, 2L, "Rust");
        eq("total leaves out tiers already bought", 507L,
                total(List.of(new GearRef("Rust", "sword", 2)), rusty, seen, balances).cost().orElse(-1L));

        // Everything but the charm bought: the 30 left is affordable. Against the full 510 the
        // bar could never finish, since the balance had gone on the gear.
        List<GearRef> allButCharm = List.of(new GearRef("Rust", "sword", 6),
                new GearRef("Rust", "pickaxe", 6), new GearRef("Rust", "helmet", 6),
                new GearRef("Rust", "chestplate", 6), new GearRef("Rust", "leggings", 6),
                new GearRef("Rust", "boots", 6));
        var nearlyDone = total(allButCharm, rusty, new PriceLedger(), balances);
        eq("total counts only what is left", 30L, nearlyDone.cost().orElse(-1L));
        eq("total finishes on what is left", State.FINISHED, nearlyDone.state());

        List<GearRef> everything = new java.util.ArrayList<>(allButCharm);
        everything.add(new GearRef("Rust", "charm", 1));
        eq("total done when every piece is maxed", State.ALL_MAXED,
                total(everything, rusty, new PriceLedger(), balances).state());

        // A Marrow sword makes the Rust one pointless.
        eq("total skips a piece owned from a later mine", 500L,
                total(List.of(new GearRef("Marrow", "sword", 1)), rusty, new PriceLedger(), balances)
                        .cost().orElse(-1L));

        // Away from any mine: the furthest mine owned from, here Marrow.
        PriceLedger marrowSeen = new PriceLedger();
        marrowSeen.seed("Marrow", "sword", 1, 10L, "Marrow");
        var away = total(List.of(new GearRef("Rust", "helmet", 2), new GearRef("Marrow", "sword", 1)),
                Optional.empty(), marrowSeen, balances);
        eq("total away from a mine follows the furthest one", "Marrow", away.mine());
        eq("total away from a mine counts what is left there", 5_090L, away.cost().orElse(-1L));
        eq("total with nothing owned and no mine", State.NO_MINE,
                total(List.of(), Optional.empty(), new PriceLedger(), balances).state());

        // Sword I owned, its price never seen: estimated from the sheet, no shop needed.
        eq("total with an owned tier never priced still counts", State.TRACKING,
                total(List.of(new GearRef("Rust", "sword", 1)), rusty, new PriceLedger(), balances).state());

        // A mine whose tool is not known yet cannot have a total.
        Map<String, Long> noTool = new LinkedHashMap<>();
        noTool.put("sword", 10L);
        noTool.put("armor", 450L);
        noTool.put("charm", 30L);
        Mine bare = new Mine("mine-bare", "mine", "Ruins", "Bare", noTool, null);
        var gap = ProgressPlanner.plan(ProgressSlot.TOTAL, List.of(), new MineCatalog(List.of(bare)),
                Optional.of(bare), new PriceLedger(), balances);
        eq("total unknown without the mine's tool", State.PRICE_UNKNOWN, gap.state());
        eq("total asks for the shop", "open the Bare shop once for the prices",
                HudModel.progressPanel(List.of(gap), HudModel.ProgressLines.all()).get(1).text());

        // The currency the shop names wins over the mine's name.
        PriceLedger dust = new PriceLedger();
        dust.seed("Rust", "sword", 1, 1L, "Rust Dust");
        balances.update("Rust Dust", 999L, 3L);
        eq("total reads the shop's currency", 999L,
                total(List.of(), rusty, dust, balances).have().orElse(-1L));
    }

    private static void severalBarsStack() {
        var sword = plan(ProgressSlot.SWORD, List.of(new GearRef("Debris", "sword", 1)),
                Map.of("Debris|sword|2", 4_000_000_000L), 1_000_000_000L);
        var chest = plan(ProgressSlot.CHESTPLATE, List.of(new GearRef("Debris", "chestplate", 1)),
                Map.of("Debris|chestplate|2", 2_000_000_000L), 1_000_000_000L).withQuantity(12);
        List<HudModel.Line> lines = HudModel.progressPanel(List.of(sword, chest), HudModel.ProgressLines.all());
        eq("two rows and two bars", 4, lines.size());
        eq("in configured order", "Debris Sword II  1b/4b", lines.get(0).text());
        eq("second bar", HudModel.Style.BAR, lines.get(3).style());

        eq("bars only", 2, HudModel.progressPanel(List.of(sword, chest),
                new HudModel.ProgressLines(false, true)).size());
        eq("text only", 2, HudModel.progressPanel(List.of(sword, chest),
                new HudModel.ProgressLines(true, false)).size());
        yes("no bars configured draws nothing",
                HudModel.progressPanel(List.of(), HudModel.ProgressLines.all()).isEmpty());
    }

    private static void mineRowsCanBeHidden() {
        var options = new HudModel.Options(true, false, false, 200.0, true, 5,
                new HudModel.MineLines(true, false, false, false, true, true),
                HudModel.BossLines.all(), HudModel.ProgressLines.all());
        List<String> text = HudModel.costPanel(
                Optional.of(MineCosts.of("Rafter", Optional.of(rafter()), new PriceLedger(), false)),
                Optional.empty(), options).stream().map(HudModel.Line::text).toList();
        yes("sword kept", text.contains("Sword: 44.52b"));
        yes("tool hidden", text.stream().noneMatch(t -> t.startsWith("Axe")));
        yes("armour hidden", text.stream().noneMatch(t -> t.startsWith("Armor")));
        yes("total is still the whole mine", text.contains("Total: 221.29b"));

        var noTotal = new HudModel.Options(true, false, false, 200.0, true, 5,
                new HudModel.MineLines(true, true, true, true, false, true),
                HudModel.BossLines.all(), HudModel.ProgressLines.all());
        yes("total can be hidden", HudModel.costPanel(
                Optional.of(MineCosts.of("Rafter", Optional.of(rafter()), new PriceLedger(), false)),
                Optional.empty(), noTotal).stream().noneMatch(l -> l.text().startsWith("Total")));
    }

    private static ProgressPlanner.ProgressView plan(ProgressSlot slot, List<GearRef> owned,
                                                     Map<String, Long> prices, long balance) {
        PriceLedger ledger = new PriceLedger();
        prices.forEach((k, amount) -> {
            String[] p = k.split("\\|");
            ledger.record(new ShopItemParser.Entry(p[0], p[1], Integer.parseInt(p[2]), p[0], amount), 1L);
        });
        ResourceBalances balances = new ResourceBalances();
        if (balance >= 0L && !owned.isEmpty()) {
            balances.update(owned.get(owned.size() - 1).mine(), balance, 1L);
        }
        return ProgressPlanner.plan(slot, owned, CATALOG, Optional.empty(), ledger, balances);
    }

    private static Mine mine(String name, String tool) {
        Map<String, Long> items = new LinkedHashMap<>();
        items.put("sword", 1L);
        items.put(tool, 1L);
        items.put("armor", 1L);
        items.put("charm", 1L);
        return new Mine("mine-" + name, "mine", "Ruins", name, items, null);
    }

    private static void eq(String what, Object expected, Object actual) {
        if (expected.equals(actual)) pass(what); else fail(what, expected, actual);
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
