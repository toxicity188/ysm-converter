package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Conservative rest-pose hints; never evaluates runtime Molang or changes animation tracks. */
final class BlockbenchProps {
    private BlockbenchProps() {}

    static void apply(JsonObject geometry, Map<String, JsonObject> animations, JsonObject project, List<String> warnings) {
        apply(geometry, animations, project, warnings, "");
    }

    static void apply(JsonObject geometry, Map<String, JsonObject> animations, JsonObject project, List<String> warnings, String preview) {
        var hiddenByLoop = new HashSet<String>();
        var previewVisible = new HashSet<String>();
        var pulses = new HashSet<String>();
        for (var file : animations.values()) for (var entry : file.getAsJsonObject("animations").entrySet()) {
            var animation = entry.getValue().getAsJsonObject();
            if (!animation.has("bones")) continue;
            for (var bone : animation.getAsJsonObject("bones").entrySet()) {
                var scale = bone.getValue().getAsJsonObject().get("scale");
                if (entry.getKey().equals(preview) && numeric(scale, 1)) previewVisible.add(bone.getKey());
                if ((!animation.has("loop") || animation.get("loop").getAsString().equals("false")) && pulse(scale)) pulses.add(bone.getKey());
            }
        }
        for (var file : animations.values()) for (var value : file.getAsJsonObject("animations").asMap().values()) {
            var animation = value.getAsJsonObject();
            if (!animation.has("loop") || !animation.get("loop").getAsString().equals("true") || !animation.has("bones")) continue;
            for (var entry : animation.getAsJsonObject("bones").entrySet()) {
                var scale = entry.getValue().getAsJsonObject().get("scale");
                if (scale != null && scale.isJsonPrimitive() && scale.getAsJsonPrimitive().isNumber() && scale.getAsDouble() == 0) hiddenByLoop.add(entry.getKey());
            }
        }
        var bones = new HashMap<String, JsonObject>();
        for (var bone : geometry.getAsJsonArray("bones")) bones.put(bone.getAsJsonObject().get("name").getAsString(), bone.getAsJsonObject());
        var groups = new HashMap<String, JsonObject>();
        collect(project.getAsJsonArray("outliner"), groups);
        var elements = new HashMap<String, JsonObject>();
        for (var element : project.getAsJsonArray("elements")) elements.put(element.getAsJsonObject().get("uuid").getAsString(), element.getAsJsonObject());
        for (var name : hiddenByLoop.stream().sorted().toList()) {
            var bone = bones.get(name);
            if (bone == null) continue;
            var descendants = new ArrayList<JsonObject>();
            subtree(name, bones, descendants);
            if (descendants.stream().anyMatch(b -> protectedName(b.get("name").getAsString()))) continue;
            var min = new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
            var max = new double[]{Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            var count = 0;
            var rotatedChild = false;
            for (var child : descendants) {
                if (child != bone && child.has("rotation")) for (var angle : child.getAsJsonArray("rotation")) if (angle.getAsDouble() != 0) rotatedChild = true;
                if (!child.has("cubes")) continue;
                for (var cube : child.getAsJsonArray("cubes")) {
                    var c = cube.getAsJsonObject();
                    if (c.has("rotation")) for (var angle : c.getAsJsonArray("rotation")) if (angle.getAsDouble() != 0) rotatedChild = true;
                    count++;
                    for (var i = 0; i < 3; i++) {
                        var start = c.getAsJsonArray("origin").get(i).getAsDouble();
                        var end = start + c.getAsJsonArray("size").get(i).getAsDouble();
                        min[i] = Math.min(min[i], Math.min(start, end));
                        max[i] = Math.max(max[i], Math.max(start, end));
                    }
                }
            }
            if (count == 0) continue;
            var extents = new double[]{max[0]-min[0], max[1]-min[1], max[2]-min[2]};
            java.util.Arrays.sort(extents);
            var parent = bone.has("parent") ? bones.get(bone.get("parent").getAsString()) : null;
            var offset = 0.0;
            if (parent != null && parent.has("pivot") && bone.has("pivot")) for (var i = 0; i < 3; i++) offset = Math.max(offset,
                    Math.abs(bone.getAsJsonArray("pivot").get(i).getAsDouble() - parent.getAsJsonArray("pivot").get(i).getAsDouble()));
            // Raw bounds are only used for unrotated contents. Root rotation does not change dimensions in its local frame.
            var elongated = !rotatedChild && extents[2] >= 24 && extents[2] >= 5 * Math.max(1, extents[1]);
            var detached = offset >= 12 && extents[2] >= 24;
            var namedObject = name.toLowerCase(Locale.ROOT).matches(".*(sword|weapon|umbrella|crossbow|shield).*") && extents[2] >= 24;
            var backdrop = parent == null && extents[1] >= 32 && bones.values().stream().anyMatch(b -> b != bone && !b.has("parent"));
            var previewOnly = parent == null && previewVisible.contains(name)
                    && name.matches("(?i:gui)(?:$|[_0-9].*|[A-Z].*)")
                    && bones.values().stream().anyMatch(b -> b != bone && !b.has("parent"));
            var effect = pulses.contains(name) && count == 1 && extents[0] <= 0.25 && extents[1] >= 4;
            // Never infer a transient effect inside a face/hair subtree.
            for (var ancestor = parent; ancestor != null; ancestor = ancestor.has("parent") ? bones.get(ancestor.get("parent").getAsString()) : null)
                if (ancestor.get("name").getAsString().toLowerCase(Locale.ROOT).matches(".*(head|face|eye|hair|mouth|brow|pupil).*")) effect = false;
            if (!previewOnly && !effect && (count < 5 || (!elongated && !detached && !backdrop && !namedObject))) continue;
            if (!groups.get(name).get("visibility").getAsBoolean()) continue;
            hide(groups.get(name), elements);
            warnings.add("Prop default visibility=false: " + name + "; constant zero scale in looping animation; "
                    + (previewOnly ? "independent GUI root enabled by metadata preview " + preview : effect ? "planar one-shot effect with zero endpoints" : backdrop ? "separate large root" : detached ? "distant attachment pivot" : namedObject ? "object vocabulary and large geometry" : "elongated geometry")
                    + "; " + count + " cubes. Conservative heuristic; restore using the outliner visibility toggle.");
        }
    }

    private static boolean numeric(com.google.gson.JsonElement value, double expected) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && value.getAsDouble() == expected;
    }

