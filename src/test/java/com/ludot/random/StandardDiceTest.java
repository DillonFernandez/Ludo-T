package com.ludot.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm that the standard dice returns only valid values.
class StandardDiceTest {

    // Repeated rolls should always return a value from 1 to 6.
    @Test
    void rollAlwaysReturnsValueBetweenOneAndSixInclusive() {
        StandardDice dice = new StandardDice();

        for (int i = 0; i < 1000; i++) {
            int value = dice.roll();
            assertTrue(value >= 1);
            assertTrue(value <= 6);
        }
    }

}
