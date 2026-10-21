package fr.penumbra.client.jumpscare;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShuffledIndexBagTest {

    @Test
    void eachNineVariantCycleContainsEveryVariantExactlyOnce() {
        ShuffledIndexBag bag = new ShuffledIndexBag(9, new Random(6751L));

        for (int cycle = 0; cycle < 40; cycle++) {
            Set<Integer> values = new HashSet<>();
            for (int draw = 0; draw < 9; draw++) {
                values.add(bag.next());
            }
            assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8), values);
        }
    }

    @Test
    void nineVariantBagNeverRepeatsAcrossCycleBoundaries() {
        ShuffledIndexBag bag = new ShuffledIndexBag(9, new Random(927L));
        int previous = -1;

        for (int draw = 0; draw < 300; draw++) {
            int current = bag.next();
            assertNotEquals(previous, current);
            previous = current;
        }
    }

    @Test
    void refusesAnEmptyBag() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShuffledIndexBag(0, new Random(1L)));
    }
}
