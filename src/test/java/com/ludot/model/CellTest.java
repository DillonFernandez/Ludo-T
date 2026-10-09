package com.ludot.model;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm how a standard-path cell behaves when pieces stack or move away.
class CellTest {

    // A single piece should not make the cell blocked.
    @Test
    void singlePieceAloneIsNotABlock() {
        for (Colour colour : Colour.values()) {
            Board board = new BoardBuilder().build();
            Piece piece = new Piece(colour, 1);

            board.placePieceOnStandardPath(piece, 4);

            assertFalse(board.getStandardCell(4).isBlocked(), colour.name());
        }
    }

    // Two pieces on one cell should make the cell blocked.
    @Test
    void twoSameColourPiecesFormABlock() {
        for (Colour colour : Colour.values()) {
            Board board = new BoardBuilder().build();
            Piece firstPiece = new Piece(colour, 1);
            Piece secondPiece = new Piece(colour, 2);

            board.placePieceOnStandardPath(firstPiece, 4);
            board.placePieceOnStandardPath(secondPiece, 4);

            assertTrue(board.getStandardCell(4).isBlocked(), colour.name());
            assertEquals(2, board.getStandardCell(4).getPieces().size(), colour.name());
        }
    }

    // A third piece should still keep the cell blocked and increase the stack.
    @Test
    void thirdSameColourPieceExtendsTheBlock() {
        for (Colour colour : Colour.values()) {
            Board board = new BoardBuilder().build();
            Piece firstPiece = new Piece(colour, 1);
            Piece secondPiece = new Piece(colour, 2);
            Piece thirdPiece = new Piece(colour, 3);

            board.placePieceOnStandardPath(firstPiece, 5);
            board.placePieceOnStandardPath(secondPiece, 5);
            board.placePieceOnStandardPath(thirdPiece, 5);

            assertTrue(board.getStandardCell(5).isBlocked(), colour.name());
            assertEquals(3, board.getStandardCell(5).getPieces().size(), colour.name());
        }
    }

    // Moving one piece away should update the old and new cells correctly.
    @Test
    void removingOnePieceUpdatesBlockStatus() {
        for (Colour colour : Colour.values()) {
            Board board = new BoardBuilder().build();
            Piece firstPiece = new Piece(colour, 1);
            Piece secondPiece = new Piece(colour, 2);

            board.placePieceOnStandardPath(firstPiece, 6);
            board.placePieceOnStandardPath(secondPiece, 6);

            assertTrue(board.getStandardCell(6).isBlocked(), colour.name());

            board.placePieceOnStandardPath(firstPiece, 7);

            assertFalse(board.getStandardCell(6).isBlocked(), colour.name());
            assertFalse(board.getStandardCell(7).isBlocked(), colour.name());
            assertEquals(1, board.getStandardCell(6).getPieces().size(), colour.name());
            assertEquals(1, board.getStandardCell(7).getPieces().size(), colour.name());
        }
    }
}
