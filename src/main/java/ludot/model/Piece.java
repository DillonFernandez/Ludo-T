// Tracks a piece's location, directions, captures, approach visits and temporary movement effects.
package ludot.model;

import ludot.config.GameSettings;
import ludot.state.NormalState;
import ludot.state.PieceState;

public final class Piece {
    private final GameSettings settings = GameSettings.getInstance();
    private final Colour colour;
    private final int number;
    private int location;
    private Direction direction;
    private Direction originalDirection;
    private int captures;
    private int approachVisits;
    private PieceState state;
    private int effectEndsAfterRound;
    private int consecutiveThrees;
    private boolean movedInBlock;

    public Piece(Colour colour, int number) {
        if (colour == null || number < 1
                || number > settings.getPiecesPerPlayer()) {
            throw new IllegalArgumentException("Invalid piece identity.");
        }
        this.colour = colour;
        this.number = number;
        returnToBase();
    }

    public void enterBoard(Direction direction) {
        if (!isAtBase() || direction == null) {
            throw new IllegalStateException("The piece cannot enter the board.");
        }
        this.direction = direction;
        originalDirection = direction;
        location = settings.getStartingCell(colour);
    }

    public void returnToBase() {
        location = settings.getBase();
        direction = Direction.CLOCKWISE;
        originalDirection = Direction.CLOCKWISE;
        captures = 0;
        approachVisits = 0;
        state = new NormalState();
        effectEndsAfterRound = 0;
        consecutiveThrees = 0;
        movedInBlock = false;
    }

    public void moveTo(int location, int approachVisits) {
        if (location < 0 || location > settings.getHome()
                || approachVisits < 0) {
            throw new IllegalArgumentException("Invalid piece destination.");
        }
        this.location = location;
        this.approachVisits = approachVisits;
    }

    public void recordCapture() {
        captures++;
    }

    public void turnCounterclockwise() {
        direction = Direction.COUNTERCLOCKWISE;
        approachVisits = 0;
    }

    public void restoreOriginalDirection() {
        direction = originalDirection;
        movedInBlock = false;
    }

    public void recordBlockMove() {
        movedInBlock = true;
    }

    public boolean hasMovedInBlock() {
        return movedInBlock;
    }

    public void chooseStartingDirection(Direction direction) {
        this.direction = direction;
        originalDirection = direction;
    }

    public void applyState(PieceState state, int round) {
        if (state == null || round < 1) {
            throw new IllegalArgumentException("Invalid piece effect.");
        }
        this.state = state;
        effectEndsAfterRound = round + settings.getEffectRounds();
        consecutiveThrees = 0;
    }

    public void finishRound(int round) {
        // The effect expires after the arrival round and four further rounds.
        if (effectEndsAfterRound > 0 && round >= effectEndsAfterRound) {
            state = new NormalState();
            effectEndsAfterRound = 0;
            consecutiveThrees = 0;
        }
    }

    public boolean mustReturnAfterRoll(int roll) {
        if (state.canMove() || isAtBase() || isAtHome()) {
            return false;
        }
        if (roll == settings.getBriefingRoll()) {
            consecutiveThrees++;
        } else {
            consecutiveThrees = 0;
        }
        return consecutiveThrees >= settings.getConsecutiveBriefingRolls();
    }

    public boolean isAtBase() {
        return location == settings.getBase();
    }

    public boolean isAtHome() {
        return location == settings.getHome();
    }

    public boolean isOnStandardPath() {
        return location >= 0 && location < settings.getStandardCells();
    }

    public boolean isInHomeStraight() {
        return location >= settings.getStandardCells() && !isAtHome();
    }

    public boolean canEnterHome(Direction travelDirection, int visits) {
        return captures >= settings.getRequiredCaptures()
                && (travelDirection == Direction.CLOCKWISE
                        || visits >= settings.getRequiredApproachVisits());
    }

    public int movementFor(int roll) {
        return state.movementFor(roll);
    }

    public boolean canMove() {
        return state.canMove() && !isAtHome();
    }

    public Colour getColour() {
        return colour;
    }

    public int getNumber() {
        return number;
    }

    public String getName() {
        return colour.getInitial() + number;
    }

    public int getLocation() {
        return location;
    }

    public Direction getDirection() {
        return direction;
    }

    public Direction getOriginalDirection() {
        return originalDirection;
    }

    public int getCaptures() {
        return captures;
    }

    public int getApproachVisits() {
        return approachVisits;
    }
}
