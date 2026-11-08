package io.github.toxicity188.ysmconverter.crypto;

/**
 * Pure Java implementation of YSM-modified CityHash64.
 *
 * <p>YSM uses modified prime constants and a custom 128-to-64 mixing function
 * compared to standard Google CityHash 1.1.
 *
 * <pre>{@code
 * byte[] data = ...;
 * long hash = CityHash.cityHash64WithSeed(data, 0, data.length, CryptoSeeds.SEED_FILE_VERIFICATION);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class CityHash {

    private static final long K0 = 0xE4986A230E5AAA17L;
    private static final long K1 = 0x91AF10802CAB25A5L;
    private static final long K2 = 0xAF29CE778879D9C7L;
    private static final long KMUL = 0xDE0F6EE09BDBAB91L;

    private CityHash() {
    }

    /**
     * Compute 64-bit CityHash with a 64-bit seed using YSM modifications.
     *
     * @param s      the byte array to hash
     * @param offset the starting offset
     * @param len    the length of bytes to hash
     * @param seed   the 64-bit seed
     * @return the 64-bit hash
     */
    public static long cityHash64WithSeed(byte[] s, int offset, int len, long seed) {
        return cityHash64WithSeeds(s, offset, len, K2, seed);
    }

    /**
     * Compute 64-bit CityHash with two 64-bit seeds using YSM modifications.
     *
     * @param s      the byte array to hash
     * @param offset the starting offset
     * @param len    the length of bytes to hash
     * @param seed0  first seed
     * @param seed1  second seed
     * @return the 64-bit hash
     */
    public static long cityHash64WithSeeds(byte[] s, int offset, int len, long seed0, long seed1) {
        return hashLen16(cityHash64(s, offset, len) - seed0, seed1);
    }

    /**
     * Compute 64-bit CityHash using YSM modifications.
     *
     * @param s      the byte array to hash
     * @param offset the starting offset
     * @param len    the length of bytes to hash
     * @return the 64-bit hash
     */
    public static long cityHash64(byte[] s, int offset, int len) {
        if (len <= 32) {
            if (len <= 16) {
                return hashLen0to16(s, offset, len);
            } else {
                return hashLen17to32(s, offset, len);
            }
        } else if (len <= 64) {
            return hashLen33to64(s, offset, len);
        }

        long x = fetch64(s, offset + len - 40);
        long y = fetch64(s, offset + len - 16) + fetch64(s, offset + len - 56);
        long z = hashLen16(fetch64(s, offset + len - 48) + len, fetch64(s, offset + len - 24));
        var v = weakHashLen32WithSeeds(s, offset + len - 64, len, z);
        var w = weakHashLen32WithSeeds(s, offset + len - 32, y + K1, x);
        x = x * K1 + fetch64(s, offset);

        int remaining = (len - 1) & ~63;
        int curr = offset;
        do {
            x = rotate(x + y + v.first + fetch64(s, curr + 8), 37) * K1;
            y = rotate(y + v.second + fetch64(s, curr + 48), 42) * K1;
            x ^= w.second;
            y += v.first + fetch64(s, curr + 40);
            z = rotate(z + w.first, 33) * K1;
            v = weakHashLen32WithSeeds(s, curr, v.second * K1, x + w.first);
            w = weakHashLen32WithSeeds(s, curr + 32, z + w.second, y + fetch64(s, curr + 16));
            long tmp = z;
            z = x;
            x = tmp;
            curr += 64;
            remaining -= 64;
        } while (remaining != 0);

        return hashLen16(hashLen16(v.first, w.first) + shiftMix(y) * K1 + z,
                hashLen16(v.second, w.second) + x);
    }

    private static long fetch64(byte[] s, int offset) {
        return (s[offset] & 0xFFL)
                | ((s[offset + 1] & 0xFFL) << 8)
                | ((s[offset + 2] & 0xFFL) << 16)
                | ((s[offset + 3] & 0xFFL) << 24)
                | ((s[offset + 4] & 0xFFL) << 32)
                | ((s[offset + 5] & 0xFFL) << 40)
                | ((s[offset + 6] & 0xFFL) << 48)
                | ((s[offset + 7] & 0xFFL) << 56);
    }

    private static long fetch32(byte[] s, int offset) {
        return (s[offset] & 0xFFL)
                | ((s[offset + 1] & 0xFFL) << 8)
                | ((s[offset + 2] & 0xFFL) << 16)
                | ((s[offset + 3] & 0xFFL) << 24);
    }

    private static long rotate(long val, int shift) {
        return Long.rotateRight(val, shift);
    }

    private static long shiftMix(long val) {
        return val ^ (val >>> 47);
    }

    private static long hash128to64(long low, long high) {
        return KMUL * shiftMix(KMUL * (shiftMix((low ^ high) * KMUL) ^ low));
    }

    private static long hashLen16(long u, long v) {
        return hash128to64(u, v);
    }

    private static long hashLen16(long u, long v, long mul) {
        long a = (u ^ v) * mul;
        a ^= (a >>> 47);
        long b = (v ^ a) * mul;
        b ^= (b >>> 47);
        b *= mul;
        return b;
    }

    private static long hashLen0to16(byte[] s, int offset, int len) {
        if (len >= 8) {
            long mul = K2 + len * 2L;
            long a = fetch64(s, offset) + K2;
            long b = fetch64(s, offset + len - 8);
            long c = rotate(b, 37) * mul + a;
            long d = (rotate(a, 25) + b) * mul;
            return hashLen16(c, d, mul);
        }
        if (len >= 4) {
            long mul = K2 + len * 2L;
            long a = fetch32(s, offset);
            return hashLen16(len + (a << 3), fetch32(s, offset + len - 4), mul);
        }
        if (len > 0) {
            int a = s[offset] & 0xFF;
            int b = s[offset + (len >> 1)] & 0xFF;
            int c = s[offset + len - 1] & 0xFF;
            int y = a + (b << 8);
            int z = len + (c << 2);
            return shiftMix(y * K2 ^ z * K0) * K2;
        }
        return K2;
    }

    private static long hashLen17to32(byte[] s, int offset, int len) {
        long mul = K2 + len * 2L;
        long a = fetch64(s, offset) * K1;
        long b = fetch64(s, offset + 8);
        long c = fetch64(s, offset + len - 8) * mul;
        long d = fetch64(s, offset + len - 16) * K2;
        return hashLen16(rotate(a + b, 43) + rotate(c, 30) + d,
                a + rotate(b + K2, 18) + c, mul);
    }

    private static long hashLen33to64(byte[] s, int offset, int len) {
        long mul = K2 + len * 2L;
        long a = fetch64(s, offset) * K2;
        long b = fetch64(s, offset + 8);
        long c = fetch64(s, offset + len - 24);
        long d = fetch64(s, offset + len - 32);
        long e = fetch64(s, offset + 16) * K2;
        long f = fetch64(s, offset + 24) * 9L;
        long g = fetch64(s, offset + len - 8);
        long h = fetch64(s, offset + len - 16) * mul;
        long u = rotate(a + g, 43) + (rotate(b, 30) + c) * 9L;
        long v = ((a + g) ^ d) + f + 1L;
        long w = Long.reverseBytes((u + v) * mul) + h;
        long x = rotate(e + f, 42) + c;
        long y = (Long.reverseBytes((v + w) * mul) + g) * mul;
        long z = e + f + c;
        a = Long.reverseBytes((x + z) * mul + y) + b;
        b = shiftMix((z + a) * mul + d + h) * mul;
        return b + x;
    }

    private record LongPair(long first, long second) {
    }

    private static LongPair weakHashLen32WithSeeds(long w, long x, long y, long z, long a, long b) {
        a += w;
        b = rotate(b + a + z, 21);
        long c = a;
        a += x;
        a += y;
        b += rotate(a, 44);
        return new LongPair(a + z, b + c);
    }

    private static LongPair weakHashLen32WithSeeds(byte[] s, int offset, long a, long b) {
        return weakHashLen32WithSeeds(
                fetch64(s, offset),
                fetch64(s, offset + 8),
                fetch64(s, offset + 16),
                fetch64(s, offset + 24),
                a,
                b);
    }
}
