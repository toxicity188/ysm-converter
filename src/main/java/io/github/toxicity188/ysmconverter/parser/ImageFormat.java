package io.github.toxicity188.ysmconverter.parser;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Image format detector and file extension resolver for YSM model resources.
 *
 * <p>Identifies supported image formats based on header byte signatures:
 * <ul>
 *   <li>{@link #PNG} - Portable Network Graphics ({@code .png})</li>
 *   <li>{@link #WEBP} - WebP image container ({@code .webp})</li>
 *   <li>{@link #AVIF} - AV1 Image File Format ({@code .avif})</li>
 *   <li>{@link #JPEG} - Joint Photographic Experts Group ({@code .jpg})</li>
 *   <li>{@link #BMP} - Windows Bitmap ({@code .bmp})</li>
 *   <li>{@link #UNKNOWN} - Unrecognized image format</li>
 * </ul>
 *
 * <pre>{@code
 * ImageFormat format = ImageFormat.detect(imageBytes);
 * String filename = ImageFormat.resolveImageFilename("texture", imageBytes, ".png");
 * }</pre>
 *
 * @since 1.0.0
 */
public enum ImageFormat {

    /**
     * Portable Network Graphics format.
     */
    PNG(".png", "image/png"),

    /**
     * WebP image format.
     */
    WEBP(".webp", "image/webp"),

    /**
     * AV1 Image File Format.
     */
    AVIF(".avif", "image/avif"),

    /**
     * JPEG image format.
     */
    JPEG(".jpg", "image/jpeg"),

    /**
     * Windows Bitmap format.
     */
    BMP(".bmp", "image/bmp"),

    /**
     * Unknown or unsupported image format.
     */
    UNKNOWN("", "application/octet-stream");

    private final @NotNull String extension;
    private final @NotNull String mimeType;

    ImageFormat(@NotNull String extension, @NotNull String mimeType) {
        this.extension = extension;
        this.mimeType = mimeType;
    }

    /**
     * Returns the primary file extension associated with this format, including the leading dot.
     *
     * @return file extension, or empty string for {@link #UNKNOWN}
     */
    @CheckReturnValue
    public @NotNull String extension() {
        return extension;
    }

    /**
     * Returns the MIME type associated with this format.
     *
     * @return MIME type string
     */
    @CheckReturnValue
    public @NotNull String mimeType() {
        return mimeType;
    }

    /**
     * Maps an integer image format code from YSM metadata to an {@link ImageFormat}.
     *
     * <p>Codes in YSM V3 metadata:
     * <ul>
     *   <li>1 = BMP</li>
     *   <li>2 = PNG</li>
     *   <li>3 = JPEG</li>
     *   <li>4 = WEBP</li>
     *   <li>5 = AVIF</li>
     * </ul>
     *
     * @param code format integer code
     * @return corresponding {@link ImageFormat}, or {@link #UNKNOWN} if unmapped
     */
    @CheckReturnValue
    public static @NotNull ImageFormat fromFormatCode(int code) {
        return switch (code) {
            case 1 -> BMP;
            case 2 -> PNG;
            case 3 -> JPEG;
            case 4 -> WEBP;
            case 5 -> AVIF;
            default -> UNKNOWN;
        };
    }

    /**
     * Detects image format from raw byte payload signatures.
     *
     * @param data binary image data, or null
     * @return detected {@link ImageFormat}, or {@link #UNKNOWN} if unidentified
     */
    @CheckReturnValue
    public static @NotNull ImageFormat detect(byte @Nullable [] data) {
        if (data == null || data.length < 2) {
            return UNKNOWN;
        }

        // 1. PNG: 89 50 4E 47 0D 0A 1A 0A
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && (data[1] & 0xFF) == 0x50
                && (data[2] & 0xFF) == 0x4E
                && (data[3] & 0xFF) == 0x47
                && (data[4] & 0xFF) == 0x0D
                && (data[5] & 0xFF) == 0x0A
                && (data[6] & 0xFF) == 0x1A
                && (data[7] & 0xFF) == 0x0A) {
            return PNG;
        }

        // 2. WebP: Offset 0..3 == "RIFF", Offset 8..11 == "WEBP"
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return WEBP;
        }

        // 3. AVIF: ISO BMFF box with 'ftyp' at offset 4..7 and 'avif'/'avis' brand
        if (data.length >= 12
                && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') {
            int boxSize = ((data[0] & 0xFF) << 24)
                    | ((data[1] & 0xFF) << 16)
                    | ((data[2] & 0xFF) << 8)
                    | (data[3] & 0xFF);
            int limit = (boxSize > 0 && boxSize <= data.length) ? boxSize : Math.min(data.length, 64);
            for (int i = 8; i + 3 < limit; i += 4) {
                if (data[i] == 'a' && data[i + 1] == 'v' && data[i + 2] == 'i'
                        && (data[i + 3] == 'f' || data[i + 3] == 's')) {
                    return AVIF;
                }
            }
        }

        // 4. JPEG: FF D8 FF
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF) {
            return JPEG;
        }

        // 5. BMP: 42 4D ("BM")
        if (data[0] == 'B' && data[1] == 'M') {
            return BMP;
        }

        return UNKNOWN;
    }

    /**
     * Resolves a destination image filename with the appropriate extension matching detected data.
     *
     * <p>If the data signature matches a known image format, any preexisting image extension is replaced
     * with the detected format's canonical extension. If format detection fails and the original name has
     * an extension, the original name is preserved; otherwise the {@code defaultExtension} is appended.
     *
     * @param rawName          original or base resource name
     * @param data             raw image payload bytes
     * @param defaultExtension fallback extension to use if format cannot be detected (e.g. {@code ".png"})
     * @return sanitized filename with correct extension
     */
    @CheckReturnValue
    public static @NotNull String resolveImageFilename(
            @NotNull String rawName,
            byte @Nullable [] data,
            @NotNull String defaultExtension
    ) {
        Objects.requireNonNull(rawName, "rawName must not be null");
        Objects.requireNonNull(defaultExtension, "defaultExtension must not be null");

        ImageFormat detected = detect(data);
        String stripped = stripKnownImageExtension(rawName);

        if (detected != UNKNOWN) {
            return stripped + detected.extension();
        }

        if (!stripped.equals(rawName) || hasExtension(rawName)) {
            return rawName;
        }

        String defExt = defaultExtension.startsWith(".") ? defaultExtension : ("." + defaultExtension);
        return rawName + defExt;
    }

    /**
     * Strips any known image extension from the provided name.
     *
     * @param name filename or path string
     * @return name without recognized image extension
     */
    @CheckReturnValue
    public static @NotNull String stripKnownImageExtension(@NotNull String name) {
        Objects.requireNonNull(name, "name must not be null");
        String lower = name.toLowerCase(Locale.ROOT);
        for (ImageFormat format : values()) {
            if (format != UNKNOWN && lower.endsWith(format.extension())) {
                return name.substring(0, name.length() - format.extension().length());
            }
        }
        if (lower.endsWith(".jpeg")) {
            return name.substring(0, name.length() - 5);
        }
        return name;
    }

    private static boolean hasExtension(@NotNull String name) {
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String filename = (slash >= 0) ? name.substring(slash + 1) : name;
        int dot = filename.lastIndexOf('.');
        return dot > 0 && dot < filename.length() - 1;
    }
}
