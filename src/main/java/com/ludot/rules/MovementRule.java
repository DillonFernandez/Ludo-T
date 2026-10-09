package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.command.BaseToStartingSquareCommand;
import com.ludot.command.Command;
import com.ludot.command.MoveCommand;
import com.ludot.command.NoMoveCommand;
import com.ludot.config.GameConfig;
import com.ludot.model.Direction;
import com.ludot.model.Location;
import com.ludot.model.LocationType;
import com.ludot.model.Piece;
import com.ludot.random.CoinToss;
import com.ludot.state.PieceState;

// Applies the movement rules for leaving base, moving on the ring, and entering home.
public class MovementRule {

    // Board used to resolve movement and destination cells.
    private final Board board;

    public MovementRule(Board board) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        this.board = board;
    }

    // Guards against invalid piece or step values before movement logic runs.
    private static void requirePiece(Piece piece) {
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
    }

    private static void requireSteps(int steps) {
        if (steps < 1)
            throw new IllegalArgumentException(
                    "Steps/dice value must be at least 1, got " + steps);
    }

    // Returns true when this piece is allowed to leave base on this dice value.
    public boolean canMoveFromBase(Piece piece, int diceValue) {
        if (piece == null)
            return false;
        requireSteps(diceValue);
        return piece.isInBase() && diceValue == GameConfig.ROLL_TO_ENTER;
    }

    // Creates the base-exit move and reuses the coin toss for the follow-up result.
    public Command commandFromBaseIfAllowed(Piece piece, int diceValue, CoinToss coinToss) {
        if (piece == null)
            return new NoMoveCommand();
        if (coinToss == null)
            throw new IllegalArgumentException("CoinToss must not be null.");
        requireSteps(diceValue);
        if (canMoveFromBase(piece, diceValue)) {
            return new BaseToStartingSquareCommand(board, piece, coinToss);
        }
        return new NoMoveCommand();
    }

    // Computes the destination cell for a standard-path move.
    public Location calculateStandardDestination(Piece piece, int steps) {
        requirePiece(piece);
        int adjustedSteps = adjustMovementForPiece(piece, steps);
        requireSteps(adjustedSteps);
        if (!piece.getLocation().isOnStandardRing()) {
            throw new IllegalArgumentException(
                    "calculateStandardDestination called for a piece not on the standard path: "
                            + piece.getLocation());
        }
        if (piece.getDirection() == null) {
            throw new IllegalArgumentException("Piece direction must not be null.");
        }
        int currentIndex = piece.getLocation().getIndex();
        int targetIndex;
        if (piece.getDirection() == Direction.CLOCKWISE) {
            targetIndex = board.getPath().moveClockwise(currentIndex, adjustedSteps);
        } else {
            targetIndex = board.getPath().moveCounterClockwise(currentIndex, adjustedSteps);
        }
        return board.getStandardCell(targetIndex).getLocation();
    }

    // Builds the move command for a standard-path piece, including home entry when
    // needed.
    public Command commandForStandardMove(Piece piece, int steps) {
        requirePiece(piece);
        if (!piece.getLocation().isOnStandardRing())
            return new NoMoveCommand();
        if (piece.getDirection() == null)
            return new NoMoveCommand();

        int adjustedSteps = adjustMovementForPiece(piece, steps);
        requireSteps(adjustedSteps);

        boolean passesApproachCell = passesOwnApproachCell(piece, adjustedSteps);

        int counterClockwiseApproachPassCount = piece.getCounterClockwiseApproachPassCount();
        if (piece.getDirection() == Direction.COUNTER_CLOCKWISE && passesApproachCell) {
            counterClockwiseApproachPassCount++;
        }

        Runnable afterExecute = null;
        if (piece.getDirection() == Direction.COUNTER_CLOCKWISE && passesApproachCell) {
            afterExecute = piece::incrementCounterClockwiseApproachPassCount;
        }

        // Redirect to home path when piece passes through its approach cell
        if (shouldEnterHomePath(piece, steps, counterClockwiseApproachPassCount)) {
            int homeIndex = calculateHomePathEntryIndex(piece, steps);
            if (homeIndex < 0)
                return new NoMoveCommand();
            if (homeIndex == GameConfig.HOME_PATH_SIZE)
                return MoveCommand.toHome(board, piece, afterExecute);
            if (homeIndex > GameConfig.HOME_PATH_SIZE)
                return new NoMoveCommand();
            return MoveCommand.toHomePath(board, piece, piece.getColour(), homeIndex, afterExecute);
        }

        // Normal standard path movement
        int currentIndex = piece.getLocation().getIndex();
        int targetIndex;
        if (piece.getDirection() == Direction.CLOCKWISE) {
            targetIndex = board.getPath().moveClockwise(currentIndex, adjustedSteps);
        } else {
            targetIndex = board.getPath().moveCounterClockwise(currentIndex, adjustedSteps);
        }
        return MoveCommand.toStandardPath(board, piece, targetIndex, afterExecute);
    }

    // Allows home-path entry only after the piece has already captured at least one
    // opponent.
    public boolean shouldEnterHomePath(Piece piece, int steps) {
        return shouldEnterHomePath(piece, steps, piece.getCounterClockwiseApproachPassCount());
    }

    private boolean shouldEnterHomePath(Piece piece, int steps, int counterClockwiseApproachPassCount) {
        requirePiece(piece);
        if (!piece.getLocation().isOnStandardRing())
            return false;
        if (!canEnterHomePath(piece))
            return false;

        int adjustedSteps = adjustMovementForPiece(piece, steps);
        requireSteps(adjustedSteps);

        int approachIndex = board.getPath().getApproachIndex(piece.getColour());
        int currentIndex = piece.getLocation().getIndex();
        int size = GameConfig.STANDARD_PATH_SIZE;
        int stepsToApproach;
        if (piece.getDirection() == Direction.CLOCKWISE) {
            stepsToApproach = (approachIndex - currentIndex + size) % size;
            // Move passes through the approach cell when steps > stepsToApproach
            return adjustedSteps > stepsToApproach;
        } else {
            // Counter-clockwise movement: home entry only after the approach cell
            // has been passed twice.
            stepsToApproach = (currentIndex - approachIndex + size) % size;
            return adjustedSteps > stepsToApproach && counterClockwiseApproachPassCount >= 2;
        }
    }

    // Calculates the home-path index the piece would reach after this move.
    public int calculateHomePathEntryIndex(Piece piece, int steps) {
        int approachIndex = board.getPath().getApproachIndex(piece.getColour());
        int currentIndex = piece.getLocation().getIndex();
        int size = GameConfig.STANDARD_PATH_SIZE;
        int stepsToApproach;
        if (piece.getDirection() == Direction.CLOCKWISE) {
            stepsToApproach = (approachIndex - currentIndex + size) % size;
        } else {
            stepsToApproach = (currentIndex - approachIndex + size) % size;
        }
        int remainingAfterApproach = adjustMovementForPiece(piece, steps) - stepsToApproach;
        // 1 step past approach = home path index 0
        return remainingAfterApproach - 1;
    }

    // Enforces the capture-before-home rule for this piece.
    public boolean canEnterHomePath(Piece piece) {
        requirePiece(piece);
        return piece.getCaptureCount() > 0;
    }

    // Returns true when this move reaches the final home square exactly.
    public boolean hasExactHomePathMove(Piece piece, int steps) {
        requirePiece(piece);
        if (piece.getLocation().getType() != LocationType.HOME_PATH)
            return false;
        int adjustedSteps = adjustMovementForPiece(piece, steps);
        requireSteps(adjustedSteps);
        int currentHomeIndex = piece.getLocation().getIndex();
        int remaining = GameConfig.HOME_PATH_SIZE - currentHomeIndex;
        return adjustedSteps == remaining;
    }

    // Builds the home-path move command and blocks any overshoot.
    public Command commandForHomePathMove(Piece piece, int steps) {
        requirePiece(piece);
        if (piece.getLocation().getType() != LocationType.HOME_PATH)
            return new NoMoveCommand();

        int adjustedSteps = adjustMovementForPiece(piece, steps);
        requireSteps(adjustedSteps);

        // Exact home reach
        if (hasExactHomePathMove(piece, steps)) {
            return MoveCommand.toHome(board, piece);
        }

        int currentHomeIndex = piece.getLocation().getIndex();
        int newIndex = currentHomeIndex + adjustedSteps;

        // Overshoot
        if (newIndex >= GameConfig.HOME_PATH_SIZE)
            return new NoMoveCommand();

        // Move forward within home path
        return MoveCommand.toHomePath(board, piece, piece.getColour(), newIndex);
    }

    // Applies any movement modifier from the piece's current effect state.
    public int adjustMovementForPiece(Piece piece, int diceValue) {
        requirePiece(piece);
        requireSteps(diceValue);

        if (piece.isInBase() || piece.isHome())
            return diceValue;

        PieceState alphaMovementState = piece.getAlphaMovementState();
        if (alphaMovementState == null || !piece.hasAlphaEffect())
            return diceValue;

        return alphaMovementState.adjustMovement(piece, diceValue);
    }

    // Detects when the move passes the piece's approach cell on the standard ring.
    private boolean passesOwnApproachCell(Piece piece, int steps) {
        if (!piece.getLocation().isOnStandardRing())
            return false;

        int approachIndex = board.getPath().getApproachIndex(piece.getColour());
        int currentIndex = piece.getLocation().getIndex();
        int size = GameConfig.STANDARD_PATH_SIZE;
        if (piece.getDirection() == Direction.CLOCKWISE) {
            int stepsToApproach = (approachIndex - currentIndex + size) % size;
            return steps > stepsToApproach;
        }

        int stepsToApproach = (currentIndex - approachIndex + size) % size;
        return steps > stepsToApproach;
    }
}