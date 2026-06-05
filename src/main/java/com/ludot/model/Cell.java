package com.ludot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Represents one board square and the pieces currently on it.
public class Cell {

    // The square stores its position, type, owner, and current occupants.
    private final Location location;
    private final CellType cellType;
    private final Colour owner;
    private final List<Piece> pieces;

    // This constructor initializes the square with no pieces on it yet.
    public Cell(Location location, CellType cellType, Colour owner) {
        if (location == null) {
            throw new IllegalArgumentException("Cell location must not be null.");
        }
        if (cellType == null) {
            throw new IllegalArgumentException("Cell type must not be null.");
        }
        this.location = location;
        this.cellType = cellType;
        this.owner = owner;
        this.pieces = new ArrayList<>();
    }

    // This shared-cell form is used for squares that are not owned by one player.
    public Cell(Location location, CellType cellType) {
        this(location, cellType, null);
    }

    // These methods update the pieces currently occupying this square.
    public void addPiece(Piece piece) {
        if (piece == null) {
            throw new IllegalArgumentException("Cannot add a null piece to a cell.");
        }
        pieces.add(piece);
    }

    // Removing a missing piece is safe because the list operation simply has no
    // effect.
    public void removePiece(Piece piece) {
        pieces.remove(piece);
    }

    // A square is occupied when it holds at least one piece.
    public boolean isOccupied() {
        return !pieces.isEmpty();
    }

    // A blockade forms when the same colour occupies the square with more than one
    // piece.
    public boolean isBlocked() {
        if (pieces.size() < 2)
            return false;
        Colour first = pieces.get(0).getColour();
        for (int i = 1; i < pieces.size(); i++) {
            if (pieces.get(i).getColour() != first)
                return false;
        }
        return true;
    }

    // The returned list is read-only so callers cannot change the cell's internal
    // state directly.
    public List<Piece> getPieces() {
        return Collections.unmodifiableList(pieces);
    }

    // These accessors expose the square's identity and ownership.
    public Location getLocation() {
        return location;
    }

    public Colour getOwner() {
        return owner;
    }

    @Override
    public String toString() {
        return "Cell{" + location.getDisplayName() + ", " + cellType + "}";
    }
}