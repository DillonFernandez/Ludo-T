package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.model.Colour;
import com.ludot.model.Location;
import com.ludot.model.LocationType;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.random.CoinToss;
import com.ludot.random.Dice;
import com.ludot.rules.RuleEngine;

import java.util.List;
import java.util.Optional;

// Chooses Red's move order to prioritize captures and safe progress.
public class RedPlayer extends AbstractPlayer {

    // The constructor supplies Red with the shared game services it needs.
    public RedPlayer(Colour colour, List<Piece> pieces, Board board,
                     RuleEngine ruleEngine, Dice dice,
                     CoinToss coinToss, GameLogger logger) {
        super(colour, pieces, board, ruleEngine, dice, coinToss, logger);
    }

    // The move order prefers captures, then entry from base, then safe forward
    // movement.
    @Override
    protected Command selectMove(int diceValue) {
        Optional<CaptureCandidate> bestOpt = findBestCaptureCandidate(diceValue);
        if (bestOpt.isPresent()) {
            CaptureCandidate best = bestOpt.get();
            return tryStandardMove(best.piece, diceValue);
        }

        // This branch tries to enter the board from base only when the rules allow it.
        Command baseMove = tryMoveFromBase(diceValue);
        if (isCommandAvailable(baseMove)) {
            // If we already have pieces on the board and the dice is six,
            // do not take another piece from the base if any on-board piece
            // could capture by moving six. Otherwise allow entering from base.
            if (countPiecesOnBoard() > 0 && diceValue == 6) {
                boolean captureWithSixPossible = false;
                for (Piece piece : movablePiecesOnBoard()) {
                    if (piece.getLocation().getType() == LocationType.HOME_PATH)
                        continue;
                    Optional<Location> destOpt = safeCalculateDestination(piece, 6);
                    if (destOpt.isEmpty())
                        continue;
                    Location dest = destOpt.get();
                    if (isOpponentAt(dest)) {
                        captureWithSixPossible = true;
                        break;
                    }
                }
                if (!captureWithSixPossible) {
                    return baseMove;
                }
            } else if (countPiecesOnBoard() == 0) {
                return baseMove;
            }
        }

        // This avoids moves that would create an unnecessary same-colour block.
        Command firstBlocked = new NoMoveCommand();
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH)
                continue;
            Optional<Location> destOpt = safeCalculateDestination(piece, diceValue);
            if (destOpt.isEmpty())
                continue;
            Location dest = destOpt.get();
            boolean wouldBlock = wouldCreateBlockForMove(dest);
            Command move = tryStandardMove(piece, diceValue);
            if (!isCommandAvailable(move))
                continue;
            if (!wouldBlock) {
                return move;
            }
            if (firstBlocked instanceof NoMoveCommand) {
                firstBlocked = move;
            }
        }

        if (isCommandAvailable(firstBlocked))
            return firstBlocked;

        // Home-path moves are considered carefully so Red does not get stuck with no
        // active pieces.
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH) {
                if (ruleEngine.movementRule().hasExactHomePathMove(piece, diceValue)) {
                    Command move = tryHomePathMove(piece, diceValue);
                    if (!isCommandAvailable(move))
                        continue;
                    boolean isFinalHome = true; // this is a decided finishing move
                    if (isFinalHome && countPiecesOnBoard() == 1) {
                        continue;
                    }
                    return move;
                }
            }
        }

        // This fallback tries any remaining home-path move when no better option
        // exists.
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH) {
                Command move = tryHomePathMove(piece, diceValue);
                if (isCommandAvailable(move))
                    return move;
            }
        }

        return new NoMoveCommand();
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

    // This scans for the best capture opportunity, preferring safer captures.
    private Optional<CaptureCandidate> findBestCaptureCandidate(int diceValue) {
        CaptureCandidate best = null;
        for (Piece piece : movablePiecesOnBoard()) {
            if (piece.getLocation().getType() == LocationType.HOME_PATH)
                continue;
            if (piece.getDirection() == null)
                continue;

            Optional<Location> destinationOpt = safeCalculateDestination(piece, diceValue);
            if (destinationOpt.isEmpty())
                continue;

            Location destination = destinationOpt.get();

            if (!isOpponentAt(destination))
                continue;

            // find the opponent piece that would be captured
            Optional<Piece> targetOpt = findOpponentAt(destination);
            if (targetOpt.isEmpty())
                continue;

            Piece target = targetOpt.get();
            int targetDistance = distanceToHome(target);
            boolean wouldCreateBlock = wouldCreateBlockForMove(destination);

            CaptureCandidate candidate = new CaptureCandidate(piece, targetDistance, wouldCreateBlock);

            if (best == null) {
                best = candidate;
            } else {
                // prefer non-block-creating captures
                if (best.wouldCreateBlock && !candidate.wouldCreateBlock) {
                    best = candidate;
                } else if (best.wouldCreateBlock == candidate.wouldCreateBlock) {
                    // tie-break: opponent piece closest to its home
                    if (candidate.targetDistance < best.targetDistance) {
                        best = candidate;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // This measures how far a piece is from its home approach area.
    private int distanceToHome(Piece piece) {
        if (piece == null || piece.getLocation() == null)
            return Integer.MAX_VALUE;
        int approachIndex = board.getPath().getApproachIndex(piece.getColour());
        int currentIndex = piece.getLocation().getIndex();
        int size = com.ludot.config.GameConfig.STANDARD_PATH_SIZE;
        if (piece.getDirection() == com.ludot.model.Direction.CLOCKWISE) {
            return (approachIndex - currentIndex + size) % size;
        }
        return (currentIndex - approachIndex + size) % size;
    }

    // This marks moves that would create an unwanted same-colour block.
    private boolean wouldCreateBlockForMove(Location destination) {
        if (destination == null)
            return false;
        if (!destination.isOnStandardRing())
            return false;
        var cell = board.getStandardCell(destination.getIndex());
        if (!cell.isOccupied())
            return false;
        for (Piece occupant : cell.getPieces()) {
            if (occupant.getColour() == this.colour)
                return true;
        }
        return false;
    }

    // This checks whether a destination contains an opponent piece to capture.
    private boolean isOpponentAt(Location destination) {
        if (destination == null)
            return false;
        if (!destination.isOnStandardRing())
            return false;
        var cell = board.getStandardCell(destination.getIndex());
        if (!cell.isOccupied())
            return false;
        if (cell.isBlocked())
            return false;
        for (Piece occupant : cell.getPieces()) {
            if (occupant.getColour() != this.colour)
                return true;
        }
        return false;
    }

    // This returns the first opponent occupant found at a destination.
    private Optional<Piece> findOpponentAt(Location destination) {
        if (destination == null)
            return Optional.empty();
        boolean isStandardPathType = destination.isStandardPath() || destination.isStartingSquare()
                || destination.isApproach() || destination.isAlpha() || destination.isBeta()
                || destination.isGamma();
        if (!isStandardPathType)
            return Optional.empty();
        var cell = board.getStandardCell(destination.getIndex());
        if (cell.isBlocked())
            return Optional.empty();
        for (Piece occupant : cell.getPieces()) {
            if (occupant.getColour() != this.colour)
                return Optional.of(occupant);
        }
        return Optional.empty();
    }

    // This small record holds the capture choice details used for comparison.
    private record CaptureCandidate(Piece piece, int targetDistance, boolean wouldCreateBlock) {
    }
}