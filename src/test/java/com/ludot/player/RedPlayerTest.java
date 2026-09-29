package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.BaseToStartingSquareCommand;
import com.ludot.command.Command;
import com.ludot.command.MoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.random.CoinFace;
import com.ludot.random.Dice;
import com.ludot.random.StandardMysteryCellSpawner;
import com.ludot.random.StandardMysteryEffectSelector;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm how Red reacts to a six in capture and base-entry situations.
class RedPlayerTest {

    private static RuleEngine createRuleEngine(Board board) {
        return new RuleEngine(
                new MovementRule(board),
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                new MysteryRule(board, new StandardMysteryEffectSelector(1L),
                        new StandardMysteryCellSpawner(1L), new SilentLogger()),
                new WinCondition());
    }

    // Red should prefer a capture move over entering the base on a six.
    @Test
    void redDoesNotEnterBaseWhenCaptureWithSixPossible() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> redPieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        Piece red = redPieces.get(0);
        board.placePieceOnStandardPath(red, 0);
        red.setDirection(Direction.CLOCKWISE);

        List<Piece> bluePieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece blue = bluePieces.get(0);
        board.placePieceOnStandardPath(blue, 6);
        blue.setDirection(Direction.CLOCKWISE);

        SequenceDice dice = new SequenceDice(6);
        RedPlayer redPlayer = new RedPlayer(Colour.RED, redPieces, board, ruleEngine, dice,
                () -> CoinFace.HEADS, new SilentLogger());

        Command command = redPlayer.takeTurn();
        assertFalse(command instanceof BaseToStartingSquareCommand);
        assertInstanceOf(MoveCommand.class, command);
        assertEquals(red, ((MoveCommand) command).getPiece());
        command.execute();
        assertEquals(6, red.getLocation().getIndex());
        ruleEngine.captureRule().commandIfCaptureAvailable(red, red.getLocation()).execute();
        assertTrue(blue.isInBase());
        assertEquals(1, red.getCaptureCount());
        assertEquals(3, redPlayer.countPiecesInBase());
    }

    // With no piece on the board, a six should move a Red piece out of base.
    @Test
    void redEntersBaseWhenNoCaptureWithSixPossibleAndNoPiecesOnBoard() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> redPieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());

        SequenceDice dice = new SequenceDice(6);
        RedPlayer redPlayer = new RedPlayer(Colour.RED, redPieces, board, ruleEngine, dice,
                () -> CoinFace.HEADS, new SilentLogger());

        Command command = redPlayer.takeTurn();
        assertInstanceOf(BaseToStartingSquareCommand.class, command);
    }

    // Returns the fixed roll sequence used by these tests.
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

    // Keeps the test output quiet while the move rules are checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
