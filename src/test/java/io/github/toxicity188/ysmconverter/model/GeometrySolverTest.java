package io.github.toxicity188.ysmconverter.model;

import io.github.toxicity188.ysmconverter.binary.Vector3D;
import io.github.toxicity188.ysmconverter.model.GeometryTypes.Face;
import io.github.toxicity188.ysmconverter.model.GeometryTypes.Vertex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeometrySolverTest {

    @Test
    void testCleanNumber() {
        assertEquals(0.0f, GeometrySolver.cleanNumber(0.00000001f));
        assertEquals(1.23457f, GeometrySolver.cleanNumber(1.2345678f));
    }

    @Test
    void testRestoreBlockbenchCubeFromAxisAlignedFaces() {
        // Create 8 vertices of a standard unit cube [0, 0, 0] to [1, 1, 1] (in meters)
        // 6 faces: north, south, west, east, up, down
        var v000 = new Vertex(new Vector3D(0, 0, 0), 0.0f, 0.0f);
        var v100 = new Vertex(new Vector3D(1, 0, 0), 1.0f, 0.0f);
        var v110 = new Vertex(new Vector3D(1, 1, 0), 1.0f, 1.0f);
        var v010 = new Vertex(new Vector3D(0, 1, 0), 0.0f, 1.0f);
        var v001 = new Vertex(new Vector3D(0, 0, 1), 0.0f, 0.0f);
        var v101 = new Vertex(new Vector3D(1, 0, 1), 1.0f, 0.0f);
        var v111 = new Vertex(new Vector3D(1, 1, 1), 1.0f, 1.0f);
        var v011 = new Vertex(new Vector3D(0, 1, 1), 0.0f, 1.0f);

        // North face (z = 0) normal: (0, 0, -1)
        var north = new Face(new Vector3D(0, 0, -1), new Vertex[]{v000, v100, v110, v010});
        // South face (z = 1) normal: (0, 0, 1)
        var south = new Face(new Vector3D(0, 0, 1), new Vertex[]{v101, v001, v011, v111});
        // West face (x = 0) normal: (-1, 0, 0)
        var west = new Face(new Vector3D(-1, 0, 0), new Vertex[]{v001, v000, v010, v011});
        // East face (x = 1) normal: (1, 0, 0)
        var east = new Face(new Vector3D(1, 0, 0), new Vertex[]{v100, v101, v111, v110});
        // Up face (y = 1) normal: (0, 1, 0)
        var up = new Face(new Vector3D(0, 1, 0), new Vertex[]{v010, v110, v111, v011});
        // Down face (y = 0) normal: (0, -1, 0)
        var down = new Face(new Vector3D(0, -1, 0), new Vertex[]{v001, v101, v100, v000});

        var faces = List.of(north, south, west, east, up, down);
        var cube = GeometrySolver.restoreBlockbenchCube(faces, 0.0f, 64, 64);

        assertNotNull(cube);
        // Cube size in Blockbench pixels (1 meter = 16 pixels)
        assertEquals(16.0f, cube.size().x(), 0.01f);
        assertEquals(16.0f, cube.size().y(), 0.01f);
        assertEquals(16.0f, cube.size().z(), 0.01f);
    }
}
