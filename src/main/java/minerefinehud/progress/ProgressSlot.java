package minerefinehud.progress;

import java.util.Locale;
import java.util.Optional;

/**
 * The gear piece the progress bar follows.
 *
 * Tools are three separate choices rather than one "tool", because the server
 * chains them by
 * type: Debris Shovel I requires Suspicious Sand Shovel VI, skipping the four
 * pickaxe and axe
 * mines in between. A single "tool" slot would send a shovel player to a
 * pickaxe mine.
 */
public enum ProgressSlot {

    SWORD("sword"),
    PICKAXE("pickaxe"),
    AXE("axe"),
    SHOVEL("shovel"),
    HELMET("helmet"),
    CHESTPLATE("chestplate"),
    LEGGINGS("leggings"),
    BOOTS("boots"),
    CHARM("charm"),
    TOTAL("total");

    private final String gear;

    ProgressSlot(String gear) {
        this.gear = gear;
    }

    /**
     * The gear word as it appears in item names, e.g. "shovel" in "[Debris Shovel]
     * [VI]".
     */
    public String gear() {
        return gear;
    }

    public boolean isTool() {
        return this == PICKAXE || this == AXE || this == SHOVEL;
    }

    public boolean isTotal() {
        return this == TOTAL;
    }

    public String label() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Tolerant lookup for the config file. Anything unrecognised, including "OFF",
     * is empty.
     */
    public static Optional<ProgressSlot> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String key = raw.trim().toUpperCase(Locale.ROOT);
        for (ProgressSlot slot : values()) {
            if (slot.name().equals(key)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }
}
