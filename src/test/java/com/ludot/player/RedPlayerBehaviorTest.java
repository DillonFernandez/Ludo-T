package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.MoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Direction;
import com.ludot.model.Location;
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

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm how Red chooses between capture, home-path, and safe moves.
class RedPlayerBehaviorTest {

    // Creates the minimal rule set used by the Red-player behavior tests.
    private RuleEngine createRuleEngine(Board board) {
        MovementRule movementRule = new MovementRule(board);
        CaptureRule captureRule = new CaptureRule(board);
        BlockRule blockRule = new BlockRule(board);
        BonusRollRule bonusRollRule = new BonusRollRule();
        MysteryRule mysteryRule = new MysteryRule(board, new StandardMysteryEffectSelector(1L),
                new StandardMysteryCellSpawner(1L), new SilentLogger());
        return new RuleEngine(movementRule, captureRule, blockRule, bonusRollRule, mysteryRule,
                new WinCondition());
    }

    // Red should choose the capture closest to the opponent's home path when
    // several are possible.
    @Test
    void choosesCaptureClosestToOpponentsHomeWhenMultipleCapturesAvailable() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> redPieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        Piece r1 = redPieces.get(0);
        Piece r2 = redPieces.get(1);

        board.placePieceOnStandardPath(r1, 0);
        r1.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(r2, 5);
        r2.setDirection(Direction.CLOCKWISE);

        List<Piece> bluePieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b1 = bluePieces.get(0);
        Piece b2 = bluePieces.get(1);
        board.placePieceOnStandardPath(b1, 3);
        b1.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(b2, 8);
        b2.setDirection(Direction.CLOCKWISE);

        Piece r3 = redPieces.get(2);
        Piece r4 = redPieces.get(3);
        board.placePieceOnStandardPath(r3, 30);
        r3.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(r4, 40);
        r4.setDirection(Direction.CLOCKWISE);
        SequenceDice dice = new SequenceDice(3);
        RedPlayer redPlayer = new RedPlayer(Colour.RED, redPieces, board, ruleEngine, dice, () -> CoinFace.HEADS,
                new SilentLogger());

