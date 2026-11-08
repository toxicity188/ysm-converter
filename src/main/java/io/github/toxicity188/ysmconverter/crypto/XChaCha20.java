package io.github.toxicity188.ysmconverter.crypto;

/**
 * Pure Java implementation of XChaCha20 cipher with YSM dynamic rounds and state updates.
 *
 * @since 1.0.0
 */
public final class XChaCha20 {

    private final int[] input = new int[16];
    private int rounds = 20;

    /**
     * Constructs an uninitialized {@link XChaCha20} cipher.
     */
    public XChaCha20() {
    }

    /**
     * Gets the current number of rounds.
     *
     * @return rounds count
     */
    public int getRounds() {
        return rounds;
    }

    /**
     * Sets the number of rounds for subsequent operations.
     *
     * @param rounds rounds count (typically 10, 20, or 30)
     */
    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    /**
     * Sets up the XChaCha20 cipher with a 256-bit key and 192-bit IV.
     *
     * @param key 32-byte key
     * @param iv  24-byte IV
     */
    public void keySetup(byte[] key, byte[] iv) {
        var k2 = new byte[32];
        hChaCha20(k2, iv, key, rounds);

        input[0] = 0x61707865;
        input[1] = 0x3320646e;
        input[2] = 0x79622d32;
        input[3] = 0x6b206574;
        input[4] = u8To32Little(k2, 0);
        input[5] = u8To32Little(k2, 4);
        input[6] = u8To32Little(k2, 8);
        input[7] = u8To32Little(k2, 12);
        input[8] = u8To32Little(k2, 16);
        input[9] = u8To32Little(k2, 20);
        input[10] = u8To32Little(k2, 24);
        input[11] = u8To32Little(k2, 28);
        input[12] = 0;
        input[13] = 0;
        input[14] = u8To32Little(iv, 16);
        input[15] = u8To32Little(iv, 20);
    }

