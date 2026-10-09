package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.*;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests cover how blocking pieces affect movement and what the engine reports.
class GameEngineBlockMovementTest {

    // Builds a small controlled scenario for each block-movement test.
    private static TestContext createContext(Direction direction,
                                             int movingPieceIndex,
                                             int blockIndex,
                                             int moveRoll,
                                             int redRoll,
                                             int greenRoll,
                                             int yellowRoll) {
        CapturingGameLogger logger = new CapturingGameLogger();
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        BlockRule blockRule = new BlockRule(board);
        BonusRollRule bonusRollRule = new BonusRollRule();
        MysteryEffectSelector mysteryEffectSelector = new StandardMysteryEffectSelector(1L);
        MysteryCellSpawner mysteryCellSpawner = new StandardMysteryCellSpawner(1L);
        MysteryRule mysteryRule = new MysteryRule(board, mysteryEffectSelector, mysteryCellSpawner, logger);
        RuleEngine ruleEngine = new RuleEngine(
                movementRule, captureRule, blockRule, bonusRollRule, mysteryRule, new WinCondition());

        SequenceDice dice = new SequenceDice(
                redRoll,
                greenRoll,
                yellowRoll,
                1,
                moveRoll,
                1,
                1,
                1,
                1,
                1,
                1,
                1);

        List<AbstractPlayer> players = new ArrayList<>();
        players.add(createMovingPlayer(Colour.RED, board, ruleEngine, dice, logger, direction));
        players.add(createNoOpPlayer(Colour.GREEN, board, ruleEngine, dice, logger));
        players.add(createNoOpPlayer(Colour.YELLOW, board, ruleEngine, dice, logger));
        players.add(createNoOpPlayer(Colour.BLUE, board, ruleEngine, dice, logger));

        TurnManager turnManager = new TurnManager(players);
        GameEngine engine = new GameEngine(board, ruleEngine, turnManager, logger, 1);

        Piece movingPiece = players.get(0).getPieces().get(0);
        board.placePieceOnStandardPath(movingPiece, movingPieceIndex);
        movingPiece.setDirection(direction);

        AbstractPlayer blockerPlayer = players.get(3);
        List<Piece> blockerPieces = blockerPlayer.getPieces();
        board.placePieceOnStandardPath(blockerPieces.get(0), blockIndex);
        blockerPieces.get(0).setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(blockerPieces.get(1), blockIndex);
        blockerPieces.get(1).setDirection(Direction.CLOCKWISE);

        return new TestContext(engine, logger, movingPiece);
    }

    // Creates the red player used to attempt the move.
    private static AbstractPlayer createMovingPlayer(Colour colour, Board board,
                                                     RuleEngine ruleEngine, Dice dice, GameLogger logger,
                                                     Direction direction) {
        List<Piece> pieces = new ArrayList<>(board.getHomeArea(colour).pieces());
        return new MovingPlayer(colour, pieces, board, ruleEngine, dice,
                () -> direction == Direction.CLOCKWISE ? CoinFace.HEADS : CoinFace.TAILS, logger);
    }

    // Creates the other players that simply wait for their turn.
    private static AbstractPlayer createNoOpPlayer(Colour colour, Board board,
                                                   RuleEngine ruleEngine, Dice dice, GameLogger logger) {
        List<Piece> pieces = new ArrayList<>(board.getHomeArea(colour).pieces());
        return new NoOpPlayer(colour, pieces, board, ruleEngine, dice,
                () -> CoinFace.HEADS, logger);
    }

    // Confirms that a blocked move stops before the opponent group.
    @Test
    void clockwiseMoveStopsBeforeOpponentBlockAndLogsMessage() {
        TestContext context = createContext(
                Direction.CLOCKWISE,
                0,
                3,
                5,
                6,
                5,
                4);

        context.engine.run();

        assertEquals(2, context.movingPiece.getLocation().getIndex());
        int noAlternativeIndex = context.logger.messages.indexOf(
                "Red does not have other pieces in the board to move instead of the blocked piece.");
        int movedBeforeBlockIndex = context.logger.messages.indexOf(
                "Moved the piece to square 2 which is the cell before the block.");
        assertTrue(noAlternativeIndex >= 0);
        assertTrue(movedBeforeBlockIndex > noAlternativeIndex);
        assertFalse(context.logger.messages.contains("Ignoring the throw and moving on to the next player."));
        assertFalse(
                context.logger.messages.stream().anyMatch(message -> message.contains("moves piece R1 from location")));
    }

