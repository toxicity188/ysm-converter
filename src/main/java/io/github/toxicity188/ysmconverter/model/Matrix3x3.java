package io.github.toxicity188.ysmconverter.model;

import io.github.toxicity188.ysmconverter.binary.Vector3D;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 3x3 column-major matrix used for geometry transformation and rotation calculations.
 *
 * @param col0 column 0 vector
 * @param col1 column 1 vector
 * @param col2 column 2 vector
 * @since 1.0.0
 */
public record Matrix3x3(@NotNull Vector3D col0, @NotNull Vector3D col1, @NotNull Vector3D col2) {

    /**
     * Compact constructor validating non-null column vectors.
     */
    public Matrix3x3 {
        Objects.requireNonNull(col0, "col0 must not be null");
        Objects.requireNonNull(col1, "col1 must not be null");
        Objects.requireNonNull(col2, "col2 must not be null");
    }

    /**
     * Identity matrix constant.
     */
    public static final @NotNull Matrix3x3 IDENTITY = new Matrix3x3(
            new Vector3D(1, 0, 0),
            new Vector3D(0, 1, 0),
            new Vector3D(0, 0, 1)
    );

    /**
     * Computes the determinant of this matrix.
     *
     * @return determinant
     */
    @Contract(pure = true)
    @CheckReturnValue
    public float det() {
        return col0.dot(col1.cross(col2));
    }

    /**
     * Multiplies the transpose of this matrix by a vector (matrix.T * v).
     *
     * @param v vector to multiply
     * @return resulting vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D transposeMul(@NotNull Vector3D v) {
        return new Vector3D(col0.dot(v), col1.dot(v), col2.dot(v));
    }

    /**
     * Multiplies this matrix by a vector (matrix * v).
     *
     * @param v vector to multiply
     * @return resulting vector
     */
    @Contract(pure = true)
    @CheckReturnValue
    public @NotNull Vector3D mul(@NotNull Vector3D v) {
        return col0.multiply(v.x()).add(col1.multiply(v.y())).add(col2.multiply(v.z()));
    }
}
