package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.BaseToStartingSquareCommand;
import com.ludot.command.CaptureCommand;
import com.ludot.command.Command;
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

// These tests cover how a piece remembers its entry direction when it leaves base.
class BaseEntryDirectionAssignmentTest {

    // Builds a small test setup with fixed dice and a controlled coin result.
    private static TestContext createContext(CoinFace coinFace) {
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

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        BaseEntryPlayer player = new BaseEntryPlayer(Colour.RED, pieces, board, ruleEngine,
                new FixedDice(6), () -> coinFace, logger);

        return new TestContext(board, movementRule, player, player.getPieces().get(0));
    }

    // Confirms that the first move from base assigns the stored direction.
    @Test
    void baseToStartingSquareMoveHappensBeforeHeadsDirectionAssignment() {
        TestContext context = createContext(CoinFace.HEADS);

        Command command = context.player.takeTurn();

        assertNull(context.piece.getDirection());

        command.execute();

        assertEquals(context.board.getPath().getStartingIndex(Colour.RED), context.piece.getLocation().getIndex());
        assertEquals(Direction.CLOCKWISE, context.piece.getDirection());
        assertEquals(Direction.CLOCKWISE, context.piece.getOriginalDirection());
    }

    @Test
        // Verifies that a later move uses the direction chosen at entry.
    void tailsAssignsCounterClockwiseAndLaterMovementUsesStoredDirection() {
        TestContext context = createContext(CoinFace.TAILS);

        Command command = context.player.takeTurn();
        command.execute();

        assertEquals(context.board.getPath().getStartingIndex(Colour.RED), context.piece.getLocation().getIndex());
        assertEquals(Direction.COUNTER_CLOCKWISE, context.piece.getDirection());
        assertEquals(Direction.COUNTER_CLOCKWISE, context.piece.getOriginalDirection());

        Command laterMove = context.movementRule.commandForStandardMove(context.piece, 2);
        laterMove.execute();

        assertEquals(context.board.getPath().moveCounterClockwise(
                        context.board.getPath().getStartingIndex(Colour.RED), 2),
                context.piece.getLocation().getIndex());
    }

    @Test
        // Ensures returning a piece to base clears the old direction state.
    void returningToBaseResetsCurrentAndOriginalDirection() {
        TestContext context = createContext(CoinFace.HEADS);

        Command command = context.player.takeTurn();
        command.execute();
        context.board.returnPieceToBase(context.piece);

        assertNull(context.piece.getDirection());
        assertNull(context.piece.getOriginalDirection());
    }

    @Test
        // Confirms that a captured piece gets a fresh entry direction on re-entry.
    void capturedPieceGetsFreshCoinTossWhenReEnteringFromBase() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        Piece capturer = new Piece(Colour.RED, 1);
        Piece captured = new Piece(Colour.BLUE, 1);
        int capturedStartIndex = board.getPath().getStartingIndex(Colour.BLUE);
        int capturerStartIndex = board.getPath().moveCounterClockwise(capturedStartIndex, 1);

        board.placePieceOnStandardPath(capturer, capturerStartIndex);
        capturer.setDirection(Direction.CLOCKWISE);

        Command firstEntry = movementRule.commandFromBaseIfAllowed(captured, 6, new SequenceCoinToss(
                CoinFace.HEADS, CoinFace.TAILS));
        assertInstanceOf(BaseToStartingSquareCommand.class, firstEntry);
        assertNull(captured.getDirection());
        firstEntry.execute();

        assertEquals(Direction.CLOCKWISE, captured.getDirection());
        assertEquals(Direction.CLOCKWISE, captured.getOriginalDirection());

        Command moveToCapture = movementRule.commandForStandardMove(capturer, 1);
        moveToCapture.execute();

        Command captureCommand = captureRule.commandIfCaptureAvailable(capturer, captured.getLocation());
        assertInstanceOf(CaptureCommand.class, captureCommand);
        captureCommand.execute();

        assertNull(captured.getDirection());
        assertNull(captured.getOriginalDirection());
        assertTrue(captured.isInBase());

        Command secondEntry = movementRule.commandFromBaseIfAllowed(captured, 6, new SequenceCoinToss(
                CoinFace.TAILS));
        secondEntry.execute();

        assertEquals(Direction.COUNTER_CLOCKWISE, captured.getDirection());
        assertEquals(Direction.COUNTER_CLOCKWISE, captured.getOriginalDirection());
    }

    // Uses the base-entry move path for these tests.
    private static final class BaseEntryPlayer extends AbstractPlayer {
        private BaseEntryPlayer(Colour colour, List<Piece> pieces, Board board,
                                RuleEngine ruleEngine, Dice dice, CoinToss coinToss, GameLogger logger) {
            super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
        }

        @Override
        protected Command selectMove(int diceValue) {
            return tryMoveFromBase(diceValue);
        }
    }

    // Keeps the dice result fixed for predictable test behavior.
    private record FixedDice(int value) implements Dice {

        @Override
        public int roll() {
            return value;
        }
    }

    // Returns the coin faces in the order provided by the test.
    private static final class SequenceCoinToss implements CoinToss {
        private final CoinFace[] faces;
        private int index;

        private SequenceCoinToss(CoinFace... faces) {
            this.faces = faces.clone();
            this.index = 0;
        }

        @Override
        public CoinFace toss() {
            return faces[index++ % faces.length];
        }
    }

    // Swallows test output so the assertions stay easy to read.
    private static final class CapturingGameLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }

    // Groups the test objects needed for each scenario.
    private record TestContext(Board board, MovementRule movementRule, BaseEntryPlayer player, Piece piece) {
    }
}
