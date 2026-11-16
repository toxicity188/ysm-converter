package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BlockbenchPropsTest {
    @Test
    void specialPrefixesNeedRoleAndDefaultStateEvidence() {
        for (var name : java.util.List.of("gui", "GuiRoot", "guitar", "ysmGlowFace", "ysmGlowEffect")) {
            for (var evidence : java.util.List.of(true, false)) {
                var geometry = JsonParser.parseString("{\"bones\":[{\"name\":\"Body\",\"pivot\":[0,0,0]},{\"name\":\"" + name
                        + "\",\"pivot\":[0,0,0],\"cubes\":[{\"origin\":[0,0,0],\"size\":[0.1,10,10]}]}]}").getAsJsonObject();
                var project = new com.google.gson.JsonObject();
                var warnings = new java.util.ArrayList<String>();
                BlockbenchGeometry.convert(geometry, project, warnings);
                var animation = JsonParser.parseString("{\"animations\":{\"default\":{\"loop\":true,\"bones\":{\""+name+"\":{\"scale\":0}}},\"preview\":{\"bones\":{\""+name+"\":{\"scale\":1}}},\"effect\":{\"bones\":{\""+name+"\":{\"scale\":{\"0\":0,\"0.5\":2,\"1\":0}}}}}}").getAsJsonObject();
                if (!name.equals("ysmGlowEffect")) animation.getAsJsonObject("animations").remove("effect");
                if (!evidence) animation.getAsJsonObject("animations").remove("default");
                var original = animation.deepCopy();
                BlockbenchProps.apply(geometry, java.util.Map.of("a", animation), project, warnings, "preview");
                assertEquals(evidence && java.util.Set.of("gui","GuiRoot","ysmGlowEffect").contains(name), project.getAsJsonArray("elements").get(0).getAsJsonObject().has("visibility"), name);
                assertEquals(original, animation);
            }
        }
    }

    @Test
    void specialResourceExamples() throws Exception {
        for (var name : java.util.List.of("Uncategorized_Create_Mechanical-Arm", "Uncategorized__Violet", "Uncategorized_Lex_Ninjia")) {
            var input = Path.of("build/ysm-parser-tests/extracted").resolve(name);
            assumeTrue(Files.isDirectory(input));
            var result = BlockbenchConverter.convert(input);
            result.write(Path.of("build/prop-check").resolve(name + ".bbmodel"));
            Files.write(Path.of("build/prop-check").resolve(name + "-warnings.txt"), result.warnings());
            var groups = new java.util.HashMap<String, com.google.gson.JsonObject>();
            collect(result.model().getAsJsonArray("outliner"), groups);
            var target = name.endsWith("Ninjia") ? "ysmGlowHuoLong" : "gui";
            assertFalse(groups.get(target).get("visibility").getAsBoolean(), name);
            if (name.endsWith("Ninjia")) assertTrue(result.warnings().stream().noneMatch(w -> w.startsWith("Prop default visibility=false: ysmGlowFireBlade;")));
        }
    }

    @Test
    void requiresIndependentEvidenceAndProtectsCharacterParts() {
        for (var name : java.util.List.of("item", "LongHair", "Coat", "Face", "RightArm")) {
            for (var loop : java.util.List.of(true, false)) {
                var geometry = JsonParser.parseString("{\"bones\":[{\"name\":\"root\",\"pivot\":[0,0,0]},{\"name\":\"" + name
                        + "\",\"parent\":\"root\",\"pivot\":[20,0,0],\"cubes\":[]}]}").getAsJsonObject();
                var cubes = geometry.getAsJsonArray("bones").get(1).getAsJsonObject().getAsJsonArray("cubes");
                for (var i=0;i<5;i++) cubes.add(JsonParser.parseString("{\"origin\":[20,0,0],\"size\":[2,30,2]}"));
                var project = new com.google.gson.JsonObject();
                var warnings = new java.util.ArrayList<String>();
                BlockbenchGeometry.convert(geometry, project, warnings);
                var animation = JsonParser.parseString("{\"animations\":{\"arbitrary_clip\":{\"loop\":"+loop+",\"bones\":{\""+name+"\":{\"scale\":0}}}}}").getAsJsonObject();
                BlockbenchProps.apply(geometry, java.util.Map.of("arbitrary.json",animation),project,warnings);
                assertEquals(name.equals("item") && loop, project.getAsJsonArray("elements").get(0).getAsJsonObject().has("visibility"));
            }
        }
    }

    @Test
    void evernightPreservesAllDataExceptVisibility() throws Exception {
        var input = Path.of("build/ysm-parser-tests/extracted/_HonkaiStarRail_HSR_March7th(Evernight)");
        assumeTrue(Files.isDirectory(input));
        var result = BlockbenchConverter.convert(input);
        var output = Path.of("build/prop-check/evernight.bbmodel");
        result.write(output);
        Files.write(output.resolveSibling("warnings.txt"), result.warnings());
        var groups = new java.util.HashMap<String, com.google.gson.JsonObject>();
        collect(result.model().getAsJsonArray("outliner"), groups);
        for (var name : java.util.List.of("LeftSword", "RightSword", "leftbone107", "rightbone107", "plane", "beijing"))
            assertFalse(groups.get(name).get("visibility").getAsBoolean(), name);
        for (var name : java.util.List.of("Head", "hair", "Face", "clothes", "catEar", "LeftArm", "RightLeg"))
            assertTrue(groups.get(name).get("visibility").getAsBoolean(), name);
        var baseline = JsonParser.parseString(Files.readString(Path.of("build/result/_HonkaiStarRail/HSR_March7th(Evernight).bbmodel"))).getAsJsonObject();
        assertEquals(baseline.get("animations"), result.model().get("animations"));
        strip(baseline); var actual = result.model().deepCopy(); strip(actual);
        assertEquals(baseline.get("elements"), actual.get("elements"));
        assertEquals(baseline.get("outliner"), actual.get("outliner"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("Prop default")));
        result.warnings().stream().filter(w -> w.startsWith("Prop default")).forEach(System.out::println);
    }
    private static void strip(com.google.gson.JsonElement element) {
        if (element.isJsonObject()) {
            element.getAsJsonObject().remove("visibility");
            element.getAsJsonObject().entrySet().removeIf(e -> e.getValue().isJsonNull());
            element.getAsJsonObject().asMap().values().forEach(BlockbenchPropsTest::strip);
        } else if (element.isJsonArray()) element.getAsJsonArray().forEach(BlockbenchPropsTest::strip);
    }
    private static void collect(com.google.gson.JsonArray nodes, java.util.Map<String, com.google.gson.JsonObject> groups) {
        for (var node : nodes) if (node.isJsonObject()) {
            var group = node.getAsJsonObject();
            groups.put(group.get("name").getAsString(), group);
            collect(group.getAsJsonArray("children"), groups);
        }
    }
}
