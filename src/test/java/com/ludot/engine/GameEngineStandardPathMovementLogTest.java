package com.ludot.engine;

import com.ludot.board.Board;
import com.ludot.board.BoardBuilder;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.config.GameConfig;
import com.ludot.model.*;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.*;
import com.ludot.rules.*;
import com.ludot.state.EnergizedState;
import com.ludot.state.SickState;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// These tests confirm which move and effect messages appear on the standard path.
class GameEngineStandardPathMovementLogTest {

    // Builds the small scenario shared by the movement-log tests.
    private static Scenario createScenario(Mode mode, Dice dice) {
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
        TestPlayer redPlayer = new TestPlayer(mode, Colour.RED,
                new ArrayList<>(board.getHomeArea(Colour.RED).pieces()),
                board, ruleEngine, dice, () -> CoinFace.HEADS, logger);
        players.add(redPlayer);
        players.add(new TestPlayer(Mode.NO_OP, Colour.GREEN,
                new ArrayList<>(board.getHomeArea(Colour.GREEN).pieces()),
                board, ruleEngine, dice, () -> CoinFace.HEADS, logger));
        players.add(new TestPlayer(Mode.NO_OP, Colour.YELLOW,
                new ArrayList<>(board.getHomeArea(Colour.YELLOW).pieces()),
                board, ruleEngine, dice, () -> CoinFace.HEADS, logger));
        players.add(new TestPlayer(Mode.NO_OP, Colour.BLUE,
                new ArrayList<>(board.getHomeArea(Colour.BLUE).pieces()),
                board, ruleEngine, dice, () -> CoinFace.HEADS, logger));

        TurnManager turnManager = new TurnManager(players);
        GameEngine engine = new GameEngine(board, ruleEngine, turnManager, logger, 1);
        return new Scenario(engine, logger, board, redPlayer, mysteryRule);
    }

    // Places the active mystery cell at a chosen index for the teleport tests.
    private static void setActiveMysteryCell(MysteryRule mysteryRule, int index) throws Exception {
        Field activeLocationField = MysteryRule.class.getDeclaredField("activeMysteryLocation");
        activeLocationField.setAccessible(true);
        activeLocationField.set(mysteryRule, Location.standardPath(index));

        Field remainingRoundsField = MysteryRule.class.getDeclaredField("activeMysteryRemainingRounds");
        remainingRoundsField.setAccessible(true);
        remainingRoundsField.setInt(mysteryRule, 1);
    }

    // Confirms the normal clockwise move message appears exactly as expected.
    @Test
    void clockwiseStandardPathMoveLogsExactMessage() {
        Scenario scenario = createScenario(Mode.STANDARD, new SequenceDice(6, 1, 1, 1, 5));
        Piece piece = scenario.redPlayer().getPieces().get(0);

        scenario.board().placePieceOnStandardPath(piece, 0);
        piece.setDirection(Direction.CLOCKWISE);

        scenario.engine().run();

        assertTrue(scenario.logger().messages.contains(
                        "Red moves piece R1 from location 0 to 5 by 5 units in clockwise direction."),
                () -> String.join("\n", scenario.logger().messages));
        assertFalse(scenario.logger().messages.stream().anyMatch(message -> message.contains("captures ")),
                () -> String.join("\n", scenario.logger().messages));
    }

    // Verifies the counter-clockwise move message uses the opposite direction.
    @Test
    void counterClockwiseStandardPathMoveLogsExactMessage() {
        Scenario scenario = createScenario(Mode.STANDARD, new SequenceDice(6, 1, 1, 1, 5));
        Piece piece = scenario.redPlayer().getPieces().get(0);

        scenario.board().placePieceOnStandardPath(piece, 10);
        piece.setDirection(Direction.COUNTER_CLOCKWISE);

        scenario.engine().run();

        assertTrue(scenario.logger().messages.contains(
                "Red moves piece R1 from location 10 to 5 by 5 units in counter-clockwise direction."));
    }

    // Checks that the energized effect changes the logged travel distance.
    @Test
    void energizedStandardPathMoveUsesAdjustedMovementValue() {
        Scenario scenario = createScenario(Mode.STANDARD, new SequenceDice(6, 1, 1, 1, 5));
        Piece piece = scenario.redPlayer().getPieces().get(0);

        scenario.board().placePieceOnStandardPath(piece, 0);
        piece.setDirection(Direction.CLOCKWISE);
        piece.setAlphaMovementState(new EnergizedState());
        piece.setAlphaEffectRemainingRounds(GameConfig.ALPHA_EFFECT_DURATION_ROUNDS);

        scenario.engine().run();

        assertTrue(scenario.logger().messages.contains(
                "Red moves piece R1 from location 0 to 10 by 10 units in clockwise direction."));
    }

