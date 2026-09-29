package com.ludot.state;

import com.ludot.config.GameConfig;
import com.ludot.model.Piece;

// Applies the Alpha boost rule, including its duration and movement effect.
public class EnergizedState implements PieceState {

    // Uses the visible name shown for this state.
    @Override
    public String getName() {
        return "Energized";
    }

    // A piece can move while energized only if it is still in play.
    @Override
    public boolean canMove(Piece piece) {
        requirePiece(piece);
        return !piece.isInBase()
                && !piece.isHome()
                && piece.getAlphaEffectRemainingRounds() > 0;
    }

    // Doubles the dice value while this effect is active.
    @Override
    public int adjustMovement(Piece piece, int diceValue) {
        requirePiece(piece);
        requireDiceValue(diceValue);
        return diceValue * GameConfig.ENERGIZED_MOVEMENT_MULTIPLIER;
    }

    // Starts the countdown for the energized effect when the piece enters this
    // state.
    @Override
    public void onEnter(Piece piece) {
        requirePiece(piece);
        piece.setAlphaEffectRemainingRounds(GameConfig.ALPHA_EFFECT_DURATION_ROUNDS);
    }

    // Decreases the remaining energized rounds after each turn.
    @Override
    public void onRoundPassed(Piece piece) {
        requirePiece(piece);
        int remaining = piece.getAlphaEffectRemainingRounds();
        if (remaining > 0) {
            piece.setAlphaEffectRemainingRounds(remaining - 1);
        }
    }
}