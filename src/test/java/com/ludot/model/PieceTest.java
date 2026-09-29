package com.ludot.model;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.output.GameLogger;
import com.ludot.random.FixedMysteryCellSpawner;
import com.ludot.random.FixedMysteryEffectSelector;
import com.ludot.rules.MysteryRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// These tests confirm how a piece stores and resets its direction during normal and mystery effects.
class PieceTest {

    // A newly assigned direction should be stored as both the current and original
    // facing.
    @Test
    void originalDirectionIsStoredWhenDirectionIsFirstAssigned() {
        for (Colour colour : Colour.values()) {
            for (Direction direction : Direction.values()) {
                Piece piece = new Piece(colour, 1);

                piece.setDirection(direction);

                assertEquals(direction, piece.getDirection(), colour.name());
                assertEquals(direction, piece.getOriginalDirection(), colour.name());
            }
        }
    }

    // A mystery effect can change the current direction without overwriting the
    // original one.
    @Test
    void gammaCanChangeCurrentDirectionWithoutChangingOriginalDirection() {
        Board board = new BoardBuilder().build();
        Piece piece = new Piece(Colour.RED, 1);
        MysteryRule mysteryRule = new MysteryRule(board,
                new FixedMysteryEffectSelector(MysteryEffect.GAMMA),
                new FixedMysteryCellSpawner(0),
                new SilentLogger());

        piece.setDirection(Direction.CLOCKWISE);
        board.teleportPieceToGamma(piece);
        mysteryRule.applyPostTeleportEffect(piece, MysteryEffect.GAMMA);

        assertEquals(Direction.COUNTER_CLOCKWISE, piece.getDirection());
        assertEquals(Direction.CLOCKWISE, piece.getOriginalDirection());
    }

    // Keeps the test output quiet while the direction rules are checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
