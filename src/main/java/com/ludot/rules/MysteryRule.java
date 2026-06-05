package com.ludot.rules;

import com.ludot.board.Board;
import com.ludot.command.Command;
import com.ludot.command.NoMoveCommand;
import com.ludot.command.TeleportCommand;
import com.ludot.config.GameConfig;
import com.ludot.model.Direction;
import com.ludot.model.Location;
import com.ludot.model.MysteryEffect;
import com.ludot.model.Piece;
import com.ludot.output.GameLogger;
import com.ludot.random.MysteryCellSpawner;
import com.ludot.random.MysteryEffectSelector;
import com.ludot.state.BriefingState;
import com.ludot.state.EnergizedState;
import com.ludot.state.SickState;

import java.util.Set;

// Applies mystery-cell spawn, teleport, and effect rules for the current round.
public class MysteryRule {

    private final Board board;
    private final MysteryEffectSelector effectSelector;
    private final MysteryCellSpawner mysteryCellSpawner;
    private final GameLogger logger;

    // State objects used when mystery effects change a piece's behavior.
    private final EnergizedState energizedState;
    private final SickState sickState;
    private final BriefingState briefingState;

    // Tracks the currently active mystery cell and its remaining lifetime.
    private Location activeMysteryLocation;
    private int activeMysteryRemainingRounds;
    private int previousMysteryIndex;
    private boolean mysteryTimingStarted;
    private int roundsSinceFirstStandardPathEntry;

    public MysteryRule(Board board, MysteryEffectSelector effectSelector,
                       MysteryCellSpawner mysteryCellSpawner, GameLogger logger) {
        if (board == null)
            throw new IllegalArgumentException("Board must not be null.");
        if (effectSelector == null)
            throw new IllegalArgumentException(
                    "MysteryEffectSelector must not be null.");
        if (mysteryCellSpawner == null)
            throw new IllegalArgumentException(
                    "MysteryCellSpawner must not be null.");
        if (logger == null)
            throw new IllegalArgumentException("GameLogger must not be null.");

        this.board = board;
        this.effectSelector = effectSelector;
        this.mysteryCellSpawner = mysteryCellSpawner;
        this.logger = logger;

        this.energizedState = new EnergizedState();
        this.sickState = new SickState();
        this.briefingState = new BriefingState();

        this.activeMysteryLocation = null;
        this.activeMysteryRemainingRounds = 0;
        this.previousMysteryIndex = -1;
        this.mysteryTimingStarted = false;
        this.roundsSinceFirstStandardPathEntry = 0;
    }

    // Exposes the current mystery-cell state to the rest of the game.

    public boolean hasActiveMysteryCell() {
        return activeMysteryLocation != null;
    }

    public Location getActiveMysteryLocation() {
        return activeMysteryLocation;
    }

    public int getActiveMysteryRemainingRounds() {
        return activeMysteryRemainingRounds;
    }

    public boolean hasMysteryTimingStarted() {
        return mysteryTimingStarted;
    }

    public void noteStandardPathEntry() {
        if (mysteryTimingStarted) {
            return;
        }
        mysteryTimingStarted = true;
        roundsSinceFirstStandardPathEntry = 0;
    }

    // Spawns a fresh mystery cell and removes the old one when its rounds end.

    public void updateMysteryCellForNewRound() {
        if (!mysteryTimingStarted) {
            return;
        }

        if (!hasActiveMysteryCell()) {
            roundsSinceFirstStandardPathEntry++;
            if (roundsSinceFirstStandardPathEntry < GameConfig.MYSTERY_CELL_APPEARS_AFTER_ROUNDS) {
                return;
            }

            spawnNewMysteryCell();
            return;
        }

        activeMysteryRemainingRounds--;

        if (activeMysteryRemainingRounds <= 0) {
            previousMysteryIndex = activeMysteryLocation.getIndex();
            activeMysteryLocation = null;
            spawnNewMysteryCell();
        }
    }

    private void spawnNewMysteryCell() {
        Set<Integer> occupied = board.getOccupiedStandardPathIndices();
        int spawnIndex = mysteryCellSpawner.spawn(previousMysteryIndex, occupied);
        activeMysteryLocation = Location.standardPath(spawnIndex);
        activeMysteryRemainingRounds = GameConfig.MYSTERY_CELL_LASTS_ROUNDS;
        logger.logMysteryCellSpawned(activeMysteryLocation);
    }

    // Returns true when the piece is currently on the active mystery cell.

