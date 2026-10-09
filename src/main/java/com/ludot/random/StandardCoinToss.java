package com.ludot.random;

import java.util.Random;

// Uses a random coin flip for normal play and a seeded flip for tests.
public class StandardCoinToss implements CoinToss {

    // Random source used to generate the coin result.
    private final Random random;

    // Uses the default random seed for normal game play.
    public StandardCoinToss() {
        this.random = new Random();
    }

    // Uses a fixed seed so tests can repeat the same outcomes.
    public StandardCoinToss(long seed) {
        this.random = new Random(seed);
    }

    // Returns one coin result for the current turn.
    @Override
    public CoinFace toss() {
        return random.nextBoolean() ? CoinFace.HEADS : CoinFace.TAILS;
    }
}