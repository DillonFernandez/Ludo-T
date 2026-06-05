package com.ludot.command;

import com.ludot.board.Board;
import com.ludot.model.Location;
import com.ludot.model.MysteryEffect;
import com.ludot.model.Piece;

// Executes a mystery-effect teleport to a named board location.
public class TeleportCommand implements Command {

    // The command depends on the board, the moving piece, and the mystery effect
    // that chooses the destination.
    private final Board board;
    private final Piece piece;
    private final MysteryEffect effect;

    // The constructor validates the inputs and stores them for later execution.
    public TeleportCommand(Board board, Piece piece, MysteryEffect effect) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
        if (effect == null)
            throw new IllegalArgumentException("MysteryEffect must not be null.");
        this.board = board;
        this.piece = piece;
        this.effect = effect;
    }

    // Execution switches on the mystery effect and moves the piece to the matching
    // board location.
    // An unknown effect is treated as a programming error.
    @Override
    public void execute() {
        switch (effect) {
            case ALPHA -> board.teleportPieceToAlpha(piece);

            case BETA -> board.teleportPieceToBeta(piece);

            case GAMMA -> board.teleportPieceToGamma(piece);

            case BASE -> board.returnPieceToBase(piece);

            case STARTING_SQUARE -> teleportToStartingSquare();

            case APPROACH -> teleportToApproach();
            default -> throw new IllegalStateException("Unknown MysteryEffect: " + effect);
        }
    }

    // These helpers place the piece on the entry or approach square, depending on
    // the effect.
    private void teleportToStartingSquare() {
        int startIndex = board.getPath().getStartingIndex(piece.getColour());
        if (piece.isInBase()) {
            board.movePieceFromBaseToStartingSquare(piece);
        } else {
            board.removePieceFromCurrentLocation(piece);
            board.getStandardCell(startIndex).addPiece(piece);
            piece.setLocation(Location.startingSquare(piece.getColour(), startIndex));
        }
    }

    private void teleportToApproach() {
        int approachIndex = board.getPath().getApproachIndex(piece.getColour());
        board.removePieceFromCurrentLocation(piece);
        board.getStandardCell(approachIndex).addPiece(piece);
        piece.setLocation(Location.approach(piece.getColour(), approachIndex));
    }

    // These accessors expose the piece and effect used by the teleport.
    public Piece getPiece() {
        return piece;
    }

    public MysteryEffect getEffect() {
        return effect;
    }
}