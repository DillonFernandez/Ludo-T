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

// These tests cover blockade capture behavior and the rules that decide when a capture is allowed.
class GameEngineBlockadeCaptureTest {

    // Builds a small scenario with a controlled blockade size for each test.
    private static TestContext createContext(int blueBlockSize) {
        CapturingGameLogger logger = new CapturingGameLogger();
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        BlockRule blockRule = new BlockRule(board);
        BonusRollRule bonusRollRule = new BonusRollRule();
        MysteryEffectSelector mysteryEffectSelector = new StandardMysteryEffectSelector(1L);
        MysteryCellSpawner mysteryCellSpawner = new StandardMysteryCellSpawner(1L);
        MysteryRule mysteryRule = new MysteryRule(board, mysteryEffectSelector, mysteryCellSpawner, logger);
        RuleEngine ruleEngine = new RuleEngine(movementRule, captureRule, blockRule,
                bonusRollRule, mysteryRule, new WinCondition());

        List<AbstractPlayer> players = new ArrayList<>();
        players.add(new BlockPlayer(Colour.RED, new ArrayList<>(board.getHomeArea(Colour.RED).pieces()),
                board, ruleEngine, new SequenceDice(6, 5, 4, 3, 4, 1, 1, 1, 1, 1),
                () -> CoinFace.HEADS, logger));
        players.add(new NoOpPlayer(Colour.GREEN, new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces()),
                board, ruleEngine, new SequenceDice(1, 1, 1, 1), () -> CoinFace.HEADS, logger));
        players.add(new NoOpPlayer(Colour.YELLOW, new ArrayList<>(board.getHomeArea(Colour.YELLOW).pieces()),
                board, ruleEngine, new SequenceDice(1, 1, 1, 1), () -> CoinFace.HEADS, logger));
        players.add(new NoOpPlayer(Colour.BLUE, new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces()),
                board, ruleEngine, new SequenceDice(1, 1, 1, 1), () -> CoinFace.HEADS, logger));

        GameEngine engine = new GameEngine(board, ruleEngine, new TurnManager(players), logger, 1);

        Piece redOne = players.get(0).getPieces().get(0);
        Piece redTwo = players.get(0).getPieces().get(1);
        board.placePieceOnStandardPath(redOne, 10);
        redOne.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(redTwo, 10);
        redTwo.setDirection(Direction.CLOCKWISE);

        Piece blueOne = players.get(3).getPieces().get(0);
        Piece blueTwo = players.get(3).getPieces().get(1);
        board.placePieceOnStandardPath(blueOne, 12);
        blueOne.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(blueTwo, 12);
        blueTwo.setDirection(Direction.CLOCKWISE);

        Piece blueThree = null;
        if (blueBlockSize == 3) {
            blueThree = players.get(3).getPieces().get(2);
            board.placePieceOnStandardPath(blueThree, 12);
            blueThree.setDirection(Direction.CLOCKWISE);
        }

        return new TestContext(engine, logger, board, redOne, redTwo, blueOne, blueTwo, blueThree);
    }

    // Confirms that equal-sized blockades capture each other and reset the captured
    // pieces.
    @Test
    void sameSizedBlockadeCapturesOpponentBlockadeAndResetsCapturedPieces() {
        TestContext context = createContext(2);

        context.engine.run();

        assertEquals(12, context.redOne.getLocation().getIndex());
        assertEquals(12, context.redTwo.getLocation().getIndex());
        assertEquals(context.redOne.getLocation(), context.redTwo.getLocation());
        assertEquals(1, context.redOne.getCaptureCount());
        assertEquals(1, context.redTwo.getCaptureCount());

        assertTrue(context.blueOne.isInBase());
        assertTrue(context.blueTwo.isInBase());
        assertEquals(0, context.blueOne.getCaptureCount());
        assertEquals(0, context.blueTwo.getCaptureCount());
        assertNull(context.blueOne.getDirection());
        assertNull(context.blueTwo.getDirection());
        assertNull(context.blueOne.getOriginalDirection());
        assertNull(context.blueTwo.getOriginalDirection());

        assertEquals(2, context.board.getStandardCell(12).getPieces().size());
        assertTrue(context.board.getStandardCell(12).isBlocked());
        assertTrue(context.logger.messages.contains(
                "Red block captures Blue block on square 12. Blue block pieces are returned to base."));
    }

    @Test
        // Verifies that different-sized blockades do not capture each other.
    void differentSizedBlockadesDoNotCaptureEachOther() {
        TestContext context = createContext(3);

        context.engine.run();

        assertFalse(context.blueOne.isInBase());
        assertFalse(context.blueTwo.isInBase());
        assertFalse(context.blueThree.isInBase());
        assertEquals(0, context.redOne.getCaptureCount());
        assertEquals(0, context.redTwo.getCaptureCount());
        assertFalse(context.logger.messages.contains(
                "Red block captures Blue block on square 12. Blue block pieces are returned to base."));
    }

    // Moves the blockade once and then stops so the test sequence stays
    // predictable.
    private static final class BlockPlayer extends AbstractPlayer {
        private boolean moved;

        private BlockPlayer(Colour colour, List<Piece> pieces, Board board, RuleEngine ruleEngine,
                            Dice dice, CoinToss coinToss, GameLogger logger) {
            super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
        }

        @Override
        protected Command selectMove(int diceValue) {
            if (moved) {
                return new NoMoveCommand();
            }
            moved = true;
            for (Piece piece : movablePiecesOnBoard()) {
                Command command = tryStandardMove(piece, diceValue);
                if (!(command instanceof NoMoveCommand)) {
                    return command;
                }
            }
            return new NoMoveCommand();
        }
    }

    // Skips turns so the test remains focused on the capture interaction.
    private static final class NoOpPlayer extends AbstractPlayer {
        private NoOpPlayer(Colour colour, List<Piece> pieces, Board board, RuleEngine ruleEngine,
                           Dice dice, CoinToss coinToss, GameLogger logger) {
            super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
        }

        @Override
        protected Command selectMove(int diceValue) {
            return new NoMoveCommand();
        }
    }

    // Returns a fixed dice sequence to drive the scenario in a controlled order.
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

    // Records messages so the tests can assert the capture outcome.
    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }

    // Groups the objects used by each blockade-capture scenario.
    private record TestContext(GameEngine engine, CapturingGameLogger logger, Board board,
                               Piece redOne, Piece redTwo, Piece blueOne, Piece blueTwo, Piece blueThree) {
    }
}