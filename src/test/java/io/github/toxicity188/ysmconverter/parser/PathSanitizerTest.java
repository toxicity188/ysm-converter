package io.github.toxicity188.ysmconverter.parser;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathSanitizerTest {

    @Test
    void testSanitizeWindowsFilename() {
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test:file"));
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test*file"));
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test?file"));
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test/file"));
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test\\file"));
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test\"file"));
        assertEquals("test_file_", PathSanitizer.sanitizeWindowsFilename("test<file>"));
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test|file"));
        assertEquals("test_file", PathSanitizer.sanitizeWindowsFilename("test_file.  "));
        assertEquals("unnamed_file", PathSanitizer.sanitizeWindowsFilename(""));
        assertEquals("unnamed_file", PathSanitizer.sanitizeWindowsFilename("..."));
    }

    @Test
    void testSafeResolve() {
        Path root = Path.of("output").toAbsolutePath();
        Path safe = PathSanitizer.safeResolve(root, "models/steve.json");
        assertTrue(safe.startsWith(root));

        assertThrows(SecurityException.class, () -> PathSanitizer.safeResolve(root, "../secret.txt"));
        assertThrows(SecurityException.class, () -> PathSanitizer.safeResolve(root, "a/../../secret.txt"));
    }
}
