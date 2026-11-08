package io.github.toxicity188.ysmconverter.api;

import io.github.toxicity188.ysmconverter.format.VersionDetector;
import io.github.toxicity188.ysmconverter.format.YsmVersion;
import io.github.toxicity188.ysmconverter.parser.YsmParser;
import io.github.toxicity188.ysmconverter.parser.YsmParserV1;
import io.github.toxicity188.ysmconverter.parser.YsmParserV2;
import io.github.toxicity188.ysmconverter.parser.YsmParserV3;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Factory for creating version-appropriate {@link YsmParser} instances.
 *
 * <pre>{@code
 * YsmParser parser = YsmParserFactory.create(Path.of("model.ysm"));
 * parser.parse();
 * parser.saveToDirectory(Path.of("output"));
 * }</pre>
 *
 * @since 1.0.0
 */
public final class YsmParserFactory {

    private YsmParserFactory() {
    }

    /**
     * Creates a {@link YsmParser} by reading the given file and detecting its format version.
     *
     * @param path path to .ysm file
     * @return parser instance for the detected version
     * @throws IOException if reading the file fails
     * @throws IllegalArgumentException if file data is malformed or unsupported
     */
    @CheckReturnValue
    public static @NotNull YsmParser create(@NotNull Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        var data = Files.readAllBytes(path);
        return create(data);
    }

    /**
     * Creates a {@link YsmParser} by detecting the format version from raw byte data.
     *
     * @param data binary content of .ysm file
     * @return parser instance for the detected version
     * @throws IllegalArgumentException if data is null, malformed, or unsupported
     */
    @CheckReturnValue
    public static @NotNull YsmParser create(byte @NotNull [] data) {
        Objects.requireNonNull(data, "data must not be null");
        var version = VersionDetector.detectVersion(data);
        return create(version, data);
    }

    /**
     * Creates a {@link YsmParser} for an explicitly specified format version.
     *
     * @param version format version
     * @param data    binary content of .ysm file
     * @return parser instance
     */
    @CheckReturnValue
    public static @NotNull YsmParser create(@NotNull YsmVersion version, byte @NotNull [] data) {
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(data, "data must not be null");
        return switch (version) {
            case V1 -> new YsmParserV1(data);
            case V2 -> new YsmParserV2(data);
            case V3 -> new YsmParserV3(data);
        };
    }
}
