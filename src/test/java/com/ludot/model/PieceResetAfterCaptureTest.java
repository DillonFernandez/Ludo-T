package com.ludot.model;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.BlockadeCaptureCommand;
import com.ludot.command.CaptureCommand;
import com.ludot.output.GameLogger;
import com.ludot.random.StandardMysteryCellSpawner;
import com.ludot.random.StandardMysteryEffectSelector;
import com.ludot.rules.MysteryRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm that captured pieces are fully reset before returning to base.
class PieceResetAfterCaptureTest {

    // Prepares a piece with temporary state to simulate a captured piece before
    // reset.
    private static void prepareCapturedPiece(Board board, MysteryRule mysteryRule, Piece piece, int cellIndex) {
        board.placePieceOnStandardPath(piece, cellIndex);
        piece.setDirection(Direction.CLOCKWISE);
        piece.incrementCaptureCount();
        piece.setAlphaMovementState(new com.ludot.state.EnergizedState());
        piece.setAlphaEffectRemainingRounds(2);
        piece.setBetaBriefingRemainingRounds(2);
        piece.setConsecutiveBetaRestrictedRollCount(1);
        piece.incrementCounterClockwiseApproachPassCount();
        board.teleportPieceToGamma(piece);
        mysteryRule.applyPostTeleportEffect(piece, MysteryEffect.GAMMA);
    }

    // Confirms the captured piece is back in base with all temporary state removed.
    private static void assertCapturedPieceReset(Board board, Piece piece, int oldCellIndex) {
        assertTrue(piece.isInBase());
        assertNull(piece.getDirection());
        assertNull(piece.getOriginalDirection());
        assertEquals(0, piece.getCaptureCount());
        assertEquals(0, piece.getAlphaEffectRemainingRounds());
        assertNull(piece.getAlphaMovementState());
        assertEquals(0, piece.getBetaBriefingRemainingRounds());
        assertEquals(0, piece.getConsecutiveBetaRestrictedRollCount());
        assertEquals(0, piece.getCounterClockwiseApproachPassCount());
        assertFalse(board.getStandardCell(oldCellIndex).getPieces().contains(piece));
    }

    // Creates the helper used to apply teleport effects in the test setup.
    private static MysteryRule createMysteryRule(Board board) {
        return new MysteryRule(board,
                new StandardMysteryEffectSelector(1L),
                new StandardMysteryCellSpawner(1L),
                new SilentLogger());
    }

    // Verifies that a normal capture clears all temporary state from the captured
    // piece.
    @Test
    void ordinaryCaptureResetsCapturedPieceStateCompletely() {
        Board board = new BoardBuilder().build();
        MysteryRule mysteryRule = createMysteryRule(board);
        Piece capturingPiece = new Piece(Colour.RED, 1);
        Piece capturedPiece = new Piece(Colour.BLUE, 1);
        int captureCellIndex = board.getPath().getGammaIndex();

        prepareCapturedPiece(board, mysteryRule, capturedPiece, 12);
        board.placePieceOnStandardPath(capturingPiece, 11);

        new CaptureCommand(board, capturingPiece, capturedPiece, capturedPiece.getLocation()).execute();

        assertCapturedPieceReset(board, capturedPiece, captureCellIndex);
    }

    // Checks that a blockade capture resets every captured piece and clears the
    // cell.
    @Test
    void blockadeCaptureResetsEveryCapturedPieceStateCompletely() {
        Board board = new BoardBuilder().build();
        MysteryRule mysteryRule = createMysteryRule(board);
        Piece capturingOne = new Piece(Colour.RED, 1);
        Piece capturingTwo = new Piece(Colour.RED, 2);
        Piece capturedOne = new Piece(Colour.BLUE, 1);
        Piece capturedTwo = new Piece(Colour.BLUE, 2);
        int captureCellIndex = board.getPath().getGammaIndex();

        prepareCapturedPiece(board, mysteryRule, capturedOne, 12);
        prepareCapturedPiece(board, mysteryRule, capturedTwo, 12);
        board.placePieceOnStandardPath(capturingOne, 10);
        capturingOne.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(capturingTwo, 10);
        capturingTwo.setDirection(Direction.CLOCKWISE);

        new BlockadeCaptureCommand(board, List.of(capturingOne, capturingTwo), List.of(capturedOne, capturedTwo),
                capturedOne.getLocation()).execute();

        assertCapturedPieceReset(board, capturedOne, captureCellIndex);
        assertCapturedPieceReset(board, capturedTwo, captureCellIndex);
        assertTrue(board.getStandardCell(captureCellIndex).getPieces().isEmpty());
        assertFalse(board.getStandardCell(captureCellIndex).isBlocked());
    }

    // Keeps the test output quiet while the capture state is checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
