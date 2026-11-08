package io.github.toxicity188.ysmconverter.compression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YsmZstdTest {

    private static final int ZSTD_MAGIC = 0xFD2FB528;

    @Test
    void testWashClearsChecksumBit() {
        // Construct minimal mock Zstandard single-segment frame header:
        // Magic: 0xFD2FB528 (little-endian: 0x28, 0xB5, 0x2F, 0xFD)
        // FHD: bit 5 = 1 (single segment), bit 2 = 1 (checksum flag set) -> 0x24
        // WindowDesc = none (single segment)
        // FCS: fcsBits=0 -> 1 byte frame content size (e.g. 0x0A)
        // Block header (3 bytes): lastBlock=1 (bit 0), type=raw (bit 1-2 = 0), size=5 (bit 3+ = 5 << 3 = 40)
        // Block data: 5 bytes
        byte[] rawZstd = new byte[]{
                0x28, (byte) 0xB5, 0x2F, (byte) 0xFD,
                0x24, // FHD with checksum bit 2 set
                0x05, // FCS = 5
                0x29, 0x00, 0x00, // std block header: lastBlock=1, raw, size=5
                0x01, 0x02, 0x03, 0x04, 0x05 // 5 bytes
        };

        // Obfuscate standard ZSTD -> YSM ZSTD
        byte[] obfuscated = YsmZstd.obfuscate(rawZstd);
        assertNotNull(obfuscated);

        // Check that YSM ZSTD header block is obfuscated (not matching original std block header)
        assertNotEquals(rawZstd[6], obfuscated[6]);

        // Wash YSM ZSTD -> Standard ZSTD
        byte[] washed = YsmZstd.wash(obfuscated);
        assertNotNull(washed);

        // Checksum bit (bit 2 of FHD) should be cleared
        assertEquals(0x20, washed[4] & 0xFF, "FHD checksum flag should be cleared");

        // Standard block header should be recovered
        assertEquals(rawZstd[6] & 0x07, washed[6] & 0x07, "Block type and last block flag recovered");
    }

    @Test
    void testInvalidZstdMagicThrows() {
        byte[] invalidMagic = new byte[]{0x00, 0x00, 0x00, 0x00, 0x20, 0x00, 0x00};
        assertThrows(IllegalArgumentException.class, () -> YsmZstd.wash(invalidMagic));
        assertThrows(IllegalArgumentException.class, () -> YsmZstd.obfuscate(invalidMagic));
    }
}
