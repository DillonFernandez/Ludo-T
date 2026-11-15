// Owns a colour's four pieces and calculates their counts and winning status.
package ludot.model;

import ludot.config.GameSettings;

public final class Player {
    private final Colour colour;
    private final Piece[] pieces;

    public Player(Colour colour) {
        if (colour == null) {
            throw new IllegalArgumentException("A player needs a colour.");
        }
        this.colour = colour;
        pieces = new Piece[GameSettings.getInstance().getPiecesPerPlayer()];
        for (int index = 0; index < pieces.length; index++) {
            pieces[index] = new Piece(colour, index + 1);
        }
    }

    public Colour getColour() {
        return colour;
    }

    public Piece[] getPieces() {
        return pieces.clone();
    }

    public int countAtBase() {
        int count = 0;
        for (Piece piece : pieces) {
            if (piece.isAtBase()) {
                count++;
            }
        }
        return count;
    }

    public int countOnBoard() {
        int count = 0;
        for (Piece piece : pieces) {
            if (!piece.isAtBase() && !piece.isAtHome()) {
                count++;
            }
        }
        return count;
    }

    public boolean hasWon() {
        for (Piece piece : pieces) {
            if (!piece.isAtHome()) {
                return false;
            }
        }
        return true;
    }
}
