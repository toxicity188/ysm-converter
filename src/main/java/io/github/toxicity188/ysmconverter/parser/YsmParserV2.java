package io.github.toxicity188.ysmconverter.parser;

import io.github.toxicity188.ysmconverter.crypto.AesCipher;

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

    private final byte[] buffer;
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    /**
     * Constructs a parser for legacy YSM V2 model bytes.
     *
     * @param buffer raw .ysm file data
     */
    public YsmParserV2(byte[] buffer) {
        this.buffer = buffer;
    }

    @Override
    public int getYSGPVersion() {
        return 2;
    }

    @Override
    public void parse() throws IOException {
        if (buffer == null || buffer.length < 24) {
            return;
        }

        int offset = 8; // Skip Magic and Version
        offset += 16;  // Skip initial 16-byte key

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }

        while (offset < buffer.length) {
            int strSize = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;

            var b64 = new String(buffer, offset, strSize, StandardCharsets.UTF_8);
            offset += strSize;
            var fileName = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);

            int dataLen = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;

            int encryptedKeyLen = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;
            if (encryptedKeyLen != 32) {
                throw new IllegalArgumentException("Invalid encrypted key length: " + encryptedKeyLen);
            }

            var encryptedKey = new byte[32];
            System.arraycopy(buffer, offset, encryptedKey, 0, 32);
            offset += 32;

            var ivBytes = new byte[16];
            System.arraycopy(buffer, offset, ivBytes, 0, 16);
            offset += 16;

            var encryptedData = new byte[dataLen];
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
    public byte[] getDecryptedData() {
        return new byte[0];
    }

    @Override
    public void saveToDirectory(Path outputDirectory) throws IOException {
        for (var entry : resources.entrySet()) {
            Path targetFile = PathSanitizer.safeResolve(outputDirectory, entry.getKey());
            PathSanitizer.writeSafe(targetFile, entry.getValue());
        }
    }

    @Override
    public Map<String, byte[]> getExtractedResources() {
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
