package com.ludot.gui;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Draws the board and tracks the screen position of each piece.
public final class LudoBoardPanel extends JPanel {

    private static final int GRID_SIZE = 15;
    private static final int CELL_SIZE = 40;
    private static final int BOARD_SIZE = GRID_SIZE * CELL_SIZE;
    private static final int SINGLE_PIECE_OUTER_DIAMETER = 34;
    private static final int SINGLE_PIECE_DIAMETER = 26;
    private static final int GROUPED_PIECE_OUTER_DIAMETER = 20;
    private static final int GROUPED_PIECE_DIAMETER = 18;
    private static final int GROUPED_PIECE_OFFSET = 9;
    private static final int PIECE_LABEL_FONT_SIZE = 10;
    private static final int CELL_NUMBER_FONT_SIZE = 9;
    private static final int CELL_NUMBER_INSET = 3;

    private static final Font PIECE_LABEL_FONT = new Font(
            Font.SANS_SERIF,
            Font.BOLD,
            PIECE_LABEL_FONT_SIZE);
    private static final Font CELL_NUMBER_FONT = new Font(
            Font.SANS_SERIF,
            Font.PLAIN,
            CELL_NUMBER_FONT_SIZE);

    private static final Color RED = new Color(220, 60, 60);
    private static final Color GREEN = new Color(60, 180, 90);
    private static final Color YELLOW = new Color(245, 200, 50);
    private static final Color BLUE = new Color(70, 120, 220);

    // Array positions match the standard-path indexes used by the socket protocol.
    private static final int[][] STANDARD_PATH_CELLS = {
            { 13, 8 }, { 12, 8 }, { 11, 8 }, { 10, 8 }, { 9, 8 },
            { 8, 9 }, { 8, 10 }, { 8, 11 }, { 8, 12 }, { 8, 13 }, { 8, 14 },
            { 7, 14 }, { 6, 14 },

            { 6, 13 }, { 6, 12 }, { 6, 11 }, { 6, 10 }, { 6, 9 },
            { 5, 8 }, { 4, 8 }, { 3, 8 }, { 2, 8 }, { 1, 8 }, { 0, 8 },
            { 0, 7 }, { 0, 6 },

            { 1, 6 }, { 2, 6 }, { 3, 6 }, { 4, 6 }, { 5, 6 },
            { 6, 5 }, { 6, 4 }, { 6, 3 }, { 6, 2 }, { 6, 1 }, { 6, 0 },
            { 7, 0 }, { 8, 0 },

            { 8, 1 }, { 8, 2 }, { 8, 3 }, { 8, 4 }, { 8, 5 },
            { 9, 6 }, { 10, 6 }, { 11, 6 }, { 12, 6 }, { 13, 6 },
            { 14, 6 }, { 14, 7 }, { 14, 8 }
    };

    private final Map<String, Point> pieceCentres = createInitialPieceCentres();

    public LudoBoardPanel() {
        Dimension boardSize = new Dimension(BOARD_SIZE, BOARD_SIZE);

        setPreferredSize(boardSize);
        setMinimumSize(boardSize);
        setMaximumSize(boardSize);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D g = (Graphics2D) graphics.create();

        g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, BOARD_SIZE, BOARD_SIZE);

        drawHomeArea(g, RED, 0, 0);
        drawHomeArea(g, GREEN, 9, 0);
        drawHomeArea(g, YELLOW, 9, 9);
        drawHomeArea(g, BLUE, 0, 9);

        drawPaths(g);
        drawHomePaths(g);
        drawCentre(g);
        drawStandardPathNumbers(g);
        drawLivePieces(g);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawRect(0, 0, BOARD_SIZE - 1, BOARD_SIZE - 1);

