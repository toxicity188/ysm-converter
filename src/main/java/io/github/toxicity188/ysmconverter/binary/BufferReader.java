package io.github.toxicity188.ysmconverter.binary;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Binary buffer reader providing primitive, varint, float, string, and vector deserialization
 * matching YSM's {@code BufferReader}.
 *
 * <pre>{@code
 * BufferReader reader = new BufferReader(data);
 * long version = reader.readDword();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class BufferReader {

    private final byte @NotNull [] data;
    private int offset;

    /**
     * Constructs a {@link BufferReader} over the provided byte array starting at offset 0.
     *
     * @param data binary data buffer
     */
    public BufferReader(byte @NotNull [] data) {
        this(data, 0);
    }

    /**
     * Constructs a {@link BufferReader} over the provided byte array starting at the specified offset.
     *
     * @param data   binary data buffer
     * @param offset initial byte offset
     */
    public BufferReader(byte @NotNull [] data, int offset) {
        this.data = Objects.requireNonNull(data, "data must not be null");
        this.offset = offset;
    }

    /**
     * Current read offset in bytes.
     *
     * @return offset
     */
    @CheckReturnValue
    public int getOffset() {
        return offset;
    }

    /**
     * Sets the current read offset.
     *
     * @param offset new offset
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * Total length of the underlying data buffer.
     *
     * @return buffer length
     */
    @CheckReturnValue
    public int getSize() {
        return data.length;
    }

    /**
     * Returns true if the reader has reached or exceeded the end of the buffer.
     *
     * @return true if EOF
     */
    @CheckReturnValue
    public boolean isEOF() {
        return offset >= data.length;
    }

    /**
     * Peeks at the current byte without advancing the read position.
     *
     * @return unsigned byte value (0-255)
     * @throws IndexOutOfBoundsException if at or past end of buffer
     */
    @CheckReturnValue
    public int seeByte() {
        if (offset >= data.length) {
            throw new IndexOutOfBoundsException("Buffer EOF at offset " + offset);
        }
        return data[offset] & 0xFF;
    }

    /**
     * Reads a single unsigned byte and advances the offset by 1.
     *
     * @return unsigned byte value (0-255)
     * @throws IndexOutOfBoundsException if at or past end of buffer
     */
    public int readByte() {
        if (offset >= data.length) {
            throw new IndexOutOfBoundsException("Buffer EOF at offset " + offset);
        }
        return data[offset++] & 0xFF;
    }

    /**
     * Reads a 16-bit little-endian unsigned word and advances the offset by 2.
     *
     * @return unsigned 16-bit integer (0-65535)
     * @throws IndexOutOfBoundsException if buffer underflows
     */
    public int readWordLE() {
        if (offset + 1 >= data.length) {
            throw new IndexOutOfBoundsException("Buffer EOF at offset " + offset);
        }
        int result = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
        offset += 2;
        return result;
    }

    /**
     * Reads a variable-length unsigned integer (LEB128/Varint).
     *
     * @return 64-bit integer
     */
    public long readVarint() {
        long value = 0;
        int shift = 0;
        int b;
        do {
            b = readByte();
            value |= ((long) (b & 0x7F)) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return value;
    }

    /**
     * Reads a 32-bit IEEE 754 floating-point number in little-endian order.
     *
     * @return float value
     * @throws IndexOutOfBoundsException if buffer underflows
     */
    public float readFloat() {
        if (offset + 4 > data.length) {
            throw new IndexOutOfBoundsException("Buffer EOF at offset " + offset);
        }
        int bits = (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
        offset += 4;
        return Float.intBitsToFloat(bits);
    }

    /**
     * Reads a 3D vector consisting of three 32-bit floats (x, y, z).
     *
     * @return vector
     */
    public @NotNull Vector3D readVector3D() {
        float x = readFloat();
        float y = readFloat();
        float z = readFloat();
        return new Vector3D(x, y, z);
    }

    /**
     * Reads a 32-bit unsigned little-endian dword.
     *
     * @return unsigned 32-bit integer as a 64-bit long
     * @throws IndexOutOfBoundsException if buffer underflows
     */
    public long readDword() {
        if (offset + 4 > data.length) {
            throw new IndexOutOfBoundsException("Buffer EOF at offset " + offset);
        }
        long r = (data[offset] & 0xFFL)
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16)
                | ((data[offset + 3] & 0xFFL) << 24);
        offset += 4;
        return r;
    }

    /**
     * Reads a length-prefixed UTF-8 string (length is encoded as varint).
     *
     * @return decoded string
     */
    public @NotNull String readString() {
        int len = (int) readVarint();
        if (len == 0) {
            return "";
        }
        if (offset + len > data.length) {
            throw new IndexOutOfBoundsException("Buffer overflow reading string of length " + len + " at " + offset);
        }
        var s = new String(data, offset, len, StandardCharsets.UTF_8);
        offset += len;
        return s;
    }

    /**
     * Reads a length-prefixed byte sequence (length is encoded as varint).
     *
     * @return copied byte array
     */
    public byte @NotNull [] readByteSequence() {
        int len = (int) readVarint();
        if (len == 0) {
            return new byte[0];
        }
        return readBytesExactly(len);
    }

    /**
     * Reads exactly {@code len} bytes from the buffer.
     *
     * @param len number of bytes to read
     * @return copied byte array
     */
    public byte @NotNull [] readBytesExactly(int len) {
        if (offset + len > data.length) {
            throw new IndexOutOfBoundsException("Buffer EOF: requested " + len + " bytes at offset " + offset);
        }
        byte[] bytes = Arrays.copyOfRange(data, offset, offset + len);
        offset += len;
        return bytes;
    }
}
