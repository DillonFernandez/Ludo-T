package com.ludot.command;

import com.ludot.board.Board;
import com.ludot.model.Location;
import com.ludot.model.Piece;

import java.util.ArrayList;
import java.util.List;

// Moves a group of same-colour pieces together to one standard-path square.
public class BlockMoveCommand implements Command {

    // The command stores the board, the moving pieces, and the shared destination.
    private final Board board;
    private final List<Piece> pieces;
    private final Location destination;

    // The pieces are copied so the command uses a stable snapshot of the move.
    public BlockMoveCommand(Board board, List<Piece> pieces, Location destination) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (pieces == null || pieces.isEmpty())
            throw new IllegalArgumentException("Pieces must not be null or empty.");
        if (destination == null)
            throw new IllegalArgumentException("Destination must not be null.");
        this.board = board;
        this.pieces = new ArrayList<>(pieces);
        this.destination = destination;
    }

    // Execution places each piece on the same standard-path square.
    @Override
    public void execute() {
        for (Piece piece : pieces) {
            board.placePieceOnStandardPath(piece, destination.getIndex());
        }
    }

    // These accessors expose the move details without allowing mutation.
    public List<Piece> getPieces() {
        return List.copyOf(pieces);
    }

    public Location getDestination() {
        return destination;
    }
}