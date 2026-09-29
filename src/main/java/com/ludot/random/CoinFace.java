package com.ludot.random;

import com.ludot.model.Direction;

// Names the coin results used to choose a movement direction.
public enum CoinFace {
    // The two possible coin outcomes are used to choose the movement direction.
    HEADS,
    TAILS;

    // This converts the coin result into the direction used by the piece.
    public Direction toDirection() {
        return this == HEADS ? Direction.CLOCKWISE : Direction.COUNTER_CLOCKWISE;
    }
}