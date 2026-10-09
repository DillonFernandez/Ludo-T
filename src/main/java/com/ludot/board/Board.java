package com.ludot.board;

import com.ludot.exception.InvalidLocationException;
import com.ludot.model.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Stores the board layout used by LUDO-T: main path cells, home paths, home areas, and named special squares.
public class Board {

    // The board keeps references to the main path, each home path, and the special
    // squares used by rule effects.
    private final Map<Integer, Cell> standardCells;
    private final Map<Colour, List<Cell>> homePathCells;
    private final Map<Colour, HomeArea> homeAreas;
    private final Path path;

    // Called only by BoardBuilder.
    public Board(Map<Integer, Cell> standardCells,
                 Map<Colour, List<Cell>> homePathCells,
                 Map<Colour, HomeArea> homeAreas,
                 Path path) {
        if (standardCells == null) {
            throw new IllegalArgumentException("standardCells must not be null.");
        }
        if (homePathCells == null) {
            throw new IllegalArgumentException("homePathCells must not be null.");
        }
        if (homeAreas == null) {
            throw new IllegalArgumentException("homeAreas must not be null.");
        }
        if (path == null) {
            throw new IllegalArgumentException("path must not be null.");
        }
        this.standardCells = standardCells;
        this.homePathCells = homePathCells;
        this.homeAreas = homeAreas;
        this.path = path;
    }

    // These guards keep invalid input from reaching the board update logic.
    private static void requirePiece(Piece piece) {
        if (piece == null) {
            throw new IllegalArgumentException("Piece must not be null.");
        }
    }

    private static void requireColour(Colour colour) {
        if (colour == null) {
            throw new IllegalArgumentException("Colour must not be null.");
        }
    }

    // These helpers locate a square by its position on the board.
    public Cell getStandardCell(int index) {
        path.validateStandardIndex(index);
        Cell cell = standardCells.get(index);
        if (cell == null) {
            throw new InvalidLocationException("No standard cell found at index " + index);
        }
        return cell;
    }

    public Cell getHomePathCell(Colour colour, int index) {
        requireColour(colour);
        List<Cell> cells = homePathCells.get(colour);
        if (cells == null || index < 0 || index >= cells.size()) {
            throw new InvalidLocationException(
                    "No home path cell for " + colour + " at index " + index);
        }
        return cells.get(index);
    }

    // Each colour owns its home area, where pieces begin and can be returned.
    public HomeArea getHomeArea(Colour colour) {
        requireColour(colour);
        HomeArea area = homeAreas.get(colour);
        if (area == null) {
            throw new InvalidLocationException("No HomeArea found for colour " + colour);
        }
        return area;
    }

    public Path getPath() {
        return path;
    }

    // These helpers expose the named entry and approach points for each colour.
    public Location getStartingLocation(Colour colour) {
        requireColour(colour);
        int index = path.getStartingIndex(colour);
        return Location.startingSquare(colour, index);
    }

    public Location getApproachLocation(Colour colour) {
        requireColour(colour);
        int index = path.getApproachIndex(colour);
        return Location.approach(colour, index);
    }

    public Location getAlphaLocation() {
        return Location.alpha(path.getAlphaIndex());
    }

    public Location getBetaLocation() {
        return Location.beta(path.getBetaIndex());
    }

    // Move helpers always remove a piece from its current cell before placing it
    // elsewhere.

    public Location getGammaLocation() {
        return Location.gamma(path.getGammaIndex());
    }

    // Occupied standard-path cells are useful for move logic and tests.
    public Set<Integer> getOccupiedStandardPathIndices() {
        Set<Integer> occupied = new HashSet<>();
        for (Map.Entry<Integer, Cell> entry : standardCells.entrySet()) {
            if (entry.getValue().isOccupied()) {
                occupied.add(entry.getKey());
            }
        }
        return occupied;
    }

    // This starts a piece from its home area onto the main path at its entry
    // square.
    public void movePieceFromBaseToStartingSquare(Piece piece) {
        requirePiece(piece);
        int startIndex = path.getStartingIndex(piece.getColour());
        getHomeArea(piece.getColour()).removePiece(piece);
        getStandardCell(startIndex).addPiece(piece);
        piece.setLocation(Location.startingSquare(piece.getColour(), startIndex));
    }

    // A standard-path move updates the piece location after the old cell is
    // cleared.
    public void placePieceOnStandardPath(Piece piece, int index) {
        requirePiece(piece);
        removePieceFromCurrentLocation(piece);
        Cell cell = getStandardCell(index);
        cell.addPiece(piece);
        piece.setLocation(cell.getLocation());
    }

    public void placePieceInHomePath(Piece piece, Colour colour, int homePathIndex) {
        requirePiece(piece);
        requireColour(colour);
        removePieceFromCurrentLocation(piece);
        Cell cell = getHomePathCell(colour, homePathIndex);
        cell.addPiece(piece);
        piece.setLocation(cell.getLocation());
    }

    // Teleports use the named special squares that are part of the LUDO-T rule set.
    public void teleportPieceToAlpha(Piece piece) {
        requirePiece(piece);
        int index = path.getAlphaIndex();
        removePieceFromCurrentLocation(piece);
        getStandardCell(index).addPiece(piece);
        piece.setLocation(Location.alpha(index));
    }

    // Move a piece instantly to the Beta square and record its new spot.
    public void teleportPieceToBeta(Piece piece) {
        requirePiece(piece);
        int index = path.getBetaIndex();
        removePieceFromCurrentLocation(piece);
        getStandardCell(index).addPiece(piece);
        piece.setLocation(Location.beta(index));
    }

    // Move a piece instantly to the Gamma square and record its new spot.
    public void teleportPieceToGamma(Piece piece) {
        requirePiece(piece);
        int index = path.getGammaIndex();
        removePieceFromCurrentLocation(piece);
        getStandardCell(index).addPiece(piece);
        piece.setLocation(Location.gamma(index));
    }

    // Returning a piece to base resets its state and places it back in its home
    // area.
    public void returnPieceToBase(Piece piece) {
        requirePiece(piece);
        removePieceFromCurrentLocation(piece);
        piece.resetToBase();
        getHomeArea(piece.getColour()).addPiece(piece);
    }

    // This removes a piece from whatever board location it currently occupies.
    // The HOME case is intentionally empty because finished pieces are no longer
    // stored in board cells.
    public void removePieceFromCurrentLocation(Piece piece) {
        requirePiece(piece);
        LocationType type = piece.getLocation().getType();

        switch (type) {
            case BASE -> getHomeArea(piece.getColour()).removePiece(piece);

            case HOME -> {
                // The piece has reached the finish; nothing to take away.
            }

            case STANDARD_PATH,
                 STARTING_SQUARE,
                 APPROACH,
                 ALPHA,
                 BETA,
                 GAMMA -> {
                int index = piece.getLocation().getIndex();
                getStandardCell(index).removePiece(piece);
            }

            case HOME_PATH -> {
                int index = piece.getLocation().getIndex();
                getHomePathCell(piece.getColour(), index).removePiece(piece);
            }
        }
    }
}