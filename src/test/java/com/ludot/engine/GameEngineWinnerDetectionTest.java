package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.*;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm the win message appears once and the game stops after the winner is found.
class GameEngineWinnerDetectionTest {

    // Builds a small scenario where Red is already home, so the win condition is
    // triggered immediately.
    private static TestContext createContext() {
        CapturingGameLogger logger = new CapturingGameLogger();
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = new RuleEngine(
                new MovementRule(board),
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                new MysteryRule(board,
                        new FixedMysteryEffectSelector(null),
                        new FixedMysteryCellSpawner(0),
                        logger),
                new WinCondition());

        List<AbstractPlayer> players = new ArrayList<>();
        players.add(createPlayer(Colour.RED, board, ruleEngine, new SequenceDice(6), logger, true));
        players.add(createPlayer(Colour.GREEN, board, ruleEngine, new SequenceDice(1), logger, false));
        players.add(createPlayer(Colour.YELLOW, board, ruleEngine, new SequenceDice(1), logger, false));
        players.add(createPlayer(Colour.BLUE, board, ruleEngine, new SequenceDice(1), logger, false));

        return new TestContext(new GameEngine(board, ruleEngine, new TurnManager(players), logger, 200), logger);
    }

    // Creates a test player and places its pieces at home when the scenario needs
    // an immediate winner.
    private static AbstractPlayer createPlayer(Colour colour, Board board, RuleEngine ruleEngine,
                                               Dice dice, GameLogger logger, boolean piecesAlreadyHome) {
        List<Piece> pieces = new ArrayList<>(board.getHomeArea(colour).pieces());
        if (piecesAlreadyHome) {
            for (Piece piece : pieces) {
                board.removePieceFromCurrentLocation(piece);
                piece.setLocation(com.ludot.model.Location.home(colour));
            }
        }
        return new NoOpPlayer(colour, pieces, board, ruleEngine, dice, new FixedCoinToss(CoinFace.HEADS), logger);
    }

    // Finds the first message containing the given text.
    private static int indexOfContaining(List<String> messages, String text) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    // Confirms the winning message is logged only once.
    @Test
    void winnerMessageAppearsExactlyOnce() {
        TestContext context = createContext();

        context.engine().run();

        long winnerMessages = context.logger().messages().stream()
                .filter(message -> message.contains("wins!!!"))
                .count();
        assertEquals(1L, winnerMessages);
    }

    // Verifies that no further roll messages appear after the winner is announced.
    @Test
    void gameStopsAfterFirstWinner() {
        TestContext context = createContext();

        context.engine().run();

        List<String> messages = context.logger().messages();
        int winnerIndex = indexOfContaining(messages, "wins!!!");
        assertTrue(winnerIndex >= 0);
        for (int i = winnerIndex + 1; i < messages.size(); i++) {
            assertFalse(messages.get(i).contains("player rolled"));
        }
    }

    // Stores the engine and logger used by the tests.
    private record TestContext(GameEngine engine, CapturingGameLogger logger) {
    }

    // Uses a player that never moves so the tests stay focused on win detection.
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

    // Returns the fixed roll sequence used by the tests.
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

    // Stores the engine messages for assertion checks.
    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }

        private List<String> messages() {
            return messages;
        }
    }
}
