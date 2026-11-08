package io.github.toxicity188.ysmconverter.api;

import io.github.toxicity188.ysmconverter.format.YsmVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Result of a YSM model parsing and resource recovery operation.
 *
 * <pre>{@code
 * YsmParseResult result = YsmConverter.parse(Path.of("model.ysm"), Path.of("output"));
 * System.out.println("Extracted " + result.resourceCount() + " resources");
 * }</pre>
 *
 * @param version        detected major format version (V1, V2, or V3)
 * @param formatVersion  sub-format version (e.g. 1, 4, 15, 26, 32 for V3; 1 or 2 for legacy)
 * @param resourceCount  total number of extracted files
 * @param extractedFiles unmodifiable list of created file paths
 * @param warnings       unmodifiable list of non-fatal warnings encountered during parsing
 * @since 1.0.0
 */
public record YsmParseResult(
        @NotNull YsmVersion version,
        int formatVersion,
        int resourceCount,
        @NotNull @Unmodifiable List<@NotNull Path> extractedFiles,
        @NotNull @Unmodifiable List<@NotNull String> warnings
) {

    /**
     * Compact constructor validating non-null components and making defensive unmodifiable copies.
     */
    public YsmParseResult {
        Objects.requireNonNull(version, "version must not be null");
        extractedFiles = List.copyOf(extractedFiles);
        warnings = List.copyOf(warnings);
    }
}