    /**
     * Encrypts or decrypts bytes in-place or into a destination buffer.
     *
     * @param in        input byte array
     * @param inOffset  input offset
     * @param out       output byte array
     * @param outOffset output offset
     * @param bytes     number of bytes to process
     */
    public void cryptBytes(byte[] in, int inOffset, byte[] out, int outOffset, int bytes) {
        if (bytes <= 0) {
            return;
        }

        int j0 = input[0], j1 = input[1], j2 = input[2], j3 = input[3];
        int j4 = input[4], j5 = input[5], j6 = input[6], j7 = input[7];
        int j8 = input[8], j9 = input[9], j10 = input[10], j11 = input[11];
        int j12 = input[12], j13 = input[13], j14 = input[14], j15 = input[15];

        var tmp = new byte[64];
        var outBuf = new byte[64];

        int currIn = inOffset;
        int currOut = outOffset;
        int remaining = bytes;

        while (true) {
            int x0 = j0, x1 = j1, x2 = j2, x3 = j3;
            int x4 = j4, x5 = j5, x6 = j6, x7 = j7;
            int x8 = j8, x9 = j9, x10 = j10, x11 = j11;
            int x12 = j12, x13 = j13, x14 = j14, x15 = j15;

            for (int i = rounds; i > 0; i -= 2) {
                // Column round
                x0 += x4; x12 = Integer.rotateLeft(x12 ^ x0, 16);
                x8 += x12; x4 = Integer.rotateLeft(x4 ^ x8, 12);
                x0 += x4; x12 = Integer.rotateLeft(x12 ^ x0, 8);
                x8 += x12; x4 = Integer.rotateLeft(x4 ^ x8, 7);

                x1 += x5; x13 = Integer.rotateLeft(x13 ^ x1, 16);
                x9 += x13; x5 = Integer.rotateLeft(x5 ^ x9, 12);
                x1 += x5; x13 = Integer.rotateLeft(x13 ^ x1, 8);
                x9 += x13; x5 = Integer.rotateLeft(x5 ^ x9, 7);

                x2 += x6; x14 = Integer.rotateLeft(x14 ^ x2, 16);
                x10 += x14; x6 = Integer.rotateLeft(x6 ^ x10, 12);
                x2 += x6; x14 = Integer.rotateLeft(x14 ^ x2, 8);
                x10 += x14; x6 = Integer.rotateLeft(x6 ^ x10, 7);

                x3 += x7; x15 = Integer.rotateLeft(x15 ^ x3, 16);
                x11 += x15; x7 = Integer.rotateLeft(x7 ^ x11, 12);
                x3 += x7; x15 = Integer.rotateLeft(x15 ^ x3, 8);
                x11 += x15; x7 = Integer.rotateLeft(x7 ^ x11, 7);

                // Diagonal round
                x0 += x5; x15 = Integer.rotateLeft(x15 ^ x0, 16);
                x10 += x15; x5 = Integer.rotateLeft(x5 ^ x10, 12);
                x0 += x5; x15 = Integer.rotateLeft(x15 ^ x0, 8);
                x10 += x15; x5 = Integer.rotateLeft(x5 ^ x10, 7);

                x1 += x6; x12 = Integer.rotateLeft(x12 ^ x1, 16);
                x11 += x12; x6 = Integer.rotateLeft(x6 ^ x11, 12);
                x1 += x6; x12 = Integer.rotateLeft(x12 ^ x1, 8);
                x11 += x12; x6 = Integer.rotateLeft(x6 ^ x11, 7);

                x2 += x7; x13 = Integer.rotateLeft(x13 ^ x2, 16);
                x8 += x13; x7 = Integer.rotateLeft(x7 ^ x8, 12);
                x2 += x7; x13 = Integer.rotateLeft(x13 ^ x2, 8);
                x8 += x13; x7 = Integer.rotateLeft(x7 ^ x8, 7);

                x3 += x4; x14 = Integer.rotateLeft(x14 ^ x3, 16);
                x9 += x14; x4 = Integer.rotateLeft(x4 ^ x9, 12);
                x3 += x4; x14 = Integer.rotateLeft(x14 ^ x3, 8);
                x9 += x14; x4 = Integer.rotateLeft(x4 ^ x9, 7);
            }

            x0 += j0; x1 += j1; x2 += j2; x3 += j3;
            x4 += j4; x5 += j5; x6 += j6; x7 += j7;
            x8 += j8; x9 += j9; x10 += j10; x11 += j11;
            x12 += j12; x13 += j13; x14 += j14; x15 += j15;

            u32To8Little(outBuf, 0, x0);
            u32To8Little(outBuf, 4, x1);
            u32To8Little(outBuf, 8, x2);
            u32To8Little(outBuf, 12, x3);
            u32To8Little(outBuf, 16, x4);
            u32To8Little(outBuf, 20, x5);
            u32To8Little(outBuf, 24, x6);
            u32To8Little(outBuf, 28, x7);
            u32To8Little(outBuf, 32, x8);
            u32To8Little(outBuf, 36, x9);
            u32To8Little(outBuf, 40, x10);
            u32To8Little(outBuf, 44, x11);
            u32To8Little(outBuf, 48, x12);
            u32To8Little(outBuf, 52, x13);
            u32To8Little(outBuf, 56, x14);
            u32To8Little(outBuf, 60, x15);

            j12++;
            if (j12 == 0) {
                j13++;
            }

            if (remaining < 64) {
                for (int i = 0; i < remaining; i++) {
                    out[currOut + i] = (byte) (outBuf[i] ^ in[currIn + i]);
                }
                input[12] = j12;
                input[13] = j13;
                return;
            } else {
                for (int i = 0; i < 64; i++) {
                    out[currOut + i] = (byte) (outBuf[i] ^ in[currIn + i]);
                }
                currIn += 64;
                currOut += 64;
                remaining -= 64;
                if (remaining == 0) {
                    input[12] = j12;
                    input[13] = j13;
                    return;
                }
            }
        }
    }

    /**
     * Updates cipher state and rounds based on CityHash value, matching YSM's {@code xchacha_update_state}.
     *
     * @param hashVal the 64-bit CityHash value
     * @return next chunk size in bytes
     */
    public int updateState(long hashVal) {
        rounds = 10 * (int) Long.remainderUnsigned(hashVal, 3) + 10;

        int lo = (int) (hashVal & 0xFFFFFFFFL);
        int hi = (int) ((hashVal >>> 32) & 0xFFFFFFFFL);

        for (int i = 4; i < 16; i++) {
            if (i % 2 == 0) {
                input[i] ^= lo;
            } else {
                input[i] ^= hi;
            }
        }

        return (int) (((hashVal & 0x3FL) | 0x40L) << 6);
    }

