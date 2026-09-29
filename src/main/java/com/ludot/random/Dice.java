package com.ludot.random;

// Provides the dice result used to decide a move.
public interface Dice {

    // Returns one dice value for the current turn.
    int roll();
}