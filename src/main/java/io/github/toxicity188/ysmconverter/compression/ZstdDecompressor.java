package io.github.toxicity188.ysmconverter.compression;

import com.github.luben.zstd.ZstdInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Decompresses YSM-compressed data using {@link YsmZstd} wash and standard Zstandard decompression.
 *
 * <pre>{@code
 * byte[] decompressed = ZstdDecompressor.decompress(ysmCompressedBytes);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class ZstdDecompressor {

    private ZstdDecompressor() {
    }

    /**
     * Decompresses YSM-compressed data.
     *
     * @param compressedData the YSM-compressed byte array
     * @return the decompressed uncompressed byte array
     * @throws IOException if decompression fails
     */
    public static byte[] decompress(byte[] compressedData) throws IOException {
        byte[] washed = YsmZstd.wash(compressedData);
        try (var in = new ZstdInputStream(new ByteArrayInputStream(washed));
             var out = new ByteArrayOutputStream()) {
            var buffer = new byte[16384];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
