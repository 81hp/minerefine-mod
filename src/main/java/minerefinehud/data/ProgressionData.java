package minerefinehud.data;

import minerefinehud.mine.MineCatalog;
import minerefinehud.mine.ProgressionParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Supplies the progression: every mine and boss in order, with the spreadsheet's costs.
 *
 * The data ships inside the mod as progression.json, generated from the community spreadsheet by
 * tools/convert-spreadsheet.py. A progression.json in the config folder takes priority, so an
 * updated spreadsheet can be used without waiting for a new release. Either way this is only the
 * baseline: prices read from the shop win over it, and cover dimensions added since.
 */
public final class ProgressionData {

    private static final String BUNDLED = "/assets/minerefine-hud/progression.json";

    private final Path overrideFile;
    private MineCatalog catalog = new MineCatalog(List.of());
    private String source = "nothing loaded";
    private String lastError;

    public ProgressionData(Path overrideFile) {
        this.overrideFile = overrideFile;
    }

    public MineCatalog catalog() {
        return catalog;
    }

    /** Where the data came from, for /mrhud debug. */
    public String source() {
        return source;
    }

    public Optional<String> lastError() {
        return Optional.ofNullable(lastError);
    }

    /** Reads the override if there is a usable one, the bundled copy otherwise. */
    public void load() {
        try {
            if (Files.isRegularFile(overrideFile)) {
                MineCatalog parsed = ProgressionParser.parse(
                        Files.readString(overrideFile, StandardCharsets.UTF_8));
                if (!parsed.isEmpty()) {
                    use(parsed, "config folder");
                    return;
                }
                lastError = "config progression.json is empty, using the bundled copy";
            }
        } catch (Exception e) {
            // A broken hand-placed file must not cost the bundled data.
            lastError = "config progression.json unreadable (" + e.getMessage() + "), using the bundled copy";
        }

        try (InputStream in = ProgressionData.class.getResourceAsStream(BUNDLED)) {
            if (in == null) {
                lastError = "bundled progression.json missing from the jar";
                return;
            }
            use(ProgressionParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)), "bundled");
        } catch (Exception e) {
            lastError = "bundled progression.json unreadable: " + e.getMessage();
        }
    }

    private void use(MineCatalog parsed, String from) {
        catalog = parsed;
        source = from;
    }
}
