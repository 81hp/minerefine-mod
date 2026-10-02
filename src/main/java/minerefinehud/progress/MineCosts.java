package minerefinehud.progress;

import minerefinehud.mine.Mine;
import minerefinehud.shop.PriceLedger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What every piece of gear at one mine costs in full, tiers I to VI, and the whole mine together.
 *
 * Two sources, the shop first:
 *
 *   SHOP   every tier of the piece has been read from the shop, so this is their sum. Current by
 *          construction, and the only source for a dimension added after the spreadsheet.
 *   SHEET  the bundled spreadsheet's figure for the piece.
 *
 * A piece with neither is unknown, and then so is the mine total: a total that quietly leaves out
 * a piece is worse than no total, same rule as the ledger's remaining cost.
 *
 * No Minecraft types; the HUD only formats what this returns.
 */
public final class MineCosts {

    public enum Source { SHOP, SHEET, NONE }

    /** @param gear "sword", "axe", "armor", "helmet" and so on; "tool" when not yet known */
    public record Piece(String gear, OptionalLong cost, Source source) {

        public boolean known() {
            return cost.isPresent();
        }
    }

    public record View(String mine, String world, List<Piece> pieces, OptionalLong total) {

        /** False for a mine nobody has priced: not in the data and its shop never opened. */
        public boolean anyKnown() {
            return pieces.stream().anyMatch(Piece::known);
        }
    }

    private static final List<String> TOOLS = List.of("pickaxe", "axe", "shovel");
    private static final List<String> ARMOR = List.of("helmet", "chestplate", "leggings", "boots");

    private MineCosts() {
    }

    /**
     * @param mineName    the mine as the server spells it, which is how the ledger is keyed
     * @param data        its entry in the bundled data, or empty (or price-less) for a new mine
     * @param armorPieces list the four armour pieces instead of one armour line
     */
    public static View of(String mineName, Optional<Mine> data, PriceLedger ledger, boolean armorPieces) {
        Optional<Mine> priced = data.filter(m -> !m.items().isEmpty());
        Map<String, Long> sheetPieces = priced.map(Mine::armorPieces).orElse(Map.of());

        List<Piece> pieces = new ArrayList<>();
        pieces.add(piece(ledger, mineName, "sword", sheet(priced, "sword")));

        Set<String> tools = new LinkedHashSet<>(priced.map(Mine::toolKeys).orElse(List.of()));
        for (String gear : ledger.observedGears(mineName)) {
            if (TOOLS.contains(gear)) {
                tools.add(gear);
            }
        }
        if (tools.isEmpty()) {
            // Every mine sells a tool. Which one is unknown until its shop is opened.
            pieces.add(new Piece("tool", OptionalLong.empty(), Source.NONE));
        }
        for (String tool : tools) {
            pieces.add(piece(ledger, mineName, tool, sheet(priced, tool)));
        }

        List<Piece> armor = new ArrayList<>();
        for (String gear : ARMOR) {
            Long fromSheet = sheetPieces.get(gear);
            armor.add(piece(ledger, mineName, gear,
                    fromSheet == null || fromSheet <= 0L ? OptionalLong.empty() : OptionalLong.of(fromSheet)));
        }
        if (armorPieces) {
            pieces.addAll(armor);
        } else {
            pieces.add(bundle(armor));
        }

        pieces.add(piece(ledger, mineName, "charm", sheet(priced, "charm")));

        return new View(mineName, priced.map(Mine::world).orElse(""), List.copyOf(pieces), total(pieces));
    }

    /** The sheet total also tells the ledger how many tiers the piece has, so a 4-tier ladder can complete. */
    private static Piece piece(PriceLedger ledger, String mineName, String gear, OptionalLong fromSheet) {
        OptionalLong fromShop = ledger.observedTotal(mineName, gear, fromSheet);
        if (fromShop.isPresent()) {
            return new Piece(gear, fromShop, Source.SHOP);
        }
        if (fromSheet.isPresent()) {
            return new Piece(gear, fromSheet, Source.SHEET);
        }
        return new Piece(gear, OptionalLong.empty(), Source.NONE);
    }

    private static OptionalLong sheet(Optional<Mine> data, String gear) {
        long v = data.map(m -> m.cost(gear)).orElse(0L);
        return v > 0L ? OptionalLong.of(v) : OptionalLong.empty();
    }

    /** Armour as one line: the four pieces summed, and only from the shop if all four are. */
    private static Piece bundle(List<Piece> armor) {
        long sum = 0L;
        boolean allShop = true;
        for (Piece p : armor) {
            if (!p.known()) {
                return new Piece("armor", OptionalLong.empty(), Source.NONE);
            }
            sum += p.cost().getAsLong();
            allShop &= p.source() == Source.SHOP;
        }
        return new Piece("armor", OptionalLong.of(sum), allShop ? Source.SHOP : Source.SHEET);
    }

    private static OptionalLong total(List<Piece> pieces) {
        long sum = 0L;
        for (Piece p : pieces) {
            if (!p.known()) {
                return OptionalLong.empty();
            }
            sum += p.cost().getAsLong();
        }
        return OptionalLong.of(sum);
    }
}
