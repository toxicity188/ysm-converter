package io.github.toxicity188.ysmconverter.format;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/**
 * Detects YSM format version from binary header data.
 *
 * <pre>{@code
 * YsmVersion ver = VersionDetector.detectVersion(data);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class VersionDetector {

    private static final int YSGP_MAGIC = 0x50475359; // "YSGP" in ASCII little-endian

    private VersionDetector() {
    }

    /**
     * Detects major YSM format version from raw byte data.
     *
     * @param data binary data of the .ysm file
     * @return detected {@link YsmVersion}
     * @throws IllegalArgumentException if data is malformed or smaller than 8 bytes
     * @throws UnsupportedOperationException if version is unsupported
     */
    @CheckReturnValue
    public static @NotNull YsmVersion detectVersion(byte @NotNull [] data) {
        if (data == null || data.length < 8) {
            throw new IllegalArgumentException("Invalid YSM file format: size must be at least 8 bytes");
        }

        // V3 detection: UTF-8 BOM (0xEF, 0xBB, 0xBF) followed by "YSGP" (0x59, 0x53, 0x47, 0x50)
        boolean hasBom = (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF;
        int magic3 = (data[3] & 0xFF) | ((data[4] & 0xFF) << 8) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 24);

        if (hasBom && magic3 == YSGP_MAGIC) {
            return YsmVersion.V3;
        }

        // V1 / V2 detection: starts directly with "YSGP"
        int magic2 = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8) | ((data[2] & 0xFF) << 16) | ((data[3] & 0xFF) << 24);
        int crypto2 = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);

        if (magic2 == YSGP_MAGIC) {
            if (crypto2 == 2) return YsmVersion.V2;
            if (crypto2 == 1) return YsmVersion.V1;
        }

        throw new UnsupportedOperationException("Unsupported YSM file format version");
    }

    /**
     * Extracts the format sub-version from V3 header text.
     *
     * @param headerText the header text preceding the null terminator
     * @return the parsed format number (e.g. 1, 4, 15, 26, 32)
     * @throws IllegalArgumentException if format tag is missing or invalid
     */
    @CheckReturnValue
    public static int extractV3Format(@NotNull String headerText) {
        int pos = headerText.indexOf("<format>");
        if (pos == -1) {
            throw new IllegalArgumentException("No <format> tag in header");
        }
        pos += 8;
        while (pos < headerText.length() && (headerText.charAt(pos) == ' ' || headerText.charAt(pos) == '\t')) {
            pos++;
        }
        int end = pos;
        while (end < headerText.length() && Character.isDigit(headerText.charAt(end))) {
            end++;
        }
        if (end == pos) {
            throw new IllegalArgumentException("Failed to parse <format> value");
        }
        return Integer.parseInt(headerText.substring(pos, end));
    }
}
