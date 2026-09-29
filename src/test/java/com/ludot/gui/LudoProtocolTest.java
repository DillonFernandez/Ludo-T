package com.ludot.gui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Point;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the same parser used by LudoFrame without constructing a top-level window. */
class LudoProtocolTest {
    private LudoBoardPanel board;
    private JTextArea output;

    @BeforeEach
    void createComponentsOnEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            board = new LudoBoardPanel();
            output = new JTextArea();
        });
    }

    @Test
    void resetBoardRestoresAllSixteenPiecesAfterLiveUpdates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Map<String, Point> initial = positions();
            assertEquals(16, initial.size());
            initial.keySet().forEach(id -> message("PIECE|" + id + "|STANDARD_PATH|-|5"));
            assertNotEquals(initial, positions());
            message("RESET_BOARD");
            assertEquals(initial, positions());
            assertEquals("", output.getText());
        });
    }

    @Test
    void ringMessagesUpdateOnlyTheAddressedPieceIncludingSpecialCells() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Map<String, Point> expected = positions();
            String[] types = {"STANDARD_PATH", "STARTING_SQUARE", "APPROACH", "ALPHA", "BETA", "GAMMA"};
            int[] indices = {51, 0, 12, 8, 26, 45};
            Point[] destinations = {cell(14, 8), cell(13, 8), cell(6, 14), cell(8, 12), cell(1, 6), cell(10, 6)};
            for (int i = 0; i < types.length; i++) {
                message("PIECE|Y1|" + types[i] + "|-|" + indices[i]);
                expected.put("Y1", destinations[i]);
                assertEquals(expected, positions(), types[i]);
            }
            assertEquals("", output.getText());
        });
    }

    @Test
    void homePathHomeAndCapturedBaseMessagesPreserveColourAndPieceIdentity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Map<String, Point> initial = positions();
            String[] colours = {"RED", "GREEN", "YELLOW", "BLUE"};
            Point[] first = {cell(1, 7), cell(7, 1), cell(13, 7), cell(7, 13)};
            Point[] last = {cell(5, 7), cell(7, 5), cell(9, 7), cell(7, 9)};
            Point[] home = {new Point(260, 288), new Point(288, 260), new Point(340, 288), new Point(288, 340)};
            for (int i = 0; i < colours.length; i++) {
                String id = colours[i].substring(0, 1) + "1";
                message("PIECE|" + id + "|HOME_PATH|" + colours[i] + "|0");
                assertEquals(first[i], positions().get(id));
                message("PIECE|" + id + "|HOME_PATH|" + colours[i] + "|4");
                assertEquals(last[i], positions().get(id));
                message("PIECE|" + id + "|HOME|" + colours[i] + "|-1");
                assertEquals(home[i], positions().get(id));
                message("PIECE|" + id + "|BASE|" + colours[i] + "|-1");
                assertEquals(initial, positions());
            }
        });
    }

    @Test
    void malformedMessagesAreLoggedWithoutChangingTheBoard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Map<String, Point> initial = positions();
            String[] malformed = {"PIECE|R1|STANDARD_PATH|-|not-a-number", "PIECE|R1|BASE"};
            for (String text : malformed) message(text);
            assertEquals(initial, positions());
            assertEquals(String.join(System.lineSeparator(), malformed) + System.lineSeparator(), output.getText());
            message("PIECE|R1|STANDARD_PATH|-|5");
            assertEquals(cell(8, 9), positions().get("R1"), "A bad line must not prevent the next valid update");
        });
    }

    @Test
    void ordinaryGameMessagesRemainOrderedAlongsideSilentStateUpdates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            message("Red player rolled 6.");
            message("PIECE|R1|STARTING_SQUARE|RED|26");
            message("Red player wins!!!");
            message("RESET_BOARD");
            assertEquals("Red player rolled 6." + System.lineSeparator()
                    + "Red player wins!!!" + System.lineSeparator(), output.getText());
            assertEquals(new Point(80, 80), positions().get("R1"));
        });
    }

    private void message(String text) {
        assertTrue(SwingUtilities.isEventDispatchThread());
        LudoFrame.handleServerMessage(text, board, output);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Point> positions() {
        // Read-only test observation avoids exposing mutable GUI internals in production.
        try {
            Field field = LudoBoardPanel.class.getDeclaredField("pieceCentres");
            field.setAccessible(true);
            return new LinkedHashMap<>((Map<String, Point>) field.get(board));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Point cell(int column, int row) {
        return new Point(column * 40 + 20, row * 40 + 20);
    }
}
