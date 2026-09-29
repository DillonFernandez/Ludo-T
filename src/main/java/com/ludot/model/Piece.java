package com.ludot.model;

import com.ludot.config.GameConfig;
import com.ludot.state.PieceState;

// Represents one token on the board and the state that affects its movement.
public class Piece {

    // A piece stores its owner, number, current location, facing, and effect
    // counters.
    private final Colour colour;
    private final int pieceNumber;

    private Location location;
    private Direction direction;
    private Direction originalDirection;

    private int captureCount;
    private int alphaEffectRemainingRounds;
    private PieceState alphaMovementState;
    private int betaBriefingRemainingRounds;
    private int consecutiveBetaRestrictedRollCount;
    private int counterClockwiseApproachPassCount;

    // The constructor validates the owner and piece number before the token can be
    // used.
    public Piece(Colour colour, int pieceNumber) {
        if (colour == null) {
            throw new IllegalArgumentException("Piece colour must not be null.");
        }
        if (pieceNumber < 1 || pieceNumber > GameConfig.PIECES_PER_PLAYER) {
            throw new IllegalArgumentException(
                    "Piece number must be 1 to " + GameConfig.PIECES_PER_PLAYER
                            + ", got " + pieceNumber);
        }
        this.colour = colour;
        this.pieceNumber = pieceNumber;
        this.location = Location.base(colour);
        this.direction = null;
        this.originalDirection = null;
        this.captureCount = 0;
        this.alphaEffectRemainingRounds = 0;
        this.alphaMovementState = null;
        this.betaBriefingRemainingRounds = 0;
        this.consecutiveBetaRestrictedRollCount = 0;
        this.counterClockwiseApproachPassCount = 0;
    }

    // The name gives a short, readable label for logs and messages.
    public String getName() {
        char initial = colour.name().charAt(0);
        return "" + initial + pieceNumber;
    }

    // Resetting to base clears all movement effects and capture history.
    public void resetToBase() {
        this.location = Location.base(colour);
        this.direction = null;
        this.originalDirection = null;
        this.captureCount = 0;
        this.alphaEffectRemainingRounds = 0;
        this.alphaMovementState = null;
        this.betaBriefingRemainingRounds = 0;
        this.consecutiveBetaRestrictedRollCount = 0;
        this.counterClockwiseApproachPassCount = 0;
    }

    // The capture counter records how many times this token has been returned to
    // base.
    public void incrementCaptureCount() {
        captureCount++;
    }

    // Restoring the original direction helps replay the token's normal path.
    public void restoreOriginalDirection() {
        if (originalDirection != null) {
            this.direction = originalDirection;
        }
    }

    // The current Alpha movement state can be read or cleared when needed.
    public PieceState getAlphaMovementState() {
        return alphaMovementState;
    }

    // This stores the special movement state produced by mystery effects.
    public void setAlphaMovementState(PieceState state) {
        this.alphaMovementState = state;
    }

    // Clearing this state removes the temporary movement override.
    public void clearAlphaMovementState() {
        this.alphaMovementState = null;
    }

    // Each counter-clockwise approach pass is counted for rule evaluation.
    public void incrementCounterClockwiseApproachPassCount() {
        counterClockwiseApproachPassCount++;
    }

    // This count records counter-clockwise passes over the approach square.
    public int getCounterClockwiseApproachPassCount() {
        return counterClockwiseApproachPassCount;
    }

    // Resetting this count starts a new tracking period for the rule logic.
    public void resetCounterClockwiseApproachPassCount() {
        counterClockwiseApproachPassCount = 0;
    }

    // These helpers expose the piece's current high-level state.
    public boolean isInBase() {
        return location.isBase();
    }

    public boolean isHome() {
        return location.isHome();
    }

    public boolean isInBriefing() {
        return betaBriefingRemainingRounds > 0;
    }

    public boolean hasAlphaEffect() {
        return alphaEffectRemainingRounds > 0;
    }

    // These accessors expose the piece's stored details.
    public Colour getColour() {
        return colour;
    }

    public int getPieceNumber() {
        return pieceNumber;
    }

    public Location getLocation() {
        return location;
    }

    // These updates keep the piece state valid, especially while it is still in
    // base.
    public void setLocation(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location must not be null.");
        }
        this.location = location;
    }

    public Direction getDirection() {
        return direction;
    }

    // The original direction is remembered the first time the token leaves base.
    public void setDirection(Direction direction) {
        if (direction == null && !location.isBase()) {
            throw new IllegalArgumentException(
                    "Direction may only be null while the piece is in base.");
        }
        this.direction = direction;
        if (direction != null && originalDirection == null) {
            originalDirection = direction;
        }
    }

    public Direction getOriginalDirection() {
        return originalDirection;
    }

    public int getCaptureCount() {
        return captureCount;
    }

    public int getAlphaEffectRemainingRounds() {
        return alphaEffectRemainingRounds;
    }

    // Alpha-effect rounds are clamped to zero so expired effects cannot linger.
    public void setAlphaEffectRemainingRounds(int rounds) {
        this.alphaEffectRemainingRounds = Math.max(0, rounds);
    }

    public int getBetaBriefingRemainingRounds() {
        return betaBriefingRemainingRounds;
    }

    // Beta briefing rounds are also clamped to zero for safety.
    public void setBetaBriefingRemainingRounds(int rounds) {
        this.betaBriefingRemainingRounds = Math.max(0, rounds);
    }

    public int getConsecutiveBetaRestrictedRollCount() {
        return consecutiveBetaRestrictedRollCount;
    }

    // This counter tracks repeated restricted rolls caused by Beta effects.
    public void setConsecutiveBetaRestrictedRollCount(int count) {
        this.consecutiveBetaRestrictedRollCount = Math.max(0, count);
    }

    @Override
    public String toString() {
        return "Piece{" + getName() + ", " + location.getDisplayName() + "}";
    }
}