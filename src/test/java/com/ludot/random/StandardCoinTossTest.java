package com.ludot.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm that the standard coin toss returns only valid faces.
class StandardCoinTossTest {

    // Repeated tosses should always return HEADS or TAILS.
    @Test
    void tossAlwaysReturnsHeadsOrTails() {
        StandardCoinToss toss = new StandardCoinToss();

        for (int i = 0; i < 1000; i++) {
            CoinFace face = toss.toss();
            assertTrue(face == CoinFace.HEADS || face == CoinFace.TAILS);
        }
    }

}
