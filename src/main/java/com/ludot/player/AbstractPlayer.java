package com.ludot.player;

import com.ludot.board.Board;
import com.ludot.command.*;
import com.ludot.config.GameConfig;
import com.ludot.model.Colour;
import com.ludot.model.Location;
import com.ludot.model.LocationType;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.random.CoinToss;
import com.ludot.random.Dice;
import com.ludot.rules.RuleEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Base class for player behaviour using the Template Method pattern.

public abstract class AbstractPlayer {

    // Each player keeps its colour, pieces, shared game services, and recent roll
    // state.
    protected final Colour colour;
    protected final List<Piece> pieces;
    protected final Board board;
    protected final RuleEngine ruleEngine;
    protected final Dice dice;
    protected final CoinToss coinToss;
    protected final GameLogger logger;

    private int consecutiveSixCount;
    private int lastDiceValue;
    private boolean lastRollIgnoredDueToThirdConsecutiveSix;
    private boolean lastRollForcedBlockBreak;

    // The constructor validates dependencies and copies the piece list for safe
    // local use.
    protected AbstractPlayer(Colour colour, List<Piece> pieces, Board board,
                             RuleEngine ruleEngine, Dice dice,
                             CoinToss coinToss, GameLogger logger) {
        if (colour == null)
            throw new IllegalArgumentException("Colour must not be null.");
        if (pieces == null)
            throw new IllegalArgumentException("Piece list must not be null.");
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (ruleEngine == null)
            throw new IllegalArgumentException("RuleEngine must not be null.");
        if (dice == null)
            throw new IllegalArgumentException("Dice must not be null.");
        if (coinToss == null)
            throw new IllegalArgumentException("CoinToss must not be null.");
        if (logger == null)
            throw new IllegalArgumentException("GameLogger must not be null.");

        for (Piece p : pieces) {
            if (p == null)
                throw new IllegalArgumentException(
                        "Piece list must not contain null pieces.");
            if (p.getColour() != colour) {
                throw new IllegalArgumentException("All pieces must match player colour "
                        + colour + " but found " + p.getColour());
            }
        }

        this.colour = colour;
        this.pieces = new ArrayList<>(pieces);
        this.board = board;
        this.ruleEngine = ruleEngine;
        this.dice = dice;
        this.coinToss = coinToss;
        this.logger = logger;
        this.consecutiveSixCount = 0;
        this.lastDiceValue = 0;
        this.lastRollIgnoredDueToThirdConsecutiveSix = false;
        this.lastRollForcedBlockBreak = false;
    }

    // These accessors expose the player's current state to the rest of the game.
    public Colour getColour() {
        return colour;
    }

    public List<Piece> getPieces() {
        return Collections.unmodifiableList(pieces);
    }

    public int getConsecutiveSixCount() {
        return consecutiveSixCount;
    }

    public int getLastDiceValue() {
        return lastDiceValue;
    }

    public boolean wasLastRollIgnoredDueToThirdConsecutiveSix() {
        return lastRollIgnoredDueToThirdConsecutiveSix;
    }

    public boolean wasLastRollForcedBlockBreak() {
        return lastRollForcedBlockBreak;
    }

    // This helper is used only to decide the starting player order.
    public int rollForStartingOrder() {
        return dice.roll();
    }

    // These helpers track consecutive sixes, which affects bonus-roll rules.
    public void resetConsecutiveSixCount() {
        consecutiveSixCount = 0;
    }

    public void incrementConsecutiveSixCountIfNeeded(int diceValue) {
        if (diceValue == GameConfig.BONUS_ROLL_VALUE) {
            consecutiveSixCount++;
        }
    }

    // These queries describe where the player's pieces currently are.
    public boolean hasPiecesInBase() {
        return countPiecesInBase() > 0;
    }

    public boolean hasPiecesOnBoard() {
        return countPiecesOnBoard() > 0;
    }

    public boolean hasWon() {
        return ruleEngine.hasWon(pieces);
    }

    public int countPiecesInBase() {
        int count = 0;
        for (Piece p : pieces) {
            if (p.isInBase())
                count++;
        }
        return count;
    }

    public int countPiecesOnBoard() {
        int count = 0;
        for (Piece p : pieces) {
            if (!p.isInBase() && !p.isHome())
                count++;
        }
        return count;
    }

    // This returns the first piece still waiting in base, or null when none exist.
    public Piece firstPieceInBase() {
        for (Piece p : pieces) {
            if (p.isInBase())
                return p;
        }
        return null;
    }

    // This collects pieces that are active on the board and can move.
    public List<Piece> movablePiecesOnBoard() {
        List<Piece> movable = new ArrayList<>();
        for (Piece p : pieces) {
            if (!p.isInBase() && !p.isHome() && !p.isInBriefing())
                movable.add(p);
        }
        return movable;
    }

    // The turn flow rolls the dice, applies the six-roll rule, and asks the
    // subclass for a move.
    public final Command takeTurn() {
        int diceValue = dice.roll();
        lastDiceValue = diceValue;
        lastRollIgnoredDueToThirdConsecutiveSix = false;
        lastRollForcedBlockBreak = false;
        logger.logPlayerRolled(colour, diceValue);

        incrementConsecutiveSixCountIfNeeded(diceValue);

        // Third consecutive six forfeits the turn
        if (ruleEngine.bonusRollRule()
                .thirdConsecutiveSixShouldBeIgnored(consecutiveSixCount)) {
            return handleThirdConsecutiveSix();
        }

        // Reset the counter when the roll is not a six
        if (diceValue != GameConfig.BONUS_ROLL_VALUE) {
            resetConsecutiveSixCount();
        }

        return selectMove(diceValue);
    }

    // Subclasses choose the move to attempt for the current dice roll.
    protected abstract Command selectMove(int diceValue);

