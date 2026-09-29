package com.ludot.state;

import com.ludot.config.GameConfig;
import com.ludot.model.Piece;

// Applies the Alpha slow-down rule, including its duration and reduced movement.
public class SickState implements PieceState {

    // Uses the visible name shown for this state.
    @Override
    public String getName() {
        return "Sick";
    }

    // A piece can move while sick only if it is still in play.
    @Override
    public boolean canMove(Piece piece) {
        requirePiece(piece);
        return !piece.isInBase() && !piece.isHome() && piece.getAlphaEffectRemainingRounds() > 0;
    }

    // Reduces the dice value, but never below one step.
    @Override
    public int adjustMovement(Piece piece, int diceValue) {
        requirePiece(piece);
        requireDiceValue(diceValue);
        int adjusted = diceValue / GameConfig.SICK_MOVEMENT_DIVISOR;
        return Math.max(1, adjusted);
    }

    // Starts the countdown for the sick effect when the piece enters this state.
    @Override
    public void onEnter(Piece piece) {
        requirePiece(piece);
        piece.setAlphaEffectRemainingRounds(GameConfig.ALPHA_EFFECT_DURATION_ROUNDS);
    }

    // Decreases the remaining sick rounds after each turn.
    @Override
    public void onRoundPassed(Piece piece) {
        requirePiece(piece);
        int remaining = piece.getAlphaEffectRemainingRounds();
        if (remaining > 0) {
            piece.setAlphaEffectRemainingRounds(remaining - 1);
        }
    }
}