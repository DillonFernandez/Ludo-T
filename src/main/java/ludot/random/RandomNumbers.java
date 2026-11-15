// Generates bounded random values, optionally using a seed for repeatable gameplay.
package ludot.random;

import java.util.Random;

public final class RandomNumbers implements RandomSource {
    private final Random random;

    public RandomNumbers() {
        random = new Random();
    }

    public RandomNumbers(long seed) {
        random = new Random(seed);
    }

    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
