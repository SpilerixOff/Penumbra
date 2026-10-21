package fr.penumbra.client.jumpscare;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Sac d'indices mélangé : chaque valeur sort exactement une fois par cycle.
 * Le premier tirage d'un nouveau cycle ne répète jamais le dernier du cycle
 * précédent lorsque le sac contient au moins deux valeurs.
 *
 * <p>Cette classe ne dépend pas de Minecraft afin que la règle de sélection
 * des variantes reste testable par un test unitaire ordinaire.</p>
 */
final class ShuffledIndexBag {

    private final int[] order;
    private final RandomGenerator random;
    private int cursor;
    private int previous = -1;

    ShuffledIndexBag(int size, RandomGenerator random) {
        if (size <= 0) {
            throw new IllegalArgumentException("Le sac doit contenir au moins une valeur");
        }
        this.order = new int[size];
        this.random = Objects.requireNonNull(random, "random");
        this.cursor = size;
    }

    int next() {
        if (cursor >= order.length) {
            refill();
        }
        int value = order[cursor++];
        previous = value;
        return value;
    }

    private void refill() {
        for (int index = 0; index < order.length; index++) {
            order[index] = index;
        }
        for (int index = order.length - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            int swap = order[index];
            order[index] = order[other];
            order[other] = swap;
        }

        if (order.length > 1 && order[0] == previous) {
            int other = 1 + random.nextInt(order.length - 1);
            int swap = order[0];
            order[0] = order[other];
            order[other] = swap;
        }
        cursor = 0;
    }
}
