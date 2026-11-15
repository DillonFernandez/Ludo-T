// Tests mystery-cell timing, empty-cell selection, landing triggers and teleport destinations.
package ludot.mystery;

import ludot.model.Colour;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.movement.MoveExecutor;
import ludot.random.RandomSource;
import ludot.state.NormalState;
import ludot.support.GameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MysteryCellsTest {
    private final GameFixture game = new GameFixture();

    // Mystery spawning waits for a standard-path piece and two further completed
    // rounds.
    @Test
    void mysteryWaitsTwoRounds() {
        for (int round = 1; round <= 10; round++) {
            game.mystery.finishRound(round);
            assertFalse(game.mystery.isActive());
        }
        game.place(game.red, 1, 26, Direction.CLOCKWISE);
        game.mystery.finishRound(11);
        game.mystery.finishRound(12);
        assertFalse(game.mystery.isActive());
        game.mystery.finishRound(13);
        assertTrue(game.mystery.isActive());
        assertEquals(4, game.mystery.getRoundsRemaining());
    }

    // Every available random choice selects the expected unoccupied cell on the
    // standard path.
    @Test
    void mysterySpawnsOnStandardCells() {
        for (int choice = 0; choice < 51; choice++) {
            GameFixture scenario = new GameFixture();
            scenario.place(scenario.red, 1, 26, Direction.CLOCKWISE);
            scenario.random.add(choice);
            scenario.spawnMystery();
            int expected = choice < 26 ? choice : choice + 1;
            assertEquals(expected, scenario.mystery.getLocation());
            assertTrue(scenario.mystery.getLocation() >= 0);
            assertTrue(scenario.mystery.getLocation() < 52);
        }
    }

    // Mystery spawning skips occupied cell zero and selects empty cell one.
    @Test
    void mysteryAvoidsOccupiedCells() {
        game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.spawnMystery();
        assertEquals(1, game.mystery.getLocation());
    }

    // A new mystery cell starts with four rounds and stays in place as the
    // remaining count decreases.
    @Test
    void mysteryLastsFourRounds() {
        game.place(game.red, 1, 26, Direction.CLOCKWISE);
        game.spawnMystery();
        int original = game.mystery.getLocation();
        assertEquals(4, game.mystery.getRoundsRemaining());
        for (int round = 4; round <= 6; round++) {
            game.mystery.finishRound(round);
            assertEquals(original, game.mystery.getLocation());
            assertEquals(7 - round, game.mystery.getRoundsRemaining());
        }
    }

    // Expiry selects a different empty cell, resets four rounds and emits a new
    // spawn message.
    @Test
    void mysteryRelocatesAfterFourRounds() {
        game.place(game.red, 1, 26, Direction.CLOCKWISE);
        game.spawnMystery();
        int original = game.mystery.getLocation();
        for (int round = 4; round <= 6; round++) {
            game.mystery.finishRound(round);
        }
        assertEquals(1, game.output.lines.size());
        game.mystery.finishRound(7);
        assertNotEquals(original, game.mystery.getLocation());
        assertEquals(4, game.mystery.getRoundsRemaining());
        assertEquals(2, game.output.lines.size());
        assertEquals("A mystery cell has spawned in location 1 and will be "
                + "at this location for the next four rounds.", game.output.lines.get(1));
        assertTrue(game.board.piecesAt(game.mystery.getLocation(),
                Colour.RED).isEmpty());
    }

    // Repeated mystery relocations always choose an empty cell different from the
    // previous location.
    @Test
    void mysteryLocationsDiffer() {
        game.place(game.red, 1, 26, Direction.CLOCKWISE);
        game.spawnMystery();
        int previous = game.mystery.getLocation();
        for (int round = 4; round <= 43; round++) {
            game.mystery.finishRound(round);
            if ((round - 3) % 4 == 0) {
                assertNotEquals(previous, game.mystery.getLocation());
                assertTrue(game.board.piecesAt(game.mystery.getLocation(), Colour.RED).isEmpty());
                previous = game.mystery.getLocation();
            }
        }
    }

    // Deployment onto the mystery cell teleports Yellow to Beta and restricts
    // movement.
    @Test
    void deploymentTriggersMystery() {
        readyAtMystery(Direction.CLOCKWISE);
        Piece yellow = game.yellow.getPieces()[0];
        game.random.add(0, 1);
        game.executor.execute(game.rules.planPiece(yellow, 6));
        assertEquals(25, yellow.getLocation());
        assertFalse(yellow.canMove());
    }

    // Passing over the mystery cell leaves the piece at its ordinary destination
    // without teleporting.
    @Test
    void passingMysteryDoesNotTeleport() {
        Piece red = readyAtMystery(Direction.CLOCKWISE);
        game.executor.execute(game.rules.planPiece(red, 2));
        assertEquals(1, red.getLocation());
        assertFalse(game.output.text().contains("teleported"));
    }

    // Each destination choice uses the expected cell, message and number of random
    // calls.
    @Test
    void teleportHasSixDestinations() {
        String[] names = { "Alpha", "Beta", "Gamma", "Base", "X", "Approach" };
        int[] cells = { 7, 25, 44, -1, 26, 24 };
        for (int choice = 0; choice < 6; choice++) {
            GameFixture scenario = new GameFixture();
            Piece red = scenario.place(scenario.red, 1, 51, Direction.CLOCKWISE);
            scenario.spawnMystery();
            DestinationCheck random = new DestinationCheck(choice);
            MoveExecutor executor = new MoveExecutor(scenario.board, random,
                    scenario.messages, scenario.mystery);
            executor.execute(scenario.rules.planPiece(red, 1));
            assertEquals(cells[choice], red.getLocation());
            assertEquals(choice == 0 ? 2 : 1, random.calls);
            assertTrue(scenario.output.lines.contains("red player lands on a mystery cell "
                    + "and is teleported to " + names[choice] + "."));
        }
    }

    // Alpha is cell seven; teleporting there captures a lone opponent but is
    // blocked by an opposing block.
    @Test
    void alphaUsesTheCorrectCell() {
        Piece red = readyAtMystery(Direction.CLOCKWISE);
        game.random.add(0, 0);
        landOnMystery(red);
        assertEquals(7, red.getLocation());
        assertEquals((50 + 9) % 52, red.getLocation());

        red.applyState(new NormalState(), 1);
        red.moveTo(51, 0);
        Piece opponent = game.place(game.blue, 1, 7, Direction.CLOCKWISE);
        game.random.add(0, 0);
        assertTrue(game.executor.execute(game.rules.planPiece(red, 1)));
        assertTrue(opponent.isAtBase());
        assertEquals(1, red.getCaptures());

        red.applyState(new NormalState(), 1);
        red.moveTo(51, 0);
        game.place(game.blue, 1, 7, Direction.CLOCKWISE);
        game.place(game.blue, 2, 7, Direction.CLOCKWISE);
        game.random.add(0);
        assertFalse(game.executor.execute(game.rules.planPiece(red, 1)));
        assertEquals(0, red.getLocation());
        assertEquals(6, red.movementFor(6));
        assertEquals(2, game.board.opponentsAt(7, Colour.RED).size());
    }

    // Selecting Beta teleports the piece to cell 25, twenty-seven cells beyond
    // Yellow's approach.
    @Test
    void betaUsesTheCorrectCell() {
        Piece red = readyAtMystery(Direction.CLOCKWISE);
        game.random.add(1);
        landOnMystery(red);
        assertEquals(25, red.getLocation());
        assertEquals((50 + 27) % 52, red.getLocation());
    }

    // Gamma, Base, start and approach use the expected cells for each colour; only
    // Base clears captures.
    @Test
    void otherDestinationsMatchEachColour() {
        int[] starts = { 26, 39, 0, 13 };
        int[] approaches = { 24, 37, 50, 11 };
        for (int colour = 0; colour < 4; colour++) {
            for (int destination = 2; destination <= 5; destination++) {
                GameFixture scenario = new GameFixture();
                Player player = scenario.board.getPlayers()[colour];
                Piece piece = scenario.place(player, 1, 51, Direction.CLOCKWISE);
                piece.recordCapture();
                scenario.spawnMystery();
                scenario.random.add(destination);
                scenario.executor.execute(scenario.rules.planPiece(piece, 1));
                int[] expected = { 44, -1, starts[colour], approaches[colour] };
                assertEquals(expected[destination - 2], piece.getLocation());
                assertEquals(destination == 3 ? 0 : 1, piece.getCaptures());
            }
        }
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

    private static final class DestinationCheck implements RandomSource {
        private final int destination;
        int calls;

        DestinationCheck(int destination) {
            this.destination = destination;
        }

        public int nextInt(int bound) {
            calls++;
            if (calls == 1) {
                assertEquals(6, bound);
                return destination;
            }
            assertEquals(2, bound);
            assertEquals(2, calls);
            return 0;
        }
    }
}
