package com.ludot.rules;

import com.ludot.model.Colour;
import com.ludot.model.Location;
import com.ludot.model.Piece;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm when the win rule should return true or false.
class WinConditionTest {

    // A player should not win while any piece is still off home.
    @Test
    void returnsFalseWhenNotAllPiecesAreHome() {
        WinCondition winCondition = new WinCondition();
        List<Piece> pieces = new ArrayList<>();
        pieces.add(new Piece(Colour.RED, 1));
        pieces.add(new Piece(Colour.RED, 2));
        pieces.add(new Piece(Colour.RED, 3));
        Piece piece4 = new Piece(Colour.RED, 4);
        piece4.setLocation(Location.home(Colour.RED));
        pieces.add(piece4);

        assertFalse(winCondition.hasPlayerWon(pieces));
    }

    // A player should win when every piece has reached home.
    @Test
    void returnsTrueWhenAllPiecesAreHome() {
        WinCondition winCondition = new WinCondition();
        List<Piece> pieces = new ArrayList<>();
        for (int pieceNumber = 1; pieceNumber <= 4; pieceNumber++) {
            Piece piece = new Piece(Colour.BLUE, pieceNumber);
            piece.setLocation(Location.home(Colour.BLUE));
            pieces.add(piece);
        }

        assertTrue(winCondition.hasPlayerWon(pieces));
    }
}