    // Verifies that the sick effect reduces the logged movement distance.
    @Test
    void sickStandardPathMoveUsesAdjustedMovementValue() {
        Scenario scenario = createScenario(Mode.STANDARD, new SequenceDice(6, 1, 1, 1, 5));
        Piece piece = scenario.redPlayer().getPieces().get(0);

        scenario.board().placePieceOnStandardPath(piece, 20);
        piece.setDirection(Direction.COUNTER_CLOCKWISE);
        piece.setAlphaMovementState(new SickState());
        piece.setAlphaEffectRemainingRounds(GameConfig.ALPHA_EFFECT_DURATION_ROUNDS);

        scenario.engine().run();

        assertTrue(scenario.logger().messages.contains(
                "Red moves piece R1 from location 20 to 18 by 2 units in counter-clockwise direction."));
    }

    // Ensures base entry does not use the standard path move message.
    @Test
    void baseEntryDoesNotEmitStandardPathMovementMessage() {
        Scenario scenario = createScenario(Mode.BASE_ENTRY, new SequenceDice(6, 1, 1, 1, 6));

        scenario.engine().run();

        assertFalse(scenario.logger().messages.stream()
                        .anyMatch(message -> message.equals(
                                "Red moves piece R1 from location 2 to 4 by 2 units in clockwise direction.")),
                () -> String.join("\n", scenario.logger().messages));
    }

    // Confirms that teleporting replaces the standard move log with the
    // mystery-cell message.
    @Test
    void teleportDoesNotEmitStandardPathMovementMessage() throws Exception {
        Scenario scenario = createScenario(Mode.NO_OP, new SequenceDice(6, 1, 1, 1, 1));
        Piece piece = scenario.redPlayer().getPieces().get(0);

        scenario.board().placePieceOnStandardPath(piece, 5);
        piece.setDirection(Direction.CLOCKWISE);
        setActiveMysteryCell(scenario.mysteryRule(), 5);

        scenario.engine().run();

        assertFalse(scenario.logger().messages.stream()
                .anyMatch(message -> message.contains("moves piece R1 from location")));
        assertTrue(scenario.logger().messages.stream()
                .anyMatch(
                        message -> message.contains(
                                "Red player lands on a mystery cell and is teleported to Base.")));
    }

    // Verifies that a blocked move does not log the normal path move.
    @Test
    void blockedMoveDoesNotEmitStandardPathMovementMessage() {
        Scenario scenario = createScenario(Mode.STANDARD, new SequenceDice(6, 1, 1, 1, 5, 1));
        Piece movingPiece = scenario.redPlayer().getPieces().get(0);
        Piece blockingPiece = scenario.board().getHomeArea(Colour.BLUE).pieces().get(0);

        scenario.board().placePieceOnStandardPath(movingPiece, 2);
        movingPiece.setDirection(Direction.CLOCKWISE);
        scenario.board().placePieceOnStandardPath(blockingPiece, 3);
        blockingPiece.setDirection(Direction.CLOCKWISE);

        scenario.engine().run();

        assertFalse(scenario.logger().messages.stream()
                .anyMatch(message -> message.equals(
                        "Red moves piece R1 from location 2 to 4 by 2 units in clockwise direction.")));
    }

    // Ensures the home-path move uses a different logging path than the main track.
    @Test
    void homeMoveDoesNotEmitStandardPathMovementMessage() {
        Scenario scenario = createScenario(Mode.HOME_PATH, new SequenceDice(6, 1, 1, 1, 1));
        Piece piece = scenario.redPlayer().getPieces().get(0);

        scenario.board().placePieceInHomePath(piece, Colour.RED, 4);

        scenario.engine().run();

        assertTrue(piece.isHome());
        assertFalse(scenario.logger().messages.stream()
                        .anyMatch(message -> message.contains("moves piece R1 from location")),
                () -> String.join("\n", scenario.logger().messages));
    }

