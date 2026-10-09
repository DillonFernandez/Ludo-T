package com.ludot.factory;

import com.ludot.config.GameConfig;
import com.ludot.model.Colour;
import com.ludot.model.Piece;

import java.util.ArrayList;
import java.util.List;

// Creates the standard set of pieces for one player colour.
public class PieceFactory {

    // Each piece is numbered so a player can distinguish their own tokens during
    // play.
    public List<Piece> createPieces(Colour colour) {
        if (colour == null) {
            throw new IllegalArgumentException("Colour must not be null.");
        }
        List<Piece> pieces = new ArrayList<>();
        for (int i = 1; i <= GameConfig.PIECES_PER_PLAYER; i++) {
            pieces.add(new Piece(colour, i));
        }
        return pieces;
    }
}