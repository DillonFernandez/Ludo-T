package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.LocationType;
import com.ludot.model.Piece;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// These tests confirm how counter-clockwise movement advances pieces and wraps at the board edge.
class CounterClockwiseMovementTest {

    // A counter-clockwise move should move the piece backward by the dice value on
    // the standard path.
    @Test
    void counterClockwisePieceMovesByDiceValueInReverseOnStandardPath() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 10);
        piece.setDirection(Direction.COUNTER_CLOCKWISE);

        movementRule.commandForStandardMove(piece, 3).execute();

        assertEquals(7, piece.getLocation().getIndex());
        assertEquals(Direction.COUNTER_CLOCKWISE, piece.getDirection());
    }

    // Movement should wrap from the start of the track back to the end of the loop.
    @Test
    void counterClockwisePieceWrapsAroundTheBoardEdge() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 1);
        piece.setDirection(Direction.COUNTER_CLOCKWISE);

        movementRule.commandForStandardMove(piece, 4).execute();

        assertEquals(49, piece.getLocation().getIndex());
    }

    // A piece on its first approach pass should not enter the home straight yet.
    @Test
    void counterClockwisePieceDoesNotEnterHomeStraightOnFirstApproachPass() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.YELLOW, 1);

        board.placePieceOnStandardPath(piece, board.getPath().getApproachIndex(Colour.YELLOW));
        piece.setDirection(Direction.COUNTER_CLOCKWISE);
        piece.incrementCaptureCount();

        movementRule.commandForStandardMove(piece, 1).execute();

        assertNotEquals(LocationType.HOME_PATH, piece.getLocation().getType());
        assertEquals(50, piece.getLocation().getIndex());
        assertEquals(1, piece.getCounterClockwiseApproachPassCount());
    }

    // The same piece should enter the home straight on the second approach pass.
    @Test
    void counterClockwisePieceEntersHomeStraightOnSecondApproachPass() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.YELLOW, 1);

        int approachIndex = board.getPath().getApproachIndex(Colour.YELLOW);
        board.placePieceOnStandardPath(piece, approachIndex);
        piece.setDirection(Direction.COUNTER_CLOCKWISE);
        piece.incrementCaptureCount();
        piece.incrementCounterClockwiseApproachPassCount();

        movementRule.commandForStandardMove(piece, 1).execute();

        assertEquals(LocationType.HOME_PATH, piece.getLocation().getType());
        assertEquals(0, piece.getLocation().getIndex());
        assertEquals(2, piece.getCounterClockwiseApproachPassCount());
    }
}
