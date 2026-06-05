package com.ludot.random;

// A fixed dice value helps tests stay deterministic.
public final class FixedDice implements Dice {

    // Stores the roll value returned by this test helper.
    private final int value;

    public FixedDice(int value) {
        this.value = value;
    }

    @Override
    public int roll() {
        return value;
    }
}
