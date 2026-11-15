// Tests deployment coin tosses, movement directions, board wrapping and passing single pieces.
package ludot.movement;

import ludot.model.Direction;
import ludot.model.Piece;
import ludot.random.RandomSource;
import ludot.support.GameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StandardMovementTest {
    private final GameFixture game = new GameFixture();

    // A single heads result sets both deployment directions to clockwise.
    @Test
    void headsSelectsClockwise() {
        Piece red = game.red.getPieces()[0];
        game.random.add(0);
        game.executor.execute(game.rules.planPiece(red, 6));
        assertEquals(Direction.CLOCKWISE, red.getDirection());
        assertEquals(Direction.CLOCKWISE, red.getOriginalDirection());
        assertEquals(1, game.random.calls);
    }

    // Tails sets both directions to counter-clockwise after deployment and
    // preserves the entry messages.
    @Test
    void tailsSelectsCounterclockwise() {
        Piece red = game.red.getPieces()[0];
        game.random.add(1);
        assertFalse(game.executor.execute(game.rules.planPiece(red, 6)));
        assertEquals(26, red.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, red.getDirection());
        assertEquals(Direction.COUNTERCLOCKWISE, red.getOriginalDirection());
        assertTrue(game.output.text().contains(
                "red player moves piece R1 to the starting point."));
        assertTrue(game.output.text().contains(
                "red player now has 1/4 on pieces on the board and 3/4 pieces on the base."));
    }

    // The direction coin is tossed only after the piece reaches its starting cell.
    @Test
    void coinTossFollowsDeployment() {
        Piece red = game.red.getPieces()[0];
        CoinCheck random = new CoinCheck(red);
        MoveExecutor executor = new MoveExecutor(game.board, random,
                game.messages, game.mystery);
        executor.execute(game.rules.planPiece(red, 6));
        assertEquals(Direction.COUNTERCLOCKWISE, red.getDirection());
    }

    // A legal clockwise roll of six advances a piece from cell ten to cell sixteen.
    @Test
    void pieceMovesClockwise() {
        Piece red = game.place(game.red, 1, 10, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 6);
        assertTrue(move.legal);
        assertEquals(6, move.steps);
        game.executor.execute(move);
        assertEquals(16, red.getLocation());
    }

    // A legal counter-clockwise roll of six moves a piece from cell ten to cell
    // four.
    @Test
    void pieceMovesCounterclockwise() {
        Piece red = game.place(game.red, 1, 10, Direction.COUNTERCLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 6);
        assertTrue(move.legal);
        assertEquals(6, move.steps);
        game.executor.execute(move);
        assertEquals(4, red.getLocation());
    }

    // Clockwise and counter-clockwise movement wrap correctly across the ends of
    // the standard path.
    @Test
    void movementWrapsAroundTheBoard() {
        Piece red = game.place(game.red, 1, 51, Direction.CLOCKWISE);
        Piece blue = game.place(game.blue, 1, 0, Direction.COUNTERCLOCKWISE);
        game.executor.execute(game.rules.planPiece(red, 3));
        game.executor.execute(game.rules.planPiece(blue, 3));
        assertEquals(2, red.getLocation());
        assertEquals(49, blue.getLocation());
    }

    // Passing a single friendly piece leaves it in place and records no capture.
    @Test
    void pieceJumpsOverAFriendlyPiece() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece other = game.place(game.red, 2, 1, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 3);
        assertTrue(move.legal);
        assertFalse(game.executor.execute(move));
        assertEquals(3, red.getLocation());
        assertEquals(1, other.getLocation());
        assertEquals(0, red.getCaptures());
    }

    // Passing a single opponent leaves it in place and records no capture.
    @Test
    void pieceJumpsOverAnOpponent() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece other = game.place(game.blue, 2, 1, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 3);
        assertTrue(move.legal);
        assertFalse(game.executor.execute(move));
        assertEquals(3, red.getLocation());
        assertEquals(1, other.getLocation());
        assertEquals(0, red.getCaptures());
    }

    private static final class CoinCheck implements RandomSource {
        private final Piece piece;

        CoinCheck(Piece piece) {
            this.piece = piece;
        }

        public int nextInt(int bound) {
            assertEquals(2, bound);
            assertEquals(26, piece.getLocation());
            assertFalse(piece.isAtBase());
            return 1;
        }
    }
}
