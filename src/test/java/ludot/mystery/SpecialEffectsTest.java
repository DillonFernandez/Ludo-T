// Tests Alpha movement changes, Beta restrictions, Gamma redirection and effect expiry.
package ludot.mystery;

import ludot.behaviour.BlueBehaviour;
import ludot.behaviour.RedBehaviour;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.movement.MovePlan;
import ludot.state.BriefingState;
import ludot.state.EnergisedState;
import ludot.state.SickState;
import ludot.support.GameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpecialEffectsTest {
    private final GameFixture game = new GameFixture();

    // Alpha's energised outcome doubles movement and emits the expected effect
    // message.
    @Test
    void alphaEnergisesAPiece() {
        Piece red = readyAtMystery(Direction.CLOCKWISE);
        game.random.add(0, 0);
        landOnMystery(red);
        assertEquals(7, red.getLocation());
        assertEquals(12, red.movementFor(6));
        assertTrue(game.output.text().contains(
                "red piece 1 feels energized, and movement speed doubles."));
    }

    // Alpha's sickness outcome halves movement and rounds an odd roll down.
    @Test
    void alphaMakesAPieceSick() {
        Piece red = readyAtMystery(Direction.CLOCKWISE);
        game.random.add(0, 1);
        landOnMystery(red);
        assertEquals(7, red.getLocation());
        assertEquals(2, red.movementFor(5));
    }

    // Doubled movement remains through round six after application in round three
    // and expires in round seven.
    @Test
    void energisedMovementLastsFourRounds() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        red.applyState(new EnergisedState(), 3);
        for (int round = 3; round < 7; round++) {
            int before = red.getLocation();
            game.executor.execute(game.rules.planPiece(red, 4));
            assertEquals((before + 8) % 52, red.getLocation());
            red.finishRound(round);
            assertEquals(8, red.movementFor(4));
        }
        red.finishRound(7);
        int before = red.getLocation();
        game.executor.execute(game.rules.planPiece(red, 4));
        assertEquals((before + 4) % 52, red.getLocation());
    }

    // Halved movement remains through round six after application in round three
    // and expires in round seven.
    @Test
    void sickMovementLastsFourRounds() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        red.applyState(new SickState(), 3);
        for (int round = 3; round < 7; round++) {
            int before = red.getLocation();
            game.executor.execute(game.rules.planPiece(red, 4));
            assertEquals((before + 2) % 52, red.getLocation());
            red.finishRound(round);
            assertEquals(2, red.movementFor(4));
        }
        red.finishRound(7);
        int before = red.getLocation();
        game.executor.execute(game.rules.planPiece(red, 4));
        assertEquals((before + 4) % 52, red.getLocation());
    }

    // A replacement sickness effect resets expiry and prevents a one-step move
    // until normal movement returns.
    @Test
    void alphaEffectsExpireAndRestart() {
        Piece piece = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        piece.applyState(new EnergisedState(), 1);
        piece.applyState(new SickState(), 3);
        piece.finishRound(5);
        assertEquals(2, piece.movementFor(5));
        assertFalse(game.rules.planPiece(piece, 1).legal);
        piece.finishRound(6);
        assertEquals(2, piece.movementFor(4));
        piece.finishRound(7);
        assertEquals(4, piece.movementFor(4));
        assertTrue(game.rules.planPiece(piece, 1).legal);
    }

    // Beta reached in round three restricts movement until the end of round seven.
    @Test
    void betaRestrictsFourRounds() {
        Piece red = readyAtMystery(Direction.CLOCKWISE);
        game.random.add(1);
        game.executor.setRound(3);
        landOnMystery(red);
        assertEquals(25, red.getLocation());
        assertFalse(red.canMove());
        red.finishRound(6);
        assertFalse(red.canMove());
        red.finishRound(7);
        assertTrue(red.canMove());
    }

    // Two successive rolls of three for the restricted player return its piece to
    // Base, clearing the restriction.
    @Test
    void consecutiveThreesReturnPieceToBase() {
        Piece red = game.place(game.red, 1, 25, Direction.CLOCKWISE);
        red.applyState(new BriefingState(), 1);
        game.random.add(2, 1, 2, 0, 2);
        playRedTurn();
        playRedTurn();
        playRedTurn();
        assertFalse(red.isAtBase());
        game.turns.playTurn(game.blue, new BlueBehaviour(game.blue, game.board, game.mystery));
        playRedTurn();
        assertTrue(red.isAtBase());
        assertTrue(red.canMove());
        assertEquals(0, red.getCaptures());
        assertTrue(game.output.text().contains("Teleporting piece 1 to base."));
    }

    // Gamma reverses clockwise arrivals, including block members, without sending
    // them to Beta.
    @Test
    void gammaReversesClockwiseMovement() {
        Piece red = readyAtMystery(Direction.CLOCKWISE);
        game.random.add(2);
        landOnMystery(red);
        assertEquals(44, red.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, red.getDirection());
        assertEquals(Direction.CLOCKWISE, red.getOriginalDirection());
        assertTrue(red.canMove());

        GameFixture block = new GameFixture();
        Piece clockwise = block.place(block.yellow, 1, 51, Direction.CLOCKWISE);
        Piece counterclockwise = block.place(block.yellow, 2, 51, Direction.COUNTERCLOCKWISE);
        counterclockwise.moveTo(51, 1);
        block.spawnMystery();
        block.random.add(2, 2);
        MovePlan group = block.rules.planBlock(block.board.companions(clockwise), 2);
        assertEquals(Direction.CLOCKWISE, group.direction);
        block.executor.execute(group);
        assertEquals(44, clockwise.getLocation());
        assertEquals(44, counterclockwise.getLocation());
        assertEquals(Direction.COUNTERCLOCKWISE, clockwise.getDirection());
        assertEquals(Direction.COUNTERCLOCKWISE, counterclockwise.getDirection());
        assertTrue(clockwise.canMove());
        assertTrue(counterclockwise.canMove());
        assertFalse(block.output.text().contains("Teleporting to Beta from Gamma."));
        block.executor.execute(block.rules.planPiece(clockwise, 1));
        assertEquals(45, clockwise.getLocation());
        assertEquals(Direction.CLOCKWISE, clockwise.getDirection());
    }

    // Gamma sends counter-clockwise arrivals, including block members, to Beta and
    // restricts them.
    @Test
    void gammaRedirectsCounterclockwiseMovement() {
        Piece red = readyAtMystery(Direction.COUNTERCLOCKWISE);
        game.random.add(2);
        landOnMystery(red);
        assertEquals(25, red.getLocation());
        assertFalse(red.canMove());
        assertTrue(game.output.text().contains("Teleporting to Beta from Gamma."));

        GameFixture block = new GameFixture();
        Piece clockwise = block.place(block.yellow, 1, 2, Direction.CLOCKWISE);
        Piece counterclockwise = block.place(block.yellow, 2, 2, Direction.COUNTERCLOCKWISE);
        block.spawnMystery();
        block.random.add(2, 2);
        MovePlan group = block.rules.planBlock(
                block.board.companions(clockwise), 4);
        assertEquals(Direction.COUNTERCLOCKWISE, group.direction);
        block.executor.execute(group);
        assertEquals(25, clockwise.getLocation());
        assertEquals(25, counterclockwise.getLocation());
        assertFalse(clockwise.canMove());
        assertFalse(counterclockwise.canMove());
        assertTrue(block.output.text().contains("yellow piece 1 is moving in a counterclockwise "
                + "direction. Teleporting to Beta from Gamma."));
    }

    // Ordinary moves onto Alpha, Beta and Gamma cells cause no teleport or special
    // effect.
    @Test
    void ordinarySpecialCellsHaveNoEffect() {
        Piece red = game.place(game.red, 1, 6, Direction.CLOCKWISE);
        game.executor.execute(game.rules.planPiece(red, 1));
        assertEquals(7, red.getLocation());
        assertEquals(6, red.movementFor(6));
        red.moveTo(24, 0);
        game.executor.execute(game.rules.planPiece(red, 1));
        assertEquals(25, red.getLocation());
        assertTrue(red.canMove());
        red.moveTo(43, 0);
        game.executor.execute(game.rules.planPiece(red, 1));
        assertEquals(44, red.getLocation());
        assertEquals(Direction.CLOCKWISE, red.getDirection());
        assertFalse(game.output.text().contains("teleported"));
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

}
