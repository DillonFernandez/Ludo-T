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

// Chooses Green's moves to build or exploit same-colour blocks.
public class GreenPlayer extends AbstractPlayer {

    // The constructor supplies Green with the shared game services it needs.
    public GreenPlayer(Colour colour, List<Piece> pieces, Board board,
                       RuleEngine ruleEngine, Dice dice,
                       CoinToss coinToss, GameLogger logger) {
        super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
    }

    // The move order prefers block-building, then captures, then general movement.
    @Override
    protected Command selectMove(int diceValue) {
        Command blockMove = findBlockBuildingMove(diceValue);
        if (diceValue == GameConfig.BONUS_ROLL_VALUE) {
            if (isCommandAvailable(blockMove)) {
                return blockMove;
            }

            Command baseMove = tryMoveFromBase(diceValue);
            if (isCommandAvailable(baseMove)) {
                return baseMove;
            }
        }

        Command homePathMove = selectHomePathMove(diceValue);
        if (isCommandAvailable(homePathMove)) {
            return homePathMove;
        }

        Command captureMove = selectCaptureMoveForPiecesNeedingCaptures(diceValue);
        if (isCommandAvailable(captureMove)) {
            return captureMove;
        }

        if (isCommandAvailable(blockMove)) {
            return blockMove;
        }

        Command standardMove = selectNonBlockStandardMove(diceValue);
        if (isCommandAvailable(standardMove)) {
            return standardMove;
        }

        Command blockBreakMove = selectBlockMove(diceValue);
        if (isCommandAvailable(blockBreakMove)) {
            return blockBreakMove;
        }

        if (diceValue != GameConfig.BONUS_ROLL_VALUE) {
            Command baseMove = tryMoveFromBase(diceValue);
            if (isCommandAvailable(baseMove)) {
                return baseMove;
            }
        }

        return new NoMoveCommand();
    }

    // This looks for the first capture opportunity for a piece that has not
    // captured yet.
    private Command selectCaptureMoveForPiecesNeedingCaptures(int diceValue) {
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getCaptureCount() != 0) {
                continue;
            }
            if (piece.getLocation().getType() == LocationType.HOME_PATH) {
                continue;
            }
            if (piece.getDirection() == null) {
                continue;
            }

            Location destination;
            try {
                destination = ruleEngine.movementRule().calculateStandardDestination(piece, diceValue);
            } catch (IllegalArgumentException e) {
                String msg = "Could not calculate destination for piece " + piece
                        + ": " + e.getMessage();
                logger.log(msg);
                continue;
            }

            Cell destinationCell = board.getStandardCell(destination.getIndex());
            if (!destinationCell.isOccupied() || destinationCell.isBlocked()) {
                continue;
            }

            boolean hasOpponentPiece = false;
            for (Piece occupant : destinationCell.getPieces()) {
                if (occupant.getColour() != colour) {
                    hasOpponentPiece = true;
                    break;
                }
            }
            if (!hasOpponentPiece) {
                continue;
            }

            Command move = tryStandardMove(piece, diceValue);
            if (isCommandAvailable(move)) {
                return move;
            }
        }

        return new NoMoveCommand();
    }

    // Home-path moves are preferred when they can complete a piece's journey.
    private Command selectHomePathMove(int diceValue) {
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() != LocationType.HOME_PATH) {
                continue;
            }
            if (ruleEngine.movementRule().hasExactHomePathMove(piece, diceValue)) {
                Command move = tryHomePathMove(piece, diceValue);
                if (isCommandAvailable(move)) {
                    return move;
                }
            }
        }

        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() != LocationType.HOME_PATH) {
                continue;
            }
            Command move = tryHomePathMove(piece, diceValue);
            if (isCommandAvailable(move)) {
                return move;
            }
        }

        return new NoMoveCommand();
    }

    // This avoids moves that depend on an existing same-colour block.
    private Command selectNonBlockStandardMove(int diceValue) {
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH) {
                continue;
            }
            if (ruleEngine.blockRule().isBlockAt(piece.getLocation())) {
                continue;
            }

            Command move = tryStandardMove(piece, diceValue);
            if (isCommandAvailable(move)) {
                return move;
            }
        }

        return new NoMoveCommand();
    }

    // These fallback moves try to build or break blocks when other options are
    // unavailable.
    private Command selectBlockMove(int diceValue) {
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH) {
                continue;
            }
            if (ruleEngine.blockRule().isBlockAt(piece.getLocation())) {
                continue;
            }

            Command move = tryStandardMove(piece, diceValue);
            if (isCommandAvailable(move)) {
                return move;
            }
        }

        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH) {
                continue;
            }
            if (!ruleEngine.blockRule().isBlockAt(piece.getLocation())) {
                continue;
            }

            Command move = tryStandardMove(piece, diceValue);
            if (isCommandAvailable(move)) {
                return move;
            }
        }

        return new NoMoveCommand();
    }

    // This targets destinations that strengthen an existing same-colour group.
    private Command findBlockBuildingMove(int diceValue) {
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH)
                continue;
            if (piece.getDirection() == null)
                continue;
            if (ruleEngine.isBlocked(piece, diceValue))
                continue;

            Optional<Location> destinationOpt = safeCalculateDestination(piece, diceValue);
            if (destinationOpt.isEmpty())
                continue;

            Location destination = destinationOpt.get();
            if (wouldCreateOrStrengthenBlock(destination)) {
                return tryStandardMove(piece, diceValue);
            }
        }
        return new NoMoveCommand();
    }

    // A destination is useful when it already contains one of Green's own pieces.
    private boolean wouldCreateOrStrengthenBlock(Location destination) {
        if (!destination.isOnStandardRing())
            return false;
        Cell cell = board.getStandardCell(destination.getIndex());
        int sameColourCount = 0;
        for (Piece occupant : cell.getPieces()) {
            if (occupant.getColour() == colour)
                sameColourCount++;
        }
        return sameColourCount >= 1;
    }

    // This helper safely returns an empty result when the move destination is
    // invalid.
    private Optional<Location> safeCalculateDestination(Piece piece, int diceValue) {
        try {
            return Optional.of(ruleEngine.movementRule().calculateStandardDestination(piece, diceValue));
        } catch (IllegalArgumentException e) {
            String msg = "Invalid destination for piece " + piece
                    + " with roll " + diceValue + ": " + e.getMessage();
            logger.log(msg);
            return Optional.empty();
        }
    }

}