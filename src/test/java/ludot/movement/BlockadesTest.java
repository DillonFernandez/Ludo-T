// Tests block formation, shared movement, capture rules, direction restoration and forced splitting.
package ludot.movement;

import java.util.List;
import ludot.behaviour.GreenBehaviour;
import ludot.behaviour.PlayerBehaviour;
import ludot.behaviour.RedBehaviour;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.state.BriefingState;
import ludot.state.EnergisedState;
import ludot.state.SickState;
import ludot.support.GameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockadesTest {
    private final GameFixture game = new GameFixture();

    // Landing on a friendly piece forms a block without recording a capture.
    @Test
    void friendlyPiecesShareACell() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.red, 2, 3, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(first, 3);
        assertTrue(move.legal);
        assertTrue(move.formsBlock);
        assertFalse(game.executor.execute(move));
        assertEquals(second.getLocation(), first.getLocation());
        assertEquals(2, game.red.countOnBoard());
    }

    // Move generation includes four individual plans and one shared plan for a
    // two-piece block.
    @Test
    void movesIncludePiecesAndBlocks() {
        game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.red, 2, 0, Direction.CLOCKWISE);
        List<MovePlan> moves = game.rules.possibleMoves(game.red, 6);
        assertEquals(5, moves.size());
        int blocks = 0;
        for (MovePlan move : moves) {
            if (move.pieces.size() > 1) {
                blocks++;
            }
        }
        assertEquals(1, blocks);
    }

    // Blocks of two to four pieces include every member and divide six steps by
    // their size.
    @Test
    void largerBlocksIncludeAllMembers() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        for (int number = 2; number <= 4; number++) {
            game.place(game.red, number, 0, Direction.CLOCKWISE);
            assertEquals(number, game.board.companions(first).size());
            MovePlan block = game.rules.planBlock(game.board.companions(first), 6);
            assertTrue(block.legal);
            assertEquals(number, block.pieces.size());
            assertEquals(6 / number, block.steps);
        }
    }

    // A clockwise six-step plan stops at cell three before the opposing block at
    // cell four.
    @Test
    void blockStopsClockwiseMovement() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.blue, 1, 4, Direction.CLOCKWISE);
        game.place(game.blue, 2, 4, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 6);
        assertTrue(move.legal);
        assertTrue(move.blocked);
        assertEquals(6, move.intendedDestination);
        assertEquals(3, move.destination);
        assertEquals(3, move.steps);
        assertEquals(2, move.blockers.size());
    }

    // A counter-clockwise six-step plan stops at cell three before the opposing
    // block at cell two.
    @Test
    void blockStopsCounterclockwiseMovement() {
        Piece red = game.place(game.red, 1, 6, Direction.COUNTERCLOCKWISE);
        game.place(game.blue, 1, 2, Direction.CLOCKWISE);
        game.place(game.blue, 2, 2, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 6);
        assertTrue(move.legal);
        assertEquals(3, move.destination);
    }

    // A single piece cannot land on an opposing block or pass a block immediately
    // ahead.
    @Test
    void pieceCannotLandOnABlock() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.blue, 1, 4, Direction.CLOCKWISE);
        game.place(game.blue, 2, 4, Direction.CLOCKWISE);
        assertFalse(game.rules.planPiece(red, 4).legal);
        red.moveTo(3, 0);
        assertFalse(game.rules.planPiece(red, 6).legal);
    }

    // Execution stops before a blockade, even when the full move would overshoot
    // Home in either direction.
    @Test
    void pieceStopsBeforeABlock() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.blue, 1, 4, Direction.CLOCKWISE);
        game.place(game.blue, 2, 4, Direction.CLOCKWISE);
        game.executor.execute(game.rules.planPiece(first, 6));
        assertEquals(3, first.getLocation());
        assertTrue(game.output.text().contains(
                "Moved the piece to square 3 which is the cell before the block."));

        GameFixture nearHome = new GameFixture();
        Piece yellow = nearHome.place(nearHome.yellow, 1, 48, Direction.CLOCKWISE);
        yellow.recordCapture();
        yellow.applyState(new EnergisedState(), 1);
        assertFalse(nearHome.rules.planPiece(yellow, 6).legal);
        nearHome.place(nearHome.red, 1, 50, Direction.CLOCKWISE);
        nearHome.place(nearHome.red, 2, 50, Direction.CLOCKWISE);
        MovePlan stopped = nearHome.rules.planPiece(yellow, 6);
        assertTrue(stopped.legal);
        assertTrue(stopped.blocked);
        assertEquals(49, stopped.destination);
        assertEquals(1, stopped.steps);
        nearHome.executor.execute(stopped);
        assertEquals(49, yellow.getLocation());
        assertFalse(yellow.isAtHome());
        assertEquals(2, nearHome.board.opponentsAt(50, yellow.getColour()).size());
        yellow.chooseStartingDirection(Direction.COUNTERCLOCKWISE);
        yellow.moveTo(0, 1);
        MovePlan counterclockwise = nearHome.rules.planPiece(yellow, 6);
        assertTrue(counterclockwise.legal);
        assertTrue(counterclockwise.blocked);
        assertEquals(51, counterclockwise.destination);
        nearHome.executor.execute(counterclockwise);
        assertEquals(51, yellow.getLocation());
        assertEquals(1, yellow.getApproachVisits());
    }

    // Only a blockade shortens a move, and Red chooses available deployment over
    // that partial move.
    @Test
    void partialMovesNeedABlockade() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        MovePlan full = game.rules.planPiece(red, 6);
        assertEquals(6, full.steps);
        assertEquals(6, full.destination);
        assertFalse(full.blocked);
        game.place(game.blue, 1, 4, Direction.CLOCKWISE);
        game.place(game.blue, 2, 4, Direction.CLOCKWISE);
        MovePlan partial = game.rules.planPiece(red, 6);
        assertEquals(3, partial.destination);
        assertEquals(3, partial.steps);
        assertTrue(partial.blocked);
        Piece other = game.place(game.red, 2, 10, Direction.CLOCKWISE);
        choose(new RedBehaviour(game.red, game.board), game.red, 6);
        assertEquals(0, red.getLocation());
        assertEquals(10, other.getLocation());
        assertEquals(26, game.red.getPieces()[2].getLocation());
    }

    // A two-piece block may land on and capture an opposing two-piece block.
    @Test
    void equalBlocksCapture() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.red, 2, 0, Direction.CLOCKWISE);
        game.place(game.blue, 1, 3, Direction.CLOCKWISE);
        game.place(game.blue, 2, 3, Direction.CLOCKWISE);
        MovePlan move = game.rules.planBlock(game.board.companions(first), 6);
        assertTrue(move.legal);
        assertTrue(move.capturesOpponent);
        assertEquals(3, move.destination);
    }

    // A two-piece block cannot land on an opposing three-piece block.
    @Test
    void unequalBlocksCannotCapture() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.red, 2, 0, Direction.CLOCKWISE);
        for (int number = 1; number <= 3; number++) {
            game.place(game.blue, number, 3, Direction.CLOCKWISE);
        }
        assertFalse(game.rules.planBlock(game.board.companions(first), 6).legal);
    }

    // Capturing a block returns both opponents to Base and clears captures, effects
    // and block-movement history.
    @Test
    void capturedBlockReturnsToBase() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.red, 2, 0, Direction.CLOCKWISE);
        Piece enemyOne = game.place(game.blue, 1, 4, Direction.CLOCKWISE);
        Piece enemyTwo = game.place(game.blue, 2, 4, Direction.COUNTERCLOCKWISE);
        game.executor.execute(game.rules.planBlock(game.board.companions(enemyOne), 2));
        assertEquals(3, enemyOne.getLocation());
        assertTrue(enemyOne.hasMovedInBlock());
        assertTrue(enemyTwo.hasMovedInBlock());
        enemyOne.recordCapture();
        enemyTwo.applyState(new SickState(), 1);
        game.executor.execute(game.rules.planBlock(game.board.companions(first), 6));
        assertTrue(enemyOne.isAtBase());
        assertTrue(enemyTwo.isAtBase());
        assertEquals(4, game.blue.countAtBase());
        assertEquals(0, enemyOne.getCaptures());
        assertEquals(6, enemyTwo.movementFor(6));
        assertFalse(enemyOne.hasMovedInBlock());
        assertFalse(enemyTwo.hasMovedInBlock());
    }

    // Every member of a capturing three-piece block gains a capture while all
    // opponents return to Base.
    @Test
    void blockMembersEachGainACapture() {
        for (int number = 1; number <= 3; number++) {
            game.place(game.red, number, 0, Direction.CLOCKWISE);
            game.place(game.blue, number, 2, Direction.CLOCKWISE);
        }
        Piece first = game.red.getPieces()[0];
        MovePlan move = game.rules.planBlock(game.board.companions(first), 6);
        assertTrue(move.legal);
        game.executor.execute(move);
        for (int index = 0; index < 3; index++) {
            assertEquals(2, game.red.getPieces()[index].getLocation());
            assertEquals(1, game.red.getPieces()[index].getCaptures());
            assertTrue(game.blue.getPieces()[index].isAtBase());
        }
    }

    // Blocks require matching divided movement, reject zero steps and apply shared
    // energised speed.
    @Test
    void blockMovementUsesSharedSpeed() {
        Piece first = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.red, 2, 0, Direction.CLOCKWISE);
        assertFalse(game.rules.planBlock(game.board.companions(first), 1).legal);
        MovePlan move = game.rules.planBlock(game.board.companions(first), 5);
        assertTrue(move.legal);
        game.executor.execute(move);
        assertEquals(2, first.getLocation());
        assertEquals(2, second.getLocation());
        first.applyState(new SickState(), 1);
        assertFalse(game.rules.planBlock(game.board.companions(first), 6).legal);
        assertTrue(game.rules.planPiece(first, 6).legal);
        first.applyState(new EnergisedState(), 1);
        second.applyState(new EnergisedState(), 1);
        game.executor.execute(game.rules.planBlock(game.board.companions(first), 6));
        assertEquals(8, first.getLocation());
        assertEquals(8, second.getLocation());
    }

    // A mixed-direction block follows the member with the longer remaining route to
    // Home.
    @Test
    void mixedBlockTakesLongerRoute() {
        Piece first = game.place(game.yellow, 1, 0, Direction.CLOCKWISE);
        Piece second = game.place(game.yellow, 2, 0, Direction.COUNTERCLOCKWISE);
        MovePlan longerCounterclockwise = game.rules.planBlock(game.board.companions(first), 6);
        assertEquals(Direction.COUNTERCLOCKWISE, longerCounterclockwise.direction);
        game.executor.execute(longerCounterclockwise);
        assertEquals(49, first.getLocation());
        assertEquals(49, second.getLocation());
        first.moveTo(10, 0);
        second.moveTo(10, 1);
        assertEquals(Direction.CLOCKWISE,
                game.rules.planBlock(game.board.companions(first), 6).direction);
    }

    // Departure restores the original direction only after the block has moved
    // together.
    @Test
    void leavingBlockRestoresDirection() {
        Piece first = game.place(game.red, 1, 1, Direction.CLOCKWISE);
        first.turnCounterclockwise();
        Piece second = game.place(game.red, 2, 1, Direction.COUNTERCLOCKWISE);
        MovePlan group = game.rules.planBlock(game.board.companions(first), 2);
        assertEquals(Direction.COUNTERCLOCKWISE, group.direction);
        game.executor.execute(group);
        assertEquals(0, first.getLocation());
        assertEquals(0, second.getLocation());
        MovePlan individual = game.rules.planPiece(first, 1);
        assertEquals(Direction.CLOCKWISE, individual.direction);
        game.executor.execute(individual);
        assertEquals(1, first.getLocation());
        assertEquals(Direction.CLOCKWISE, first.getDirection());
        game.executor.execute(game.rules.planPiece(second, 1));
        assertEquals(51, second.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, second.getDirection());
        assertEquals(Direction.COUNTERCLOCKWISE, second.getOriginalDirection());

        GameFixture unmoved = new GameFixture();
        Piece changed = unmoved.place(unmoved.red, 1, 51, Direction.CLOCKWISE);
        unmoved.spawnMystery();
        unmoved.random.add(2);
        unmoved.executor.execute(unmoved.rules.planPiece(changed, 1));
        assertEquals(44, changed.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, changed.getDirection());
        unmoved.place(unmoved.red, 2, 44, Direction.CLOCKWISE);
        MovePlan leavingUnmovedBlock = unmoved.rules.planPiece(changed, 1);
        assertEquals(Direction.COUNTERCLOCKWISE, leavingUnmovedBlock.direction);
        unmoved.executor.execute(leavingUnmovedBlock);
        assertEquals(43, changed.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, changed.getDirection());
        assertEquals(Direction.CLOCKWISE, changed.getOriginalDirection());
    }

    // A third six splits blocks using six total steps where legal, respecting Home,
    // blockades and restrictions.
    @Test
    void thirdSixBreaksABlock() {
        for (int size = 2; size <= 4; size++) {
            GameFixture scenario = new GameFixture();
            for (int number = 1; number <= size; number++) {
                scenario.place(scenario.green, number, 0, Direction.CLOCKWISE);
            }
            for (int index = size; index < 4; index++) {
                scenario.green.getPieces()[index].moveTo(57, 0);
            }
            scenario.random.add(5, 5, 5);
            scenario.turns.playTurn(scenario.green,
                    new GreenBehaviour(scenario.green, scenario.board));
            int beforeBreak = 2 * (6 / size);
            int steps = 0;
            int remaining = 0;
            for (int index = 0; index < size; index++) {
                Piece piece = scenario.green.getPieces()[index];
                steps += piece.getLocation() - beforeBreak;
                if (piece.getLocation() == beforeBreak) {
                    remaining++;
                }
                assertEquals(1, scenario.board.companions(piece).size());
            }
            assertEquals(6, steps);
            assertEquals(1, remaining);
            assertEquals(3, scenario.random.calls);
        }

        GameFixture exactHome = new GameFixture();
        for (int number = 1; number <= 3; number++) {
            Piece piece = exactHome.place(exactHome.red, number, 52, Direction.CLOCKWISE);
            piece.recordCapture();
        }
        exactHome.breaker.breakBlocks(exactHome.red);
        assertEquals(52, exactHome.red.getPieces()[0].getLocation());
        assertEquals(53, exactHome.red.getPieces()[1].getLocation());
        assertTrue(exactHome.red.getPieces()[2].isAtHome());

        GameFixture overshoot = new GameFixture();
        for (int number = 1; number <= 2; number++) {
            Piece piece = overshoot.place(overshoot.red, number, 56, Direction.CLOCKWISE);
            piece.recordCapture();
        }
        overshoot.red.getPieces()[2].moveTo(57, 0);
        overshoot.red.getPieces()[3].moveTo(57, 0);
        overshoot.random.add(5, 5, 5, 0, 0);
        RedBehaviour nearHome = new RedBehaviour(overshoot.red, overshoot.board);
        overshoot.turns.playTurn(overshoot.red, nearHome);
        assertEquals(56, overshoot.red.getPieces()[0].getLocation());
        assertEquals(56, overshoot.red.getPieces()[1].getLocation());
        assertEquals(3, overshoot.random.calls);
        assertFalse(overshoot.red.hasWon());
        overshoot.turns.playTurn(overshoot.red, nearHome);
        overshoot.turns.playTurn(overshoot.red, nearHome);
        assertTrue(overshoot.red.hasWon());
        assertEquals(5, overshoot.random.calls);

        GameFixture surrounded = new GameFixture();
        for (int number = 1; number <= 2; number++) {
            surrounded.place(surrounded.red, number, 0, Direction.CLOCKWISE);
            surrounded.place(surrounded.blue, number, 1, Direction.CLOCKWISE);
        }
        surrounded.red.getPieces()[2].moveTo(57, 0);
        surrounded.red.getPieces()[3].moveTo(57, 0);
        surrounded.random.add(5, 5, 5);
        surrounded.turns.playTurn(surrounded.red,
                new RedBehaviour(surrounded.red, surrounded.board));
        assertEquals(0, surrounded.red.getPieces()[0].getLocation());
        assertEquals(0, surrounded.red.getPieces()[1].getLocation());
        assertEquals(2, surrounded.board.companions(surrounded.red.getPieces()[0]).size());
        assertEquals(2, surrounded.board.opponentsAt(1, surrounded.red.getColour()).size());
        assertEquals(3, surrounded.random.calls);
        assertTrue(surrounded.output.text().contains("Ignoring the throw and moving on"));

        GameFixture obstructed = new GameFixture();
        Piece remaining = obstructed.place(obstructed.red, 1, 0, Direction.CLOCKWISE);
        Piece forwards = obstructed.place(obstructed.red, 2, 0, Direction.CLOCKWISE);
        Piece backwards = obstructed.place(obstructed.red, 3, 0, Direction.COUNTERCLOCKWISE);
        obstructed.place(obstructed.blue, 1, 3, Direction.CLOCKWISE);
        obstructed.place(obstructed.blue, 2, 3, Direction.CLOCKWISE);
        obstructed.breaker.breakBlocks(obstructed.red);
        assertEquals(0, remaining.getLocation());
        assertEquals(1, forwards.getLocation());
        assertEquals(47, backwards.getLocation());
        assertEquals(6, forwards.getLocation() + (52 - backwards.getLocation()));
        assertEquals(2, obstructed.board.opponentsAt(3, obstructed.red.getColour()).size());
        assertEquals(Direction.COUNTERCLOCKWISE, backwards.getDirection());

        GameFixture restricted = new GameFixture();
        Piece waiting = restricted.place(restricted.red, 1, 0, Direction.CLOCKWISE);
        restricted.place(restricted.red, 2, 0, Direction.CLOCKWISE);
        waiting.applyState(new BriefingState(), 1);
        assertFalse(restricted.rules.planForcedMove(waiting, 6).legal);
        restricted.breaker.breakBlocks(restricted.red);
        assertEquals(0, waiting.getLocation());
        assertFalse(waiting.canMove());
        assertEquals(6, restricted.red.getPieces()[1].getLocation());
    }

    private void choose(PlayerBehaviour behaviour, Player player, int roll) {
        behaviour.chooseMove(game.rules.possibleMoves(player, roll),
                game.executor).execute();
    }
}
