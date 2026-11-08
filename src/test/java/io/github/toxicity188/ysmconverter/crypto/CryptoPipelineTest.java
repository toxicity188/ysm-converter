package io.github.toxicity188.ysmconverter.crypto;

import io.github.toxicity188.ysmconverter.compression.ZstdDecompressor;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;
import java.util.zip.Inflater;
import java.util.zip.InflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CryptoPipelineTest {

    @Test
    void testDecryptionPipelineOnRealV3Model() throws IOException {
        Path modelPath = Path.of("external/Yes-Steve-Model-Repo/_GenshinImpact/GI_qz_yg.ysm");
        if (!Files.exists(modelPath)) {
            System.out.println("Model file not found, skipping test.");
            return;
        }

        byte[] fileBytes = Files.readAllBytes(modelPath);
        assertTrue(fileBytes.length > 64);

        // 1. Verify whole file hash
        long expectedHash = (fileBytes[fileBytes.length - 8] & 0xFFL)
                | ((fileBytes[fileBytes.length - 7] & 0xFFL) << 8)
                | ((fileBytes[fileBytes.length - 6] & 0xFFL) << 16)
                | ((fileBytes[fileBytes.length - 5] & 0xFFL) << 24)
                | ((fileBytes[fileBytes.length - 4] & 0xFFL) << 32)
                | ((fileBytes[fileBytes.length - 3] & 0xFFL) << 40)
                | ((fileBytes[fileBytes.length - 2] & 0xFFL) << 48)
                | ((fileBytes[fileBytes.length - 1] & 0xFFL) << 56);

        long computedHash = CityHash.cityHash64WithSeed(fileBytes, 0, fileBytes.length - 8, CryptoSeeds.SEED_FILE_VERIFICATION);
        assertEquals(expectedHash, computedHash, "CityHash integrity check must match file hash");

        // 2. Extract key, IV
        byte[] key = new byte[32];
        System.arraycopy(fileBytes, fileBytes.length - 64, key, 0, 32);
        byte[] iv = new byte[24];
        System.arraycopy(fileBytes, fileBytes.length - 32, iv, 0, 24);

        // 3. Find binary data (after null-terminated header + 4-byte crypto magic)
        int nullIdx = -1;
        for (int i = 0; i < fileBytes.length; i++) {
            if (fileBytes[i] == 0) {
                nullIdx = i;
                break;
            }
        }
        assertTrue(nullIdx > 0, "Header must be null-terminated");

        int binaryStart = nullIdx + 1;
        int cryptoMagic = (fileBytes[binaryStart] & 0xFF)
                | ((fileBytes[binaryStart + 1] & 0xFF) << 8)
                | ((fileBytes[binaryStart + 2] & 0xFF) << 16)
                | ((fileBytes[binaryStart + 3] & 0xFF) << 24);
        assertEquals(3, cryptoMagic, "Crypto magic must be 3");

        binaryStart += 4;
        int binaryEnd = fileBytes.length - 64;
        byte[] binaryData = new byte[binaryEnd - binaryStart];
        System.arraycopy(fileBytes, binaryStart, binaryData, 0, binaryData.length);

        // 4. Decrypt ModifiedChaCha + MT19937
        byte[] chachaDecrypted = ModifiedChaCha.decrypt(binaryData, key, iv, CryptoSeeds.SEED_RES_VERIFICATION);
        byte[] xorredData = Mt19937Cipher.decrypt(chachaDecrypted, key, iv);

        int n = ((xorredData[0] & 0xFF) | ((xorredData[1] & 0xFF) << 8)) & 0x3FF;
        int trueDataOffset = 2 + n;
        byte[] trueData = new byte[xorredData.length - trueDataOffset];
        System.arraycopy(xorredData, trueDataOffset, trueData, 0, trueData.length);

        // 5. Decompress ZSTD
        byte[] decompressed = ZstdDecompressor.decompress(trueData);
        assertNotNull(decompressed);
        assertTrue(decompressed.length > 4);

        int version = (decompressed[0] & 0xFF)
                | ((decompressed[1] & 0xFF) << 8)
                | ((decompressed[2] & 0xFF) << 16)
                | ((decompressed[3] & 0xFF) << 24);
        System.out.println("Successfully decrypted and decompressed V3! Model format version = " + version);
        assertEquals(1, version, "GI_qz_yg.ysm format version should be 1");
    }

    @Test
    void testDecryptionPipelineOnRealV2Model() throws Exception {
        Path modelPath = Path.of("external/Yes-Steve-Model-Repo/_GenshinImpact/GI_Diluke.ysm");
        if (!Files.exists(modelPath)) {
            System.out.println("Model file not found, skipping test.");
            return;
        }

        byte[] data = Files.readAllBytes(modelPath);
        assertTrue(data.length > 24);

        // Check magic & version
        int magic = ((data[0] & 0xFF)) | ((data[1] & 0xFF) << 8) | ((data[2] & 0xFF) << 16) | ((data[3] & 0xFF) << 24);
        assertEquals(0x50475359, magic); // "YSGP"
        int cryptoVer = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        assertEquals(2, cryptoVer);

        int offset = 8;
        // Skip 16-byte key
        offset += 16;

        MessageDigest md5 = MessageDigest.getInstance("MD5");
        int resourceCount = 0;

        while (offset < data.length) {
            int strSize = ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            offset += 4;

            String b64 = new String(data, offset, strSize, StandardCharsets.UTF_8);
            offset += strSize;
            String fileName = new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);

            int dataLen = ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            offset += 4;

            int encKeyLen = ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            offset += 4;
            assertEquals(32, encKeyLen);

            byte[] encryptedKey = new byte[32];
            System.arraycopy(data, offset, encryptedKey, 0, 32);
            offset += 32;

            byte[] ivBytes = new byte[16];
            System.arraycopy(data, offset, ivBytes, 0, 16);
            offset += 16;

            byte[] encryptedData = new byte[dataLen];
            System.arraycopy(data, offset, encryptedData, 0, dataLen);
            offset += dataLen;

            // MD5 and JavaRandom
            byte[] digest = md5.digest(encryptedData);
            long seed = 0;
            for (int i = 8; i < 16; i++) {
                seed = (seed << 8) | (digest[i] & 0xFFL);
            }

            Random rand = new Random(seed);
            byte[] randomKey = new byte[16];
            rand.nextBytes(randomKey);

            byte[] realKey = AesCipher.decryptCbc(randomKey, ivBytes, encryptedKey);
            byte[] decrypted = AesCipher.decryptCbc(realKey, ivBytes, encryptedData);

            // Zlib inflate
            var out = new ByteArrayOutputStream();
            try (var infOut = new InflaterOutputStream(out, new Inflater())) {
                infOut.write(decrypted);
            }
            byte[] decompressed = out.toByteArray();
            assertTrue(decompressed.length > 0);
            System.out.println("V2 resource parsed: " + fileName + " (" + decompressed.length + " bytes)");
            resourceCount++;
        }

        assertTrue(resourceCount > 0, "Should have extracted V2 resources");
        System.out.println("Successfully extracted " + resourceCount + " resources from V2 model!");
    }
}
