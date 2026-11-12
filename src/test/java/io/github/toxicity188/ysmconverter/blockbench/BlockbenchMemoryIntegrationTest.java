package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.format.YsmVersion;
import io.github.toxicity188.ysmconverter.parser.ImageFormat;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BlockbenchMemoryIntegrationTest {
    @Test
    void encryptedModelsBecomeEditableProjectsWithoutIntermediateFiles() {
        var repository = Path.of("external/Yes-Steve-Model-Repo");
        assumeTrue(Files.isDirectory(repository), "Optional model repository is not installed");
        var models = List.of("Uncategorized/_Meiria.ysm", "_BlueArchive/BA_Asuna.ysm", "_Arknights/Ak_Eblana_1.21-fix-2.ysm");
        var versions = List.of(YsmVersion.V1, YsmVersion.V2, YsmVersion.V3);
        assertAll(models.stream().map(name -> () -> {
            // Only the encrypted source is read. No extracted directory or output file is created.
            try (var input = Files.newInputStream(repository.resolve(name))) {
                var parsed = YsmConverter.parse(input);
                assertEquals(versions.get(models.indexOf(name)), parsed.version(), name);
                assertTrue(parsed.formatVersion() > 0);
                assertFalse(parsed.resources().isEmpty());
                JsonElement converted = BlockbenchConverter.convert(parsed.resources());
                assertProject(converted);
                System.out.printf("%s: %s/%d, %d resources, %d cubes, %d parser warnings%n", name,
                        parsed.version(), parsed.formatVersion(), parsed.resources().size(),
                        converted.getAsJsonObject().getAsJsonArray("elements").size(), parsed.warnings().size());
            }
        }));
    }

    private static void assertProject(JsonElement converted) {
        assertTrue(converted.isJsonObject());
        var project = converted.getAsJsonObject();
        assertEquals("bedrock", project.getAsJsonObject("meta").get("model_format").getAsString());
        assertEquals("4.10", project.getAsJsonObject("meta").get("format_version").getAsString());
        assertTrue(project.getAsJsonObject("resolution").get("width").getAsInt() > 0);
        assertTrue(project.getAsJsonObject("resolution").get("height").getAsInt() > 0);
        var elements = project.getAsJsonArray("elements");
        var textures = project.getAsJsonArray("textures");
        assertFalse(elements.isEmpty());
        assertFalse(textures.isEmpty());
        var elementIds = new HashSet<String>();
        for (var element : elements) {
            var cube = element.getAsJsonObject();
            assertTrue(elementIds.add(cube.get("uuid").getAsString()));
            assertEquals("cube", cube.get("type").getAsString());
            for (var field : List.of("from", "to", "origin", "rotation")) {
                var vector = cube.getAsJsonArray(field);
                assertEquals(3, vector.size());
                for (var axis : vector) assertTrue(Double.isFinite(axis.getAsDouble()));
            }
            for (var face : cube.getAsJsonObject("faces").entrySet()) {
                var value = face.getValue().getAsJsonObject();
                assertEquals(4, value.getAsJsonArray("uv").size());
                if (!value.get("texture").isJsonNull()) {
                    var index = value.get("texture").getAsInt();
                    assertTrue(index >= 0 && index < textures.size());
                }
            }
        }
        for (var texture : textures) {
            var source = texture.getAsJsonObject().get("source").getAsString();
            assertTrue(source.startsWith("data:image/png;base64,"));
            assertEquals(ImageFormat.PNG, ImageFormat.detect(Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1))));
        }
        var bones = new HashSet<String>();
        var references = new HashSet<String>();
        inspectOutliner(project.getAsJsonArray("outliner"), bones, references);
        assertEquals(elementIds, references);
        assertFalse(bones.isEmpty());
        for (var animation : project.getAsJsonArray("animations")) {
            for (var animator : animation.getAsJsonObject().getAsJsonObject("animators").keySet()) {
                assertTrue(animator.equals("effects") || bones.contains(animator));
            }
        }
    }

    private static void inspectOutliner(JsonArray nodes, Set<String> bones, Set<String> references) {
        for (var node : nodes) {
            if (node.isJsonPrimitive()) assertTrue(references.add(node.getAsString()));
            else {
                var bone = node.getAsJsonObject();
                assertTrue(bones.add(bone.get("uuid").getAsString()));
                inspectOutliner(bone.getAsJsonArray("children"), bones, references);
            }
        }
    }
}
