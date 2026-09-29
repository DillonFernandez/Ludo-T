package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.factory.PlayerFactory;
import com.ludot.model.Colour;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.*;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests verify the log output for base entry and for blocked base exits.
class GameEngineStartingPointLogTest {

    // Builds the small four-player scenario used by the log tests.
    private static GameEngine createEngine(CapturingGameLogger logger, Dice dice) {
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

        PlayerFactory playerFactory = new PlayerFactory(board, ruleEngine, dice,
                () -> CoinFace.HEADS, logger);
        List<AbstractPlayer> players = new ArrayList<>();
        for (Colour colour : List.of(Colour.RED, Colour.GREEN, Colour.YELLOW, Colour.BLUE)) {
            players.add(playerFactory.createPlayer(colour, new ArrayList<>(board.getHomeArea(colour).pieces())));
        }

        TurnManager turnManager = new TurnManager(players);
        return new GameEngine(board, ruleEngine, turnManager, logger, 1);
    }

    // Creates one player for the base-exit rule check.
    private static AbstractPlayer createPlayer(Board board, MovementRule movementRule, Colour colour) {
        CapturingGameLogger logger = new CapturingGameLogger();
        RuleEngine ruleEngine = new RuleEngine(
                movementRule,
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                new MysteryRule(board, new StandardMysteryEffectSelector(1L), new StandardMysteryCellSpawner(1L),
                        logger),
                new WinCondition());
        PlayerFactory playerFactory = new PlayerFactory(board, ruleEngine,
                new SequenceDice(5), () -> CoinFace.HEADS, logger);
        return playerFactory.createPlayer(colour, new ArrayList<>(board.getHomeArea(colour).pieces()));
    }

    // Finds the first message containing the given text from a starting index.
    private static int indexOfContaining(List<String> messages, String text, int startIndex) {
        for (int i = Math.max(0, startIndex); i < messages.size(); i++) {
            if (messages.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    // Confirms the move message and the follow-up count message appear in order.
    @Test
    void baseToStartingPointMoveLogsPieceNameAndCounts() {
        CapturingGameLogger logger = new CapturingGameLogger();
        GameEngine engine = createEngine(logger, new SequenceDice(6, 1, 1, 1));

        engine.run();

        int moveIndex = logger.messages.indexOf("Red player moves piece R1 to the starting point.");
        int countIndex = logger.messages.indexOf(
                "Red player now has 1/4 on pieces on the board and 3/4 pieces on the base.");
        int nextRollIndex = indexOfContaining(logger.messages, "player rolled", countIndex + 1);

        assertEquals(moveIndex + 1, countIndex);
        assertTrue(moveIndex >= 0);
        assertTrue(countIndex > moveIndex);
        assertTrue(nextRollIndex > countIndex);
    }

    // A non-six roll must not allow a piece to leave the base.
    @Test
    void nonSixRollStillCannotMovePieceFromBase() {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        AbstractPlayer redPlayer = createPlayer(board, movementRule, Colour.RED);

        assertFalse(movementRule.canMoveFromBase(redPlayer.firstPieceInBase(), 5));
    }

    // Returns the fixed roll sequence used by the tests.
    private static final class SequenceDice implements Dice {
        private final int[] values;
        private int index;

        private SequenceDice(int... values) {
            this.values = values.clone();
            this.index = 0;
        }

        @Override
        public int roll() {
            int value = values[index % values.length];
            index++;
            return value;
        }
    }

    // Stores the logged messages for assertion checks.
    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}