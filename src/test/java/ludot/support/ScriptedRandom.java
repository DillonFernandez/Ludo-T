// Supplies queued random values and counts calls, returning zero after the queue is exhausted.
package ludot.support;

import java.util.ArrayDeque;
import java.util.Deque;
import ludot.random.RandomSource;

public final class ScriptedRandom implements RandomSource {
    private final Deque<Integer> values = new ArrayDeque<>();
    private int fallback;
    public int calls;

    public void add(int... numbers) {
        for (int number : numbers) {
            values.add(number);
        }
    }

    public int nextInt(int bound) {
        calls++;
        int value = values.isEmpty() ? fallback : values.removeFirst();
        if (value < 0 || value >= bound) {
            throw new AssertionError("Scripted value " + value
                    + " is outside random bound " + bound);
        }
        return value;
    }
}
