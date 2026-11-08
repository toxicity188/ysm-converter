package io.github.toxicity188.ysmconverter.parser;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility functions for path sanitization and safe file extraction without path traversal vulnerabilities.
 *
 * @since 1.0.0
 */
public final class PathSanitizer {

    private static final String INVALID_WINDOWS_CHARS = "\\/:*?\"<>|";

    private PathSanitizer() {
    }

    /**
     * Replaces characters that are illegal in Windows filenames with an underscore,
     * stripping trailing spaces or dots.
     *
     * @param filename filename string
     * @return sanitized filename safe for filesystem operations
     */
    @CheckReturnValue
    public static @NotNull String sanitizeWindowsFilename(@Nullable String filename) {
        if (filename == null || filename.isEmpty()) {
            return "unnamed_file";
        }

        var sb = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            if (INVALID_WINDOWS_CHARS.indexOf(c) != -1) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }

        while (!sb.isEmpty() && (sb.charAt(sb.length() - 1) == ' ' || sb.charAt(sb.length() - 1) == '.')) {
            sb.setLength(sb.length() - 1);
        }

        if (sb.isEmpty()) {
            return "unnamed_file";
        }

        return sb.toString();
    }

    /**
     * Safely resolves a child path within a root directory, preventing directory traversal attacks.
     *
     * @param rootDir      root output directory
     * @param relativePath relative path to resolve
     * @return normalized target path
     * @throws SecurityException if the resolved path attempts to escape the root directory
     */
    @CheckReturnValue
    public static @NotNull Path safeResolve(@NotNull Path rootDir, @NotNull String relativePath) {
        Path resolved = rootDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(rootDir.normalize())) {
            throw new SecurityException("Path traversal attempt detected: " + relativePath);
        }
        return resolved;
    }

    /**
     * Writes binary data to a file safely, creating parent directories as needed.
     *
     * @param targetPath absolute or normalized target path
     * @param data       bytes to write
     * @throws IOException if writing fails
     */
    public static void writeSafe(@NotNull Path targetPath, byte @NotNull [] data) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(targetPath, data);
    }
}
