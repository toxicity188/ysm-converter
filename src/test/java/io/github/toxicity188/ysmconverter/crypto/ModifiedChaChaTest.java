package io.github.toxicity188.ysmconverter.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModifiedChaChaTest {

    @Test
    void testModifiedChaChaRoundtrip() {
        var key = new byte[32];
        var iv = new byte[24];
        for (int i = 0; i < 32; i++) key[i] = (byte) (i + 1);
        for (int i = 0; i < 24; i++) iv[i] = (byte) (i + 33);

        // Test small, medium, and multi-chunk sizes (chunk size is typically ~4096-8192)
        int[] sizes = {10, 256, 4096, 8192, 16384};

        for (int size : sizes) {
            byte[] plain = new byte[size];
            for (int i = 0; i < size; i++) {
                plain[i] = (byte) ((i * 17) ^ 0x5C);
            }

            byte[] cipher = ModifiedChaCha.encrypt(plain, key, iv, CryptoSeeds.SEED_RES_VERIFICATION);
            assertNotNull(cipher);
            assertEquals(plain.length, cipher.length);

            byte[] decrypted = ModifiedChaCha.decrypt(cipher, key, iv, CryptoSeeds.SEED_RES_VERIFICATION);
            assertArrayEquals(plain, decrypted, "Decrypted data must match plaintext for size " + size);
        }
    }
}
