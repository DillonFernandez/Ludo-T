// Tests window startup, board layout and rendering, state updates and responsive game completion.
package ludot.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import ludot.server.ServerFixture;
import ludot.application.GameSnapshot;
import ludot.game.LudoGame;
import ludot.game.LudoGameFactory;
import ludot.model.Colour;
import ludot.model.Direction;
import ludot.model.Piece;
import ludot.support.GameFixture;
import ludot.support.ScriptedRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class GuiTest {
    // The visible LUDO-T window exposes the board accessibility name and displays
    // startup output.
    @Test
    void guiLaunchesSuccessfully() throws Exception {
        try (ServerFixture server = new ServerFixture();
                WindowFixture view = new WindowFixture(server.server.getPort(), true)) {
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(view.window.isVisible());
                assertTrue(view.window.isDisplayable());
                assertEquals("LUDO-T", view.window.getTitle());
                assertEquals("LUDO-T board", view.board.getAccessibleContext().getAccessibleName());
            });
            view.awaitText(text -> text.contains(" player has four (04) pieces"));
            view.saveWindowImage(Path.of("target", "gui-verification.png"));
        }
    }

    // The board has 52 distinct adjacent standard cells and twenty distinct
    // home-straight cells at expected coordinates.
    @Test
    void boardLayoutMatchesTheReferenceStructure() {
        Set<Point> cells = new HashSet<>();
        for (int location = 0; location < 52; location++) {
            Point here = BoardPanel.standardCell(location);
            Point next = BoardPanel.standardCell((location + 1) % 52);
            assertTrue(cells.add(here));
            assertTrue(Math.abs(here.x - next.x) <= 1 && Math.abs(here.y - next.y) <= 1);
        }
        assertEquals(new Point(13, 8), BoardPanel.standardCell(0));
        assertEquals(new Point(6, 13), BoardPanel.standardCell(13));
        assertEquals(new Point(1, 6), BoardPanel.standardCell(26));
        assertEquals(new Point(8, 1), BoardPanel.standardCell(39));
        for (Colour colour : Colour.values()) {
            for (int location = 52; location <= 56; location++) {
                assertTrue(cells.add(BoardPanel.homeCell(colour, location)));
            }
        }
        assertEquals(72, cells.size());
        assertEquals(new Point(1, 7), BoardPanel.homeCell(Colour.RED, 52));
        assertEquals(new Point(7, 5), BoardPanel.homeCell(Colour.GREEN, 56));
        assertEquals(new Point(9, 7), BoardPanel.homeCell(Colour.YELLOW, 56));
        assertEquals(new Point(7, 9), BoardPanel.homeCell(Colour.BLUE, 56));
    }

    // Player bases and home paths render in their expected colours at both tested
    // sizes.
    @Test
    void boardColoursAndAreasRenderCorrectly() throws Exception {
        try (WindowFixture view = new WindowFixture()) {
            for (int size : new int[] { 450, 750 }) {
                BufferedImage image = view.image(size);
                assertColour(image, size / 30, size / 30, Colour.RED);
                assertColour(image, size * 29 / 30, size / 30, Colour.GREEN);
                assertColour(image, size / 30, size * 29 / 30, Colour.BLUE);
                assertColour(image, size * 29 / 30, size * 29 / 30, Colour.YELLOW);
                for (Colour colour : Colour.values()) {
                    Point cell = BoardPanel.homeCell(colour, 52);
                    assertColour(image, (int) ((cell.x + 0.5) * size / 15),
                            (int) ((cell.y + 0.5) * size / 15), colour);
                }
                assertEquals(Color.WHITE.getRGB(), image.getRGB(size / 10, size / 10));
            }
        }
    }

    // Pieces render in their matching bases, at Home and in a shared standard cell.
    @Test
    void initialPiecesAppearAtTheCorrectPositions() throws Exception {
        GameFixture game = new GameFixture();
        try (WindowFixture view = new WindowFixture()) {
            view.apply(snapshot(game, 1));
            BufferedImage image = view.image(750);
            for (Colour colour : Colour.values()) {
                for (int piece = 0; piece < 4; piece++) {
                    int x = (colour == Colour.GREEN || colour == Colour.YELLOW ? 9 : 0)
                            + 2 + (piece % 2) * 2;
                    int y = (colour == Colour.YELLOW || colour == Colour.BLUE ? 9 : 0)
                            + 2 + (piece / 2) * 2;
                    assertColour(image, x * 50 - 9, y * 50 - 9, colour);
                }
            }
            int[] locations = snapshot(game, 2).getLocations();
            locations[0] = 57;
            view.apply(new GameSnapshot(2, "display", 0, "RUNNING", -1, locations, "Home"));
            assertColour(view.image(750), 308, 354, Colour.RED);
            locations[0] = 5;
            locations[1] = 5;
            view.apply(new GameSnapshot(3, "display", 0, "RUNNING", -1, locations, "Stack"));
            Point cell = BoardPanel.standardCell(5);
            assertColour(view.image(750), cell.x * 50 + 3, cell.y * 50 + 21, Colour.RED);
        }
    }

    // Opening the window starts the server game and shows its output in a read-only
    // view without top controls.
    @Test
    void requiredStartupWorksWithoutUnwantedControls() throws Exception {
        LinkedBlockingQueue<GameSnapshot> updates = new LinkedBlockingQueue<>();
        try (ServerFixture server = new ServerFixture()) {
            server.client(updates::add);
            assertEquals("READY", server.service.getSnapshot().getStatus());
            try (WindowFixture view = new WindowFixture(server.server.getPort(), true)) {
                GameSnapshot started = updates.poll(5, TimeUnit.SECONDS);
                assertNotNull(started);
                assertTrue(started.isRunning());
                view.awaitText(text -> text.equals(started.getLog()));
                SwingUtilities.invokeAndWait(() -> {
                    BorderLayout layout = (BorderLayout) view.window.getContentPane().getLayout();
                    assertNull(layout.getLayoutComponent(BorderLayout.NORTH));
                    assertFalse(view.output.isEditable());
                });
            }
        }
    }

    // Applying server state displays the same log, running status and accessible
    // round description.
    @Test
    void displayedInformationMatchesGameState() throws Exception {
        try (ServerFixture server = new ServerFixture(); WindowFixture view = new WindowFixture()) {
            var client = server.client();
            GameSnapshot state = client.request("START").get(5, TimeUnit.SECONDS);
            view.apply(state);
            assertEquals(state.getLog(), view.text());
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("Round 0 / 10,000 - running", view.status.getText());
                assertTrue(view.board.getAccessibleContext().getAccessibleDescription().contains("Round 0"));
            });
        }
    }

    // A move redraws Red at its new cell, clears its old cell and displays the
    // movement message.
    @Test
    void pieceMovementUpdatesTheGui() throws Exception {
        GameFixture game = new GameFixture();
        Piece red = game.place(game.red, 1, 5, Direction.CLOCKWISE);
        try (WindowFixture view = new WindowFixture()) {
            view.apply(snapshot(game, 1));
            assertPiece(view.image(750), 5, Colour.RED);
            game.executor.execute(game.rules.planPiece(red, 3));
            view.apply(snapshot(game, 2));
            BufferedImage image = view.image(750);
            assertPiece(image, 8, Colour.RED);
            Point previous = BoardPanel.standardCell(5);
            assertEquals(Color.WHITE.getRGB(), image.getRGB(previous.x * 50 + 16, previous.y * 50 + 16));
            assertTrue(view.text().contains("from location 5 to 8 by 3 units"));
        }
    }

    // A capture redraws the capturer at the destination and the captured piece at
    // Base with the capture message.
    @Test
    void capturesUpdateBothAffectedPositions() throws Exception {
        GameFixture game = new GameFixture();
        Piece red = game.place(game.red, 1, 5, Direction.CLOCKWISE);
        Piece blue = game.place(game.blue, 1, 8, Direction.CLOCKWISE);
        try (WindowFixture view = new WindowFixture()) {
            view.apply(snapshot(game, 1));
            assertPiece(view.image(750), 8, Colour.BLUE);
            assertTrue(game.executor.execute(game.rules.planPiece(red, 3)));
            assertTrue(blue.isAtBase());
            view.apply(snapshot(game, 2));
            BufferedImage image = view.image(750);
            assertPiece(image, 8, Colour.RED);
            assertColour(image, 91, 541, Colour.BLUE);
            assertTrue(view.text().contains("captures blue piece 1, and returns it to the base."));
        }
    }

    // Mystery output and tooltips match the state, and a purple marker preserves
    // the underlying cell colour.
    @Test
    void mysteryEventsAndStatusesDisplayCorrectly() throws Exception {
        GameFixture game = new GameFixture();
        Piece red = game.place(game.red, 1, 51, Direction.CLOCKWISE);
        game.spawnMystery();
        game.random.add(0, 0);
        game.executor.execute(game.rules.planPiece(red, 1));
        GameSnapshot state = snapshot(game, 2);
        state = new GameSnapshot(2, "display", 3, "RUNNING", game.mystery.getLocation(),
                state.getLocations(), state.getLog());
        try (WindowFixture view = new WindowFixture()) {
            view.apply(state);
            assertTrue(view.text().contains("teleported to Alpha."));
            assertTrue(view.text().contains("feels energized, and movement speed doubles."));
            BufferedImage image = view.image(750);
            Point cell = BoardPanel.standardCell(game.mystery.getLocation());
            view.apply(new GameSnapshot(2, "display", 3, "RUNNING", -1,
                    state.getLocations(), state.getLog()));
            BufferedImage unmarked = view.image(750);
            assertEquals(unmarked.getRGB(cell.x * 50 + 5, cell.y * 50 + 25),
                    image.getRGB(cell.x * 50 + 5, cell.y * 50 + 25));
            view.apply(state);
            int purple = 0;
            for (int x = cell.x * 50; x < (cell.x + 1) * 50; x++) {
                for (int y = cell.y * 50; y < (cell.y + 1) * 50; y++) {
                    if (image.getRGB(x, y) == new Color(95, 35, 120).getRGB()) {
                        purple++;
                    }
                    assertNotEquals(new Color(238, 218, 249).getRGB(), image.getRGB(x, y));
                }
            }
            assertTrue(purple > 0);
            assertTrue(
                    javax.imageio.ImageIO.write(image, "png", Path.of("target", "mystery-verification.png").toFile()));
            SwingUtilities.invokeAndWait(() -> assertTrue(view.board.getToolTipText(new MouseEvent(
                    view.board, MouseEvent.MOUSE_MOVED, 0, 0, cell.x * 50 + 25,
                    cell.y * 50 + 25, 0, false)).contains("mystery cell")));
        }
    }

    // Winning and round-limit games display complete output and a finished status
    // while Swing remains responsive.
    @Test
    void completionStatesDisplayWithoutFreezing() throws Exception {
        for (boolean limit : new boolean[] { false, true }) {
            ScriptedRandom random = new ScriptedRandom();
            random.add(5, 4, 3, 2);
            try (ServerFixture server = limit ? new ServerFixture(false, 1, 100, random)
                    : new ServerFixture(false, 1, 100, new ludot.random.RandomNumbers(1234));
                    WindowFixture view = new WindowFixture(server.server.getPort(), true)) {
                view.awaitEnding(limit ? "The game ran 10,000 rounds without a winner, so the simulation has ended."
                        : " player wins!!!");
                String output = view.text();
                assertTrue(output.startsWith("The red player has four"));
                assertTrue(output.contains("Round 1\n"));
                assertTrue(output.split("\n").length > 600);
                if (limit) {
                    assertTrue(output.contains("Round 9999\n"));
                    assertTrue(output.contains("Round 10000\n"));
                    assertFalse(output.contains("Round 10001\n"));
                }
                CountDownLatch responsive = new CountDownLatch(1);
                SwingUtilities.invokeLater(() -> {
                    assertTrue(view.status.getText().endsWith("finished"));
                    javax.swing.JScrollPane console = (javax.swing.JScrollPane) view.output.getParent().getParent();
                    assertEquals(0, console.getHorizontalScrollBar().getValue());
                    responsive.countDown();
                });
                assertTrue(responsive.await(5, TimeUnit.SECONDS));
            }
        }
    }

    private static GameSnapshot snapshot(GameFixture game, long revision) {
        LudoGame simulation = new LudoGame(game.board, game.random, game.messages, new LudoGameFactory());
        return GameSnapshot.capture(revision, "display", "RUNNING", simulation, game.output.text());
    }

    private static void assertPiece(BufferedImage image, int location, Colour colour) {
        Point cell = BoardPanel.standardCell(location);
        assertColour(image, cell.x * 50 + 16, cell.y * 50 + 16, colour);
    }

    private static void assertColour(BufferedImage image, int x, int y, Colour colour) {
        Color[] colours = { new Color(220, 60, 60), new Color(60, 180, 90),
                new Color(245, 200, 50), new Color(70, 120, 220) };
        assertEquals(colours[colour.ordinal()].getRGB(), image.getRGB(x, y));
    }
}