    private static boolean pulse(com.google.gson.JsonElement scale) {
        if (scale == null || !scale.isJsonObject() || scale.getAsJsonObject().size() < 3) return false;
        var keys = new java.util.TreeMap<Double, Double>();
        for (var entry : scale.getAsJsonObject().entrySet()) {
            var value = entry.getValue();
            if (value.isJsonObject()) {
                var object = value.getAsJsonObject();
                if (object.has("pre") || !object.has("post")) return false;
                value = object.get("post");
            }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
            try { keys.put(Double.parseDouble(entry.getKey()), value.getAsDouble()); }
            catch (NumberFormatException ignored) { return false; }
        }
        return keys.firstKey() == 0 && keys.firstEntry().getValue() == 0 && keys.lastEntry().getValue() == 0
                && keys.values().stream().allMatch(v -> Double.isFinite(v) && v >= 0)
                && keys.values().stream().anyMatch(v -> v > 0);
    }

    private static boolean protectedName(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*(body|head|hair|face|eye|brow|pupil|mouth|ear|neck|arm|hand|leg|foot|cloth|coat|dress|skirt|sleeve|ribbon|tail|wing|skin|expression|cheek|lip).*");
    }

    private static void subtree(String name, Map<String, JsonObject> bones, List<JsonObject> result) {
        result.add(bones.get(name));
        for (var b : bones.values()) if (b.has("parent") && b.get("parent").getAsString().equals(name)) subtree(b.get("name").getAsString(), bones, result);
    }

    private static void collect(JsonArray nodes, Map<String, JsonObject> groups) {
        for (var node : nodes) if (node.isJsonObject()) {
            var group = node.getAsJsonObject();
            groups.put(group.get("name").getAsString(), group);
            collect(group.getAsJsonArray("children"), groups);
        }
    }

    private static void hide(JsonObject group, Map<String, JsonObject> elements) {
        group.addProperty("visibility", false);
        for (var child : group.getAsJsonArray("children")) {
            if (child.isJsonObject()) hide(child.getAsJsonObject(), elements);
            else elements.get(child.getAsString()).addProperty("visibility", false);
        }
    }
}
