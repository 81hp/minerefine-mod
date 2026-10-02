package minerefinehud.mine;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Binds progression.json onto {@link Mine} objects.
 *
 * The file is generated from the community spreadsheet by tools/convert-spreadsheet.py. Gson
 * ships with Minecraft, so this adds no dependency.
 */
public final class ProgressionParser {

    private static final Gson GSON = new Gson();
    private static final Type LIST_OF_MINE = new TypeToken<List<Mine>>() {}.getType();

    private ProgressionParser() {
    }

    public static MineCatalog parse(String json) {
        List<Mine> entries = GSON.fromJson(json, LIST_OF_MINE);
        return new MineCatalog(entries == null ? List.of() : entries);
    }
}
