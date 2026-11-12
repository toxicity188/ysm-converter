package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class BlockbenchGeometry {
    private static final List<String> FACES = List.of("north", "east", "south", "west", "up", "down");

    private BlockbenchGeometry() {
    }

    static Map<String, String> convert(JsonObject geometry, JsonObject project, List<String> warnings) {
        var bones = geometry.getAsJsonArray("bones");
        if (bones == null) throw new IllegalArgumentException("Geometry has no bones array");
        var groups = new LinkedHashMap<String, JsonObject>();
        var sources = new LinkedHashMap<String, JsonObject>();
        var ids = new LinkedHashMap<String, String>();
        var elements = new JsonArray();
        var outliner = new JsonArray();
        project.add("elements", elements);
        project.add("outliner", outliner);
        for (var entry : bones) {
            var bone = entry.getAsJsonObject();
            var name = bone.get("name").getAsString();
            if (groups.containsKey(name)) throw new IllegalArgumentException("Duplicate bone: " + name);
            var group = new JsonObject();
            var id = id("bone/" + name);
            ids.put(name, id);
            sources.put(name, bone);
            groups.put(name, group);
            group.addProperty("name", name);
            group.addProperty("uuid", id);
            group.add("origin", reflected(vector(bone, "pivot", 0), false));
            group.add("rotation", reflected(vector(bone, "rotation", 0), true));
            group.addProperty("mirror_uv", bool(bone, "mirror", false));
            group.addProperty("reset", bool(bone, "reset", false));
            group.addProperty("visibility", !bool(bone, "neverRender", false));
            group.add("children", new JsonArray());
            if (bone.has("binding")) group.add("bedrock_binding", bone.get("binding").deepCopy());
            for (var field : List.of("poly_mesh", "texture_meshes", "render_group_id")) {
                if (bone.has(field)) warnings.add("Unsupported geometry field " + name + "." + field);
            }
        }
        for (var entry : sources.entrySet()) {
            var name = entry.getKey();
            var bone = entry.getValue();
            var group = groups.get(name);
            var children = group.getAsJsonArray("children");
            // Resolve after all bones exist: source order need not be parent-first.
            var ancestors = new HashSet<String>();
            var current = name;
            while (current != null) {
                if (!ancestors.add(current)) throw new IllegalArgumentException("Cyclic bone hierarchy: " + name);
                var source = sources.get(current);
                if (source == null) throw new IllegalArgumentException("Missing parent bone: " + current);
                current = string(source, "parent", null);
            }
            var parent = string(bone, "parent", null);
            (parent == null ? outliner : groups.get(parent).getAsJsonArray("children")).add(group);
            if (bone.has("cubes")) {
                var index = 0;
                for (var value : bone.getAsJsonArray("cubes")) {
                    var cube = cube(value.getAsJsonObject(), bone, name, index++);
                    elements.add(cube);
                    children.add(cube.get("uuid"));
                }
            }
            if (bone.has("locators")) {
                for (var locator : bone.getAsJsonObject("locators").entrySet()) {
                    var source = locator.getValue();
                    var element = new JsonObject();
                    element.addProperty("type", "locator");
                    element.addProperty("name", locator.getKey());
                    element.addProperty("uuid", id("locator/" + name + "/" + locator.getKey()));
                    element.add("position", reflected(source.isJsonArray() ? vector(source) : vector(source.getAsJsonObject(), "offset", 0), false));
                    if (source.isJsonObject()) {
                        element.add("rotation", reflected(vector(source.getAsJsonObject(), "rotation", 0), true));
                        element.addProperty("ignore_inherited_scale", bool(source.getAsJsonObject(), "ignore_inherited_scale", false));
                    }
                    elements.add(element);
                    children.add(element.get("uuid"));
                }
            }
        }
        return ids;
    }

    private static JsonObject cube(JsonObject source, JsonObject bone, String name, int index) {
        var origin = vector(source, "origin", 0);
        var size = vector(source, "size", 0);
        var cube = new JsonObject();
        cube.addProperty("name", string(source, "name", name + "_" + index));
        cube.addProperty("uuid", id("cube/" + name + "/" + index));
        cube.addProperty("type", "cube");
        cube.add("from", array(-origin[0] - size[0], origin[1], origin[2]));
        cube.add("to", array(-origin[0], origin[1] + size[1], origin[2] + size[2]));
        cube.add("origin", reflected(source.has("pivot") ? vector(source.get("pivot")) : vector(bone, "pivot", 0), false));
        cube.add("rotation", reflected(vector(source, "rotation", 0), true));
        cube.addProperty("inflate", number(source, "inflate", number(bone, "inflate", 0)));
        cube.addProperty("autouv", 0);
        var mirror = bool(source, "mirror", bool(bone, "mirror", false));
        cube.addProperty("mirror_uv", mirror);
        var uv = source.get("uv");
        var box = uv == null || uv.isJsonArray();
        cube.addProperty("box_uv", box);
        var faces = new JsonObject();
        cube.add("faces", faces);
        if (box) {
            var offset = uv == null ? array(0, 0) : uv.getAsJsonArray();
            if (offset.size() != 2) throw new IllegalArgumentException("Box UV must have two coordinates");
            cube.add("uv_offset", offset.deepCopy());
            var u = offset.get(0).getAsDouble();
            var v = offset.get(1).getAsDouble();
            // Bedrock box UV uses integer dimensions even when geometry is fractional.
            var x = Math.floor(size[0] + 1e-6);
            var y = Math.floor(size[1] + 1e-6);
            var z = Math.floor(size[2] + 1e-6);
            faces.add("east", face(array(u, v + z, u + z, v + z + y), 0));
            faces.add("north", face(array(u + z, v + z, u + z + x, v + z + y), 0));
            faces.add("west", face(array(u + z + x, v + z, u + 2 * z + x, v + z + y), 0));
            faces.add("south", face(array(u + 2 * z + x, v + z, u + 2 * (z + x), v + z + y), 0));
            faces.add("up", face(array(u + z + x, v + z, u + z, v), 0));
            faces.add("down", face(array(u + z + 2 * x, v, u + z + x, v + z), 0));
            if (mirror) {
                var east = faces.get("east");
                faces.add("east", faces.get("west"));
                faces.add("west", east);
                for (var value : faces.entrySet()) {
                    var rectangle = value.getValue().getAsJsonObject().getAsJsonArray("uv");
                    var left = rectangle.get(0);
                    rectangle.set(0, rectangle.get(2));
                    rectangle.set(2, left);
                }
            }
        } else {
            for (var direction : FACES) {
                if (!uv.getAsJsonObject().has(direction)) {
                    var face = face(array(0, 0, 0, 0), 0);
                    face.add("texture", JsonNull.INSTANCE);
                    faces.add(direction, face);
                    continue;
                }
                var sourceFace = uv.getAsJsonObject().getAsJsonObject(direction);
                var offset = sourceFace.getAsJsonArray("uv");
                var u = offset.get(0).getAsDouble();
                var v = offset.get(1).getAsDouble();
                var dimensions = switch (direction) {
                    case "up", "down" -> array(size[0], size[2]);
                    case "east", "west" -> array(size[2], size[1]);
                    default -> array(size[0], size[1]);
                };
                var extent = sourceFace.has("uv_size") ? sourceFace.getAsJsonArray("uv_size") : dimensions;
                var endU = u + extent.get(0).getAsDouble();
                var endV = v + extent.get(1).getAsDouble();
                var rectangle = direction.equals("up") || direction.equals("down")
                        ? array(endU, endV, u, v) : array(u, v, endU, endV);
                var face = face(rectangle, number(sourceFace, "uv_rotation", 0));
                if (sourceFace.has("material_instance")) face.add("material_name", sourceFace.get("material_instance").deepCopy());
                faces.add(direction, face);
            }
        }
        return cube;
    }

    private static JsonObject face(JsonArray uv, double rotation) {
        var face = new JsonObject();
        face.add("uv", uv);
        face.addProperty("texture", 0);
        if (rotation != 0) face.addProperty("rotation", rotation);
        return face;
    }

    static String id(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static JsonArray array(double... values) {
        var array = new JsonArray();
        for (var value : values) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite coordinate");
            array.add(value);
        }
        return array;
    }

    private static JsonArray reflected(double[] vector, boolean rotation) {
        return array(-vector[0], rotation ? -vector[1] : vector[1], vector[2]);
    }

    private static double[] vector(JsonElement value) {
        var array = value.getAsJsonArray();
        if (array.size() != 3) throw new IllegalArgumentException("Expected three coordinates");
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static double[] vector(JsonObject object, String key, double fallback) {
        return object.has(key) ? vector(object.get(key)) : new double[]{fallback, fallback, fallback};
    }

    static String string(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }

    static double number(JsonObject object, String key, double fallback) {
        var value = object.has(key) ? object.get(key).getAsDouble() : fallback;
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + key);
        return value;
    }

    static boolean bool(JsonObject object, String key, boolean fallback) {
        return object.has(key) ? object.get(key).getAsBoolean() : fallback;
    }
}
