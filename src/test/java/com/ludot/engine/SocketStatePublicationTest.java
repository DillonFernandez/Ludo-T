package com.ludot.engine;

import com.ludot.board.*;
import com.ludot.command.*;
import com.ludot.model.*;
import com.ludot.output.GameLogger;
import com.ludot.player.AbstractPlayer;
import com.ludot.random.*;
import com.ludot.rules.*;
import com.ludot.server.SocketGameLogger;
import com.ludot.support.IsolatedDatabase;
import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises engine-to-network publication, including post-teleport state. */
class SocketStatePublicationTest {
    @Test
    void baseEntryMovementAndHomeArrivalPublishCorrectProtocolLocations() throws Exception {
        for (Mode mode : List.of(Mode.BASE, Mode.STANDARD, Mode.HOME)) {
            try (var scenario = new Scenario(mode, MysteryEffect.BASE, mode == Mode.BASE ? 6 : 1)) {
                Piece piece = scenario.red.getPieces().get(0);
                if (mode == Mode.STANDARD) scenario.place(piece, 4, Direction.CLOCKWISE);
                if (mode == Mode.HOME) scenario.board.placePieceInHomePath(piece, Colour.RED, 4);
                scenario.run();
                String expected = switch (mode) {
                    case BASE -> "PIECE|R1|STARTING_SQUARE|RED|26";
                    case STANDARD -> "PIECE|R1|STANDARD_PATH|-|5";
                    case HOME -> "PIECE|R1|HOME|RED|-1";
                    default -> throw new AssertionError();
                };
                assertEquals(expected, scenario.lastState("R1"));
                assertEquals("PIECE|R2|BASE|RED|-1", scenario.lastState("R2"));
                scenario.assertClientsAgree();
            }
        }
    }

    @Test
    void ordinaryCapturePublishesBothCaptorAndCapturedBaseState() throws Exception {
        try (var scenario = new Scenario(Mode.STANDARD, MysteryEffect.BASE, 1)) {
            Piece captor = scenario.red.getPieces().get(0);
            Piece victim = scenario.players.get(3).getPieces().get(0);
            scenario.place(captor, 0, Direction.CLOCKWISE);
            scenario.place(victim, 1, Direction.CLOCKWISE);
            scenario.run();
            assertEquals("PIECE|R1|STANDARD_PATH|-|1", scenario.lastState("R1"));
            assertEquals("PIECE|B1|BASE|BLUE|-1", scenario.lastState("B1"));
            assertEquals(1, captor.getCaptureCount());
            List<String> lines = scenario.lines();
            int captured = lines.indexOf("Red piece R1 lands on square 1, captures Blue piece B1, and returns it to the base.");
            assertTrue(captured >= 0);
            assertEquals("PIECE|R1|STANDARD_PATH|-|1", lines.get(captured + 1));
            assertEquals("PIECE|B1|BASE|BLUE|-1", lines.get(captured + 2));
            scenario.assertClientsAgree();
        }
    }

    @Test
    void blockadeCapturePublishesEveryMemberOfBothBlocks() throws Exception {
        try (var scenario = new Scenario(Mode.STANDARD, MysteryEffect.BASE, 4)) {
            for (int i = 0; i < 2; i++) {
                scenario.place(scenario.red.getPieces().get(i), 10, Direction.CLOCKWISE);
                scenario.place(scenario.players.get(3).getPieces().get(i), 12, Direction.CLOCKWISE);
            }
            scenario.run();
            for (int number = 1; number <= 2; number++) {
                assertEquals("PIECE|R" + number + "|APPROACH|BLUE|12", scenario.lastState("R" + number));
                assertEquals("PIECE|B" + number + "|BASE|BLUE|-1", scenario.lastState("B" + number));
            }
            int capture = scenario.lines().indexOf("Red block captures Blue block on square 12. Blue block pieces are returned to base.");
            assertTrue(capture >= 0);
            assertEquals(List.of("PIECE|R1|APPROACH|BLUE|12", "PIECE|R2|APPROACH|BLUE|12",
                    "PIECE|B1|BASE|BLUE|-1", "PIECE|B2|BASE|BLUE|-1"), scenario.lines().subList(capture + 1, capture + 5));
            scenario.assertClientsAgree();
        }
    }

