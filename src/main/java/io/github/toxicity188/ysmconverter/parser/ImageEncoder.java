package io.github.toxicity188.ysmconverter.parser;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Utility for converting raw RGBA pixel arrays into standard PNG format.
 *
 * <pre>{@code
 * byte[] png = ImageEncoder.encodeRgbaToPng(rgbaBytes, width, height);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ImageEncoder {

    private ImageEncoder() {
    }

    /**
     * Encodes a raw RGBA byte buffer into PNG format.
     *
     * @param rgbaData raw RGBA byte array of length width * height * 4
     * @param width    image width
     * @param height   image height
     * @return PNG encoded byte array
     * @throws IOException if encoding fails or dimensions mismatch
     */
    @CheckReturnValue
    public static byte @NotNull [] encodeRgbaToPng(byte @NotNull [] rgbaData, int width, int height) throws IOException {
        if (rgbaData == null || rgbaData.length != width * height * 4) {
            throw new IllegalArgumentException("Invalid RGBA buffer length: expected " + (width * height * 4)
                    + " bytes, got " + (rgbaData == null ? 0 : rgbaData.length));
        }

        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) {
            int r = rgbaData[i * 4] & 0xFF;
            int g = rgbaData[i * 4 + 1] & 0xFF;
            int b = rgbaData[i * 4 + 2] & 0xFF;
            int a = rgbaData[i * 4 + 3] & 0xFF;
            argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        image.setRGB(0, 0, width, height, argb, 0, width);

        var baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }
}
