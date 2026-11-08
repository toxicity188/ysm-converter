package io.github.toxicity188.ysmconverter.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects and organizes player texture variant mappings for inclusion in {@code ysm.json}.
 *
 * @since 1.0.0
 */
public final class PlayerTextureCollector {

    private record Entry(
            @NotNull String baseName,
            @NotNull List<@NotNull Variant> variants,
            @NotNull Map<@NotNull String, @NotNull Integer> variantIndex
    ) {
        Entry(@NotNull String baseName) {
            this(baseName, new ArrayList<>(), new LinkedHashMap<>());
        }
    }

    private static final class Variant {
        final @NotNull String type;
        @NotNull String path;

        Variant(@NotNull String type, @NotNull String path) {
            this.type = type;
            this.path = path;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Integer> entryIndex = new LinkedHashMap<>();

    /**
     * Constructs a new empty {@link PlayerTextureCollector}.
     */
    public PlayerTextureCollector() {
    }

    /**
     * Splits a raw texture name into base name and variant type (defaults to "uv").
     *
     * @param rawName raw texture identifier
     * @return 2-element array [baseName, type]
     */
    @CheckReturnValue
    public static @NotNull String @NotNull [] splitName(@NotNull String rawName) {
        int pos = rawName.indexOf('/');
        if (pos == -1) {
            return new String[]{rawName, "uv"};
        }
        return new String[]{rawName.substring(0, pos), rawName.substring(pos + 1)};
    }

    /**
     * Adds or updates a texture path for a base texture and variant.
     *
     * @param rawName raw texture identifier (may include {@code "/variant"})
     * @param path    relative path to the texture file
     */
    public void add(@NotNull String rawName, @NotNull String path) {
        String[] split = splitName(rawName);
        String baseName = split[0];
        String type = split[1];

        int pos;
        if (!entryIndex.containsKey(baseName)) {
            pos = entries.size();
            entryIndex.put(baseName, pos);
            entries.add(new Entry(baseName));
        } else {
            pos = entryIndex.get(baseName);
        }

        Entry entry = entries.get(pos);
        if (!entry.variantIndex().containsKey(type)) {
            entry.variantIndex().put(type, entry.variants().size());
            entry.variants().add(new Variant(type, path));
        } else {
            int vIdx = entry.variantIndex().get(type);
            entry.variants().get(vIdx).path = path;
        }
    }

    /**
     * Emits the collected textures into the given player JSON object node.
     *
     * @param playerNode player JSON node
     * @param mapper     Jackson object mapper
     */
    public void emitTo(@NotNull ObjectNode playerNode, @NotNull ObjectMapper mapper) {
        if (entries.isEmpty()) {
            return;
        }

        ArrayNode texArr = mapper.createArrayNode();
        for (Entry entry : entries) {
            ObjectNode texObj = mapper.createObjectNode();
            for (Variant v : entry.variants()) {
                texObj.put(v.type, v.path);
            }
            texArr.add(texObj);
        }
        playerNode.set("texture", texArr);
    }
}