    @Test
    void mysteryTeleportsPublishFinalDestinationsIncludingGammaToBetaBeforeNextTurn() throws Exception {
        for (MysteryEffect effect : MysteryEffect.values()) {
            for (Direction direction : Direction.values()) {
                try (var scenario = new Scenario(Mode.STANDARD, effect, 1)) {
                    MysteryRule mystery = scenario.rules.mysteryRule();
                    mystery.noteStandardPathEntry();
                    mystery.updateMysteryCellForNewRound();
                    mystery.updateMysteryCellForNewRound();
                    Piece piece = scenario.red.getPieces().get(0);
                    scenario.place(piece, direction == Direction.CLOCKWISE ? 6 : 8, direction);
                    scenario.run();
                    String location = switch (effect) {
                        case ALPHA -> "ALPHA|-|8";
                        case BETA -> "BETA|-|26";
                        case GAMMA -> direction == Direction.CLOCKWISE ? "GAMMA|-|45" : "BETA|-|26";
                        case BASE -> "BASE|RED|-1";
                        case STARTING_SQUARE -> "STARTING_SQUARE|RED|26";
                        case APPROACH -> "APPROACH|RED|25";
                    };
                    String expected = "PIECE|R1|" + location;
                    assertEquals(expected, scenario.lastState("R1"), effect + " " + direction);
                    List<String> lines = scenario.lines();
                    int teleport = -1;
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).startsWith("Red piece R1 teleported to")) teleport = i;
                    }
                    assertTrue(teleport >= 0);
                    String firstStateAfterTeleport = lines.subList(teleport + 1, lines.size()).stream()
                            .filter(line -> line.startsWith("PIECE|R1|")).findFirst().orElseThrow();
                    assertEquals(expected, firstStateAfterTeleport, "Never publish a stale teleport source");
                    int nextTurn = lines.indexOf("Green player rolled 1.");
                    assertTrue(lines.subList(teleport + 1, nextTurn).contains(expected), "Publish during the turn, not just at round end");
                    scenario.assertClientsAgree();
                }
            }
        }
    }

    @Test
    void restrictedBetaReturnPublishesBaseAfterResettingThePiece() throws Exception {
        try (var scenario = new Scenario(Mode.NONE, MysteryEffect.BASE, 3)) {
            Piece piece = scenario.red.getPieces().get(0);
            scenario.place(piece, 26, Direction.CLOCKWISE);
            scenario.rules.mysteryRule().applyPostTeleportEffect(piece, MysteryEffect.BETA);
            piece.setConsecutiveBetaRestrictedRollCount(2);
            scenario.run();
            assertEquals("PIECE|R1|BASE|RED|-1", scenario.lastState("R1"));
            List<String> states = scenario.lines().stream().filter(line -> line.startsWith("PIECE|R1|")).toList();
            assertFalse(states.isEmpty());
            assertTrue(states.stream().allMatch("PIECE|R1|BASE|RED|-1"::equals));
            assertNull(piece.getDirection());
            assertFalse(piece.isInBriefing());
            scenario.assertClientsAgree();
        }
    }

    @Test
    void thirdSixForcedBreakPublishesAllChangedPiecesBeforeTheNextPlayer() throws Exception {
        try (var scenario = new Scenario(Mode.NONE, MysteryEffect.BASE, 6, 6, 6)) {
            for (int i = 0; i < 3; i++) scenario.place(scenario.red.getPieces().get(i), 14, Direction.CLOCKWISE);
            scenario.run();
            assertEquals("PIECE|R1|STANDARD_PATH|-|14", scenario.lastState("R1"));
            assertEquals("PIECE|R2|STANDARD_PATH|-|17", scenario.lastState("R2"));
            assertEquals("PIECE|R3|STANDARD_PATH|-|17", scenario.lastState("R3"));
            List<String> lines = scenario.lines();
            int next = lines.indexOf("Green player rolled 1.");
            assertTrue(next >= 0);
            assertTrue(lines.subList(0, next).contains("PIECE|R2|STANDARD_PATH|-|17"));
            assertTrue(lines.subList(0, next).contains("PIECE|R3|STANDARD_PATH|-|17"));
            scenario.assertClientsAgree();
        }
    }

    private enum Mode { BASE, STANDARD, HOME, NONE }

    private static final class Scenario implements AutoCloseable {
        final IsolatedDatabase database = new IsolatedDatabase();
        final StringWriter first = new StringWriter();
        final StringWriter second = new StringWriter();
        final PrintWriter firstOutput = new PrintWriter(first, true);
        final PrintWriter secondOutput = new PrintWriter(second, true);
        final SocketGameLogger logger = new SocketGameLogger(database.repository);
        final Board board = new BoardBuilder().build();
        final RuleEngine rules;
        final List<AbstractPlayer> players = new ArrayList<>();
        final TestPlayer red;

        Scenario(Mode mode, MysteryEffect effect, int... rolls) throws Exception {
            logger.addClient(firstOutput);
            logger.addClient(secondOutput);
            rules = new RuleEngine(new MovementRule(board), new CaptureRule(board), new BlockRule(board),
                    new BonusRollRule(), new MysteryRule(board, new FixedMysteryEffectSelector(effect),
                    new FixedMysteryCellSpawner(7), logger), new WinCondition());
            Deque<Integer> sequence = new ArrayDeque<>();
            sequence.add(6); // Starting-player selection, not a turn roll.
            for (int roll : rolls) sequence.add(roll);
            red = new TestPlayer(Colour.RED, board, rules,
                    () -> sequence.isEmpty() ? 1 : sequence.removeFirst(), logger, mode);
            players.add(red);
            for (Colour colour : List.of(Colour.GREEN, Colour.YELLOW, Colour.BLUE)) {
                players.add(new TestPlayer(colour, board, rules, () -> 1, logger, Mode.NONE));
            }
        }

        void place(Piece piece, int index, Direction direction) {
            board.placePieceOnStandardPath(piece, index);
            piece.setDirection(direction);
        }
        void run() { new GameEngine(board, rules, new TurnManager(players), logger, 1).run(); }
        List<String> lines() { return first.toString().lines().toList(); }
        String lastState(String id) {
            return lines().stream().filter(line -> line.startsWith("PIECE|" + id + "|"))
                    .reduce((previous, current) -> current).orElseThrow();
        }
        void assertClientsAgree() { assertEquals(first.toString(), second.toString()); }
        @Override public void close() throws Exception {
            logger.removeClient(firstOutput);
            logger.removeClient(secondOutput);
            firstOutput.close();
            secondOutput.close();
            database.close();
        }
    }

    private static final class TestPlayer extends AbstractPlayer {
        private final Mode mode;
        private boolean selected;
        TestPlayer(Colour colour, Board board, RuleEngine rules, Dice dice, GameLogger logger, Mode mode) {
            super(colour, new ArrayList<>(board.getHomeArea(colour).pieces()), board, rules, dice,
                    () -> CoinFace.HEADS, logger);
            this.mode = mode;
        }
        @Override protected Command selectMove(int roll) {
            if (selected) return new NoMoveCommand();
            selected = true;
            return switch (mode) {
                case BASE -> tryMoveFromBase(roll);
                case STANDARD -> tryStandardMove(getPieces().get(0), roll);
                case HOME -> tryHomePathMove(getPieces().get(0), roll);
                case NONE -> new NoMoveCommand();
            };
        }
    }
}
