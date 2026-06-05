package com.ludot.command;

import com.ludot.board.Board;
import com.ludot.model.Location;
import com.ludot.model.Piece;

// Moves one piece to the square just before an opponent's blockade.
public class MoveBeforeBlockCommand implements Command {

    // The command stores the board, the moving piece, and the target square.
    private final Board board;
    private final Piece piece;
    private final Location destination;

    // The move details are kept so the command can execute later.
    public MoveBeforeBlockCommand(Board board, Piece piece, Location destination) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
        if (destination == null)
            throw new IllegalArgumentException("Destination must not be null.");
        this.board = board;
        this.piece = piece;
        this.destination = destination;
    }

    // Execution places the piece on the standard path at the requested index.
    @Override
    public void execute() {
        board.placePieceOnStandardPath(piece, destination.getIndex());
    }

    // This exposes the piece that this command will move.
    public Piece getPiece() {
        return piece;
    }

    // This exposes the destination square for the move.
    public Location getDestination() {
        return destination;
    }
}