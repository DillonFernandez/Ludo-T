package com.ludot.random;

import com.ludot.model.MysteryEffect;

import java.util.Random;

// Chooses a mystery effect when a piece lands on a mystery cell.
public class StandardMysteryEffectSelector implements MysteryEffectSelector {

    // Available effects used for random selection.
    private static final MysteryEffect[] VALUES = MysteryEffect.values();

    // Random source used to choose an effect index.
    private final Random random;

    // Uses the default random seed for normal game play.
    public StandardMysteryEffectSelector() {
        this.random = new Random();
    }

    // Uses a fixed seed so tests can repeat the same outcomes.
    public StandardMysteryEffectSelector(long seed) {
        this.random = new Random(seed);
    }

    // Returns one mystery effect for the current turn.
    @Override
    public MysteryEffect selectEffect() {
        return VALUES[random.nextInt(VALUES.length)];
    }
}