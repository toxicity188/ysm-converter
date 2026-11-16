package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BlockbenchAnimationAttachmentTest {
    @Test
    void removesOnlyTheTwoOrphanedTracksInTheOptionalAttachment() throws IOException {
        var input = Path.of("build/animation-reference-input/model.bbmodel");
        assumeTrue(Files.isRegularFile(input), "Optional model.bbmodel attachment is not installed");
        var source = JsonParser.parseString(Files.readString(input)).getAsJsonObject();
        var original = source.deepCopy();
        var result = BlockbenchConverter.repairAnimationReferences(source);
        assertEquals(original, source);
        assertEquals(source.get("elements"), result.model().get("elements"));
        assertEquals(source.get("outliner"), result.model().get("outliner"));
        assertEquals(source.get("textures"), result.model().get("textures"));
        assertEquals(1, result.model().getAsJsonArray("animations").size());
        var before = source.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonObject("animators");
        var after = result.model().getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonObject("animators");
        assertEquals(5, before.size());
        assertEquals(3, after.size());
        assertEquals(Set.of("pla_left_arm", "group", "bone"), after.asMap().values().stream()
                .map(a -> a.getAsJsonObject().get("name").getAsString()).collect(java.util.stream.Collectors.toSet()));
        for (var entry : after.entrySet()) assertEquals(before.get(entry.getKey()), entry.getValue());
        assertEquals(15, after.asMap().values().stream().mapToInt(a -> a.getAsJsonObject().getAsJsonArray("keyframes").size()).sum());
        assertEquals(2, result.warnings().size());
        BlockbenchAnimationReferencesTest.assertValidReferences(result.model());
        var output = Path.of("build/animation-reference-check");
        result.write(output.resolve("model.bbmodel"));
        Files.write(output.resolve("warnings.txt"), result.warnings());
    }
}
