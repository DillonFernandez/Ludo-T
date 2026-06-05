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
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 4);

        assertFalse(board.getStandardCell(4).isBlocked());
    }

    // Two pieces on one cell should make the cell blocked.
    @Test
    void twoSameColourPiecesFormABlock() {
        Board board = new BoardBuilder().build();
        Piece firstPiece = new Piece(Colour.RED, 1);
        Piece secondPiece = new Piece(Colour.RED, 2);

        board.placePieceOnStandardPath(firstPiece, 4);
        board.placePieceOnStandardPath(secondPiece, 4);

        assertTrue(board.getStandardCell(4).isBlocked());
        assertEquals(2, board.getStandardCell(4).getPieces().size());
    }

    // A third piece should still keep the cell blocked and increase the stack.
    @Test
    void thirdSameColourPieceExtendsTheBlock() {
        Board board = new BoardBuilder().build();
        Piece firstPiece = new Piece(Colour.RED, 1);
        Piece secondPiece = new Piece(Colour.RED, 2);
        Piece thirdPiece = new Piece(Colour.RED, 3);

        board.placePieceOnStandardPath(firstPiece, 5);
        board.placePieceOnStandardPath(secondPiece, 5);
        board.placePieceOnStandardPath(thirdPiece, 5);

        assertTrue(board.getStandardCell(5).isBlocked());
        assertEquals(3, board.getStandardCell(5).getPieces().size());
    }

    // Moving one piece away should update the old and new cells correctly.
    @Test
    void removingOnePieceUpdatesBlockStatus() {
        Board board = new BoardBuilder().build();
        Piece firstPiece = new Piece(Colour.BLUE, 1);
        Piece secondPiece = new Piece(Colour.BLUE, 2);

        board.placePieceOnStandardPath(firstPiece, 6);
        board.placePieceOnStandardPath(secondPiece, 6);

        assertTrue(board.getStandardCell(6).isBlocked());

        board.placePieceOnStandardPath(firstPiece, 7);

        assertFalse(board.getStandardCell(6).isBlocked());
        assertFalse(board.getStandardCell(7).isBlocked());
        assertEquals(1, board.getStandardCell(6).getPieces().size());
        assertEquals(1, board.getStandardCell(7).getPieces().size());
    }
}
