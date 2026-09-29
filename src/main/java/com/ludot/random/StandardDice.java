package com.ludot.random;

import com.ludot.config.GameConfig;

import java.util.Random;

// Uses the configured dice range for normal play and repeatable tests.
public class StandardDice implements Dice {

    // Random source used to generate the roll value.
    private final Random random;

    // Uses the default random seed for normal game play.
    public StandardDice() {
        this.random = new Random();
    }

    // Uses a fixed seed so tests can repeat the same outcomes.
    public StandardDice(long seed) {
        this.random = new Random(seed);
    }

    // Returns one value within the configured dice range.
    @Override
    public int roll() {
        return GameConfig.DICE_MIN
                + random.nextInt(GameConfig.DICE_MAX - GameConfig.DICE_MIN + 1);
    }
}