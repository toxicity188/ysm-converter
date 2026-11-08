package io.github.toxicity188.ysmconverter.model;

import io.github.toxicity188.ysmconverter.binary.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Map;
import java.util.Objects;

/**
 * Common geometry model types used during YSM model parsing and geometry reconstruction.
 *
 * @since 1.0.0
 */
public final class GeometryTypes {

    private GeometryTypes() {
    }

    /**
     * A polygon vertex with 3D coordinate and UV coordinates.
     *
     * @param vec 3D position vector
     * @param u   U texture coordinate (0.0 to 1.0)
     * @param v   V texture coordinate (0.0 to 1.0)
     * @since 1.0.0
     */
    public record Vertex(@NotNull Vector3D vec, float u, float v) {

        /**
         * Validates non-null position vector.
         */
        public Vertex {
            Objects.requireNonNull(vec, "vec must not be null");
        }
    }

    /**
     * A polygon face with a surface normal and 4 vertices.
     *
     * @param normal   face normal vector
     * @param vertices four vertices forming the quad
     * @since 1.0.0
     */
    public record Face(@NotNull Vector3D normal, @NotNull Vertex @NotNull [] vertices) {

        /**
         * Validates non-null normal and vertices.
         */
        public Face {
            Objects.requireNonNull(normal, "normal must not be null");
            Objects.requireNonNull(vertices, "vertices must not be null");
        }
    }

    /**
     * UV rectangular mapping on a cube face.
     *
     * @param u     U coordinate in pixels
     * @param v     V coordinate in pixels
     * @param uSize width in pixels (can be negative for mirrored UV)
     * @param vSize height in pixels (can be negative for mirrored UV)
     * @since 1.0.0
     */
    public record UvBox(float u, float v, float uSize, float vSize) {
    }

    /**
     * Reconstructed cube definition for Bedrock geometry format.
     *
     * @param origin   cube origin point
     * @param size     cube dimension [width, height, depth]
     * @param inflate  inflation amount
     * @param pivot    cube rotation pivot point
     * @param rotation rotation angles in degrees (Euler XYZ)
     * @param uv       unmodifiable map of face names (north, south, east, west, up, down) to UV boxes
     * @since 1.0.0
     */
    public record BlockbenchCube(
            @NotNull Vector3D origin,
            @NotNull Vector3D size,
            float inflate,
            @NotNull Vector3D pivot,
            @NotNull Vector3D rotation,
            @NotNull @Unmodifiable Map<@NotNull String, @NotNull UvBox> uv
    ) {

        /**
         * Compact constructor ensuring non-null components and unmodifiable UV map snapshot.
         */
        public BlockbenchCube {
            Objects.requireNonNull(origin, "origin must not be null");
            Objects.requireNonNull(size, "size must not be null");
            Objects.requireNonNull(pivot, "pivot must not be null");
            Objects.requireNonNull(rotation, "rotation must not be null");
            uv = Map.copyOf(uv);
        }
    }
}