    // Confirms that the move log appears before the capture log in the same turn.
    @Test
    void standardMoveMessageIsPrintedBeforeCaptureMessage() {
        Scenario scenario = createScenario(Mode.STANDARD, new SequenceDice(6, 1, 1, 1, 5));
        Piece movingPiece = scenario.redPlayer().getPieces().get(0);
        Piece capturedPiece = scenario.board().getHomeArea(Colour.BLUE).pieces().get(0);

        scenario.board().placePieceOnStandardPath(movingPiece, 0);
        movingPiece.setDirection(Direction.CLOCKWISE);
        scenario.board().placePieceOnStandardPath(capturedPiece, 5);
        capturedPiece.setDirection(Direction.CLOCKWISE);

        scenario.engine().run();

        int moveIndex = scenario.logger().messages.indexOf(
                "Red moves piece R1 from location 0 to 5 by 5 units in clockwise direction.");
        int captureIndex = scenario.logger().messages.indexOf(
                "Red piece R1 lands on square 5, captures Blue piece B1, and returns it to the base.");

        assertTrue(moveIndex >= 0);
        assertTrue(captureIndex > moveIndex);
    }

    @Test
    void mysteryEffectMessagesUseExpectedExactOutput() {
        CapturingGameLogger logger = new CapturingGameLogger();
        Board board = new BoardBuilder().build();
        MysteryRule mysteryRule = new MysteryRule(board,
                new FixedMysteryEffectSelector(MysteryEffect.GAMMA),
                new FixedMysteryCellSpawner(10), logger);

        Piece clockwisePiece = new Piece(Colour.RED, 1);
        board.placePieceOnStandardPath(clockwisePiece, 15);
        clockwisePiece.setDirection(Direction.CLOCKWISE);
        mysteryRule.applyPostTeleportEffect(clockwisePiece, MysteryEffect.GAMMA);

        assertTrue(logger.messages.contains(
                "The Red piece R1, which was moving clockwise, has changed to moving counterclockwise."));

        logger.messages.clear();
        Piece counterClockwisePiece = new Piece(Colour.GREEN, 1);
        board.placePieceOnStandardPath(counterClockwisePiece, 16);
        counterClockwisePiece.setDirection(Direction.COUNTER_CLOCKWISE);
        mysteryRule.applyPostTeleportEffect(counterClockwisePiece, MysteryEffect.GAMMA);

        assertTrue(logger.messages.contains(
                "The Green piece G1 is moving in a counterclockwise direction. Teleporting to Beta from Gamma."));
        assertTrue(logger.messages.contains("Green piece G1 teleported to Beta."));
    }

    // Verifies that the move log still appears before the mystery teleport message.
    @Test
    void standardMoveMessageIsPrintedBeforeMysteryTeleportMessage() throws Exception {
        Scenario scenario = createScenario(Mode.STANDARD, new SequenceDice(6, 1, 1, 1, 5));
        Piece movingPiece = scenario.redPlayer().getPieces().get(0);

        scenario.board().placePieceOnStandardPath(movingPiece, 0);
        movingPiece.setDirection(Direction.CLOCKWISE);
        setActiveMysteryCell(scenario.mysteryRule(), 5);

        scenario.engine().run();

        int moveIndex = scenario.logger().messages.indexOf(
                "Red moves piece R1 from location 0 to 5 by 5 units in clockwise direction.");
        int mysteryIndex = scenario.logger().messages.indexOf(
                "Red player lands on a mystery cell and is teleported to Base.");

        assertTrue(moveIndex >= 0);
        assertTrue(mysteryIndex > moveIndex);
    }

    // Labels the red player's move mode for each scenario.
    private enum Mode {
        STANDARD,
        HOME_PATH,
        BASE_ENTRY,
        NO_OP
    }

    // Groups the objects used by each movement-log scenario.
    private record Scenario(GameEngine engine, CapturingGameLogger logger, Board board,
                            TestPlayer redPlayer, MysteryRule mysteryRule) {
    }

    // Uses one simple move mode to keep each test focused on the log output.
    private static final class TestPlayer extends AbstractPlayer {
        private final Mode mode;
        private final Piece movingPiece;

        private TestPlayer(Mode mode, Colour colour, List<Piece> pieces, Board board,
                           RuleEngine ruleEngine, Dice dice, CoinToss coinToss, GameLogger logger) {
            super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
            this.mode = mode;
            this.movingPiece = pieces.get(0);
        }

        @Override
        protected Command selectMove(int diceValue) {
            return switch (mode) {
                case STANDARD -> tryStandardMove(movingPiece, diceValue);
                case HOME_PATH -> tryHomePathMove(movingPiece, diceValue);
                case BASE_ENTRY -> tryMoveFromBase(diceValue);
                case NO_OP -> new NoMoveCommand();
            };
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

    // Stores the logged messages for assertion checks.
    private static final class CapturingGameLogger extends GameLogger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }
}
