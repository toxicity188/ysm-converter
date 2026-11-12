package io.github.toxicity188.ysmconverter.api;

import io.github.toxicity188.ysmconverter.format.YsmVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decrypted YSM resources and metadata, independent of filesystem extraction.
 * The resource map is a shallow snapshot: its structure is unmodifiable, but byte arrays remain mutable.
 * <pre>{@code
 * var result = YsmConverter.parse(inputStream);
 * var model = BlockbenchConverter.convert(result.resources());
 * result.warnings().forEach(System.out::println);
 * }</pre>
 *
 * @param version major YSM format version
 * @param formatVersion detailed format version reported by the parser
 * @param resources relative resource names and decrypted bytes
 * @param warnings non-fatal parsing warnings
 * @since 1.0.0
 */
public record YsmResourceResult(
        @NotNull YsmVersion version,
        int formatVersion,
        @NotNull @Unmodifiable Map<String, byte[]> resources,
        @NotNull @Unmodifiable List<String> warnings
) {
    /**
     * Copies the map structure and warning list without duplicating resource bytes.
     *
     * @param version major format version
     * @param formatVersion detailed format version
     * @param resources decrypted resource map
     * @param warnings parsing diagnostics
     * @throws NullPointerException if a component is null
     * @since 1.0.0
     */
    public YsmResourceResult {
        Objects.requireNonNull(version, "version must not be null");
        resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        warnings = List.copyOf(warnings);
    }
}
