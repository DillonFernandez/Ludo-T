package com.ludot.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm that the standard coin toss returns only valid faces.
class StandardCoinTossTest {

    // Repeated tosses should always return HEADS or TAILS.
    @Test
    void tossAlwaysReturnsHeadsOrTails() {
        StandardCoinToss toss = new StandardCoinToss(63038L);
        var observed = java.util.EnumSet.noneOf(CoinFace.class);

        for (int i = 0; i < 1000; i++) {
            CoinFace face = toss.toss();
            observed.add(face);
            assertTrue(face == CoinFace.HEADS || face == CoinFace.TAILS);
        }
        org.junit.jupiter.api.Assertions.assertEquals(java.util.EnumSet.allOf(CoinFace.class), observed);
    }

}
