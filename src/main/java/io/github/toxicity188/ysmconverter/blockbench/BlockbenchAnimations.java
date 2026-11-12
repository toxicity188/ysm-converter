package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static io.github.toxicity188.ysmconverter.blockbench.BlockbenchGeometry.*;

final class BlockbenchAnimations {
    private BlockbenchAnimations() {
    }

    static JsonArray convert(Map<String, JsonObject> files, Map<String, String> bones, List<String> warnings) {
        var animations = new JsonArray();
        var seen = new LinkedHashMap<String, JsonObject>();
        var missing = new LinkedHashSet<String>();
        var expressions = false;
        for (var file : files.entrySet()) {
            for (var entry : file.getValue().getAsJsonObject("animations").entrySet()) {
                var name = entry.getKey();
                var source = entry.getValue().getAsJsonObject();
                if (seen.containsKey(name)) {
                    if (seen.get(name).equals(source)) continue;
                    var renamed = name + " [" + file.getKey() + "]";
                    warnings.add("Conflicting animation " + name + " retained as " + renamed);
                    name = renamed;
                }
                seen.put(name, source);
                var animation = new JsonObject();
                animation.addProperty("name", name);
                animation.addProperty("uuid", id("animation/" + name));
                var loop = string(source, "loop", "false");
                animation.addProperty("loop", switch (loop) {
                    case "true", "loop" -> "loop";
                    case "hold_on_last_frame" -> "hold";
                    default -> "once";
                });
                animation.addProperty("override", bool(source, "override_previous_animation", false));
                animation.addProperty("snapping", 20);
                for (var property : List.of("anim_time_update", "blend_weight", "start_delay", "loop_delay")) {
                    if (source.has(property)) animation.add(property, source.get(property).deepCopy());
                }
                var animators = new JsonObject();
                animation.add("animators", animators);
                if (source.has("bones")) {
                    for (var bone : source.getAsJsonObject("bones").entrySet()) {
                        var boneId = bones.get(bone.getKey());
                        if (boneId == null) {
                            missing.add(bone.getKey());
                            continue;
                        }
                        var animator = new JsonObject();
                        animator.addProperty("name", bone.getKey());
                        animator.addProperty("type", "bone");
                        var keys = new JsonArray();
                        animator.add("keyframes", keys);
                        animators.add(boneId, animator);
                        for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                            if (!List.of("position", "rotation", "scale").contains(channel.getKey())) {
                                warnings.add("Unsupported animation channel " + name + "/" + bone.getKey() + "/" + channel.getKey());
                                continue;
                            }
                            var value = channel.getValue();
                            if (!value.isJsonObject() || value.getAsJsonObject().has("post")
                                    || value.getAsJsonObject().has("pre") || value.getAsJsonObject().has("vector")) {
                                keys.add(key(channel.getKey(), 0, value));
                            } else {
                                var timestamps = value.getAsJsonObject().entrySet().stream()
                                        .sorted(java.util.Comparator.comparingDouble(e -> Double.parseDouble(e.getKey())))
                                        .toList();
                                for (var timestamp : timestamps) {
                                    keys.add(key(channel.getKey(), Double.parseDouble(timestamp.getKey()), timestamp.getValue()));
                                }
                            }
                        }
                        for (var key : keys) {
                            for (var point : key.getAsJsonObject().getAsJsonArray("data_points")) {
                                for (var axis : point.getAsJsonObject().entrySet()) {
                                    if (axis.getValue().getAsJsonPrimitive().isString()) expressions = true;
                                }
                            }
                        }
                    }
                }
                var effects = new JsonArray();
                for (var type : List.of("timeline", "sound_effects", "particle_effects")) {
                    if (!source.has(type)) continue;
                    for (var entryEffect : source.getAsJsonObject(type).entrySet()) {
                        var key = new JsonObject();
                        key.addProperty("time", time(Double.parseDouble(entryEffect.getKey())));
                        key.addProperty("channel", switch (type) {
                            case "sound_effects" -> "sound";
                            case "particle_effects" -> "particle";
                            default -> "timeline";
                        });
                        var points = new JsonArray();
                        var value = entryEffect.getValue();
                        if (type.equals("timeline")) {
                            var point = new JsonObject();
                            var script = value.isJsonArray()
                                    ? String.join("\n", value.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList())
                                    : value.getAsString();
                            point.addProperty("script", script);
                            points.add(point);
                        } else {
                            if (value.isJsonArray()) points = value.deepCopy().getAsJsonArray();
                            else points.add(value.deepCopy());
                            if (type.equals("particle_effects")) for (var point : points) {
                                var object = point.getAsJsonObject();
                                if (object.has("pre_effect_script")) object.add("script", object.remove("pre_effect_script"));
                            }
                        }
                        key.add("data_points", points);
                        effects.add(key);
                    }
                    warnings.add("Preserved " + name + " " + type + "; YSM runtime hooks/assets are not executed by this converter");
                }
                if (!effects.isEmpty()) {
                    var animator = new JsonObject();
                    animator.addProperty("type", "effect");
                    animator.add("keyframes", effects);
                    animators.add("effects", animator);
                }
                var length = time(number(source, "animation_length", 0));
                for (var animator : animators.entrySet()) {
                    for (var key : animator.getValue().getAsJsonObject().getAsJsonArray("keyframes")) {
                        length = Math.max(length, key.getAsJsonObject().get("time").getAsDouble());
                    }
                }
                animation.addProperty("length", length);
                animations.add(animation);
            }
        }
        if (!missing.isEmpty()) warnings.add("Animation tracks without matching third-person bones were skipped: " + String.join(", ", missing));
        if (expressions) warnings.add("Molang expressions are preserved; YSM query/variable values must be supplied in Blockbench for runtime-equivalent previews");
        return animations;
    }

    private static JsonObject key(String channel, double timestamp, JsonElement source) {
        var key = new JsonObject();
        key.addProperty("channel", channel);
        key.addProperty("time", time(timestamp));
        var points = new JsonArray();
        var interpolation = "linear";
        if (source.isJsonObject()) {
            var object = source.getAsJsonObject();
            interpolation = string(object, "lerp_mode", "linear");
            if (object.has("pre")) points.add(point(object.get("pre")));
            if (object.has("post")) points.add(point(object.get("post")));
            if (points.isEmpty() && object.has("vector")) points.add(point(object.get("vector")));
            if (points.isEmpty()) throw new IllegalArgumentException("Animation key has no vector, pre or post value");
            if (object.has("easing")) throw new IllegalArgumentException("Unsupported animation easing: " + object.get("easing"));
        } else {
            points.add(point(source));
        }
        if (!List.of("linear", "catmullrom", "step").contains(interpolation)) {
            throw new IllegalArgumentException("Unsupported interpolation: " + interpolation);
        }
        key.addProperty("interpolation", interpolation);
        key.add("data_points", points);
        return key;
    }

    private static JsonObject point(JsonElement source) {
        if (source.isJsonObject() && source.getAsJsonObject().has("vector")) source = source.getAsJsonObject().get("vector");
        if (source.isJsonArray() && source.getAsJsonArray().size() != 3) throw new IllegalArgumentException("Animation vector must have three axes");
        var point = new JsonObject();
        var axes = List.of("x", "y", "z");
        for (var i = 0; i < 3; i++) {
            var value = source.isJsonArray() ? source.getAsJsonArray().get(i) : source;
            if (!value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Animation axis must be numeric or Molang");
            if (value.getAsJsonPrimitive().isNumber() && !Double.isFinite(value.getAsDouble())) throw new IllegalArgumentException("Non-finite animation value");
            // Project 4.10 stores the same signs as the source Bedrock/YSM animation.
            // Blockbench 5 migrates these signs when opening this project version.
            point.add(axes.get(i), value.deepCopy());
        }
        return point;
    }

    private static double time(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 10000) throw new IllegalArgumentException("Animation time outside Blockbench's 0..10000 second range: " + value);
        return value;
    }
}
