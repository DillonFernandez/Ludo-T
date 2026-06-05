package com.ludot.command;

import com.ludot.board.Board;
import com.ludot.model.Colour;
import com.ludot.model.Location;
import com.ludot.model.Piece;

// Represents one move action that can place a piece on the main path, home path, or final home.
public class MoveCommand implements Command {

    // The command stores the board, the moving piece, the destination type, and any
    // path-specific details.
    private final Board board;
    private final Piece piece;
    private final TargetType targetType;
    private final int pathIndex;
    private final Colour homePathColour;
    private final Runnable afterExecute;
    // The constructor keeps the move details private so the factory methods control
    // how commands are created.
    private MoveCommand(Board board, Piece piece, TargetType targetType,
                        int pathIndex, Colour homePathColour, Runnable afterExecute) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
        this.board = board;
        this.piece = piece;
        this.targetType = targetType;
        this.pathIndex = pathIndex;
        this.homePathColour = homePathColour;
        this.afterExecute = afterExecute;
    }

    // These factory methods cover the common move cases used by the game.
    public static MoveCommand toStartingSquare(Board board, Piece piece) {
        return new MoveCommand(board, piece, TargetType.STARTING_SQUARE, -1, null, null);
    }

    // This variant places a piece on a specific standard-path square.
    public static MoveCommand toStandardPath(Board board, Piece piece, int index) {
        return toStandardPath(board, piece, index, null);
    }

    // This variant also runs an extra callback after the move is finished.
    public static MoveCommand toStandardPath(Board board,
                                             Piece piece,
                                             int index,
                                             Runnable afterExecute) {
        return new MoveCommand(board, piece, TargetType.STANDARD_PATH, index, null, afterExecute);
    }

    // This variant moves a piece onto the home path for the selected colour.
    public static MoveCommand toHomePath(Board board,
                                         Piece piece,
                                         Colour colour,
                                         int homePathIndex) {
        return toHomePath(board, piece, colour, homePathIndex, null);
    }

    // This variant also runs a callback after the piece reaches its home path.
    public static MoveCommand toHomePath(Board board,
                                         Piece piece,
                                         Colour colour,
                                         int homePathIndex,
                                         Runnable afterExecute) {
        if (colour == null)
            throw new IllegalArgumentException("Colour must not be null.");
        return new MoveCommand(board, piece, TargetType.HOME_PATH, homePathIndex, colour, afterExecute);
    }

    // This variant finishes the piece in its final home location.
    public static MoveCommand toHome(Board board, Piece piece) {
        return toHome(board, piece, null);
    }

    // This variant also runs a callback once the piece has reached home.
    public static MoveCommand toHome(Board board, Piece piece, Runnable afterExecute) {
        return new MoveCommand(board, piece, TargetType.HOME, -1, null, afterExecute);
    }

    // This exposes the piece that the move command will act on.
    public Piece getPiece() {
        return piece;
    }

    // These checks help other parts identify the kind of move this command
    // represents.
    public boolean isStartingSquareMove() {
        return targetType == TargetType.STARTING_SQUARE;
    }

    public boolean isStandardPathMove() {
        return targetType == TargetType.STANDARD_PATH;
    }

    // Execution chooses the correct board helper based on the recorded destination
    // type.
    @Override
    public void execute() {
        switch (targetType) {
            case STARTING_SQUARE -> board.movePieceFromBaseToStartingSquare(piece);
            case STANDARD_PATH -> board.placePieceOnStandardPath(piece, pathIndex);
            case HOME_PATH -> board.placePieceInHomePath(piece, homePathColour, pathIndex);
            case HOME -> {
                // The HOME case removes the piece from its current cell and marks it as
                // finished.
                board.removePieceFromCurrentLocation(piece);
                piece.clearAlphaMovementState();
                piece.setLocation(Location.home(piece.getColour()));
            }
        }

        if (afterExecute != null) {
            afterExecute.run();
        }
    }

    // The destination type tells the executor which board helper to use.
    private enum TargetType {
        STARTING_SQUARE,
        STANDARD_PATH,
        HOME_PATH,
        HOME
    }
}