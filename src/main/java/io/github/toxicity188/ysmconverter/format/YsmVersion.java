package io.github.toxicity188.ysmconverter.format;

/**
 * Enumeration of supported YSM format major versions.
 *
 * @since 1.0.0
 */
public enum YsmVersion {
    /**
     * Legacy YSM Version 1 (AES-CBC without key obfuscation, zlib compressed).
     */
    V1(1),

    /**
     * Legacy YSM Version 2 (AES-CBC with MD5 and JavaRandom key derivation, zlib compressed).
     */
    V2(2),

    /**
     * Modern YSM Version 3 (Modified ChaCha + MT19937, YsmZstd compressed, binary serialized).
     */
    V3(3);

    private final int majorNumber;

    YsmVersion(int majorNumber) {
        this.majorNumber = majorNumber;
    }

    /**
     * Gets the numeric major version identifier.
     *
     * @return major version number
     */
    public int getMajorNumber() {
        return majorNumber;
    }
}
