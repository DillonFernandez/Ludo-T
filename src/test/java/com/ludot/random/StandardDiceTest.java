package com.ludot.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm that the standard dice returns only valid values.
class StandardDiceTest {

    // Repeated rolls should always return a value from 1 to 6.
    @Test
    void rollAlwaysReturnsValueBetweenOneAndSixInclusive() {
        StandardDice dice = new StandardDice(63038L);
        var observed = new java.util.HashSet<Integer>();

        for (int i = 0; i < 1000; i++) {
            int value = dice.roll();
            observed.add(value);
            assertTrue(value >= 1);
            assertTrue(value <= 6);
        }
        org.junit.jupiter.api.Assertions.assertEquals(java.util.Set.of(1, 2, 3, 4, 5, 6), observed);
    }

}
