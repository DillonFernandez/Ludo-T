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
import com.ludot.random.MysteryCellSpawner;
import com.ludot.random.MysteryEffectSelector;
import com.ludot.rules.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

// These tests confirm how Blue chooses moves, handles special cells, and cycles through its pieces.
class BluePlayerTest {

    // Creates the minimal rules and mystery setup used by these tests.
    private static RuleEngine createRuleEngine(Board board, int mysterySpawnIndex) {
        MysteryRule mysteryRule = new MysteryRule(board,
                new ScriptedMysteryEffectSelector(MysteryEffect.BASE),
                new FixedMysteryCellSpawner(mysterySpawnIndex),
                new SilentLogger());
        return new RuleEngine(
                new MovementRule(board),
                new CaptureRule(board),
                new BlockRule(board),
                new BonusRollRule(),
                mysteryRule,
                new WinCondition());
    }

    // Advances the mystery cell to the position needed by the test.
    private static void activateMysteryCell(MysteryRule mysteryRule) {
        mysteryRule.noteStandardPathEntry();
        mysteryRule.updateMysteryCellForNewRound();
        mysteryRule.updateMysteryCellForNewRound();
    }

    // Places a piece on the standard path and sets its starting direction.
    private static void placeMovablePiece(Board board, Piece piece, int index, Direction direction) {
        board.placePieceOnStandardPath(piece, index);
        piece.setDirection(direction);
    }

    // Confirms the selected command moves the expected piece.
    private static void assertMovePiece(Command command, Piece expectedPiece) {
        assertInstanceOf(MoveCommand.class, command);
        assertEquals(expectedPiece, ((MoveCommand) command).getPiece());
    }

