package minerefinehud.mine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The server splits a full armour upgrade into four pieces on a fixed ratio.
 *
 * Derived from the eleven Ruins mines in the job calculator data, which publish both the bundled
 * armour total and the per-piece costs. In all eleven the pieces sum to the bundle exactly, and
 * the proportions are constant:
 *
 *   helmet 20.00%, chestplate 31.11%, leggings 26.67%, boots 22.22%
 *
 * Those are 9/45, 14/45, 12/45 and 10/45. Because the ratio is fixed, per-piece costs can be
 * derived for all 83 mines, not only the eleven that publish them.
 */
public final class ArmorSplit {

    public static final int HELMET_PARTS = 9;
    public static final int CHESTPLATE_PARTS = 14;
    public static final int LEGGINGS_PARTS = 12;
    public static final int BOOTS_PARTS = 10;
    public static final int TOTAL_PARTS = 45;

    private ArmorSplit() {
    }

    public static long helmet(long armorTotal) {
        return share(armorTotal, HELMET_PARTS);
    }

    public static long chestplate(long armorTotal) {
        return share(armorTotal, CHESTPLATE_PARTS);
    }

    public static long leggings(long armorTotal) {
        return share(armorTotal, LEGGINGS_PARTS);
    }

    /**
     * Boots take whatever is left rather than their own rounded share.
     *
     * Rounding each of the four independently loses up to a block or two against the total, which
     * looks like a bug when a player adds the HUD figures up by hand. Making the last piece the
     * remainder guarantees the four always sum to the bundle exactly, and it still reproduces the
     * published boots figure at the precision the data is given in.
     */
    public static long boots(long armorTotal) {
        return armorTotal - helmet(armorTotal) - chestplate(armorTotal) - leggings(armorTotal);
    }

    private static long share(long armorTotal, int parts) {
        return Math.round(armorTotal * (double) parts / TOTAL_PARTS);
    }

    /** Derived pieces in display order. Insertion-ordered so the HUD renders predictably. */
    public static Map<String, Long> derive(long armorTotal) {
        Map<String, Long> out = new LinkedHashMap<>(4);
        out.put("helmet", helmet(armorTotal));
        out.put("chestplate", chestplate(armorTotal));
        out.put("leggings", leggings(armorTotal));
        out.put("boots", boots(armorTotal));
        return out;
    }
}