    public boolean hasPieceLandedOnMysteryCell(Piece piece) {
        if (!hasActiveMysteryCell())
            return false;
        if (piece == null)
            return false;
        if (!piece.getLocation().isOnStandardRing())
            return false;
        return piece.getLocation().getIndex() == activeMysteryLocation.getIndex();
    }

    // Creates the teleport command for a piece that lands on a mystery cell.

    public Command commandForMysteryEffect(Piece piece) {
        if (piece == null)
            return new NoMoveCommand();
        if (!hasPieceLandedOnMysteryCell(piece))
            return new NoMoveCommand();

        MysteryEffect effect = selectEffect();
        Location destinationLocation = destinationFor(effect, piece);
        logger.logMysteryTeleport(piece.getColour(), destinationLocation);
        logger.logTeleportedTo(piece, briefDestinationName(effect));
        return new TeleportCommand(board, piece, effect);
    }

    public MysteryEffect selectEffect() {
        return effectSelector.selectEffect();
    }

    // Applies any extra state change that follows a teleport effect.

    public void applyPostTeleportEffect(Piece piece, MysteryEffect effect) {
        if (piece == null || effect == null)
            return;

        switch (effect) {
            case ALPHA -> applyAlphaEffect(piece);
            case BETA -> applyBetaEffect(piece);
            case GAMMA -> applyGammaEffect(piece);
            // BASE, STARTING_SQUARE, APPROACH need no extra state change
            default -> {
            }
        }
    }

    private void applyAlphaEffect(Piece piece) {
        MysteryEffect toss = effectSelector.selectEffect();
        if (toss.ordinal() % 2 == 0) {
            piece.setAlphaMovementState(energizedState);
            energizedState.onEnter(piece);
            logger.logEnergized(piece);
        } else {
            piece.setAlphaMovementState(sickState);
            sickState.onEnter(piece);
            logger.logSick(piece);
        }
    }

    private void applyBetaEffect(Piece piece) {
        briefingState.onEnter(piece);
        logger.logBriefing(piece);
    }

    private void applyGammaEffect(Piece piece) {
        Direction dir = piece.getDirection();
        if (dir == null)
            return;

        if (dir == Direction.CLOCKWISE) {
            piece.setDirection(Direction.COUNTER_CLOCKWISE);
            logger.logGammaClockwiseChanged(piece);
        } else {
            logger.logGammaCounterClockwiseToBeta(piece);
            logger.logTeleportedTo(piece, "Beta");
            new TeleportCommand(board, piece, MysteryEffect.BETA).execute();
            briefingState.onEnter(piece);
            logger.logBriefing(piece);
        }
    }

    // Tracks repeated restricted rolls while the piece is in briefing mode.

    public void handleBriefingRestrictedRoll(Piece piece, int diceValue) {
        if (piece == null)
            return;
        if (!piece.isInBriefing())
            return;

        if (diceValue == GameConfig.BETA_RESTRICTED_ROLL) {
            int count = piece.getConsecutiveBetaRestrictedRollCount() + 1;
            piece.setConsecutiveBetaRestrictedRollCount(count);
        } else {
            piece.setConsecutiveBetaRestrictedRollCount(0);
        }

        if (piece.getConsecutiveBetaRestrictedRollCount() >= GameConfig.BETA_RESTRICTED_ROLL_LIMIT) {
            logger.logBriefingRestrictedTeleport(piece);
            board.returnPieceToBase(piece);
        }
    }

    public void onRoundPassedForPiece(Piece piece) {
        if (piece == null)
            return;
        if (!piece.isInBriefing())
            return;
        briefingState.onRoundPassed(piece);
    }

    // Maps each mystery effect to its destination and display name.

    private Location destinationFor(MysteryEffect effect, Piece piece) {
        return switch (effect) {
            case ALPHA -> board.getAlphaLocation();
            case BETA -> board.getBetaLocation();
            case GAMMA -> board.getGammaLocation();
            case BASE -> Location.base(piece.getColour());
            case STARTING_SQUARE -> board.getStartingLocation(piece.getColour());
            case APPROACH -> board.getApproachLocation(piece.getColour());
        };
    }

    private String briefDestinationName(MysteryEffect effect) {
        return switch (effect) {
            case ALPHA -> "Alpha";
            case BETA -> "Beta";
            case GAMMA -> "Gamma";
            case APPROACH -> "Approach";
            case STARTING_SQUARE -> "X";
            case BASE -> "Base";
        };
    }

}