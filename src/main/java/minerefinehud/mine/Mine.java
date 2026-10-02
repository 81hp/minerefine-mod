package minerefinehud.mine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One stage of the progression. Costs are block counts, not currency: the number of blocks that
 * must be mined at this mine to afford that upgrade. Boss stages use fragments instead.
 *
 * Field names match progression.json, which tools/convert-spreadsheet.py generates in the shape
 * the job calculator's data.js used, so Gson binds straight onto it.
 */
public final class Mine {

    /** The three tool keys that appear in the data. Which one is present varies by mine. */
    private static final String[] TOOL_KEYS = { "pickaxe", "axe", "shovel" };

    private String id;
    private String type;
    private String world;
    private String name;
    private Map<String, Long> items;
    private Map<String, Long> armorPieces;
    private Double fragmentsPerCredit;
    private String beforeWorld;

    /** Gson needs this. */
    public Mine() {
    }

    public Mine(String id, String type, String world, String name,
                Map<String, Long> items, Map<String, Long> armorPieces) {
        this.id = id;
        this.type = type;
        this.world = world;
        this.name = name;
        this.items = items;
        this.armorPieces = armorPieces;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name == null ? "" : name;
    }

    public String world() {
        return world == null ? "" : world;
    }

    public boolean isMine() {
        return "mine".equals(type);
    }

    public boolean isBoss() {
        return "boss".equals(type);
    }

    public Double fragmentsPerCredit() {
        return fragmentsPerCredit;
    }

    public String beforeWorld() {
        return beforeWorld;
    }

    public Map<String, Long> items() {
        return items == null ? Collections.emptyMap() : items;
    }

    public long cost(String key) {
        Long v = items().get(key);
        return v == null ? 0L : v;
    }

    /** Whole-piece cost for any gear word, the four armour pieces included. 0 when not listed. */
    public long pieceCost(String gear) {
        Long v = items().get(gear);
        if (v != null) {
            return v;
        }
        return armorPieces().getOrDefault(gear, 0L);
    }

    public long sword() {
        return cost("sword");
    }

    public long armor() {
        return cost("armor");
    }

    public long charm() {
        return cost("charm");
    }

    /**
     * The mine's tool slot. Sixty-six mines use a pickaxe, ten an axe and seven a shovel, so this
     * must never be hardcoded.
     */
    public Optional<String> toolKey() {
        for (String key : TOOL_KEYS) {
            if (items().containsKey(key)) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    /** Every tool this mine sells, in key order. Usually one; Oak sells both an axe and a pickaxe. */
    public List<String> toolKeys() {
        List<String> out = new ArrayList<>();
        for (String key : TOOL_KEYS) {
            if (items().containsKey(key)) {
                out.add(key);
            }
        }
        return out;
    }

    public long toolCost() {
        return toolKey().map(this::cost).orElse(0L);
    }

    /**
     * Per-piece armour costs: the published split where the data has one, otherwise derived from
     * the fixed 9:14:12:10 ratio.
     */
    public Map<String, Long> armorPieces() {
        if (armorPieces != null && !armorPieces.isEmpty()) {
            return new LinkedHashMap<>(armorPieces);
        }
        long total = armor();
        return total > 0L ? ArmorSplit.derive(total) : Collections.emptyMap();
    }

    /** True when the split came from the data rather than from the ratio. */
    public boolean armorPiecesArePublished() {
        return armorPieces != null && !armorPieces.isEmpty();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    @Override
    public String toString() {
        return name() + " (" + world() + ")";
    }
}
