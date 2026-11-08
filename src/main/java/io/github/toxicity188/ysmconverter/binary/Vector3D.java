package io.github.toxicity188.ysmconverter.binary;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable 3D vector representing coordinates, rotation, or scale.
 *
 * <pre>{@code
 * Vector3D v1 = new Vector3D(1.0f, 2.0f, 3.0f);
 * Vector3D v2 = v1.multiply(2.0f);
 * }</pre>
 *
 * @param x x component
 * @param y y component
 * @param z z component
 * @since 1.0.0
 */
public record Vector3D(float x, float y, float z) {

    /**
     * Zero vector constant (0, 0, 0).
     */
    public static final @NotNull Vector3D ZERO = new Vector3D(0.0f, 0.0f, 0.0f);

    /**
     * Adds another vector to this vector.
     *
     * @param o vector to add
     * @return sum vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D add(@NotNull Vector3D o) {
        return new Vector3D(x + o.x, y + o.y, z + o.z);
    }

    /**
     * Subtracts another vector from this vector.
     *
     * @param o vector to subtract
     * @return difference vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D subtract(@NotNull Vector3D o) {
        return new Vector3D(x - o.x, y - o.y, z - o.z);
    }

    /**
     * Multiplies this vector by a scalar.
     *
     * @param s scalar value
     * @return scaled vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D multiply(float s) {
        return new Vector3D(x * s, y * s, z * s);
    }

    /**
     * Component-wise multiplication with another vector.
     *
     * @param o vector to multiply
     * @return resulting vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D multiply(@NotNull Vector3D o) {
        return new Vector3D(x * o.x, y * o.y, z * o.z);
    }

    /**
     * Divides this vector by a scalar.
     *
     * @param s scalar value
     * @return divided vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D divide(float s) {
        return new Vector3D(x / s, y / s, z / s);
    }

    /**
     * Computes the dot product with another vector.
     *
     * @param o other vector
     * @return dot product
     */
    @Contract(pure = true)
    @CheckReturnValue
    public float dot(@NotNull Vector3D o) {
        return x * o.x + y * o.y + z * o.z;
    }

    /**
     * Computes the cross product with another vector.
     *
     * @param o other vector
     * @return cross product vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D cross(@NotNull Vector3D o) {
        return new Vector3D(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x
        );
    }

    /**
     * Computes the Euclidean length of this vector.
     *
     * @return vector length
     */
    @Contract(pure = true)
    @CheckReturnValue
    public float length() {
        return (float) Math.sqrt(dot(this));
    }

    /**
     * Returns a normalized unit vector with the same direction, or ZERO if length is near zero.
     *
     * @return normalized vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D normalize() {
        float l = length();
        return l > 1e-8f ? divide(l) : ZERO;
    }

    /**
     * Component-wise round to nearest integers.
     *
     * @return rounded vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D round() {
        return new Vector3D(Math.round(x), Math.round(y), Math.round(z));
    }
}
