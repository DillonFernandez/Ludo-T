package com.ludot.command;

import com.ludot.model.Location;
import com.ludot.model.Piece;

/**
 * @param piece The command stores the moving piece, the blocking location, and the blocking piece.
 */ // Records a blocked move so the game can report why the move did not proceed.
public record BlockedMoveCommand(Piece piece, Location blockingLocation, Piece blockingPiece) implements Command {

    // The details are kept as immutable evidence of the blocked move for reporting
    // or tests.
    public BlockedMoveCommand {
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
        if (blockingLocation == null)
            throw new IllegalArgumentException("Blocking location must not be null.");
        if (blockingPiece == null)
            throw new IllegalArgumentException("Blocking piece must not be null.");
    }

    // Execution is intentionally empty because a blocked move does not change the
    // board state.
    @Override
    public void execute() {
        // intentionally left empty
    }
}