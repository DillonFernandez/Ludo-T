package com.ludot.model;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.output.GameLogger;
import com.ludot.random.StandardMysteryCellSpawner;
import com.ludot.random.StandardMysteryEffectSelector;
import com.ludot.rules.MysteryRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// These tests confirm how a piece stores and resets its direction during normal and mystery effects.
class PieceTest {

    // A newly assigned direction should be stored as both the current and original
    // facing.
    @Test
    void originalDirectionIsStoredWhenDirectionIsFirstAssigned() {
        Piece piece = new Piece(Colour.RED, 1);

        piece.setDirection(Direction.CLOCKWISE);

        assertEquals(Direction.CLOCKWISE, piece.getDirection());
        assertEquals(Direction.CLOCKWISE, piece.getOriginalDirection());
    }

    // A mystery effect can change the current direction without overwriting the
    // original one.
    @Test
    void gammaCanChangeCurrentDirectionWithoutChangingOriginalDirection() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.RED, 1);
        MysteryRule mysteryRule = new MysteryRule(board,
                new StandardMysteryEffectSelector(1L),
                new StandardMysteryCellSpawner(1L),
                new SilentLogger());

        piece.setDirection(Direction.CLOCKWISE);
        board.teleportPieceToGamma(piece);
        mysteryRule.applyPostTeleportEffect(piece, MysteryEffect.GAMMA);

        assertEquals(Direction.COUNTER_CLOCKWISE, piece.getDirection());
        assertEquals(Direction.CLOCKWISE, piece.getOriginalDirection());
    }

    // Returning a piece to base should clear its stored facing information.
    @Test
    void returnToBaseResetsOriginalDirection() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.RED, 1);

        piece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(piece, 10);

        board.returnPieceToBase(piece);

        assertNull(piece.getDirection());
        assertNull(piece.getOriginalDirection());
    }

    // Keeps the test output quiet while the direction rules are checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
