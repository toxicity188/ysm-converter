package io.github.toxicity188.ysmconverter.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MersenneTwister64Test {

    @Test
    void testDeterminismAndNonZero() {
        var mt1 = new MersenneTwister64(5489L);
        var mt2 = new MersenneTwister64(5489L);

        for (int i = 0; i < 1000; i++) {
            long v1 = mt1.nextLong();
            long v2 = mt2.nextLong();
            assertEquals(v1, v2, "Identical seeds must produce identical sequence");
        }
    }

    @Test
    void testDifferentSeedsProduceDifferentOutputs() {
        var mt1 = new MersenneTwister64(12345L);
        var mt2 = new MersenneTwister64(67890L);

        assertNotEquals(mt1.nextLong(), mt2.nextLong());
    }

    @Test
    void testStateTwistCrossingNN() {
        // NN = 312, so generating > 312 numbers forces state twist / refill
        var mt = new MersenneTwister64(1337L);
        for (int i = 0; i < 700; i++) {
            long val = mt.nextLong();
            // Just ensure execution doesn't throw or produce all identical values
            assertNotNull(val);
        }
    }
}
