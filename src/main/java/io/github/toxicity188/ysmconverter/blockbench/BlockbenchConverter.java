package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.toxicity188.ysmconverter.blockbench.BlockbenchGeometry.*;

/**
 * Converts already extracted YSM resources into an editable third-person Blockbench project.
 * Supports Bedrock cube geometry, embedded skin variants and bone animation. First-person
 * arm resources, projectile models and TAC animations are excluded. Original .bbmodel files
 * are never used as conversion input. Runtime controllers and YSM-specific Molang queries
 * cannot be reproduced without the game runtime; diagnostics describe these limitations.
 * High-confidence prop candidates start hidden in the outliner. Their geometry and animation
 * remain editable; conversion diagnostics identify the hidden subtrees and inference evidence.
 * <pre>{@code
 * var resources = YsmConverter.decrypt(inputStream);
 * JsonElement model = BlockbenchConverter.convert(resources);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class BlockbenchConverter {
    private BlockbenchConverter() {
    }

    /**
     * Converts an extracted resource directory without modifying its files or following symlinks.
     * For example, {@code BlockbenchConverter.convert(Path.of("extracted")).write(Path.of("model.bbmodel"))}.
     *
     * @param directory directory containing ysm.json or legacy main.json
     * @return self-contained project and conversion diagnostics
     * @throws IOException if a resource cannot be read or converted
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull BlockbenchResult convert(@NotNull Path directory) throws IOException {
        var resources = new LinkedHashMap<String, byte[]>();
        try (var paths = Files.walk(directory)) {
            for (var file : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                var name = directory.relativize(file).toString().replace('\\', '/');
                if (name.endsWith(".json") || image(name)) resources.put(name, Files.readAllBytes(file));
            }
        }
        var result = convertWithWarnings(resources);
        result.model().addProperty("name", directory.toAbsolutePath().getFileName().toString());
        return result;
    }

    /**
     * Converts the resource map returned by an existing YSM parser, without decrypting again.
     * For example, {@code BlockbenchConverter.convert(parser.getExtractedResources())}.
     * Textures are decoded and embedded as PNG. Missing or invalid texture references use conservative
     * image inference; ambiguous candidates remain unassigned. Use {@link #convertWithWarnings(Map)}
     * to inspect texture selection diagnostics.
     *
     * @param resources relative resource paths and their contents
     * @return self-contained Blockbench 4.10 project, also readable by Blockbench 5
     * @throws IOException if metadata, references, geometry, animation or texture data is invalid or unsupported
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull JsonElement convert(@NotNull Map<String, byte[]> resources) throws IOException {
        return convertWithWarnings(resources).model();
    }

    /**
     * Converts resources entirely in memory while retaining conversion diagnostics.
     * For example, {@code var result = BlockbenchConverter.convertWithWarnings(resources)}.
     * No temporary image cache files are created, regardless of global ImageIO cache settings.
     *
     * @param resources relative resource paths and their contents
     * @return editable project and warnings about texture inference and unsupported runtime features
     * @throws IOException if resources are invalid, unsupported or cannot be decoded
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull BlockbenchResult convertWithWarnings(@NotNull Map<String, byte[]> resources) throws IOException {
        try {
            return new Conversion(resources).convert();
        } catch (JsonParseException | IllegalArgumentException | IllegalStateException | NullPointerException | IndexOutOfBoundsException exception) {
            throw new IOException("Invalid YSM resources: " + exception.getMessage(), exception);
        }
    }

    /**
     * Repairs bone animator references in a copy of an existing Blockbench project, entirely in memory.
     * Valid UUIDs are preserved. A stale UUID is replaced only by an unambiguous outliner bone name.
     * Unresolvable tracks and animations emptied by their removal are dropped with warnings.
     * Effects use the reserved {@code effects} animator key. Geometry and the input object are unchanged.
     * For example, {@code var result = BlockbenchConverter.repairAnimationReferences(model)}.
     *
     * @param model project with nested outliner groups and optional animations
     * @return repaired project and diagnostics for changed or unsupported animators
     * @throws IOException if the project has malformed animation or outliner data
     * @since 1.0.0
     */
    @CheckReturnValue
    public static @NotNull BlockbenchResult repairAnimationReferences(@NotNull JsonObject model) throws IOException {
        try {
            var copy = model.deepCopy();
            var warnings = new ArrayList<String>();
            BlockbenchAnimations.repairReferences(copy, Map.of(), warnings);
            return new BlockbenchResult(copy, warnings);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | IndexOutOfBoundsException exception) {
            throw new IOException("Invalid Blockbench animation references: " + exception.getMessage(), exception);
        }
    }

    private static final class Conversion {
        private final Map<String, byte[]> resources = new LinkedHashMap<>();
        private final Map<String, JsonObject> documents = new LinkedHashMap<>();
        private final Map<String, BufferedImage> images = new LinkedHashMap<>();
        private final LinkedHashSet<String> auxiliaryTextures = new LinkedHashSet<>();
        private final List<String> warnings = new ArrayList<>();

        Conversion(Map<String, byte[]> input) {
            input.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var name = normalize(entry.getKey());
                if (resources.putIfAbsent(name, entry.getValue()) != null) throw new IllegalArgumentException("Duplicate resource path: " + name);
            });
        }

        BlockbenchResult convert() throws IOException {
            var manifest = json(resources.containsKey("ysm.json") ? "ysm.json" : "main.json");
            var modern = manifest.has("files");
            var player = modern ? manifest.getAsJsonObject("files").getAsJsonObject("player") : null;
            var geometryPath = modern ? player.getAsJsonObject("model").get("main").getAsString() : "main.json";
            var document = json(geometryPath);
            JsonObject geometry;
            JsonObject description;
            if (document.has("minecraft:geometry")) {
                var geometries = document.getAsJsonArray("minecraft:geometry");
                if (geometries.size() != 1) throw new IOException("Expected one player geometry in " + geometryPath + ", found " + geometries.size());
                geometry = geometries.get(0).getAsJsonObject();
                description = geometry.getAsJsonObject("description");
            } else {
                var geometries = document.entrySet().stream().filter(e -> e.getKey().startsWith("geometry.")).toList();
                if (geometries.size() != 1) throw new IOException("No unambiguous player geometry in " + geometryPath);
                geometry = geometries.getFirst().getValue().getAsJsonObject();
                description = new JsonObject();
                description.addProperty("identifier", geometries.getFirst().getKey());
                description.addProperty("texture_width", number(geometry, "texturewidth", 64));
                description.addProperty("texture_height", number(geometry, "textureheight", 64));
            }
            var width = number(description, "texture_width", 64);
            var height = number(description, "texture_height", 64);
            if (width <= 0 || height <= 0 || width != Math.rint(width) || height != Math.rint(height)) throw new IOException("Invalid geometry texture dimensions");
            var project = new JsonObject();
            var meta = new JsonObject();
            meta.addProperty("format_version", "4.10");
            meta.addProperty("model_format", "bedrock");
            meta.addProperty("box_uv", false);
            project.add("meta", meta);
            project.addProperty("name", string(description, "identifier", "YSM model"));
            project.addProperty("model_identifier", string(description, "identifier", "geometry.unknown").replaceFirst("^geometry\\.", ""));
            var resolution = new JsonObject();
            resolution.addProperty("width", width);
            resolution.addProperty("height", height);
            project.add("resolution", resolution);
            if (manifest.has("metadata")) project.addProperty("credit", manifest.get("metadata").toString());
            else if (resources.containsKey("info.json")) project.addProperty("credit", json("info.json").toString());
            var bones = BlockbenchGeometry.convert(geometry, project, warnings);
            var textures = new LinkedHashSet<String>();
            var animations = new LinkedHashMap<String, JsonObject>();
            if (modern) {
                if (player.has("texture")) collectTextures(player.get("texture"), textures);
                if (player.has("animation")) {
                    for (var entry : player.getAsJsonObject("animation").entrySet()) {
                        if (!excluded(entry.getKey()) && !excluded(entry.getValue().getAsString())) addAnimation(entry.getValue().getAsString(), animations);
                    }
                }
                if (player.has("animation_controllers") && !player.get("animation_controllers").getAsJsonArray().isEmpty()) warnings.add("YSM animation controllers are not converted");
                if (manifest.has("properties")) warnings.add("YSM gameplay properties (scale, preview selection, render layers and controls) are not applied to authoring geometry");
            } else {
                if (resources.containsKey("main.animation.json")) addAnimation("main.animation.json", animations);
                for (var name : resources.keySet()) {
                    if (excluded(name)) continue;
                    if (name.endsWith(".json") && !name.equals("main.json") && !name.equals("info.json")
                            && json(name).has("animations")) addAnimation(name, animations);
                    if (image(name) && !name.contains("/") && !name.toLowerCase(Locale.ROOT).matches(".*_(normal|specular|emissive)\\.[^.]+$")) textures.add(name);
                }
                if (!textures.isEmpty()) warnings.add("Legacy layout: player skins inferred from root images; first sorted skin is the default");
            }
            var requested = new LinkedHashSet<>(textures);
            for (var name : requested) {
                try {
                    readImage(name);
                } catch (IOException exception) {
                    textures.remove(name);
                    warnings.add("Invalid player texture: " + name + "; " + exception.getMessage());
                }
            }
            if (textures.isEmpty()) inferTexture(textures, requested, project, geometryPath, width, height, manifest);
            if (textures.isEmpty()) {
                // False means an unassigned visible face; null means a deliberately hidden face.
                for (var element : project.getAsJsonArray("elements")) {
                    var cube = element.getAsJsonObject();
                    if (cube.has("faces")) for (var face : cube.getAsJsonObject("faces").asMap().values()) {
                        var object = face.getAsJsonObject();
                        if (!object.get("texture").isJsonNull()) object.addProperty("texture", false);
                    }
                }
            }
            project.add("textures", textures(textures, width, height, manifest));
            project.add("animations", BlockbenchAnimations.convert(animations, bones, warnings));
            BlockbenchAnimations.repairReferences(project, bones, warnings);
            BlockbenchProps.apply(geometry, animations, project, warnings,
                    manifest.has("properties") ? string(manifest.getAsJsonObject("properties"), "preview_animation", "") : "");
            return new BlockbenchResult(project, warnings);
        }

        private void addAnimation(String name, Map<String, JsonObject> animations) throws IOException {
            var normalized = normalize(name);
            var document = json(normalized);
            if (!document.has("animations")) throw new IOException("Animation resource has no animations: " + name);
            animations.put(normalized, document);
        }

        private void collectTextures(JsonElement value, LinkedHashSet<String> textures) {
            if (value.isJsonArray()) for (var entry : value.getAsJsonArray()) collectTextures(entry, textures);
            else if (value.isJsonPrimitive()) textures.add(normalize(value.getAsString()));
            else if (value.isJsonObject()) {
                var object = value.getAsJsonObject();
                if (object.has("uv") && !object.get("uv").isJsonNull()) textures.add(normalize(object.get("uv").getAsString()));
                for (var key : object.keySet()) if (!key.equals("uv")) {
                    warnings.add("Auxiliary player texture is not a skin and was not applied: " + key);
                    if (object.get(key).isJsonPrimitive()) auxiliaryTextures.add(object.get(key).getAsString().replace('\\', '/'));
                }
            }
        }

        private void inferTexture(LinkedHashSet<String> textures, LinkedHashSet<String> requested, JsonObject project,
                                  String geometryPath, double width, double height, JsonObject manifest) throws IOException {
            var candidates = new LinkedHashMap<String, BufferedImage>();
            for (var name : resources.keySet()) {
                if (!image(name) || auxiliaryTextures.contains(name)
                        || List.of(name.split("/")).stream().anyMatch(BlockbenchConverter::excluded)
                        || name.toLowerCase(Locale.ROOT).matches("(^|.*/)(icon|preview|thumbnail|avatar|portrait)([._ /-].*)?")) continue;
                try {
                    candidates.put(name, readImage(name));
                } catch (IOException exception) {
                    if (!requested.contains(name)) warnings.add("Skipped fallback image: " + name + "; " + exception.getMessage());
                }
            }
            if (candidates.isEmpty()) throw new IOException("No player color texture found; " + String.join("; ", warnings));
            // Expanded face UVs cover box UV, signed sizes and per-face UV without duplicating geometry logic.
            for (var element : project.getAsJsonArray("elements")) {
                var cube = element.getAsJsonObject();
                if (!cube.has("faces")) continue;
                for (var face : cube.getAsJsonObject("faces").asMap().values()) {
                    var object = face.getAsJsonObject();
                    if (object.get("texture").isJsonNull()) continue;
                    var uv = object.getAsJsonArray("uv");
                    for (var axis = 0; axis < 4; axis++) {
                        var value = uv.get(axis).getAsDouble();
                        if (value < -0.0001 || value > (axis % 2 == 0 ? width : height) + 0.0001) {
                            warnings.add("Texture fallback left unassigned: geometry UVs exceed the declared atlas; candidates: " + candidates.keySet());
                            return;
                        }
                    }
                }
            }
            var selected = manifest.has("properties") ? string(manifest.getAsJsonObject("properties"), "default_texture", "") : "";
            var identifier = project.get("model_identifier").getAsString();
            var best = new ArrayList<String>();
            var bestScore = -1;
            for (var entry : candidates.entrySet()) {
                var name = entry.getKey();
                var bitmap = entry.getValue();
                // Bitmap resolution can differ from logical UV resolution, but uniform scaling preserves density.
                if (bitmap.getWidth() * height != bitmap.getHeight() * width) continue;
                var base = stem(name);
                var score = bitmap.getWidth() == width && bitmap.getHeight() == height ? 2 : 0;
                if (name.equals(selected) || base.equals(selected)
                        || requested.stream().anyMatch(n -> stem(n).equalsIgnoreCase(base))) score += 16;
                if (base.equalsIgnoreCase(identifier) || (!stem(geometryPath).equalsIgnoreCase("main")
                        && base.equalsIgnoreCase(stem(geometryPath)))) score += 8;
                var lower = name.toLowerCase(Locale.ROOT);
                if (base.toLowerCase(Locale.ROOT).matches("(.*[._ -])?(skin|albedo|diffuse|color)([._ -].*)?")) score += 4;
                if (lower.matches(".*(^|/)(textures?|player|body)/.*")) score++;
                if (lower.matches(".*[._ -](normal|specular|emissive)\\.[^.]+$")) score -= 4;
                if (score > bestScore || best.isEmpty()) {
                    bestScore = score;
                    best.clear();
                    best.add(name);
                } else if (score == bestScore) best.add(name);
            }
            if (best.size() == 1 && (candidates.size() == 1 || bestScore >= 0)) {
                var name = best.getFirst();
                textures.add(name);
                warnings.add("Texture fallback selected " + name + " from " + candidates.size()
                        + " image(s): compatible UV atlas " + width + "x" + height + ", bitmap "
                        + candidates.get(name).getWidth() + "x" + candidates.get(name).getHeight()
                        + (candidates.size() == 1 ? "; sole eligible image" : "; unique best evidence score " + bestScore));
            } else warnings.add("Texture fallback left unassigned: insufficient or ambiguous image evidence; candidates: " + candidates.keySet());
        }

        private BufferedImage readImage(String name) throws IOException {
            if (images.containsKey(name)) return images.get(name);
            // Explicit memory streams avoid ImageIO's default disk cache without changing global settings.
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(resource(name)))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException("Cannot decode player texture: " + name);
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    var image = reader.read(0);
                    images.put(name, image);
                    return image;
                } finally {
                    reader.dispose();
                }
            }
        }

        private JsonArray textures(LinkedHashSet<String> names, double width, double height, JsonObject manifest) throws IOException {
            var ordered = new ArrayList<>(names);
            if (manifest.has("properties")) {
                var selected = string(manifest.getAsJsonObject("properties"), "default_texture", "");
                var match = ordered.stream().filter(n -> n.equals(selected) || stem(n).equals(selected)).findFirst();
                if (match.isPresent()) {
                    ordered.remove(match.get());
                    ordered.addFirst(match.get());
                } else if (!selected.isEmpty() && !selected.equals("default")) warnings.add("Default texture not found: " + selected + "; using first skin");
            }
            var result = new JsonArray();
            for (var name : ordered) {
                var image = readImage(name);
                var png = new ByteArrayOutputStream();
                try (var output = new MemoryCacheImageOutputStream(png)) {
                    if (!ImageIO.write(image, "PNG", output)) throw new IOException("PNG encoder unavailable");
                }
                var texture = new JsonObject();
                texture.addProperty("name", name.substring(name.lastIndexOf('/') + 1));
                texture.addProperty("id", String.valueOf(result.size()));
                texture.addProperty("uuid", id("texture/" + name));
                texture.addProperty("mode", "bitmap");
                texture.addProperty("internal", true);
                texture.addProperty("visible", true);
                texture.addProperty("use_as_default", result.isEmpty());
                texture.addProperty("width", image.getWidth());
                texture.addProperty("height", image.getHeight());
                texture.addProperty("uv_width", width);
                texture.addProperty("uv_height", height);
                texture.addProperty("source", "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()));
                result.add(texture);
            }
            return result;
        }

        private JsonObject json(String name) throws IOException {
            var key = normalize(name);
            if (!documents.containsKey(key)) {
                var content = new String(resource(key), StandardCharsets.UTF_8);
                if (content.startsWith("\uFEFF")) content = content.substring(1);
                documents.put(key, JsonParser.parseString(content).getAsJsonObject());
            }
            return documents.get(key);
        }

        private byte[] resource(String name) throws IOException {
            var data = resources.get(normalize(name));
            if (data == null) throw new IOException("Missing referenced resource: " + name);
            return data;
        }
    }

    private static String normalize(String name) {
        var normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":") || List.of(normalized.split("/", -1)).stream().anyMatch(p -> p.isEmpty() || p.equals(".."))) {
            throw new IllegalArgumentException("Unsafe resource path: " + name);
        }
        return normalized;
    }

    private static String stem(String name) {
        var basename = name.substring(name.lastIndexOf('/') + 1);
        var dot = basename.lastIndexOf('.');
        return dot < 0 ? basename : basename.substring(0, dot);
    }

    private static boolean excluded(String name) {
        var base = name.replace('\\', '/').toLowerCase(Locale.ROOT);
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.matches("(arm|arms|tac|arrow|projectile)([._ -].*)?");
    }

    private static boolean image(String name) {
        return name.toLowerCase(Locale.ROOT).matches(".*\\.(png|webp|avif|jpg|jpeg|bmp)$");
    }
}
