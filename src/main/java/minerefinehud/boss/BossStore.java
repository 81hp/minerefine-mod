package minerefinehud.boss;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes learned boss intervals to disk so they survive a relog.
 *
 * Without this the mod forgets every interval on every restart and has to watch a full kill to
 * spawn cycle again before it can show a countdown.
 */
public final class BossStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST = new TypeToken<List<BossTracker.Persisted>>() {}.getType();

    private final Path file;

    public BossStore(Path file) {
        this.file = file;
    }

    public void load(BossTracker tracker) {
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            List<BossTracker.Persisted> saved =
                    GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LIST);
            tracker.importAll(saved);
        } catch (Exception ignored) {
            // A corrupt timer file is not worth failing startup over. The mod simply relearns.
        }
    }

    public void save(BossTracker tracker) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(tracker.export(), LIST), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Losing timers is survivable; crashing the client on logout is not.
        }
    }
}
