package com.ludot.command;

import com.ludot.board.Board;
import com.ludot.model.Piece;
import com.ludot.random.CoinToss;

// Moves one piece from its home area onto the main track and chooses its starting direction.
public class BaseToStartingSquareCommand implements Command {

    // The command depends on the board, the selected piece, and the coin toss that
    // sets its direction.
    private final Board board;
    private final Piece piece;
    private final CoinToss coinToss;

    // This action is created with the specific board state and piece that will be
    // moved.
    public BaseToStartingSquareCommand(Board board, Piece piece, CoinToss coinToss) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
        if (coinToss == null)
            throw new IllegalArgumentException("CoinToss must not be null.");
        this.board = board;
        this.piece = piece;
        this.coinToss = coinToss;
    }

    // The move updates the board first, then the coin toss determines the piece's
    // facing.
    @Override
    public void execute() {
        board.movePieceFromBaseToStartingSquare(piece);
        piece.setDirection(coinToss.toss().toDirection());
    }

    // This exposes the piece that this command is responsible for moving.
    public Piece getPiece() {
        return piece;
    }

    // This flag identifies this command as a starting-square move.
    public boolean isStartingSquareMove() {
        return true;
    }
}