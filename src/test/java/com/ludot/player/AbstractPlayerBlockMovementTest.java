package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.random.*;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm how paired movement keeps the original direction memory for block moves.
class AbstractPlayerBlockMovementTest {

    // Creates the small two-piece setup used by the block tests.
    private static TestPlayer createPlayer() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        RuleEngine ruleEngine = new RuleEngine(
                movementRule,
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                new MysteryRule(board,
                        new StandardMysteryEffectSelector(1L),
                        new StandardMysteryCellSpawner(1L),
                        new SilentLogger()),
                new WinCondition());

        return new TestPlayer(
                Colour.RED,
                new ArrayList<>(board.getHomeArea(Colour.RED).pieces()),
                board,
                ruleEngine,
                new FixedDice(3),
                () -> CoinFace.HEADS,
                new SilentLogger());
    }

    // A lone piece should still move normally outside a block.
    @Test
    void singlePieceMovementStillWorksWhenNotInABlock() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        RuleEngine ruleEngine = new RuleEngine(
                movementRule,
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                new MysteryRule(board,
                        new StandardMysteryEffectSelector(1L),
                        new StandardMysteryCellSpawner(1L),
                        new SilentLogger()),
                new WinCondition());

        TestPlayer player = new TestPlayer(
                Colour.RED,
                new ArrayList<>(board.getHomeArea(Colour.RED).pieces()),
                board,
                ruleEngine,
                new FixedDice(3),
                () -> CoinFace.HEADS,
                new SilentLogger());

        Piece piece = player.getPieces().get(0);
        board.placePieceOnStandardPath(piece, 10);
        piece.setDirection(Direction.CLOCKWISE);

        Command command = player.moveOnce(3);
        command.execute();

        assertEquals(13, piece.getLocation().getIndex());
    }

    @Test
    void blockWithOppositeDirectionsChoosesTheLongerRouteToHome() {
        Board board = new BoardBuilder().build();
        BlockRule blockRule = new BlockRule(board);

        Piece firstPiece = new Piece(Colour.RED, 1);
        Piece secondPiece = new Piece(Colour.RED, 2);

        board.placePieceOnStandardPath(firstPiece, 10);
        firstPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(secondPiece, 10);
        secondPiece.setDirection(Direction.COUNTER_CLOCKWISE);

        assertEquals(Direction.COUNTER_CLOCKWISE, blockRule.chooseBlockDirection(firstPiece));
    }

    @Test
    void blockMovementDistanceUsesDiceValueDividedByBlockSize() {
        Board board = new BoardBuilder().build();
        BlockRule blockRule = new BlockRule(board);

        assertEquals(2, blockRule.calculateBlockMovementDistance(6, 3));
        assertEquals(2, blockRule.calculateBlockMovementDistance(5, 2));
    }

    @Test
    void blockMoveMovesAllPiecesToTheSameDestination() {
        Board board = new BoardBuilder().build();
        Piece firstPiece = new Piece(Colour.RED, 1);
        Piece secondPiece = new Piece(Colour.RED, 2);

        board.placePieceOnStandardPath(firstPiece, 10);
        firstPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(secondPiece, 10);
        secondPiece.setDirection(Direction.CLOCKWISE);

        new com.ludot.command.BlockMoveCommand(board, List.of(firstPiece, secondPiece),
                board.getStandardCell(15).getLocation()).execute();

        assertEquals(15, firstPiece.getLocation().getIndex());
        assertEquals(15, secondPiece.getLocation().getIndex());
        assertEquals(firstPiece.getLocation(), secondPiece.getLocation());
        assertTrue(board.getStandardCell(15).getPieces().contains(firstPiece));
        assertTrue(board.getStandardCell(15).getPieces().contains(secondPiece));
    }

    // A block move should not overwrite the piece's original direction.
    @Test
    void blockMoveDoesNotOverwriteOriginalDirection() {
        TestPlayer player = createPlayer();
        Piece firstPiece = player.getPieces().get(0);
        Piece secondPiece = player.getPieces().get(1);

        player.board.placePieceOnStandardPath(firstPiece, 10);
        firstPiece.setDirection(Direction.CLOCKWISE);
        player.board.placePieceOnStandardPath(secondPiece, 10);
        secondPiece.setDirection(Direction.CLOCKWISE);

        Command command = player.movePiece(firstPiece, 5);
        command.execute();

        assertEquals(Direction.CLOCKWISE, firstPiece.getOriginalDirection());
        assertEquals(Direction.CLOCKWISE, secondPiece.getOriginalDirection());
    }

    // When a block splits, the piece should return to its original direction.
    @Test
    void clockwiseOriginalPieceRestoresDirectionWhenLeavingMovedBlock() {
        TestPlayer player = createPlayer();
        Piece firstPiece = player.getPieces().get(0);
        Piece secondPiece = player.getPieces().get(1);

        player.board.placePieceOnStandardPath(firstPiece, 14);
        firstPiece.setDirection(Direction.CLOCKWISE);
        player.board.placePieceOnStandardPath(secondPiece, 14);
        secondPiece.setDirection(Direction.CLOCKWISE);

        player.movePiece(firstPiece, 5).execute();
        player.movePiece(firstPiece, 4).execute();

        firstPiece.setDirection(Direction.COUNTER_CLOCKWISE);
        Command breakMove = player.movePiece(firstPiece, 3);
        breakMove.execute();

        assertEquals(Direction.CLOCKWISE, firstPiece.getDirection());
        assertEquals(21, firstPiece.getLocation().getIndex());
        assertEquals(18, secondPiece.getLocation().getIndex());
        assertEquals(Direction.CLOCKWISE, secondPiece.getDirection());
    }

    // The same original-direction rule should also work for counter-clockwise
    // blocks.
    @Test
    void counterClockwiseOriginalPieceRestoresDirectionWhenLeavingMovedBlock() {
        TestPlayer player = createPlayer();
        Piece firstPiece = player.getPieces().get(0);
        Piece secondPiece = player.getPieces().get(1);

        player.board.placePieceOnStandardPath(firstPiece, 20);
        firstPiece.setDirection(Direction.COUNTER_CLOCKWISE);
        player.board.placePieceOnStandardPath(secondPiece, 20);
        secondPiece.setDirection(Direction.COUNTER_CLOCKWISE);

        player.movePiece(firstPiece, 4).execute();
        player.movePiece(firstPiece, 2).execute();

        firstPiece.setDirection(Direction.CLOCKWISE);
        Command breakMove = player.movePiece(firstPiece, 3);
        breakMove.execute();

        assertEquals(Direction.COUNTER_CLOCKWISE, firstPiece.getDirection());
        assertEquals(14, firstPiece.getLocation().getIndex());
    }

    // Repeated block moves should preserve the original direction memory.
    @Test
    void originalDirectionSurvivesMultipleBlockMoves() {
        TestPlayer player = createPlayer();
        Piece firstPiece = player.getPieces().get(0);
        Piece secondPiece = player.getPieces().get(1);

        player.board.placePieceOnStandardPath(firstPiece, 14);
        firstPiece.setDirection(Direction.CLOCKWISE);
        player.board.placePieceOnStandardPath(secondPiece, 14);
        secondPiece.setDirection(Direction.CLOCKWISE);

        player.movePiece(firstPiece, 5).execute();
        player.movePiece(firstPiece, 2).execute();
        player.movePiece(firstPiece, 4).execute();

        assertEquals(Direction.CLOCKWISE, firstPiece.getOriginalDirection());
        assertEquals(Direction.CLOCKWISE, secondPiece.getOriginalDirection());
    }

    // Uses one fixed piece for the block-movement checks.
    private static final class TestPlayer extends AbstractPlayer {
        private final Board board;

        private TestPlayer(Colour colour, List<Piece> pieces, Board board, RuleEngine ruleEngine,
                           Dice dice, CoinToss coinToss, GameLogger logger) {
            super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
            this.board = board;
        }

        // Moves the first piece in the test setup.
        private Command moveOnce(int diceValue) {
            return tryStandardMove(getPieces().get(0), diceValue);
        }

        // Moves the piece chosen by the test.
        private Command movePiece(Piece piece, int diceValue) {
            return tryStandardMove(piece, diceValue);
        }

        @Override
        protected Command selectMove(int diceValue) {
            return moveOnce(diceValue);
        }
    }

    // Returns one fixed value so the tests stay predictable.
    private record FixedDice(int value) implements Dice {

        @Override
        public int roll() {
            return value;
        }
    }

    // Keeps the test output quiet while the block behavior is checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}