package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.command.BlockadeCaptureCommand;
import com.ludot.command.CaptureCommand;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.model.Cell;
import com.ludot.model.Colour;
import com.ludot.model.Location;
import com.ludot.model.Piece;

import java.util.List;

// Applies the capture rules for single pieces and locked groups.
public class CaptureRule {

    // Board used to inspect occupied cells during capture checks.
    private final Board board;

    public CaptureRule(Board board) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        this.board = board;
    }

    // Returns true when a single piece can legally capture an enemy piece on this
    // cell.
    public boolean canCapture(Piece capturingPiece, Location location) {
        if (capturingPiece == null || location == null)
            return false;
        if (!location.isOnStandardRing())
            return false;
        if (capturingPiece.getLocation() == null || !capturingPiece.getLocation().equals(location))
            return false;

        Cell cell = board.getStandardCell(location.getIndex());
        if (!cell.isOccupied())
            return false;

        if (cell.isBlocked())
            return false;

        for (Piece occupant : cell.getPieces()) {
            if (occupant.getColour() != capturingPiece.getColour())
                return true;
        }
        return false;
    }

    // Finds the first enemy piece that can be captured on this cell.
    public Piece findCapturablePiece(Piece capturingPiece, Location location) {
        if (capturingPiece == null || location == null)
            return null;
        if (!location.isOnStandardRing())
            return null;
        if (capturingPiece.getLocation() == null || !capturingPiece.getLocation().equals(location))
            return null;

        Cell cell = board.getStandardCell(location.getIndex());
        if (cell.isBlocked())
            return null;

        for (Piece occupant : cell.getPieces()) {
            if (occupant.getColour() != capturingPiece.getColour())
                return occupant;
        }
        return null;
    }

    // Returns true when two blockade groups are allowed to capture each other.
    public boolean canCaptureBlockade(List<Piece> capturingBlockadePieces, List<Piece> capturedBlockadePieces) {
        if (!isValidBlockadeCapture(capturingBlockadePieces, capturedBlockadePieces))
            return false;

        Colour capturingColour = capturingBlockadePieces.get(0).getColour();
        Colour capturedColour = capturedBlockadePieces.get(0).getColour();
        return capturingColour != capturedColour;
    }

    // Builds the blockade-capture command only when the move is legal.
    public Command commandIfBlockadeCaptureAvailable(List<Piece> capturingBlockadePieces,
                                                     List<Piece> capturedBlockadePieces, Location location) {
        if (!canCaptureBlockade(capturingBlockadePieces, capturedBlockadePieces))
            return new NoMoveCommand();

        return new BlockadeCaptureCommand(board, capturingBlockadePieces, capturedBlockadePieces, location);
    }

    // Builds the single-piece capture command only when the move is legal.
    public Command commandIfCaptureAvailable(Piece capturingPiece, Location location) {
        if (!canCapture(capturingPiece, location))
            return new NoMoveCommand();

        Piece target = findCapturablePiece(capturingPiece, location);
        if (target == null)
            return new NoMoveCommand();

        return new CaptureCommand(board, capturingPiece, target, location);
    }

    // Ensures both blockade groups have matching structure before capture is
    // allowed.
    private boolean isValidBlockadeCapture(List<Piece> capturingBlockadePieces,
                                           List<Piece> capturedBlockadePieces) {
        if (capturingBlockadePieces == null || capturedBlockadePieces.isEmpty())
            return false;
        if (capturedBlockadePieces == null || capturedBlockadePieces.isEmpty())
            return false;
        if (capturingBlockadePieces.size() != capturedBlockadePieces.size())
            return false;

        Colour capturingColour = capturingBlockadePieces.get(0).getColour();
        if (capturingColour == null)
            return false;
        for (Piece piece : capturingBlockadePieces) {
            if (piece == null || piece.getColour() != capturingColour)
                return false;
        }

        Colour capturedColour = capturedBlockadePieces.get(0).getColour();
        if (capturedColour == null)
            return false;
        for (Piece piece : capturedBlockadePieces) {
            if (piece == null || piece.getColour() != capturedColour)
                return false;
        }

        return true;
    }
}