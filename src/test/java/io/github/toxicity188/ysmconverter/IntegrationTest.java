package io.github.toxicity188.ysmconverter;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.api.YsmParseResult;
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
                                jsonReader.setStrictness(Strictness.LENIENT);
                                JsonElement parsed = JsonParser.parseReader(jsonReader);
                                assertNotNull(parsed);
                            }
                        }, "Extracted JSON must be well-formed: " + extractedPath);
                    } else if (fileName.endsWith(".png")) {
                        // PNG magic validation
                        byte[] sig = new byte[8];
                        try (var is = Files.newInputStream(extractedPath)) {
                            int read = is.read(sig);
                            assertEquals(8, read);
                            assertArrayEquals(PNG_SIGNATURE, sig, "Extracted PNG must have valid PNG header: " + extractedPath);
                        }
                    }

                    fileRelNames.add(modelOutDir.relativize(extractedPath).toString().replace('\\', '/'));
                }

                modelReports.add(new ModelReport(
                        uniqueName,
                        ysmFile.toString().replace('\\', '/'),
                        modelOutDir.toString().replace('\\', '/'),
                        "SUCCESS",
                        result.version().name(),
                        result.formatVersion(),
                        result.resourceCount(),
                        fileRelNames,
                        result.warnings(),
                        duration,
                        null
                ));
                successCount++;
                System.out.printf("  [SUCCESS] %s (%s format %d) - %d files in %d ms%n",
                        uniqueName, result.version().name(), result.formatVersion(), result.resourceCount(), duration);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                failureCount++;
                modelReports.add(new ModelReport(
                        uniqueName,
                        ysmFile.toString().replace('\\', '/'),
                        modelOutDir.toString().replace('\\', '/'),
                        "FAILED",
                        "UNKNOWN",
                        -1,
                        0,
                        List.of(),
                        List.of(),
                        duration,
                        e.getClass().getSimpleName() + ": " + e.getMessage()
                ));
                System.err.printf("  [FAILED]  %s - %s: %s%n", uniqueName, e.getClass().getSimpleName(), e.getMessage());
            }
        }

        OverallReport overallReport = new OverallReport(
                Instant.now().toString(),
                ysmFiles.size(),
                successCount,
                failureCount,
                versionCounts,
                formatVersionCounts,
                modelReports
        );

        Path reportJsonPath = reportsDir.resolve("parsing-report.json");
        try (var writer = Files.newBufferedWriter(reportJsonPath, StandardCharsets.UTF_8)) {
            gson.toJson(overallReport, writer);
        }
        System.out.println("Integration test complete. Report written to " + reportJsonPath);

        // Verification assertion
        assertEquals(0, failureCount, "All discovered models should parse successfully without failures");
        assertTrue(successCount > 0, "At least one model should have succeeded");
    }
}
