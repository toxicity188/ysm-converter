package io.github.toxicity188.ysmconverter;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.api.YsmParseResult;
import io.github.toxicity188.ysmconverter.parser.ImageFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    record ModelReport(
            String name,
            String sourcePath,
            String outputPath,
            String status,
            String version,
            int formatVersion,
            int resourceCount,
            int pngCount,
            int webpCount,
            int avifCount,
            List<String> extractedFiles,
            List<String> warnings,
            long durationMs,
            String errorMessage
    ) {
    }

    record OverallReport(
            String timestamp,
            int totalDiscovered,
            int totalSucceeded,
            int totalFailed,
            Map<String, Integer> versionCounts,
            Map<String, Integer> formatVersionCounts,
            int totalPngImages,
            int totalWebpImages,
            int totalAvifImages,
            int totalOtherImages,
            int totalInvalidImages,
            List<ModelReport> models
    ) {
    }

    @Test
    void testAllRepositoryModels() throws IOException {
        Path repoDir = Path.of("external/Yes-Steve-Model-Repo");
        if (!Files.exists(repoDir)) {
            System.out.println("Test model repository not found at " + repoDir + "; skipping integration tests.");
            return;
        }

        List<Path> ysmFiles;
        try (Stream<Path> walk = Files.walk(repoDir)) {
            ysmFiles = walk.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".ysm"))
                    .sorted()
                    .toList();
        }

        if (ysmFiles.isEmpty()) {
            System.out.println("No .ysm files discovered in repository; skipping.");
            return;
        }

        System.out.println("Discovered " + ysmFiles.size() + " .ysm models in " + repoDir);

        Path testOutputDir = Path.of("build/ysm-parser-tests");
        Path extractedBase = testOutputDir.resolve("extracted");
        Path reportsDir = testOutputDir.resolve("reports");
        Files.createDirectories(extractedBase);
        Files.createDirectories(reportsDir);

        List<ModelReport> modelReports = new ArrayList<>();
        Map<String, Integer> versionCounts = new LinkedHashMap<>();
        Map<String, Integer> formatVersionCounts = new LinkedHashMap<>();
        int successCount = 0;
        int failureCount = 0;
        int totalPng = 0;
        int totalWebp = 0;
        int totalAvif = 0;
        int totalOther = 0;
        int totalInvalid = 0;

        for (Path ysmFile : ysmFiles) {
            Path relPath = repoDir.relativize(ysmFile);
            String uniqueName = relPath.toString().replace('\\', '_').replace('/', '_');
            if (uniqueName.toLowerCase(Locale.ROOT).endsWith(".ysm")) {
                uniqueName = uniqueName.substring(0, uniqueName.length() - 4);
            }

            Path modelOutDir = extractedBase.resolve(uniqueName);
            long startTime = System.currentTimeMillis();

            try {
                YsmParseResult result = YsmConverter.parse(ysmFile, modelOutDir);
                long duration = System.currentTimeMillis() - startTime;

                versionCounts.merge(result.version().name(), 1, Integer::sum);
                formatVersionCounts.merge(String.valueOf(result.formatVersion()), 1, Integer::sum);

                int modelPng = 0;
                int modelWebp = 0;
                int modelAvif = 0;

                // Verify extracted files integrity
                List<String> fileRelNames = new ArrayList<>();
                for (Path extractedPath : result.extractedFiles()) {
                    assertTrue(Files.exists(extractedPath), "Extracted file must exist: " + extractedPath);
                    long fileSize = Files.size(extractedPath);
                    assertTrue(fileSize > 0, "Extracted file must not be empty: " + extractedPath);

                    String fileName = extractedPath.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (fileName.endsWith(".json")) {
                        // Semantic JSON validation
                        assertDoesNotThrow(() -> {
                            try (var reader = Files.newBufferedReader(extractedPath, StandardCharsets.UTF_8)) {
                                var jsonReader = new JsonReader(reader);
                                jsonReader.setLenient(true);
                                JsonElement parsed = JsonParser.parseReader(jsonReader);
                                assertNotNull(parsed);
                            }
                        }, "Extracted JSON must be well-formed: " + extractedPath);
                    } else if (isImageFile(fileName)) {
                        byte[] fileBytes = Files.readAllBytes(extractedPath);
                        ImageFormat detected = ImageFormat.detect(fileBytes);

                        switch (detected) {
                            case PNG -> {
                                validatePng(extractedPath, fileBytes);
                                modelPng++;
                                totalPng++;
                            }
                            case WEBP -> {
                                validateWebp(extractedPath, fileBytes);
                                modelWebp++;
                                totalWebp++;
                            }
                            case AVIF -> {
                                validateAvif(extractedPath, fileBytes);
                                modelAvif++;
                                totalAvif++;
                            }
                            case JPEG -> {
                                validateJpeg(extractedPath, fileBytes);
                                totalOther++;
                            }
                            case BMP -> {
                                validateBmp(extractedPath, fileBytes);
                                totalOther++;
                            }
                            case UNKNOWN -> {
                                totalInvalid++;
                                fail("Extracted image has unrecognized or corrupt format: " + extractedPath);
                            }
                        }

                        // Validate filename extension matches detected format
                        assertEquals(detected.extension(), getExtension(fileName),
                                "Image filename extension mismatch with detected signature: " + extractedPath);
                    }

                    fileRelNames.add(modelOutDir.relativize(extractedPath).toString().replace('\\', '/'));
                }

                // Verify resource references consistency in ysm.json
                Path ysmJsonPath = modelOutDir.resolve("ysm.json");
                if (Files.exists(ysmJsonPath)) {
                    validateYsmJsonResourceReferences(ysmJsonPath, modelOutDir);
                }

                modelReports.add(new ModelReport(
                        uniqueName,
                        ysmFile.toString().replace('\\', '/'),
                        modelOutDir.toString().replace('\\', '/'),
                        "SUCCESS",
                        result.version().name(),
                        result.formatVersion(),
                        result.resourceCount(),
                        modelPng,
                        modelWebp,
                        modelAvif,
                        fileRelNames,
                        result.warnings(),
                        duration,
                        null
                ));
                successCount++;
                System.out.printf("  [SUCCESS] %s (%s format %d) - %d files (PNG=%d, WebP=%d, AVIF=%d) in %d ms%n",
                        uniqueName, result.version().name(), result.formatVersion(), result.resourceCount(),
                        modelPng, modelWebp, modelAvif, duration);
            } catch (Throwable t) {
                long duration = System.currentTimeMillis() - startTime;
                failureCount++;
                totalInvalid++;
                modelReports.add(new ModelReport(
                        uniqueName,
                        ysmFile.toString().replace('\\', '/'),
                        modelOutDir.toString().replace('\\', '/'),
                        "FAILED",
                        "UNKNOWN",
                        -1,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        duration,
                        t.getClass().getSimpleName() + ": " + t.getMessage()
                ));
                System.err.printf("  [FAILED]  %s - %s: %s%n", uniqueName, t.getClass().getSimpleName(), t.getMessage());
            }
        }

        OverallReport overallReport = new OverallReport(
                Instant.now().toString(),
                ysmFiles.size(),
                successCount,
                failureCount,
                versionCounts,
                formatVersionCounts,
                totalPng,
                totalWebp,
                totalAvif,
                totalOther,
                totalInvalid,
                modelReports
        );

        Path reportJsonPath = reportsDir.resolve("parsing-report.json");
        try (var writer = Files.newBufferedWriter(reportJsonPath, StandardCharsets.UTF_8)) {
            gson.toJson(overallReport, writer);
        }
        System.out.println("Integration test complete. Report written to " + reportJsonPath);
        System.out.printf("Images summary: PNG=%d, WebP=%d, AVIF=%d, Other=%d, Invalid=%d%n",
                totalPng, totalWebp, totalAvif, totalOther, totalInvalid);

        // Verification assertion
        assertEquals(0, failureCount, failureCount + " models failed during integration testing");
        assertTrue(successCount > 0, "At least one model should have succeeded");
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".avif")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp");
    }

    private static String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    private static void validatePng(Path path, byte[] data) {
        assertTrue(data.length >= 8, "PNG file too small: " + path);
        for (int i = 0; i < 8; i++) {
            assertEquals(PNG_SIGNATURE[i], data[i], "Invalid PNG signature byte at " + i + ": " + path);
        }
        // First chunk must be IHDR
        if (data.length >= 16) {
            assertEquals('I', data[12], "First chunk must be IHDR: " + path);
            assertEquals('H', data[13], "First chunk must be IHDR: " + path);
            assertEquals('D', data[14], "First chunk must be IHDR: " + path);
            assertEquals('R', data[15], "First chunk must be IHDR: " + path);
        }
    }

    private static void validateWebp(Path path, byte[] data) {
        assertTrue(data.length >= 12, "WebP file too small: " + path);
        assertEquals('R', data[0], "WebP must start with RIFF: " + path);
        assertEquals('I', data[1], "WebP must start with RIFF: " + path);
        assertEquals('F', data[2], "WebP must start with RIFF: " + path);
        assertEquals('F', data[3], "WebP must start with RIFF: " + path);

        assertEquals('W', data[8], "RIFF container must contain WEBP identifier: " + path);
        assertEquals('E', data[9], "RIFF container must contain WEBP identifier: " + path);
        assertEquals('B', data[10], "RIFF container must contain WEBP identifier: " + path);
        assertEquals('P', data[11], "RIFF container must contain WEBP identifier: " + path);

        // RIFF chunk size validation
        int riffSize = (data[4] & 0xFF) | ((data[5] & 0xFF) << 8) | ((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 24);
        assertTrue(riffSize > 0, "WebP RIFF chunk size must be positive: " + path);
        assertTrue(data.length >= riffSize + 8, "WebP file length must be at least RIFF chunk size + 8: " + path);

        // First chunk header validation (VP8 , VP8L, or VP8X)
        if (data.length >= 16) {
            assertEquals('V', data[12], "WebP chunk must start with VP8: " + path);
            assertEquals('P', data[13], "WebP chunk must start with VP8: " + path);
            assertEquals('8', data[14], "WebP chunk must start with VP8: " + path);
        }
    }

    private static void validateAvif(Path path, byte[] data) {
        assertTrue(data.length >= 12, "AVIF file too small: " + path);
        assertEquals('f', data[4], "AVIF must contain ftyp box: " + path);
        assertEquals('t', data[5], "AVIF must contain ftyp box: " + path);
        assertEquals('y', data[6], "AVIF must contain ftyp box: " + path);
        assertEquals('p', data[7], "AVIF must contain ftyp box: " + path);
        assertEquals(ImageFormat.AVIF, ImageFormat.detect(data), "File must be recognized as AVIF: " + path);
    }

    private static void validateJpeg(Path path, byte[] data) {
        assertTrue(data.length >= 3, "JPEG file too small: " + path);
        assertEquals((byte) 0xFF, data[0], "JPEG must start with 0xFFD8FF: " + path);
        assertEquals((byte) 0xD8, data[1], "JPEG must start with 0xFFD8FF: " + path);
        assertEquals((byte) 0xFF, data[2], "JPEG must start with 0xFFD8FF: " + path);
    }

    private static void validateBmp(Path path, byte[] data) {
        assertTrue(data.length >= 2, "BMP file too small: " + path);
        assertEquals('B', data[0], "BMP must start with 'BM': " + path);
        assertEquals('M', data[1], "BMP must start with 'BM': " + path);
    }

    private static void validateYsmJsonResourceReferences(Path ysmJsonPath, Path modelOutDir) {
        try (var reader = Files.newBufferedReader(ysmJsonPath, StandardCharsets.UTF_8)) {
            var jsonReader = new JsonReader(reader);
            jsonReader.setLenient(true);
            JsonObject root = JsonParser.parseReader(jsonReader).getAsJsonObject();

            // Check metadata.authors[*].avatar
            if (root.has("metadata") && root.get("metadata").isJsonObject()) {
                JsonObject meta = root.getAsJsonObject("metadata");
                if (meta.has("authors") && meta.get("authors").isJsonArray()) {
                    for (JsonElement authorElem : meta.getAsJsonArray("authors")) {
                        if (authorElem.isJsonObject()) {
                            JsonObject author = authorElem.getAsJsonObject();
                            if (author.has("avatar") && !author.get("avatar").getAsString().isEmpty()) {
                                validateReferencedResource(modelOutDir, author.get("avatar").getAsString(), false);
                            }
                        }
                    }
                }
            }

            // Check files.player.texture[*]
            if (root.has("files") && root.get("files").isJsonObject()) {
                JsonObject files = root.getAsJsonObject("files");
                if (files.has("player") && files.get("player").isJsonObject()) {
                    JsonObject player = files.getAsJsonObject("player");
                    if (player.has("texture") && player.get("texture").isJsonArray()) {
                        for (JsonElement texElem : player.getAsJsonArray("texture")) {
                            if (texElem.isJsonObject()) {
                                JsonObject tex = texElem.getAsJsonObject();
                                for (String key : List.of("uv", "normal", "specular")) {
                                    if (tex.has(key) && !tex.get(key).getAsString().isEmpty()) {
                                        validateReferencedResource(modelOutDir, tex.get(key).getAsString(), true);
                                    }
                                }
                            }
                        }
                    }
                }

                // Check other categories (e.g. vehicles, sub_entities)
                for (var catEntry : files.entrySet()) {
                    if (catEntry.getKey().equals("player")) continue;
                    if (catEntry.getValue().isJsonObject()) {
                        JsonObject catObj = catEntry.getValue().getAsJsonObject();
                        for (var subEntry : catObj.entrySet()) {
                            if (subEntry.getValue().isJsonObject()) {
                                JsonObject subObj = subEntry.getValue().getAsJsonObject();
                                if (subObj.has("texture") && !subObj.get("texture").getAsString().isEmpty()) {
                                    validateReferencedResource(modelOutDir, subObj.get("texture").getAsString(), true);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            fail("Failed to parse and validate ysm.json resource references in " + ysmJsonPath + ": " + e.getMessage());
        }
    }

    private static void validateReferencedResource(Path modelOutDir, String relativePath, boolean mustExist) throws IOException {
        Path target = modelOutDir.resolve(relativePath);
        if (!Files.exists(target)) {
            if (mustExist) {
                fail("Referenced resource does not exist on disk: " + relativePath + " in " + modelOutDir);
            }
            return;
        }
        assertTrue(Files.size(target) > 0, "Referenced resource must not be empty: " + relativePath);

        if (isImageFile(relativePath)) {
            byte[] bytes = Files.readAllBytes(target);
            ImageFormat format = ImageFormat.detect(bytes);
            assertNotEquals(ImageFormat.UNKNOWN, format, "Referenced image has unknown format: " + relativePath);
            assertEquals(format.extension(), getExtension(relativePath.toLowerCase(Locale.ROOT)),
                    "Referenced image extension does not match detected image format: " + relativePath);
        }
    }
}
