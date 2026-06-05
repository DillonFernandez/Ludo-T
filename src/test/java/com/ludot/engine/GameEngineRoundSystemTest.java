package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Location;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.player.RedPlayer;
import com.ludot.random.*;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests cover round timing, bonus rolls, and the special three-six rule in the game loop.
class GameEngineRoundSystemTest {

    // Builds a small scenario with controlled dice, capture behavior, and home
    // placement.
    private static TestContext createContext(Dice dice, int maxRounds,
                                             boolean useCaptureRedPlayer, boolean piecesAlreadyHome) {
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

        List<AbstractPlayer> players = new ArrayList<>();
        if (useCaptureRedPlayer) {
            players.add(createCaptureRedPlayer(board, ruleEngine, dice, logger, piecesAlreadyHome));
        } else {
            players.add(createNoOpPlayer(Colour.RED, board, ruleEngine, dice, logger, piecesAlreadyHome));
        }
        players.add(createNoOpPlayer(Colour.GREEN, board, ruleEngine, dice, logger, piecesAlreadyHome));
        players.add(createNoOpPlayer(Colour.YELLOW, board, ruleEngine, dice, logger, piecesAlreadyHome));
        players.add(createNoOpPlayer(Colour.BLUE, board, ruleEngine, dice, logger, piecesAlreadyHome));

        TurnManager turnManager = new TurnManager(players);
        GameEngine engine = new GameEngine(board, ruleEngine, turnManager, logger, maxRounds);

        if (useCaptureRedPlayer) {
            Piece redPiece = players.get(0).getPieces().get(0);
            Piece bluePiece = players.get(3).getPieces().get(0);
            board.placePieceOnStandardPath(redPiece, 0);
            redPiece.setDirection(Direction.CLOCKWISE);
            board.placePieceOnStandardPath(bluePiece, 1);
            bluePiece.setDirection(Direction.CLOCKWISE);
        }

        return new TestContext(engine, logger);
    }

    // Builds a two-piece blockade scenario used by the block-break tests.
    private static BlockadeContext createBlockadeContext(Dice dice, int maxRounds) {
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

        List<AbstractPlayer> players = new ArrayList<>();
        players.add(createNoOpPlayer(Colour.RED, board, ruleEngine, dice, logger, false));
        players.add(createNoOpPlayer(Colour.GREEN, board, ruleEngine, dice, logger, false));
        players.add(createNoOpPlayer(Colour.YELLOW, board, ruleEngine, dice, logger, false));
        players.add(createNoOpPlayer(Colour.BLUE, board, ruleEngine, dice, logger, false));

        TurnManager turnManager = new TurnManager(players);
        GameEngine engine = new GameEngine(board, ruleEngine, turnManager, logger, maxRounds);

        Piece stayPiece = players.get(0).getPieces().get(0);
        Piece movedPiece = players.get(0).getPieces().get(1);

        stayPiece.setDirection(Direction.CLOCKWISE);
        movedPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(stayPiece, 14);
        board.placePieceOnStandardPath(movedPiece, 14);
        movedPiece.setDirection(Direction.COUNTER_CLOCKWISE);

        return new BlockadeContext(engine, logger, stayPiece, movedPiece, null);
    }

    // Builds a larger blockade scenario to test proportional movement splitting.
    private static BlockadeContext createThreePieceBlockadeContext(Dice dice, int maxRounds) {
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

        List<AbstractPlayer> players = new ArrayList<>();
        players.add(createNoOpPlayer(Colour.RED, board, ruleEngine, dice, logger, false));
        players.add(createNoOpPlayer(Colour.GREEN, board, ruleEngine, dice, logger, false));
        players.add(createNoOpPlayer(Colour.YELLOW, board, ruleEngine, dice, logger, false));
        players.add(createNoOpPlayer(Colour.BLUE, board, ruleEngine, dice, logger, false));

        TurnManager turnManager = new TurnManager(players);
        GameEngine engine = new GameEngine(board, ruleEngine, turnManager, logger, maxRounds);

        Piece stayPiece = players.get(0).getPieces().get(0);
        Piece movedPiece = players.get(0).getPieces().get(1);
        Piece movedPieceTwo = players.get(0).getPieces().get(2);
        Piece sparePiece = players.get(0).getPieces().get(3);

        stayPiece.setDirection(Direction.CLOCKWISE);
        movedPiece.setDirection(Direction.CLOCKWISE);
        movedPieceTwo.setDirection(Direction.CLOCKWISE);
        sparePiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(stayPiece, 14);
        board.placePieceOnStandardPath(movedPiece, 14);
        board.placePieceOnStandardPath(movedPieceTwo, 14);
        board.placePieceOnStandardPath(sparePiece, 20);

        return new BlockadeContext(engine, logger, stayPiece, movedPiece, movedPieceTwo);
    }

