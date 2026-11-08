package io.github.toxicity188.ysmconverter.model;

import io.github.toxicity188.ysmconverter.binary.Vector3D;
import io.github.toxicity188.ysmconverter.model.GeometryTypes.BlockbenchCube;
import io.github.toxicity188.ysmconverter.model.GeometryTypes.Face;
import io.github.toxicity188.ysmconverter.model.GeometryTypes.UvBox;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Reconstructs Bedrock-format cube geometry from raw polygon face vertex data.
 *
 * <pre>{@code
 * BlockbenchCube cube = GeometrySolver.restoreBlockbenchCube(faces, 0.0f, 512, 512);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class GeometrySolver {

    private GeometrySolver() {
    }

    private record FaceInfo(Vector3D nw, Vector3D tu, Vector3D tv, Face rawFace) {
    }

    private record UvDirs(Vector3D expU, Vector3D expV) {
    }

    private static float cleanVal(float v) {
        return Math.round(v * 10000.0f) / 10000.0f;
    }

    /**
     * Cleans floating point value by rounding to 5 decimal places or zeroing near-zero values.
     *
     * @param v float value
     * @return cleaned float
     */
    @CheckReturnValue
    public static float cleanNumber(float v) {
        if (Math.abs(v) < 1e-9f) {
            return 0.0f;
        }
        return Math.round(v * 100000.0f) / 100000.0f;
    }

    /**
     * Cleans a 3D vector by cleaning each component.
     *
     * @param v vector
     * @return cleaned vector
     */
    @CheckReturnValue
    public static @NotNull Vector3D cleanVector(@NotNull Vector3D v) {
        return new Vector3D(cleanNumber(v.x()), cleanNumber(v.y()), cleanNumber(v.z()));
    }

    private static UvDirs getExpectedUvDirs(Vector3D localNormal) {
        var n = localNormal.round();
        if (n.x() == -1) return new UvDirs(new Vector3D(0, 0, 1), new Vector3D(0, -1, 0));
        if (n.x() == 1) return new UvDirs(new Vector3D(0, 0, -1), new Vector3D(0, -1, 0));
        if (n.y() == 1) return new UvDirs(new Vector3D(-1, 0, 0), new Vector3D(0, 0, -1));
        if (n.y() == -1) return new UvDirs(new Vector3D(-1, 0, 0), new Vector3D(0, 0, 1));
        if (n.z() == -1) return new UvDirs(new Vector3D(-1, 0, 0), new Vector3D(0, -1, 0));
        if (n.z() == 1) return new UvDirs(new Vector3D(1, 0, 0), new Vector3D(0, -1, 0));
        return new UvDirs(Vector3D.ZERO, Vector3D.ZERO);
    }

    private static Vector3D matrixToEulerXyz(Matrix3x3 m) {
        float sy = (float) Math.sqrt(m.col0().x() * m.col0().x() + m.col0().y() * m.col0().y());
        boolean singular = sy < 1e-6f;
        float xRad, yRad, zRad;

        if (!singular) {
            xRad = (float) Math.atan2(m.col1().z(), m.col2().z());
            yRad = (float) Math.atan2(-m.col0().z(), sy);
            zRad = (float) Math.atan2(m.col0().y(), m.col0().x());
        } else {
            xRad = (float) Math.atan2(-m.col2().y(), m.col1().y());
            yRad = (float) Math.atan2(-m.col0().z(), sy);
            zRad = 0;
        }

        float radToDeg = (float) (180.0 / Math.PI);
        return new Vector3D(xRad * radToDeg, yRad * radToDeg, zRad * radToDeg);
    }

    /**
     * Reconstructs an oriented bounding box / cube definition from polygon faces.
     *
     * @param facesData       the faces composing the cube
     * @param originalInflate original inflation value
     * @param textureWidth    texture width in pixels
     * @param textureHeight   texture height in pixels
     * @return the reconstructed cube
     */
    @CheckReturnValue
    public static @NotNull BlockbenchCube restoreBlockbenchCube(
            @NotNull List<@NotNull Face> facesData,
            float originalInflate,
            int textureWidth,
            int textureHeight
    ) {
        var comp = Comparator.<Vector3D>comparingDouble(Vector3D::x)
                .thenComparingDouble(Vector3D::y)
                .thenComparingDouble(Vector3D::z);
        var uniquePts = new TreeSet<Vector3D>((a, b) -> {
            if (Math.abs(a.x() - b.x()) >= 1e-4f) return Float.compare(a.x(), b.x());
            if (Math.abs(a.y() - b.y()) >= 1e-4f) return Float.compare(a.y(), b.y());
            if (Math.abs(a.z() - b.z()) >= 1e-4f) return Float.compare(a.z(), b.z());
            return 0;
        });

        var candidateAxes = new ArrayList<Vector3D>();
        var faceInfos = new ArrayList<FaceInfo>();

        for (var f : facesData) {
            var verts = new Vector3D[4];
            for (int i = 0; i < 4; i++) {
                verts[i] = f.vertices()[i].vec().multiply(16.0f);
                var p4 = new Vector3D(
                        Math.round(verts[i].x() * 10000.0f) / 10000.0f,
                        Math.round(verts[i].y() * 10000.0f) / 10000.0f,
                        Math.round(verts[i].z() * 10000.0f) / 10000.0f
                );
                uniquePts.add(p4);
            }

            var nw = f.normal();
            var tu = Vector3D.ZERO;
            var tv = Vector3D.ZERO;

            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                var dx = verts[i].subtract(verts[j]);
                float du = f.vertices()[i].u() - f.vertices()[j].u();
                float dv = f.vertices()[i].v() - f.vertices()[j].v();
                float lenDx = dx.length();

                if (lenDx < 1e-5f) continue;

                if (Math.abs(du) > 1e-5f && Math.abs(dv) < 1e-5f) {
                    tu = dx.divide(lenDx).multiply(du > 0 ? 1.0f : -1.0f);
                }
                if (Math.abs(dv) > 1e-5f && Math.abs(du) < 1e-5f) {
                    tv = dx.divide(lenDx).multiply(dv > 0 ? 1.0f : -1.0f);
                }
            }

            faceInfos.add(new FaceInfo(nw, tu, tv, f));
            candidateAxes.add(nw);
            if (tu.length() > 0.5f) candidateAxes.add(tu);
            if (tv.length() > 0.5f) candidateAxes.add(tv);
        }

        var rawAxes = new ArrayList<Vector3D>();
        for (var vec : candidateAxes) {
            var vecNorm = vec.normalize();
            boolean axisExists = false;
            for (var a : rawAxes) {
                if (Math.abs(vecNorm.dot(a)) > 0.95f) {
                    axisExists = true;
                    break;
                }
            }
            if (!axisExists) rawAxes.add(vecNorm);
            if (rawAxes.size() == 3) break;
        }

        if (rawAxes.size() == 1) {
            var n = rawAxes.get(0);
            var temp = Math.abs(n.x()) < 0.9f ? new Vector3D(1, 0, 0) : new Vector3D(0, 1, 0);
            var u = n.cross(temp).normalize();
            rawAxes.add(u);
            rawAxes.add(n.cross(u).normalize());
        } else if (rawAxes.size() == 2) {
            var w = rawAxes.get(0).cross(rawAxes.get(1));
            if (w.length() > 1e-5f) rawAxes.add(w.normalize());
        } else if (rawAxes.isEmpty()) {
            rawAxes.add(new Vector3D(1, 0, 0));
            rawAxes.add(new Vector3D(0, 1, 0));
            rawAxes.add(new Vector3D(0, 0, 1));
        }

        float bestScore = Float.NEGATIVE_INFINITY;
        Matrix3x3 bestRotMatrix = Matrix3x3.IDENTITY;
        Vector3D bestEuler = Vector3D.ZERO;
        float minEulerSum = Float.POSITIVE_INFINITY;

        int[][] perms = {
                {0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
        };
        float[][] signs = {
                {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1},
                {-1, 1, 1}, {-1, 1, -1}, {-1, -1, 1}, {-1, -1, -1}
        };

        for (var p : perms) {
            for (var s : signs) {
                var mat = new Matrix3x3(
                        rawAxes.get(p[0]).multiply(s[0]),
                        rawAxes.get(p[1]).multiply(s[1]),
                        rawAxes.get(p[2]).multiply(s[2])
                );

                if (mat.det() < 0.9f) continue;

                var col0 = mat.col0().normalize();
                var projOn0 = col0.multiply(mat.col1().dot(col0));
                var col1 = mat.col1().subtract(projOn0).normalize();
                var col2 = col0.cross(col1).normalize();
                var rotMat = new Matrix3x3(col0, col1, col2);

                float score = 0;
                for (var info : faceInfos) {
                    var nLocal = rotMat.transposeMul(info.nw());
                    var tuLocal = rotMat.transposeMul(info.tu());
                    var tvLocal = rotMat.transposeMul(info.tv());

                    var exp = getExpectedUvDirs(nLocal);
                    score += Math.abs(tuLocal.dot(exp.expU()));
                    score += Math.abs(tvLocal.dot(exp.expV()));
                }

                var euler = matrixToEulerXyz(rotMat);
                float eulerSum = Math.abs(euler.x()) + Math.abs(euler.y()) + Math.abs(euler.z());

                if (score > bestScore + 1e-4f) {
                    bestScore = score;
                    bestRotMatrix = rotMat;
                    bestEuler = euler;
                    minEulerSum = eulerSum;
                } else if (Math.abs(score - bestScore) <= 1e-4f && eulerSum < minEulerSum) {
                    bestRotMatrix = rotMat;
                    bestEuler = euler;
                    minEulerSum = eulerSum;
                }
            }
        }

        var localMin = new float[]{1e9f, 1e9f, 1e9f};
        var localMax = new float[]{-1e9f, -1e9f, -1e9f};

        for (var pt : uniquePts) {
            var localPt = bestRotMatrix.transposeMul(pt);
            localMin[0] = Math.min(localMin[0], localPt.x());
            localMin[1] = Math.min(localMin[1], localPt.y());
            localMin[2] = Math.min(localMin[2], localPt.z());

            localMax[0] = Math.max(localMax[0], localPt.x());
            localMax[1] = Math.max(localMax[1], localPt.y());
            localMax[2] = Math.max(localMax[2], localPt.z());
        }

        var localCenter = new Vector3D(
                (localMin[0] + localMax[0]) / 2.0f,
                (localMin[1] + localMax[1]) / 2.0f,
                (localMin[2] + localMax[2]) / 2.0f
        );

        var dumpCenter = bestRotMatrix.col0().multiply(localCenter.x())
                .add(bestRotMatrix.col1().multiply(localCenter.y()))
                .add(bestRotMatrix.col2().multiply(localCenter.z()));

        var bbPivot = new Vector3D(-dumpCenter.x(), dumpCenter.y(), dumpCenter.z());

        float[] sizeSigns = {1.0f, 1.0f, 1.0f};
        Vector3D[] cols = {bestRotMatrix.col0(), bestRotMatrix.col1(), bestRotMatrix.col2()};

        for (int axIdx = 0; axIdx < 3; ++axIdx) {
            var localAx = cols[axIdx];
            for (var info : faceInfos) {
                var nLocal = bestRotMatrix.transposeMul(info.nw());
                float nLocalVal = (axIdx == 0) ? nLocal.x() : (axIdx == 1 ? nLocal.y() : nLocal.z());

                if (Math.abs(nLocalVal) > 0.5f) {
                    var faceCenter = Vector3D.ZERO;
                    for (int i = 0; i < 4; i++) {
                        faceCenter = faceCenter.add(info.rawFace().vertices()[i].vec().multiply(16.0f));
                    }
                    faceCenter = faceCenter.divide(4.0f);
                    var offset = faceCenter.subtract(dumpCenter);

                    float dotOffset = offset.dot(localAx);
                    if (Math.abs(dotOffset) > 1e-3f) {
                        if (dotOffset * nLocalVal < 0) {
                            sizeSigns[axIdx] = -1.0f;
                        }
                    }
                    break;
                }
            }
        }

        var bbRot = new Vector3D(-bestEuler.x(), -bestEuler.y(), bestEuler.z());

        float sxMax = 0, syMax = 0, szMax = 0;
        for (var pt : uniquePts) {
            var d = pt.subtract(dumpCenter);
            sxMax = Math.max(sxMax, Math.abs(d.dot(bestRotMatrix.col0())));
            syMax = Math.max(syMax, Math.abs(d.dot(bestRotMatrix.col1())));
            szMax = Math.max(szMax, Math.abs(d.dot(bestRotMatrix.col2())));
        }

        var bbSize = new Vector3D(
                (sxMax * 2 - 2 * originalInflate) * sizeSigns[0],
                (syMax * 2 - 2 * originalInflate) * sizeSigns[1],
                (szMax * 2 - 2 * originalInflate) * sizeSigns[2]
        );

        var bbOrigin = bbPivot.subtract(bbSize.divide(2.0f));
        var sizeSignsVec = new Vector3D(sizeSigns[0], sizeSigns[1], sizeSigns[2]);

        var uvMap = new LinkedHashMap<String, UvBox>();

        for (var info : faceInfos) {
            var nLocal = bestRotMatrix.transposeMul(info.nw());
            var tuLocal = bestRotMatrix.transposeMul(info.tu());
            var tvLocal = bestRotMatrix.transposeMul(info.tv());

            var rawExp = getExpectedUvDirs(nLocal);
            var expU = rawExp.expU().multiply(sizeSignsVec);
            var expV = rawExp.expV().multiply(sizeSignsVec);

            float uMin = 1e9f, vMin = 1e9f, uMax = -1e9f, vMax = -1e9f;
            for (int i = 0; i < 4; i++) {
                float u = info.rawFace().vertices()[i].u() * textureWidth;
                float v = info.rawFace().vertices()[i].v() * textureHeight;
                uMin = Math.min(uMin, u); uMax = Math.max(uMax, u);
                vMin = Math.min(vMin, v); vMax = Math.max(vMax, v);
            }

            float uStart = uMin;
            float vStart = vMin;
            float uSz = uMax - uMin;
            float vSz = vMax - vMin;

            if (tuLocal.dot(expU) < -0.2f) {
                uStart = uMax;
                uSz = -uSz;
            }
            if (tvLocal.dot(expV) < -0.2f) {
                vStart = vMax;
                vSz = -vSz;
            }

            var box = new UvBox(cleanVal(uStart), cleanVal(vStart), cleanVal(uSz), cleanVal(vSz));

            if (nLocal.dot(new Vector3D(1, 0, 0)) > 0.9f) uvMap.put("east", box);
            else if (nLocal.dot(new Vector3D(-1, 0, 0)) > 0.9f) uvMap.put("west", box);
            else if (nLocal.dot(new Vector3D(0, 1, 0)) > 0.9f) uvMap.put("up", box);
            else if (nLocal.dot(new Vector3D(0, -1, 0)) > 0.9f) uvMap.put("down", box);
            else if (nLocal.dot(new Vector3D(0, 0, 1)) > 0.9f) uvMap.put("south", box);
            else if (nLocal.dot(new Vector3D(0, 0, -1)) > 0.9f) uvMap.put("north", box);
        }

        return new BlockbenchCube(bbOrigin, bbSize, originalInflate, bbPivot, bbRot, uvMap);
    }
}
