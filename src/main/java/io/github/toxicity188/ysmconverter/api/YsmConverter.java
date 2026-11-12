package io.github.toxicity188.ysmconverter.api;

import io.github.toxicity188.ysmconverter.format.VersionDetector;
import io.github.toxicity188.ysmconverter.format.YsmVersion;
import io.github.toxicity188.ysmconverter.parser.PathSanitizer;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Main public entry point for in-memory YSM decryption and optional resource extraction.
 *
 * <pre>{@code
 * var result = YsmConverter.parse(inputStream);
 * System.out.println("Decoded " + result.resources().size() + " resources.");
 * }</pre>
 *
 * @since 1.0.0
 */
public final class YsmConverter {

    private YsmConverter() {
    }

    /**
     * Decrypts and parses bytes entirely in memory. For example, {@code decrypt(ysmBytes)}.
     * Use {@link #parse(byte[])} when version information and warnings are also needed.
     *
     * @param ysmData encrypted YSM bytes
     * @return resource names mapped to their decrypted contents; map structure is unmodifiable
     * @throws IOException if decryption or parsing fails
     * @throws IllegalArgumentException if the header or parsed data is malformed
     * @throws UnsupportedOperationException if the format version is unsupported
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull Map<String, byte[]> decrypt(byte @NotNull [] ysmData) throws IOException {
        return parse(ysmData).resources();
    }

    /**
     * Reads and decrypts a stream in memory without closing it. For example, {@code decrypt(input)}.
     *
     * @param input caller-owned stream, read from its current position to EOF
     * @return decrypted resource map
     * @throws IOException if reading, decryption or parsing fails
     * @throws IllegalArgumentException if the header or parsed data is malformed
     * @throws UnsupportedOperationException if the format version is unsupported
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull Map<String, byte[]> decrypt(@NotNull InputStream input) throws IOException {
        return parse(input).resources();
    }

    /**
     * Reads a YSM file and returns resources without extracting files.
     * For example, {@code decrypt(Path.of("model.ysm"))}.
     *
     * @param path encrypted YSM file
     * @return decrypted resource map
     * @throws IOException if reading, decryption or parsing fails
     * @throws IllegalArgumentException if the header or parsed data is malformed
     * @throws UnsupportedOperationException if the format version is unsupported
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull Map<String, byte[]> decrypt(@NotNull Path path) throws IOException {
        return parse(path).resources();
    }

    /**
     * Decrypts and parses bytes in memory, retaining version information and warnings.
     * For example, {@code var resources = parse(ysmBytes).resources()}.
     *
     * @param ysmData encrypted YSM bytes
     * @return resources and parsing metadata, without any extracted paths
     * @throws IOException if decryption or parsing fails
     * @throws IllegalArgumentException if the header or parsed data is malformed
     * @throws UnsupportedOperationException if the format version is unsupported
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull YsmResourceResult parse(byte @NotNull [] ysmData) throws IOException {
        Objects.requireNonNull(ysmData, "ysmData must not be null");
        var version = VersionDetector.detectVersion(ysmData);
        var parser = YsmParserFactory.create(version, ysmData);
        parser.parse();
        return new YsmResourceResult(version, parser.getFormatVersion(), parser.getExtractedResources(), parser.getWarnings());
    }

    /**
     * Parses a stream in memory without closing it, on either success or failure.
     * For example, {@code var result = parse(input)}.
     *
     * @param input caller-owned stream, read from its current position to EOF
     * @return resources, version information and warnings
     * @throws IOException if reading, decryption or parsing fails
     * @throws IllegalArgumentException if the header or parsed data is malformed
     * @throws UnsupportedOperationException if the format version is unsupported
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull YsmResourceResult parse(@NotNull InputStream input) throws IOException {
        Objects.requireNonNull(input, "input must not be null");
        return parse(input.readAllBytes());
    }

    /**
     * Reads a YSM file through the memory API and closes the input stream.
     * For example, {@code var result = parse(Path.of("model.ysm"))}.
     *
     * @param path encrypted YSM file
     * @return resources, version information and warnings; no files are written
     * @throws IOException if reading, decryption or parsing fails
     * @throws IllegalArgumentException if the header or parsed data is malformed
     * @throws UnsupportedOperationException if the format version is unsupported
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull YsmResourceResult parse(@NotNull Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        try (var input = Files.newInputStream(path)) {
            return parse(input);
        }
    }

    /**
     * Parses a YSM model file from the specified path and extracts its resources to the output directory.
     *
     * @param ysmFilePath path to the .ysm file
     * @param outputDir   destination directory for extracted resources
     * @return parsing result containing summary metadata
     * @throws IOException if reading, parsing, or file extraction fails
     */
    @CheckReturnValue
    public static @NotNull YsmParseResult parse(@NotNull Path ysmFilePath, @NotNull Path outputDir) throws IOException {
        Objects.requireNonNull(ysmFilePath, "ysmFilePath must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        return extract(parse(ysmFilePath), outputDir);
    }

    /**
     * Parses YSM model data from a byte array and extracts its resources to the output directory.
     *
     * @param ysmData   raw .ysm binary bytes
     * @param outputDir destination directory for extracted resources
     * @return parsing result containing summary metadata
     * @throws IOException if parsing or file extraction fails
     */
    @CheckReturnValue
    public static @NotNull YsmParseResult parse(byte @NotNull [] ysmData, @NotNull Path outputDir) throws IOException {
        Objects.requireNonNull(ysmData, "ysmData must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");

        return extract(parse(ysmData), outputDir);
    }

    private static YsmParseResult extract(YsmResourceResult result, Path outputDir) throws IOException {
        var extractedFiles = new ArrayList<Path>();
        for (var resource : result.resources().entrySet()) {
            var path = PathSanitizer.safeResolve(outputDir, resource.getKey());
            PathSanitizer.writeSafe(path, resource.getValue());
            extractedFiles.add(path);
        }

        return new YsmParseResult(
                result.version(),
                result.formatVersion(),
                extractedFiles.size(),
                List.copyOf(extractedFiles),
                result.warnings()
        );
    }

    /**
     * Detects the major YSM format version of a file.
     *
     * @param ysmFilePath path to the .ysm file
     * @return detected major version
     * @throws IOException if reading the file fails
     */
    @CheckReturnValue
    public static @NotNull YsmVersion getVersion(@NotNull Path ysmFilePath) throws IOException {
        Objects.requireNonNull(ysmFilePath, "ysmFilePath must not be null");
        var data = Files.readAllBytes(ysmFilePath);
        return getVersion(data);
    }

    /**
     * Detects the major YSM format version of raw data.
     *
     * @param ysmData raw .ysm binary bytes
     * @return detected major version
     */
    @CheckReturnValue
    public static @NotNull YsmVersion getVersion(byte @NotNull [] ysmData) {
        return VersionDetector.detectVersion(ysmData);
    }

    /**
     * Gets the detailed format version number of a .ysm file.
     *
     * @param ysmFilePath path to the .ysm file
     * @return format version number
     * @throws IOException if reading the file fails
     */
    @CheckReturnValue
    public static int getFormatVersion(@NotNull Path ysmFilePath) throws IOException {
        Objects.requireNonNull(ysmFilePath, "ysmFilePath must not be null");
        var data = Files.readAllBytes(ysmFilePath);
        return getFormatVersion(data);
    }

    /**
     * Gets the detailed format version number from raw data.
     *
     * @param ysmData raw .ysm binary bytes
     * @return format version number
     */
    @CheckReturnValue
    public static int getFormatVersion(byte @NotNull [] ysmData) {
        var parser = YsmParserFactory.create(ysmData);
        return parser.getFormatVersion();
    }
}
