package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Location;
import com.ludot.model.MysteryEffect;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.CoinFace;
import com.ludot.random.CoinToss;
import com.ludot.random.Dice;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// These tests cover the two main end conditions of the game loop: a winner and a safety limit.
class GameEngineCompletionTest {

    // Builds a small scenario with a fixed round limit for each test.
    private static GameEngine createEngine(CapturingGameLogger logger,
                                           boolean piecesAlreadyHome, int maxRounds) {
        Board board = new BoardBuilder().build();
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        BlockRule blockRule = new BlockRule(board);
        BonusRollRule bonusRollRule = new BonusRollRule();
        MysteryRule mysteryRule = new MysteryRule(
                board,
                () -> MysteryEffect.ALPHA,
                (previousIndex, occupiedIndices) -> 0,
                logger);
        RuleEngine ruleEngine = new RuleEngine(
                movementRule, captureRule, blockRule, bonusRollRule, mysteryRule, new WinCondition());

        Dice dice = new SequenceDice(1, 2, 3, 4);
        List<AbstractPlayer> players = new ArrayList<>();
        for (Colour colour : List.of(Colour.RED, Colour.GREEN, Colour.YELLOW, Colour.BLUE)) {
            List<Piece> pieces = createPieces(colour, piecesAlreadyHome && colour == Colour.RED);
            players.add(new NoOpPlayer(colour, pieces, board, ruleEngine,
                    dice, () -> CoinFace.HEADS, logger));
        }

        TurnManager turnManager = new TurnManager(players);
        return new GameEngine(board, ruleEngine, turnManager, logger, maxRounds);
    }

    // Creates four pieces for the requested colour, optionally placing them in
    // base.
    private static List<Piece> createPieces(Colour colour, boolean piecesAlreadyHome) {
        Piece piece1 = new Piece(colour, 1);
        Piece piece2 = new Piece(colour, 2);
        Piece piece3 = new Piece(colour, 3);
        Piece piece4 = new Piece(colour, 4);

        if (piecesAlreadyHome) {
            piece1.setLocation(Location.home(colour));
            piece2.setLocation(Location.home(colour));
            piece3.setLocation(Location.home(colour));
            piece4.setLocation(Location.home(colour));
        }

        return new ArrayList<>(List.of(piece1, piece2, piece3, piece4));
    }

    // Confirms that an already-finished player ends the simulation immediately.
    @Test
    void gameEndsWithWinnerAutomatically() {
        CapturingGameLogger logger = new CapturingGameLogger();
        GameEngine engine = createEngine(logger, true, 5);

        engine.run();

        assertTrue(engine.isFinished());
        assertEquals(Colour.RED, engine.getWinner());
        assertEquals(0, engine.getCompletedRoundCount());
        assertTrue(logger.messages.stream().anyMatch(message -> message.equals("Red player wins!!!")));
    }

    @Test
        // Verifies that the safety limit stops the simulation with a clear message.
    void maxRoundsEndsSafelyWithClearMessage() {
        CapturingGameLogger logger = new CapturingGameLogger();
        GameEngine engine = createEngine(logger, false, 1);

        engine.run();

        assertTrue(engine.isFinished());
        assertNull(engine.getWinner());
        assertEquals(1, engine.getCompletedRoundCount());
        assertTrue(logger.messages.stream().anyMatch(message -> message.equals(
                "========================================\nSimulation ended because the safety limit was reached after 1 rounds.\nNo winner was found.\n========================================")));
    }

    // Always skips its turn so the simulation remains predictable.
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

    // Returns a fixed dice sequence to keep turn order stable.
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

    // Stores the simulation messages so the tests can assert the ending.
    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}