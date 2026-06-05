package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Piece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// These tests confirm how clockwise movement advances pieces and wraps at the board edge.
class ClockwiseMovementTest {

    // A clockwise move should advance the piece by the dice value on the standard
    // path.
    @Test
    void clockwisePieceMovesByDiceValueOnStandardPath() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 5);
        piece.setDirection(Direction.CLOCKWISE);

        movementRule.commandForStandardMove(piece, 3).execute();

        assertEquals(8, piece.getLocation().getIndex());
    }

    // Movement should wrap from the end of the track back to the start of the loop.
    @Test
    void clockwisePieceWrapsAroundTheBoardEdge() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 50);
        piece.setDirection(Direction.CLOCKWISE);

        movementRule.commandForStandardMove(piece, 4).execute();

        assertEquals(2, piece.getLocation().getIndex());
    }

    // A standard move should not change the piece's clockwise direction.
    @Test
    void clockwisePieceDirectionRemainsClockwiseAfterMove() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 10);
        piece.setDirection(Direction.CLOCKWISE);

        movementRule.commandForStandardMove(piece, 2).execute();

        assertEquals(Direction.CLOCKWISE, piece.getDirection());
    }

}
