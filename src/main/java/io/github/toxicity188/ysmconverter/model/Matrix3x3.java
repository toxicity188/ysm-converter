package io.github.toxicity188.ysmconverter.model;

import io.github.toxicity188.ysmconverter.binary.Vector3D;

/**
 * 3x3 column-major matrix used for geometry transformation and rotation calculations.
 *
 * @param col0 column 0 vector
 * @param col1 column 1 vector
 * @param col2 column 2 vector
 * @since 1.0.0
 */
public record Matrix3x3(Vector3D col0, Vector3D col1, Vector3D col2) {

    /**
     * Identity matrix constant.
     */
    public static final Matrix3x3 IDENTITY = new Matrix3x3(
            new Vector3D(1, 0, 0),
            new Vector3D(0, 1, 0),
            new Vector3D(0, 0, 1)
    );

    /**
     * Computes the determinant of this matrix.
     *
     * @return determinant
     */
    public float det() {
        return col0.dot(col1.cross(col2));
    }

    /**
     * Multiplies the transpose of this matrix by a vector (matrix.T * v).
     *
     * @param v vector to multiply
     * @return resulting vector
     */
    public Vector3D transposeMul(Vector3D v) {
        return new Vector3D(col0.dot(v), col1.dot(v), col2.dot(v));
    }

    /**
     * Multiplies this matrix by a vector (matrix * v).
     *
     * @param v vector to multiply
     * @return resulting vector
     */
    public Vector3D mul(Vector3D v) {
        return col0.multiply(v.x()).add(col1.multiply(v.y())).add(col2.multiply(v.z()));
    }
}
