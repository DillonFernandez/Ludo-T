package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.BaseToStartingSquareCommand;
import com.ludot.command.BlockMoveCommand;
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

// These tests confirm how Green chooses moves, prefers block decisions, and handles captures.
class GreenPlayerTest {

    // Creates the minimal rule set used by the Green-player tests.
    private static RuleEngine createRuleEngine(Board board) {
        return new RuleEngine(
                new MovementRule(board),
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                new MysteryRule(board,
                        new StandardMysteryEffectSelector(1L),
                        new StandardMysteryCellSpawner(1L),
                        new SilentLogger()),
                new WinCondition());
    }

    // Green should prefer forming a block before using a base-entry move on a six.
    @Test
    void createsABlockBeforeUsingBaseEntryOnSix() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces());
        Piece movingPiece = pieces.get(0);
        Piece blockPartner = pieces.get(1);
        Piece basePiece = pieces.get(2);

        board.placePieceOnStandardPath(movingPiece, 0);
        movingPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(blockPartner, 6);
        blockPartner.setDirection(Direction.CLOCKWISE);

        GreenPlayer player = new GreenPlayer(Colour.GREEN, pieces, board, ruleEngine,
                new FixedDice(6), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(movingPiece, ((MoveCommand) command).getPiece());

        command.execute();

        assertEquals(6, movingPiece.getLocation().getIndex());
        assertEquals(2, board.getStandardCell(6).getPieces().size());
        assertTrue(basePiece.isInBase());
    }

    // A six should move a base piece onto the starting square when that does not
    // create a block.
    @Test
    void movesBasePieceToXOnSixWhenThatDoesNotCreateABlock() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces());
        GreenPlayer player = new GreenPlayer(Colour.GREEN, pieces, board, ruleEngine,
                new FixedDice(6), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(BaseToStartingSquareCommand.class, command);
        command.execute();

        assertTrue(pieces.get(0).getLocation().isStartingSquare());
        assertEquals(39, pieces.get(0).getLocation().getIndex());
    }

    // Green should finish a home-path move before breaking an existing block.
    @Test
    void movesHomePathPieceBeforeBreakingABlock() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces());
        Piece blockOne = pieces.get(0);
        Piece blockTwo = pieces.get(1);
        Piece homePiece = pieces.get(2);

        board.placePieceOnStandardPath(blockOne, 10);
        blockOne.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(blockTwo, 10);
        blockTwo.setDirection(Direction.CLOCKWISE);

        homePiece.setLocation(Location.homePath(Colour.GREEN, 3));
        homePiece.setDirection(Direction.CLOCKWISE);

        GreenPlayer player = new GreenPlayer(Colour.GREEN, pieces, board, ruleEngine,
                new FixedDice(2), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(homePiece, ((MoveCommand) command).getPiece());

        command.execute();

        assertTrue(homePiece.isHome());
        assertEquals(10, blockOne.getLocation().getIndex());
        assertEquals(10, blockTwo.getLocation().getIndex());
    }

    // A normal move should be preferred over moving one of Green's own paired
    // pieces.
    @Test
    void prefersNonBlockMoveBeforeMovingItsOwnBlock() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces());
        Piece blockOne = pieces.get(0);
        Piece blockTwo = pieces.get(1);
        Piece mover = pieces.get(2);

        pieces = new ArrayList<>(List.of(blockOne, blockTwo, mover, pieces.get(3)));

        board.placePieceOnStandardPath(blockOne, 10);
        blockOne.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(blockTwo, 10);
        blockTwo.setDirection(Direction.CLOCKWISE);

        board.placePieceOnStandardPath(mover, 0);
        mover.setDirection(Direction.CLOCKWISE);

        GreenPlayer player = new GreenPlayer(Colour.GREEN, pieces, board, ruleEngine,
                new FixedDice(2), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(mover, ((MoveCommand) command).getPiece());

        command.execute();

        assertEquals(2, mover.getLocation().getIndex());
        assertEquals(10, blockOne.getLocation().getIndex());
        assertEquals(10, blockTwo.getLocation().getIndex());
    }

    // If no other piece can use the roll, Green should fall back to the block move.
    @Test
    void fallsBackToBlockMoveWhenNoOtherPieceCanUseTheRoll() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces());
        Piece blockOne = pieces.get(0);
        Piece blockTwo = pieces.get(1);

        board.placePieceOnStandardPath(blockOne, 14);
        blockOne.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(blockTwo, 14);
        blockTwo.setDirection(Direction.CLOCKWISE);

        GreenPlayer player = new GreenPlayer(Colour.GREEN, pieces, board, ruleEngine,
                new FixedDice(2), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(BlockMoveCommand.class, command);

        command.execute();

        assertEquals(15, blockOne.getLocation().getIndex());
        assertEquals(15, blockTwo.getLocation().getIndex());
    }

    // Green should seek a capture when the opponent is in range and no capture has
    // happened yet.
    @Test
    void seeksCaptureWhenNoCaptureHasBeenMadeAndOpponentIsInRange() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces());
        Piece needsCapture = pieces.get(0);

        board.placePieceOnStandardPath(needsCapture, 0);
        needsCapture.setDirection(Direction.CLOCKWISE);

        Piece opponent = new Piece(Colour.RED, 1);
        board.placePieceOnStandardPath(opponent, 3);
        opponent.setDirection(Direction.CLOCKWISE);

        GreenPlayer player = new GreenPlayer(Colour.GREEN, pieces, board, ruleEngine,
                new FixedDice(3), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();
        command.execute();

        assertInstanceOf(MoveCommand.class, command);
        MoveCommand mc = (MoveCommand) command;
        assertEquals(needsCapture, mc.getPiece());
        assertEquals(3, needsCapture.getLocation().getIndex());
    }

    // Returns one fixed roll value so the tests stay predictable.
    private record FixedDice(int value) implements Dice {

        @Override
        public int roll() {
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
