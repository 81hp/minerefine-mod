package minerefinehud.progress;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * How much of each mine's resource the player has, as last reported by the server.
 *
 * Keyed by the currency name the shop prints ("Debris" in "Debris x1.96B"), because that is what
 * a price is denominated in, so a price and a balance can always be matched without guessing.
 * Newest reading wins whatever its source.
 */
public final class ResourceBalances {

    public record Reading(long amount, long seenAt) {}

    private final Map<String, Reading> byCurrency = new HashMap<>();

    public void update(String currency, long amount, long nowMs) {
        if (currency == null || currency.isBlank() || amount < 0L) {
            return;
        }
        byCurrency.put(key(currency), new Reading(amount, nowMs));
    }

    /**
     * Puts back what a currency held before a reading that turned out to be another resource's,
     * or removes it if it held nothing.
     */
    public void restore(String currency, Optional<Reading> before) {
        if (currency == null || currency.isBlank()) {
            return;
        }
        if (before.isPresent()) {
            byCurrency.put(key(currency), before.get());
        } else {
            byCurrency.remove(key(currency));
        }
    }

    public Optional<Reading> get(String currency) {
        if (currency == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCurrency.get(key(currency)));
    }

    public Map<String, Reading> all() {
        return Map.copyOf(byCurrency);
    }

    private static String key(String currency) {
        return currency.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