        g.dispose();
    }

    private void drawHomeArea(
            Graphics2D g,
            Color color,
            int column,
            int row) {
        int x = column * CELL_SIZE;
        int y = row * CELL_SIZE;

        g.setColor(color);
        g.fillRect(
                x,
                y,
                6 * CELL_SIZE,
                6 * CELL_SIZE);

        g.setColor(Color.WHITE);
        g.fillRect(
                x + CELL_SIZE,
                y + CELL_SIZE,
                4 * CELL_SIZE,
                4 * CELL_SIZE);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));

        g.drawRect(
                x,
                y,
                6 * CELL_SIZE,
                6 * CELL_SIZE);

        g.drawRect(
                x + CELL_SIZE,
                y + CELL_SIZE,
                4 * CELL_SIZE,
                4 * CELL_SIZE);

        drawHomeSlot(g,
                x + 2 * CELL_SIZE,
                y + 2 * CELL_SIZE);

        drawHomeSlot(g,
                x + 4 * CELL_SIZE,
                y + 2 * CELL_SIZE);

        drawHomeSlot(g,
                x + 2 * CELL_SIZE,
                y + 4 * CELL_SIZE);

        drawHomeSlot(g,
                x + 4 * CELL_SIZE,
                y + 4 * CELL_SIZE);
    }

    private void drawHomeSlot(
            Graphics2D g,
            int centreX,
            int centreY) {
        int diameter = 34;

        g.setColor(Color.WHITE);
        g.fillOval(
                centreX - diameter / 2,
                centreY - diameter / 2,
                diameter,
                diameter);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawOval(
                centreX - diameter / 2,
                centreY - diameter / 2,
                diameter,
                diameter);
    }

    public void updatePieceState(
            String pieceId,
            String locationType,
            String locationColour,
            int index) {
        // Protocol locations are converted into the centre point used for drawing.
        Point point = switch (locationType) {
            case "BASE" -> basePointFor(pieceId);
            case "HOME" -> homePointFor(pieceId);
            case "HOME_PATH" -> homePathPoint(locationColour, index);
            case "STANDARD_PATH",
                    "STARTING_SQUARE",
                    "APPROACH",
                    "ALPHA",
                    "BETA",
                    "GAMMA" ->
                standardPathPoint(index);
            default -> null;
        };

        if (point != null) {
            pieceCentres.put(pieceId, point);
            repaint();
        }
    }

    private Point standardPathPoint(int index) {
        if (index < 0 || index >= STANDARD_PATH_CELLS.length) {
            return null;
        }

        int[] cell = STANDARD_PATH_CELLS[index];

        return pointForCell(cell[0], cell[1]);
    }

    private Point homePathPoint(
            String colour,
            int index) {
        if (index < 0 || index >= 5) {
            return null;
        }

        return switch (colour) {
            case "RED" -> pointForCell(index + 1, 7);
            case "GREEN" -> pointForCell(7, index + 1);
            case "YELLOW" -> pointForCell(13 - index, 7);
            case "BLUE" -> pointForCell(7, 13 - index);
            default -> null;
        };
    }

    private Point pointForCell(
            int column,
            int row) {
        return new Point(
                column * CELL_SIZE + CELL_SIZE / 2,
                row * CELL_SIZE + CELL_SIZE / 2);
    }

    private Point basePointFor(String pieceId) {
        int number = Character.digit(pieceId.charAt(1), 10);

        int xOffset = number == 2 || number == 4 ? 80 : 0;
        int yOffset = number >= 3 ? 80 : 0;

        return switch (pieceId.charAt(0)) {
            case 'R' -> new Point(80 + xOffset, 80 + yOffset);
            case 'G' -> new Point(440 + xOffset, 80 + yOffset);
            case 'Y' -> new Point(440 + xOffset, 440 + yOffset);
            case 'B' -> new Point(80 + xOffset, 440 + yOffset);
            default -> null;
        };
    }

    private Point homePointFor(String pieceId) {
        int number = Character.digit(pieceId.charAt(1), 10);

        int firstOffset = number == 1 || number == 3 ? -10 : 10;
        int secondOffset = number <= 2 ? -12 : 12;

        return switch (pieceId.charAt(0)) {
            case 'R' -> new Point(
                    270 + firstOffset,
                    300 + secondOffset);
            case 'G' -> new Point(
                    300 + secondOffset,
                    270 + firstOffset);
            case 'Y' -> new Point(
                    330 - firstOffset,
                    300 + secondOffset);
            case 'B' -> new Point(
                    300 + secondOffset,
                    330 - firstOffset);
            default -> null;
        };
    }

    private Map<String, Point> createInitialPieceCentres() {
        Map<String, Point> centres = new LinkedHashMap<>();

        centres.put("R1", new Point(80, 80));
        centres.put("R2", new Point(160, 80));
        centres.put("R3", new Point(80, 160));
        centres.put("R4", new Point(160, 160));

        centres.put("G1", new Point(440, 80));
        centres.put("G2", new Point(520, 80));
        centres.put("G3", new Point(440, 160));
        centres.put("G4", new Point(520, 160));

        centres.put("Y1", new Point(440, 440));
        centres.put("Y2", new Point(520, 440));
        centres.put("Y3", new Point(440, 520));
        centres.put("Y4", new Point(520, 520));

        centres.put("B1", new Point(80, 440));
        centres.put("B2", new Point(160, 440));
        centres.put("B3", new Point(80, 520));
        centres.put("B4", new Point(160, 520));

        return centres;
    }

    public void resetPieces() {
        pieceCentres.clear();
        pieceCentres.putAll(createInitialPieceCentres());
        repaint();
    }

    public void setPieceAtBoardCell(
            String pieceId,
            int column,
            int row) {
        pieceCentres.put(
                pieceId,
                new Point(
                        column * CELL_SIZE + CELL_SIZE / 2,
                        row * CELL_SIZE + CELL_SIZE / 2));

        repaint();
    }

    private void drawLivePieces(Graphics2D g) {
        // Pieces sharing a location are grouped before offsets are calculated so
        // blockades remain visible instead of being drawn on top of each other.
        Map<Point, List<String>> piecesByCentre = new LinkedHashMap<>();

        pieceCentres.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> piecesByCentre
                        .computeIfAbsent(entry.getValue(), unused -> new ArrayList<>())
                        .add(entry.getKey()));

        for (Map.Entry<Point, List<String>> group : piecesByCentre.entrySet()) {
            Point centre = group.getKey();
            List<String> pieceIds = group.getValue();
            Point[] offsets = drawingOffsets(pieceIds.size());
            boolean grouped = pieceIds.size() > 1;

            for (int index = 0; index < pieceIds.size(); index++) {
                String pieceId = pieceIds.get(index);
                Point offset = offsets[index];

                drawPiece(
                        g,
                        pieceId,
                        centre.x + offset.x,
                        centre.y + offset.y,
                        grouped);
            }
        }
    }

    private Point[] drawingOffsets(int pieceCount) {
        return switch (pieceCount) {
            case 1 -> new Point[] {
                    new Point(0, 0)
            };
            case 2 -> new Point[] {
                    new Point(-GROUPED_PIECE_OFFSET, 0),
                    new Point(GROUPED_PIECE_OFFSET, 0)
            };
            case 3 -> new Point[] {
                    new Point(0, -GROUPED_PIECE_OFFSET),
                    new Point(-GROUPED_PIECE_OFFSET, GROUPED_PIECE_OFFSET),
                    new Point(GROUPED_PIECE_OFFSET, GROUPED_PIECE_OFFSET)
            };
            case 4 -> new Point[] {
                    new Point(-GROUPED_PIECE_OFFSET, -GROUPED_PIECE_OFFSET),
                    new Point(GROUPED_PIECE_OFFSET, -GROUPED_PIECE_OFFSET),
                    new Point(-GROUPED_PIECE_OFFSET, GROUPED_PIECE_OFFSET),
                    new Point(GROUPED_PIECE_OFFSET, GROUPED_PIECE_OFFSET)
            };
            default -> gridOffsets(pieceCount);
        };
    }

    private Point[] gridOffsets(int pieceCount) {
        // A centred grid handles mixed-colour groups larger than the common cases.
        int columns = (int) Math.ceil(Math.sqrt(pieceCount));
        int rows = (pieceCount + columns - 1) / columns;
        Point[] offsets = new Point[pieceCount];

        for (int index = 0; index < pieceCount; index++) {
            int row = index / columns;
            int column = index % columns;
            int piecesInRow = Math.min(columns, pieceCount - row * columns);
            int x = (2 * column - piecesInRow + 1) * GROUPED_PIECE_OFFSET;
            int y = (2 * row - rows + 1) * GROUPED_PIECE_OFFSET;

            offsets[index] = new Point(x, y);
        }

        return offsets;
    }

    private Color colourFor(String pieceId) {
        return switch (pieceId.charAt(0)) {
            case 'R' -> RED;
            case 'G' -> GREEN;
            case 'Y' -> YELLOW;
            case 'B' -> BLUE;
            default -> Color.BLACK;
        };
    }

    private void drawPiece(
            Graphics2D g,
            String pieceId,
            int centreX,
            int centreY,
            boolean grouped) {
        int outerDiameter = grouped
                ? GROUPED_PIECE_OUTER_DIAMETER
                : SINGLE_PIECE_OUTER_DIAMETER;
        int pieceDiameter = grouped
                ? GROUPED_PIECE_DIAMETER
                : SINGLE_PIECE_DIAMETER;
        Color pieceColour = colourFor(pieceId);

        g.setColor(Color.WHITE);
        g.fillOval(
                centreX - outerDiameter / 2,
                centreY - outerDiameter / 2,
                outerDiameter,
                outerDiameter);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));
        g.drawOval(
                centreX - outerDiameter / 2,
                centreY - outerDiameter / 2,
                outerDiameter,
                outerDiameter);

        g.setColor(pieceColour);
        g.fillOval(
                centreX - pieceDiameter / 2,
                centreY - pieceDiameter / 2,
                pieceDiameter,
                pieceDiameter);

        g.setColor(Color.BLACK);
        g.drawOval(
                centreX - pieceDiameter / 2,
                centreY - pieceDiameter / 2,
                pieceDiameter,
                pieceDiameter);

        drawPieceId(g, pieceId, pieceColour, centreX, centreY);
    }

    private void drawPieceId(
            Graphics2D g,
            String pieceId,
            Color pieceColour,
            int centreX,
            int centreY) {
        g.setFont(PIECE_LABEL_FONT);
        g.setColor(readableTextColour(pieceColour));

        FontMetrics metrics = g.getFontMetrics();
        int textX = centreX - metrics.stringWidth(pieceId) / 2;
        int textY = centreY + (metrics.getAscent() - metrics.getDescent()) / 2;

        g.drawString(pieceId, textX, textY);
    }

    private Color readableTextColour(Color pieceColour) {
        return pieceColour == RED || pieceColour == BLUE
                ? Color.WHITE
                : Color.BLACK;
    }

    private void drawStandardPathNumbers(Graphics2D g) {
        g.setFont(CELL_NUMBER_FONT);
        g.setColor(Color.BLACK);

        FontMetrics metrics = g.getFontMetrics();

        for (int index = 0; index < STANDARD_PATH_CELLS.length; index++) {
            int[] cell = STANDARD_PATH_CELLS[index];
            int x = cell[0] * CELL_SIZE + CELL_NUMBER_INSET;
            int y = cell[1] * CELL_SIZE + CELL_NUMBER_INSET + metrics.getAscent();

            g.drawString(String.valueOf(index), x, y);
        }
    }

    private void drawPaths(Graphics2D g) {
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int column = 0; column < GRID_SIZE; column++) {

                boolean horizontalPath = row >= 6 && row <= 8;

                boolean verticalPath = column >= 6 && column <= 8;

                if (horizontalPath || verticalPath) {
                    drawCell(
                            g,
                            column,
                            row,
                            Color.WHITE);
                }
            }
        }

        drawCell(g, 1, 6, RED);
        drawCell(g, 8, 1, GREEN);
        drawCell(g, 13, 8, YELLOW);
        drawCell(g, 6, 13, BLUE);
    }

    private void drawHomePaths(Graphics2D g) {
        for (int column = 1; column <= 5; column++) {
            drawCell(g, column, 7, RED);
        }

        for (int row = 1; row <= 5; row++) {
            drawCell(g, 7, row, GREEN);
        }

        for (int column = 9; column <= 13; column++) {
            drawCell(g, column, 7, YELLOW);
        }

        for (int row = 9; row <= 13; row++) {
            drawCell(g, 7, row, BLUE);
        }
    }

    private void drawCell(
            Graphics2D g,
            int column,
            int row,
            Color color) {
        int x = column * CELL_SIZE;
        int y = row * CELL_SIZE;

        g.setColor(color);
        g.fillRect(
                x,
                y,
                CELL_SIZE,
                CELL_SIZE);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1));

        g.drawRect(
                x,
                y,
                CELL_SIZE,
                CELL_SIZE);
    }

    private void drawCentre(Graphics2D g) {
        int left = 6 * CELL_SIZE;
        int top = 6 * CELL_SIZE;
        int right = 9 * CELL_SIZE;
        int bottom = 9 * CELL_SIZE;

        int centreX = BOARD_SIZE / 2;
        int centreY = BOARD_SIZE / 2;

        g.setColor(RED);
        g.fillPolygon(
                new int[] { left, centreX, left },
                new int[] { top, centreY, bottom },
                3);

        g.setColor(GREEN);
        g.fillPolygon(
                new int[] { left, centreX, right },
                new int[] { top, centreY, top },
                3);

        g.setColor(YELLOW);
        g.fillPolygon(
                new int[] { right, centreX, right },
                new int[] { top, centreY, bottom },
                3);

        g.setColor(BLUE);
        g.fillPolygon(
                new int[] { left, centreX, right },
                new int[] { bottom, centreY, bottom },
                3);

        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(2));

        g.drawRect(
                left,
                top,
                3 * CELL_SIZE,
                3 * CELL_SIZE);

        g.drawLine(left, top, centreX, centreY);
        g.drawLine(right, top, centreX, centreY);
        g.drawLine(right, bottom, centreX, centreY);
        g.drawLine(left, bottom, centreX, centreY);
    }
}
