package com.ludot.rules;

import com.ludot.model.Piece;

import java.util.List;

// Checks whether every piece in a player's set has reached home.
public class WinCondition {

    // Returns true only when every piece in the list has finished.
    public boolean hasPlayerWon(List<Piece> pieces) {
        if (pieces == null)
            throw new IllegalArgumentException("Piece list must not be null.");
        if (pieces.isEmpty())
            return false;

        for (Piece piece : pieces) {
            if (piece == null)
                throw new IllegalArgumentException(
                        "Piece list must not contain null pieces.");
            if (!isPieceHome(piece))
                return false;
        }
        return true;
    }

    // Returns true for a single piece that has already reached home.
    public boolean isPieceHome(Piece piece) {
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
        return piece.isHome();
    }
}