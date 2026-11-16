package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchAnimationReferencesTest {
    @Test
    void repairsStaleUuidByNestedBoneNameWithoutChangingKeyframesOrGeometry() throws IOException {
        var model = model();
        var original = model.deepCopy();
        var result = BlockbenchConverter.repairAnimationReferences(model);
        assertEquals(original, model);
        assertEquals(original.get("outliner"), result.model().get("outliner"));
        assertEquals(original.get("elements"), result.model().get("elements"));
        var animators = animators(result.model());
        assertEquals(animators(original).get("stale"), animators.get("arm-id"));
        assertFalse(animators.has("stale"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Repaired animator UUID")));
        assertValidReferences(result.model());
        assertEquals(result.model(), BlockbenchConverter.repairAnimationReferences(result.model()).model());
    }

    @Test
    void removesMissingAndAmbiguousBonesButKeepsValidUuidRegardlessOfName() throws IOException {
        var model = model();
        model.getAsJsonArray("outliner").add(JsonParser.parseString("""
                {"name":"arm","uuid":"other-arm-id","children":[]}
                """));
        animators(model).add("missing", animator("absent", 0));
        animators(model).add("root-id", animator("outdated name", 0));
        var result = BlockbenchConverter.repairAnimationReferences(model);
        assertEquals(Set.of("root-id"), animators(result.model()).keySet());
        assertEquals(animators(model).get("root-id"), animators(result.model()).get("root-id"));
        assertEquals(2, result.warnings().stream().filter(w -> w.contains("Removed bone animator")).count());
        assertValidReferences(result.model());
    }

    @Test
    void sourceMappingDisambiguatesOnlyWhenItPointsToMatchingOutlinerBone() {
        var model = model();
        model.getAsJsonArray("outliner").add(JsonParser.parseString("""
                {"name":"arm","uuid":"other-arm-id","children":[]}
                """));
        var warnings = new ArrayList<String>();
        BlockbenchAnimations.repairReferences(model, Map.of("arm", "arm-id"), warnings);
        assertEquals(Set.of("arm-id"), animators(model).keySet());
        assertValidReferences(model);
        var ambiguous = model();
        ambiguous.getAsJsonArray("outliner").add(JsonParser.parseString("""
                {"name":"arm","uuid":"other-arm-id","children":[]}
                """));
        BlockbenchAnimations.repairReferences(ambiguous, Map.of("arm", "root-id"), warnings);
        assertTrue(ambiguous.getAsJsonArray("animations").isEmpty());
    }

    @Test
    void doesNotTreatCubeReferencesOrDuplicateGroupUuidsAsValidBones() throws IOException {
        var model = model();
        animators(model).remove("stale");
        animators(model).add("cube-id", animator("cube", 0));
        var result = BlockbenchConverter.repairAnimationReferences(model);
        assertTrue(result.model().getAsJsonArray("animations").isEmpty());
        model = model();
        model.getAsJsonArray("outliner").add(JsonParser.parseString("""
                {"name":"another","uuid":"arm-id","children":[]}
                """));
        result = BlockbenchConverter.repairAnimationReferences(model);
        assertTrue(result.model().getAsJsonArray("animations").isEmpty());
    }

    @Test
    void preservesEffectsAndDoesNotMistakeBoneNamedEffectsForSpecialAnimator() throws IOException {
        var model = model();
        animators(model).add("effects", JsonParser.parseString("""
                {"type":"effect","keyframes":[{"channel":"timeline","time":0,"data_points":[{"script":"v.test=1;"}]}]}
                """));
        animators(model).getAsJsonObject("stale").addProperty("name", "missing");
        var result = BlockbenchConverter.repairAnimationReferences(model);
        assertEquals(Set.of("effects"), animators(result.model()).keySet());
        assertEquals(animators(model).get("effects"), animators(result.model()).get("effects"));
        var effect = animators(model).remove("effects");
        animators(model).add("old-effect-id", effect);
        result = BlockbenchConverter.repairAnimationReferences(model);
        assertEquals(Set.of("effects"), animators(result.model()).keySet());
        animators(model).remove("old-effect-id");
        animators(model).add("effects", animator("missing", 0));
        assertTrue(BlockbenchConverter.repairAnimationReferences(model).model().getAsJsonArray("animations").isEmpty());
    }

    @Test
    void mergesNonConflictingKeysAndNeverOverwritesExistingValidTracks() throws IOException {
        var model = model();
        animators(model).add("arm-id", animator("arm", 1));
        var result = BlockbenchConverter.repairAnimationReferences(model);
        var keys = animators(result.model()).getAsJsonObject("arm-id").getAsJsonArray("keyframes");
        assertEquals(2, keys.size());
        assertEquals(animators(model).getAsJsonObject("stale").getAsJsonArray("keyframes").get(0), keys.get(1));
        animators(model).add("arm-id", animator("arm", 0));
        animators(model).getAsJsonObject("stale").getAsJsonArray("keyframes").get(0).getAsJsonObject().addProperty("interpolation", "step");
        result = BlockbenchConverter.repairAnimationReferences(model);
        assertEquals(animators(model).get("arm-id"), animators(result.model()).get("arm-id"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("conflicting animator repairs")));
        animators(model).add("other-stale", animators(model).remove("arm-id"));
        assertTrue(BlockbenchConverter.repairAnimationReferences(model).model().getAsJsonArray("animations").isEmpty());
    }

    @Test
    void sourceConversionDropsOnlyAnimationsEmptiedByInvalidBoneRemoval() {
        var source = JsonParser.parseString("""
                {"animations":{
                  "missing":{"bones":{"absent":{"rotation":[1,2,3]}}},
                  "mixed":{"bones":{"root":{"position":[0,1,0]},"absent":{"rotation":[1,2,3]}}},
                  "effects":{"bones":{"absent":{"rotation":[1,2,3]}},"timeline":{"0":"v.test=1;"}},
                  "empty":{"animation_length":1}
                }}
                """).getAsJsonObject();
        var warnings = new ArrayList<String>();
        var animations = BlockbenchAnimations.convert(Map.of("main.animation.json", source), Map.of("root", "root-id"), warnings);
        assertEquals(3, animations.size());
        assertEquals(Set.of("root-id"), animations.get(0).getAsJsonObject().getAsJsonObject("animators").keySet());
        assertEquals(Set.of("effects"), animations.get(1).getAsJsonObject().getAsJsonObject("animators").keySet());
        assertEquals("empty", animations.get(2).getAsJsonObject().get("name").getAsString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Removed animation missing")));
    }

    static void assertValidReferences(JsonObject model) {
        var ids = new HashSet<String>();
        collectIds(model.getAsJsonArray("outliner"), ids);
        for (var animation : model.getAsJsonArray("animations")) {
            for (var entry : animation.getAsJsonObject().getAsJsonObject("animators").entrySet()) {
                var animator = entry.getValue().getAsJsonObject();
                if (entry.getKey().equals("effects")) assertEquals("effect", animator.get("type").getAsString());
                else assertTrue(ids.contains(entry.getKey()), animation.getAsJsonObject().get("name") + "/" + entry.getKey());
            }
        }
    }

    private static void collectIds(JsonArray nodes, Set<String> ids) {
        for (var node : nodes) if (node.isJsonObject()) {
            var group = node.getAsJsonObject();
            assertTrue(ids.add(group.get("uuid").getAsString()));
            collectIds(group.getAsJsonArray("children"), ids);
        }
    }

    private static JsonObject model() {
        var model = JsonParser.parseString("""
                {"elements":[{"uuid":"cube-id","name":"cube","type":"cube"}],
                 "outliner":[{"name":"root","uuid":"root-id","children":[
                   {"name":"arm","uuid":"arm-id","children":["cube-id"]}]}],
                 "animations":[{"name":"test","length":1,"animators":{}}]}
                """).getAsJsonObject();
        animators(model).add("stale", animator("arm", 0));
        return model;
    }

    private static JsonObject animator(String name, double time) {
        var animator = JsonParser.parseString("""
                {"type":"bone","keyframes":[{"channel":"rotation","time":0,"interpolation":"catmullrom",
                  "data_points":[{"x":"v.pose","y":2,"z":3},{"x":4,"y":5,"z":6}],"uuid":"key-id"}]}
                """).getAsJsonObject();
        animator.addProperty("name", name);
        animator.getAsJsonArray("keyframes").get(0).getAsJsonObject().addProperty("time", time);
        return animator;
    }

    private static JsonObject animators(JsonObject model) {
        return model.getAsJsonArray("animations").get(0).getAsJsonObject().getAsJsonObject("animators");
    }
}
