package spacecolony.sim;

import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeterministicRngTest {

    @Test
    void sameInputs_produceSameStream() {
        Random a = DeterministicRng.forStep(42L, 100L, 6L);
        Random b = DeterministicRng.forStep(42L, 100L, 6L);
        for (int i = 0; i < 10; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
    }

    @Test
    void seedAndTick_areNotCommutative() {
        Random a = DeterministicRng.forStep(0L, 1L, 0L);
        Random b = DeterministicRng.forStep(1L, 0L, 0L);
        assertNotEquals(a.nextLong(), b.nextLong(),
            "(seed, tick) must not be a commutative pair");
    }

    @Test
    void swappedSeedAndTick_yieldDifferentStreams() {
        Random a = DeterministicRng.forStep(5L, 3L, 9L);
        Random b = DeterministicRng.forStep(3L, 5L, 9L);
        assertNotEquals(a.nextLong(), b.nextLong());
    }

    @Test
    void differentStepIds_yieldDifferentStreams() {
        Random a = DeterministicRng.forStep(42L, 100L, 1L);
        Random b = DeterministicRng.forStep(42L, 100L, 2L);
        assertNotEquals(a.nextLong(), b.nextLong());
    }

    @Test
    void zeroInputs_doNotCollapseToZero() {
        Random a = DeterministicRng.forStep(0L, 0L, 0L);
        // The RNG itself with seed=0 still produces a deterministic stream,
        // but consecutive nextLong values shouldn't all be zero.
        long sum = 0;
        for (int i = 0; i < 8; i++) sum |= a.nextLong();
        assertNotEquals(0L, sum);
    }
}
