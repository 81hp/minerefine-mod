package minerefinehud.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
