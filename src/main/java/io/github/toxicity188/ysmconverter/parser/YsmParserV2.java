package io.github.toxicity188.ysmconverter.parser;

import io.github.toxicity188.ysmconverter.crypto.AesCipher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

/**
 * Parser for legacy YSM Version 2 model files.
 *
 * <pre>{@code
 * YsmParserV2 parser = new YsmParserV2(data);
 * parser.parse();
 * parser.saveToDirectory(outputDir);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class YsmParserV2 implements YsmParser {

    private final byte @NotNull [] buffer;
    private final @NotNull Map<@NotNull String, byte @NotNull []> resources = new LinkedHashMap<>();

    /**
     * Constructs a parser for legacy YSM V2 model bytes.
     *
     * @param buffer raw .ysm file data
     */
    public YsmParserV2(byte @NotNull [] buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer must not be null");
    }

    @Override
    public int getYSGPVersion() {
        return 2;
    }

    @Override
    public int getFormatVersion() {
        return 2;
    }

    @Override
    public void parse() throws IOException {
        if (buffer.length < 24) {
            return;
        }

        int magic = ((buffer[0] & 0xFF)) | ((buffer[1] & 0xFF) << 8) | ((buffer[2] & 0xFF) << 16) | ((buffer[3] & 0xFF) << 24);
        if (magic != 0x50475359) {
            throw new IllegalArgumentException("Invalid YSM V2 magic bytes");
        }

        int offset = 8; // Skip Magic and Version
        offset += 16;  // Skip initial 16-byte key

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }

        while (offset < buffer.length) {
            int strSize = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;

            String base64Name = new String(buffer, offset, strSize, StandardCharsets.UTF_8);
            offset += strSize;
            String fileName = new String(Base64.getDecoder().decode(base64Name), StandardCharsets.UTF_8);

            int dataLen = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;

            int encKeyLen = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;

            byte[] encryptedKey = new byte[encKeyLen];
            System.arraycopy(buffer, offset, encryptedKey, 0, encKeyLen);
            offset += encKeyLen;

            byte[] ivBytes = new byte[16];
            System.arraycopy(buffer, offset, ivBytes, 0, 16);
            offset += 16;

            byte[] encryptedData = new byte[dataLen];
            System.arraycopy(buffer, offset, encryptedData, 0, dataLen);
            offset += dataLen;

            byte[] digest = md5.digest(encryptedData);
            long seed = 0;
            for (int i = 8; i < 16; i++) {
                seed = (seed << 8) | (digest[i] & 0xFFL);
            }

            var rand = new Random(seed);
            var randomKey = new byte[16];
            rand.nextBytes(randomKey);

            try {
                byte[] realKey = AesCipher.decryptCbc(randomKey, ivBytes, encryptedKey);
                byte[] decrypted = AesCipher.decryptCbc(realKey, ivBytes, encryptedData);
                byte[] decompressed = zlibDecompress(decrypted);
                resources.put(fileName, decompressed);
            } catch (GeneralSecurityException e) {
                throw new IOException("Failed to decrypt resource: " + fileName, e);
            }
        }
    }

    @Override
    public byte @NotNull [] getDecryptedData() {
        return new byte[0];
    }

    @Override
    public void saveToDirectory(@NotNull Path outputDirectory) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory must not be null");
        for (var entry : resources.entrySet()) {
            Path targetFile = PathSanitizer.safeResolve(outputDirectory, entry.getKey());
            PathSanitizer.writeSafe(targetFile, entry.getValue());
        }
    }

    @Override
    public @NotNull @UnmodifiableView Map<@NotNull String, byte @NotNull []> getExtractedResources() {
        return Collections.unmodifiableMap(resources);
    }

    private static byte[] zlibDecompress(byte[] compressed) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var infOut = new InflaterOutputStream(out, new Inflater())) {
            infOut.write(compressed);
        }
        return out.toByteArray();
    }
}
