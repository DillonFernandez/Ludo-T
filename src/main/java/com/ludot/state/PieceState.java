package com.ludot.state;

import com.ludot.model.Piece;

// Defines the shared behavior for every temporary piece state in the game.
public interface PieceState {

    // Returns the label used for this state in the UI and logs.
    String getName();

    // Returns true when the piece may move normally in this state.
    boolean canMove(Piece piece);

    // Adjusts the dice value when the current state changes movement.
    int adjustMovement(Piece piece, int diceValue);

    // Runs when the piece enters this state.
    void onEnter(Piece piece);

    // Runs at the end of each round to update time-based effects.
    void onRoundPassed(Piece piece);

    // Guards against null pieces and invalid dice values before state logic runs.

    default void requirePiece(Piece piece) {
        if (piece == null) {
            throw new IllegalArgumentException("Piece must not be null.");
        }
    }

    default void requireDiceValue(int diceValue) {
        if (diceValue < 1) {
            throw new IllegalArgumentException(
                    "Dice value must be at least 1, got " + diceValue);
        }
    }
}