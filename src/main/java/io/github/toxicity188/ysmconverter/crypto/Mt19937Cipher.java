package io.github.toxicity188.ysmconverter.crypto;

/**
 * Implements MT19937-64 based XOR keystream encryption and decryption.
 *
 * <pre>{@code
 * byte[] decrypted = Mt19937Cipher.decrypt(data, key, iv);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class Mt19937Cipher {

    private Mt19937Cipher() {
    }

    /**
     * Decrypts or encrypts data using an MT19937-64 keystream seeded by CityHash of (key + IV).
     *
     * @param data the data bytes to transform
     * @param key  the 32-byte key
     * @param iv   the 24-byte IV
     * @return the transformed data bytes
     */
    public static byte[] decrypt(byte[] data, byte[] key, byte[] iv) {
        var keyIv = new byte[56];
        System.arraycopy(key, 0, keyIv, 0, 32);
        System.arraycopy(iv, 0, keyIv, 32, 24);

        long seed = CityHash.cityHash64WithSeed(keyIv, 0, 56, CryptoSeeds.SEED_KEY_DERIVATION);
        var mt = new MersenneTwister64(seed);

        var result = new byte[data.length];
        int i = 0;
        while (i < data.length) {
            long rnd = mt.nextLong();
            for (int j = 0; j < 8 && i < data.length; ++j) {
                byte keystreamByte = (byte) ((rnd >>> (j * 8)) & 0xFF);
                result[i] = (byte) (data[i] ^ keystreamByte);
                i++;
            }
        }

        return result;
    }
}
