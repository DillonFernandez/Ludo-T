// Tests the wording, order and console formatting of game messages and round status.
package ludot.output;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import ludot.behaviour.RedBehaviour;
import ludot.game.LudoGame;
import ludot.game.LudoGameFactory;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.state.NormalState;
import ludot.support.GameFixture;
import ludot.support.RecordedOutput;
import ludot.support.ScriptedRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleMessagesTest {
        private final GameFixture game = new GameFixture();

        // Introductions, starting rolls, starting-player selection and turn order match
        // the required text.
        @Test
        void initialMessagesMatchRequirements() {
                ScriptedRandom random = new ScriptedRandom();
                random.add(5, 4, 3, 2, 0);
                RecordedOutput output = new RecordedOutput();
                LudoGame simulation = new LudoGameFactory().createGame(random, output);
                prepareWinner(simulation.getBoard().getPlayers()[0]);
                simulation.play();
                String[] expected = {
                                "The red player has four (04) pieces named R1, R2, R3, and R4.",
                                "The green player has four (04) pieces named G1, G2, G3, and G4.",
                                "The yellow player has four (04) pieces named Y1, Y2, Y3, and Y4.",
                                "The blue player has four (04) pieces named B1, B2, B3, and B4.",
                                "red rolls 6", "green rolls 5", "yellow rolls 4", "blue rolls 3",
                                "red player has the highest roll and will begin the game.",
                                "The order of a single round is red, green, yellow, and blue."
                };
                for (int index = 0; index < expected.length; index++) {
                        assertEquals(expected[index], output.lines.get(index));
                }
        }

        // Roll and deployment messages match, with the expected console spacing,
        // headings and line wrapping.
        @Test
        void diceAndDeploymentMessagesMatch() {
                game.random.add(5, 0, 0);
                playRedTurn();
                assertEquals("red player rolled 6.", game.output.lines.get(0));
                assertEquals("red player moves piece R1 to the starting point.", game.output.lines.get(1));
                assertEquals("red player now has 1/4 on pieces on the board "
                                + "and 3/4 pieces on the base.", game.output.lines.get(2));
                assertEquals("red player rolled 1.", game.output.lines.get(3));
                PrintStream originalOutput = System.out;
                ByteArrayOutputStream captured = new ByteArrayOutputStream();
                try {
                        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                        GameMessages messages = new GameMessages(new ConsoleOutput());
                        messages.roll(game.green, 4);
                        messages.roundStarted(2);
                        messages.roll(game.red, 6);
                        messages.entry(game.red.getPieces()[0]);
                        messages.briefingReturn(game.red.getPieces()[0]);
                        messages.roll(game.red, 2);
                        messages.winner(game.red);
                } finally {
                        System.setOut(originalOutput);
                }
                String divider = "===============================================";
                String expected = String.join(System.lineSeparator(),
                                "green player rolled 4.", "", divider, "Round 2", divider,
                                "red player rolled 6.",
                                "    red player moves piece R1 to the starting point.",
                                "    red piece 1 is movement-restricted and has rolled three consecutively. Teleporting piece 1",
                                "    to base.", "",
                                "red player rolled 2.", "", divider, "red player wins!!!", divider, "");
                assertEquals(expected,
                                captured.toString(StandardCharsets.UTF_8));
        }

        // Counter-clockwise movement, capture and updated piece counts appear in the
        // expected order and wording.
        @Test
        void movementAndCaptureMessagesMatch() {
                Piece red = game.place(game.red, 1, 5, Direction.COUNTERCLOCKWISE);
                game.place(game.blue, 2, 3, Direction.CLOCKWISE);
                game.executor.execute(game.rules.planPiece(red, 2));
                assertEquals("red moves piece R1 from location 5 to 3 by 2 units "
                                + "in counter-clockwise direction.", game.output.lines.get(0));
                assertEquals("red piece 1 lands on square 3, captures blue piece 2, "
                                + "and returns it to the base.", game.output.lines.get(1));
                assertEquals("red player now has 1/4 on pieces on the board "
                                + "and 3/4 pieces on the base.", game.output.lines.get(2));
        }

        // Blocked moves report either an ignored throw or movement to the cell
        // immediately before the block.
        @Test
        void blockedMovementMessagesMatch() {
                Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
                game.place(game.blue, 1, 4, Direction.CLOCKWISE);
                game.place(game.blue, 2, 4, Direction.CLOCKWISE);
                game.random.add(3);
                playRedTurn();
                assertEquals("red piece 1 is blocked from moving from 0 to 4 "
                                + "by blue piece 1.", game.output.lines.get(1));
                assertEquals("red does not have other pieces in the board "
                                + "to move instead of the blocked piece.", game.output.lines.get(2));
                assertEquals("Ignoring the throw and moving on to the next player.",
                                game.output.lines.get(3));
                game.output.lines.clear();
                game.executor.execute(game.rules.planPiece(red, 6));
                assertEquals("red piece 1 is blocked from moving from 0 to 6 "
                                + "by blue piece 1.", game.output.lines.get(0));
                assertEquals("red does not have other pieces in the board "
                                + "to move instead of the blocked piece.", game.output.lines.get(1));
                assertEquals("Moved the piece to square 3 which is the cell before the block.",
                                game.output.lines.get(2));
        }

        // All teleport destinations and Alpha, Beta and Gamma effects emit the expected
        // messages.
        @Test
        void mysteryAndEffectMessagesMatch() {
                String[] names = { "Alpha", "Beta", "Gamma", "Base", "X", "Approach" };
                for (int destination = 0; destination < 6; destination++) {
                        GameFixture scenario = new GameFixture();
                        Piece red = scenario.place(scenario.red, 1, 51, Direction.CLOCKWISE);
                        scenario.spawnMystery();
                        assertEquals("A mystery cell has spawned in location 0 and will be "
                                        + "at this location for the next four rounds.", scenario.output.lines.get(0));
                        scenario.random.add(destination);
                        if (destination == 0) {
                                scenario.random.add(0);
                        }
                        scenario.executor.execute(scenario.rules.planPiece(red, 1));
                        assertEquals("red player lands on a mystery cell and is teleported to "
                                        + names[destination] + ".", scenario.output.lines.get(2));
                        assertEquals("red piece 1 teleported to " + names[destination] + ".",
                                        scenario.output.lines.get(3));
                        if (destination == 0) {
                                assertEquals("red piece 1 feels energized, and movement speed doubles.",
                                                scenario.output.lines.get(4));
                        } else if (destination == 1) {
                                assertEquals("red piece 1 attends briefing and cannot move for four rounds.",
                                                scenario.output.lines.get(4));
                                scenario.random.add(2, 2);
                                scenario.turns.playTurn(scenario.red, new RedBehaviour(scenario.red, scenario.board));
                                scenario.turns.playTurn(scenario.red, new RedBehaviour(scenario.red, scenario.board));
                                assertTrue(scenario.output.lines.contains("red piece 1 is movement-restricted "
                                                + "and has rolled three consecutively. Teleporting piece 1 to base."));
                        } else if (destination == 2) {
                                assertEquals("The red piece 1, which was moving clockwise, "
                                                + "has changed to moving counterclockwise.",
                                                scenario.output.lines.get(4));
                        }
                }
                Piece red = readyAtMystery(Direction.CLOCKWISE);
                game.random.add(0, 1);
                landOnMystery(red);
                assertTrue(game.output.lines.contains("red piece 1 feels sick, and movement speed halves."));
                red.applyState(new NormalState(), 1);
                red.moveTo(1, 0);
                red.turnCounterclockwise();
                game.random.add(2);
                landOnMystery(red);
                assertTrue(game.output.lines.contains("The red piece 1 is moving in a counterclockwise "
                                + "direction. Teleporting to Beta from Gamma."));
        }

        // Round status lists piece counts and locations, mystery lifetime and correctly
        // formatted console lines.
        @Test
        void roundStatusListsPieceLocations() {
                Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
                game.red.getPieces()[1].moveTo(52, 0);
                game.red.getPieces()[2].moveTo(57, 0);
                game.spawnMystery();
                game.output.lines.clear();
                game.messages.roundStatus(game.board, game.mystery);
                assertEquals("red player now has 2/4 on pieces on the board "
                                + "and 1/4 pieces on the base.", game.output.lines.get(0));
                assertEquals("============================", game.output.lines.get(1));
                assertEquals("Location of pieces red", game.output.lines.get(2));
                assertEquals("============================", game.output.lines.get(3));
                assertEquals("Piece 1 -> 0.", game.output.lines.get(4));
                assertEquals("Piece 2 -> redhomepath0.", game.output.lines.get(5));
                assertEquals("Piece 3 -> Home.", game.output.lines.get(6));
                assertEquals("Piece 4 -> Base.", game.output.lines.get(7));
                assertEquals("The mystery cell is at 1 and will be at that location "
                                + "for the next 4 values.", game.output.lines.get(8));
                assertEquals(36, game.output.lines.size());
                assertEquals("redhomepath4", game.messages.location(red, 56));

                PrintStream originalOutput = System.out;
                ByteArrayOutputStream captured = new ByteArrayOutputStream();
                try {
                        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
                        GameMessages messages = new GameMessages(new ConsoleOutput());
                        messages.roundStatus(game.board, game.mystery);
                } finally {
                        System.setOut(originalOutput);
                }
                String console = captured.toString(StandardCharsets.UTF_8);
                for (Player player : game.board.getPlayers()) {
                        assertTrue(console.contains(System.lineSeparator() + "----------- Location of pieces "
                                        + player.getColour().getName() + " -----------" + System.lineSeparator()));
                }
                assertFalse(console.contains("============================"));
                assertTrue(console.contains("    Piece 1 -> 0." + System.lineSeparator()));
                assertTrue(console.contains("    Piece 4 -> Base." + System.lineSeparator()));
                assertTrue(console.contains(System.lineSeparator() + System.lineSeparator()
                                + "green player now has 0/4 on pieces on the board and 4/4 pieces on the base."));
                assertTrue(console.contains("    The mystery cell is at 1 and will be at that location "
                                + "for the next 4 values."));
                for (String line : console.split(System.lineSeparator())) {
                        assertTrue(line.length() <= 96, "Console line exceeds the agreed width: " + line);
                        assertFalse(line.contains("\t"), "Alignment must use spaces rather than tabs.");
                }
        }

        private void playRedTurn() {
                game.turns.playTurn(game.red, new RedBehaviour(game.red, game.board));
        }

        private Piece readyAtMystery(Direction direction) {
                Piece red = game.place(game.red, 1, 51, direction);
                game.spawnMystery();
                assertEquals(0, game.mystery.getLocation());
                if (direction == Direction.COUNTERCLOCKWISE) {
                        red.moveTo(1, 0);
                }
                return red;
        }

        private void landOnMystery(Piece piece) {
                game.executor.execute(game.rules.planPiece(piece, 1));
        }

        private static void prepareWinner(Player player) {
                for (Piece piece : player.getPieces()) {
                        piece.moveTo(57, 0);
                }
                player.getPieces()[0].moveTo(56, 0);
        }

}
