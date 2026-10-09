package com.ludot.command;

import com.ludot.board.Board;
import com.ludot.model.Location;
import com.ludot.model.Piece;

import java.util.ArrayList;
import java.util.List;

// Captures a same-sized blockade and returns the captured pieces to base while crediting the capturers.
public class BlockadeCaptureCommand implements Command {

    // The command stores the board, the two piece groups, and the capture location.
    private final Board board;
    private final List<Piece> capturingPieces;
    private final List<Piece> capturedPieces;
    private final Location captureLocation;

    // The lists are copied so the command works with a stable snapshot of the
    // capture.
    public BlockadeCaptureCommand(Board board, List<Piece> capturingPieces,
                                  List<Piece> capturedPieces, Location captureLocation) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (capturingPieces == null || capturingPieces.isEmpty())
            throw new IllegalArgumentException("Capturing pieces must not be null or empty.");
        if (capturedPieces == null || capturedPieces.isEmpty())
            throw new IllegalArgumentException("Captured pieces must not be null or empty.");
        if (captureLocation == null)
            throw new IllegalArgumentException("Capture location must not be null.");
        this.board = board;
        this.capturingPieces = new ArrayList<>(capturingPieces);
        this.capturedPieces = new ArrayList<>(capturedPieces);
        this.captureLocation = captureLocation;
    }

    // Execution updates capture counts and sends each captured piece back to its
    // home area.
    @Override
    public void execute() {
        for (Piece capturingPiece : capturingPieces) {
            capturingPiece.incrementCaptureCount();
        }
        for (Piece capturedPiece : capturedPieces) {
            board.returnPieceToBase(capturedPiece);
        }
    }

    // These accessors expose the capture details without allowing the internal
    // lists to be changed.
    public List<Piece> getCapturingPieces() {
        return List.copyOf(capturingPieces);
    }

    public List<Piece> getCapturedPieces() {
        return List.copyOf(capturedPieces);
    }

    public Location getCaptureLocation() {
        return captureLocation;
    }
}