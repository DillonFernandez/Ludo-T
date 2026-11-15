// Tests shared settings, game termination, repeatable valid gameplay and console startup.
package ludot.game;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import ludot.Main;
import ludot.config.GameSettings;
import ludot.model.Board;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.output.GameOutput;
import ludot.random.RandomNumbers;
import ludot.random.RandomSource;
import ludot.support.RecordedOutput;
import ludot.support.ScriptedRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

class GameCompletionTest {

    // Settings share one instance with the expected round, dice, consecutive-six
    // and effect limits.
    @Test
    void settingsAreSharedAndLimitRounds() {
        GameSettings first = GameSettings.getInstance();
        GameSettings second = GameSettings.getInstance();
        assertSame(first, second);
        assertEquals(10_000, first.getMaximumRounds());
        assertEquals(6, first.getDiceFaces());
        assertEquals(3, first.getMaximumConsecutiveSixes());
        assertEquals(4, first.getEffectRounds());
    }

    // An early Red win stops further turns without counting the partial round as
    // complete.
    @Test
    void winnerEndsTheGame() {
        ScriptedRandom random = new ScriptedRandom();
        random.add(5, 4, 3, 2, 0);
        CountedOutput output = new CountedOutput();
        LudoGame game = new LudoGameFactory().createGame(random, output);
        Player red = game.getBoard().getPlayers()[0];
        prepareWinner(red);

        game.play();

        assertTrue(red.hasWon());
        assertEquals(0, game.getCompletedRounds());
        assertEquals(1, output.rounds);
        assertEquals(1, output.rolls);
        assertEquals("red player wins!!!", output.lastLine);
        assertEquals(1, output.winners);
    }

    // A game with no deployment ends after 10,000 rounds and 40,000 turns without
    // declaring a winner.
    @Test
    @Timeout(30)
    void noWinnerStopsAtRoundLimit() {
        ScriptedRandom random = new ScriptedRandom();
        random.add(5, 4, 3, 2);
        CountedOutput output = new CountedOutput();
        LudoGame game = new LudoGameFactory().createGame(random, output);

        game.play();

        assertEquals(10_000, game.getCompletedRounds());
        assertEquals(10_000, output.rounds);
        assertEquals(40_000, output.rolls);
        assertEquals(40_000, output.statuses);
        assertEquals(40_004, random.calls);
        assertEquals("The game ran 10,000 rounds without a winner, "
                + "so the simulation has ended.", output.lastLine);
        assertEquals(0, output.winners);
        for (Player player : game.getBoard().getPlayers()) {
            assertEquals(4, player.countAtBase());
        }
    }

    // Equal seeds produce matching valid games with one winner, and console startup
    // reaches one valid ending.
    @Test
    @Timeout(30)
    void completeGamesPreserveValidState() {
        CheckedRandom firstRandom = new CheckedRandom(1234);
        RecordedOutput firstOutput = new RecordedOutput();
        LudoGame first = new LudoGameFactory().createGame(firstRandom, firstOutput);
        firstRandom.board = first.getBoard();
        CheckedRandom secondRandom = new CheckedRandom(1234);
        RecordedOutput secondOutput = new RecordedOutput();
        LudoGame second = new LudoGameFactory().createGame(secondRandom, secondOutput);
        secondRandom.board = second.getBoard();
        first.play();
        second.play();
        firstRandom.checkBoard();
        secondRandom.checkBoard();
        assertEquals(firstOutput.lines, secondOutput.lines);
        assertEquals(first.getCompletedRounds(), second.getCompletedRounds());
        assertTrue(first.getCompletedRounds() > 0);
        assertTrue(first.getCompletedRounds() <= 10_000);
        String output = firstOutput.text();
        assertTrue(output.contains(" player rolled 6."));
        assertTrue(output.contains(" to the starting point."));
        assertTrue(output.contains(" from location "));
        int winners = 0;
        for (Player player : first.getBoard().getPlayers()) {
            if (player.hasWon()) {
                winners++;
                assertEquals(player.getColour().getName() + " player wins!!!",
                        firstOutput.lines.get(firstOutput.lines.size() - 1));
            }
        }
        assertEquals(1, winners);
        checkMainEntryPoint();
    }

    private static void checkMainEntryPoint() {
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream console = new PrintStream(captured, true, StandardCharsets.UTF_8);
        try {
            System.setOut(console);
            Main.main(new String[0]);
        } finally {
            System.setOut(originalOutput);
            console.close();
        }
        String output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(output.startsWith("The red player has four (04) pieces named R1, R2, R3, and R4."));
        assertTrue(output.contains("The green player has four (04) pieces named G1, G2, G3, and G4."));
        assertTrue(output.contains("The yellow player has four (04) pieces named Y1, Y2, Y3, and Y4."));
        assertTrue(output.contains("The blue player has four (04) pieces named B1, B2, B3, and B4."));
        int rounds = 0;
        int endings = 0;
        for (String line : output.split(System.lineSeparator())) {
            if (line.startsWith("Round ")) {
                rounds++;
                assertEquals("Round " + rounds, line);
            }
            if (line.endsWith(" player wins!!!")) {
                endings++;
            } else if (line.startsWith("The game ran ")) {
                assertEquals("The game ran 10,000 rounds without a winner, "
                        + "so the simulation has ended.", line);
                assertEquals(10_000, rounds);
                endings++;
            }
        }
        assertTrue(rounds > 0);
        assertTrue(rounds <= 10_000);
        assertEquals(1, endings);
    }

    private static void prepareWinner(Player player) {
        for (Piece piece : player.getPieces()) {
            piece.moveTo(57, 0);
        }
        player.getPieces()[0].moveTo(56, 0);
    }

    private static final class CountedOutput implements GameOutput {
        int rounds;
        int rolls;
        int statuses;
        int winners;
        String lastLine;

        public void write(String message) {
            lastLine = message;
            if (message.startsWith("Round ")) {
                rounds++;
                assertEquals("Round " + rounds, message);
            }
            if (message.contains(" player rolled ")) {
                rolls++;
            }
            if (message.startsWith("Location of pieces ")) {
                statuses++;
            }
            if (message.endsWith(" player wins!!!")) {
                winners++;
            }
        }
    }

    private static final class CheckedRandom implements RandomSource {
        private final RandomNumbers random;
        Board board;

        CheckedRandom(int seed) {
            random = new RandomNumbers(seed);
        }

        public int nextInt(int bound) {
            checkBoard();
            return random.nextInt(bound);
        }

        void checkBoard() {
            if (board == null) {
                return;
            }
            for (Player player : board.getPlayers()) {
                int pieces = 0;
                for (Piece piece : player.getPieces()) {
                    pieces++;
                    assertTrue(piece.getLocation() >= -1);
                    assertTrue(piece.getLocation() <= 57);
                    assertTrue(piece.getCaptures() >= 0);
                    if (piece.isAtBase()) {
                        assertEquals(0, piece.getCaptures());
                        assertEquals(0, piece.getApproachVisits());
                    }
                    if (piece.isOnStandardPath()) {
                        assertTrue(board.opponentsAt(piece.getLocation(),
                                piece.getColour()).isEmpty());
                    }
                }
                assertEquals(4, pieces);
            }
        }
    }
}
