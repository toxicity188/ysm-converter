package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import io.github.toxicity188.ysmconverter.parser.PathSanitizer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * An editable Blockbench project and diagnostics about unsupported YSM runtime features.
 * The JSON tree is mutable; warnings are an immutable snapshot.
 * <pre>{@code
 * var result = BlockbenchConverter.convert(Path.of("extracted"));
 * result.write(Path.of("output/model.bbmodel"));
 * result.warnings().forEach(System.out::println);
 * }</pre>
 *
 * @param model self-contained project JSON, including embedded textures
 * @param warnings conversion limitations and resource selection diagnostics
 * @since 1.0.0
 */
public record BlockbenchResult(@NotNull JsonObject model, @NotNull List<String> warnings) {
    /**
     * Creates a result with a mutable project and a defensive copy of its diagnostics.
     *
     * @param model project JSON
     * @param warnings diagnostic messages
     * @throws NullPointerException if either argument is null
     * @since 1.0.0
     */
    public BlockbenchResult {
        Objects.requireNonNull(model, "model");
        warnings = List.copyOf(warnings);
    }

    /**
     * Writes this project as UTF-8 JSON, creating parent directories and replacing the destination.
     * For example, {@code result.write(Path.of("output/model.bbmodel"))}.
     *
     * @param destination output .bbmodel path
     * @throws IOException if the project cannot be written
     * @since 1.0.0
     */
    public void write(@NotNull Path destination) throws IOException {
        var json = new GsonBuilder().serializeNulls().disableHtmlEscaping().create().toJson(model);
        PathSanitizer.writeSafe(destination, json.getBytes(StandardCharsets.UTF_8));
    }
}
