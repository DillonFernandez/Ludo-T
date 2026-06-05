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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests cover starting-player selection, tie-breaking, and the round-order message shown at startup.
class GameEngineFirstPlayerSelectionTest {

    // Builds a small startup scenario with fixed dice and a round limit.
    private static TestContext createContext(Dice dice, int maxRounds) {
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

        PlayerFactory playerFactory = new PlayerFactory(board, ruleEngine, dice,
                () -> CoinFace.HEADS, logger);
        List<AbstractPlayer> players = new ArrayList<>();
        for (Colour colour : List.of(Colour.RED, Colour.GREEN, Colour.YELLOW, Colour.BLUE)) {
            players.add(playerFactory.createPlayer(colour, new ArrayList<>(board.getHomeArea(colour).pieces())));
        }

        TurnManager turnManager = new TurnManager(players);
        GameEngine engine = new GameEngine(board, ruleEngine, turnManager, logger, maxRounds);
        return new TestContext(engine, turnManager, logger);
    }

    // Confirms that the highest roll chooses the starting player.
    @Test
    void selectStartingPlayerRollsAllPlayersAndChoosesHighest() {
        TestContext context = createContext(new SequenceDice(2, 5, 3, 1), 1);

        AbstractPlayer selected = context.engine.selectStartingPlayer();

        assertEquals(Colour.GREEN, selected.getColour());
        assertEquals(Colour.GREEN, context.turnManager.getCurrentPlayer().getColour());
        assertEquals(List.of(
                        "Red rolls 2",
                        "Green rolls 5",
                        "Yellow rolls 3",
                        "Blue rolls 1",
                        "Green player has the highest roll and will begin the game."),
                context.logger.messages);
    }

    @Test
        // Verifies that a tie is resolved by rerolling only the tied players.
    void selectStartingPlayerRerollsOnlyTiedPlayersUntilSingleWinnerExists() {
        TestContext context = createContext(new SequenceDice(6, 6, 2, 1, 3, 5), 1);

        AbstractPlayer selected = context.engine.selectStartingPlayer();

        assertEquals(Colour.GREEN, selected.getColour());
        assertEquals(Colour.GREEN, context.turnManager.getCurrentPlayer().getColour());
        assertEquals(List.of(
                        "Red rolls 6",
                        "Green rolls 6",
                        "Yellow rolls 2",
                        "Blue rolls 1",
                        "Red rolls 3",
                        "Green rolls 5",
                        "Green player has the highest roll and will begin the game."),
                context.logger.messages);
    }

    @Test
        // Checks that startup output announces the chosen starter before round 1
        // begins.
    void runPrintsRoundOrderStartingFromSelectedPlayer() {
        TestContext context = createContext(new SequenceDice(1, 2, 3, 4, 1, 1, 1, 1), 1);

        context.engine.run();

        assertEquals(List.of(
                        "========================================\n" +
                                "          LUDO-T SIMULATION\n" +
                                "========================================",
                        "The red player has four (04) pieces named R1, R2, R3, and R4.",
                        "The green player has four (04) pieces named G1, G2, G3, and G4.",
                        "The yellow player has four (04) pieces named Y1, Y2, Y3, and Y4.",
                        "The blue player has four (04) pieces named B1, B2, B3, and B4.",
                        "Red rolls 1",
                        "Green rolls 2",
                        "Yellow rolls 3",
                        "Blue rolls 4",
                        "Blue player has the highest roll and will begin the game."),
                context.logger.messages.subList(0, 10));

        int roundHeaderIndex = context.logger.messages.indexOf("---------- Round 1 ----------");
        assertTrue(roundHeaderIndex > 0);
        int highestRollIndex = context.logger.messages
                .indexOf("Blue player has the highest roll and will begin the game.");
        int orderIndex = context.logger.messages
                .indexOf("The order of a single round is Blue, Red, Green, and Yellow.");

        assertTrue(highestRollIndex >= 0);
        assertTrue(orderIndex >= 0);
        assertTrue(highestRollIndex < orderIndex);
        assertTrue(orderIndex < roundHeaderIndex);

        int redRollIndex = context.logger.messages.indexOf("Red rolls 1");
        int greenRollIndex = context.logger.messages.indexOf("Green rolls 2");
        int yellowRollIndex = context.logger.messages.indexOf("Yellow rolls 3");
        int blueRollIndex = context.logger.messages.indexOf("Blue rolls 4");

        assertTrue(redRollIndex < roundHeaderIndex);
        assertTrue(greenRollIndex < roundHeaderIndex);
        assertTrue(yellowRollIndex < roundHeaderIndex);
        assertTrue(blueRollIndex < roundHeaderIndex);
    }

    // Groups the objects needed for each startup test.
    private record TestContext(GameEngine engine, TurnManager turnManager, CapturingGameLogger logger) {
    }

    // Returns a fixed rolling sequence for deterministic tests.
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

    // Stores the startup messages so the assertions can inspect them.
    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}