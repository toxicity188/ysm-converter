package io.github.toxicity188.ysmconverter.parser;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ImageFormatTest {

    private static final byte[] PNG_HEADER = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };

    private static final byte[] WEBP_HEADER = new byte[]{
            'R', 'I', 'F', 'F', 0x20, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'L'
    };

    private static final byte[] RIFF_WAV_HEADER = new byte[]{
            'R', 'I', 'F', 'F', 0x20, 0x00, 0x00, 0x00, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '
    };

    private static final byte[] AVIF_HEADER = new byte[]{
            0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'a', 'v', 'i', 'f', 0x00, 0x00, 0x00, 0x00
    };

    private static final byte[] JPEG_HEADER = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'
    };

    private static final byte[] BMP_HEADER = new byte[]{
            'B', 'M', 0x36, 0x00, 0x0C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x36, 0x00, 0x00, 0x00
    };

    @Test
    void testFormatDetection() {
        assertEquals(ImageFormat.PNG, ImageFormat.detect(PNG_HEADER));
        assertEquals(ImageFormat.WEBP, ImageFormat.detect(WEBP_HEADER));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(RIFF_WAV_HEADER), "RIFF with WAVE must not be detected as WEBP");
        assertEquals(ImageFormat.AVIF, ImageFormat.detect(AVIF_HEADER));
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(JPEG_HEADER));
        assertEquals(ImageFormat.BMP, ImageFormat.detect(BMP_HEADER));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(new byte[]{1, 2, 3}));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(null));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.detect(new byte[0]));
    }

    @Test
    void testFromFormatCode() {
        assertEquals(ImageFormat.BMP, ImageFormat.fromFormatCode(1));
        assertEquals(ImageFormat.PNG, ImageFormat.fromFormatCode(2));
        assertEquals(ImageFormat.JPEG, ImageFormat.fromFormatCode(3));
        assertEquals(ImageFormat.WEBP, ImageFormat.fromFormatCode(4));
        assertEquals(ImageFormat.AVIF, ImageFormat.fromFormatCode(5));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromFormatCode(0));
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromFormatCode(99));
    }

    @Test
    void testResolveImageFilename() {
        // Correct extension replaces mislabeled .png
        assertEquals("arrow.webp", ImageFormat.resolveImageFilename("arrow.png", WEBP_HEADER, ".png"));
        assertEquals("arrow.webp", ImageFormat.resolveImageFilename("arrow", WEBP_HEADER, ".png"));
        assertEquals("avatar/OBJ.avif", ImageFormat.resolveImageFilename("avatar/OBJ.png", AVIF_HEADER, ".png"));
        assertEquals("main.png", ImageFormat.resolveImageFilename("main.png", PNG_HEADER, ".png"));
        assertEquals("main.png", ImageFormat.resolveImageFilename("main", PNG_HEADER, ".png"));

        // Fallback for unknown
        assertEquals("data.png", ImageFormat.resolveImageFilename("data", new byte[]{1, 2}, ".png"));
        assertEquals("data.custom", ImageFormat.resolveImageFilename("data.custom", new byte[]{1, 2}, ".png"));
    }

    @Test
    void testStripKnownImageExtension() {
        assertEquals("arrow", ImageFormat.stripKnownImageExtension("arrow.png"));
        assertEquals("arrow", ImageFormat.stripKnownImageExtension("arrow.webp"));
        assertEquals("arrow", ImageFormat.stripKnownImageExtension("arrow.avif"));
        assertEquals("arrow", ImageFormat.stripKnownImageExtension("arrow.jpg"));
        assertEquals("arrow", ImageFormat.stripKnownImageExtension("arrow.jpeg"));
        assertEquals("arrow", ImageFormat.stripKnownImageExtension("arrow.bmp"));
        assertEquals("arrow.json", ImageFormat.stripKnownImageExtension("arrow.json"));
    }
}
