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
        Board board = new BoardBuilder().build();
        Piece piece = board.getHomeArea(Colour.RED).pieces().get(0);

        board.movePieceFromBaseToStartingSquare(piece);

        assertTrue(piece.getLocation().isStartingSquare());
        assertEquals(board.getPath().getStartingIndex(Colour.RED), piece.getLocation().getIndex());
        assertEquals(3, board.getHomeArea(Colour.RED).countPieces());
    }

    @Test
        // Verifies that returning a piece to base clears its path state and direction.
    void returningPieceToBaseResetsItsLocationAndDirection() {
        Board board = new BoardBuilder().build();
        Piece piece = board.getHomeArea(Colour.RED).pieces().get(0);

        board.placePieceOnStandardPath(piece, 5);
        piece.setDirection(Direction.CLOCKWISE);

        board.returnPieceToBase(piece);

        assertTrue(piece.isInBase());
        assertNull(piece.getDirection());
        assertEquals(4, board.getHomeArea(Colour.RED).countPieces());
    }
}
