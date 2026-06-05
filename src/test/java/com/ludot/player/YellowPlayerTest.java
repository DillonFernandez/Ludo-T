package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.BaseToStartingSquareCommand;
import com.ludot.command.Command;
import com.ludot.command.MoveCommand;
import com.ludot.model.*;
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

// These tests confirm how Yellow chooses between base entry, capture, and home-path moves.
class YellowPlayerTest {

    // Creates the minimal rule set used by the Yellow-player tests.
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

    // A six should move a Yellow piece out of base to its starting square.
    @Test
    void movesFromBaseToXWhenSixIsThrownAndBaseHasPieces() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);
        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.YELLOW).pieces());

        YellowPlayer player = new YellowPlayer(Colour.YELLOW, pieces, board, ruleEngine,
                new FixedDice(6), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(BaseToStartingSquareCommand.class, command);
        command.execute();

        Piece movedPiece = pieces.get(0);
        assertTrue(movedPiece.getLocation().isStartingSquare());
        assertEquals(LocationType.STARTING_SQUARE, movedPiece.getLocation().getType());
        assertEquals(0, movedPiece.getLocation().getIndex());
    }

    // Yellow should choose the piece that still needs a capture before a piece
    // already close to home.
    @Test
    void prioritisesPiecesThatNeedCapturesBeforePiecesThatAlreadyCanEnterTheHomeStraight() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.YELLOW).pieces());
        Piece needsCapture = pieces.get(0);
        Piece alreadyEligible = pieces.get(1);

        board.placePieceOnStandardPath(needsCapture, 0);
        needsCapture.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(alreadyEligible, 5);
        alreadyEligible.setDirection(Direction.CLOCKWISE);
        alreadyEligible.incrementCaptureCount();

        Piece opponentForNeedsCapture = new Piece(Colour.BLUE, 1);
        board.placePieceOnStandardPath(opponentForNeedsCapture, 3);
        opponentForNeedsCapture.setDirection(Direction.CLOCKWISE);

        Piece opponentForAlreadyEligible = new Piece(Colour.RED, 1);
        board.placePieceOnStandardPath(opponentForAlreadyEligible, 8);
        opponentForAlreadyEligible.setDirection(Direction.CLOCKWISE);

        YellowPlayer player = new YellowPlayer(Colour.YELLOW, pieces, board, ruleEngine,
                new FixedDice(3), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(needsCapture, ((MoveCommand) command).getPiece());
    }

    // Yellow should use the piece that can capture when an opponent is in range.
    @Test
    void capturesWithAPieceThatStillNeedsCapturesWhenAnOpponentIsWithinRange() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.YELLOW).pieces());
        Piece needsCapture = pieces.get(0);

        board.placePieceOnStandardPath(needsCapture, 0);
        needsCapture.setDirection(Direction.CLOCKWISE);

        Piece opponent = new Piece(Colour.BLUE, 1);
        board.placePieceOnStandardPath(opponent, 3);
        opponent.setDirection(Direction.CLOCKWISE);

        YellowPlayer player = new YellowPlayer(Colour.YELLOW, pieces, board, ruleEngine,
                new FixedDice(3), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(needsCapture, ((MoveCommand) command).getPiece());
        command.execute();

        assertEquals(3, needsCapture.getLocation().getIndex());
        assertTrue(board.getStandardCell(3).getPieces().contains(needsCapture));
        assertTrue(board.getStandardCell(3).getPieces().contains(opponent));
    }

    // Yellow should prefer the winning home-path move over another capture.
    @Test
    void doesNotSeekExtraCapturesWhenAWinningMoveIsAvailable() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.YELLOW).pieces());
        Piece capturingPiece = pieces.get(0);
        Piece homePathPiece = pieces.get(1);

        board.placePieceOnStandardPath(capturingPiece, 0);
        capturingPiece.setDirection(Direction.CLOCKWISE);
        capturingPiece.incrementCaptureCount();

        homePathPiece.setLocation(Location.homePath(Colour.YELLOW, 4));
        homePathPiece.setDirection(Direction.CLOCKWISE);

        Piece opponent = new Piece(Colour.BLUE, 1);
        board.placePieceOnStandardPath(opponent, 3);
        opponent.setDirection(Direction.CLOCKWISE);

        YellowPlayer player = new YellowPlayer(Colour.YELLOW, pieces, board, ruleEngine,
                new FixedDice(1), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(homePathPiece, ((MoveCommand) command).getPiece());
    }

    // With no capture available, Yellow should move the piece nearest home.
    @Test
    void movesThePieceClosestToHomeWhenNoCapturesArePossible() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.YELLOW).pieces());
        Piece closerPiece = pieces.get(0);
        Piece fartherPiece = pieces.get(1);

        board.placePieceOnStandardPath(closerPiece, 50);
        closerPiece.setDirection(Direction.CLOCKWISE);
        board.placePieceOnStandardPath(fartherPiece, 40);
        fartherPiece.setDirection(Direction.CLOCKWISE);

        YellowPlayer player = new YellowPlayer(Colour.YELLOW, pieces, board, ruleEngine,
                new FixedDice(1), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(closerPiece, ((MoveCommand) command).getPiece());
    }

    // Returns the fixed roll value used by these tests.
    private record FixedDice(int value) implements Dice {

        @Override
        public int roll() {
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
