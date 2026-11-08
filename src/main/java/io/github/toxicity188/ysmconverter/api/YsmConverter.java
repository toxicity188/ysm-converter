package io.github.toxicity188.ysmconverter.api;

import io.github.toxicity188.ysmconverter.format.VersionDetector;
import io.github.toxicity188.ysmconverter.format.YsmVersion;
import io.github.toxicity188.ysmconverter.parser.PathSanitizer;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Main public entry point for converting and extracting YSM model files.
 *
 * <pre>{@code
 * YsmParseResult result = YsmConverter.parse(Path.of("model.ysm"), Path.of("output"));
 * System.out.println("Extracted " + result.resourceCount() + " resources.");
 * }</pre>
 *
 * @since 1.0.0
 */
public final class YsmConverter {

    private YsmConverter() {
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
        var data = Files.readAllBytes(ysmFilePath);
        return parse(data, outputDir);
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

        var version = VersionDetector.detectVersion(ysmData);
        var parser = YsmParserFactory.create(version, ysmData);
        parser.parse();
        parser.saveToDirectory(outputDir);

        var extractedFiles = new ArrayList<Path>();
        for (var key : parser.getExtractedResources().keySet()) {
            extractedFiles.add(PathSanitizer.safeResolve(outputDir, key));
        }

        return new YsmParseResult(
                version,
                parser.getFormatVersion(),
                extractedFiles.size(),
                List.copyOf(extractedFiles),
                List.copyOf(parser.getWarnings())
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
