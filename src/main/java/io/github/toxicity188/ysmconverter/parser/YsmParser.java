package io.github.toxicity188.ysmconverter.parser;

import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.api.YsmParseResult;
import io.github.toxicity188.ysmconverter.api.YsmParserFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Base interface for YSM model parsers across all supported format versions.
 *
 * <pre>{@code
 * YsmParseResult result = YsmParser.parse(Path.of("model.ysm"), Path.of("output"));
 * System.out.println("Extracted " + result.resourceCount() + " resources.");
 * }</pre>
 *
 * @since 1.0.0
 */
public interface YsmParser {

    /**
     * Creates a {@link YsmParser} for the specified file.
     *
     * @param path path to .ysm file
     * @return parser instance for the detected version
     * @throws IOException if reading the file fails
     */
    static YsmParser of(Path path) throws IOException {
        return YsmParserFactory.create(path);
    }

    /**
     * Creates a {@link YsmParser} for raw .ysm byte data.
     *
     * @param data binary content of .ysm file
     * @return parser instance for the detected version
     */
    static YsmParser of(byte[] data) {
        return YsmParserFactory.create(data);
    }

    /**
     * Parses a .ysm file and extracts its resources to the output directory.
     *
     * @param ysmFilePath path to the .ysm file
     * @param outputDir   destination directory
     * @return parsing result summary
     * @throws IOException if parsing or extraction fails
     */
    static YsmParseResult parse(Path ysmFilePath, Path outputDir) throws IOException {
        return YsmConverter.parse(ysmFilePath, outputDir);
    }

    /**
     * Parses raw .ysm data and extracts its resources to the output directory.
     *
     * @param ysmBytes  binary content of .ysm file
     * @param outputDir destination directory
     * @return parsing result summary
     * @throws IOException if parsing or extraction fails
     */
    static YsmParseResult parse(byte[] ysmBytes, Path outputDir) throws IOException {
        return YsmConverter.parse(ysmBytes, outputDir);
    }

    /**
     * Gets the major YSGP version number (1, 2, or 3).
     *
     * @return YSGP version
     */
    int getYSGPVersion();

    /**
     * Gets the format sub-version (e.g. 1, 4, 15, 26, 32 for V3; 1 for V1; 2 for V2).
     *
     * @return format version number
     */
    default int getFormatVersion() {
        return getYSGPVersion();
    }

    /**
     * Gets the non-fatal warnings encountered during parsing.
     *
     * @return unmodifiable list of warning messages
     */
    default List<String> getWarnings() {
        return List.of();
    }

    /**
     * Performs decryption, decompression, and binary deserialization of the model data.
     *
     * @throws IOException if binary parsing or I/O fails
     */
    void parse() throws IOException;

    /**
     * Gets the raw decrypted data buffer before binary deserialization.
     *
     * @return decrypted byte array
     */
    byte[] getDecryptedData();

    /**
     * Saves all recovered model resources into the specified output directory.
     *
     * @param outputDirectory directory where files will be created
     * @throws IOException if file creation or writing fails
     */
    void saveToDirectory(Path outputDirectory) throws IOException;

    /**
     * Gets a map of all recovered resource relative paths to their binary contents.
     *
     * @return unmodifiable map of relative path to resource bytes
     */
    Map<String, byte[]> getExtractedResources();
}
