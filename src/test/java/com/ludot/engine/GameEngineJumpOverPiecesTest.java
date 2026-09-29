package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.CaptureCommand;
import com.ludot.command.Command;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Piece;
import com.ludot.rules.CaptureRule;
import com.ludot.rules.MovementRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// These tests cover the jump-over rule and the capture rule that applies only on landing.
class GameEngineJumpOverPiecesTest {

    // Confirms that passing over a same-colour piece does not trigger a capture.
    @Test
    void pieceCanJumpOverOwnSinglePiece() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        Piece movingPiece = new Piece(Colour.RED, 1);
        Piece ownSinglePiece = new Piece(Colour.RED, 2);

        board.placePieceOnStandardPath(movingPiece, 0);
        movingPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(ownSinglePiece, 1);
        ownSinglePiece.setDirection(Direction.CLOCKWISE);

        Command command = movementRule.commandForStandardMove(movingPiece, 5);
        command.execute();

        assertEquals(5, movingPiece.getLocation().getIndex());
        assertEquals(1, ownSinglePiece.getLocation().getIndex());
        assertFalse(captureRule.canCapture(movingPiece, ownSinglePiece.getLocation()));
    }

    @Test
        // Verifies that jumping over an opponent piece is not itself a capture.
    void pieceCanJumpOverOpponentSinglePieceWithoutCapturingIt() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        Piece movingPiece = new Piece(Colour.RED, 1);
        Piece opponentSinglePiece = new Piece(Colour.BLUE, 1);

        board.placePieceOnStandardPath(movingPiece, 0);
        movingPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(opponentSinglePiece, 1);
        opponentSinglePiece.setDirection(Direction.CLOCKWISE);

        Command command = movementRule.commandForStandardMove(movingPiece, 5);
        command.execute();

        assertEquals(5, movingPiece.getLocation().getIndex());
        assertEquals(1, opponentSinglePiece.getLocation().getIndex());
        assertFalse(opponentSinglePiece.isInBase());
        assertFalse(captureRule.canCapture(movingPiece, opponentSinglePiece.getLocation()));
    }

    @Test
        // Confirms that capture happens only when the mover lands on an opponent
        // square.
    void captureOnlyHappensWhenLandingOnOpponentCell() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        Piece movingPiece = new Piece(Colour.RED, 1);
        Piece opponentPiece = new Piece(Colour.BLUE, 1);

        board.placePieceOnStandardPath(movingPiece, 0);
        movingPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(opponentPiece, 5);
        opponentPiece.setDirection(Direction.CLOCKWISE);

        Command moveCommand = movementRule.commandForStandardMove(movingPiece, 5);
        moveCommand.execute();

        assertTrue(captureRule.canCapture(movingPiece, opponentPiece.getLocation()));
        Command captureCommand = captureRule.commandIfCaptureAvailable(movingPiece, opponentPiece.getLocation());
        assertInstanceOf(CaptureCommand.class, captureCommand);
        captureCommand.execute();

        assertEquals(1, movingPiece.getCaptureCount());
        assertTrue(opponentPiece.isInBase());
    }
}
