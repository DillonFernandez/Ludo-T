package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.config.GameConfig;
import com.ludot.model.*;
import com.ludot.output.GameLogger;
import com.ludot.random.CoinToss;
import com.ludot.random.Dice;
import com.ludot.rules.RuleEngine;

import java.util.List;
import java.util.Optional;

// Chooses Yellow's move by prioritizing entry, captures, and the closest finish path.
public class YellowPlayer extends AbstractPlayer {

    // The constructor supplies Yellow with the shared game services it needs.
    public YellowPlayer(Colour colour, List<Piece> pieces, Board board,
                        RuleEngine ruleEngine, Dice dice,
                        CoinToss coinToss, GameLogger logger) {
        super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
    }

    @Override
    protected Command selectMove(int diceValue) {
        // The move order prefers base entry on a six, then captures, then the closest
        // finish move.
        if (diceValue == GameConfig.BONUS_ROLL_VALUE && hasPiecesInBase()) {
            Command baseMove = tryMoveFromBase(diceValue);
            if (isCommandAvailable(baseMove)) {
                return baseMove;
            }
        }

        Command captureMove = selectCaptureMoveForPiecesNeedingCaptures(diceValue);
        if (isCommandAvailable(captureMove)) {
            return captureMove;
        }

        Command closestMove = selectClosestMove(diceValue);
        if (isCommandAvailable(closestMove)) {
            return closestMove;
        }

        return new NoMoveCommand();
    }

    private Command selectCaptureMoveForPiecesNeedingCaptures(int diceValue) {
        // This searches for a capture that also advances a piece toward home.
        Command bestMove = new NoMoveCommand();
        int bestScore = Integer.MAX_VALUE;

        for (Piece piece : movablePiecesOnBoard()) {
            if (!isEligibleForCaptureConsideration(piece)) {
                continue;
            }

            Optional<Location> destinationOpt = safeCalculateDestination(piece, diceValue);
            if (destinationOpt.isEmpty()) {
                continue;
            }
            Location destination = destinationOpt.get();
            if (!wouldCaptureAtDestination(piece, destination)) {
                continue;
            }

            Command move = tryStandardMove(piece, diceValue);
            if (!isCommandAvailable(move)) {
                continue;
            }

            int score = distanceToHome(piece);
            if (score < bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    // A piece is eligible only if it can move and has not yet taken a capture.
    private boolean isEligibleForCaptureConsideration(Piece piece) {
        if (piece == null)
            return false;
        if (piece.getCaptureCount() >= 1)
            return false;
        if (piece.getLocation().getType() == LocationType.HOME_PATH)
            return false;
        return piece.getDirection() != null;
    }

    private Command selectClosestMove(int diceValue) {
        // This picks the most advanced legal move when no capture is available.
        Command bestMove = new NoMoveCommand();
        int bestScore = Integer.MAX_VALUE;

        for (Piece piece : movablePiecesOnBoard()) {
            Command move = moveForPiece(piece, diceValue);

            if (!isCommandAvailable(move)) {
                continue;
            }

            int score = distanceToHome(piece);
            if (score < bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    // A destination counts as a capture if it contains an opponent piece.
    private boolean wouldCaptureAtDestination(Piece piece, Location destination) {
        if (piece == null || destination == null) {
            return false;
        }
        if (!destination.isOnStandardRing()) {
            return false;
        }

        Cell cell = board.getStandardCell(destination.getIndex());
        if (!cell.isOccupied() || cell.isBlocked()) {
            return false;
        }

        for (Piece occupant : cell.getPieces()) {
            if (occupant.getColour() != colour) {
                return true;
            }
        }
        return false;
    }

    // This helper safely returns the destination when the move is legal.
    private Optional<Location> safeCalculateDestination(Piece piece, int diceValue) {
        try {
            return Optional.of(
                    ruleEngine.movementRule()
                            .calculateStandardDestination(piece, diceValue));
        } catch (IllegalArgumentException e) {
            String msg = "Could not calculate destination for piece " + piece
                    + " with roll " + diceValue + ": " + e.getMessage();
            logger.log(msg);
            return Optional.empty();
        }
    }

    // This score estimates how far each piece is from finishing.
    private int distanceToHome(Piece piece) {
        if (piece == null || piece.getLocation() == null) {
            return Integer.MAX_VALUE;
        }
        if (piece.getLocation().getType() == LocationType.HOME_PATH) {
            return GameConfig.HOME_PATH_SIZE - piece.getLocation().getIndex();
        }
        if (!piece.getLocation().isOnStandardRing() || piece.getDirection() == null) {
            return Integer.MAX_VALUE;
        }

        int approachIndex = board.getPath().getApproachIndex(piece.getColour());
        int currentIndex = piece.getLocation().getIndex();
        int size = GameConfig.STANDARD_PATH_SIZE;
        int stepsToApproach;
        if (piece.getDirection() == Direction.CLOCKWISE) {
            stepsToApproach = (approachIndex - currentIndex + size) % size;
        } else {
            stepsToApproach = (currentIndex - approachIndex + size) % size;
        }
        return stepsToApproach + GameConfig.HOME_PATH_SIZE;
    }

    // The move type depends on whether the piece is on the home path or on the main
    // ring.
    private Command moveForPiece(Piece piece, int diceValue) {
        if (piece.getLocation().getType() == LocationType.HOME_PATH) {
            return tryHomePathMove(piece, diceValue);
        }
        return tryStandardMove(piece, diceValue);
    }

}