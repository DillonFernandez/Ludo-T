package com.ludot.board;

import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Piece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// These tests verify the main piece-movement rules around base entry and return.
class BoardMovementTest {

    // Confirms that a piece leaves base and lands on its starting square.
    @Test
    void movingPieceFromBasePlacesItOnStartingSquare() {
        for (Colour colour : Colour.values()) {
            Board board = new BoardBuilder().build();
            Piece piece = board.getHomeArea(colour).pieces().get(0);

            board.movePieceFromBaseToStartingSquare(piece);

            assertTrue(piece.getLocation().isStartingSquare(), colour.name());
            assertEquals(board.getPath().getStartingIndex(colour), piece.getLocation().getIndex(), colour.name());
            assertEquals(3, board.getHomeArea(colour).countPieces(), colour.name());
        }
    }

    @Test
        // Verifies that returning a piece to base clears its path state and direction.
    void returningPieceToBaseResetsItsLocationAndDirection() {
        for (Colour colour : Colour.values()) {
            for (Direction direction : Direction.values()) {
                Board board = new BoardBuilder().build();
                Piece piece = board.getHomeArea(colour).pieces().get(0);
                int occupiedIndex = 5;

                board.placePieceOnStandardPath(piece, occupiedIndex);
                piece.setDirection(direction);

                board.returnPieceToBase(piece);

                assertTrue(piece.isInBase(), colour + " " + direction);
                assertNull(piece.getDirection(), colour + " " + direction);
                assertNull(piece.getOriginalDirection(), colour + " " + direction);
                assertFalse(board.getStandardCell(occupiedIndex).getPieces().contains(piece),
                        colour + " " + direction);
                assertEquals(4, board.getHomeArea(colour).countPieces(), colour + " " + direction);
            }
        }
    }
}
