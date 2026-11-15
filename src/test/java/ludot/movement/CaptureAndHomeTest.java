// Tests capture outcomes, reset state, bonus rolls, Home-entry requirements and winning.
package ludot.movement;

import ludot.behaviour.RedBehaviour;
import ludot.config.GameSettings;
import ludot.model.Colour;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.model.Player;
import ludot.state.BriefingState;
import ludot.support.GameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaptureAndHomeTest {
    private final GameFixture game = new GameFixture();

    // Landing on a single opponent records a capture and clears opposing occupancy
    // of the destination.
    @Test
    void landingCapturesAnOpponent() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.blue, 1, 3, Direction.CLOCKWISE);
        MovePlan move = game.rules.planPiece(red, 3);
        assertTrue(move.legal);
        assertTrue(move.capturesOpponent);
        assertTrue(game.executor.execute(move));
        assertEquals(3, red.getLocation());
        assertTrue(game.board.opponentsAt(3, Colour.RED).isEmpty());
    }

    // Deployment captures a starting-cell opponent and reports counts after the
    // piece has entered.
    @Test
    void deploymentCapturesAtX() {
        Piece opponent = game.place(game.blue, 1, 26, Direction.CLOCKWISE);
        Piece red = game.red.getPieces()[0];
        assertTrue(game.executor.execute(game.rules.planPiece(red, 6)));
        assertTrue(opponent.isAtBase());
        assertEquals(1, red.getCaptures());
        assertEquals(26, red.getLocation());
        assertFalse(game.output.text().contains("red player now has 0/4"));
    }

    // Capture clears the opponent's history, effects and directions, including
    // consecutive briefing rolls.
    @Test
    void captureResetsPieceState() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        Piece blue = game.place(game.blue, 1, 3, Direction.COUNTERCLOCKWISE);
        blue.recordCapture();
        blue.moveTo(3, 2);
        blue.applyState(new BriefingState(), 1);
        assertFalse(blue.mustReturnAfterRoll(3));
        assertTrue(game.executor.execute(game.rules.planPiece(red, 3)));
        assertEquals(3, red.getLocation());
        assertEquals(1, red.getCaptures());
        assertTrue(blue.isAtBase());
        assertEquals(0, blue.getCaptures());
        assertEquals(0, blue.getApproachVisits());
        assertEquals(6, blue.movementFor(6));
        assertEquals(Direction.CLOCKWISE, blue.getDirection());
        assertEquals(Direction.CLOCKWISE, blue.getOriginalDirection());
        assertTrue(blue.canMove());
        blue.enterBoard(Direction.CLOCKWISE);
        blue.applyState(new BriefingState(), 2);
        assertFalse(blue.mustReturnAfterRoll(3));
    }

    // A capture grants one further roll, including when the capturing roll is six.
    @Test
    void captureGrantsABonusRoll() {
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        game.place(game.blue, 1, 1, Direction.CLOCKWISE);
        game.random.add(0, 1);
        game.turns.playTurn(game.red, new RedBehaviour(game.red, game.board));
        assertEquals(3, red.getLocation());
        assertEquals(1, red.getCaptures());
        assertEquals(2, game.random.calls);

        GameFixture sixCapture = new GameFixture();
        Piece capturer = sixCapture.place(sixCapture.red, 1, 0, Direction.CLOCKWISE);
        sixCapture.place(sixCapture.blue, 1, 6, Direction.CLOCKWISE);
        sixCapture.random.add(5, 0);
        sixCapture.turns.playTurn(sixCapture.red,
                new RedBehaviour(sixCapture.red, sixCapture.board));
        assertEquals(7, capturer.getLocation());
        assertEquals(1, capturer.getCaptures());
        assertEquals(2, sixCapture.random.calls);
    }

    // A capture permits clockwise Home entry, while counter-clockwise entry also
    // requires two approach visits.
    @Test
    void captureRecordsHomeEligibility() {
        Piece yellow = game.place(game.yellow, 1, 48, Direction.CLOCKWISE);
        game.place(game.red, 1, 49, Direction.CLOCKWISE);
        assertFalse(yellow.canEnterHome(Direction.CLOCKWISE, 0));
        game.executor.execute(game.rules.planPiece(yellow, 1));
        assertEquals(1, yellow.getCaptures());
        assertTrue(yellow.canEnterHome(Direction.CLOCKWISE, 0));
        assertFalse(yellow.canEnterHome(Direction.COUNTERCLOCKWISE, 1));
        assertTrue(yellow.canEnterHome(Direction.COUNTERCLOCKWISE, 2));
    }

    // A piece without a capture passes its approach and continues around the
    // standard path.
    @Test
    void homeEntryNeedsACapture() {
        Piece yellow = game.place(game.yellow, 1, 49, Direction.CLOCKWISE);
        game.executor.execute(game.rules.planPiece(yellow, 2));
        assertEquals(51, yellow.getLocation());
        assertFalse(yellow.isInHomeStraight());
        game.executor.execute(game.rules.planPiece(yellow, 4));
        assertEquals(3, yellow.getLocation());
    }

    // Capturing at the approach lets the next step enter the home straight.
    @Test
    void captureEnablesHomeEntry() {
        Piece yellow = game.place(game.yellow, 1, 49, Direction.CLOCKWISE);
        game.place(game.red, 1, 50, Direction.CLOCKWISE);
        assertEquals(51, game.rules.planPiece(yellow, 2).destination);
        game.executor.execute(game.rules.planPiece(yellow, 1));
        game.executor.execute(game.rules.planPiece(yellow, 1));
        assertEquals(52, yellow.getLocation());
        assertTrue(yellow.isInHomeStraight());
    }

    // Each eligible colour enters cell 52 from its approach and can reach Home with
    // five more steps.
    @Test
    void eachColourEntersItsHomeStraight() {
        GameSettings settings = GameSettings.getInstance();
        for (Player player : game.board.getPlayers()) {
            Piece piece = game.place(player, 1,
                    settings.getApproachCell(player.getColour()),
                    Direction.CLOCKWISE);
            piece.recordCapture();
            MovePlan homeStraight = game.rules.planPiece(piece, 1);
            assertTrue(homeStraight.legal);
            assertEquals(52, homeStraight.destination);
            game.executor.execute(homeStraight);
            assertEquals(57, game.rules.planPiece(piece, 5).destination);
        }
    }

    // A captured piece passes its first counter-clockwise approach without entering
    // the home straight.
    @Test
    void firstCounterclockwiseApproachSkipsHome() {
        Piece yellow = game.place(game.yellow, 1, 0, Direction.COUNTERCLOCKWISE);
        yellow.recordCapture();
        game.executor.execute(game.rules.planPiece(yellow, 3));
        assertEquals(49, yellow.getLocation());
        assertEquals(1, yellow.getApproachVisits());
        assertFalse(yellow.isInHomeStraight());
    }

    // Each captured counter-clockwise piece enters the home straight after its
    // second approach visit.
    @Test
    void secondCounterclockwiseApproachAllowsHome() {
        GameSettings settings = GameSettings.getInstance();
        for (Player player : game.board.getPlayers()) {
            Piece piece = game.place(player, 1,
                    settings.getStartingCell(player.getColour()),
                    Direction.COUNTERCLOCKWISE);
            piece.recordCapture();
            for (int step = 1; step <= 54; step++) {
                game.executor.execute(game.rules.planPiece(piece, 1));
                assertTrue(piece.isOnStandardPath());
                assertEquals(step < 2 ? 0 : step < 54 ? 1 : 2,
                        piece.getApproachVisits());
            }
            assertEquals(settings.getApproachCell(player.getColour()), piece.getLocation());
            game.executor.execute(game.rules.planPiece(piece, 1));
            assertEquals(52, piece.getLocation());
        }
    }

    // An overshooting roll is illegal, while the exact remaining distance reaches
    // Home.
    @Test
    void homeNeedsAnExactRoll() {
        Piece yellow = game.place(game.yellow, 1, 54, Direction.CLOCKWISE);
        assertFalse(game.rules.planPiece(yellow, 4).legal);
        assertEquals(54, yellow.getLocation());
        MovePlan exact = game.rules.planPiece(yellow, 3);
        assertTrue(exact.legal);
        game.executor.execute(exact);
        assertTrue(yellow.isAtHome());
    }

    // Winning requires all four pieces at Home, where they cannot move or count as
    // board occupants.
    @Test
    void fourPiecesAtHomeWin() {
        for (Piece piece : game.red.getPieces()) {
            piece.moveTo(56, 0);
            assertFalse(game.red.hasWon());
            game.executor.execute(game.rules.planPiece(piece, 1));
            assertTrue(piece.isAtHome());
            assertFalse(piece.canMove());
            assertFalse(game.rules.planPiece(piece, 6).legal);
            assertTrue(game.board.companions(piece).isEmpty());
        }
        assertTrue(game.red.hasWon());
        assertEquals(0, game.red.countOnBoard());
        assertEquals(0, game.red.countAtBase());
        assertTrue(game.board.piecesAt(57, Colour.RED).isEmpty());
    }
}
