// Draws the LUDO-T board and snapshot pieces, with location tooltips and accessibility text.
package ludot.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import ludot.application.GameSnapshot;
import ludot.config.GameSettings;
import ludot.model.Colour;

public final class BoardPanel extends JPanel {
    private static final Color[] COLOURS = {
            new Color(220, 60, 60), new Color(60, 180, 90),
            new Color(245, 200, 50), new Color(70, 120, 220)
    };
    private static final Point[] PATH = createPath();
    private GameSnapshot snapshot;

    public BoardPanel() {
        setPreferredSize(new Dimension(750, 750));
        setMinimumSize(new Dimension(300, 300));
        setBackground(Color.WHITE);
        setToolTipText("LUDO-T board");
        getAccessibleContext().setAccessibleName("LUDO-T board");
    }

    public void setSnapshot(GameSnapshot snapshot) {
        this.snapshot = snapshot;
        getAccessibleContext().setAccessibleDescription(
                "Round " + snapshot.getRound() + ". Piece locations are also listed in the simulation output.");
        repaint();
    }

    private static Point[] createPath() {
        List<Point> cells = new ArrayList<>();
        for (int x = 13; x >= 9; x--) {
            cells.add(new Point(x, 8));
        }
        for (int y = 9; y <= 14; y++) {
            cells.add(new Point(8, y));
        }
        cells.add(new Point(7, 14));
        for (int y = 14; y >= 9; y--) {
            cells.add(new Point(6, y));
        }
        for (int x = 5; x >= 0; x--) {
            cells.add(new Point(x, 8));
        }
        cells.add(new Point(0, 7));
        for (int x = 0; x <= 5; x++) {
            cells.add(new Point(x, 6));
        }
        for (int y = 5; y >= 0; y--) {
            cells.add(new Point(6, y));
        }
        cells.add(new Point(7, 0));
        for (int y = 0; y <= 5; y++) {
            cells.add(new Point(8, y));
        }
        for (int x = 9; x <= 14; x++) {
            cells.add(new Point(x, 6));
        }
        cells.add(new Point(14, 7));
        cells.add(new Point(14, 8));
        return cells.toArray(new Point[0]);
    }

    public static Point standardCell(int location) {
        if (location < 0 || location >= PATH.length) {
            throw new IllegalArgumentException("Invalid standard cell.");
        }
        return new Point(PATH[location]);
    }

