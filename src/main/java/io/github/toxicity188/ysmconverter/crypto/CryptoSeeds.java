package io.github.toxicity188.ysmconverter.crypto;

/**
 * Common cryptographic seed constants used by the YSM format.
 *
 * @since 1.0.0
 */
public final class CryptoSeeds {

    /**
     * Seed used for MT19937 key derivation.
     */
    public static final long SEED_KEY_DERIVATION = 0xD017CBBA7B5D3581L;

    /**
     * Seed used for resource verification and Modified ChaCha decryption.
     */
    public static final long SEED_RES_VERIFICATION = 0xA62B1A2C43842BC3L;

    /**
     * Seed used for cached model decryption.
     */
    public static final long SEED_CACHE_DECRYPTION = 0xD1C3D1D13A99752BL;

    /**
     * Seed used for whole file integrity verification.
     */
    public static final long SEED_FILE_VERIFICATION = 0x9E5599DB80C67C29L;

    /**
     * Seed used for packet integrity verification.
     */
    public static final long SEED_PACKET_VERIFICATION = 0xEE6FA63D570BD77BL;

    /**
     * Seed used for cache integrity verification.
     */
    public static final long SEED_CACHE_VERIFICATION = 0xF346451E53A22261L;

    private CryptoSeeds() {
    }
}
