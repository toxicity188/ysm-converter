package io.github.toxicity188.ysmconverter.compression;

import java.util.Arrays;

/**
 * Handles YSM-specific Zstandard frame transformations (wash and obfuscate).
 *
 * <pre>{@code
 * byte[] standardZstd = YsmZstd.wash(ysmCompressedData);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class YsmZstd {

    private static final int ZSTD_MAGIC = 0xFD2FB528;
    private static final int STD_BT_RAW = 0;
    private static final int STD_BT_RLE = 1;
    private static final int STD_BT_COMPRESSED = 2;
    private static final int STD_BT_RESERVED = 3;

    private YsmZstd() {
    }

    private static int calculateFrameHeaderSize(int fhd) {
        int size = 1;
        boolean singleSegment = ((fhd >>> 5) & 1) == 1;

        int dictIdSize = 0;
        int dictIdBits = fhd & 3;
        if (dictIdBits == 1) dictIdSize = 1;
        else if (dictIdBits == 2) dictIdSize = 2;
        else if (dictIdBits == 3) dictIdSize = 4;

        int fcsSize = 0;
        int fcsBits = (fhd >>> 6) & 3;
        if (fcsBits == 0) fcsSize = singleSegment ? 1 : 0;
        else if (fcsBits == 1) fcsSize = 2;
        else if (fcsBits == 2) fcsSize = 4;
        else if (fcsBits == 3) fcsSize = 8;

        int windowDescSize = singleSegment ? 0 : 1;
        return size + windowDescSize + dictIdSize + fcsSize;
    }

    /**
     * Converts YSM-modified ZSTD compressed data into standard ZSTD format.
     *
     * @param compressedData the YSM-compressed byte array
     * @return standard ZSTD byte array ready for standard decompression
     * @throws IllegalArgumentException if the data is invalid or not recognized
     */
    public static byte[] wash(byte[] compressedData) {
        if (compressedData == null || compressedData.length < 5) {
            throw new IllegalArgumentException("Invalid data length for ZSTD wash");
        }

        var data = Arrays.copyOf(compressedData, compressedData.length);

        int magic = (data[0] & 0xFF)
                | ((data[1] & 0xFF) << 8)
                | ((data[2] & 0xFF) << 16)
                | ((data[3] & 0xFF) << 24);
        if (magic != ZSTD_MAGIC) {
            throw new IllegalArgumentException("Not a standard ZSTD Magic Number: 0x" + Integer.toHexString(magic));
        }

        int fhd = data[4] & 0xFF;
        data[4] = (byte) (fhd & 0xFB);

        int frameHeaderSize = calculateFrameHeaderSize(fhd);
        int offset = 4 + frameHeaderSize;

        while (offset + 3 <= data.length) {
            int b0 = data[offset] & 0xFF;
            int b1 = data[offset + 1] & 0xFF;
            int b2 = data[offset + 2] & 0xFF;

            int lastBlock = (b0 >>> 7) & 1;
            int blockTypeYSM = (b0 >>> 5) & 3;
            int rawSize = ((b0 & 0x1F) << 16) | b1 | (b2 << 8);
            int cSize = rawSize ^ 0xD4E9;

            int blockTypeStd = switch (blockTypeYSM) {
                case 0 -> STD_BT_COMPRESSED;
                case 1 -> STD_BT_RLE;
                case 2 -> STD_BT_RESERVED;
                case 3 -> STD_BT_RAW;
                default -> throw new IllegalStateException("Unknown block type: " + blockTypeYSM);
            };

            int stdHeader = lastBlock | (blockTypeStd << 1) | (cSize << 3);
            data[offset] = (byte) (stdHeader & 0xFF);
            data[offset + 1] = (byte) ((stdHeader >>> 8) & 0xFF);
            data[offset + 2] = (byte) ((stdHeader >>> 16) & 0xFF);

            int blockDataSize = (blockTypeStd == STD_BT_RLE) ? 1 : cSize;
            offset += 3 + blockDataSize;

            if (lastBlock == 1) {
                break;
            }
        }

        return data;
    }

    /**
     * Converts standard ZSTD compressed data into YSM-modified ZSTD format.
     *
     * @param standardData standard ZSTD compressed byte array
     * @return YSM-modified ZSTD byte array
     * @throws IllegalArgumentException if the data is invalid
     */
    public static byte[] obfuscate(byte[] standardData) {
        if (standardData == null || standardData.length < 5) {
            throw new IllegalArgumentException("Invalid data length for ZSTD obfuscate");
        }

        var data = Arrays.copyOf(standardData, standardData.length);

        int magic = (data[0] & 0xFF)
                | ((data[1] & 0xFF) << 8)
                | ((data[2] & 0xFF) << 16)
                | ((data[3] & 0xFF) << 24);
        if (magic != ZSTD_MAGIC) {
            throw new IllegalArgumentException("Not a standard ZSTD Magic Number");
        }

        int fhd = data[4] & 0xFF;
        int frameHeaderSize = calculateFrameHeaderSize(fhd);
        int offset = 4 + frameHeaderSize;

        while (offset + 3 <= data.length) {
            int b0 = data[offset] & 0xFF;
            int b1 = data[offset + 1] & 0xFF;
            int b2 = data[offset + 2] & 0xFF;
            int cBlockHeader = b0 | (b1 << 8) | (b2 << 16);

            int lastBlock = cBlockHeader & 1;
            int blockTypeStd = (cBlockHeader >>> 1) & 3;
            int cSize = cBlockHeader >>> 3;

            int blockDataSize = (blockTypeStd == STD_BT_RLE) ? 1 : cSize;

            int blockTypeYSM = switch (blockTypeStd) {
                case STD_BT_RAW -> 3;
                case STD_BT_RLE -> 1;
                case STD_BT_COMPRESSED -> 0;
                case STD_BT_RESERVED -> 2;
                default -> throw new IllegalStateException("Unknown block type: " + blockTypeStd);
            };

            int rawSize = cSize ^ 0xD4E9;
            int ysmB0 = (lastBlock << 7) | (blockTypeYSM << 5) | ((rawSize >>> 16) & 0x1F);
            int ysmB1 = rawSize & 0xFF;
            int ysmB2 = (rawSize >>> 8) & 0xFF;

            data[offset] = (byte) ysmB0;
            data[offset + 1] = (byte) ysmB1;
            data[offset + 2] = (byte) ysmB2;

            offset += 3 + blockDataSize;

            if (lastBlock == 1) {
                break;
            }
        }

        return data;
    }
}
