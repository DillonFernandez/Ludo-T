package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.command.BlockMoveCommand;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.config.GameConfig;
import com.ludot.model.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Applies the block rules used when several pieces share one standard-path cell.
public class BlockRule {

    // Board used to inspect occupied standard-path cells.
    private final Board board;

    // Requires a real board because every block check depends on it.
    public BlockRule(Board board) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        this.board = board;
    }

    private static void requirePiece(Piece piece) {
        if (piece == null)
            throw new IllegalArgumentException("Piece must not be null.");
    }

    private static void requireSteps(int steps) {
        if (steps < 1)
            throw new IllegalArgumentException(
                    "Steps must be at least 1, got " + steps);
    }

    // Checks whether a standard-path cell currently behaves like a block.
    public boolean isBlockAt(Location location) {
        if (location == null)
            return false;
        if (!location.isOnStandardRing())
            return false;
        Cell cell = board.getStandardCell(location.getIndex());
        return cell.isBlocked();
    }

    // Returns true when the player has at least one block on the standard path.
    public boolean hasBlockade(List<Piece> pieces) {
        return findFirstBlockadeLocation(pieces) != null;
    }

    // Returns the pieces currently sharing a blocked standard-path cell.
    public List<Piece> getBlockadePieces(Location location) {
        if (location == null)
            return List.of();
        if (!location.isOnStandardRing())
            return List.of();
        Cell cell = board.getStandardCell(location.getIndex());
        if (!cell.isBlocked())
            return List.of();
        return new ArrayList<>(cell.getPieces());
    }

    // Finds the earliest blocked cell for this player's pieces on the standard
    // path.
    public Location findFirstBlockadeLocation(List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty())
            return null;

        Location firstBlockade = null;
        Set<Integer> seenIndexes = new HashSet<>();

        for (Piece piece : pieces) {
            if (piece == null || piece.isInBase() || piece.isHome())
                continue;
            Location location = piece.getLocation();
            if (!location.isOnStandardRing())
                continue;
            int index = location.getIndex();
            if (!seenIndexes.add(index))
                continue;
            if (isBlockAt(location) && (firstBlockade == null || index < firstBlockade.getIndex())) {
                firstBlockade = location;
            }
        }

        return firstBlockade;
    }

    // Returns true when the blocked pieces disagree on the travel direction.
    public boolean hasOppositeDirections(Piece piece) {
        Cell cell = getStandardBlockCell(piece);
        if (cell == null)
            return false;
        boolean[] flags = getDirectionFlags(cell);
        return flags[0] && flags[1];
    }

    // Chooses the direction that keeps the block moving toward home.
    public Direction chooseBlockDirection(Piece piece) {
        Cell cell = getStandardBlockCell(piece);
        if (cell == null)
            return null;
        boolean[] flags = getDirectionFlags(cell);
        boolean hasClockwise = flags[0];
        boolean hasCounterClockwise = flags[1];

        if (hasClockwise && !hasCounterClockwise)
            return Direction.CLOCKWISE;
        if (hasCounterClockwise && !hasClockwise)
            return Direction.COUNTER_CLOCKWISE;
        if (!hasClockwise && !hasCounterClockwise)
            return null;

        int clockwiseDistance = distanceToHome(piece.getColour(), piece.getLocation().getIndex(),
                Direction.CLOCKWISE);
        int counterClockwiseDistance = distanceToHome(piece.getColour(), piece.getLocation().getIndex(),
                Direction.COUNTER_CLOCKWISE);
        return clockwiseDistance >= counterClockwiseDistance
                ? Direction.CLOCKWISE
                : Direction.COUNTER_CLOCKWISE;
    }

    // Shares the dice value across the block size to compute the movement distance.
    public int calculateBlockMovementDistance(int diceValue, int blockSize) {
        requireSteps(diceValue);
        if (blockSize < 1)
            throw new IllegalArgumentException("Block size must be at least 1, got " + blockSize);
        return diceValue / blockSize;
    }

    // Creates the move command only when the block can legally advance.
    public Command commandForBlockMove(Piece piece, int diceValue) {
        if (piece == null)
            return new NoMoveCommand();
        if (piece.getLocation() == null || !piece.getLocation().isStandardPath())
            return new NoMoveCommand();
        if (piece.getDirection() == null)
            return new NoMoveCommand();

        Cell cell = board.getStandardCell(piece.getLocation().getIndex());
        if (!cell.isBlocked())
            return new NoMoveCommand();

        int movementDistance = calculateBlockMovementDistance(diceValue, cell.getPieces().size());
        if (movementDistance < 1)
            return new NoMoveCommand();

        Direction blockDirection = chooseBlockDirection(piece);
        if (blockDirection == null)
            return new NoMoveCommand();

        if (wouldEnterHomePath(piece, blockDirection, movementDistance))
            return new NoMoveCommand();

        Location blockingLocation = movementDistance > 1
                ? findBlockingLocation(piece.getLocation(), piece.getColour(), blockDirection, movementDistance - 1)
                : null;

        Location destination;
        if (blockingLocation == null) {
            destination = locationAfterMove(piece.getLocation(), blockDirection, movementDistance);
        } else {
            destination = findCellBeforeBlock(piece.getLocation(), piece.getColour(), blockDirection,
                    movementDistance - 1);
        }

        if (destination == null || destination.equals(piece.getLocation()))
            return new NoMoveCommand();

        List<Piece> movingPieces = new ArrayList<>(cell.getPieces());
        return new BlockMoveCommand(board, movingPieces, destination);
    }

    // Checks whether an enemy block blocks the path of this piece.
    public boolean isBlockedOnPath(Piece piece, int steps) {
        requirePiece(piece);
        requireSteps(steps);
        if (!piece.getLocation().isOnStandardRing())
            return false;
        if (piece.getDirection() == null)
            return false;

        return isBlockedOnPath(piece.getLocation(), piece.getColour(), piece.getDirection(), steps);
    }

    public boolean isBlockedOnPath(Location location, Colour colour, Direction direction, int steps) {
        return findBlockingLocation(location, colour, direction, steps) != null;
    }

    // Finds the first enemy block ahead of this piece along its direction.
    public Location findBlockingLocation(Piece piece, int steps) {
        requirePiece(piece);
        requireSteps(steps);
        if (!piece.getLocation().isOnStandardRing())
            return null;
        if (piece.getDirection() == null)
            return null;

        return findBlockingLocation(piece.getLocation(), piece.getColour(), piece.getDirection(), steps);
    }

    public Location findBlockingLocation(Location location, Colour colour, Direction direction, int steps) {
        if (location == null || colour == null || direction == null)
            return null;
        if (!location.isOnStandardRing())
            return null;

        int currentIndex = location.getIndex();

        for (int step = 1; step <= steps; step++) {
            int checkIndex = nextIndex(currentIndex, step, direction);
            Cell cell = board.getStandardCell(checkIndex);

            if (cell.isBlocked()) {
                Piece blocker = cell.getPieces().get(0);
                if (blocker.getColour() != colour)
                    return cell.getLocation();
            }
        }
        return null;
    }

    // Helper methods used by the block-path calculations.

    // Finds the last safe cell before an enemy block on the path.
    public Location findCellBeforeBlock(Piece piece, int steps) {
        requirePiece(piece);
        requireSteps(steps);
        if (!piece.getLocation().isOnStandardRing())
            return null;
        if (piece.getDirection() == null)
            return null;

        return findCellBeforeBlock(piece.getLocation(), piece.getColour(), piece.getDirection(), steps);
    }

    public Location findCellBeforeBlock(Location location, Colour colour, Direction direction, int steps) {
        if (location == null || colour == null || direction == null)
            return null;
        if (!location.isOnStandardRing())
            return null;

        int currentIndex = location.getIndex();

        for (int step = 1; step <= steps; step++) {
            int checkIndex = nextIndex(currentIndex, step, direction);
            Cell cell = board.getStandardCell(checkIndex);

            if (cell.isBlocked()) {
                Piece blocker = cell.getPieces().get(0);
                if (blocker.getColour() != colour) {
                    if (step == 1) {
                        return location;
                    }
                    int beforeIndex = nextIndex(currentIndex, step - 1, direction);
                    return board.getStandardCell(beforeIndex).getLocation();
                }
            }
        }
        return null;
    }

    // Moves one step count around the standard ring in the chosen direction.
    private int nextIndex(int startIndex, int steps, Direction direction) {
        if (direction == Direction.CLOCKWISE) {
            return board.getPath().moveClockwise(startIndex, steps);
        }
        return board.getPath().moveCounterClockwise(startIndex, steps);
    }

    // Returns the destination cell after the requested number of steps.
    private Location locationAfterMove(Location location, Direction direction, int steps) {
        int targetIndex = nextIndex(location.getIndex(), steps, direction);
        return board.getStandardCell(targetIndex).getLocation();
    }

    // Detects whether the move would cross into the home approach area.
    private boolean wouldEnterHomePath(Piece piece, Direction direction, int steps) {
        int approachIndex = board.getPath().getApproachIndex(piece.getColour());
        int currentIndex = piece.getLocation().getIndex();
        int size = GameConfig.STANDARD_PATH_SIZE;
        int stepsToApproach;
        if (direction == Direction.CLOCKWISE) {
            stepsToApproach = (approachIndex - currentIndex + size) % size;
        } else {
            stepsToApproach = (currentIndex - approachIndex + size) % size;
        }
        return steps > stepsToApproach;
    }

    // Measures the distance from the current cell to the home approach.
    private int distanceToHome(Colour colour, int currentIndex, Direction direction) {
        int approachIndex = board.getPath().getApproachIndex(colour);
        int size = GameConfig.STANDARD_PATH_SIZE;
        if (direction == Direction.CLOCKWISE) {
            return (approachIndex - currentIndex + size) % size;
        }
        return (currentIndex - approachIndex + size) % size;
    }

    // Returns the blocked cell only when the piece stands on one.
    private Cell getStandardBlockCell(Piece piece) {
        if (piece == null || piece.getLocation() == null)
            return null;
        if (!piece.getLocation().isStandardPath())
            return null;
        Cell cell = board.getStandardCell(piece.getLocation().getIndex());
        return cell.isBlocked() ? cell : null;
    }

    // Records whether the blocked pieces prefer clockwise or counter-clockwise
    // movement.
    private boolean[] getDirectionFlags(Cell cell) {
        boolean hasClockwise = false;
        boolean hasCounterClockwise = false;
        for (Piece occupant : cell.getPieces()) {
            if (occupant.getDirection() == Direction.CLOCKWISE) {
                hasClockwise = true;
            } else if (occupant.getDirection() == Direction.COUNTER_CLOCKWISE) {
                hasCounterClockwise = true;
            }
            if (hasClockwise && hasCounterClockwise)
                break;
        }
        return new boolean[]{hasClockwise, hasCounterClockwise};
    }
}