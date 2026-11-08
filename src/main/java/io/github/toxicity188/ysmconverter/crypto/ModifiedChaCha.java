package io.github.toxicity188.ysmconverter.crypto;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

/**
 * Implements the YSM-specific Modified ChaCha cipher.
 *
 * <p>Blocks are encrypted or decrypted with dynamic round counts and state permutations
 * updated after each chunk using CityHash of the plaintext.
 *
 * <pre>{@code
 * byte[] decrypted = ModifiedChaCha.decrypt(data, key, iv, CryptoSeeds.SEED_RES_VERIFICATION);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ModifiedChaCha {

    private ModifiedChaCha() {
    }

    /**
     * Decrypts data using YSM's Modified ChaCha algorithm.
     *
     * @param data the ciphertext bytes
     * @param key  the 32-byte key
     * @param iv   the 24-byte IV
     * @param seed the seed used for round and block hash derivation
     * @return the decrypted plaintext
     */
    @CheckReturnValue
    public static byte @NotNull [] decrypt(byte @NotNull [] data, byte @NotNull [] key, byte @NotNull [] iv, long seed) {
        var keyIv = new byte[56];
        System.arraycopy(key, 0, keyIv, 0, 32);
        System.arraycopy(iv, 0, keyIv, 32, 24);

        long hash2 = CityHash.cityHash64WithSeed(keyIv, 0, 56, seed);
        int nextRoundSize = (int) (((hash2 & 0x3FL) | 0x40L) << 6);

        var ctx = new XChaCha20();
        ctx.setRounds(10 * (int) Long.remainderUnsigned(hash2, 3) + 10);
        ctx.keySetup(key, iv);

        var result = new byte[data.length];
        int blockPointer = 0;

        while (blockPointer < data.length) {
            int chunkSize = Math.min(nextRoundSize, data.length - blockPointer);
            ctx.cryptBytes(data, blockPointer, result, blockPointer, chunkSize);

            long resHash = CityHash.cityHash64WithSeed(result, blockPointer, chunkSize, seed);
            nextRoundSize = ctx.updateState(resHash);
            blockPointer += chunkSize;
        }

        return result;
    }

    /**
     * Encrypts data using YSM's Modified ChaCha algorithm.
     *
     * @param data the plaintext bytes
     * @param key  the 32-byte key
     * @param iv   the 24-byte IV
     * @param seed the seed used for round and block hash derivation
     * @return the encrypted ciphertext
     */
    @CheckReturnValue
    public static byte @NotNull [] encrypt(byte @NotNull [] data, byte @NotNull [] key, byte @NotNull [] iv, long seed) {
        var keyIv = new byte[56];
        System.arraycopy(key, 0, keyIv, 0, 32);
        System.arraycopy(iv, 0, keyIv, 32, 24);

        long hash2 = CityHash.cityHash64WithSeed(keyIv, 0, 56, seed);
        int nextRoundSize = (int) (((hash2 & 0x3FL) | 0x40L) << 6);

        var ctx = new XChaCha20();
        ctx.setRounds(10 * (int) Long.remainderUnsigned(hash2, 3) + 10);
        ctx.keySetup(key, iv);

        var result = new byte[data.length];
        int blockPointer = 0;

        while (blockPointer < data.length) {
            int chunkSize = Math.min(nextRoundSize, data.length - blockPointer);
            ctx.cryptBytes(data, blockPointer, result, blockPointer, chunkSize);

            long resHash = CityHash.cityHash64WithSeed(data, blockPointer, chunkSize, seed);
            nextRoundSize = ctx.updateState(resHash);
            blockPointer += chunkSize;
        }

        return result;
    }
}
