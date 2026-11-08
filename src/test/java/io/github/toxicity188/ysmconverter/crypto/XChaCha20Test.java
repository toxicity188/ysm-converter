package io.github.toxicity188.ysmconverter.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class XChaCha20Test {

    @Test
    void testEncryptDecryptSymmetry() {
        var key = new byte[32];
        var iv = new byte[24];
        for (int i = 0; i < 32; i++) key[i] = (byte) (i * 7);
        for (int i = 0; i < 24; i++) iv[i] = (byte) (i * 13);

        byte[] plain = "The quick brown fox jumps over the lazy dog. 1234567890".getBytes(StandardCharsets.UTF_8);

        var cipherEnc = new XChaCha20();
        cipherEnc.keySetup(key, iv);
        var cipherText = new byte[plain.length];
        cipherEnc.cryptBytes(plain, 0, cipherText, 0, plain.length);

        assertNotEquals(new String(plain), new String(cipherText));

        var cipherDec = new XChaCha20();
        cipherDec.keySetup(key, iv);
        var decrypted = new byte[plain.length];
        cipherDec.cryptBytes(cipherText, 0, decrypted, 0, cipherText.length);

        assertArrayEquals(plain, decrypted);
    }

    @Test
    void testDifferentRounds() {
        var key = new byte[32];
        var iv = new byte[24];
        byte[] plain = "Testing different round counts (10, 20, 30)".getBytes(StandardCharsets.UTF_8);

        for (int rounds : new int[]{10, 20, 30}) {
            var enc = new XChaCha20();
            enc.setRounds(rounds);
            enc.keySetup(key, iv);
            var cipherText = new byte[plain.length];
            enc.cryptBytes(plain, 0, cipherText, 0, plain.length);

            var dec = new XChaCha20();
            dec.setRounds(rounds);
            dec.keySetup(key, iv);
            var decrypted = new byte[plain.length];
            dec.cryptBytes(cipherText, 0, decrypted, 0, cipherText.length);

            assertArrayEquals(plain, decrypted, "Round " + rounds + " should decrypt correctly");
        }
    }
}