    @Test
        // Verifies the same blocked-move rule in the opposite direction.
    void counterClockwiseMoveStopsBeforeOpponentBlockAndLogsMessage() {
        TestContext context = createContext(
                Direction.COUNTER_CLOCKWISE,
                20,
                16,
                5,
                6,
                5,
                4);

        context.engine.run();

        assertEquals(17, context.movingPiece.getLocation().getIndex());
        int noAlternativeIndex = context.logger.messages.indexOf(
                "Red does not have other pieces in the board to move instead of the blocked piece.");
        int movedBeforeBlockIndex = context.logger.messages.indexOf(
                "Moved the piece to square 17 which is the cell before the block.");
        assertTrue(noAlternativeIndex >= 0);
        assertTrue(movedBeforeBlockIndex > noAlternativeIndex);
        assertFalse(context.logger.messages.contains("Ignoring the throw and moving on to the next player."));
        assertFalse(
                context.logger.messages.stream().anyMatch(message -> message.contains("moves piece R1 from location")));
    }

    @Test
    void singleOpponentPieceDoesNotActLikeABlockade() {
        Board board = new BoardBuilder().build();
        BlockRule blockRule = new BlockRule(board);

        Piece movingPiece = new Piece(Colour.RED, 1);
        Piece blocker = new Piece(Colour.BLUE, 1);

        board.placePieceOnStandardPath(movingPiece, 2);
        movingPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(blocker, 3);
        blocker.setDirection(Direction.CLOCKWISE);

        assertFalse(blockRule.isBlockedOnPath(movingPiece, 1));
        assertNull(blockRule.findBlockingLocation(movingPiece, 1));
        assertNull(blockRule.findCellBeforeBlock(movingPiece, 1));
    }

    @Test
        // Checks the adjacent-block case where the move is ignored.
    void adjacentOpponentBlockIgnoresTheMoveAndLogsBlockedMessage() {
        TestContext context = createContext(
                Direction.CLOCKWISE,
                2,
                3,
                2,
                6,
                5,
                4);

        context.engine.run();

        assertEquals(2, context.movingPiece.getLocation().getIndex());
        int blockedIndex = context.logger.messages.indexOf(
                "Red piece R1 is blocked from moving from 2 to 3 by Blue piece B1.");
        int noAlternativeIndex = context.logger.messages.indexOf(
                "Red does not have other pieces in the board to move instead of the blocked piece.");
        int ignoredThrowIndex = context.logger.messages.indexOf(
                "Ignoring the throw and moving on to the next player.");
        assertTrue(blockedIndex >= 0);
        assertTrue(noAlternativeIndex > blockedIndex);
        assertTrue(ignoredThrowIndex > noAlternativeIndex);
        assertFalse(
                context.logger.messages.stream().anyMatch(message -> message.contains("moves piece R1 from location")));
    }

    // Tries to move a piece and falls back to no move when blocked.
    private static final class MovingPlayer extends AbstractPlayer {
        private MovingPlayer(Colour colour, List<Piece> pieces, Board board,
                             RuleEngine ruleEngine, Dice dice, CoinToss coinToss, GameLogger logger) {
            super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
        }

        @Override
        protected Command selectMove(int diceValue) {
            for (Piece piece : movablePiecesOnBoard()) {
                Command command = tryStandardMove(piece, diceValue);
                if (!(command instanceof NoMoveCommand)) {
                    return command;
                }
            }
            return new NoMoveCommand();
        }
    }

    // Always skips its turn to keep the test focused on the moving piece.
    private static final class NoOpPlayer extends AbstractPlayer {
        private NoOpPlayer(Colour colour, List<Piece> pieces, Board board,
                           RuleEngine ruleEngine, Dice dice, CoinToss coinToss, GameLogger logger) {
            super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
        }

        @Override
        protected Command selectMove(int diceValue) {
            return new NoMoveCommand();
        }
    }

    // Returns a fixed dice sequence for predictable test turns.
    private static final class SequenceDice implements Dice {
        private final int[] values;
        private int index;

        private SequenceDice(int... values) {
            this.values = values.clone();
        }

        @Override
        public int roll() {
            int value = values[index % values.length];
            index++;
            return value;
        }
    }

    // Keeps the engine messages so the test can assert what happened.
    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }

    // Groups the objects used by each block-movement scenario.
    private record TestContext(GameEngine engine, CapturingGameLogger logger, Piece movingPiece) {
    }
}
