package io.github.toxicity188.ysmconverter.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersionDetectorTest {

    @Test
    void testDetectV1() {
        byte[] v1Header = new byte[]{
                0x59, 0x53, 0x47, 0x50, // "YSGP"
                0x00, 0x00, 0x00, 0x01  // cryptoVer = 1
        };
        assertEquals(YsmVersion.V1, VersionDetector.detectVersion(v1Header));
    }

    @Test
    void testDetectV2() {
        byte[] v2Header = new byte[]{
                0x59, 0x53, 0x47, 0x50, // "YSGP"
                0x00, 0x00, 0x00, 0x02  // cryptoVer = 2
        };
        assertEquals(YsmVersion.V2, VersionDetector.detectVersion(v2Header));
    }

    @Test
    void testDetectV3() {
        byte[] v3Header = new byte[]{
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, // UTF-8 BOM
                0x59, 0x53, 0x47, 0x50,                 // "YSGP"
                0x00
        };
        assertEquals(YsmVersion.V3, VersionDetector.detectVersion(v3Header));
    }

    @Test
    void testExtractV3Format() {
        assertEquals(1, VersionDetector.extractV3Format("header <format> 1 extra"));
        assertEquals(4, VersionDetector.extractV3Format("<format> 4"));
        assertEquals(15, VersionDetector.extractV3Format("<format>15\nfoo"));
        assertEquals(26, VersionDetector.extractV3Format("YSGP<format>  26\0"));
        assertEquals(32, VersionDetector.extractV3Format("<format>32"));
    }

    @Test
    void testInvalidHeaderThrows() {
        assertThrows(IllegalArgumentException.class, () -> VersionDetector.detectVersion(new byte[5]));
        assertThrows(UnsupportedOperationException.class, () -> VersionDetector.detectVersion(new byte[]{
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
        }));
        assertThrows(IllegalArgumentException.class, () -> VersionDetector.extractV3Format("no format tag"));
    }
}
