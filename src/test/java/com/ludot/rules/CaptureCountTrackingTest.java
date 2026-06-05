package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.*;
import com.ludot.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm how capture counts affect movement and reset behavior.
class CaptureCountTrackingTest {

    // A new piece should start with no capture credit.
    @Test
    void newPieceStartsWithZeroCaptureCount() {
        Piece piece = new Piece(Colour.RED, 1);

        assertEquals(0, piece.getCaptureCount());
    }

    // Only the capturing piece should gain a capture count.
    @Test
    void ordinaryCaptureIncrementsOnlyTheCapturingPiece() {
        Board board = new BoardBuilder().build();
        Piece capturingPiece = new Piece(Colour.RED, 1);
        Piece capturedPiece = new Piece(Colour.BLUE, 1);

        board.placePieceOnStandardPath(capturingPiece, 0);
        capturingPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(capturedPiece, 1);
        capturedPiece.setDirection(Direction.CLOCKWISE);

        Command command = new CaptureCommand(board, capturingPiece, capturedPiece, capturedPiece.getLocation());
        command.execute();

        assertEquals(1, capturingPiece.getCaptureCount());
        assertEquals(0, capturedPiece.getCaptureCount());
    }

    // A block capture should credit every piece in the capturing blockade.
    @Test
    void sameSizedBlockadeCaptureIncrementsEveryPieceInTheCapturingBlockade() {
        Board board = new BoardBuilder().build();
        Piece capturingOne = new Piece(Colour.RED, 1);
        Piece capturingTwo = new Piece(Colour.RED, 2);
        Piece capturedOne = new Piece(Colour.BLUE, 1);
        Piece capturedTwo = new Piece(Colour.BLUE, 2);

        board.placePieceOnStandardPath(capturingOne, 10);
        capturingOne.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(capturingTwo, 10);
        capturingTwo.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(capturedOne, 12);
        board.placePieceOnStandardPath(capturedTwo, 12);

        Command command = new BlockadeCaptureCommand(board,
                List.of(capturingOne, capturingTwo), List.of(capturedOne, capturedTwo), capturedOne.getLocation());
        command.execute();

        assertEquals(1, capturingOne.getCaptureCount());
        assertEquals(1, capturingTwo.getCaptureCount());
        assertEquals(0, capturedOne.getCaptureCount());
        assertEquals(0, capturedTwo.getCaptureCount());
    }

    // Returning a piece to base should clear its capture count.
    @Test
    void captureCountResetsToZeroWhenReturnedToBase() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.GREEN, 1);

        piece.incrementCaptureCount();
        piece.incrementCaptureCount();
        board.placePieceOnStandardPath(piece, 8);

        board.returnPieceToBase(piece);

        assertTrue(piece.isInBase());
        assertEquals(0, piece.getCaptureCount());
    }

    // A piece with zero captures should not enter the home straight early.
    @Test
    void zeroCapturesCannotEnterHomeStraight() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 24);
        piece.setDirection(Direction.CLOCKWISE);

        Command command = movementRule.commandForStandardMove(piece, 2);
        command.execute();

        assertEquals(LocationType.BETA, piece.getLocation().getType());
        assertEquals(26, piece.getLocation().getIndex());
    }

    // One capture should allow entry into the home straight when the move is
    // otherwise valid.
    @Test
    void oneCaptureCanEnterHomeStraightWhenOtherRulesAreSatisfied() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        board.placePieceOnStandardPath(piece, 24);
        piece.setDirection(Direction.CLOCKWISE);
        piece.incrementCaptureCount();

        Command command = movementRule.commandForStandardMove(piece, 2);
        command.execute();

        assertEquals(LocationType.HOME_PATH, piece.getLocation().getType());
        assertEquals(0, piece.getLocation().getIndex());
    }

    @Test
    void homePathMoveUsesExactRemainingStepsToReachHome() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        piece.setLocation(Location.homePath(Colour.RED, 3));
        piece.incrementCaptureCount();

        Command exactMove = movementRule.commandForHomePathMove(piece, 2);

        assertInstanceOf(MoveCommand.class, exactMove);
        exactMove.execute();

        assertTrue(piece.isHome());
        assertEquals(LocationType.HOME, piece.getLocation().getType());
    }

    @Test
    void homePathMoveOvershootDoesNotMovePieceToHome() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        Piece piece = new Piece(Colour.RED, 1);

        piece.setLocation(Location.homePath(Colour.RED, 3));
        piece.incrementCaptureCount();

        Command overshoot = movementRule.commandForHomePathMove(piece, 3);

        assertInstanceOf(NoMoveCommand.class, overshoot);
        assertFalse(piece.isHome());
        assertEquals(LocationType.HOME_PATH, piece.getLocation().getType());
    }
}
