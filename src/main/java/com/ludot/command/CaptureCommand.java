package com.ludot.command;

import com.ludot.board.Board;
import com.ludot.model.Location;
import com.ludot.model.Piece;

// Executes a capture by crediting the capturer and returning the captured piece to base.
public class CaptureCommand implements Command {

    // The command stores the board, the two pieces involved, and the capture
    // location.
    private final Board board;
    private final Piece capturingPiece;
    private final Piece capturedPiece;
    private final Location captureLocation;

    // The capture details are kept for execution when the command runs.
    public CaptureCommand(Board board, Piece capturingPiece,
                          Piece capturedPiece, Location captureLocation) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (capturingPiece == null)
            throw new IllegalArgumentException("Capturing piece must not be null.");
        if (capturedPiece == null)
            throw new IllegalArgumentException("Captured piece must not be null.");
        if (captureLocation == null)
            throw new IllegalArgumentException("Capture location must not be null.");
        this.board = board;
        this.capturingPiece = capturingPiece;
        this.capturedPiece = capturedPiece;
        this.captureLocation = captureLocation;
    }

    // Execution updates the capture count and sends the captured piece back to
    // base.
    @Override
    public void execute() {
        capturingPiece.incrementCaptureCount();
        board.returnPieceToBase(capturedPiece);
    }

    // These accessors expose the capture details without allowing mutation.
    public Piece getCapturingPiece() {
        return capturingPiece;
    }

    public Piece getCapturedPiece() {
        return capturedPiece;
    }

    public Location getCaptureLocation() {
        return captureLocation;
    }
}