// Tests initial players and pieces, standard-cell numbering and colour-specific board locations.
package ludot.model;

import ludot.config.GameSettings;
import ludot.game.LudoGame;
import ludot.game.LudoGameFactory;
import ludot.support.GameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardSetupTest {
    private final GameFixture game = new GameFixture();
    private final GameSettings settings = GameSettings.getInstance();

    // Cells zero to 51 belong to the standard path, while cell 52 starts the home
    // straight.
    @Test
    void standardPathHas52Cells() {
        assertEquals(52, settings.getStandardCells());
        Piece red = game.place(game.red, 1, 0, Direction.CLOCKWISE);
        for (int cell = 0; cell < 52; cell++) {
            red.moveTo(cell, 0);
            assertTrue(red.isOnStandardPath());
            assertSame(red, game.board.piecesAt(cell, Colour.RED).get(0));
        }
        red.moveTo(52, 0);
        assertFalse(red.isOnStandardPath());
        assertTrue(red.isInHomeStraight());
    }

    // Clockwise single-step moves follow displayed cell numbers and wrap from 51 to
    // zero.
    @Test
    void cellsAreNumberedClockwise() {
        Piece yellow = game.place(game.yellow, 1, 0, Direction.CLOCKWISE);
        assertEquals(0, settings.getStartingCell(Colour.YELLOW));
        for (int cell = 0; cell < 52; cell++) {
            assertEquals(Integer.toString(cell), game.messages.location(yellow, cell));
            game.executor.execute(game.rules.planPiece(yellow, 1));
            assertEquals((cell + 1) % 52, yellow.getLocation());
        }
    }

    // Each colour has five named home-straight cells whose occupants exclude other
    // colours.
    @Test
    void eachColourHasAHomeStraight() {
        assertEquals(5, settings.getHomeStraightCells());
        for (Player player : game.board.getPlayers()) {
            Piece piece = player.getPieces()[0];
            for (int cell = 52; cell <= 56; cell++) {
                piece.moveTo(cell, 0);
                assertTrue(piece.isInHomeStraight());
                assertEquals(player.getColour().getName() + "homepath" + (cell - 52),
                        game.messages.location(piece, cell));
                assertSame(piece, game.board.piecesAt(cell, player.getColour()).get(0));
                assertEquals(1, game.board.piecesAt(cell, player.getColour()).size());
                assertTrue(game.board.opponentsAt(cell, player.getColour()).isEmpty());
            }
        }
    }

    // Yellow, Blue, Red and Green enter at cells zero, 13, 26 and 39 respectively.
    @Test
    void startingSquaresMatchColours() {
        Colour[] colours = { Colour.YELLOW, Colour.BLUE, Colour.RED, Colour.GREEN };
        int[] cells = { 0, 13, 26, 39 };
        for (int index = 0; index < colours.length; index++) {
            assertEquals(cells[index], settings.getStartingCell(colours[index]));
            Piece piece = new Piece(colours[index], 1);
            piece.enterBoard(Direction.CLOCKWISE);
            assertEquals(cells[index], piece.getLocation());
        }
    }

    // The approach cells are 50 for Yellow, 11 for Blue, 24 for Red and 37 for
    // Green.
    @Test
    void approachSquaresMatchColours() {
        assertEquals(50, settings.getApproachCell(Colour.YELLOW));
        assertEquals(11, settings.getApproachCell(Colour.BLUE));
        assertEquals(24, settings.getApproachCell(Colour.RED));
        assertEquals(37, settings.getApproachCell(Colour.GREEN));
    }

    // The factory creates four players in Red, Green, Yellow and Blue order.
    @Test
    void fourPlayersHaveDifferentColours() {
        LudoGame simulation = new LudoGameFactory().createGame(game.random, game.output);
        Player[] players = simulation.getBoard().getPlayers();
        assertEquals(4, players.length);
        assertEquals(Colour.RED, players[0].getColour());
        assertEquals(Colour.GREEN, players[1].getColour());
        assertEquals(Colour.YELLOW, players[2].getColour());
        assertEquals(Colour.BLUE, players[3].getColour());
    }

    // Every player owns four numbered pieces with the matching colour and initial.
    @Test
    void eachPlayerHasFourNamedPieces() {
        String[] initials = { "R", "G", "Y", "B" };
        Player[] players = game.board.getPlayers();
        for (int index = 0; index < players.length; index++) {
            Piece[] pieces = players[index].getPieces();
            assertEquals(4, pieces.length);
            for (int number = 1; number <= 4; number++) {
                assertEquals(initials[index] + number, pieces[number - 1].getName());
                assertEquals(number, pieces[number - 1].getNumber());
                assertEquals(players[index].getColour(), pieces[number - 1].getColour());
            }
        }
    }

    // All sixteen pieces start at Base with no captures or approach visits and no
    // winning player.
    @Test
    void allPiecesStartAtBase() {
        for (Player player : game.board.getPlayers()) {
            assertEquals(4, player.countAtBase());
            assertEquals(0, player.countOnBoard());
            assertFalse(player.hasWon());
            for (Piece piece : player.getPieces()) {
                assertTrue(piece.isAtBase());
                assertEquals(-1, piece.getLocation());
                assertEquals(0, piece.getCaptures());
                assertEquals(0, piece.getApproachVisits());
            }
        }
        assertFalse(game.board.hasStandardPieces());
    }
}
