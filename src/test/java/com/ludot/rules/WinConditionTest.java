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
        for (Colour colour : Colour.values()) {
            List<Piece> pieces = new ArrayList<>();
            for (int number = 1; number <= 3; number++) {
                Piece piece = new Piece(colour, number);
                piece.setLocation(Location.home(colour));
                pieces.add(piece);
            }
            Piece piece4 = new Piece(colour, 4);
            // The final piece alone is still in base: three home is not a win.
            pieces.add(piece4);

            assertFalse(winCondition.hasPlayerWon(pieces), colour.name());
        }
    }

    // A player should win when every piece has reached home.
    @Test
    void returnsTrueWhenAllPiecesAreHome() {
        WinCondition winCondition = new WinCondition();
        for (Colour colour : Colour.values()) {
            List<Piece> pieces = new ArrayList<>();
            for (int pieceNumber = 1; pieceNumber <= 4; pieceNumber++) {
                Piece piece = new Piece(colour, pieceNumber);
                piece.setLocation(Location.home(colour));
                pieces.add(piece);
            }

            assertTrue(winCondition.hasPlayerWon(pieces), colour.name());
        }
    }
}
