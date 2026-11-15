// Tests move priorities for Red, Green, Yellow and Blue, including blocks and mystery cells.
package ludot.behaviour;

import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.state.BriefingState;
import ludot.support.GameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStrategiesTest {
    private final GameFixture game = new GameFixture();

    // Red captures with an active piece before deploying, even when deployment
    // could also capture.
    @Test
    void redPrioritisesExistingCaptures() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece opponent = game.place(game.blue, 1, 6, Direction.CLOCKWISE);
        choose(new RedBehaviour(game.red, game.board), game.red, 6);
        assertEquals(6, red.getLocation());
        assertTrue(opponent.isAtBase());
        assertEquals(3, game.red.countAtBase());

        GameFixture competing = new GameFixture();
        Piece existing = competing.place(competing.red, 1, 0, Direction.CLOCKWISE);
        Piece sixCellsAway = competing.place(competing.green, 1, 6, Direction.CLOCKWISE);
        Piece atStartingCell = competing.place(competing.yellow, 1, 26, Direction.CLOCKWISE);
        assertTrue(competing.board.distanceFromHome(atStartingCell) < competing.board.distanceFromHome(sixCellsAway));
        new RedBehaviour(competing.red, competing.board).chooseMove(
                competing.rules.possibleMoves(competing.red, 6), competing.executor).execute();
        assertEquals(6, existing.getLocation());
        assertTrue(sixCellsAway.isAtBase());
        assertEquals(26, atStartingCell.getLocation());
        assertEquals(3, competing.red.countAtBase());
    }

    // Red chooses the capture whose opponent has the shortest remaining route to
    // Home.
    @Test
    void redCapturesTheOpponentNearestHome() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.red, 2, 10, Direction.CLOCKWISE);
        Piece nearHome = game.place(game.blue, 1, 2, Direction.CLOCKWISE);
        Piece farFromHome = game.place(game.green, 1, 12, Direction.CLOCKWISE);
        choose(new RedBehaviour(game.red, game.board), game.red, 2);
        assertEquals(2, first.getLocation());
        assertEquals(10, second.getLocation());
        assertTrue(nearHome.isAtBase());
        assertFalse(farFromHome.isAtBase());
    }

    // Red deploys a base piece on six when no capture is available.
    @Test
    void redDeploysWhenSixCannotCapture() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        choose(new RedBehaviour(game.red, game.board), game.red, 6);
        assertEquals(0, first.getLocation());
        assertEquals(26, game.red.getPieces()[1].getLocation());
    }

    // Red avoids forming a block unless the alternative piece cannot move.
    @Test
    void redAvoidsNewBlocks() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.red, 2, 2, Direction.CLOCKWISE);
        choose(new RedBehaviour(game.red, game.board), game.red, 2);
        assertEquals(0, first.getLocation());
        assertEquals(4, second.getLocation());
        second.moveTo(2, 0);
        second.applyState(new BriefingState(), 1);
        choose(new RedBehaviour(game.red, game.board), game.red, 2);
        assertEquals(2, first.getLocation());
        assertEquals(2, game.board.companions(first).size());
    }

    // Green forms a block before deploying, then deploys when no new block is
    // available.
    @Test
    void greenCreatesBlocksBeforeDeploying() {
        Piece first = game.place(game.green, 1, 10, Direction.CLOCKWISE);
        Piece second = game.place(game.green, 2, 16, Direction.CLOCKWISE);
        choose(new GreenBehaviour(game.green, game.board), game.green, 6);
        assertEquals(16, first.getLocation());
        assertEquals(16, second.getLocation());
        assertEquals(2, game.green.countAtBase());
        choose(new GreenBehaviour(game.green, game.board), game.green, 6);
        assertEquals(39, game.green.getPieces()[2].getLocation());
        assertEquals(1, game.green.countAtBase());
    }

    // Green sends a separate piece Home rather than breaking an existing block.
    @Test
    void greenFinishesOtherPiecesFirst() {
        Piece first = game.place(game.green, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.green, 2, 0, Direction.CLOCKWISE);
        Piece other = game.place(game.green, 3, 56, Direction.CLOCKWISE);
        choose(new GreenBehaviour(game.green, game.board), game.green, 1);
        assertTrue(other.isAtHome());
        assertEquals(0, first.getLocation());
        assertEquals(0, second.getLocation());
        assertEquals(2, game.board.companions(first).size());
    }

    // Green advances a movable block instead of a separate piece nearer Home.
    @Test
    void greenPrioritisesBlockMovement() {
        Piece first = game.place(game.green, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.green, 2, 0, Direction.CLOCKWISE);
        Piece other = game.place(game.green, 3, 30, Direction.CLOCKWISE);
        choose(new GreenBehaviour(game.green, game.board), game.green, 2);
        assertEquals(1, first.getLocation());
        assertEquals(1, second.getLocation());
        assertEquals(30, other.getLocation());
    }

    // Green preserves a block while another piece can move, but separates it when
    // necessary.
    @Test
    void greenBreaksBlocksWhenRequired() {
        Piece first = game.place(game.green, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.green, 2, 0, Direction.CLOCKWISE);
        Piece other = game.place(game.green, 3, 10, Direction.CLOCKWISE);
        GreenBehaviour behaviour = new GreenBehaviour(game.green, game.board);
        choose(behaviour, game.green, 1);
        assertEquals(0, first.getLocation());
        assertEquals(11, other.getLocation());

        GameFixture competing = new GameFixture();
        Piece backOne = competing.place(competing.green, 1, 0, Direction.CLOCKWISE);
        Piece backTwo = competing.place(competing.green, 2, 0, Direction.CLOCKWISE);
        Piece inFront = competing.place(competing.green, 3, 1, Direction.CLOCKWISE);
        new GreenBehaviour(competing.green, competing.board).chooseMove(
                competing.rules.possibleMoves(competing.green, 1), competing.executor).execute();
        assertEquals(0, backOne.getLocation());
        assertEquals(0, backTwo.getLocation());
        assertEquals(2, inFront.getLocation());
        assertEquals(2, competing.board.companions(backOne).size());
        other.applyState(new BriefingState(), 1);
        choose(behaviour, game.green, 1);
        assertEquals(1, first.getLocation());
        assertEquals(0, second.getLocation());
        assertEquals(11, other.getLocation());
    }

    // Yellow deploys on six instead of capturing with an active piece.
    @Test
    void yellowPrioritisesDeployment() {
        Piece first = game.place(game.yellow, 1, 10, Direction.CLOCKWISE);
        Piece opponent = game.place(game.blue, 1, 16, Direction.CLOCKWISE);
        choose(new YellowBehaviour(game.yellow, game.board), game.yellow, 6);
        assertEquals(10, first.getLocation());
        assertFalse(opponent.isAtBase());
        assertEquals(0, game.yellow.getPieces()[1].getLocation());
    }

    // Yellow chooses a piece needing a capture over another piece already eligible
    // for Home.
    @Test
    void yellowPrioritisesNeededCaptures() {
        Piece needsCapture = game.place(game.yellow, 1, 0, Direction.CLOCKWISE);
        Piece almostHome = game.place(game.yellow, 2, 49, Direction.CLOCKWISE);
        almostHome.recordCapture();
        Piece opponent = game.place(game.blue, 1, 2, Direction.CLOCKWISE);
        choose(new YellowBehaviour(game.yellow, game.board), game.yellow, 2);
        assertEquals(2, needsCapture.getLocation());
        assertEquals(49, almostHome.getLocation());
        assertTrue(opponent.isAtBase());
    }

    // Yellow advances the piece nearest Home, accounting for its restored direction
    // after leaving a moved block.
    @Test
    void yellowMovesThePieceNearestHome() {
        Piece first = game.place(game.yellow, 1, 0, Direction.CLOCKWISE);
        Piece almostHome = game.place(game.yellow, 2, 49, Direction.CLOCKWISE);
        almostHome.recordCapture();
        choose(new YellowBehaviour(game.yellow, game.board), game.yellow, 2);
        assertEquals(0, first.getLocation());
        assertEquals(52, almostHome.getLocation());

        choose(new YellowBehaviour(game.yellow, game.board), game.yellow, 2);
        assertEquals(0, first.getLocation());
        assertEquals(54, almostHome.getLocation());

        GameFixture reversed = new GameFixture();
        Piece clockwise = reversed.place(reversed.yellow, 1, 51, Direction.CLOCKWISE);
        Piece counterclockwise = reversed.place(reversed.yellow, 2, 51,
                Direction.COUNTERCLOCKWISE);
        clockwise.recordCapture();
        counterclockwise.recordCapture();
        counterclockwise.moveTo(51, 1);
        reversed.spawnMystery();
        reversed.random.add(2, 2);
        reversed.executor.execute(reversed.rules.planBlock(
                reversed.board.companions(clockwise), 2));
        assertEquals(44, clockwise.getLocation());
        assertEquals(44, counterclockwise.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, clockwise.getDirection());
        assertTrue(clockwise.hasMovedInBlock());
        Piece other = reversed.place(reversed.yellow, 3, 20, Direction.CLOCKWISE);
        other.recordCapture();
        assertEquals(12, reversed.board.distanceFromHome(clockwise,
                Direction.CLOCKWISE));
        assertEquals(36, reversed.board.distanceFromHome(other));

        new YellowBehaviour(reversed.yellow, reversed.board).chooseMove(
                reversed.rules.possibleMoves(reversed.yellow, 1), reversed.executor).execute();

        assertEquals(45, clockwise.getLocation());
        assertEquals(Direction.CLOCKWISE, clockwise.getDirection());
        assertFalse(clockwise.hasMovedInBlock());
        assertEquals(44, counterclockwise.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, counterclockwise.getDirection());
        assertEquals(20, other.getLocation());
    }

    // Blue moves pieces in number order and returns to the first after the fourth.
    @Test
    void blueCyclesThroughPieces() {
        for (int number = 1; number <= 4; number++) {
            game.place(game.blue, number, number * 10, Direction.CLOCKWISE);
        }
        BlueBehaviour behaviour = new BlueBehaviour(game.blue, game.board, game.mystery);
        for (int turn = 0; turn < 5; turn++) {
            int selected = turn % 4;
            int[] before = new int[4];
            for (int index = 0; index < 4; index++) {
                before[index] = game.blue.getPieces()[index].getLocation();
            }
            choose(behaviour, game.blue, 1);
            for (int index = 0; index < 4; index++) {
                assertEquals(before[index] + (index == selected ? 1 : 0),
                        game.blue.getPieces()[index].getLocation());
            }
        }
    }

    // Blue favours a counter-clockwise mystery landing, which returns the selected
    // piece to Base.
    @Test
    void blueSeeksCounterclockwiseMystery() {
        Piece first = game.place(game.blue, 1, 10, Direction.CLOCKWISE);
        Piece second = game.place(game.blue, 2, 1, Direction.COUNTERCLOCKWISE);
        game.spawnMystery();
        game.random.add(3);
        choose(new BlueBehaviour(game.blue, game.board, game.mystery),
                game.blue, 1);
        assertEquals(10, first.getLocation());
        assertTrue(second.isAtBase());
    }

    // Blue avoids a clockwise mystery landing when another piece can move, but uses
    // it otherwise.
    @Test
    void blueAvoidsClockwiseMystery() {
        Piece first = game.place(game.blue, 1, 51, Direction.CLOCKWISE);
        Piece second = game.place(game.blue, 2, 5, Direction.CLOCKWISE);
        game.spawnMystery();
        choose(new BlueBehaviour(game.blue, game.board, game.mystery),
                game.blue, 1);
        assertEquals(51, first.getLocation());
        assertEquals(6, second.getLocation());
        second.applyState(new BriefingState(), 1);
        game.random.add(4);
        choose(new BlueBehaviour(game.blue, game.board, game.mystery), game.blue, 1);
        assertEquals(13, first.getLocation());
        assertEquals(6, second.getLocation());
    }

    private void choose(PlayerBehaviour behaviour, Player player, int roll) {
        behaviour.chooseMove(game.rules.possibleMoves(player, roll),
                game.executor).execute();
    }
}
