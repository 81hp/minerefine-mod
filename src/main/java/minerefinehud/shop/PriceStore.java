package minerefinehud.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Persists observed prices, so a mine visited once keeps its prices forever.
 *
 * Only observations are written. Prefill comes from the spreadsheet on every start, which means a
 * corrected spreadsheet takes effect immediately rather than being shadowed by a stale cache.
 */
public final class PriceStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST = new TypeToken<List<PriceLedger.Persisted>>() {}.getType();

    private final Path file;

    public PriceStore(Path file) {
        this.file = file;
    }

    public void load(PriceLedger ledger) {
        try {
            if (Files.isRegularFile(file)) {
                ledger.importAll(GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST));
            }
        } catch (Exception ignored) {
            // A corrupt cache just means relearning, which happens by playing.
        }
    }

    /** Shipped with the mod: tiers per item, "mine|gear" to count. See tools/export-tier-counts.py. */
    private static final String BUNDLED_TIERS = "/assets/minerefine-hud/tiers.json";
    private static final Type TIERS = new TypeToken<Map<String, Integer>>() {}.getType();

    /** Teaches the ledger every item's tier count, so maxed gear is known without a shop visit. */
    public static void loadTierCounts(PriceLedger ledger) {
        try (InputStream in = PriceStore.class.getResourceAsStream(BUNDLED_TIERS)) {
            if (in == null) {
                return;
            }
            Map<String, Integer> counts = GSON.fromJson(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8), TIERS);
            if (counts == null) {
                return;
            }
            counts.forEach((key, tiers) -> {
                int bar = key.lastIndexOf('|');
                if (bar > 0 && tiers != null) {
                    ledger.knowTierCount(key.substring(0, bar), key.substring(bar + 1), tiers);
                }
            });
        } catch (Exception ignored) {
            // Without the table the ledger guesses six tiers, as before.
        }
    }

    public void save(PriceLedger ledger) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(ledger.export(), LIST), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
