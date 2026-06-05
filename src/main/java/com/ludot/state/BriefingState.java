package com.ludot.state;

import com.ludot.config.GameConfig;
import com.ludot.model.Piece;

// Keeps a piece paused in Beta for a fixed number of turns.
public class BriefingState implements PieceState {

    // Uses the briefing label shown by the game state.
    @Override
    public String getName() {
        return "Briefing";
    }

    // A piece in briefing can move again only after its waiting rounds are over.
    @Override
    public boolean canMove(Piece piece) {
        requirePiece(piece);
        return piece.getBetaBriefingRemainingRounds() <= 0;
    }

    // Briefing forces movement to zero for the current turn.
    @Override
    public int adjustMovement(Piece piece, int diceValue) {
        requirePiece(piece);
        requireDiceValue(diceValue);
        return 0;
    }

    // Resets the waiting counter and the restricted-roll streak when briefing
    // starts.
    @Override
    public void onEnter(Piece piece) {
        requirePiece(piece);
        piece.setBetaBriefingRemainingRounds(GameConfig.BETA_BRIEFING_DURATION_ROUNDS);
        piece.setConsecutiveBetaRestrictedRollCount(0);
    }

    // Decreases the remaining briefing turns at the end of each round.
    @Override
    public void onRoundPassed(Piece piece) {
        requirePiece(piece);
        int remaining = piece.getBetaBriefingRemainingRounds();
        if (remaining > 0) {
            piece.setBetaBriefingRemainingRounds(remaining - 1);
        }
    }
}