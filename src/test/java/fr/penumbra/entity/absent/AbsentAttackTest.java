package fr.penumbra.entity.absent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AbsentAttackTest {
    @Test
    void clockIsIdleAtZeroAndStartsAtBeginningWithoutClientAge() {
        assertEquals(0, AbsentAttack.progress(0, .9f, 9));
        assertEquals(0, AbsentAttack.progress(1, 0, 9));
        assertEquals(.5f, AbsentAttack.progress(5, .5f, 9), 1E-6);
        assertEquals(1, AbsentAttack.progress(10, 1, 9));
    }
    @Test
    void malformedInterpolationCannotEscapeClockOrProduceNan() {
        assertEquals(0, AbsentAttack.progress(-1, 1, 9));
        assertEquals(0, AbsentAttack.progress(7, 1, 0));
        assertEquals(0, AbsentAttack.progress(1, Float.NaN, 9));
        assertEquals(1, AbsentAttack.progress(Integer.MAX_VALUE, 1, 9));
    }
}