    // These helpers support the common move options used by player subclasses.
    protected Command tryMoveFromBase(int diceValue) {
        Piece basePiece = firstPieceInBase();
        if (basePiece == null) {
            return new NoMoveCommand();
        }
        if (!ruleEngine.canMoveFromBase(basePiece, diceValue)) {
            return new NoMoveCommand();
        }
        return new BaseToStartingSquareCommand(board, basePiece, coinToss);
    }

    protected Command tryStandardMove(Piece piece, int diceValue) {
        if (piece == null)
            return new NoMoveCommand();
        int adjustedSteps = ruleEngine.movementRule().adjustMovementForPiece(piece, diceValue);
        if (piece.getLocation().isStandardPath()
                && ruleEngine.blockRule().isBlockAt(piece.getLocation())) {
            if (piece.getOriginalDirection() != null
                    && piece.getDirection() != piece.getOriginalDirection()) {
                piece.restoreOriginalDirection();
                return ruleEngine.movementRule().commandForStandardMove(piece, diceValue);
            }
            return ruleEngine.blockRule().commandForBlockMove(piece, adjustedSteps);
        }
        if (ruleEngine.isBlocked(piece, diceValue)) {
            Location blockingLocation = ruleEngine.blockRule().findBlockingLocation(piece, adjustedSteps);
            Location beforeBlockLocation = ruleEngine.blockRule().findCellBeforeBlock(piece, adjustedSteps);

            if (blockingLocation == null || beforeBlockLocation == null) {
                return new NoMoveCommand();
            }

            if (beforeBlockLocation.equals(piece.getLocation())) {
                return new BlockedMoveCommand(
                        piece,
                        blockingLocation,
                        board.getStandardCell(blockingLocation.getIndex())
                                .getPieces().get(0));
            }

            return new MoveBeforeBlockCommand(board, piece, beforeBlockLocation);
        }
        return ruleEngine.movementRule().commandForStandardMove(piece, diceValue);
    }

    // This handles moves that continue along the home path.
    public Command tryForcedBreakMove(Piece piece, int diceValue) {
        if (piece == null)
            return new NoMoveCommand();

        if (piece.getOriginalDirection() != null
                && piece.getDirection() != piece.getOriginalDirection()) {
            piece.restoreOriginalDirection();
        }

        int adjustedSteps = ruleEngine.movementRule().adjustMovementForPiece(piece, diceValue);
        if (ruleEngine.isBlocked(piece, diceValue)) {
            Location blockingLocation = ruleEngine.blockRule()
                    .findBlockingLocation(piece, adjustedSteps);
            Location beforeBlockLocation = ruleEngine.blockRule()
                    .findCellBeforeBlock(piece, adjustedSteps);

            if (blockingLocation == null || beforeBlockLocation == null) {
                return new NoMoveCommand();
            }

            if (beforeBlockLocation.equals(piece.getLocation())) {
                return new BlockedMoveCommand(
                        piece,
                        blockingLocation,
                        board.getStandardCell(blockingLocation.getIndex())
                                .getPieces().get(0));
            }

            return new MoveBeforeBlockCommand(board, piece, beforeBlockLocation);
        }

        return ruleEngine.movementRule().commandForStandardMove(piece, diceValue);
    }

    protected Command tryHomePathMove(Piece piece, int diceValue) {
        if (piece == null)
            return new NoMoveCommand();
        if (piece.getLocation().getType() != LocationType.HOME_PATH)
            return new NoMoveCommand();
        return ruleEngine.movementRule().commandForHomePathMove(piece, diceValue);
    }

    // This helper distinguishes an actual move from a no-move result.
    protected boolean isCommandAvailable(Command command) {
        return !(command instanceof NoMoveCommand);
    }

    // This checks whether any movable piece is currently part of a blockade.
    private boolean hasBlockadeOnBoard() {
        return ruleEngine.blockRule().hasBlockade(movablePiecesOnBoard());
    }

    // The three-sixes rule either breaks a blockade or skips the turn.
    private Command handleThirdConsecutiveSix() {
        if (hasBlockadeOnBoard()) {
            Location blockadeLocation = ruleEngine.blockRule()
                    .findFirstBlockadeLocation(movablePiecesOnBoard());
            if (blockadeLocation != null) {
                lastRollForcedBlockBreak = true;
                resetConsecutiveSixCount();
                return new ForcedBlockBreakCommand(blockadeLocation);
            }
        }
        logger.logThirdConsecutiveSixIgnored(colour);
        lastRollIgnoredDueToThirdConsecutiveSix = true;
        resetConsecutiveSixCount();
        return new NoMoveCommand();
    }

    // This command breaks a blockade by moving the extra pieces out one at a time.
    private final class ForcedBlockBreakCommand implements Command {

        private final Location blockadeLocation;

        private ForcedBlockBreakCommand(Location blockadeLocation) {
            this.blockadeLocation = blockadeLocation;
        }

        @Override
        public void execute() {
            if (blockadeLocation == null)
                return;
            List<Piece> blockPieces = new ArrayList<>(board.getStandardCell(blockadeLocation.getIndex()).getPieces());
            if (blockPieces.size() < 2)
                return;

            // Leave one piece behind and move the rest in board order.
            for (int i = 1; i < blockPieces.size(); i++) {
                Piece movingPiece = blockPieces.get(i);
                int moveDistance = 6 / (blockPieces.size() - 1);
                moveDistance = Math.max(1, moveDistance);
                Command move = tryForcedBreakMove(movingPiece, moveDistance);
                move.execute();
                logger.logThirdConsecutiveSixBlockBreak(
                        colour,
                        movingPiece,
                        moveDistance,
                        movingPiece.getDirection());
            }
        }
    }
}