    // Blue should move through its four pieces in order and wrap back to the first
    // one.
    @Test
    void cyclesThroughB1ToB4AndBackToB1() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board, 1);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b1 = pieces.get(0);
        Piece b2 = pieces.get(1);
        Piece b3 = pieces.get(2);
        Piece b4 = pieces.get(3);

        placeMovablePiece(board, b1, 0, Direction.CLOCKWISE);
        placeMovablePiece(board, b2, 5, Direction.CLOCKWISE);
        placeMovablePiece(board, b3, 10, Direction.CLOCKWISE);
        placeMovablePiece(board, b4, 15, Direction.CLOCKWISE);

        BluePlayer player = new BluePlayer(Colour.BLUE, pieces, board, ruleEngine,
                new FixedDice(1), () -> CoinFace.HEADS, new SilentLogger());

        Command turn1 = player.takeTurn();
        assertMovePiece(turn1, b1);
        turn1.execute();

        Command turn2 = player.takeTurn();
        assertMovePiece(turn2, b2);
        turn2.execute();

        Command turn3 = player.takeTurn();
        assertMovePiece(turn3, b3);
        turn3.execute();

        Command turn4 = player.takeTurn();
        assertMovePiece(turn4, b4);
        turn4.execute();

        Command turn5 = player.takeTurn();
        assertMovePiece(turn5, b1);
    }

    // If one candidate cannot move, Blue should try the next available piece.
    @Test
    void considersTheNextPieceWhenTheSelectedCyclicPieceCannotMove() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board, 1);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b1 = pieces.get(0);
        Piece b2 = pieces.get(1);

        placeMovablePiece(board, b2, 4, Direction.CLOCKWISE);

        BluePlayer player = new BluePlayer(Colour.BLUE, pieces, board, ruleEngine,
                new FixedDice(1), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertMovePiece(command, b2);
        assertTrue(b1.isInBase());
    }

    // A roll of six should allow a piece to leave base and enter the starting
    // square.
    @Test
    void movesABasePieceToXOnSix() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board, 1);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b1 = pieces.get(0);

        BluePlayer player = new BluePlayer(Colour.BLUE, pieces, board, ruleEngine,
                new FixedDice(6), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(BaseToStartingSquareCommand.class, command);
        command.execute();

        assertEquals(LocationType.STARTING_SQUARE, b1.getLocation().getType());
        assertEquals(13, b1.getLocation().getIndex());
    }

    // When several legal moves exist, Blue should use the provided random choice.
    @Test
    void usesTheProvidedRandomChoiceWhenSeveralLegalMovesExist() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board, 1);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b2 = pieces.get(1);
        Piece b3 = pieces.get(2);

        placeMovablePiece(board, b2, 4, Direction.CLOCKWISE);
        placeMovablePiece(board, b3, 8, Direction.CLOCKWISE);

        Random random = new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };

        BluePlayer player = new BluePlayer(Colour.BLUE, pieces, board, ruleEngine,
                new FixedDice(1), () -> CoinFace.HEADS, new SilentLogger(), random);

        Command command = player.takeTurn();

        assertMovePiece(command, b3);
    }

    // Counter-clockwise moves should prefer the mystery cell when that choice is
    // legal.
    @Test
    void prioritisesLandingOnTheMysteryCellWhenMovingCounterClockwise() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board, 7);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b1 = pieces.get(0);
        Piece b2 = pieces.get(1);

        placeMovablePiece(board, b1, 10, Direction.COUNTER_CLOCKWISE);
        placeMovablePiece(board, b2, 4, Direction.CLOCKWISE);
        activateMysteryCell(ruleEngine.mysteryRule());

        BluePlayer player = new BluePlayer(Colour.BLUE, pieces, board, ruleEngine,
                new FixedDice(3), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(b1, ((MoveCommand) command).getPiece());
        command.execute();
        assertEquals(7, b1.getLocation().getIndex());
    }

    // Blue should avoid the mystery cell when a safer move is available.
    @Test
    void avoidsLandingOnTheMysteryCellWhenMovingClockwiseIfALegalAlternativeExists() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board, 13);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b1 = pieces.get(0);

        placeMovablePiece(board, b1, 10, Direction.CLOCKWISE);
        b1.incrementCaptureCount();
        activateMysteryCell(ruleEngine.mysteryRule());

        BluePlayer player = new BluePlayer(Colour.BLUE, pieces, board, ruleEngine,
                new FixedDice(3), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(b1, ((MoveCommand) command).getPiece());
        command.execute();
        assertEquals(0, b1.getLocation().getIndex());
    }

    // If no safe alternative exists, Blue may still choose the mystery cell.
    @Test
    void mayLandOnTheMysteryCellWhenAClockwisePieceHasNoLegalAlternative() {
        Board board = new BoardBuilder().build();
        RuleEngine ruleEngine = createRuleEngine(board, 3);

        List<Piece> pieces = new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces());
        Piece b1 = pieces.get(0);

        placeMovablePiece(board, b1, 0, Direction.CLOCKWISE);
        activateMysteryCell(ruleEngine.mysteryRule());

        BluePlayer player = new BluePlayer(Colour.BLUE, pieces, board, ruleEngine,
                new FixedDice(3), () -> CoinFace.HEADS, new SilentLogger());

        Command command = player.takeTurn();

        assertInstanceOf(MoveCommand.class, command);
        assertEquals(b1, ((MoveCommand) command).getPiece());
        command.execute();
        assertEquals(3, b1.getLocation().getIndex());
    }

    // Returns one fixed roll value so the tests stay predictable.
    private record FixedDice(int value) implements Dice {

        @Override
        public int roll() {
            return value;
        }
    }

    // Places the mystery cell at a fixed index for the test scenario.
    private record FixedMysteryCellSpawner(int spawnIndex) implements MysteryCellSpawner {

        @Override
        public int spawn(int previousIndex, Set<Integer> occupiedIndices) {
            return spawnIndex;
        }
    }

    // Returns the scripted mystery effects needed by the test.
    private static final class ScriptedMysteryEffectSelector implements MysteryEffectSelector {
        private final Deque<MysteryEffect> effects = new ArrayDeque<>();

        private ScriptedMysteryEffectSelector(MysteryEffect... effects) {
            for (MysteryEffect effect : effects) {
                this.effects.addLast(effect);
            }
        }

        @Override
        public MysteryEffect selectEffect() {
            if (effects.isEmpty()) {
                return MysteryEffect.BASE;
            }
            return effects.removeFirst();
        }
    }

    // Keeps the test output quiet while the move choice is checked.
    private static final class SilentLogger extends GameLogger {
        @Override
        public void log(String message) {
        }
    }
}