    public static Point homeCell(Colour colour, int location) {
        if (location < 52 || location > 56) {
            throw new IllegalArgumentException("Invalid home-straight cell.");
        }
        int offset = location - 52;
        switch (colour) {
            case RED:
                return new Point(1 + offset, 7);
            case GREEN:
                return new Point(7, 1 + offset);
            case YELLOW:
                return new Point(13 - offset, 7);
            case BLUE:
                return new Point(7, 13 - offset);
            default:
                throw new IllegalArgumentException("Unknown colour.");
        }
    }

    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double size = Math.min(getWidth(), getHeight());
            double cell = size / 15.0;
            g.translate((getWidth() - size) / 2, (getHeight() - size) / 2);
            g.scale(cell, cell);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke((float) (1.2 / cell)));
            drawBase(g, 0, 0, 0);
            drawBase(g, 1, 9, 0);
            drawBase(g, 3, 0, 9);
            drawBase(g, 2, 9, 9);
            for (int location = 0; location < PATH.length; location++) {
                Point point = PATH[location];
                Color fill = Color.WHITE;
                for (Colour colour : Colour.values()) {
                    if (GameSettings.getInstance().getStartingCell(colour) == location) {
                        fill = COLOURS[colour.ordinal()];
                    }
                }
                drawCell(g, point, fill);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(0.22f));
                g.drawString(Integer.toString(location), point.x + 0.08f, point.y + 0.28f);
            }
            for (Colour colour : Colour.values()) {
                for (int location = 52; location <= 56; location++) {
                    drawCell(g, homeCell(colour, location), COLOURS[colour.ordinal()]);
                }
            }
            drawHome(g, 0, 6, 6, 6, 9);
            drawHome(g, 1, 6, 6, 9, 6);
            drawHome(g, 2, 9, 6, 9, 9);
            drawHome(g, 3, 6, 9, 9, 9);
            if (snapshot != null) {
                drawMystery(g);
                drawPieces(g);
            }
            g.setColor(Color.BLACK);
            g.draw(new Rectangle2D.Double(0, 0, 15, 15));
        } finally {
            g.dispose();
        }
    }

    private void drawBase(Graphics2D g, int colour, int x, int y) {
        g.setColor(COLOURS[colour]);
        g.fill(new Rectangle2D.Double(x, y, 6, 6));
        g.setColor(Color.BLACK);
        g.draw(new Rectangle2D.Double(x, y, 6, 6));
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(x + 1, y + 1, 4, 4));
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(0.05f));
        g.draw(new Rectangle2D.Double(x + 1, y + 1, 4, 4));
        for (int piece = 0; piece < 4; piece++) {
            double cx = x + 2 + (piece % 2) * 2;
            double cy = y + 2 + (piece / 2) * 2;
            g.draw(new Ellipse2D.Double(cx - 0.42, cy - 0.42, 0.84, 0.84));
        }
        g.setStroke(new BasicStroke(0.025f));
    }

    private void drawCell(Graphics2D g, Point point, Color fill) {
        Rectangle2D cell = new Rectangle2D.Double(point.x, point.y, 1, 1);
        g.setColor(fill);
        g.fill(cell);
        g.setColor(Color.BLACK);
        g.draw(cell);
    }

    private void drawHome(Graphics2D g, int colour, int x1, int y1, int x2, int y2) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(x1, y1);
        triangle.lineTo(x2, y2);
        triangle.lineTo(7.5, 7.5);
        triangle.closePath();
        g.setColor(COLOURS[colour]);
        g.fill(triangle);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(0.05f));
        g.draw(triangle);
        g.setStroke(new BasicStroke(0.025f));
    }

    private void drawMystery(Graphics2D g) {
        if (snapshot.getMystery() < 0) {
            return;
        }
        Point point = standardCell(snapshot.getMystery());
        g.setColor(new Color(95, 35, 120));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(0.48f));
        g.drawString("?", point.x + 0.35f, point.y + 0.83f);
    }

    private void drawPieces(Graphics2D g) {
        int[] locations = snapshot.getLocations();
        for (int index = 0; index < locations.length; index++) {
            int colour = index / 4;
            int number = index % 4;
            int location = locations[index];
            double x;
            double y;
            double diameter = 0.82;
            if (location == -1) {
                int baseX = colour == 1 || colour == 2 ? 9 : 0;
                int baseY = colour == 2 || colour == 3 ? 9 : 0;
                x = baseX + 2 + (number % 2) * 2;
                y = baseY + 2 + (number / 2) * 2;
            } else if (location == 57) {
                double along = number % 2 == 0 ? 7.18 : 7.82;
                double depth = number / 2 == 0 ? 6.4 : 6.95;
                if (colour == 0 || colour == 2) {
                    x = colour == 0 ? depth : 15 - depth;
                    y = along;
                } else {
                    x = along;
                    y = colour == 1 ? depth : 15 - depth;
                }
            } else {
                Point point = location < 52 ? standardCell(location)
                        : homeCell(Colour.values()[colour], location);
                x = point.x + 0.5;
                y = point.y + 0.5;
                int count = 0;
                int position = 0;
                for (int other = 0; other < locations.length; other++) {
                    if (locations[other] == location && (location < 52 || other / 4 == colour)) {
                        if (other < index) {
                            position++;
                        }
                        count++;
                    }
                }
                if (count > 1) {
                    x = point.x + 0.32 + (position % 2) * 0.36;
                    y = point.y + (count > 2 ? 0.32 + (position / 2) * 0.36 : 0.5);
                }
            }
            drawPiece(g, x, y, diameter, colour, number + 1);
        }
    }

    private void drawPiece(Graphics2D g, double x, double y, double diameter, int colour, int number) {
        Ellipse2D ring = new Ellipse2D.Double(x - diameter / 2, y - diameter / 2, diameter, diameter);
        g.setColor(Color.WHITE);
        g.fill(ring);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(0.045f));
        g.draw(ring);
        double inner = diameter * 0.78;
        Ellipse2D token = new Ellipse2D.Double(x - inner / 2, y - inner / 2, inner, inner);
        g.setColor(COLOURS[colour]);
        g.fill(token);
        g.setColor(Color.BLACK);
        g.draw(token);
        g.setColor(colour == 0 || colour == 3 ? Color.WHITE : Color.BLACK);
        float fontSize = (float) (diameter * 0.32);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 1).deriveFont(fontSize));
        String name = Colour.values()[colour].getInitial() + number;
        double textWidth = g.getFont().getStringBounds(name, g.getFontRenderContext()).getWidth();
        g.drawString(name, (float) (x - textWidth / 2),
                (float) y + fontSize * 0.35f);
        g.setStroke(new BasicStroke(0.025f));
    }

    public String getToolTipText(MouseEvent event) {
        if (snapshot == null) {
            return "LUDO-T board";
        }
        double size = Math.min(getWidth(), getHeight());
        double x = (event.getX() - (getWidth() - size) / 2) * 15 / size;
        double y = (event.getY() - (getHeight() - size) / 2) * 15 / size;
        for (int location = 0; location < PATH.length; location++) {
            Point point = PATH[location];
            if (x >= point.x && x < point.x + 1 && y >= point.y && y < point.y + 1) {
                String text = "Cell " + location;
                int[] positions = snapshot.getLocations();
                for (int index = 0; index < positions.length; index++) {
                    if (positions[index] == location) {
                        text += " " + Colour.values()[index / 4].getInitial() + (index % 4 + 1);
                    }
                }
                return text + (location == snapshot.getMystery() ? " - mystery cell" : "");
            }
        }
        return "Round " + snapshot.getRound() + ". See simulation output for all piece locations.";
    }
}
