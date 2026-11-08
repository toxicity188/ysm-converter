package io.github.toxicity188.ysmconverter.parser;

import io.github.toxicity188.ysmconverter.crypto.AesCipher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

/**
 * Parser for legacy YSM Version 1 model files.
 *
 * <pre>{@code
 * YsmParserV1 parser = new YsmParserV1(data);
 * parser.parse();
 * parser.saveToDirectory(outputDir);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class YsmParserV1 implements YsmParser {

    private final byte[] buffer;
    private final Map<String, byte[]> resources = new LinkedHashMap<>();

    /**
     * Constructs a parser for legacy YSM V1 model bytes.
     *
     * @param buffer raw .ysm file data
     */
    public YsmParserV1(byte[] buffer) {
        this.buffer = buffer;
    }

    @Override
    public int getYSGPVersion() {
        return 1;
    }

    @Override
    public void parse() throws IOException {
        if (buffer == null || buffer.length < 24) {
            return;
        }

        int offset = 8; // Skip Magic and Version
        offset += 16;  // Skip initial 16-byte key

        while (offset < buffer.length) {
            int strSize = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;

            var fileName = new String(buffer, offset, strSize, StandardCharsets.UTF_8);
            offset += strSize;

            int dataLen = ((buffer[offset] & 0xFF) << 24)
                    | ((buffer[offset + 1] & 0xFF) << 16)
                    | ((buffer[offset + 2] & 0xFF) << 8)
                    | (buffer[offset + 3] & 0xFF);
            offset += 4;

            var aesKey = new byte[16];
            System.arraycopy(buffer, offset, aesKey, 0, 16);
            offset += 16;

            var ivBytes = new byte[16];
            System.arraycopy(buffer, offset, ivBytes, 0, 16);
            offset += 16;

            var encryptedData = new byte[dataLen];
            System.arraycopy(buffer, offset, encryptedData, 0, dataLen);
            offset += dataLen;

            try {
                byte[] decrypted = AesCipher.decryptCbc(aesKey, ivBytes, encryptedData);
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