    private static void hChaCha20(byte[] out, byte[] in, byte[] k, int rounds) {
        int x0 = 0x61707865;
        int x1 = 0x3320646e;
        int x2 = 0x79622d32;
        int x3 = 0x6b206574;

        int x4 = u8To32Little(k, 0);
        int x5 = u8To32Little(k, 4);
        int x6 = u8To32Little(k, 8);
        int x7 = u8To32Little(k, 12);
        int x8 = u8To32Little(k, 16);
        int x9 = u8To32Little(k, 20);
        int x10 = u8To32Little(k, 24);
        int x11 = u8To32Little(k, 28);
        int x12 = u8To32Little(in, 0);
        int x13 = u8To32Little(in, 4);
        int x14 = u8To32Little(in, 8);
        int x15 = u8To32Little(in, 12);

        for (int i = 0; i < rounds; i += 2) {
            x0 += x4; x12 = Integer.rotateLeft(x12 ^ x0, 16);
            x8 += x12; x4 = Integer.rotateLeft(x4 ^ x8, 12);
            x0 += x4; x12 = Integer.rotateLeft(x12 ^ x0, 8);
            x8 += x12; x4 = Integer.rotateLeft(x4 ^ x8, 7);

            x1 += x5; x13 = Integer.rotateLeft(x13 ^ x1, 16);
            x9 += x13; x5 = Integer.rotateLeft(x5 ^ x9, 12);
            x1 += x5; x13 = Integer.rotateLeft(x13 ^ x1, 8);
            x9 += x13; x5 = Integer.rotateLeft(x5 ^ x9, 7);

            x2 += x6; x14 = Integer.rotateLeft(x14 ^ x2, 16);
            x10 += x14; x6 = Integer.rotateLeft(x6 ^ x10, 12);
            x2 += x6; x14 = Integer.rotateLeft(x14 ^ x2, 8);
            x10 += x14; x6 = Integer.rotateLeft(x6 ^ x10, 7);

            x3 += x7; x15 = Integer.rotateLeft(x15 ^ x3, 16);
            x11 += x15; x7 = Integer.rotateLeft(x7 ^ x11, 12);
            x3 += x7; x15 = Integer.rotateLeft(x15 ^ x3, 8);
            x11 += x15; x7 = Integer.rotateLeft(x7 ^ x11, 7);

            x0 += x5; x15 = Integer.rotateLeft(x15 ^ x0, 16);
            x10 += x15; x5 = Integer.rotateLeft(x5 ^ x10, 12);
            x0 += x5; x15 = Integer.rotateLeft(x15 ^ x0, 8);
            x10 += x15; x5 = Integer.rotateLeft(x5 ^ x10, 7);

            x1 += x6; x12 = Integer.rotateLeft(x12 ^ x1, 16);
            x11 += x12; x6 = Integer.rotateLeft(x6 ^ x11, 12);
            x1 += x6; x12 = Integer.rotateLeft(x12 ^ x1, 8);
            x11 += x12; x6 = Integer.rotateLeft(x6 ^ x11, 7);

            x2 += x7; x13 = Integer.rotateLeft(x13 ^ x2, 16);
            x8 += x13; x7 = Integer.rotateLeft(x7 ^ x8, 12);
            x2 += x7; x13 = Integer.rotateLeft(x13 ^ x2, 8);
            x8 += x13; x7 = Integer.rotateLeft(x7 ^ x8, 7);

            x3 += x4; x14 = Integer.rotateLeft(x14 ^ x3, 16);
            x9 += x14; x4 = Integer.rotateLeft(x4 ^ x9, 12);
            x3 += x4; x14 = Integer.rotateLeft(x14 ^ x3, 8);
            x9 += x14; x4 = Integer.rotateLeft(x4 ^ x9, 7);
        }

        u32To8Little(out, 0, x0);
        u32To8Little(out, 4, x1);
        u32To8Little(out, 8, x2);
        u32To8Little(out, 12, x3);
        u32To8Little(out, 16, x12);
        u32To8Little(out, 20, x13);
        u32To8Little(out, 24, x14);
        u32To8Little(out, 28, x15);
    }

    private static int u8To32Little(byte[] p, int offset) {
        return (p[offset] & 0xFF)
                | ((p[offset + 1] & 0xFF) << 8)
                | ((p[offset + 2] & 0xFF) << 16)
                | ((p[offset + 3] & 0xFF) << 24);
    }

    private static void u32To8Little(byte[] p, int offset, int v) {
        p[offset] = (byte) (v & 0xFF);
        p[offset + 1] = (byte) ((v >>> 8) & 0xFF);
        p[offset + 2] = (byte) ((v >>> 16) & 0xFF);
        p[offset + 3] = (byte) ((v >>> 24) & 0xFF);
    }
}