    // Creates a passive player, optionally starting them in base for special cases.
    private static AbstractPlayer createNoOpPlayer(Colour colour, Board board, RuleEngine ruleEngine,
                                                   Dice dice, GameLogger logger, boolean piecesAlreadyHome) {
        List<Piece> pieces = new ArrayList<>(board.getHomeArea(colour).pieces());
        if (piecesAlreadyHome) {
            for (Piece piece : pieces) {
                piece.setLocation(Location.home(colour));
            }
        }
        return new NoOpPlayer(colour, pieces, board, ruleEngine, dice, () -> CoinFace.HEADS, logger);
    }

    // Creates the red player used to test capture bonus behavior.
    private static AbstractPlayer createCaptureRedPlayer(Board board, RuleEngine ruleEngine,
                                                         Dice dice, GameLogger logger, boolean piecesAlreadyHome) {
        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        if (piecesAlreadyHome) {
            for (Piece piece : pieces) {
                piece.setLocation(Location.home(Colour.RED));
            }
        }
        return new RedPlayer(Colour.RED, pieces, board, ruleEngine, dice, () -> CoinFace.HEADS, logger);
    }

    // Small message helpers used by the assertions.
    private static int indexOf(List<String> messages, String text) {
        return messages.indexOf(text);
    }

    private static int indexOfContaining(List<String> messages, String text) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfContaining(List<String> messages, String text, int fromIndex) {
        for (int i = Math.max(0, fromIndex); i < messages.size(); i++) {
            if (messages.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    private static int countContaining(List<String> messages, String text) {
        int count = 0;
        for (String message : messages) {
            if (message.contains(text)) {
                count++;
            }
        }
        return count;
    }

    private static int countExact(List<String> messages, String text) {
        int count = 0;
        for (String message : messages) {
            if (message.equals(text)) {
                count++;
            }
        }
        return count;
    }

    // Confirms that a normal turn sequence advances the round only after four
    // turns.
    @Test
    void roundCountIncreasesOnlyAfterFourNormalTurns() {
        TestContext context = createContext(new SequenceDice(5, 4, 3, 2, 5, 5, 5, 5, 5, 5, 5, 5), 2,
                false, false);

        context.engine.run();

        int roundTwoIndex = indexOf(context.logger.messages, "---------- Round 2 ----------");
        int blueRollIndex = indexOf(context.logger.messages, "Blue player rolled 5.");
        int yellowRollIndex = indexOf(context.logger.messages, "Yellow player rolled 5.");

        assertTrue(roundTwoIndex > blueRollIndex);
        assertTrue(roundTwoIndex > yellowRollIndex);
        assertFalse(context.logger.messages.stream()
                .anyMatch(message -> message.contains("gets another roll")));
    }

    @Test
        // Verifies that a six grants a bonus roll without advancing the round.
    void sixBonusDoesNotAdvanceRound() {
        TestContext context = createContext(new SequenceDice(5, 4, 3, 2, 1, 6, 1, 1, 1, 1, 1), 1,
                false, false);

        context.engine.run();

        int sixIndex = indexOfContaining(context.logger.messages, "player rolled 6.");
        int bonusMessageIndex = indexOfContaining(context.logger.messages,
                "gets another roll as he/she rolled 6.");
        int thirdSixMessageIndex = indexOf(context.logger.messages,
                "Red player rolled three consecutively, ignoring the throw and moving on to the next player.");
        int bonusRollIndex = indexOfContaining(context.logger.messages, "player rolled 1.", sixIndex + 1);
        int nextRollIndex = indexOfContaining(context.logger.messages, "player rolled 1.", bonusRollIndex + 1);

        assertTrue(sixIndex >= 0);
        assertTrue(bonusMessageIndex > sixIndex);
        assertEquals(-1, thirdSixMessageIndex);
        assertTrue(bonusRollIndex > bonusMessageIndex);
        assertTrue(nextRollIndex > bonusRollIndex);
    }

    @Test
        // Checks that the third consecutive six produces one clear override message.
    void thirdConsecutiveSixMessageIsPrintedExactlyOnceWithoutBlock() {
        TestContext context = createContext(new SequenceDice(5, 4, 3, 2, 6, 6, 6, 1, 1, 1), 1,
                false, false);

        context.engine.run();

        int firstSixIndex = indexOfContaining(context.logger.messages, "player rolled 6.");
        int secondSixIndex = indexOfContaining(context.logger.messages, "player rolled 6.", firstSixIndex + 1);
        int thirdSixIndex = indexOfContaining(context.logger.messages, "player rolled 6.", secondSixIndex + 1);
        int ignoreMessageIndex = indexOf(context.logger.messages,
                "Red player rolled three consecutively, ignoring the throw and moving on to the next player.");
        int nextRollIndex = indexOfContaining(context.logger.messages, "player rolled 1.", thirdSixIndex + 1);

        assertTrue(firstSixIndex >= 0);
        assertTrue(secondSixIndex > firstSixIndex);
        assertTrue(thirdSixIndex > secondSixIndex);
        assertTrue(ignoreMessageIndex > thirdSixIndex);
        assertTrue(nextRollIndex > ignoreMessageIndex);
        assertEquals(1, countExact(context.logger.messages,
                "Red player rolled three consecutively, ignoring the throw and moving on to the next player."));
    }

    @Test
        // Verifies that two sixes are not enough to trigger the special rule.
    void thirdConsecutiveSixMessageIsNotPrintedForTwoSixes() {
        TestContext context = createContext(new SequenceDice(5, 4, 3, 2, 6, 6, 1, 1, 1, 1), 1,
                false, false);

        context.engine.run();

        assertEquals(-1, indexOf(context.logger.messages,
                "Red player rolled three consecutively, ignoring the throw and moving on to the next player."));
    }

    @Test
        // Confirms that a capture bonus also does not advance the round early.
    void captureBonusDoesNotAdvanceRound() {
        TestContext context = createContext(new SequenceDice(5, 4, 3, 2, 1, 2, 1, 1, 1, 1), 1,
                true, false);

        context.engine.run();

        int captureIndex = indexOf(context.logger.messages,
                "Red piece R1 lands on square 1, captures Blue piece B1, and returns it to the base.");
        int countIndex = indexOf(context.logger.messages,
                "Red player now has 1/4 on pieces on the board and 3/4 pieces on the base.");
        int bonusMessageIndex = indexOf(context.logger.messages,
                "Red player gets another roll as he/she captured Blue piece B1.");
        int bonusRollIndex = indexOfContaining(context.logger.messages, "player rolled 2.");
        int nextRollIndex = indexOfContaining(context.logger.messages, "player rolled 1.", bonusRollIndex + 1);

        assertTrue(captureIndex >= 0);
        assertTrue(countIndex > captureIndex);
        assertTrue(bonusMessageIndex > captureIndex);
        assertTrue(bonusRollIndex > bonusMessageIndex);
        assertTrue(bonusRollIndex > captureIndex);
        assertTrue(nextRollIndex > bonusRollIndex);
    }

    @Test
        // Verifies the special blockade-breaking case for three consecutive sixes.
    void thirdConsecutiveSixBreaksBlockadeInsteadOfBeingIgnored() {
        BlockadeContext context = createBlockadeContext(new SequenceDice(5, 4, 3, 2, 6, 6, 6, 1, 1, 1), 1);

        context.engine.run();

        assertEquals(14, context.stayPiece.getLocation().getIndex());
        assertEquals(20, context.movedPiece.getLocation().getIndex());
        assertEquals(Direction.CLOCKWISE, context.movedPiece.getDirection());

        int thirdSixIndex = indexOfContaining(context.logger.messages, "player rolled 6.",
                indexOfContaining(context.logger.messages, "player rolled 6.",
                        indexOfContaining(context.logger.messages, "player rolled 6.") + 1) + 1);
        int nextRollIndex = indexOfContaining(context.logger.messages, "player rolled 1.", thirdSixIndex + 1);

        int blockBreakIndex = indexOf(context.logger.messages,
                "Red player rolled three consecutively and break the block removing the piece "
                        + context.movedPiece.getName()
                        + " from the block, and piece "
                        + context.movedPiece.getName()
                        + " moves 6 units in clockwise direction.");
        assertTrue(blockBreakIndex >= 0);
        assertEquals(-1, indexOf(context.logger.messages,
                "Red player rolled three consecutively, ignoring the throw and moving on to the next player."));
        assertTrue(nextRollIndex > thirdSixIndex);
    }

    @Test
        // Checks that a forced block break distributes movement across the blocked
        // pieces.
    void forcedBlockBreakSplitsMovementProportionallyForLargerBlocks() {
        BlockadeContext context = createThreePieceBlockadeContext(
                new SequenceDice(5, 4, 3, 2, 6, 6, 6, 1, 1, 1), 1);

        context.engine.run();

        assertEquals(14, context.stayPiece.getLocation().getIndex());
        assertEquals(17, context.movedPiece.getLocation().getIndex());
        assertEquals(17, context.movedPieceTwo.getLocation().getIndex());
    }

    @Test
        // Confirms that round-end status includes piece locations and mystery-cell
        // reporting.
    void afterRoundStatusIsPrintedAfterEachCompletedRound() {
        TestContext context = createContext(new SequenceDice(6, 5, 4, 3, 6, 1, 1, 1, 1, 1, 1, 1), 2,
                true, false);

        context.engine.run();

        assertEquals(2, countContaining(context.logger.messages, "Location of pieces Red"));
        assertEquals(2, countContaining(context.logger.messages, "Location of pieces Green"));
        assertEquals(2, countContaining(context.logger.messages, "Location of pieces Yellow"));
        assertEquals(2, countContaining(context.logger.messages, "Location of pieces Blue"));
        assertTrue(context.logger.messages.stream().anyMatch(message -> message.equals(
                "============================\nLocation of pieces Red\n============================")));
        assertTrue(context.logger.messages.stream().anyMatch(message -> message.startsWith("Piece R1 -> ")));
        assertTrue(context.logger.messages.stream().anyMatch(message -> message.contains("The mystery cell is at")));
    }

    @Test
        // Verifies that mystery-cell output appears only after completed rounds.
    void mysteryCellTimingStillUsesCompletedRounds() {
        TestContext context = createContext(new SequenceDice(6, 5, 4, 3, 6, 1, 1, 1, 1, 1, 1, 1), 2,
                true, false);

        context.engine.run();

        int roundOneIndex = indexOf(context.logger.messages, "---------- Round 1 ----------");
        int roundTwoIndex = indexOf(context.logger.messages, "---------- Round 2 ----------");
        int mysteryStatusIndex = indexOfContaining(context.logger.messages, "The mystery cell is at");

        assertTrue(roundOneIndex >= 0);
        assertTrue(roundTwoIndex > roundOneIndex);
        assertTrue(mysteryStatusIndex > roundTwoIndex);
    }

    private record TestContext(GameEngine engine, CapturingGameLogger logger) {
    }

    private record BlockadeContext(GameEngine engine, CapturingGameLogger logger,
                                   Piece stayPiece, Piece movedPiece, Piece movedPieceTwo) {
    }

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

    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}