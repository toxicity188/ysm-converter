package io.github.toxicity188.ysmconverter.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.toxicity188.ysmconverter.binary.BufferReader;
import io.github.toxicity188.ysmconverter.binary.Vector3D;
import io.github.toxicity188.ysmconverter.compression.ZstdDecompressor;
import io.github.toxicity188.ysmconverter.crypto.CityHash;
import io.github.toxicity188.ysmconverter.crypto.CryptoSeeds;
import io.github.toxicity188.ysmconverter.crypto.ModifiedChaCha;
import io.github.toxicity188.ysmconverter.crypto.Mt19937Cipher;
import io.github.toxicity188.ysmconverter.format.VersionDetector;
import io.github.toxicity188.ysmconverter.model.GeometrySolver;
import io.github.toxicity188.ysmconverter.model.GeometryTypes.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Parser for modern YSM Version 3 model files.
 *
 * <pre>{@code
 * YsmParserV3 parser = new YsmParserV3(data);
 * parser.parse();
 * parser.saveToDirectory(outputDir);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class YsmParserV3 implements YsmParser {

    private final byte[] buffer;
    private final ObjectMapper mapper = new ObjectMapper();

    private int formatVersion;
    private byte[] decryptedData;
    private byte[] decompressedData;

    private final Map<String, String> subEntityCategories = new LinkedHashMap<>();
    private final Map<String, byte[]> soundFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> functionFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> languageFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> animControllerFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> textureFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> specialImageFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> avatarFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> modelFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> animationFiles = new LinkedHashMap<>();
    private final Map<String, byte[]> backgroundFiles = new LinkedHashMap<>();
    private byte[] infoJsonFile;
    private byte[] ysmJsonFile;

    private boolean formatJson = false;

    /**
     * Constructs a parser for YSM V3 model bytes.
     *
     * @param buffer raw .ysm file data
     */
    public YsmParserV3(byte[] buffer) {
        this.buffer = buffer;
    }

    /**
     * Sets whether extracted JSON files should be pretty-printed.
     *
     * @param formatJson true to pretty-print, false for compact
     */
    public void setFormatJson(boolean formatJson) {
        this.formatJson = formatJson;
        if (formatJson) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        } else {
            mapper.disable(SerializationFeature.INDENT_OUTPUT);
        }
    }

    private final List<String> warnings = new ArrayList<>();

    @Override
    public int getYSGPVersion() {
        return 3;
    }

    /**
     * Gets the parsed format sub-version (e.g. 1, 4, 15, 26, 32).
     *
     * @return format sub-version
     */
    @Override
    public int getFormatVersion() {
        if (formatVersion == 0 && buffer != null && buffer.length >= 8) {
            for (var i = 0; i < buffer.length; i++) {
                if (buffer[i] == 0) {
                    var headerString = new String(buffer, 0, i, StandardCharsets.UTF_8);
                    try {
                        formatVersion = VersionDetector.extractV3Format(headerString);
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        }
        return formatVersion;
    }

    @Override
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    @Override
    public byte[] getDecryptedData() {
        return decompressedData != null ? decompressedData : new byte[0];
    }

    @Override
    public void parse() throws IOException {
        if (buffer == null || buffer.length < 8 + 24 + 32 + 8) {
            throw new IllegalArgumentException("File too small to be a valid YSM V3 file");
        }

        // Find null-terminated header string
        int nullIdx = -1;
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] == 0) {
                nullIdx = i;
                break;
            }
        }
        if (nullIdx == -1) {
            throw new IllegalArgumentException("YSM V3 header not null-terminated");
        }

        var headerString = new String(buffer, 0, nullIdx, StandardCharsets.UTF_8);
        formatVersion = VersionDetector.extractV3Format(headerString);

        // Read tail (key: 32 bytes, IV: 24 bytes, fileHash: 8 bytes)
        var key = new byte[32];
        System.arraycopy(buffer, buffer.length - 64, key, 0, 32);
        var iv = new byte[24];
        System.arraycopy(buffer, buffer.length - 32, iv, 0, 24);

        long expectedHash = (buffer[buffer.length - 8] & 0xFFL)
                | ((buffer[buffer.length - 7] & 0xFFL) << 8)
                | ((buffer[buffer.length - 6] & 0xFFL) << 16)
                | ((buffer[buffer.length - 5] & 0xFFL) << 24)
                | ((buffer[buffer.length - 4] & 0xFFL) << 32)
                | ((buffer[buffer.length - 3] & 0xFFL) << 40)
                | ((buffer[buffer.length - 2] & 0xFFL) << 48)
                | ((buffer[buffer.length - 1] & 0xFFL) << 56);

        long computedHash = CityHash.cityHash64WithSeed(buffer, 0, buffer.length - 8, CryptoSeeds.SEED_FILE_VERIFICATION);
        if (computedHash != expectedHash) {
            throw new SecurityException("YSM file integrity check failed (corrupted or tampered data)");
        }

        int binaryStart = nullIdx + 1;
        int crypto = (buffer[binaryStart] & 0xFF)
                | ((buffer[binaryStart + 1] & 0xFF) << 8)
                | ((buffer[binaryStart + 2] & 0xFF) << 16)
                | ((buffer[binaryStart + 3] & 0xFF) << 24);
        if (crypto != 3) {
            throw new IllegalArgumentException("Expected crypto flag 3, got " + crypto);
        }
        binaryStart += 4;

        int binaryEnd = buffer.length - 64;
        var binaryData = new byte[binaryEnd - binaryStart];
        System.arraycopy(buffer, binaryStart, binaryData, 0, binaryData.length);

        // Decrypt
        byte[] chachaDecrypted = ModifiedChaCha.decrypt(binaryData, key, iv, CryptoSeeds.SEED_RES_VERIFICATION);
        byte[] xorredData = Mt19937Cipher.decrypt(chachaDecrypted, key, iv);

        int n = ((xorredData[0] & 0xFF) | ((xorredData[1] & 0xFF) << 8)) & 0x3FF;
        int trueDataOffset = 2 + n;
        decryptedData = Arrays.copyOfRange(xorredData, trueDataOffset, xorredData.length);

        // Decompress
        decompressedData = ZstdDecompressor.decompress(decryptedData);

        // Binary deserialization
        var reader = new BufferReader(decompressedData);
        long version = reader.readDword();
        if (version != formatVersion) {
            throw new UnsupportedOperationException("Decompressed format version mismatch: expected "
                    + formatVersion + ", got " + version);
        }

        if (formatVersion < 4) {
            deserializeLegacyV1(reader);
        } else if (formatVersion <= 15) {
            deserializeLegacyV15(reader);
        } else {
            deserializeModern(reader);
        }
    }

    private void deserializeLegacyV1(BufferReader reader) throws IOException {
        long skipBytes = reader.readVarint();
        reader.setOffset(reader.getOffset() + (int) skipBytes);

        long modelCount = reader.readVarint();
        for (long i = 0; i < modelCount; i++) {
            long modelId = reader.readVarint();
            if (reader.readVarint() != 1) {
                throw new IllegalStateException("Unknown field in model definition");
            }
            byte[] model = parseModels(reader);
            String modelName = switch ((int) modelId) {
                case 1 -> "main";
                case 2 -> "arm";
                case 3 -> "arrow";
                default -> throw new IllegalStateException("Unknown model ID: " + modelId);
            };
            modelFiles.put(modelName, model);
        }

        long animationBlobCount = reader.readVarint();
        for (long i = 0; i < animationBlobCount; i++) {
            long modelId = reader.readVarint();
            reader.readVarint();
            byte[] anim = parseAnimations(reader);
            animationFiles.put(getAnimationModelName((int) modelId), anim);
        }

        long customTextureCount = reader.readVarint();
        for (long i = 0; i < customTextureCount; i++) {
            String textureName = reader.readString();
            if (textureName.equals("/ARROW\\")) {
                textureName = "arrow";
            }
            if (formatVersion < 4) {
                long unkk = reader.readVarint();
                if (unkk != 1) throw new IllegalStateException("Unknown texture flag: " + unkk);
            }

            byte[] fileData = reader.readByteSequence();
            int width = (int) reader.readVarint();
            int height = (int) reader.readVarint();
            byte[] pngBytes = ImageEncoder.encodeRgbaToPng(fileData, width, height);
            textureFiles.put(textureName, pngBytes);
        }

        long modelTableSize = reader.readVarint();
        for (long i = 0; i < modelTableSize; i++) {
            reader.readVarint();
            reader.readString();
        }

        long animationsTableSize = reader.readVarint();
        for (long i = 0; i < animationsTableSize; i++) {
            reader.readVarint();
            reader.readString();
        }

        long textureTableSize = reader.readVarint();
        for (long i = 0; i < textureTableSize; i++) {
            reader.readString();
            reader.readString();
        }
    }

    private void deserializeLegacyV15(BufferReader reader) throws IOException {
        long skipBytes = reader.readVarint();
        reader.setOffset(reader.getOffset() + (int) skipBytes);

        long modelCount = reader.readVarint();
        for (long i = 0; i < modelCount; i++) {
            long modelId = reader.readVarint();
            reader.readVarint(); // unk
            byte[] model = parseModels(reader);
            String modelName = switch ((int) modelId) {
                case 1 -> "main";
                case 2 -> "arm";
                case 3 -> "arrow";
                default -> throw new IllegalStateException("Unknown model ID: " + modelId);
            };
            modelFiles.put(modelName, model);
        }

        long animationBlobCount = reader.readVarint();
        for (long i = 0; i < animationBlobCount; i++) {
            long modelId = reader.readVarint();
            reader.readVarint();
            byte[] anim = parseAnimations(reader);
            animationFiles.put(getAnimationModelName((int) modelId), anim);
        }

        if (formatVersion > 9) {
            long animControllerCount = reader.readVarint();
            for (long i = 0; i < animControllerCount; i++) {
                String controllerName;
                if (formatVersion <= 15) {
                    controllerName = "controller";
                    reader.readVarint();
                } else {
                    controllerName = reader.readString();
                    reader.readString();
                }
                byte[] animController = parseAnimationControllers(reader);
                animControllerFiles.put(controllerName, animController);
            }

            long animationControllerTableSize = reader.readVarint();
            for (long i = 0; i < animationControllerTableSize; i++) {
                reader.readString();
                reader.readString();
            }
        }

        long customTextureCount = reader.readVarint();
        for (long i = 0; i < customTextureCount; i++) {
            String textureName = reader.readString();
            if (textureName.equals("/ARROW\\")) {
                textureName = "arrow";
            }
            byte[] fileData = reader.readByteSequence();
            int width = (int) reader.readVarint();
            int height = (int) reader.readVarint();
            byte[] pngBytes = ImageEncoder.encodeRgbaToPng(fileData, width, height);
            textureFiles.put(textureName, pngBytes);

            long subTextureSize = reader.readVarint();
            for (long j = 0; j < subTextureSize; j++) {
                long specularType = reader.readVarint();
                byte[] subData = reader.readByteSequence();
                int spW = (int) reader.readVarint();
                int spH = (int) reader.readVarint();
                byte[] spPngBytes = ImageEncoder.encodeRgbaToPng(subData, spW, spH);
                String suffix = (specularType == 1) ? "_normal" : (specularType == 2 ? "_specular" : "_special");
                specialImageFiles.put(textureName + suffix, spPngBytes);
            }
        }

        if (formatVersion > 9) {
            parseSoundFiles(reader);
            long soundTableCount = reader.readVarint();
            for (long i = 0; i < soundTableCount; i++) {
                reader.readString();
                reader.readString();
            }
        }

        long extraTextureCount = reader.readVarint();
        for (long i = 0; i < extraTextureCount; i++) {
            String textureName = reader.readString();
            byte[] fileData = reader.readByteSequence();
            int width = (int) reader.readVarint();
            int height = (int) reader.readVarint();
            byte[] pngBytes = ImageEncoder.encodeRgbaToPng(fileData, width, height);
            avatarFiles.put(textureName, pngBytes);
        }

        long modelTableSize = reader.readVarint();
        for (long i = 0; i < modelTableSize; i++) {
            reader.readVarint();
            reader.readString();
        }

        long animationsTableSize = reader.readVarint();
        for (long i = 0; i < animationsTableSize; i++) {
            reader.readVarint();
            reader.readString();
        }

        long textureTableSize = reader.readVarint();
        for (long i = 0; i < textureTableSize; i++) {
            reader.readString();
            reader.readString();
            long subTextureSize = reader.readVarint();
            for (long j = 0; j < subTextureSize; j++) {
                reader.readVarint();
                reader.readString();
            }
        }

        parseYsmJson(reader);
    }

    private void deserializeModern(BufferReader reader) throws IOException {
        parseSoundFiles(reader);
        parseFunctionFiles(reader);
        parseLanguageFiles(reader);

        if (formatVersion < 26) {
            long subEntitySize = reader.readVarint();
            for (long i = 0; i < subEntitySize; i++) {
                parseSubEntity(reader, "SubEntity");
            }
            reader.readVarint(); // footer flag
        } else {
            long vehiclesSize = reader.readVarint();
            for (long i = 0; i < vehiclesSize; i++) {
                parseSubEntity(reader, "vehicle");
            }
            long projectileSize = reader.readVarint();
            for (long i = 0; i < projectileSize; i++) {
                parseSubEntity(reader, "projectiles");
            }
        }

        if (reader.readVarint() != 1) {
            throw new IllegalStateException("Unknown field flag expected 1");
        }

        long animCount = reader.readVarint();
        for (long i = 0; i < animCount; i++) {
            long modelId = reader.readVarint();
            byte[] anim = parseAnimations(reader);
            animationFiles.put(getAnimationModelName((int) modelId), anim);
        }

        long animControllerCount = reader.readVarint();
        for (long i = 0; i < animControllerCount; i++) {
            String controllerName;
            if (formatVersion <= 15) {
                controllerName = "controller";
                reader.readVarint();
            } else {
                controllerName = reader.readString();
                reader.readString();
            }
            byte[] animController = parseAnimationControllers(reader);
            animControllerFiles.put(controllerName, animController);
        }

        parseTextureFiles(reader);

        long modelSize = reader.readVarint();
        for (long i = 0; i < modelSize; i++) {
            long modelId = reader.readVarint();
            byte[] model = parseModels(reader);
            String modelName = switch ((int) modelId) {
                case 1 -> "main";
                case 2 -> "arm";
                default -> throw new IllegalStateException("Unknown model ID: " + modelId);
            };
            modelFiles.put(modelName, model);
        }

        parseYsmJson(reader);
    }

    private void parseSubEntity(BufferReader reader, String categoryName) throws IOException {
        String subModuleName = "";
        if (formatVersion <= 26) {
            subModuleName = reader.readString();
        }

        boolean hasSubAnim = reader.readVarint() != 0;
        byte[] anim = null;
        if (hasSubAnim) {
            anim = parseAnimations(reader);
        }

        boolean hasSubController = reader.readVarint() != 0;
        byte[] subController = null;
        if (hasSubController) {
            reader.readString(); // controllerHash
            subController = parseAnimationControllers(reader);
        }

        byte[] basicTexture = parseSpecialImage(reader);
        reader.readVarint(); // uv_w
        reader.readVarint(); // uv_h
        reader.readVarint(); // uv_format
        reader.readVarint(); // uv_unk2

        var subTextureFiles = new ArrayList<Map.Entry<String, byte[]>>();
        long subTextureSize = reader.readVarint();
        for (long i = 0; i < subTextureSize; i++) {
            long specularType = reader.readVarint();
            byte[] basicSubTexture = parseSpecialImage(reader);
            if (specularType == 1) {
                subTextureFiles.add(Map.entry("normal", basicSubTexture));
            } else if (specularType == 2) {
                subTextureFiles.add(Map.entry("specular", basicSubTexture));
            } else {
                throw new IllegalStateException("Unknown specular type: " + specularType);
            }
            reader.readVarint(); // sp_w
            reader.readVarint(); // sp_h
            reader.readVarint(); // sp_format
            reader.readVarint(); // sp_unk2
        }

        byte[] model = parseModels(reader);

        if (formatVersion > 26) {
            long subModels = reader.readVarint();
            for (long j = 0; j < subModels; j++) {
                subModuleName = reader.readString();
                if (subModuleName.contains("minecraft:")) {
                    subModuleName = subModuleName.substring(subModuleName.indexOf(":") + 1);
                }
                subEntityCategories.put(subModuleName, categoryName);
                modelFiles.put(subModuleName, model);
                for (var item : subTextureFiles) {
                    textureFiles.put(subModuleName + "_" + item.getKey(), item.getValue());
                }
                textureFiles.put(subModuleName, basicTexture);
                if (hasSubAnim) {
                    animationFiles.put(categoryName + "/" + subModuleName, anim);
                }
                if (hasSubController) {
                    animControllerFiles.put(categoryName + "/" + subModuleName, subController);
                }
            }
            return;
        }

        if (subModuleName.contains("minecraft:")) {
            subModuleName = subModuleName.substring(subModuleName.indexOf(":") + 1);
        }
        subEntityCategories.put(subModuleName, categoryName);
        modelFiles.put(subModuleName, model);
        for (var item : subTextureFiles) {
            textureFiles.put(subModuleName + "_" + item.getKey(), item.getValue());
        }
        textureFiles.put(subModuleName, basicTexture);
        if (hasSubAnim) {
            animationFiles.put(categoryName + "/" + subModuleName, anim);
        }
        if (hasSubController) {
            animControllerFiles.put(categoryName + "/" + subModuleName, subController);
        }
    }

    private static String getAnimationModelName(int modelId) {
        String[] names = {
                "unknown", "main", "arm", "extra", "tac", "arrow",
                "carryon", "parcool", "swem", "slashblade", "tlm",
                "fp.arm", "immersive_melodies", "irons_spell_books"
        };
        if (modelId >= 0 && modelId < names.length) {
            return names[modelId];
        }
        return "unknown";
    }

    private byte[] parseSpecialImage(BufferReader reader) {
        reader.readString(); // hash
        return reader.readByteSequence();
    }

    private void parseSoundFiles(BufferReader reader) {
        long cnt = reader.readVarint();
        for (long i = 0; i < cnt; i++) {
            String name = reader.readString();
            if (formatVersion > 15) {
                reader.readString(); // hash
            }
            byte[] fileData = reader.readByteSequence();
            soundFiles.put(name, fileData);
        }
    }

    private void parseFunctionFiles(BufferReader reader) {
        long cnt = reader.readVarint();
        for (long i = 0; i < cnt; i++) {
            String name = reader.readString();
            reader.readString(); // hash
            byte[] fileData = reader.readByteSequence();
            functionFiles.put(name, fileData);
        }
    }

    private void parseLanguageFiles(BufferReader reader) throws IOException {
        long cnt = reader.readVarint();
        for (long i = 0; i < cnt; i++) {
            String name = reader.readString();
            reader.readString(); // hash
            ObjectNode nodesData = mapper.createObjectNode();
            long nodes = reader.readVarint();
            for (long j = 0; j < nodes; j++) {
                String nodeName = reader.readString();
                String nodeValue = reader.readString();
                nodesData.put(nodeName, nodeValue);
            }
            languageFiles.put(name, mapper.writeValueAsBytes(nodesData));
        }
    }

    private void parseTextureFiles(BufferReader reader) {
        long cnt = reader.readVarint();
        for (long i = 0; i < cnt; i++) {
            String name = reader.readString();
            reader.readString(); // hash
            byte[] fileData = reader.readByteSequence();
            reader.readVarint(); // width
            reader.readVarint(); // height
            reader.readVarint(); // image_format
            reader.readVarint(); // unk_flag

            long subTextureSize = reader.readVarint();
            for (long j = 0; j < subTextureSize; j++) {
                long specularType = reader.readVarint();
                byte[] specialImageData = parseSpecialImage(reader);
                String suffix = (specularType == 1) ? "_normal" : (specularType == 2 ? "_specular" : "_special");
                specialImageFiles.put(name + suffix, specialImageData);
                reader.readVarint(); // sp_w
                reader.readVarint(); // sp_h
                reader.readVarint(); // sp_format
                reader.readVarint(); // sp_flag
            }

            textureFiles.put(name, fileData);
        }
    }

    private record MolangTriple(Object v0, Object v1, Object v2) {
    }

    private record Keyframe(MolangTriple post, MolangTriple pre, int lerpMode) {
    }

    private record BonesKeyFrame(List<Map.Entry<Float, Keyframe>> bone) {
    }

    private BonesKeyFrame parseChannel(BufferReader reader) {
        long molangs = reader.readVarint();
        if (molangs == 0) {
            return null;
        }

        var list = new ArrayList<Map.Entry<Float, Keyframe>>((int) molangs);
        for (long i = 0; i < molangs; i++) {
            float time = reader.readFloat() / 20.0f;
            int lerpMode = (int) reader.readVarint();

            Object[] first = new Object[3];
            for (int j = 0; j < 3; j++) {
                int type = reader.readByte();
                if (type == 1) {
                    first[j] = reader.readFloat();
                } else if (type == 2) {
                    first[j] = reader.readString();
                } else {
                    throw new IllegalStateException("Unknown keyframe type: " + type);
                }
            }
            var firstData = new MolangTriple(first[0], first[1], first[2]);

            long hasPre = reader.readVarint();
            if (hasPre >= 2) {
                throw new IllegalStateException("Invalid hasPre flag: " + hasPre);
            }

            if (hasPre != 0) {
                Object[] second = new Object[3];
                for (int j = 0; j < 3; j++) {
                    int type = reader.readByte();
                    if (type == 1) {
                        second[j] = reader.readFloat();
                    } else if (type == 2) {
                        second[j] = reader.readString();
                    } else {
                        throw new IllegalStateException("Unknown keyframe type: " + type);
                    }
                }
                var secondData = new MolangTriple(second[0], second[1], second[2]);
                list.add(Map.entry(time, new Keyframe(secondData, firstData, lerpMode)));
            } else {
                list.add(Map.entry(time, new Keyframe(firstData, null, lerpMode)));
            }
        }

        return new BonesKeyFrame(list);
    }

    private JsonNode molangToJson(MolangTriple mp) {
        if (mp.v0().equals(mp.v1()) && mp.v1().equals(mp.v2())) {
            if (mp.v0() instanceof Float f) {
                return mapper.getNodeFactory().numberNode(Math.abs(f) < 1e-6f ? 0.0f : f);
            } else if (mp.v0() instanceof String s) {
                return mapper.getNodeFactory().textNode(s);
            }
        }

        ArrayNode arr = mapper.createArrayNode();
        Object[] vals = {mp.v0(), mp.v1(), mp.v2()};
        for (Object arg : vals) {
            if (arg instanceof Float f) {
                arr.add(Math.abs(f) < 1e-6f ? 0.0f : f);
            } else if (arg instanceof String s) {
                arr.add(s);
            }
        }
        return arr;
    }

    private JsonNode channelToJson(BonesKeyFrame channel) {
        if (channel == null || channel.bone().isEmpty()) {
            return null;
        }

        if (channel.bone().size() == 1
                && Math.abs(channel.bone().get(0).getKey()) < 1e-6f
                && channel.bone().get(0).getValue().pre() == null
                && channel.bone().get(0).getValue().lerpMode() == 0) {
            return molangToJson(channel.bone().get(0).getValue().post());
        }

        ObjectNode keyframes = mapper.createObjectNode();
        for (var entry : channel.bone()) {
            float time = entry.getKey();
            Keyframe kf = entry.getValue();

            String tStr;
            if (Math.abs(time) < 1e-6f) {
                tStr = "0.0";
            } else {
                tStr = String.format(Locale.ROOT, "%.6f", time);
                while (!tStr.isEmpty() && tStr.charAt(tStr.length() - 1) == '0') {
                    tStr = tStr.substring(0, tStr.length() - 1);
                }
                if (!tStr.isEmpty() && tStr.charAt(tStr.length() - 1) == '.') {
                    tStr += "0";
                }
            }

            JsonNode val;
            if (kf.pre() == null && kf.lerpMode() == 0) {
                val = molangToJson(kf.post());
            } else {
                ObjectNode kfData = mapper.createObjectNode();
                kfData.set("post", molangToJson(kf.post()));
                if (kf.pre() != null) {
                    kfData.set("pre", molangToJson(kf.pre()));
                }
                if (kf.lerpMode() == 1) {
                    kfData.put("lerp_mode", "step");
                } else if (kf.lerpMode() == 2) {
                    kfData.put("lerp_mode", "catmullrom");
                }
                val = kfData;
            }
            keyframes.set(tStr, val);
        }
        return keyframes;
    }

    private byte[] parseAnimations(BufferReader reader) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format_version", "1.8.0");
        ObjectNode animationsDesc = mapper.createObjectNode();

        if (formatVersion > 15) {
            reader.readString(); // hash
        }

        long animCount = reader.readVarint();
        for (long anim = 0; anim < animCount; anim++) {
            String animName = reader.readString();
            ObjectNode animObj = mapper.createObjectNode();

            float animLen = reader.readFloat() / 20.0f;
            if (animLen > 0.0f && !Float.isInfinite(animLen)) {
                animObj.put("animation_length", animLen);
            }

            long loop = reader.readVarint();
            if (loop == 1) {
                animObj.put("loop", true);
            } else if (loop == 2) {
                animObj.put("loop", "hold_on_last_frame");
            }

            if (formatVersion > 9) {
                if (reader.readVarint() != 0) throw new IllegalStateException();
                if (reader.readVarint() != 0) throw new IllegalStateException();

                long blendWeightMolangs = reader.readVarint();
                for (long molangI = 0; molangI < blendWeightMolangs; molangI++) {
                    int type = reader.readByte();
                    if (type == 1) {
                        animObj.put("blend_weight", reader.readFloat());
                    } else if (type == 2) {
                        animObj.put("blend_weight", reader.readString());
                    }
                }

                if (reader.readVarint() != 0) throw new IllegalStateException();
            }

            long boneCount = reader.readVarint();
            ObjectNode bonesObj = mapper.createObjectNode();

            for (long b = 0; b < boneCount; b++) {
                String boneNameValue = reader.readString();
                ObjectNode boneData = mapper.createObjectNode();

                var rotation = parseChannel(reader);
                var position = parseChannel(reader);
                var scale = parseChannel(reader);

                JsonNode rotJson = channelToJson(rotation);
                if (rotJson != null) boneData.set("rotation", rotJson);

                JsonNode posJson = channelToJson(position);
                if (posJson != null) boneData.set("position", posJson);

                JsonNode scaleJson = channelToJson(scale);
                if (scaleJson != null) boneData.set("scale", scaleJson);

                if (!boneData.isEmpty()) {
                    bonesObj.set(boneNameValue, boneData);
                }
            }

            if (!bonesObj.isEmpty()) {
                animObj.set("bones", bonesObj);
            }

            // Timeline
            long tlHeader = reader.readVarint();
            if (tlHeader > 0) {
                ObjectNode tlObj = mapper.createObjectNode();
                for (long i = 0; i < tlHeader; i++) {
                    long tlInside = reader.readVarint();
                    ArrayNode eventArr = mapper.createArrayNode();
                    for (long j = 0; j < tlInside; j++) {
                        eventArr.add(reader.readString());
                    }
                    float time = reader.readFloat() / 20.0f;
                    tlObj.set(String.valueOf(time), eventArr);
                }
                animObj.set("timeline", tlObj);
            }

            // Sound effects
            if (formatVersion > 9) {
                long effHeader = reader.readVarint();
                if (effHeader > 0) {
                    ObjectNode efObj = mapper.createObjectNode();
                    for (long i = 0; i < effHeader; i++) {
                        String effect = reader.readString();
                        float time = reader.readFloat() / 20.0f;
                        String timeStr = (Math.abs(time) < 1e-6f) ? "0.0" : String.valueOf(time);
                        ObjectNode effItem = mapper.createObjectNode();
                        effItem.put("effect", effect);
                        efObj.set(timeStr, effItem);
                    }
                    animObj.set("sound_effects", efObj);
                }
            }

            animationsDesc.set(animName, animObj);
        }

        if (!animationsDesc.isEmpty()) {
            root.set("animations", animationsDesc);
        }

        return mapper.writeValueAsBytes(root);
    }

    private byte[] parseAnimationControllers(BufferReader reader) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("format_version", "1.19.0");
        ObjectNode controllers = mapper.createObjectNode();

        long animCount = reader.readVarint();
        for (long anim = 0; anim < animCount; anim++) {
            String animName = reader.readString();
            ObjectNode controllerData = mapper.createObjectNode();

            String initialState = reader.readString();
            if (!initialState.isEmpty()) {
                controllerData.put("initial_state", initialState);
            }

            ObjectNode statesData = mapper.createObjectNode();
            long statesCount = reader.readVarint();

            for (long i = 0; i < statesCount; i++) {
                String stateName = reader.readString();
                ObjectNode stateObj = mapper.createObjectNode();

                long animationsSize = reader.readVarint();
                if (animationsSize > 0) {
                    ArrayNode animArray = mapper.createArrayNode();
                    for (long j = 0; j < animationsSize; j++) {
                        String ak = reader.readString();
                        String av = reader.readString();
                        if (av.isEmpty()) {
                            animArray.add(ak);
                        } else {
                            ObjectNode animItem = mapper.createObjectNode();
                            animItem.put(ak, av);
                            animArray.add(animItem);
                        }
                    }
                    stateObj.set("animations", animArray);
                }

                long transitionsSize = reader.readVarint();
                if (transitionsSize > 0) {
                    ArrayNode transArray = mapper.createArrayNode();
                    for (long j = 0; j < transitionsSize; j++) {
                        String tk = reader.readString();
                        String tv = reader.readString();
                        ObjectNode transItem = mapper.createObjectNode();
                        transItem.put(tk, tv);
                        transArray.add(transItem);
                    }
                    stateObj.set("transitions", transArray);
                }

                long onEntryCount = reader.readVarint();
                if (onEntryCount > 0) {
                    ArrayNode entryArray = mapper.createArrayNode();
                    for (long j = 0; j < onEntryCount; j++) {
                        entryArray.add(reader.readString());
                    }
                    stateObj.set("on_entry", entryArray);
                }

                long onExitCount = reader.readVarint();
                if (onExitCount > 0) {
                    ArrayNode exitArray = mapper.createArrayNode();
                    for (long j = 0; j < onExitCount; j++) {
                        exitArray.add(reader.readString());
                    }
                    stateObj.set("on_exit", exitArray);
                }

                if (reader.readVarint() != 0) {
                    stateObj.put("blend_transition", reader.readFloat());
                } else {
                    long blendTransitionsCount = reader.readVarint();
                    if (blendTransitionsCount > 0) {
                        ObjectNode blendObj = mapper.createObjectNode();
                        for (long j = 0; j < blendTransitionsCount; j++) {
                            float bk = reader.readFloat();
                            float bv = reader.readFloat();
                            blendObj.put(String.valueOf(bk), bv);
                        }
                        stateObj.set("blend_transitions", blendObj);
                    }
                }

                if (reader.readVarint() != 0) {
                    stateObj.put("blend_via_shortest_path", true);
                }

                if (formatVersion > 26) {
                    long soundEffectsCount = reader.readVarint();
                    if (soundEffectsCount > 0) {
                        ArrayNode soundEffectsArray = mapper.createArrayNode();
                        for (long j = 0; j < soundEffectsCount; j++) {
                            String effectName = reader.readString();
                            ObjectNode effectItem = mapper.createObjectNode();
                            effectItem.put("effect", effectName);
                            soundEffectsArray.add(effectItem);
                        }
                        stateObj.set("sound_effects", soundEffectsArray);
                    }
                }

                statesData.set(stateName, stateObj);
            }

            controllerData.set("states", statesData);
            controllers.set(animName, controllerData);
        }

        root.set("animation_controllers", controllers);
        return mapper.writeValueAsBytes(root);
    }

    private byte[] parseModels(BufferReader reader) throws IOException {
        if (formatVersion > 15) {
            reader.readString(); // sha256
        }

        long sizeOfBones = reader.readVarint();
        record ParsedBone(String name, String parent, Vector3D pivot, Vector3D rotation, List<List<Face>> cubes) {}
        var bones = new ArrayList<ParsedBone>((int) sizeOfBones);

        for (long i = 0; i < sizeOfBones; i++) {
            String parent = reader.readString();
            long cubesSize = reader.readVarint();
            var boneCubes = new ArrayList<List<Face>>((int) cubesSize);

            for (long j = 0; j < cubesSize; j++) {
                long uvSize = reader.readVarint();
                var faces = new ArrayList<Face>((int) uvSize);
                for (long k = 0; k < uvSize; k++) {
                    Vector3D normal = reader.readVector3D();
                    var vertices = new Vertex[4];
                    for (int v = 0; v < 4; v++) {
                        Vector3D vec = reader.readVector3D();
                        float u = reader.readFloat();
                        float vCoord = reader.readFloat();
                        vertices[v] = new Vertex(vec, u, vCoord);
                    }
                    faces.add(new Face(normal, vertices));
                }
                boneCubes.add(faces);

                reader.readVarint(); // unk1
                reader.readVarint(); // unk2
                reader.readVarint(); // unk3
            }

            String boneName = reader.readString();

            reader.readVarint();
            reader.readVarint();
            reader.readVarint();
            reader.readVarint();
            reader.readVarint();

            float pivotX = reader.readFloat();
            float pivotY = reader.readFloat();
            float pivotZ = reader.readFloat();

            float rotX = reader.readFloat();
            float rotY = reader.readFloat();
            float rotZ = reader.readFloat();

            bones.add(new ParsedBone(boneName, parent, new Vector3D(pivotX, pivotY, pivotZ),
                    new Vector3D(rotX, rotY, rotZ), boneCubes));
        }

        String identifier = reader.readString();
        float textureHeight = reader.readFloat();
        float textureWidth = reader.readFloat();
        float visibleBoundsHeight = reader.readFloat();
        float visibleBoundsWidth = reader.readFloat();

        long offsetSize = reader.readVarint();
        var offsetList = new ArrayList<Float>((int) offsetSize);
        for (long i = 0; i < offsetSize; i++) {
            offsetList.add(reader.readFloat());
        }

        float scaleX = reader.readFloat();
        float scaleY = reader.readFloat();

        long hasInfoJson = reader.readVarint();
        if (hasInfoJson > 0) {
            parseLegacyYSMInfo(reader);
        }

        reader.readVarint();
        reader.readVarint();
        reader.readVarint();

        ObjectNode outGeom = mapper.createObjectNode();
        ObjectNode description = mapper.createObjectNode();
        description.put("identifier", identifier);
        description.put("texture_width", GeometrySolver.cleanNumber(textureWidth));
        description.put("texture_height", GeometrySolver.cleanNumber(textureHeight));
        description.put("visible_bounds_width", GeometrySolver.cleanNumber(visibleBoundsWidth));
        description.put("visible_bounds_height", GeometrySolver.cleanNumber(visibleBoundsHeight));

        ArrayNode offsetArr = mapper.createArrayNode();
        for (float v : offsetList) {
            offsetArr.add(GeometrySolver.cleanNumber(v));
        }
        description.set("visible_bounds_offset", offsetArr);
        outGeom.set("description", description);

        ArrayNode bonesArr = mapper.createArrayNode();
        for (var b : bones) {
            ObjectNode bJson = mapper.createObjectNode();
            bJson.put("name", b.name());

            ArrayNode pivotArr = mapper.createArrayNode();
            pivotArr.add(GeometrySolver.cleanNumber(-b.pivot().x()));
            pivotArr.add(GeometrySolver.cleanNumber(b.pivot().y()));
            pivotArr.add(GeometrySolver.cleanNumber(b.pivot().z()));
            bJson.set("pivot", pivotArr);

            if (!b.parent().isEmpty()) {
                bJson.put("parent", b.parent());
            }

            float radToDeg = (float) (180.0 / Math.PI);
            var rot = new Vector3D((-b.rotation().x()) * radToDeg, (-b.rotation().y()) * radToDeg, b.rotation().z() * radToDeg);
            if (Math.abs(rot.x()) > 1e-6f || Math.abs(rot.y()) > 1e-6f || Math.abs(rot.z()) > 1e-6f) {
                ArrayNode rotArr = mapper.createArrayNode();
                rotArr.add(GeometrySolver.cleanNumber(rot.x()));
                rotArr.add(GeometrySolver.cleanNumber(rot.y()));
                rotArr.add(GeometrySolver.cleanNumber(rot.z()));
                bJson.set("rotation", rotArr);
            }

            ArrayNode cubesArr = mapper.createArrayNode();
            for (var faces : b.cubes()) {
                try {
                    BlockbenchCube bbCube = GeometrySolver.restoreBlockbenchCube(faces, 0.0f, (int) textureWidth, (int) textureHeight);
                    ObjectNode cJson = mapper.createObjectNode();

                    ArrayNode originArr = mapper.createArrayNode();
                    originArr.add(GeometrySolver.cleanNumber(bbCube.origin().x()));
                    originArr.add(GeometrySolver.cleanNumber(bbCube.origin().y()));
                    originArr.add(GeometrySolver.cleanNumber(bbCube.origin().z()));
                    cJson.set("origin", originArr);

                    ArrayNode sizeArr = mapper.createArrayNode();
                    sizeArr.add(GeometrySolver.cleanNumber(bbCube.size().x()));
                    sizeArr.add(GeometrySolver.cleanNumber(bbCube.size().y()));
                    sizeArr.add(GeometrySolver.cleanNumber(bbCube.size().z()));
                    cJson.set("size", sizeArr);

                    ArrayNode bbPivotArr = mapper.createArrayNode();
                    bbPivotArr.add(GeometrySolver.cleanNumber(bbCube.pivot().x()));
                    bbPivotArr.add(GeometrySolver.cleanNumber(bbCube.pivot().y()));
                    bbPivotArr.add(GeometrySolver.cleanNumber(bbCube.pivot().z()));
                    cJson.set("pivot", bbPivotArr);

                    ArrayNode bbRotArr = mapper.createArrayNode();
                    bbRotArr.add(GeometrySolver.cleanNumber(bbCube.rotation().x()));
                    bbRotArr.add(GeometrySolver.cleanNumber(bbCube.rotation().y()));
                    bbRotArr.add(GeometrySolver.cleanNumber(bbCube.rotation().z()));
                    cJson.set("rotation", bbRotArr);

                    ObjectNode uvJson = mapper.createObjectNode();
                    for (var uvEntry : bbCube.uv().entrySet()) {
                        ObjectNode faceUv = mapper.createObjectNode();
                        ArrayNode uvs = mapper.createArrayNode();
                        uvs.add(GeometrySolver.cleanNumber(uvEntry.getValue().u()));
                        uvs.add(GeometrySolver.cleanNumber(uvEntry.getValue().v()));
                        faceUv.set("uv", uvs);

                        ArrayNode uvSizes = mapper.createArrayNode();
                        uvSizes.add(GeometrySolver.cleanNumber(uvEntry.getValue().uSize()));
                        uvSizes.add(GeometrySolver.cleanNumber(uvEntry.getValue().vSize()));
                        faceUv.set("uv_size", uvSizes);

                        uvJson.set(uvEntry.getKey(), faceUv);
                    }
                    cJson.set("uv", uvJson);
                    cubesArr.add(cJson);
                } catch (Exception ignored) {
                }
            }

            if (!cubesArr.isEmpty()) {
                bJson.set("cubes", cubesArr);
            }

            bonesArr.add(bJson);
        }
        outGeom.set("bones", bonesArr);

        ObjectNode finalOutput = mapper.createObjectNode();
        finalOutput.put("format_version", "1.12.0");
        ArrayNode geomArr = mapper.createArrayNode();
        geomArr.add(outGeom);
        finalOutput.set("minecraft:geometry", geomArr);

        return mapper.writeValueAsBytes(finalOutput);
    }

    private void parseLegacyYSMInfo(BufferReader reader) throws IOException {
        String infoName = reader.readString();
        String infoTips = reader.readString();

        long extraAnims = reader.readVarint();
        ArrayNode jinfoExtra = mapper.createArrayNode();
        for (long i = 0; i < extraAnims; i++) {
            jinfoExtra.add(reader.readString());
        }

        long authors = reader.readVarint();
        ArrayNode jAuthors = mapper.createArrayNode();
        for (long i = 0; i < authors; i++) {
            jAuthors.add(reader.readString());
        }

        String infoLicense = reader.readString();
        boolean infoFree = reader.readVarint() != 0;

        ObjectNode j = mapper.createObjectNode();
        j.put("name", infoName);
        j.put("tips", infoTips);
        j.set("authors", jAuthors);
        j.put("license", infoLicense);
        j.set("extra_animation_names", jinfoExtra);
        j.put("free", infoFree);

        infoJsonFile = mapper.writeValueAsBytes(j);
    }

    private void parseYsmJson(BufferReader reader) throws IOException {
        reader.readString(); // sha256
        ObjectNode root = mapper.createObjectNode();
        ObjectNode metadata = mapper.createObjectNode();
        ObjectNode properties = mapper.createObjectNode();

        root.put("spec", 2);

        long isNewVersion = reader.readVarint();
        if (isNewVersion == 0) {
            return;
        }

        if (formatVersion <= 15) {
            reader.readVarint();
        }

        metadata.put("name", reader.readString());
        metadata.put("tips", reader.readString());

        ObjectNode license = mapper.createObjectNode();
        license.put("type", reader.readString());
        license.put("desc", reader.readString());
        metadata.set("license", license);

        long authorsCount = reader.readVarint();
        if (authorsCount > 0) {
            ArrayNode authorsArr = mapper.createArrayNode();
            for (long j = 0; j < authorsCount; j++) {
                ObjectNode authorObj = mapper.createObjectNode();
                String authorName = reader.readString();
                authorObj.put("name", authorName);
                authorObj.put("role", reader.readString());

                long authorContact = reader.readVarint();
                if (authorContact > 0) {
                    ObjectNode contactsObj = mapper.createObjectNode();
                    for (long k = 0; k < authorContact; k++) {
                        contactsObj.put(reader.readString(), reader.readString());
                    }
                    authorObj.set("contact", contactsObj);
                }

                authorObj.put("comment", reader.readString());
                authorObj.put("avatar", "avatar/" + PathSanitizer.sanitizeWindowsFilename(authorName + ".png"));
                authorsArr.add(authorObj);
            }
            metadata.set("authors", authorsArr);
        }

        long linksCount = reader.readVarint();
        if (linksCount > 0) {
            ObjectNode linksObj = mapper.createObjectNode();
            for (long j = 0; j < linksCount; j++) {
                linksObj.put(reader.readString(), reader.readString());
            }
            metadata.set("link", linksObj);
        }

        properties.put("width_scale", reader.readFloat());
        properties.put("height_scale", reader.readFloat());

        long extraAnimations = reader.readVarint();
        if (extraAnimations > 0) {
            ObjectNode extraAnimObj = mapper.createObjectNode();
            for (long j = 0; j < extraAnimations; j++) {
                extraAnimObj.put(reader.readString(), reader.readString());
            }
            properties.set("extra_animation", extraAnimObj);
        }

        if (formatVersion > 9) {
            long extraAnimationButtons = reader.readVarint();
            if (extraAnimationButtons > 0) {
                ArrayNode buttonsArr = mapper.createArrayNode();
                for (long j = 0; j < extraAnimationButtons; j++) {
                    ObjectNode buttonObj = mapper.createObjectNode();
                    buttonObj.put("id", reader.readString());
                    buttonObj.put("name", reader.readString());

                    if (reader.readVarint() != 0) throw new IllegalStateException();

                    long configForms = reader.readVarint();
                    if (configForms > 0) {
                        ArrayNode configArr = mapper.createArrayNode();
                        for (long k = 0; k < configForms; k++) {
                            ObjectNode formObj = mapper.createObjectNode();
                            String type = reader.readString();
                            formObj.put("type", type);
                            formObj.put("title", reader.readString());
                            formObj.put("description", reader.readString());
                            formObj.put("value", reader.readString());

                            float step = reader.readFloat();
                            float min = reader.readFloat();
                            float max = reader.readFloat();

                            if (type.equals("range")) {
                                formObj.put("step", step);
                                formObj.put("min", min);
                                formObj.put("max", max);
                            }

                            long labelsSize = reader.readVarint();
                            if (labelsSize > 0) {
                                ObjectNode labelsObj = mapper.createObjectNode();
                                for (long l = 0; l < labelsSize; l++) {
                                    labelsObj.put(reader.readString(), reader.readString());
                                }
                                formObj.set("labels", labelsObj);
                            }
                            configArr.add(formObj);
                        }
                        buttonObj.set("config_forms", configArr);
                    }
                    buttonsArr.add(buttonObj);
                }
                properties.set("extra_animation_buttons", buttonsArr);
            }

            long cnt = reader.readVarint();
            ArrayNode extraAnimationClassify = mapper.createArrayNode();
            for (long i = 0; i < cnt; i++) {
                ObjectNode sigObj = mapper.createObjectNode();
                sigObj.put("id", reader.readString());
                long extras = reader.readVarint();
                ObjectNode labelsObj = mapper.createObjectNode();
                for (long j = 0; j < extras; j++) {
                    labelsObj.put(reader.readString(), reader.readString());
                }
                sigObj.set("extra_animation", labelsObj);
                extraAnimationClassify.add(sigObj);
            }
            properties.set("extra_animation_classify", extraAnimationClassify);
        }

        String defaultTexture = reader.readString();
        if (!defaultTexture.isEmpty()) {
            properties.put("default_texture", defaultTexture);
        }

        String previewAnimation = reader.readString();
        if (!previewAnimation.isEmpty()) {
            properties.put("preview_animation", previewAnimation);
        }

        properties.put("free", reader.readVarint() != 0);

        if (formatVersion > 4) {
            properties.put("render_layers_first", reader.readVarint() != 0);
        }

        if (formatVersion >= 15) {
            properties.put("all_cutout", reader.readVarint() != 0);
            properties.put("disable_preview_rotation", reader.readVarint() != 0);
        }

        String guiForeground = "";
        String guiBackground = "";

        if (formatVersion > 15) {
            properties.put("gui_no_lighting", reader.readVarint() != 0);
            if (formatVersion >= 32) {
                properties.put("merge_multiline_expr", reader.readVarint() != 0);
            }

            guiForeground = reader.readString();
            if (!guiForeground.isEmpty()) {
                properties.put("gui_foreground", guiForeground);
            }

            guiBackground = reader.readString();
            if (!guiBackground.isEmpty()) {
                properties.put("gui_background", guiBackground);
            }

            long avatars = reader.readVarint();
            if (avatars > 0) {
                for (long j = 0; j < avatars; j++) {
                    String avatarName = reader.readString();
                    byte[] data = reader.readByteSequence();
                    avatarFiles.put(avatarName, data);
                    reader.readVarint(); // width
                    reader.readVarint(); // height
                    reader.readVarint(); // image_format
                    reader.readVarint(); // unk_flag
                }
            }
        }

        root.set("metadata", metadata);
        root.set("properties", properties);
        root.set("files", buildFilesFromParsedData());

        cleanJsonFloats(root);
        ysmJsonFile = mapper.writeValueAsBytes(root);

        if (formatVersion <= 15) {
            return;
        }

        if (!guiForeground.isEmpty() || !guiBackground.isEmpty()) {
            long bgCount = reader.readVarint();
            for (long i = 0; i < bgCount; i++) {
                String name = reader.readString();
                byte[] fileData = reader.readByteSequence();

                String relativePath;
                if (name.equals("gui_foreground") && !guiForeground.isEmpty()) {
                    relativePath = guiForeground;
                } else if (name.equals("gui_background") && !guiBackground.isEmpty()) {
                    relativePath = guiBackground;
                } else {
                    relativePath = "background/" + PathSanitizer.sanitizeWindowsFilename(name + ".png");
                }

                backgroundFiles.put(relativePath, fileData);
                reader.readVarint(); // width
                reader.readVarint(); // height
                reader.readVarint(); // image_format
                reader.readVarint(); // unk_flag
            }
        }
    }

    private void cleanJsonFloats(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            var fields = new ArrayList<String>();
            obj.fieldNames().forEachRemaining(fields::add);
            for (String field : fields) {
                JsonNode child = obj.get(field);
                if (child.isFloatingPointNumber()) {
                    double val = child.doubleValue();
                    double rounded = Math.round(val * 100000.0) / 100000.0;
                    obj.put(field, rounded);
                } else {
                    cleanJsonFloats(child);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isFloatingPointNumber()) {
                    double val = child.doubleValue();
                    double rounded = Math.round(val * 100000.0) / 100000.0;
                    arr.set(i, mapper.getNodeFactory().numberNode(rounded));
                } else {
                    cleanJsonFloats(child);
                }
            }
        }
    }

    private ObjectNode buildFilesFromParsedData() {
        ObjectNode files = mapper.createObjectNode();
        var subEntityModels = new HashSet<String>();
        for (String name : modelFiles.keySet()) {
            if (!name.equals("main") && !name.equals("arm")) {
                subEntityModels.add(name);
            }
        }

        Map<String, String> knownSubModels = Map.of(
                "arrow", "projectiles",
                "trident", "projectiles",
                "horse", "vehicles",
                "minecart", "vehicles",
                "boat", "vehicles"
        );

        // Player section
        ObjectNode player = mapper.createObjectNode();

        ObjectNode playerModels = mapper.createObjectNode();
        if (modelFiles.containsKey("main")) playerModels.put("main", "models/main.json");
        if (modelFiles.containsKey("arm")) playerModels.put("arm", "models/arm.json");
        if (!playerModels.isEmpty()) player.set("model", playerModels);

        ObjectNode playerAnims = mapper.createObjectNode();
        for (String name : animationFiles.keySet()) {
            if (name.contains("/")) continue;
            if (subEntityModels.contains(name)) continue;

            String key = normalizeAnimKey(name);
            String path = "animations/" + PathSanitizer.sanitizeWindowsFilename(animPathComponent(key) + ".animation.json");
            playerAnims.put(key, path);
        }
        if (!playerAnims.isEmpty()) player.set("animation", playerAnims);

        ArrayNode ac = mapper.createArrayNode();
        for (String name : animControllerFiles.keySet()) {
            if (name.contains("/")) continue;
            if (subEntityModels.contains(name)) continue;
            ac.add("controller/" + PathSanitizer.sanitizeWindowsFilename(name + ".json"));
        }
        player.set("animation_controllers", ac);

        ArrayNode playerTex = mapper.createArrayNode();
        for (String name : textureFiles.keySet()) {
            boolean isSub = false;
            for (String sn : subEntityModels) {
                if (name.equals(sn) || name.startsWith(sn + "_")) {
                    isSub = true;
                    break;
                }
            }
            if (isSub) continue;

            ObjectNode texObj = mapper.createObjectNode();
            texObj.put("uv", "textures/" + PathSanitizer.sanitizeWindowsFilename(name + ".png"));

            String expectedNormal = name + "_normal";
            String expectedSpecular = name + "_specular";

            if (specialImageFiles.containsKey(expectedNormal)) {
                texObj.put("normal", "textures/" + PathSanitizer.sanitizeWindowsFilename(expectedNormal + ".png"));
            }
            if (specialImageFiles.containsKey(expectedSpecular)) {
                texObj.put("specular", "textures/" + PathSanitizer.sanitizeWindowsFilename(expectedSpecular + ".png"));
            }
            playerTex.add(texObj);
        }
        if (!playerTex.isEmpty()) player.set("texture", playerTex);
        files.set("player", player);

        // Sub entities section
        var catModels = new LinkedHashMap<String, List<String>>();
        for (String mn : subEntityModels) {
            String cat = subEntityCategories.get(mn);
            if (cat == null) {
                cat = knownSubModels.getOrDefault(mn, "sub_entities");
            } else if (cat.equals("vehicle")) {
                cat = "vehicles";
            } else if (cat.equals("SubEntity")) {
                cat = knownSubModels.getOrDefault(mn, cat);
            }
            catModels.computeIfAbsent(cat, k -> new ArrayList<>()).add(mn);
        }

        for (var catEntry : catModels.entrySet()) {
            String cat = catEntry.getKey();
            ObjectNode section = mapper.createObjectNode();
            for (String mn : catEntry.getValue()) {
                ObjectNode entry = mapper.createObjectNode();
                entry.put("model", "models/" + PathSanitizer.sanitizeWindowsFilename(mn + ".json"));

                for (String animName : animationFiles.keySet()) {
                    boolean match = false;
                    String animPath = null;
                    if (animName.contains("/")) {
                        int sp = animName.indexOf('/');
                        if (animName.substring(sp + 1).equals(mn)) {
                            match = true;
                            String catPrefix = animName.substring(0, sp + 1);
                            String baseName = animName.substring(sp + 1);
                            animPath = "animations/" + catPrefix + PathSanitizer.sanitizeWindowsFilename(baseName + ".animation.json");
                        }
                    } else if (animName.equals(mn)) {
                        match = true;
                        animPath = (cat.equals("vehicles") ? "animations/vehicle/" : "animations/")
                                + PathSanitizer.sanitizeWindowsFilename(mn + ".animation.json");
                    }
                    if (match) {
                        entry.put("animation", animPath);
                        break;
                    }
                }

                for (String animName : animControllerFiles.keySet()) {
                    boolean match = false;
                    String animPath = null;
                    if (animName.contains("/")) {
                        int sp = animName.indexOf('/');
                        if (animName.substring(sp + 1).equals(mn)) {
                            match = true;
                            String catPrefix = animName.substring(0, sp + 1);
                            String baseName = animName.substring(sp + 1);
                            animPath = "controller/" + catPrefix + PathSanitizer.sanitizeWindowsFilename(baseName + ".json");
                        }
                    } else if (animName.equals(mn)) {
                        match = true;
                        animPath = (cat.equals("vehicles") ? "controller/vehicle/" : "controller/")
                                + PathSanitizer.sanitizeWindowsFilename(mn + ".json");
                    }
                    if (match) {
                        entry.put("controller", animPath);
                        break;
                    }
                }

                for (String texName : textureFiles.keySet()) {
                    if (texName.equals(mn) || texName.startsWith(mn + "_")) {
                        entry.put("texture", "textures/" + PathSanitizer.sanitizeWindowsFilename(mn + ".png"));
                        break;
                    }
                }

                section.set(mn, entry);
            }
            files.set(cat, section);
        }

        return files;
    }

    private static String normalizeAnimKey(String name) {
        if (name.equals("fp.arm") || name.equals("fp_arm")) return "fp_arm";
        if (name.equals("iss") || name.equals("irons_spell_books")) return "irons_spell_books";
        return name;
    }

    private static String animPathComponent(String key) {
        if (key.equals("fp_arm")) return "fp.arm";
        if (key.equals("irons_spell_books")) return "iss";
        return key;
    }

    @Override
    public void saveToDirectory(Path outputDirectory) throws IOException {
        boolean useLegacyRootLayout = (ysmJsonFile == null || ysmJsonFile.length == 0);

        if (useLegacyRootLayout) {
            if (infoJsonFile != null && infoJsonFile.length > 0) {
                Path target = PathSanitizer.safeResolve(outputDirectory, "info.json");
                PathSanitizer.writeSafe(target, infoJsonFile);
            }
        } else if (ysmJsonFile != null && ysmJsonFile.length > 0) {
            Path target = PathSanitizer.safeResolve(outputDirectory, "ysm.json");
            PathSanitizer.writeSafe(target, ysmJsonFile);
        }

        exportMapped(outputDirectory, useLegacyRootLayout, soundFiles, "sounds", ".ogg");
        exportMapped(outputDirectory, useLegacyRootLayout, functionFiles, "functions", ".molang");
        exportMapped(outputDirectory, useLegacyRootLayout, languageFiles, "lang", ".json");
        exportMapped(outputDirectory, useLegacyRootLayout, animControllerFiles, "controller", ".json");
        exportMapped(outputDirectory, useLegacyRootLayout, modelFiles, "models", ".json");
        exportMapped(outputDirectory, useLegacyRootLayout, animationFiles, "animations", ".animation.json");
        exportMapped(outputDirectory, useLegacyRootLayout, textureFiles, "textures", ".png");
        exportMapped(outputDirectory, useLegacyRootLayout, specialImageFiles, "textures", ".png");

        for (var entry : avatarFiles.entrySet()) {
            String safeFilename = PathSanitizer.sanitizeWindowsFilename(entry.getKey() + ".png");
            String rel = useLegacyRootLayout ? safeFilename : ("avatar/" + safeFilename);
            Path target = PathSanitizer.safeResolve(outputDirectory, rel);
            PathSanitizer.writeSafe(target, entry.getValue());
        }

        for (var entry : backgroundFiles.entrySet()) {
            String rel = entry.getKey();
            if (!rel.contains(".")) {
                rel += ".png";
            }
            Path p = Path.of(rel);
            String safeFilename = PathSanitizer.sanitizeWindowsFilename(p.getFileName().toString());
            Path safeParent = p.getParent();
            String safeRel = (safeParent != null ? safeParent.toString().replace('\\', '/') + "/" : "") + safeFilename;
            String relPath = useLegacyRootLayout ? safeFilename : safeRel;
            Path target = PathSanitizer.safeResolve(outputDirectory, relPath);
            PathSanitizer.writeSafe(target, entry.getValue());
        }
    }

    private void exportMapped(
            Path outputDirectory,
            boolean useLegacyRootLayout,
            Map<String, byte[]> items,
            String defaultFolder,
            String extension
    ) throws IOException {
        for (var entry : items.entrySet()) {
            String rawPath = entry.getKey();
            if (!rawPath.endsWith(extension)) {
                rawPath += extension;
            }
            Path p = Path.of(rawPath);
            String safeFilename = PathSanitizer.sanitizeWindowsFilename(p.getFileName().toString());
            Path parent = p.getParent();
            String safeRel = (parent != null ? parent.toString().replace('\\', '/') + "/" : "") + safeFilename;
            String finalRel = useLegacyRootLayout ? safeFilename : (defaultFolder + "/" + safeRel);
            Path target = PathSanitizer.safeResolve(outputDirectory, finalRel);
            PathSanitizer.writeSafe(target, entry.getValue());
        }
    }

    @Override
    public Map<String, byte[]> getExtractedResources() {
        var map = new LinkedHashMap<String, byte[]>();
        boolean useLegacyRootLayout = (ysmJsonFile == null || ysmJsonFile.length == 0);

        if (useLegacyRootLayout) {
            if (infoJsonFile != null && infoJsonFile.length > 0) {
                map.put("info.json", infoJsonFile);
            }
        } else if (ysmJsonFile != null && ysmJsonFile.length > 0) {
            map.put("ysm.json", ysmJsonFile);
        }

        addMappedToResources(map, useLegacyRootLayout, soundFiles, "sounds", ".ogg");
        addMappedToResources(map, useLegacyRootLayout, functionFiles, "functions", ".molang");
        addMappedToResources(map, useLegacyRootLayout, languageFiles, "lang", ".json");
        addMappedToResources(map, useLegacyRootLayout, animControllerFiles, "controller", ".json");
        addMappedToResources(map, useLegacyRootLayout, modelFiles, "models", ".json");
        addMappedToResources(map, useLegacyRootLayout, animationFiles, "animations", ".animation.json");
        addMappedToResources(map, useLegacyRootLayout, textureFiles, "textures", ".png");
        addMappedToResources(map, useLegacyRootLayout, specialImageFiles, "textures", ".png");

        for (var entry : avatarFiles.entrySet()) {
            String safeFilename = PathSanitizer.sanitizeWindowsFilename(entry.getKey() + ".png");
            String rel = useLegacyRootLayout ? safeFilename : ("avatar/" + safeFilename);
            map.put(rel, entry.getValue());
        }

        for (var entry : backgroundFiles.entrySet()) {
            String rel = entry.getKey();
            if (!rel.contains(".")) {
                rel += ".png";
            }
            Path p = Path.of(rel);
            String safeFilename = PathSanitizer.sanitizeWindowsFilename(p.getFileName().toString());
            Path safeParent = p.getParent();
            String safeRel = (safeParent != null ? safeParent.toString().replace('\\', '/') + "/" : "") + safeFilename;
            String relPath = useLegacyRootLayout ? safeFilename : safeRel;
            map.put(relPath, entry.getValue());
        }

        return Collections.unmodifiableMap(map);
    }

    private void addMappedToResources(
            Map<String, byte[]> target,
            boolean useLegacyRootLayout,
            Map<String, byte[]> items,
            String defaultFolder,
            String extension
    ) {
        for (var entry : items.entrySet()) {
            String rawPath = entry.getKey();
            if (!rawPath.endsWith(extension)) {
                rawPath += extension;
            }
            Path p = Path.of(rawPath);
            String safeFilename = PathSanitizer.sanitizeWindowsFilename(p.getFileName().toString());
            Path parent = p.getParent();
            String safeRel = (parent != null ? parent.toString().replace('\\', '/') + "/" : "") + safeFilename;
            String finalRel = useLegacyRootLayout ? safeFilename : (defaultFolder + "/" + safeRel);
            target.put(finalRel, entry.getValue());
        }
    }
}
