package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional compatibility tests; all generated third-party assets stay in ignored build output. */
class BlockbenchExamplesTest {
    @Test
    void recoversUnreferencedSuzuharaColorTextureInMemory() throws IOException {
        var directory = Path.of("external/examples/_Vtuber_Vtuber_Suzuhara-Lulu");
        assumeTrue(Files.isDirectory(directory), "Optional Suzuhara example is not installed");
        var resources = new LinkedHashMap<String, byte[]>();
        try (var paths = Files.walk(directory)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                resources.put(directory.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        var result = BlockbenchConverter.convertWithWarnings(resources);
        var model = result.model();
        assertEquals(704, model.getAsJsonArray("elements").size());
        assertEquals(1, model.getAsJsonArray("textures").size());
        var texture = model.getAsJsonArray("textures").get(0).getAsJsonObject();
        assertEquals("suzuhara_ruru_normal.png", texture.get("name").getAsString());
        assertEquals(256, texture.get("uv_width").getAsInt());
        assertEquals(256, texture.get("uv_height").getAsInt());
        var bytes = Base64.getDecoder().decode(texture.get("source").getAsString().split(",", 2)[1]);
        // ImageIO.read(ImageInputStream) closes the stream after a successful read.
        var expected = ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(resources.get("suzuhara_ruru_normal.png"))));
        var actual = ImageIO.read(new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes)));
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertArrayEquals(expected.getRGB(0, 0, 256, 256, null, 0, 256), actual.getRGB(0, 0, 256, 256, null, 0, 256));
        for (var element : model.getAsJsonArray("elements")) {
            for (var face : element.getAsJsonObject().getAsJsonObject("faces").asMap().values()) {
                var index = face.getAsJsonObject().get("texture");
                if (!index.isJsonNull()) assertEquals(0, index.getAsInt());
            }
        }
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Texture fallback selected")));
    }

    @TestFactory
    List<DynamicTest> convertsExtractedExamples() throws IOException {
        var directory = Path.of("external/examples");
        assumeTrue(Files.isDirectory(directory), "Optional extracted examples are not installed");
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isDirectory).sorted().map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                var converted = BlockbenchConverter.convert(path);
                var project = converted.model();
                var geometryPath = path.resolve("main.json");
                if (Files.exists(path.resolve("ysm.json"))) {
                    var manifest = read(path.resolve("ysm.json"));
                    geometryPath = path.resolve(manifest.getAsJsonObject("files").getAsJsonObject("player").getAsJsonObject("model").get("main").getAsString());
                }
                var geometry = read(geometryPath).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
                var cubes = 0;
                for (var bone : geometry.getAsJsonArray("bones")) {
                    var object = bone.getAsJsonObject();
                    if (object.has("cubes")) cubes += object.getAsJsonArray("cubes").size();
                }
                assertEquals(cubes, project.getAsJsonArray("elements").size());
                var groups = new HashSet<String>();
                var elements = new HashSet<String>();
                inspectOutliner(project.getAsJsonArray("outliner"), groups, elements);
                assertEquals(geometry.getAsJsonArray("bones").size(), groups.size());
                assertEquals(cubes, elements.size());
                for (var element : project.getAsJsonArray("elements")) assertTrue(elements.contains(element.getAsJsonObject().get("uuid").getAsString()));
                for (var animation : project.getAsJsonArray("animations")) {
                    for (var animator : animation.getAsJsonObject().getAsJsonObject("animators").keySet()) assertTrue(animator.equals("effects") || groups.contains(animator));
                }
                var output = Path.of("build/blockbench-examples").resolve(path.getFileName().toString());
                converted.write(output.resolve("model.bbmodel"));
                Files.write(output.resolve("warnings.txt"), converted.warnings());
                System.out.printf("%s: %d cubes, %d bones, %d textures, %d animations, %d warnings%n", path.getFileName(), cubes, groups.size(),
                        project.getAsJsonArray("textures").size(), project.getAsJsonArray("animations").size(), converted.warnings().size());
                if (path.getFileName().toString().equals("_BlueArchive_BA_Asuna")) compareAsunaOriginal(path, project);
            })).toList();
        }
    }

    private static void compareAsunaOriginal(Path directory, JsonObject project) throws IOException {
        var original = read(directory.resolve("mra.bbmodel"));
        assertEquals(original.getAsJsonArray("elements").size(), project.getAsJsonArray("elements").size());
        assertEquals(original.getAsJsonArray("animations").size(), project.getAsJsonArray("animations").size());
        // Cube pivots may be arbitrary when rotation is zero. Compare actual shape and UV instead.
        var remaining = new ArrayList<>(original.getAsJsonArray("elements").asList());
        for (var element : project.getAsJsonArray("elements")) {
            var actual = element.getAsJsonObject();
            var match = remaining.stream().filter(e -> matches(e.getAsJsonObject(), actual)).findFirst();
            assertTrue(match.isPresent(), "No original cube matching " + actual.get("name"));
            var originalFaces = match.orElseThrow().getAsJsonObject().getAsJsonObject("faces");
            for (var face : actual.getAsJsonObject("faces").entrySet()) {
                var expectedUv = originalFaces.getAsJsonObject(face.getKey()).getAsJsonArray("uv");
                var actualUv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                for (var axis = 0; axis < 4; axis++) assertEquals(expectedUv.get(axis).getAsDouble(), actualUv.get(axis).getAsDouble(), 0.0001);
            }
            remaining.remove(match.orElseThrow());
        }
    }

    private static boolean matches(JsonObject expected, JsonObject actual) {
        for (var field : List.of("from", "to", "rotation", "uv_offset")) {
            var a = actual.getAsJsonArray(field);
            var b = expected.getAsJsonArray(field);
            for (var i = 0; i < a.size(); i++) if (Math.abs(a.get(i).getAsDouble() - (b == null ? 0 : b.get(i).getAsDouble())) > 0.0001) return false;
        }
        var inflate = expected.has("inflate") ? expected.get("inflate").getAsDouble() : 0;
        return Math.abs(inflate - actual.get("inflate").getAsDouble()) < 0.0001
                && (expected.has("mirror_uv") && expected.get("mirror_uv").getAsBoolean()) == actual.get("mirror_uv").getAsBoolean();
    }

    private static void inspectOutliner(com.google.gson.JsonArray nodes, Set<String> groups, Set<String> elements) {
        for (var node : nodes) {
            if (node.isJsonPrimitive()) assertTrue(elements.add(node.getAsString()));
            else {
                var group = node.getAsJsonObject();
                assertTrue(groups.add(group.get("uuid").getAsString()));
                inspectOutliner(group.getAsJsonArray("children"), groups, elements);
            }
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