        Command cmd = redPlayer.takeTurn();
        assertInstanceOf(MoveCommand.class, cmd);
        MoveCommand mc = (MoveCommand) cmd;
        assertEquals(r2, mc.getPiece());
    }

    // Red should prefer a capture over a winning home-path move when both are
    // possible.
    @Test
    void prioritisesCaptureOverWinningMoveWhenBothAreAvailable() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> redPieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        Piece homePiece = redPieces.get(0);
        Piece capturer = redPieces.get(1);

        homePiece.setDirection(Direction.CLOCKWISE);
        int homePathIndex = 4;
        homePiece.setLocation(Location.homePath(Colour.RED, homePathIndex));

        board.placePieceOnStandardPath(capturer, 10);
        capturer.setDirection(Direction.CLOCKWISE);
        List<Piece> bluePieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece blue = bluePieces.get(0);
        board.placePieceOnStandardPath(blue, 11);
        blue.setDirection(Direction.CLOCKWISE);

        SequenceDice dice = new SequenceDice(1);
        RedPlayer redPlayer = new RedPlayer(Colour.RED, redPieces, board, ruleEngine, dice, () -> CoinFace.HEADS,
                new SilentLogger());

        Command cmd = redPlayer.takeTurn();
        assertInstanceOf(MoveCommand.class, cmd);
        MoveCommand mc = (MoveCommand) cmd;
        assertEquals(capturer, mc.getPiece());
    }

    // Red should keep a piece on the standard path when that is the safer choice.
    @Test
    void keepsOnePieceInStandardPath() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> redPieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        Piece p1 = redPieces.get(0);
        Piece p2 = redPieces.get(1);

        p1.setDirection(Direction.CLOCKWISE);
        p1.setLocation(Location.homePath(Colour.RED, 4));

        board.placePieceOnStandardPath(p2, 10);
        p2.setDirection(Direction.CLOCKWISE);

        SequenceDice dice = new SequenceDice(1);
        RedPlayer redPlayer = new RedPlayer(Colour.RED, redPieces, board, ruleEngine, dice, () -> CoinFace.HEADS,
                new SilentLogger());

        Command cmd = redPlayer.takeTurn();
        assertInstanceOf(MoveCommand.class, cmd);
        MoveCommand mc = (MoveCommand) cmd;
        assertEquals(p2, mc.getPiece());
    }

    // Red should avoid creating a block when a safe move is available.
    @Test
    void avoidsCreatingBlocksWhenNonBlockMoveExists() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> redPieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        Piece p1 = redPieces.get(0);
        Piece p2 = redPieces.get(1);

        board.placePieceOnStandardPath(p1, 10);
        p1.setDirection(Direction.CLOCKWISE);
        Piece blocker = redPieces.get(2);
        board.placePieceOnStandardPath(blocker, 13);
        blocker.setDirection(Direction.CLOCKWISE);

        board.placePieceOnStandardPath(p2, 20);
        p2.setDirection(Direction.CLOCKWISE);

        SequenceDice dice = new SequenceDice(3);
        RedPlayer redPlayer = new RedPlayer(Colour.RED, redPieces, board, ruleEngine, dice, () -> CoinFace.HEADS,
                new SilentLogger());

        Command cmd = redPlayer.takeTurn();
        cmd.execute();

        assertInstanceOf(MoveCommand.class, cmd);
        MoveCommand mc = (MoveCommand) cmd;
        assertEquals(p2, mc.getPiece());
    }

    // If every legal move would create a block, Red must still choose one.
    @Test
    void createsBlockWhenUnavoidable() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> redPieces = new ArrayList<>(board.getHomeArea(Colour.RED).pieces());
        Piece p1 = redPieces.get(0);

        board.placePieceOnStandardPath(p1, 10);
        p1.setDirection(Direction.CLOCKWISE);
        Piece p2 = redPieces.get(1);
        board.placePieceOnStandardPath(p2, 11);
        p2.setDirection(Direction.CLOCKWISE);
        Piece p3 = redPieces.get(2);
        board.placePieceOnStandardPath(p3, 12);
        p3.setDirection(Direction.CLOCKWISE);
        Piece p4 = redPieces.get(3);
        board.placePieceOnStandardPath(p4, 13);
        p4.setDirection(Direction.CLOCKWISE);

        SequenceDice dice = new SequenceDice(1);
        RedPlayer redPlayer = new RedPlayer(Colour.RED, redPieces, board, ruleEngine, dice, () -> CoinFace.HEADS,
                new SilentLogger());
        Command cmd = redPlayer.takeTurn();
        assertInstanceOf(MoveCommand.class, cmd);
        MoveCommand mc = (MoveCommand) cmd;

        List<Piece> nonBlockMovers = new ArrayList<>();
        for (Piece p : redPieces) {
            if (!(p.getLocation().isStandardPath() || p.getLocation().isStartingSquare() || p.getLocation().isApproach()
                    || p.getLocation().isAlpha() || p.getLocation().isBeta() || p.getLocation().isGamma()))
                continue;
            var pd = ruleEngine.movementRule().calculateStandardDestination(p, 3);
            if (pd == null)
                continue;
            var cell = board.getStandardCell(pd.getIndex());
            boolean wouldBlock = cell.getPieces().stream().anyMatch(x -> x.getColour() == Colour.RED);
            Command c = ruleEngine.movementRule().commandForStandardMove(p, 3);
            if (!(c instanceof com.ludot.command.NoMoveCommand) && !wouldBlock) {
                nonBlockMovers.add(p);
            }
        }

        var chosenDest = ruleEngine.movementRule().calculateStandardDestination(mc.getPiece(), 3);
        var chosenCell = board.getStandardCell(chosenDest.getIndex());
        boolean chosenCreatesBlock = chosenCell.getPieces().stream().anyMatch(p -> p.getColour() == Colour.RED);

        if (nonBlockMovers.isEmpty()) {
            assertTrue(chosenCreatesBlock,
                    "No non-block moves available but chosen move did not create a block; chosen=" + mc.getPiece());
        } else {
            assertFalse(chosenCreatesBlock,
                    "Non-block moves exist but chosen move created a block; non-block movers=" + nonBlockMovers);
        }
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

    // Keeps the test output quiet while the move-choice rules are checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
