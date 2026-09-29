package com.ludot.board;

import com.ludot.model.Colour;
import com.ludot.model.Piece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @param owner The owner colour and the pieces currently stored in this home area.
 */ // Holds the pieces that start in one player's home area before they enter the main path.
public record HomeArea(Colour owner, List<Piece> pieces) {

    // A home area copies its starting pieces and verifies that every piece belongs
    // to the same colour.
    public HomeArea(Colour owner, List<Piece> pieces) {
        if (owner == null) {
            throw new IllegalArgumentException("HomeArea owner must not be null.");
        }
        if (pieces == null) {
            throw new IllegalArgumentException("HomeArea piece list must not be null.");
        }
        for (Piece piece : pieces) {
            requireValidPiece(piece, owner);
        }
        this.owner = owner;
        this.pieces = new ArrayList<>(pieces);
    }

    // These helpers manage piece movement in and out of the home area and report
    // its current state.

    // This guard keeps invalid or mismatched pieces from being stored in the home
    // area.
    private static void requireValidPiece(Piece piece, Colour owner) {
        if (piece == null) {
            throw new IllegalArgumentException("A piece in HomeArea must not be null.");
        }
        if (piece.getColour() != owner) {
            throw new IllegalArgumentException(
                    "Piece colour " + piece.getColour()
                            + " does not match HomeArea owner " + owner + ".");
        }
    }

    // A piece can only enter this home area if it belongs to the same owner.
    public void addPiece(Piece piece) {
        requireValidPiece(piece, owner);
        pieces.add(piece);
    }

    // Removal is safe even if the piece is not currently stored here.
    public void removePiece(Piece piece) {
        if (piece == null) {
            throw new IllegalArgumentException("Piece to remove must not be null.");
        }
        pieces.remove(piece);
    }

    // This reports how many pieces are still waiting in the home area.
    public int countPieces() {
        return pieces.size();
    }

    // An empty home area means this player has no pieces left to start from.
    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    // The returned list is read-only so callers cannot accidentally mutate the
    // internal storage.
    @Override
    public List<Piece> pieces() {
        return Collections.unmodifiableList(pieces);
    }
}