package io.github.toxicity188.ysmconverter.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CityHashTest {

    @Test
    void testEmptyAndShortInputs() {
        byte[] empty = new byte[0];
        long h0 = CityHash.cityHash64(empty, 0, 0);
        long h0Seed = CityHash.cityHash64WithSeed(empty, 0, 0, CryptoSeeds.SEED_FILE_VERIFICATION);
        assertNotEquals(0L, h0);
        assertNotEquals(0L, h0Seed);

        byte[] single = new byte[]{0x42};
        long h1 = CityHash.cityHash64(single, 0, 1);
        assertNotEquals(h0, h1);
    }

    @Test
    void testDeterminism() {
        byte[] data = "Hello, YSM Converter!".getBytes(StandardCharsets.UTF_8);
        long h1 = CityHash.cityHash64(data, 0, data.length);
        long h2 = CityHash.cityHash64(data, 0, data.length);
        assertEquals(h1, h2);

        long hs1 = CityHash.cityHash64WithSeed(data, 0, data.length, 123456789L);
        long hs2 = CityHash.cityHash64WithSeed(data, 0, data.length, 123456789L);
        assertEquals(hs1, hs2);
        assertNotEquals(h1, hs1);
    }

    @Test
    void testLengthsAcrossCityHashBranches() {
        // CityHash has branches for <= 16, <= 32, <= 64, and > 64 bytes
        int[] lengths = {0, 1, 4, 8, 15, 16, 17, 31, 32, 33, 63, 64, 65, 128, 256};
        for (int len : lengths) {
            byte[] buf = new byte[len];
            for (int i = 0; i < len; i++) {
                buf[i] = (byte) ((i * 31) ^ 0xAA);
            }
            long h = CityHash.cityHash64(buf, 0, len);
            long hs = CityHash.cityHash64WithSeed(buf, 0, len, CryptoSeeds.SEED_RES_VERIFICATION);
            assertNotEquals(0L, h, "Hash should be non-zero for len " + len);
            assertNotEquals(0L, hs, "Seeded hash should be non-zero for len " + len);
        }
    }
}
