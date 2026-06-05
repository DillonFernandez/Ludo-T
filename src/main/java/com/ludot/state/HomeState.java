package com.ludot.state;

import com.ludot.model.Location;
import com.ludot.model.Piece;

// Applies the finished-piece state once a token has reached home.
public class HomeState implements PieceState {

    // Uses the visible home-state label shown by the game.
    @Override
    public String getName() {
        return "Home";
    }

    // Finished pieces cannot move again.
    @Override
    public boolean canMove(Piece piece) {
        requirePiece(piece);
        return false;
    }

    // Home movement always resolves to zero.
    @Override
    public int adjustMovement(Piece piece, int diceValue) {
        requirePiece(piece);
        requireDiceValue(diceValue);
        return 0;
    }

    // Places the piece on its home destination and clears any active effect.
    @Override
    public void onEnter(Piece piece) {
        requirePiece(piece);
        piece.clearAlphaMovementState();
        piece.setLocation(Location.home(piece.getColour()));
    }

    // No further state change is needed once the piece is home.
    @Override
    public void onRoundPassed(Piece piece) {
        requirePiece(piece);
    }
}
