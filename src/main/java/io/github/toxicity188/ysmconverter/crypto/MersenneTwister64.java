package io.github.toxicity188.ysmconverter.crypto;

/**
 * Pure Java implementation of 64-bit Mersenne Twister pseudorandom number generator,
 * exactly matching C++ {@code std::mt19937_64}.
 *
 * <pre>{@code
 * MersenneTwister64 mt = new MersenneTwister64(seed);
 * long val = mt.nextLong();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class MersenneTwister64 {

    private static final int NN = 312;
    private static final int MM = 156;
    private static final long MATRIX_A = 0xB5026F5AA96619E9L;
    private static final long UM = 0xFFFFFFFF80000000L;
    private static final long LM = 0x7FFFFFFFL;

    private final long[] mt = new long[NN];
    private int mti;

    /**
     * Constructs a new {@link MersenneTwister64} with the given 64-bit seed.
     *
     * @param seed the initial 64-bit seed
     */
    public MersenneTwister64(long seed) {
        init(seed);
    }

    /**
     * Initializes the state vector with the given seed.
     *
     * @param seed the initial 64-bit seed
     */
    public void init(long seed) {
        mt[0] = seed;
        for (mti = 1; mti < NN; mti++) {
            mt[mti] = (6364136223846793005L * (mt[mti - 1] ^ (mt[mti - 1] >>> 62)) + mti);
        }
    }

    /**
     * Generates the next pseudo-random 64-bit integer, matching {@code std::mt19937_64::operator()}.
     *
     * @return the next 64-bit integer
     */
    public long nextLong() {
        if (mti >= NN) {
            for (int i = 0; i < NN - MM; i++) {
                long x = (mt[i] & UM) | (mt[i + 1] & LM);
                mt[i] = mt[i + MM] ^ (x >>> 1) ^ ((x & 1L) != 0 ? MATRIX_A : 0L);
            }
            for (int i = NN - MM; i < NN - 1; i++) {
                long x = (mt[i] & UM) | (mt[i + 1] & LM);
                mt[i] = mt[i + (MM - NN)] ^ (x >>> 1) ^ ((x & 1L) != 0 ? MATRIX_A : 0L);
            }
            long x = (mt[NN - 1] & UM) | (mt[0] & LM);
            mt[NN - 1] = mt[MM - 1] ^ (x >>> 1) ^ ((x & 1L) != 0 ? MATRIX_A : 0L);
            mti = 0;
        }

        long x = mt[mti++];
        x ^= (x >>> 29) & 0x5555555555555555L;
        x ^= (x << 17) & 0x71D67FFFEDA60000L;
        x ^= (x << 37) & 0xFFF7EEE000000000L;
        x ^= (x >>> 43);
        return x;
    }
}
