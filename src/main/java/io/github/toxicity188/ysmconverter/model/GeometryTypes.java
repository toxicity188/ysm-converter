package io.github.toxicity188.ysmconverter.model;

import io.github.toxicity188.ysmconverter.binary.Vector3D;

import java.util.Map;

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
    public record Vertex(Vector3D vec, float u, float v) {
    }

    /**
     * A polygon face with a surface normal and 4 vertices.
     *
     * @param normal   face normal vector
     * @param vertices four vertices forming the quad
     * @since 1.0.0
     */
    public record Face(Vector3D normal, Vertex[] vertices) {
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
     * @param uv       map of face names (north, south, east, west, up, down) to UV boxes
     * @since 1.0.0
     */
    public record BlockbenchCube(
            Vector3D origin,
            Vector3D size,
            float inflate,
            Vector3D pivot,
            Vector3D rotation,
            Map<String, UvBox> uv
    ) {
    }
}
