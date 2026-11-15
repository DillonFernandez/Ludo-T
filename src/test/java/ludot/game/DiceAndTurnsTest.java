// Tests dice ranges, starting order, deployment, extra rolls and completed-round counting.
package ludot.game;

import ludot.behaviour.GreenBehaviour;
import ludot.behaviour.RedBehaviour;
import ludot.config.GameSettings;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.movement.MovePlan;
import ludot.output.GameOutput;
import ludot.random.RandomNumbers;
import ludot.support.GameFixture;
import ludot.support.RecordedOutput;
import ludot.support.ScriptedRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiceAndTurnsTest {
    private final GameFixture game = new GameFixture();

    // Seeded dice produce every value from one to six, and turns report each
    // supplied roll correctly.
    @Test
    void diceStayWithinRange() {
        RandomNumbers random = new RandomNumbers(1234);
        boolean[] seen = new boolean[7];
        for (int attempt = 0; attempt < 600; attempt++) {
            int roll = random.nextInt(6) + 1;
            assertTrue(roll >= 1 && roll <= 6);
            seen[roll] = true;
        }
        for (int roll = 1; roll <= 6; roll++) {
            assertTrue(seen[roll], "Missing dice value " + roll);
            GameFixture scenario = new GameFixture();
            scenario.random.add(roll - 1);
            scenario.turns.playTurn(scenario.red,
                    new RedBehaviour(scenario.red, scenario.board));
            assertEquals("red player rolled " + roll + ".", scenario.output.lines.get(0));
        }
    }

    // Yellow starts after the highest initial roll and wins without any further
    // starting rolls.
    @Test
    void highestRollStarts() {
        ScriptedRandom random = new ScriptedRandom();
        random.add(1, 2, 5, 3, 0);
        RecordedOutput output = new RecordedOutput();
        LudoGame simulation = new LudoGameFactory().createGame(random, output);
        prepareWinner(simulation.getBoard().getPlayers()[2]);
        simulation.play();
        assertEquals("yellow player has the highest roll and will begin the game.",
                output.lines.get(8));
        assertEquals("Round 1", output.lines.get(10));
        assertEquals("yellow player rolled 1.", output.lines.get(11));
        assertEquals("yellow player wins!!!", output.lines.get(output.lines.size() - 1));
        assertEquals(5, random.calls);
    }

    // Only players tied for the highest roll reroll, and the winner determines the
    // turn order.
    @Test
    void tiedPlayersReroll() {
        ScriptedRandom random = new ScriptedRandom();
        random.add(5, 5, 1, 0, 2, 4, 0);
        RecordedOutput output = new RecordedOutput();
        LudoGame game = new LudoGameFactory().createGame(random, output);
        prepareWinner(game.getBoard().getPlayers()[1]);

        game.play();

        assertEquals("red rolls 6", output.lines.get(4));
        assertEquals("green rolls 6", output.lines.get(5));
        assertEquals("yellow rolls 2", output.lines.get(6));
        assertEquals("blue rolls 1", output.lines.get(7));
        assertEquals("red rolls 3", output.lines.get(8));
        assertEquals("green rolls 5", output.lines.get(9));
        assertEquals("The order of a single round is green, yellow, blue, and red.",
                output.lines.get(11));
        assertEquals("green player wins!!!",
                output.lines.get(output.lines.size() - 1));
    }

    // The announced turn order follows the same colour cycle from each possible
    // starting player.
    @Test
    void turnsPassClockwise() {
        String[] orders = {
                "red, green, yellow, and blue.",
                "green, yellow, blue, and red.",
                "yellow, blue, red, and green.",
                "blue, red, green, and yellow."
        };
        for (int starter = 0; starter < 4; starter++) {
            ScriptedRandom random = new ScriptedRandom();
            for (int index = 0; index < 4; index++) {
                random.add(index == starter ? 5 : index);
            }
            RecordedOutput output = new RecordedOutput();
            LudoGame game = new LudoGameFactory().createGame(random, output);
            prepareWinner(game.getBoard().getPlayers()[starter]);
            game.play();
            assertEquals("The order of a single round is " + orders[starter],
                    output.lines.get(9));
        }
    }

    // Extra rolls remain within one completed round, with one status per player and
    // one winning message.
    @Test
    void bonusRollsStayInTheRound() {
        ScriptedRandom random = new ScriptedRandom();
        random.add(5, 4, 3, 2, 5, 0, 5, 0, 0, 0, 0);
        CountedOutput output = new CountedOutput();
        LudoGame simulation = new LudoGameFactory().createGame(random, output);
        prepareWinner(simulation.getBoard().getPlayers()[3]);
        simulation.play();
        assertEquals(1, simulation.getCompletedRounds());
        assertEquals(6, output.rolls);
        assertEquals(1, output.rounds);
        assertEquals(4, output.statuses);
        assertEquals(11, random.calls);
        assertEquals("blue player wins!!!", output.lastLine);
        assertEquals(1, output.winners);
    }

    // A roll of one leaves all Red pieces at Base and ends the turn after one roll.
    @Test
    void basePiecesNeedASix() {
        playRedTurn();
        assertEquals(4, game.red.countAtBase());
        assertEquals(1, game.random.calls);
        assertEquals("red player rolled 1.", game.output.text());
    }

    // Each colour can deploy only on six and enters at its assigned starting cell.
    @Test
    void sixDeploysToX() {
        for (Player player : game.board.getPlayers()) {
            Piece piece = player.getPieces()[0];
            for (int roll = 1; roll < 6; roll++) {
                assertFalse(game.rules.planPiece(piece, roll).legal);
            }
            MovePlan entry = game.rules.planPiece(piece, 6);
            assertTrue(entry.legal);
            assertTrue(entry.entry);
            game.executor.execute(entry);
            assertEquals(GameSettings.getInstance().getStartingCell(player.getColour()),
                    piece.getLocation());
            assertEquals(3, player.countAtBase());
        }
    }

    // An active piece advances six cells without deploying a piece from Base.
    @Test
    void sixMovesAnActivePiece() {
        Piece red = game.place(game.red, 1, 10, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 6);
        assertTrue(move.legal);
        assertFalse(move.entry);
        game.executor.execute(move);
        assertEquals(16, red.getLocation());
        assertEquals(3, game.red.countAtBase());
    }

    // Two consecutive sixes each grant another roll, producing fourteen cells of
    // movement.
    @Test
    void sixesGrantBonusRolls() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        for (int index = 1; index < 4; index++) {
            game.red.getPieces()[index].moveTo(57, 0);
        }
        game.random.add(5, 5, 1);
        playRedTurn();
        assertEquals(14, red.getLocation());
        assertEquals(3, game.random.calls);
        assertEquals(2, countLines(game.output, "red player rolled 6."));
        assertEquals(1, countLines(game.output, "red player rolled 2."));
    }

    // A third consecutive six adds no movement and ends the turn before the next
    // player rolls.
    @Test
    void thirdSixEndsTheTurn() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        for (int index = 1; index < 4; index++) {
            game.red.getPieces()[index].moveTo(57, 0);
        }
        game.random.add(5, 5, 5);
        playRedTurn();
        assertEquals(12, red.getLocation());
        assertEquals(3, game.random.calls);
        game.random.add(0);
        game.turns.playTurn(game.green, new GreenBehaviour(game.green, game.board));
        assertEquals(4, game.random.calls);
        assertEquals("green player rolled 1.",
                game.output.lines.get(game.output.lines.size() - 1));
    }

    private void playRedTurn() {
        game.turns.playTurn(game.red, new RedBehaviour(game.red, game.board));
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

    private int countLines(RecordedOutput output, String expected) {
        int count = 0;
        for (String line : output.lines) {
            if (line.equals(expected)) {
                count++;
            }
        }
        return count;
    }
}
