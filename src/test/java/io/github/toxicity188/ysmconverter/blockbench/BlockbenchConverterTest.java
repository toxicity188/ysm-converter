package io.github.toxicity188.ysmconverter.blockbench;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlockbenchConverterTest {
    @TempDir Path temporary;

    @Test
    void convertsCoordinatesHierarchyAndBothUvModes() throws IOException {
        var resources = resources("""
                {"name":"child","parent":"root","pivot":[1,2,3],"rotation":[10,20,30],"cubes":[
                  {"origin":[1,2,3],"size":[2,3,4],"pivot":[5,6,7],"rotation":[15,25,35],"inflate":0.1,"mirror":true,"uv":[8,9]},
                  {"origin":[0,0,0],"size":[2,3,4],"uv":{"north":{"uv":[10,20],"uv_size":[-2,3]},"up":{"uv":[1,2],"uv_size":[2,4]},"down":{"uv":[3,4],"uv_size":[2,-4],"uv_rotation":90}}}
                ]},{"name":"root","pivot":[0,0,0]}
                """);
        var result = BlockbenchConverter.convert(resources);
        var root = result.model().getAsJsonArray("outliner").get(0).getAsJsonObject();
        var child = root.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("root", root.get("name").getAsString());
        assertJson("[-1,2,3]", child.get("origin"));
        assertJson("[-10,-20,30]", child.get("rotation"));
        var cube = result.model().getAsJsonArray("elements").get(0).getAsJsonObject();
        assertJson("[-3,2,3]", cube.get("from"));
        assertJson("[-1,5,7]", cube.get("to"));
        assertJson("[-5,6,7]", cube.get("origin"));
        assertJson("[-15,-25,35]", cube.get("rotation"));
        assertEquals(0.1, cube.get("inflate").getAsDouble());
        assertTrue(cube.get("mirror_uv").getAsBoolean());
        assertJson("[18,13,14,16]", cube.getAsJsonObject("faces").getAsJsonObject("east").get("uv"));
        var faces = result.model().getAsJsonArray("elements").get(1).getAsJsonObject().getAsJsonObject("faces");
        assertJson("[10,20,8,23]", faces.getAsJsonObject("north").get("uv"));
        assertJson("[3,6,1,2]", faces.getAsJsonObject("up").get("uv"));
        assertJson("[5,0,3,4]", faces.getAsJsonObject("down").get("uv"));
        assertEquals(90, faces.getAsJsonObject("down").get("rotation").getAsInt());
        assertTrue(faces.getAsJsonObject("south").get("texture").isJsonNull());
        var destination = temporary.resolve("nested/model.bbmodel");
        result.write(destination);
        assertEquals(result.model(), JsonParser.parseString(Files.readString(destination)));
        var texture = result.model().getAsJsonArray("textures").get(0).getAsJsonObject();
        assertTrue(texture.get("source").getAsString().startsWith("data:image/png;base64,"));
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(texture.get("source").getAsString().split(",")[1]))));
    }

    @Test
    void preservesAnimationExpressionsPrePostInterpolationAndInferredDuration() throws IOException {
        var resources = resources("{\"name\":\"root\"}");
        put(resources, "main.animation.json", """
                {"animations":{"walk":{"loop":"hold_on_last_frame","bones":{"root":{
                  "position":{"1.5":{"pre":[1,2,3],"post":[4,5,6],"lerp_mode":"catmullrom"},"0":[0,0,0]},
                  "rotation":["query.head_x_rotation",2,3],"scale":1.5
                }},"timeline":{"0.2":["v.a=1;","v.b=2;"]}}}}
                """);
        var model = BlockbenchConverter.convert(resources);
        var animation = model.model().getAsJsonArray("animations").get(0).getAsJsonObject();
        assertEquals("hold", animation.get("loop").getAsString());
        assertEquals(1.5, animation.get("length").getAsDouble());
        var boneId = model.model().getAsJsonArray("outliner").get(0).getAsJsonObject().get("uuid").getAsString();
        var keys = animation.getAsJsonObject("animators").getAsJsonObject(boneId).getAsJsonArray("keyframes");
        assertEquals(0, keys.get(0).getAsJsonObject().get("time").getAsDouble());
        var split = keys.get(1).getAsJsonObject();
        assertEquals("catmullrom", split.get("interpolation").getAsString());
        assertJson("[{\"x\":1,\"y\":2,\"z\":3},{\"x\":4,\"y\":5,\"z\":6}]", split.get("data_points"));
        assertEquals("query.head_x_rotation", keys.get(2).getAsJsonObject().getAsJsonArray("data_points").get(0).getAsJsonObject().get("x").getAsString());
        assertJson("[{\"x\":1.5,\"y\":1.5,\"z\":1.5}]", keys.get(3).getAsJsonObject().get("data_points"));
        assertTrue(animation.getAsJsonObject("animators").has("effects"));
        assertTrue(model.warnings().stream().anyMatch(w -> w.contains("Molang")));
    }

    @Test
    void followsManifestAndExcludesArmTacProjectileAndAuxiliaryTextures() throws IOException {
        var resources = resources("{\"name\":\"body\"}");
        resources.put("models/player.json", resources.remove("main.json"));
        resources.put("textures/skin.png", resources.remove("skin.png"));
        put(resources, "ysm.json", """
                {"files":{"player":{"model":{"main":"models/player.json","arm":"missing-arm.json"},
                  "texture":[{"uv":"textures/skin.png","normal":"missing-normal.png"}],
                  "animation":{"main":"animations/player.json","arm":"missing-arm.json","tac":"missing-tac.json"}},
                  "projectiles":{"arrow":{"model":"missing-arrow.json"}}}}
                """);
        put(resources, "animations/player.json", "{\"animations\":{\"idle\":{\"bones\":{\"body\":{\"rotation\":[0,0,0]}}}}}");
        put(resources, "unused.json", "invalid unused document");
        var result = BlockbenchConverter.convert(resources);
        assertEquals(1, result.model().getAsJsonArray("textures").size());
        assertEquals(1, result.model().getAsJsonArray("animations").size());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Auxiliary")));
    }

    @Test
    void legacyExclusionsAndDuplicateAnimationsDoNotDependOnOriginalProject() throws IOException {
        var resources = resources("{\"name\":\"root\"}");
        var animation = "{\"animations\":{\"idle\":{\"bones\":{\"root\":{\"rotation\":[1,2,3]}}}}}";
        put(resources, "main.animation.json", animation);
        put(resources, "copy.json", animation);
        put(resources, "arm.animation.json", "invalid");
        put(resources, "tac.animation.json", "invalid");
        put(resources, "arm.json", "invalid");
        put(resources, "original.bbmodel", "invalid");
        resources.put("arrow.png", new byte[]{1});
        var result = BlockbenchConverter.convert(resources);
        assertEquals(1, result.model().getAsJsonArray("animations").size());
        assertEquals(1, result.model().getAsJsonArray("textures").size());
        assertEquals(result.model(), BlockbenchConverter.convert(resources).model());
    }

    @Test
    void rejectsCyclesMissingParentsMissingResourcesAndUnsafePaths() throws IOException {
        assertThrows(IOException.class, () -> BlockbenchConverter.convert(resources("{\"name\":\"a\",\"parent\":\"b\"},{\"name\":\"b\",\"parent\":\"a\"}")));
        assertThrows(IOException.class, () -> BlockbenchConverter.convert(resources("{\"name\":\"a\",\"parent\":\"missing\"}")));
        assertThrows(IOException.class, () -> BlockbenchConverter.convert(resources("{\"name\":\"a\"},{\"name\":\"a\"}")));
        var input = resources("{\"name\":\"a\"}");
        input.put("../skin.png", new byte[0]);
        assertThrows(IOException.class, () -> BlockbenchConverter.convert(input));
        input.remove("../skin.png");
        input.remove("skin.png");
        assertThrows(IOException.class, () -> BlockbenchConverter.convert(input));
    }

    @Test
    void readsLegacyBedrockGeometry() throws IOException {
        var input = resources("{\"name\":\"root\"}");
        put(input, "main.json", "{\"format_version\":\"1.8.0\",\"geometry.test\":{\"texturewidth\":32,\"textureheight\":16,\"bones\":[{\"name\":\"root\"}]}}");
        var result = BlockbenchConverter.convert(input);
        assertJson("{\"width\":32,\"height\":16}", result.model().get("resolution"));
    }

    @Test
    void preservesSignedCubeDimensionsFoundInReconstructedGeometry() throws IOException {
        var input = resources("""
                {"name":"body","cubes":[{"origin":[1,2,3],"size":[2,-0.2,-0.3],
                "uv":{"north":{"uv":[1,2],"uv_size":[2,0.2]}}}]}
                """);
        var cube = BlockbenchConverter.convert(input).model().getAsJsonArray("elements").get(0).getAsJsonObject();
        assertJson("[-3,2,3]", cube.get("from"));
        assertJson("[-1,1.8,2.7]", cube.get("to"));
    }

    @Test
    void malformedJsonAndNonFiniteAnimationTimesHaveCheckedDiagnostics() throws IOException {
        var input = resources("{\"name\":\"root\"}");
        put(input, "main.animation.json", "{\"animations\":{\"bad\":{\"bones\":{\"root\":{\"rotation\":{\"NaN\":[1,2,3]}}}}}}");
        assertThrows(IOException.class, () -> BlockbenchConverter.convert(input));
        put(input, "main.json", "{ broken");
        assertThrows(IOException.class, () -> BlockbenchConverter.convert(input));
    }

    private static Map<String, byte[]> resources(String bones) throws IOException {
        var resources = new LinkedHashMap<String, byte[]>();
        put(resources, "main.json", "{\"format_version\":\"1.12.0\",\"minecraft:geometry\":[{\"description\":{\"identifier\":\"geometry.test\",\"texture_width\":32,\"texture_height\":32},\"bones\":[" + bones + "]}]}");
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB), "PNG", bytes);
        resources.put("skin.png", bytes.toByteArray());
        return resources;
    }

    private static void put(Map<String, byte[]> resources, String name, String content) {
        resources.put(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertJson(String expected, com.google.gson.JsonElement actual) {
        assertEquals(JsonParser.parseString(expected), actual);
    }
}
