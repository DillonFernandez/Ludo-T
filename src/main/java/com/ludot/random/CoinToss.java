package com.ludot.random;

// Provides the coin result used to choose a movement direction.
public interface CoinToss {

    // Returns one coin result for the current turn.
    CoinFace toss();
}